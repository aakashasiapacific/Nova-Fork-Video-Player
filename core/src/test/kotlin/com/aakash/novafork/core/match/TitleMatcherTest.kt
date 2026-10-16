package com.aakash.novafork.core.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleMatcherTest {

    private data class Candidate(
        val id: Int,
        val title: String,
        val original: String? = null,
        val year: Int? = null,
        val popularity: Double = 0.0,
    )

    private fun best(query: String, year: Int?, vararg candidates: Candidate): Candidate? =
        TitleMatcher.best(
            query = query,
            year = year,
            candidates = candidates.toList(),
            title = { it.title },
            originalTitle = { it.original },
            candidateYear = { it.year },
            popularity = { it.popularity },
        )

    @Test
    fun normalizeLowercasesAndStripsPunctuation() {
        assertEquals("spider man no way home", TitleMatcher.normalize("Spider-Man: No Way Home"))
        assertEquals("dune part two", TitleMatcher.normalize("Dune: Part Two"))
        assertEquals("oceans eleven", TitleMatcher.normalize("Ocean's Eleven"))
        assertEquals("oceans eleven", TitleMatcher.normalize("Ocean’s   Eleven!"))
    }

    @Test
    fun normalizeStripsDiacritics() {
        assertEquals("amelie", TitleMatcher.normalize("Amélie"))
        assertEquals("pokemon detective pikachu", TitleMatcher.normalize("Pokémon Detective Pikachu"))
        assertEquals("les miserables", TitleMatcher.normalize("Les Misérables"))
        assertEquals("strasse", TitleMatcher.normalize("Straße"))
        assertEquals("smorrebrod", TitleMatcher.normalize("Smørrebrød"))
    }

    @Test
    fun normalizeTurnsAmpersandIntoAnd() {
        assertEquals("fast and furious", TitleMatcher.normalize("Fast & Furious"))
        assertEquals("mr and mrs smith", TitleMatcher.normalize("Mr. & Mrs. Smith"))
    }

    @Test
    fun normalizeDropsOneLeadingArticle() {
        assertEquals("matrix", TitleMatcher.normalize("The Matrix"))
        assertEquals("quiet place", TitleMatcher.normalize("  A   Quiet  Place "))
        assertEquals("american werewolf in london", TitleMatcher.normalize("An American Werewolf in London"))
        assertEquals("a team", TitleMatcher.normalize("The A-Team"))
        assertEquals("the", TitleMatcher.normalize("The"))
        assertEquals("theory of everything", TitleMatcher.normalize("The Theory of Everything"))
    }

    @Test
    fun normalizeJoinsInitials() {
        assertEquals("swat", TitleMatcher.normalize("S.W.A.T."))
        assertEquals("et the extra terrestrial", TitleMatcher.normalize("E.T. the Extra-Terrestrial"))
        assertEquals("ai artificial intelligence", TitleMatcher.normalize("A.I. Artificial Intelligence"))
    }

    @Test
    fun normalizeKeepsNonLatinScripts() {
        assertEquals("千と千尋の神隠し", TitleMatcher.normalize("千と千尋の神隠し"))
        assertEquals("", TitleMatcher.normalize("  ...  "))
    }

    @Test
    fun similarityIsOneForEqualNormalizedTitles() {
        assertEquals(1.0, TitleMatcher.similarity("Dune Part Two", "Dune: Part Two"), 0.0)
        assertEquals(1.0, TitleMatcher.similarity("The Matrix", "matrix"), 0.0)
        assertEquals(1.0, TitleMatcher.similarity("SWAT", "S.W.A.T."), 0.0)
    }

    @Test
    fun similarityToleratesSmallDifferences() {
        assertTrue(TitleMatcher.similarity("Spiderman", "Spider-Man") >= 0.85)
        assertTrue(TitleMatcher.similarity("The Office US", "The Office") >= 0.6)
        assertTrue(TitleMatcher.similarity("Harry Potter and the Philosophers Stone", "Harry Potter and the Sorcerer's Stone") >= 0.6)
    }

    @Test
    fun similarityUsesTokenSetsForReorderedWords() {
        assertEquals(1.0, TitleMatcher.similarity("Stone Philosophers", "Philosophers Stone"), 1e-9)
    }

    @Test
    fun similarityIsLowForDifferentTitles() {
        assertTrue(TitleMatcher.similarity("Inception", "Interstellar") < 0.6)
        assertTrue(TitleMatcher.similarity("The Office", "Parks and Recreation") < 0.3)
        assertEquals(0.0, TitleMatcher.similarity("", "Inception"), 0.0)
    }

    @Test
    fun similarityIsSymmetricAndBounded() {
        val pairs = listOf("Alien" to "Aliens", "Up" to "Us", "Toy Story 3" to "Toy Story", "Heat" to "Heathers")
        for ((a, b) in pairs) {
            val ab = TitleMatcher.similarity(a, b)
            assertEquals(ab, TitleMatcher.similarity(b, a), 1e-12)
            assertTrue(ab in 0.0..1.0)
        }
    }

    @Test
    fun bestPrefersTheMatchingYear() {
        val remake = Candidate(1, "Dune", year = 2021, popularity = 300.0)
        val original = Candidate(2, "Dune", year = 1984, popularity = 40.0)
        assertEquals(original, best("Dune", 1984, remake, original))
        assertEquals(remake, best("Dune", 2021, remake, original))
    }

    @Test
    fun bestAllowsAYearOffByOne() {
        val festivalYear = Candidate(1, "Some Film", year = 2018)
        val other = Candidate(2, "Some Film", year = 2005, popularity = 90.0)
        assertEquals(festivalYear, best("Some Film", 2019, festivalYear, other))
    }

    @Test
    fun bestUsesPopularityAsTiebreakWithoutYear() {
        val obscure = Candidate(1, "The Office", year = 2001, popularity = 20.0)
        val famous = Candidate(2, "The Office", year = 2005, popularity = 250.0)
        assertEquals(famous, best("The Office", null, obscure, famous))
    }

    @Test
    fun popularityDoesNotBeatABetterTitle() {
        val exact = Candidate(1, "Heat", year = 1995, popularity = 1.0)
        val popular = Candidate(2, "Heathers", year = 1995, popularity = 5000.0)
        assertEquals(exact, best("Heat", null, popular, exact))
    }

    @Test
    fun bestMatchesTheOriginalTitle() {
        val spiritedAway = Candidate(1, "Spirited Away", original = "千と千尋の神隠し", year = 2001)
        assertEquals(spiritedAway, best("千と千尋の神隠し", 2001, spiritedAway))
        val amelie = Candidate(2, "Amélie", original = "Le Fabuleux Destin d'Amélie Poulain", year = 2001)
        assertEquals(amelie, best("Le Fabuleux Destin d Amelie Poulain", null, amelie))
    }

    @Test
    fun bestRejectsWeakMatches() {
        assertNull(best("Inception", 2010, Candidate(1, "Interstellar", year = 2014), Candidate(2, "Insomnia", year = 2002)))
        assertNull(best("Inception", 2010))
        assertNull(best("   ", null, Candidate(1, "Inception")))
    }

    @Test
    fun bestRejectsAMediocreTitleWithTheWrongYear() {
        // Similar enough on its own (≈0.67), but an 18-year gap leaves too little confidence.
        assertNull(best("The Office US", 2019, Candidate(1, "The Office", year = 2001)))
        assertEquals(1, best("The Office US", null, Candidate(1, "The Office", year = 2001))?.id)
    }

    @Test
    fun bestKeepsAnExactTitleEvenWhenTheYearIsWrong() {
        assertEquals(7, best("Inception", 2012, Candidate(7, "Inception", year = 2010))?.id)
    }
}
