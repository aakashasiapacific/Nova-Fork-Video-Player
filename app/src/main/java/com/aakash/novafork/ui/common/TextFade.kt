package com.aakash.novafork.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Cut-off text fades away at the end of its last line instead of ending in "…" (iOS 27). Pair
 * with maxLines and TextOverflow.Clip; [lines] is that maxLines, to find the last line. Text that
 * fits is left whole: [cut] says whether it was cut off (the Text's `hasVisualOverflow`).
 */
fun Modifier.fadeLastLine(lines: Int, cut: () -> Boolean): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        if (!cut()) return@drawWithContent
        val lineHeight = size.height / lines.coerceAtLeast(1)
        val top = size.height - lineHeight
        val start = size.width * 0.72f
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = start, endX = size.width),
            topLeft = Offset(start, top),
            size = Size(size.width - start, lineHeight),
            blendMode = BlendMode.DstOut,
        )
    }
