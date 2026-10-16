package com.aakash.novafork.core.library

/**
 * File and folder names in the order people expect: runs of digits compare as numbers
 * ("Episode 2" before "Episode 10", "S01E02" before "S1E10") and letters ignore case.
 *
 * Names that only differ in case or leading zeros ("Part 02" / "part 2") still get a fixed order
 * (plain string order breaks the tie), so a sort comes out the same every time and the comparator
 * is consistent with equals.
 */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        val natural = compareNatural(a, b)
        return if (natural != 0) natural else a.compareTo(b)
    }

    private fun compareNatural(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            if (a[i].isAsciiDigit() && b[j].isAsciiDigit()) {
                // Numbers of any length: without leading zeros, the longer one is larger, and
                // equally long ones compare digit by digit.
                while (i < a.length && a[i] == '0') i++
                while (j < b.length && b[j] == '0') j++
                val startA = i
                val startB = j
                while (i < a.length && a[i].isAsciiDigit()) i++
                while (j < b.length && b[j].isAsciiDigit()) j++
                val lengthA = i - startA
                val lengthB = j - startB
                if (lengthA != lengthB) return lengthA.compareTo(lengthB)
                for (k in 0 until lengthA) {
                    val byDigit = a[startA + k].compareTo(b[startB + k])
                    if (byDigit != 0) return byDigit
                }
            } else {
                val byChar = a[i].lowercaseChar().compareTo(b[j].lowercaseChar())
                if (byChar != 0) return byChar
                i++
                j++
            }
        }
        // One is a prefix of the other (in this order): the shorter comes first.
        return (a.length - i).compareTo(b.length - j)
    }

    private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
}
