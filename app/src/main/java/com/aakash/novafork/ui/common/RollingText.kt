package com.aakash.novafork.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset

/**
 * Text whose digits roll when they change, as iOS's numeric content transition: a digit going up
 * slides up and out while the new one comes up from below (down for a smaller one); anything that
 * is not a digit crossfades. Clocks pass equal-width digits in their style. Characters are lined
 * up from the end, so "9:59" to "10:00" rolls each place under its old one. Reads as one piece of
 * text to accessibility and tests.
 */
@Composable
fun RollingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    // Each digit is its own piece, so it can roll alone; the words between them stay whole, so
    // their letters keep their natural spacing ("m left" is one run, not six separate letters).
    val pieces = remember(text) { rollingPieces(text) }
    Row(modifier.clearAndSetSemantics { this.text = AnnotatedString(text) }, verticalAlignment = Alignment.CenterVertically) {
        pieces.forEach { (start, piece) ->
            key(text.length - start) {
                AnimatedContent(
                    targetState = piece,
                    transitionSpec = {
                        val from = initialState.singleOrNull()
                        val to = targetState.singleOrNull()
                        if (from != null && to != null && from.isDigit() && to.isDigit()) {
                            val up = to > from
                            val roll = Motion.content(IntOffset.VisibilityThreshold)
                            (slideInVertically(roll) { h -> if (up) h else -h } + fadeIn(Motion.content())) togetherWith
                                (slideOutVertically(roll) { h -> if (up) -h else h } + fadeOut(Motion.press())) using
                                SizeTransform(clip = true)
                        } else {
                            fadeIn(Motion.content()) togetherWith fadeOut(Motion.press()) using SizeTransform(clip = true)
                        }
                    },
                    label = "rollingChar",
                ) { shown ->
                    // The caller's own digits: a running clock passes equal-width ones in its style.
                    Text(shown, style = style, color = color, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/** [text] cut into single digits and the runs of other characters between them, each with where it starts. */
internal fun rollingPieces(text: String): List<Pair<Int, String>> {
    val pieces = ArrayList<Pair<Int, String>>()
    var runStart = -1
    text.forEachIndexed { index, char ->
        if (char.isDigit()) {
            if (runStart >= 0) pieces.add(runStart to text.substring(runStart, index))
            runStart = -1
            pieces.add(index to char.toString())
        } else if (runStart < 0) {
            runStart = index
        }
    }
    if (runStart >= 0) pieces.add(runStart to text.substring(runStart))
    return pieces
}
