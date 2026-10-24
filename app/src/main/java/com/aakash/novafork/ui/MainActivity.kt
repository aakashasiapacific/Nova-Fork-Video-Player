package com.aakash.novafork.ui

import android.app.ActivityOptions
import android.app.RecoverableSecurityException
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.IntentSender
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings as AndroidSettings
import android.util.Log
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aakash.novafork.data.StoragePermissions
import com.aakash.novafork.data.backup.BackupManager
import com.aakash.novafork.player.PlayerIntents
import com.aakash.novafork.ui.audio.LocalMusicEnabled
import com.aakash.novafork.ui.common.LocalHeroPreviewEnabled
import com.aakash.novafork.ui.common.LocalStatusBarIcons
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.DialogUi
import com.aakash.novafork.ui.model.TrashedVideoUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * The library. Hosts [NovaRoot] and adds the actions that need an Activity on top of
 * [MainViewModel]: starting the player, the folder and backup file pickers and storage
 * permission prompts.
 */
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val uiPrefs by lazy { getSharedPreferences(UI_PREFS, MODE_PRIVATE) }

    /** Taken just before a permission prompt, to tell "the system showed nothing" apart from a denial. */
    private var askedBefore = false
    private var rationaleBefore = false

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onPermissionResult()
    }

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onFolderPicked(uri)
    }

    private val backupSaver =
        registerForActivityResult(ActivityResultContracts.CreateDocument(BackupManager.MIME_TYPE)) { uri ->
            if (uri != null) viewModel.writeBackup(uri)
        }

    private val backupOpener = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.readBackup(uri)
    }

    private val restorePrompt = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        viewModel.onVideoRestored()
        lifecycleScope.launch { viewModel.showDialog(DialogUi.RecentlyDeleted(trashedVideos())) }
    }

    /** The video whose deletion waits on Android's prompt. */
    private var pendingDelete: Pair<Long, MainViewModel.DeleteTarget>? = null

    private val deletePrompt = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val (videoId, target) = pendingDelete ?: return@registerForActivityResult
        pendingDelete = null
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        // Android 11 and later bin it on "Allow"; Android 10 only granted the right, so try again.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            viewModel.onVideoDeleted(videoId, target.title, toBin = true)
        } else {
            deleteFile(videoId, target)
        }
    }

    private val actions: AppActions by lazy {
        object : AppActions by viewModel {
            override fun play(videoId: Long, startOver: Boolean) {
                startActivity(PlayerIntents.forVideo(this@MainActivity, videoId, startOver), growFromTouch())
            }

            override fun playUrl(url: String) {
                val link = url.trim()
                if (!InputRules.isStreamUrl(link)) {
                    viewModel.showMessage(InputRules.STREAM_URL_ERROR)
                    return
                }
                viewModel.dismissDialog()
                startActivity(PlayerIntents.forUri(this@MainActivity, link), growFromTouch())
            }

            override fun playRemote(uri: String, title: String) {
                startActivity(PlayerIntents.forUri(this@MainActivity, uri, title.ifBlank { null }), growFromTouch())
            }

            override fun openWebPage(url: String) {
                if (!tryStart(Intent(Intent.ACTION_VIEW, Uri.parse(url)))) viewModel.showMessage("No browser to open $url")
            }

            override fun requestPermission() = requestStoragePermission()

            override fun requestAllFilesAccess() = openAllFilesAccess()

            override fun openAppSettings() = openAppDetails()

            override fun shareCrashReport() {
                val report = viewModel.state.value.crashReport ?: return
                shareText("Nova Fork crash report", report)
            }

            override fun shareText(subject: String, text: String) {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, subject)
                    .putExtra(Intent.EXTRA_TEXT, text)
                if (!tryStart(Intent.createChooser(send, "Share $subject"))) {
                    viewModel.showMessage("No app to share the report with")
                }
            }

            override fun addFolder() {
                try {
                    folderPicker.launch(null)
                } catch (e: ActivityNotFoundException) {
                    viewModel.showMessage("This device has no folder picker")
                }
            }

            override fun exportBackup(includeSecrets: Boolean) {
                viewModel.prepareExport(includeSecrets)
                try {
                    backupSaver.launch(BackupManager.fileName(LocalDate.now()))
                } catch (e: ActivityNotFoundException) {
                    viewModel.showMessage("This device has no file picker to save the backup with")
                }
            }

            // Always our own question first: some phones grant Android's request without asking
            // (owner: videos went with no prompt), so it never is the only safeguard.
            override fun deleteVideo(videoId: Long) {
                lifecycleScope.launch {
                    val target = viewModel.deleteTarget(videoId) ?: return@launch
                    viewModel.showDialog(DialogUi.ConfirmDeleteVideo(videoId, target.title, toBin = canBin(target)))
                }
            }

            override fun confirmDeleteVideo(videoId: Long) {
                viewModel.dismissDialog()
                lifecycleScope.launch {
                    val target = viewModel.deleteTarget(videoId) ?: return@launch
                    if (canBin(target) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // Into Android's bin, not deleted: restorable for 30 days.
                        val request = MediaStore.createTrashRequest(contentResolver, listOf(Uri.parse(target.uri)), true)
                        askToDelete(videoId, target, request.intentSender)
                    } else {
                        deleteFile(videoId, target)
                    }
                }
            }

            override fun showRecentlyDeleted() {
                viewModel.showDialog(DialogUi.RecentlyDeleted())
                lifecycleScope.launch { viewModel.showDialog(DialogUi.RecentlyDeleted(trashedVideos())) }
            }

            override fun restoreVideo(uri: String) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
                val request = MediaStore.createTrashRequest(contentResolver, listOf(Uri.parse(uri)), false)
                try {
                    restorePrompt.launch(IntentSenderRequest.Builder(request.intentSender).build())
                } catch (e: ActivityNotFoundException) {
                    viewModel.showMessage("Couldn't restore this video")
                }
            }

            override fun importBackup() {
                try {
                    backupOpener.launch(BackupManager.OPEN_MIME_TYPES)
                } catch (e: ActivityNotFoundException) {
                    viewModel.showMessage("This device has no file picker to open a backup with")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Android 12+: the black launch screen fades into Home instead of cutting (iOS).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { view ->
                view.animate().alpha(0f).setDuration(SPLASH_FADE_MS).withEndAction { view.remove() }.start()
            }
        }
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            // One dark theme: light (white) system bar icons on clear bars, except where a page
            // asks for dark ones over a bright picture (the Home hero, see LocalStatusBarIcons).
            LaunchedEffect(Unit) { applySystemBarStyle(dark = true) }
            CompositionLocalProvider(
                LocalStatusBarIcons provides { darkIcons -> setDarkStatusBarIcons(darkIcons) },
                LocalHeroPreviewEnabled provides true,
                LocalMusicEnabled provides true,
            ) {
                NovaRoot(state = state, actions = actions)
            }
        }
        lifecycleScope.launch {
            // Queues resolve in the background (folder, season, playlist); play each one once.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.queueRequests.collect { queue ->
                    startActivity(
                        PlayerIntents.forQueue(this@MainActivity, queue.videoIds.toLongArray(), queue.startIndex, queue.shuffle),
                        growFromTouch(),
                    )
                }
            }
        }
        val askedOnce = uiPrefs.getBoolean(KEY_ASKED_STORAGE, false)
        if (savedInstanceState == null && !askedOnce && !StoragePermissions.hasAnyVideoAccess(this)) {
            requestStoragePermission()
        }
    }

    private fun askToDelete(videoId: Long, target: MainViewModel.DeleteTarget, sender: IntentSender) {
        pendingDelete = videoId to target
        try {
            deletePrompt.launch(IntentSenderRequest.Builder(sender).build())
        } catch (e: ActivityNotFoundException) {
            pendingDelete = null
            viewModel.showMessage("Couldn't delete this video")
        }
    }

    /** Deletes the file itself: a picked folder's document, or (Android 10 and older) a MediaStore row. */
    private fun deleteFile(videoId: Long, target: MainViewModel.DeleteTarget) {
        lifecycleScope.launch {
            val uri = Uri.parse(target.uri)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (target.mediaStore) contentResolver.delete(uri, null, null) > 0 else DocumentsContract.deleteDocument(contentResolver, uri)
                }
            }
            val error = result.exceptionOrNull()
            when {
                result.getOrDefault(false) -> viewModel.onVideoDeleted(videoId, target.title, toBin = false)
                // Android 10: a video another app made needs the user's yes first.
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && error is RecoverableSecurityException ->
                    askToDelete(videoId, target, error.userAction.actionIntent.intentSender)
                else -> viewModel.showMessage(
                    if (target.mediaStore) "Couldn't delete this video" else "Couldn't delete this video. Add its folder again to allow changes.",
                )
            }
        }
    }

    /** Android 11 and later keep MediaStore videos in a bin for 30 days; picked folders have none. */
    private fun canBin(target: MainViewModel.DeleteTarget): Boolean =
        target.mediaStore && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /** The videos in Android's bin, soonest gone last. Empty before Android 11. */
    private suspend fun trashedVideos(): List<TrashedVideoUi> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        return withContext(Dispatchers.IO) {
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val args = Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.MediaColumns.DATE_EXPIRES} DESC")
            }
            val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_EXPIRES)
            val now = System.currentTimeMillis() / 1000
            runCatching {
                contentResolver.query(collection, projection, args, null)?.use { c ->
                    buildList {
                        while (c.moveToNext()) {
                            val id = c.getLong(0)
                            val expires = if (c.isNull(2)) null else c.getLong(2)
                            add(
                                TrashedVideoUi(
                                    uri = ContentUris.withAppendedId(collection, id).toString(),
                                    // Android names a binned file ".trashed-<time>-name.mp4".
                                    name = c.getString(1).orEmpty().replace(Regex("^\\.trashed-\\d+-"), ""),
                                    daysLeft = expires?.let { ((it - now + 86_399) / 86_400).toInt().coerceAtLeast(0) },
                                ),
                            )
                        }
                    }
                }
            }.getOrNull().orEmpty()
        }
    }

    /** Where the last touch went down, for the player to grow out of. */
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            lastTouchX = event.rawX
            lastTouchY = event.rawY
        }
        return super.dispatchTouchEvent(event)
    }

    /** The player grows out of the Play button that was tapped instead of sliding in (iOS). */
    private fun growFromTouch(): Bundle {
        val size = (120 * resources.displayMetrics.density).toInt()
        return ActivityOptions.makeScaleUpAnimation(
            window.decorView,
            (lastTouchX - size / 2).toInt(),
            (lastTouchY - size / 4).toInt(),
            size,
            size / 2,
        ).toBundle()
    }

    override fun onResume() {
        super.onResume()
        // Access can change in system settings while the app is in the background.
        viewModel.onPermissionsChanged()
    }

    private fun setDarkStatusBarIcons(dark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = dark
    }

    private fun applySystemBarStyle(dark: Boolean) {
        val style = if (dark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, DARK_SCRIM)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    // Storage permission

    private fun requestStoragePermission() {
        askedBefore = uiPrefs.getBoolean(KEY_ASKED_STORAGE, false)
        rationaleBefore = shouldShowRequestPermissionRationale(primaryPermission())
        uiPrefs.edit().putBoolean(KEY_ASKED_STORAGE, true).apply()
        permissionLauncher.launch(StoragePermissions.required())
    }

    private fun onPermissionResult() {
        val granted = StoragePermissions.hasAnyVideoAccess(this)
        // After "Don't allow" twice Android stops showing the prompt and denies at once: the
        // only way left is the app's page in system settings.
        val blocked = !granted && askedBefore && !rationaleBefore &&
            !shouldShowRequestPermissionRationale(primaryPermission())
        if (blocked) {
            viewModel.showMessage("Allow access to videos under Permissions")
            openAppDetails()
        }
        viewModel.onPermissionsChanged(rescan = granted)
    }

    private fun primaryPermission(): String = StoragePermissions.required().first()

    /** This app's page in system settings, where Permissions › Photos and videos can be set to "Allow all". */
    private fun openAppDetails() {
        val intent = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        if (!tryStart(intent)) viewModel.showMessage("Open Settings › Apps to allow access to videos")
    }

    private fun openAllFilesAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Before Android 11 the storage permission already covers every file.
            requestStoragePermission()
            return
        }
        val opened = tryStart(StoragePermissions.allFilesAccessIntent(this)) ||
            tryStart(Intent(AndroidSettings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        if (!opened) viewModel.showMessage("This device doesn't offer All files access")
    }

    private fun tryStart(intent: Intent): Boolean =
        try {
            startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }

    // Folder picker

    private fun onFolderPicked(treeUri: Uri) {
        lifecycleScope.launch {
            val name = withContext(Dispatchers.IO) {
                try {
                    // Write too, when the picker gave it, so a video can be deleted from the app.
                    try {
                        contentResolver.takePersistableUriPermission(
                            treeUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        )
                    } catch (e: SecurityException) {
                        contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "No persistable permission for $treeUri", e)
                    return@withContext null
                }
                folderName(treeUri)
            }
            if (name == null) {
                viewModel.showMessage("Couldn't keep access to that folder. Try picking it again.")
            } else {
                viewModel.addFolder(treeUri.toString(), name)
            }
        }
    }

    /** The picked folder's display name, read from its document (no androidx.documentfile needed). */
    private fun folderName(treeUri: Uri): String {
        val fallback = treeFolderLabel(treeUri.toString())?.substringAfterLast('/') ?: "Folder"
        return try {
            val document = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
            contentResolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                ?.takeIf { it.isNotBlank() }
                ?: fallback
        } catch (e: IllegalArgumentException) {
            fallback
        } catch (e: SecurityException) {
            fallback
        } catch (e: UnsupportedOperationException) {
            fallback
        }
    }

    private companion object {
        const val TAG = "MainActivity"
        const val UI_PREFS = "ui"
        const val KEY_ASKED_STORAGE = "asked_storage_permission"

        /** How long the launch screen takes to fade into Home. */
        const val SPLASH_FADE_MS = 260L

        /** The navigation bar scrim enableEdgeToEdge uses on Android 8.0, which has no dark nav icons. */
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
