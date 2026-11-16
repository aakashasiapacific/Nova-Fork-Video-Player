package com.aakash.novafork.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings as AndroidSettings
import androidx.core.content.ContextCompat

/** How many of the device's videos the app can read through MediaStore. */
enum class VideoAccess {
    /** None: device storage can't be scanned. */
    NONE,

    /** Android 14+ "limited access": only the videos (and photos) the user picked. */
    PARTIAL,

    /** Every video. */
    FULL,
}

/**
 * Storage access, Android 8 → 16.
 *
 * Media access (videos, plus images for poster.jpg/fanart.jpg) is enough for the device library.
 * "All files access" is optional, like in VLC: Android hides non-media files such as .srt
 * subtitles from apps without it, so with it subtitles and artwork next to device videos load too.
 * On Android 11+ it also counts as media access: MediaProvider lets an app that manages all files
 * read every media row, so a user who granted only that still gets the device library.
 */
object StoragePermissions {
    /** Runtime permissions to request for the device library. */
    fun required(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_IMAGES,
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** The video permission itself: READ_MEDIA_VIDEO on Android 13+, READ_EXTERNAL_STORAGE before. */
    fun hasVideoPermission(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= 33 -> granted(context, Manifest.permission.READ_MEDIA_VIDEO)
        else -> granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** Every video on the device is visible: the video permission, or All files access (Android 11+). */
    fun hasVideoAccess(context: Context): Boolean = hasVideoPermission(context) || isStorageManager()

    /** Android 14+ "limited access": only videos the user picked are visible. */
    fun hasPartialAccess(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 34 &&
            !hasVideoAccess(context) &&
            granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

    /** Some or all videos are visible: worth scanning MediaStore. */
    fun hasAnyVideoAccess(context: Context): Boolean = hasVideoAccess(context) || hasPartialAccess(context)

    fun videoAccess(context: Context): VideoAccess = when {
        hasVideoAccess(context) -> VideoAccess.FULL
        hasPartialAccess(context) -> VideoAccess.PARTIAL
        else -> VideoAccess.NONE
    }

    /** Images next to videos (local posters) can be read through MediaStore. */
    fun hasImageAccess(context: Context): Boolean = isStorageManager() || when {
        Build.VERSION.SDK_INT >= 33 -> granted(context, Manifest.permission.READ_MEDIA_IMAGES)
        else -> granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** The runtime permission music needs: READ_MEDIA_AUDIO on Android 13+, READ_EXTERNAL_STORAGE before. */
    fun audioPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    /** Every music file on the device is visible: the audio permission, or All files access (Android 11+). */
    fun hasAudioAccess(context: Context): Boolean = granted(context, audioPermission()) || isStorageManager()

    /** All files access is a separate special permission on Android 11+. */
    val canRequestAllFilesAccess: Boolean get() = Build.VERSION.SDK_INT >= 30

    /**
     * True when plain java.io.File access to shared storage works for every file type:
     * Android 11+ with "All files access", or Android 10 and older with the storage permission.
     */
    fun hasAllFilesAccess(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= 30 -> isStorageManager()
        else -> granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** System screen where the user grants "All files access" to this app (Android 11+). */
    fun allFilesAccessIntent(context: Context): Intent =
        Intent(AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))

    /**
     * Each storage permission as this app sees it right now, for Library diagnostics: name → "granted",
     * "denied" or "not on this Android version".
     */
    fun describe(context: Context): List<Pair<String, String>> {
        val sdk = Build.VERSION.SDK_INT
        fun state(permission: String, since: Int, until: Int = Int.MAX_VALUE): String = when {
            sdk < since || sdk > until -> NOT_HERE
            granted(context, permission) -> "granted"
            else -> "denied"
        }
        return listOf(
            "Videos (READ_MEDIA_VIDEO)" to state(Manifest.permission.READ_MEDIA_VIDEO, since = 33),
            "Images (READ_MEDIA_IMAGES)" to state(Manifest.permission.READ_MEDIA_IMAGES, since = 33),
            "Music (READ_MEDIA_AUDIO)" to state(Manifest.permission.READ_MEDIA_AUDIO, since = 33),
            "Limited access (READ_MEDIA_VISUAL_USER_SELECTED)" to
                state(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, since = 34),
            "Storage (READ_EXTERNAL_STORAGE)" to state(Manifest.permission.READ_EXTERNAL_STORAGE, since = 0, until = 32),
            "All files access (MANAGE_EXTERNAL_STORAGE)" to when {
                sdk < 30 -> NOT_HERE
                isStorageManager() -> "allowed"
                else -> "not allowed"
            },
        )
    }

    /** Android 11+ "All files access"; a ROM whose app-op check fails counts as without it. */
    private fun isStorageManager(): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        return try {
            Environment.isExternalStorageManager()
        } catch (e: RuntimeException) {
            false
        }
    }

    private const val NOT_HERE = "not on this Android version"
}
