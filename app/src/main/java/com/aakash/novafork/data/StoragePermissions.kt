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

/**
 * Storage access, Android 8 → 16.
 *
 * Media access (videos, plus images for poster.jpg/fanart.jpg) is enough for the device library.
 * "All files access" is optional, like in VLC: Android hides non-media files such as .srt
 * subtitles from apps without it, so with it subtitles and artwork next to device videos load too.
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

    /** Every video on the device is visible. */
    fun hasVideoAccess(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= 33 -> granted(context, Manifest.permission.READ_MEDIA_VIDEO)
        else -> granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** Android 14+ "limited access": only videos the user picked are visible. */
    fun hasPartialAccess(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 34 &&
            !granted(context, Manifest.permission.READ_MEDIA_VIDEO) &&
            granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

    /** Some or all videos are visible: worth scanning MediaStore. */
    fun hasAnyVideoAccess(context: Context): Boolean = hasVideoAccess(context) || hasPartialAccess(context)

    /** Images next to videos (local posters) can be read through MediaStore. */
    fun hasImageAccess(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= 33 -> granted(context, Manifest.permission.READ_MEDIA_IMAGES)
        else -> granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** All files access is a separate special permission on Android 11+. */
    val canRequestAllFilesAccess: Boolean get() = Build.VERSION.SDK_INT >= 30

    /**
     * True when plain java.io.File access to shared storage works for every file type:
     * Android 11+ with "All files access", or Android 10 and older with the storage permission.
     */
    fun hasAllFilesAccess(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= 30 -> Environment.isExternalStorageManager()
        else -> granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** System screen where the user grants "All files access" to this app (Android 11+). */
    fun allFilesAccessIntent(context: Context): Intent =
        Intent(AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
}
