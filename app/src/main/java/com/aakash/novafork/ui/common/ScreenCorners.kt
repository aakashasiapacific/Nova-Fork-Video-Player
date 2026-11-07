package com.aakash.novafork.ui.common

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The phone screen's own corner radius (Android 12 and later report it), so shapes near the edge
 * can follow it, as iOS 27's concentric corners do; [fallback] where it is not known.
 */
@Composable
fun rememberScreenCornerRadius(fallback: Dp = ScreenCornerFallback): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(view, density) {
        val radiusPx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0
        } else {
            0
        }
        if (radiusPx > 0) with(density) { radiusPx.toDp() } else fallback
    }
}

/**
 * The corner for a shape [inset] in from the screen's edge, concentric with the screen's corner
 * (its radius less the gap), never under [minimum].
 */
@Composable
fun rememberConcentricCorner(inset: Dp, minimum: Dp = 12.dp): Dp =
    (rememberScreenCornerRadius() - inset).coerceAtLeast(minimum)

private val ScreenCornerFallback = 40.dp
