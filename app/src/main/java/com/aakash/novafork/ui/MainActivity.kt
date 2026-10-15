package com.aakash.novafork.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.Settings as AndroidSettings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aakash.novafork.data.StoragePermissions
import com.aakash.novafork.data.ThemeMode
import com.aakash.novafork.player.PlayerIntents
import com.aakash.novafork.ui.model.AppActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The library. Hosts [NovaRoot] and adds the actions that need an Activity on top of
 * [MainViewModel]: starting the player, the folder picker and storage permission prompts.
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

    private val actions: AppActions by lazy {
        object : AppActions by viewModel {
            override fun play(videoId: Long, startOver: Boolean) {
                startActivity(PlayerIntents.forVideo(this@MainActivity, videoId, startOver))
            }

            override fun playUrl(url: String) {
                val link = url.trim()
                if (!InputRules.isStreamUrl(link)) {
                    viewModel.showMessage(InputRules.STREAM_URL_ERROR)
                    return
                }
                viewModel.dismissDialog()
                startActivity(PlayerIntents.forUri(this@MainActivity, link))
            }

            override fun playRemote(uri: String, title: String) {
                startActivity(PlayerIntents.forUri(this@MainActivity, uri, title.ifBlank { null }))
            }

            override fun openWebPage(url: String) {
                if (!tryStart(Intent(Intent.ACTION_VIEW, Uri.parse(url)))) viewModel.showMessage("No browser to open $url")
            }

            override fun requestPermission() = requestStoragePermission()

            override fun requestAllFilesAccess() = openAllFilesAccess()

            override fun shareCrashReport() {
                val report = viewModel.state.value.crashReport ?: return
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Nova Fork crash report")
                    .putExtra(Intent.EXTRA_TEXT, report)
                if (!tryStart(Intent.createChooser(send, "Share crash report"))) {
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
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val dark = when (state.settings.settings.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // System bar icons follow the app's theme, which need not match the system's.
            LaunchedEffect(dark) { applySystemBarStyle(dark) }
            NovaRoot(state = state, actions = actions)
        }
        lifecycleScope.launch {
            // Queues resolve in the background (folder, season, playlist); play each one once.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.queueRequests.collect { queue ->
                    startActivity(
                        PlayerIntents.forQueue(this@MainActivity, queue.videoIds.toLongArray(), queue.startIndex, queue.shuffle),
                    )
                }
            }
        }
        val askedOnce = uiPrefs.getBoolean(KEY_ASKED_STORAGE, false)
        if (savedInstanceState == null && !askedOnce && !StoragePermissions.hasAnyVideoAccess(this)) {
            requestStoragePermission()
        }
    }

    override fun onResume() {
        super.onResume()
        // Access can change in system settings while the app is in the background.
        viewModel.onPermissionsChanged()
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
                    contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
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

        /** The navigation bar scrim enableEdgeToEdge uses on Android 8.0, which has no dark nav icons. */
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
