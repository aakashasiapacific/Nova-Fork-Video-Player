package com.aakash.novafork.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.theme.NovaColors
import com.aakash.novafork.ui.theme.NovaMotion
import com.aakash.novafork.ui.theme.NovaRadius
import com.aakash.novafork.ui.theme.NovaType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Thin capsule progress: white fill on white 20%. Continue Watching cards draw it 3dp tall INSIDE
 * the artwork, 6dp from the bottom with 12dp side insets; details use it under the Resume button.
 * Fills the available width unless the modifier sets one.
 */
@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 3.dp,
    trackColor: Color = NovaColors.ProgressTrack,
    color: Color = Color.White,
) {
    val p = progress.coerceIn(0f, 1f)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(trackColor, cornerRadius = radius)
        if (p > 0f) {
            drawRoundRect(color, size = Size(max(size.width * p, size.height), size.height), cornerRadius = radius)
        }
    }
}

/**
 * The iOS activity indicator: eight rounded spokes, the brightest one stepping clockwise.
 * 20dp is iOS "medium"; use 36dp for "large". With [revealFraction] (0–1) it does not spin and
 * shows that share of the spokes, as iOS does while you pull to refresh.
 */
@Composable
fun ActivityIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    color: Color = NovaColors.secondaryLabel,
    revealFraction: Float? = null,
) {
    val base = modifier
        .size(size)
        .semantics { contentDescription = "Loading" }
    if (revealFraction != null) {
        val visible = ceil(revealFraction.coerceIn(0f, 1f) * SPOKES).toInt()
        Canvas(base) { drawSpokes(color, head = -1, visible = visible) }
    } else {
        val transition = rememberInfiniteTransition(label = "activity")
        val step = transition.animateFloat(
            initialValue = 0f,
            targetValue = SPOKES.toFloat(),
            animationSpec = infiniteRepeatable(tween(durationMillis = 800, easing = LinearEasing)),
            label = "spoke",
        )
        Canvas(base) { drawSpokes(color, head = step.value.toInt() % SPOKES, visible = SPOKES) }
    }
}

private const val SPOKES = 8

/** [head] = index of the brightest spoke, or -1 for evenly lit spokes. */
private fun DrawScope.drawSpokes(color: Color, head: Int, visible: Int) {
    val r = size.minDimension / 2f
    val width = r * 0.22f
    val inner = r * 0.46f
    val outer = r - width / 2f
    for (i in 0 until visible.coerceAtMost(SPOKES)) {
        val fade = if (head < 0) {
            0.85f
        } else {
            val age = ((head - i) % SPOKES + SPOKES) % SPOKES
            (1f - age * 0.11f).coerceAtLeast(0.22f)
        }
        val angle = Math.toRadians(i * 45.0 - 90.0)
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        drawLine(
            color = color.copy(alpha = color.alpha * fade),
            start = center + Offset(dx * inner, dy * inner),
            end = center + Offset(dx * outer, dy * outer),
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
    }
}

/** Pull distance that triggers a refresh in material3's PullToRefreshBox (its default threshold). */
private val RefreshThreshold = 80.dp

/**
 * iOS-style pull-to-refresh indicator for material3 PullToRefreshBox(indicator = …): a spinner
 * that fills in spoke by spoke as you pull, spins while refreshing and slides away after.
 * Place it with Modifier.align(Alignment.TopCenter) plus any top padding (status bar, header).
 */
@Composable
fun NovaRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    color: Color = NovaColors.secondaryLabel,
) {
    Box(
        modifier
            .size(40.dp)
            .graphicsLayer {
                val f = if (isRefreshing) 1f else state.distanceFraction
                translationY = f.coerceIn(0f, 1.6f) * RefreshThreshold.toPx() - size.height
                alpha = if (isRefreshing) 1f else ((f - 0.15f) / 0.5f).coerceIn(0f, 1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isRefreshing) {
            ActivityIndicator(size = 24.dp, color = color)
        } else {
            ActivityIndicator(size = 24.dp, color = color, revealFraction = state.distanceFraction)
        }
    }
}

/**
 * material3 PullToRefreshBox with [NovaRefreshIndicator]. [indicatorPadding] moves the spinner
 * below a status bar or header. Pass only the "files are being scanned" flag as [isRefreshing]
 * (ScanUi.running), never artwork fetching.
 */
@Composable
fun NovaPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    indicatorPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            NovaRefreshIndicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(indicatorPadding),
            )
        },
        content = content,
    )
}

