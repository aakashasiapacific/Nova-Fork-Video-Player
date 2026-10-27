package com.aakash.novafork.core.audio

import java.text.Normalizer
import java.util.Locale

/**
 * How songs are grouped into albums and artists, Apple Music style: an album is its album artist
 * plus its name; without an album-artist tag, the songs of one album name in one folder (a
 * compilation of many artists, usually). An artist is the album artist, else the song's artist.
 */
object MusicKeys {
    const val UNKNOWN_ARTIST = "Unknown Artist"
    const val UNKNOWN_ALBUM = "Unknown Album"
    const val VARIOUS_ARTISTS = "Various Artists"

    /** The artist a song is listed under: its album artist, else its artist. */
    fun artistOf(albumArtist: String?, artist: String?): String =
        AudioFiles.cleanTag(albumArtist) ?: AudioFiles.cleanTag(artist) ?: UNKNOWN_ARTIST

    fun artistKey(albumArtist: String?, artist: String?): String = normalize(artistOf(albumArtist, artist))

    /**
     * The album a song belongs to. [folder] is the song's folder path; it only counts for songs
     * without an album-artist tag, so one album split over "CD1" and "CD2" folders stays together
     * when it is tagged properly.
     */
    fun albumKey(albumArtist: String?, album: String?, folder: String): String {
        val name = normalize(AudioFiles.cleanTag(album) ?: UNKNOWN_ALBUM)
        val owner = AudioFiles.cleanTag(albumArtist)
        return if (owner != null) "a:${normalize(owner)}|$name" else "f:${folder.lowercase(Locale.ROOT)}|$name"
    }

    /**
     * The artist shown for an album: its album artist when the songs have one, else the artist
     * they share, else [VARIOUS_ARTISTS].
     */
    fun albumArtistOf(albumArtists: Collection<String?>, artists: Collection<String?>): String {
        AudioFiles.cleanTag(albumArtists.firstNotNullOfOrNull { AudioFiles.cleanTag(it) })?.let { return it }
        val distinct = artists.mapNotNull { AudioFiles.cleanTag(it) }.distinctBy(::normalize)
        return when (distinct.size) {
            0 -> UNKNOWN_ARTIST
            1 -> distinct.single()
            else -> VARIOUS_ARTISTS
        }
    }

    /**
     * Sorts names the way Apple Music lists them: ignoring case, accents and a leading "The "
     * ("The Beatles" under B).
     */
    fun sortKey(name: String): String {
        val folded = normalize(name)
        return folded.removePrefix("the ").ifEmpty { folded }
    }

    /** Lower case, accents dropped, inner spaces collapsed: "  Beyoncé " and "beyonce" are one artist. */
    fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        val out = StringBuilder(decomposed.length)
        var lastSpace = false
        for (c in decomposed) {
            if (Character.getType(c) == Character.NON_SPACING_MARK.toInt()) continue
            if (c.isWhitespace()) {
                if (!lastSpace && out.isNotEmpty()) out.append(' ')
                lastSpace = true
            } else {
                out.append(c.lowercaseChar())
                lastSpace = false
            }
        }
        return out.toString().trimEnd()
    }
}
