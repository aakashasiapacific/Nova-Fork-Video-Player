package com.aakash.novafork.data.music

import com.aakash.novafork.data.db.SongEntity
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarSongsTest {

    private fun song(id: Long, artistKey: String = "a$id", genre: String? = null, year: Int? = null, present: Boolean = true) = SongEntity(
        id = id,
        sourceId = 1,
        uri = "content://song/$id",
        fileName = "$id.mp3",
        folder = "Music",
        title = "Song $id",
        albumKey = "album$id",
        artistKey = artistKey,
        genre = genre,
        year = year,
        present = present,
    )

    private val seeds = listOf(song(1, artistKey = "queen", genre = "Rock", year = 1980))

    @Test
    fun `same artist ranks above same genre above the same era`() {
        val sameArtist = song(2, artistKey = "queen")
        val sameGenre = song(3, genre = "rock")
        val sameEra = song(4, year = 1984)
        val library = listOf(sameEra, sameGenre, sameArtist)

        val picked = SimilarSongs.pick(seeds, library, exclude = emptySet(), limit = 10, random = Random(1))

        assertEquals(listOf(2L, 3L, 4L), picked.map { it.id })
    }

    @Test
    fun `songs that match nothing are left out`() {
        val library = listOf(song(2, genre = "Jazz", year = 1999), song(3, artistKey = "queen"))

        val picked = SimilarSongs.pick(seeds, library, exclude = emptySet(), limit = 10)

        assertEquals(listOf(3L), picked.map { it.id })
    }

    @Test
    fun `excluded and missing songs are never picked`() {
        val library = listOf(
            song(2, artistKey = "queen"),
            song(3, artistKey = "queen"),
            song(4, artistKey = "queen", present = false),
        )

        val picked = SimilarSongs.pick(seeds, library, exclude = setOf(2L), limit = 10)

        assertEquals(listOf(3L), picked.map { it.id })
    }

    @Test
    fun `at most the limit is picked, best first`() {
        val library = (2L..30L).map { song(it, artistKey = "queen") } + (31L..40L).map { song(it, genre = "Rock") }

        val picked = SimilarSongs.pick(seeds, library, exclude = emptySet(), limit = 20, random = Random(7))

        assertEquals(20, picked.size)
        assertTrue(picked.all { it.artistKey == "queen" })
        assertEquals(picked.size, picked.map { it.id }.toSet().size)
    }

    @Test
    fun `an era is within five years of any seed`() {
        val twoSeeds = listOf(song(1, year = 1970), song(2, year = 2000))
        val library = listOf(song(3, year = 2004), song(4, year = 1985))

        val picked = SimilarSongs.pick(twoSeeds, library, exclude = setOf(1L, 2L), limit = 10)

        assertEquals(listOf(3L), picked.map { it.id })
    }

    @Test
    fun `nothing is picked without seeds or a library`() {
        assertTrue(SimilarSongs.pick(emptyList(), listOf(song(2)), exclude = emptySet(), limit = 10).isEmpty())
        assertTrue(SimilarSongs.pick(seeds, emptyList(), exclude = emptySet(), limit = 10).isEmpty())
    }
}
