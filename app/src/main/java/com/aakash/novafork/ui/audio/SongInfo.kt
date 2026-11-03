package com.aakash.novafork.ui.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aakash.novafork.core.audio.AudioQuality
import com.aakash.novafork.core.format.Formats
import com.aakash.novafork.data.db.SongEntity
import com.aakash.novafork.data.music.Album
import com.aakash.novafork.music.AudioInfo
import com.aakash.novafork.music.AudioInfos
import com.aakash.novafork.ui.common.IosSheet

/**
 * The song's [AudioInfo], read on a background thread the first time and remembered; null until
 * then (and for no song), so what shows first is only what the file name and library know.
 */
@Composable
internal fun rememberAudioInfo(song: SongEntity?): AudioInfo? {
    val context = LocalContext.current
    val uri = song?.uri
    val info by produceState(uri?.let(AudioInfos::cached), uri) {
        // A new song must not show the last one's details while it is being read.
        value = uri?.let(AudioInfos::cached)
        if (uri != null && value == null) value = AudioInfos.load(context, uri)
    }
    return info
}

/** "Lossless" / "Hi-Res Lossless" for [song], or null when it isn't lossless (or not known yet). */
internal fun losslessLabel(song: SongEntity, info: AudioInfo?): String? =
    AudioQuality.label(AudioQuality.isLossless(song.fileName, info?.codecMime), info?.sampleRateHz, info?.bitDepth)

/** Whether all of [album]'s songs are lossless; an album of m4a files settles once their codecs are read. */
@Composable
internal fun rememberAlbumLossless(album: Album): Boolean {
    val context = LocalContext.current
    val lossless by produceState(album.songs.isNotEmpty() && album.songs.all(AudioInfos::isLossless), album) {
        value = AudioInfos.allLossless(context, album.songs)
    }
    return lossless
}

/** Apple Music's small grey quality tag ("Lossless"): a rounded grey chip with a light caption. */
@Composable
internal fun LosslessBadge(label: String, modifier: Modifier = Modifier, onDark: Boolean = false) {
    Text(
        label,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White.copy(alpha = if (onDark) 0.7f else 0.55f),
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** A song's "Song Info" sheet: format, quality, size, length and where the file is. */
@Composable
internal fun SongInfoSheet(song: SongEntity, onDismiss: () -> Unit) {
    val info = rememberAudioInfo(song)
    val format = AudioQuality.formatName(song.fileName, info?.codecMime)
    val quality = losslessLabel(song, info)
    val bitrate = info?.bitrateKbps ?: AudioQuality.averageBitrateKbps(song.sizeBytes, song.durationMs)
    val rows = listOfNotNull(
        "Format" to format,
        quality?.let { "Quality" to it },
        bitrate?.let { "Bitrate" to AudioQuality.bitrate(it) },
        info?.sampleRateHz?.let { "Sample Rate" to AudioQuality.sampleRate(it) },
        info?.bitDepth?.let { "Bit Depth" to AudioQuality.bitDepth(it) },
        song.sizeBytes?.let { "Size" to Formats.size(it) },
        song.durationMs?.let { "Duration" to Formats.clock(it) },
        "File Name" to song.fileName,
        song.folder.takeIf { it.isNotBlank() }?.let { "Folder" to it },
    )
    IosSheet(onDismissRequest = onDismiss, title = "Song Info", subtitle = song.title) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 24.dp)) {
            rows.forEach { (label, value) ->
                Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(label, fontSize = 16.sp, color = Color.White.copy(alpha = 0.55f))
                    Text(value, fontSize = 16.sp, color = Color.White, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }
                HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.12f))
            }
        }
    }
}
