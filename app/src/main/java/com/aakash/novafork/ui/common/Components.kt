package com.aakash.novafork.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.common.IosProgressBar
import com.aakash.novafork.ui.model.ScanUi
import com.aakash.novafork.ui.theme.NovaColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Icon from res/drawable (Framework7 Icons, MIT: see docs/ARCHITECTURE.md for the list). */
@Composable
fun NovaIcon(
    @DrawableRes res: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(painter = painterResource(res), contentDescription = contentDescription, modifier = modifier, tint = tint)
}

/** "★ 7.8" pill. Hidden when [rating] is null or 0. */
@Composable
fun RatingPill(rating: Float?, modifier: Modifier = Modifier, onImage: Boolean = false) {
    if (rating == null || rating <= 0f) return
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (onImage) Color.Black.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(painterResource(R.drawable.ic_star), contentDescription = null, tint = NovaColors.Gold, modifier = Modifier.size(12.dp))
        Text(
            text = String.format(Locale.US, "%.1f", rating),
            style = MaterialTheme.typography.labelSmall,
            color = if (onImage) Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Progress on a card, as Apple TV draws it: a rounded bar inset from the picture's bottom edge
 * on a translucent track. Draws nothing for null/0.
 */
@Composable
fun ProgressStrip(progress: Float?, modifier: Modifier = Modifier, height: Dp = 4.dp) {
    if (progress == null || progress <= 0f) return
    Box(
        modifier
            .padding(horizontal = 8.dp, vertical = 7.dp)
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.3f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** Small round check shown on watched items. */
@Composable
fun WatchedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_check), contentDescription = "Watched", tint = NovaColors.Mint, modifier = Modifier.size(15.dp))
    }
}

/** Joins non-blank parts with " · ". */
fun metaLine(vararg parts: String?): String = parts.filterNot { it.isNullOrBlank() }.joinToString(" · ")

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (actionLabel != null && onAction != null) {
            IosTextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier
            .widthIn(max = 420.dp)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // iOS's "content unavailable" look: a large grey symbol on its own, a bold title, grey text.
        Icon(painterResource(icon), contentDescription = null, tint = IosColors.SecondaryLabel, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            IosButton(onClick = onAction) { Text(actionLabel) }
        }
        if (secondaryLabel != null && onSecondary != null) {
            IosTextButton(onClick = onSecondary) { Text(secondaryLabel) }
        }
    }
}

/** Slim progress line + label while the library scans or fetches artwork. Nothing when idle. */
@Composable
fun ScanBanner(scan: ScanUi, modifier: Modifier = Modifier) {
    if (!scan.running && !scan.fetchingArtwork) return
    Column(modifier.fillMaxWidth()) {
        val p = scan.progress
        if (p == null) {
            IosProgressBar(modifier = Modifier.fillMaxWidth().height(2.dp))
        } else {
            IosProgressBar(progress = { p }, modifier = Modifier.fillMaxWidth().height(2.dp))
        }
        if (!scan.label.isNullOrBlank()) {
            Text(
                scan.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A poster row's fling (owner: stopping card by card felt like friction): the row glides as far as
 * the flick sends it, as any list does, and comes to rest with a card lined up at the row's start
 * (after its padding) in that same one motion, never a glide and then a second snap.
 */
@Composable
fun rememberRowSnap(state: LazyListState): FlingBehavior {
    val decay = rememberSplineBasedDecay<Float>()
    return remember(state, decay) { RowGlide(state, decay) }
}

private class RowGlide(private val state: LazyListState, private val decay: DecayAnimationSpec<Float>) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val info = state.layoutInfo
        val cards = info.visibleItemsInfo
        if (cards.isEmpty() || info.totalItemsCount == 0) return initialVelocity
        // Where the flick would glide to, and the card edge nearest that.
        val glide = decay.calculateTargetValue(0f, initialVelocity)
        val stride = cards.sumOf { it.size }.toFloat() / cards.size + info.mainAxisItemSpacing
        val first = cards.first()
        val index = (first.index + ((glide - first.offset) / stride).roundToInt()).coerceIn(0, info.totalItemsCount - 1)
        val target = cards.firstOrNull { it.index == index }?.offset?.toFloat()
            ?: (first.offset + (index - first.index) * stride)
        if (abs(target) < 1f) return 0f
        // The flick's own glide, at the speed that ends it exactly there.
        val velocity = velocityFor(target)
        var moved = 0f
        var blocked = false
        AnimationState(0f, velocity).animateDecay(decay) {
            val used = scrollBy(value - moved)
            blocked = abs(value - moved - used) > 0.5f
            moved += used
            if (blocked) cancelAnimation()
        }
        if (!blocked) scrollBy(target - moved)
        return 0f
    }

    /** The fling speed whose glide covers [distance] (the glide grows with the speed). */
    private fun velocityFor(distance: Float): Float {
        val goal = abs(distance)
        var high = 1_000f
        while (abs(decay.calculateTargetValue(0f, high)) < goal && high < 1e6f) high *= 2f
        var low = 0f
        repeat(24) {
            val mid = (low + high) / 2f
            if (abs(decay.calculateTargetValue(0f, mid)) < goal) low = mid else high = mid
        }
        return if (distance < 0f) -high else high
    }
}
