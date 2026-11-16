package com.aakash.novafork.data.music

import com.aakash.novafork.data.db.SongEntity
import kotlin.math.abs
import kotlin.random.Random

/** Songs "like" others, from the library itself: Play Similar and the music Autoplay (unit-tested). */
object SimilarSongs {
    /** Years apart that still count as the same era. */
    private const val ERA_YEARS = 5

    /**
     * Songs from [library] like [seeds] (owner): the same artist scores most, then the same genre,
     * then one released within five years of a seed. Each is scored and shuffled a little, never one
     * of [exclude], at most [limit]; songs that match nothing are left out.
     */
    fun pick(
        seeds: List<SongEntity>,
        library: List<SongEntity>,
        exclude: Set<Long>,
        limit: Int,
        random: Random = Random.Default,
    ): List<SongEntity> {
        val artists = seeds.map { it.artistKey }.toSet()
        val genres = seeds.mapNotNull { it.genre?.lowercase()?.takeIf { g -> g.isNotBlank() } }.toSet()
        val years = seeds.mapNotNull { it.year }.toSet()
        return library
            .asSequence()
            .filter { it.id !in exclude && it.present }
            .map { song ->
                var score = 0
                if (song.artistKey in artists) score += 3
                if (song.genre?.lowercase() in genres) score += 2
                if (song.year != null && years.any { abs(song.year - it) <= ERA_YEARS }) score += 1
                song to score
            }
            .filter { it.second > 0 }
            .shuffled(random)
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
            .toList()
    }
}
