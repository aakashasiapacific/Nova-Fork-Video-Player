package com.aakash.novafork.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Preferences › Appearance › Source Colours: a small dot on posters says which source a video is on. */
val LocalSourceDots = compositionLocalOf { false }

/** The colours sources take in turn (iOS system colours). */
private val SourceColors = listOf(
    Color(0xFF0A84FF), Color(0xFFFF9F0A), Color(0xFF30D158), Color(0xFFBF5AF2),
    Color(0xFFFF375F), Color(0xFF64D2FF), Color(0xFFFFD60A), Color(0xFFAC8E68),
)

fun sourceColor(sourceId: Long): Color = SourceColors[(sourceId % SourceColors.size).toInt().let { if (it < 0) -it else it }]

@Composable
fun SourceDot(sourceId: Long, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier
            .size(9.dp)
            .background(sourceColor(sourceId), CircleShape)
            .border(1.dp, Color.Black.copy(alpha = 0.35f), CircleShape),
    )
}
