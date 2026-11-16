package com.aakash.novafork

import androidx.compose.ui.text.font.FontWeight
import com.aakash.novafork.ui.common.highlightMatches
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHighlightTest {

    private fun boldParts(text: String, query: String): List<String> {
        val highlighted = highlightMatches(text, query)
        return highlighted.spanStyles
            .filter { it.item.fontWeight == FontWeight.Bold }
            .map { highlighted.text.substring(it.start, it.end) }
    }

    @Test
    fun `every word typed is bold wherever it appears, whatever its case`() {
        assertEquals(listOf("Dune", "Two"), boldParts("Dune: Part Two", "two dune"))
        assertEquals(listOf("Pa", "papa"), boldParts("Parasite papa", "pa"))
    }

    @Test
    fun `nothing typed leaves the title plain`() {
        assertEquals(emptyList<String>(), boldParts("Spirited Away", "  "))
        assertEquals("Spirited Away", highlightMatches("Spirited Away", "").text)
    }
}
