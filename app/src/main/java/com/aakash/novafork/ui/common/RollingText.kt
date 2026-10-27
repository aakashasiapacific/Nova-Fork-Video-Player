package com.aakash.novafork.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
 * is not a digit crossfades. Digits are tabular so the line keeps its width. Characters are lined
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
    val tabular = style.copy(fontFeatureSettings = "tnum")
    Row(modifier.clearAndSetSemantics { this.text = AnnotatedString(text) }, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { index, char ->
            key(text.length - index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        if (initialState.isDigit() && targetState.isDigit()) {
                            val up = targetState > initialState
                            val roll = spring<IntOffset>(dampingRatio = 0.85f, stiffness = 500f)
                            (slideInVertically(roll) { h -> if (up) h else -h } + fadeIn(tween(180))) togetherWith
                                (slideOutVertically(roll) { h -> if (up) -h else h } + fadeOut(tween(140))) using
                                SizeTransform(clip = true)
                        } else {
                            fadeIn(tween(180)) togetherWith fadeOut(tween(140)) using SizeTransform(clip = true)
                        }
                    },
                    label = "rollingChar",
                ) { shown ->
                    Text(shown.toString(), style = tabular, color = color, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}
