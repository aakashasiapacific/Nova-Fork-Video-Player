package com.aakash.novafork.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalOrderTest {

    private fun sorted(vararg names: String): List<String> = names.sortedWith(NaturalOrder)

    @Test
    fun numbersCompareByValue() {
        assertEquals(
            listOf("Episode 1.mkv", "Episode 2.mkv", "Episode 9.mkv", "Episode 10.mkv", "Episode 100.mkv"),
            sorted("Episode 10.mkv", "Episode 2.mkv", "Episode 100.mkv", "Episode 1.mkv", "Episode 9.mkv"),
        )
        assertTrue(NaturalOrder.compare("S01E02.mkv", "S1E10.mkv") < 0)
        assertTrue(NaturalOrder.compare("Show S02E01", "Show S1E10") > 0)
    }

    @Test
    fun lettersIgnoreCase() {
        assertEquals(listOf("apple", "Banana", "cherry"), sorted("cherry", "Banana", "apple"))
        assertTrue(NaturalOrder.compare("PART 3", "part 20") < 0)
    }

    @Test
    fun aPrefixComesFirst() {
        assertEquals(listOf("Movie", "Movie 2", "Movie 2 Extended"), sorted("Movie 2 Extended", "Movie", "Movie 2"))
    }

    @Test
    fun numbersLongerThanALongStillCompare() {
        val small = "clip 123456789012345678901234567890"
        val large = "clip 123456789012345678901234567891"
        assertTrue(NaturalOrder.compare(small, large) < 0)
        assertTrue(NaturalOrder.compare("clip 99999999999999999999", small) < 0)
    }

    @Test
    fun tiesStillHaveAFixedOrder() {
        // Same name to a person, still two different strings: never 0, and always the same way round.
        val pairs = listOf("Part 02" to "Part 2", "part 2" to "Part 2", "0" to "000", "a1b" to "a01b")
        for ((a, b) in pairs) {
            val ab = NaturalOrder.compare(a, b)
            assertTrue("$a vs $b", ab != 0)
            assertEquals("$a vs $b", -Integer.signum(ab), Integer.signum(NaturalOrder.compare(b, a)))
        }
        assertEquals(0, NaturalOrder.compare("Episode 7", "Episode 7"))
        assertEquals(sorted("Part 2", "part 2", "Part 02"), sorted("Part 02", "Part 2", "part 2"))
    }

    @Test
    fun orderIsConsistent() {
        val names = listOf(
            "", "0", "00", "1", "01", "9", "10", "a", "A", "a1", "a01", "a2", "a10", "a 1", "a-1", "a.1",
            "Episode 2", "episode 2", "Episode 02", "Episode 10", "Episode 2b", "Episode", "Z", "z9", "~", " 1",
        )
        for (a in names) {
            for (b in names) {
                val ab = Integer.signum(NaturalOrder.compare(a, b))
                assertEquals("antisymmetric: '$a' vs '$b'", -ab, Integer.signum(NaturalOrder.compare(b, a)))
                assertEquals("zero only for equal strings: '$a' vs '$b'", a == b, ab == 0)
                for (c in names) {
                    if (ab < 0 && NaturalOrder.compare(b, c) < 0) {
                        assertTrue("transitive: '$a' < '$b' < '$c'", NaturalOrder.compare(a, c) < 0)
                    }
                }
            }
        }
    }
}
