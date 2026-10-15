package com.aakash.novafork.ui.common

import androidx.annotation.DrawableRes
import com.aakash.novafork.R
import com.aakash.novafork.data.db.SourceType

/** The icon of a source type, shared by Browse, Settings and the add-source dialogs. */
@DrawableRes
fun sourceTypeIcon(type: SourceType): Int = when (type) {
    SourceType.DEVICE -> R.drawable.ic_storage
    SourceType.FOLDER -> R.drawable.ic_folder
    SourceType.SMB -> R.drawable.ic_lan
    SourceType.STREAM -> R.drawable.ic_live_tv
    SourceType.FTP -> R.drawable.ic_dns
    SourceType.SFTP -> R.drawable.ic_key
    SourceType.NFS -> R.drawable.ic_computer
    SourceType.WEBDAV -> R.drawable.ic_cloud
    SourceType.UPNP -> R.drawable.ic_wifi
    SourceType.JELLYFIN -> R.drawable.ic_video_library
    SourceType.EMBY -> R.drawable.ic_smart_display
    SourceType.PLEX -> R.drawable.ic_theaters
}
