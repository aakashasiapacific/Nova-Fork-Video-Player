package com.aakash.novafork.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * iOS's scroll indicator on a long grid: a thin grey bar at the right edge, sized to how much of
 * the list shows and placed where it is, that appears while the grid moves and fades a moment
 * after. Short lists (under three screens) don't get one. It stays clear of the bars below.
 */
@Composable
fun Modifier.iosScrollIndicator(state: LazyGridState): Modifier {
    var showing by remember { mutableStateOf(false) }
    val moving = state.isScrollInProgress
    LaunchedEffect(moving) {
        if (moving) {
            showing = true
        } else {
            delay(INDICATOR_LINGER_MS)
            showing = false
        }
    }
    val alpha by animateFloatAsState(if (showing) 1f else 0f, tween(if (showing) 120 else 300), label = "scrollIndicator")
    val bottomSpace = LocalBottomBarSpace.current
    return drawWithContent {
        drawContent()
        if (alpha <= 0f) return@drawWithContent
        val info = state.layoutInfo
        val total = info.totalItemsCount
        val visible = info.visibleItemsInfo.size
        if (total == 0 || visible == 0 || total < visible * 3) return@drawWithContent
        val top = 4.dp.toPx()
        val track = size.height - top - bottomSpace.toPx() - 4.dp.toPx()
        if (track <= 0f) return@drawWithContent
        val thumb = (track * visible / total).coerceAtLeast(36.dp.toPx())
        val first = state.firstVisibleItemIndex.toFloat()
        val progress = (first / (total - visible).coerceAtLeast(1)).coerceIn(0f, 1f)
        val width = 3.dp.toPx()
        drawRoundRect(
            color = Color.White.copy(alpha = 0.45f * alpha),
            topLeft = Offset(size.width - width - 3.dp.toPx(), top + (track - thumb) * progress),
            size = Size(width, thumb),
            cornerRadius = CornerRadius(width / 2f),
        )
    }
}

private const val INDICATOR_LINGER_MS = 700L