/** Hero page dots: 6dp (iPad 7dp) circles, white and white 35%. Nothing for one page. */
@Composable
fun PageDots(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier,
    dotSize: Dp = LocalNovaDimens.current.pageDotSize,
    activeColor: Color = Color.White,
    inactiveColor: Color = NovaColors.DotInactive,
) {
    if (count <= 1) return
    Row(
        modifier.semantics { contentDescription = "Page ${current + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(dotSize + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val color by animateColorAsState(
                if (index == current) activeColor else inactiveColor,
                NovaMotion.fade(),
                label = "dot",
            )
            Box(
                Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun BadgeFrame(modifier: Modifier, color: Color, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .border(1.dp, color, RoundedCornerShape(NovaRadius.Badge))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Outlined 4dp-corner box with Caption 2 semibold text: "4K", "HD", "CC", "PG-13". */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier, color: Color = NovaColors.secondaryLabel) {
    BadgeFrame(modifier, color) {
        Text(text = text, style = NovaType.caption2Semibold, color = color, maxLines = 1)
    }
}

/** "★ 8.2" badge. Draws nothing for null, NaN or 0. */
@Composable
fun RatingBadge(rating: Float?, modifier: Modifier = Modifier, color: Color = NovaColors.secondaryLabel) {
    if (rating == null || rating.isNaN() || rating <= 0f) return
    BadgeFrame(modifier, color) {
        Icon(NovaGlyphs.Star, contentDescription = null, modifier = Modifier.size(9.dp), tint = color)
        Spacer(Modifier.width(2.dp))
        Text(
            text = String.format(Locale.US, "%.1f", rating),
            style = NovaType.caption2Semibold,
            color = color,
            maxLines = 1,
        )
    }
}

/**
 * Player gesture HUD (volume, brightness, seek): a small Clear glass capsule with a white [icon]
 * and a thin bar for [fraction] (0–1) and/or a short [text] ("+10 s", "1:02:15"). Over video only;
 * centre it near the top of the player and fade it in/out yourself.
 */
@Composable
fun GlassHud(
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    fraction: Float? = null,
    text: String? = null,
) {
    Row(
        modifier
            .height(44.dp)
            .glass(CircleShape, style = GlassStyle.Clear, elevation = 8.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
        }
        if (fraction != null) {
            ProgressBar(
                progress = fraction,
                modifier = Modifier
                    .width(120.dp)
                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f) },
                height = 4.dp,
                trackColor = Color(0x4DFFFFFF),
            )
        }
        if (text != null) {
            Text(text = text, style = NovaType.monoSubhead.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1)
        }
    }
}

/**
 * A circular countdown: a thin ring that fills clockwise from the top as [progress] goes 0 → 1,
 * around [content] (usually PlayGlyph). The "Up Next" card's play button.
 */
@Composable
fun CountdownRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    strokeWidth: Dp = 3.dp,
    color: Color = Color.White,
    trackColor: Color = Color(0x4DFFFFFF),
    content: @Composable BoxScope.() -> Unit = {},
) {
    val p = progress.coerceIn(0f, 1f)
    Box(
        modifier
            .size(size)
            .drawBehind {
                val w = strokeWidth.toPx()
                val inset = w / 2f
                val arcSize = Size(this.size.width - w, this.size.height - w)
                val stroke = Stroke(width = w, cap = StrokeCap.Round)
                drawArc(trackColor, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = stroke)
                if (p > 0f) {
                    drawArc(color, -90f, 360f * p, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = stroke)
                }
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * The replacement for Snackbar: a glass capsule with Subhead semibold text (and an optional
 * [icon]) that slides in, stays [durationMs], slides out and then calls [onDismissed] — consume
 * the one-shot message there (AppActions.consumeMessage). A new [message] replaces the visible one
 * and restarts the timer; tapping the capsule dismisses it early. Align it yourself: bottom-centre
 * above glassTabBarClearance() on phone, bottom-centre with 24dp margin on iPad.
 */
@Composable
fun GlassToast(
    message: String?,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    durationMs: Long = 3_000L,
    glass: GlassState? = LocalGlassState.current,
) {
    val visible = remember { MutableTransitionState(false) }
    var shown by remember { mutableStateOf("") }
    val dismissed by rememberUpdatedState(onDismissed)
    LaunchedEffect(message) {
        if (message == null) {
            visible.targetState = false
            return@LaunchedEffect
        }
        shown = message
        visible.targetState = true
        // Wait for the timer, or for a tap (which hides the toast itself).
        withTimeoutOrNull(durationMs) { snapshotFlow { visible.targetState }.first { !it } }
        visible.targetState = false
        snapshotFlow { visible.isIdle }.first { it }
        dismissed()
    }
    AnimatedVisibility(
        visibleState = visible,
        modifier = modifier,
        enter = slideInVertically(NovaMotion.standard()) { it / 2 } + fadeIn(NovaMotion.fade(NovaMotion.FastMs)),
        exit = slideOutVertically(tween(200)) { it / 2 } + fadeOut(tween(160)),
    ) {
        Row(
            Modifier
                .novaClickable(onClickLabel = "Dismiss") { visible.targetState = false }
                .widthIn(max = 560.dp)
                .heightIn(min = 44.dp)
                .glass(CircleShape, glass, elevation = 12.dp)
                .padding(horizontal = 20.dp, vertical = 11.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp), tint = NovaColors.label)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = shown,
                style = NovaType.subheadSemibold,
                color = NovaColors.label,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
