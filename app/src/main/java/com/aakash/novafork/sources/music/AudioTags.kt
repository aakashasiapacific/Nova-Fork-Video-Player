package com.aakash.novafork.sources.music

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.aakash.novafork.core.audio.AudioFiles

/** Tags read from the file itself, for music MediaStore doesn't describe (picked folders). */
internal data class FileTags(
    val title: String?,
    val artist: String?,
    val albumArtist: String?,
    val album: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val genre: String?,
    val durationMs: Long?,
)

internal object AudioTags {
    private const val TAG = "AudioTags"

    /** Null when the file can't be read; blocking, call off the main thread. */
    fun read(context: Context, uri: Uri): FileTags? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            fun tag(key: Int): String? = AudioFiles.cleanTag(retriever.extractMetadata(key))
            FileTags(
                title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                albumArtist = tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                trackNumber = AudioFiles.leadingNumber(tag(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)),
                discNumber = AudioFiles.leadingNumber(tag(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)),
                year = AudioFiles.year(tag(MediaMetadataRetriever.METADATA_KEY_YEAR))
                    ?: AudioFiles.year(tag(MediaMetadataRetriever.METADATA_KEY_DATE)),
                genre = tag(MediaMetadataRetriever.METADATA_KEY_GENRE),
                durationMs = tag(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0 },
            )
        } catch (e: RuntimeException) {
            // IllegalArgumentException / RuntimeException: not a file the platform can read.
            Log.w(TAG, "Couldn't read the tags of a music file", e)
            null
        } finally {
            releaseQuietly(retriever)
        }
    }

    /** The picture embedded in the file (ID3 APIC, FLAC PICTURE, MP4 covr); null when there is none. */
    fun embeddedPicture(context: Context, uri: Uri): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (uri.scheme == "file") retriever.setDataSource(uri.path) else retriever.setDataSource(context, uri)
            retriever.embeddedPicture
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't read the embedded picture of a music file", e)
            null
        } finally {
            releaseQuietly(retriever)
        }
    }

    private fun releaseQuietly(retriever: MediaMetadataRetriever) {
        try {
            retriever.release()
        } catch (e: Exception) {
            // Android 10+ declares an IOException; nothing is left to clean up either way.
            Log.w(TAG, "Couldn't release a metadata retriever", e)
        }
    }
}
