package com.aakash.novafork.player

import kotlin.random.Random

/**
 * The library videos the player works through, by id. Keeps the order they were given in and
 * the order they play in (the same, or shuffled with the playing item first); positions are
 * indexes into the play order. Pure state: the player decides when to move.
 */
internal class PlayQueue(private val random: Random = Random.Default) {
    /** In the order they were queued. */
    private var ids: List<Long> = emptyList()

    /** Play order, as indexes into [ids]. */
    private var order: List<Int> = emptyList()

    /** Index into [order] of the playing item. */
    var position: Int = 0
        private set

    var shuffled: Boolean = false
        private set

    val size: Int get() = order.size

    val isEmpty: Boolean get() = order.isEmpty()

    val currentId: Long? get() = order.getOrNull(position)?.let { ids[it] }

    /** The ids in play order. */
    val playOrder: List<Long> get() = order.map { ids[it] }

    /** The id at [position] of the play order. */
    fun idAt(position: Int): Long? = order.getOrNull(position)?.let { ids[it] }

    /** Replaces the queue; [startIndex] is an index into [videoIds] and plays first. */
    fun set(videoIds: List<Long>, startIndex: Int, shuffle: Boolean) {
        ids = videoIds.toList()
        val start = if (ids.isEmpty()) 0 else startIndex.coerceIn(0, ids.lastIndex)
        shuffled = shuffle
        if (shuffle && ids.isNotEmpty()) {
            order = shuffledOrder(first = start)
            position = 0
        } else {
            order = ids.indices.toList()
            position = start
        }
    }

    fun clear() {
        ids = emptyList()
        order = emptyList()
        position = 0
    }

    /**
     * Turns shuffle on (the playing item first, the rest in random order) or off (the queued
     * order, at the playing item's place in it).
     */
    fun setShuffled(on: Boolean) {
        if (on == shuffled) return
        shuffled = on
        if (order.isEmpty()) return
        val current = order[position]
        if (on) {
            order = shuffledOrder(first = current)
            position = 0
        } else {
            order = ids.indices.toList()
            position = current
        }
    }

    /** The play-order position after the current one; with [wrap], the last one leads to the first. */
    fun nextPosition(wrap: Boolean): Int? = when {
        order.isEmpty() -> null
        position < order.lastIndex -> position + 1
        wrap -> 0
        else -> null
    }

    /** The play-order position before the current one; with [wrap], the first one leads to the last. */
    fun previousPosition(wrap: Boolean): Int? = when {
        order.isEmpty() -> null
        position > 0 -> position - 1
        wrap -> order.lastIndex
        else -> null
    }

    /** Makes [position] (of the play order) the playing item; its id, or null when out of range. */
    fun moveTo(position: Int): Long? {
        if (position !in order.indices) return null
        this.position = position
        return ids[order[position]]
    }

    /**
     * Queues [newIds] right after the playing item ([next], Apple Music's Play Next) or after
     * everything else (Play Last). The playing item keeps its place.
     */
    fun insert(newIds: List<Long>, next: Boolean) {
        if (newIds.isEmpty()) return
        val first = ids.size
        ids = ids + newIds
        val added = newIds.indices.map { first + it }
        if (order.isEmpty()) {
            order = added
            position = 0
            return
        }
        order = if (next) {
            order.subList(0, position + 1) + added + order.subList(position + 1, order.size)
        } else {
            order + added
        }
    }

    /**
     * Moves the item at play-order position [from] to [to], both after the playing item (Now
     * Playing's Up Next, reordered by dragging); anything else is left as it is.
     */
    fun move(from: Int, to: Int) {
        if (from == to || from !in order.indices || to !in order.indices || from <= position || to <= position) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** The ids queued after the playing item, in play order (at most [limit]). */
    fun upNext(limit: Int): List<Long> =
        if (order.isEmpty()) emptyList() else order.drop(position + 1).take(limit).map { ids[it] }

    /**
     * Drops [gone] videos (no longer in the library), except the playing one, which shows its
     * own error when it can't be opened.
     */
    fun remove(gone: Set<Long>) {
        if (order.isEmpty() || gone.isEmpty()) return
        val current = order[position]
        val keep = ids.indices.filter { it == current || ids[it] !in gone }
        if (keep.size == ids.size) return
        val newIndex = IntArray(ids.size) { -1 }
        keep.forEachIndexed { new, old -> newIndex[old] = new }
        ids = keep.map { ids[it] }
        order = order.mapNotNull { old -> newIndex[old].takeIf { it >= 0 } }
        position = order.indexOf(newIndex[current]).coerceAtLeast(0)
    }

    private fun shuffledOrder(first: Int): List<Int> =
        listOf(first) + ids.indices.filter { it != first }.shuffled(random)
}
