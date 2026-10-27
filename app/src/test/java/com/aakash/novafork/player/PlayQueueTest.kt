package com.aakash.novafork.player

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayQueueTest {

    private val ids = listOf(10L, 20L, 30L, 40L, 50L)

    @Test
    fun `plays in the queued order from the start index`() {
        val queue = PlayQueue()
        queue.set(ids, startIndex = 2, shuffle = false)

        assertEquals(ids, queue.playOrder)
        assertEquals(2, queue.position)
        assertEquals(30L, queue.currentId)
        assertEquals(3, queue.nextPosition(wrap = false))
        assertEquals(1, queue.previousPosition(wrap = false))
    }

    @Test
    fun `start index out of range is clamped`() {
        val queue = PlayQueue()
        queue.set(ids, startIndex = 99, shuffle = false)
        assertEquals(50L, queue.currentId)

        queue.set(ids, startIndex = -3, shuffle = false)
        assertEquals(10L, queue.currentId)
    }

    @Test
    fun `the ends stop without wrap and go round with it`() {
        val queue = PlayQueue()
        queue.set(ids, startIndex = 4, shuffle = false)
        assertNull(queue.nextPosition(wrap = false))
        assertEquals(0, queue.nextPosition(wrap = true))

        queue.set(ids, startIndex = 0, shuffle = false)
        assertNull(queue.previousPosition(wrap = false))
        assertEquals(4, queue.previousPosition(wrap = true))
    }

    @Test
    fun `shuffled queue starts with the chosen video and keeps every video once`() {
        val queue = PlayQueue(Random(7))
        queue.set(ids, startIndex = 3, shuffle = true)

        assertTrue(queue.shuffled)
        assertEquals(0, queue.position)
        assertEquals(40L, queue.currentId)
        assertEquals(ids.sorted(), queue.playOrder.sorted())
    }

    @Test
    fun `turning shuffle on and off keeps the playing video`() {
        val queue = PlayQueue(Random(3))
        queue.set(ids, startIndex = 1, shuffle = false)
        queue.moveTo(3)
        assertEquals(40L, queue.currentId)

        queue.setShuffled(true)
        assertEquals(0, queue.position)
        assertEquals(40L, queue.currentId)
        assertEquals(ids.sorted(), queue.playOrder.sorted())

        queue.setShuffled(false)
        assertFalse(queue.shuffled)
        assertEquals(ids, queue.playOrder)
        assertEquals(3, queue.position)
        assertEquals(40L, queue.currentId)
    }

    @Test
    fun `moveTo rejects positions outside the queue`() {
        val queue = PlayQueue()
        queue.set(ids, startIndex = 0, shuffle = false)

        assertNull(queue.moveTo(5))
        assertNull(queue.moveTo(-1))
        assertEquals(0, queue.position)
        assertEquals(20L, queue.moveTo(1))
        assertEquals(20L, queue.idAt(1))
    }

    @Test
    fun `removing gone videos keeps the playing one and its place`() {
        val queue = PlayQueue()
        queue.set(ids, startIndex = 2, shuffle = false)

        queue.remove(setOf(10L, 30L, 50L))

        // The playing video stays even when it is gone: it shows its own error.
        assertEquals(listOf(20L, 30L, 40L), queue.playOrder)
        assertEquals(30L, queue.currentId)
        assertEquals(1, queue.position)
    }

    @Test
    fun `removing from a shuffled queue keeps the play order of the rest`() {
        val queue = PlayQueue(Random(11))
        queue.set(ids, startIndex = 0, shuffle = true)
        val before = queue.playOrder

        queue.remove(setOf(20L, 40L))

        assertEquals(before.filter { it != 20L && it != 40L }, queue.playOrder)
        assertEquals(10L, queue.currentId)
    }

    @Test
    fun `an empty queue has nothing to play`() {
        val queue = PlayQueue()
        queue.set(emptyList(), startIndex = 0, shuffle = true)

        assertTrue(queue.isEmpty)
        assertNull(queue.currentId)
        assertNull(queue.nextPosition(wrap = true))
        assertNull(queue.previousPosition(wrap = true))
        queue.setShuffled(false)
        queue.remove(setOf(1L))
        assertEquals(0, queue.size)
    }

    @Test
    fun `clear empties the queue`() {
        val queue = PlayQueue()
        queue.set(ids, startIndex = 2, shuffle = false)
        queue.clear()

        assertTrue(queue.isEmpty)
        assertEquals(0, queue.position)
        assertNull(queue.currentId)
    }

    @Test
    fun `play next goes right after the playing item and play last at the end`() {
        val queue = PlayQueue()
        queue.set(ids, startIndex = 1, shuffle = false)

        queue.insert(listOf(60L, 70L), next = true)
        assertEquals(listOf(10L, 20L, 60L, 70L, 30L, 40L, 50L), queue.playOrder)
        assertEquals(20L, queue.currentId)

        queue.insert(listOf(80L), next = false)
        assertEquals(80L, queue.playOrder.last())
        assertEquals(listOf(60L, 70L, 30L), queue.upNext(limit = 3))
        assertEquals(2, queue.nextPosition(wrap = false))
    }

    @Test
    fun `inserting into an empty queue starts it`() {
        val queue = PlayQueue()
        queue.insert(listOf(5L, 6L), next = true)

        assertEquals(5L, queue.currentId)
        assertEquals(listOf(6L), queue.upNext(limit = 10))
    }

    @Test
    fun `inserted items survive shuffle on and off`() {
        val queue = PlayQueue(Random(5))
        queue.set(ids, startIndex = 0, shuffle = true)
        queue.insert(listOf(99L), next = true)
        assertEquals(99L, queue.upNext(limit = 1).single())

        queue.setShuffled(false)
        assertEquals(ids + 99L, queue.playOrder)
    }

    @Test
    fun `up next can be reordered but not the playing item or what came before`() {
        val queue = PlayQueue()
        queue.set(ids, startIndex = 1, shuffle = false)

        queue.move(from = 4, to = 2)
        assertEquals(listOf(10L, 20L, 50L, 30L, 40L), queue.playOrder)
        assertEquals(20L, queue.currentId)

        queue.move(from = 1, to = 3)
        queue.move(from = 0, to = 2)
        queue.move(from = 2, to = 9)
        assertEquals(listOf(10L, 20L, 50L, 30L, 40L), queue.playOrder)
        assertEquals(listOf(50L, 30L, 40L), queue.upNext(limit = 5))
    }
}
