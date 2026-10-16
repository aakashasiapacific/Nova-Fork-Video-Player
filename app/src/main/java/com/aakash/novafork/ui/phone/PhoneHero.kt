package com.aakash.novafork.ui.phone

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.common.BackdropArt
import com.aakash.novafork.ui.common.HeroVideoPreview
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.RatingPill
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.HeroTarget
import com.aakash.novafork.ui.model.HeroUi
import kotlinx.coroutines.delay

/** A hero without a preview moves on after this long, like Apple TV's. */
internal const val HERO_AUTO_ADVANCE_MS = 7_000L

/** With a preview playing it stays longer, so there is time to watch. */
internal const val HERO_PREVIEW_DWELL_MS = 30_000L

/** The preview starts after the hero has been still for this long. */
internal const val HERO_PREVIEW_DELAY_MS = 2_500L

/** The dot row and the mute button sit this far above the hero's bottom edge. */
private val HeroDotsBottom = 16.dp
private val HeroContentBottom = 52.dp

/**
 * Apple TV's phone hero: full-bleed from the very top of the screen (under a clear status bar)
 * down to [height], swiped page by page. Each page centres the title, the meta line, a white
 * Play pill and a round "+" (watchlist) in its lower part; the image fades into the black page
 * below. The dots are white and grey, the active one a pill that fills as the auto-advance timer
 * runs; the mute button for previews sits at the end of their row, within thumb reach.
 *
 * [previewEnabled]: the page shown may play a muted preview of its video ([HeroUi.previewUri])
 * once it has been still for a moment ([previewAllowed] = the hero is on screen and the app in
 * front). [muted] is the mute button's state.
 */
@Composable
internal fun PhoneHeroPager(
    heroes: List<HeroUi>,
    height: Dp,
    pagerState: PagerState,
    previewEnabled: Boolean,
    previewAllowed: Boolean,
    muted: Boolean,
    onToggleMute: () -> Unit,
    actions: AppActions,
) {
    val dragged by pagerState.interactionSource.collectIsDraggedAsState()
    val settled = pagerState.settledPage
    val current = heroes.getOrNull(settled)
    val canPreview = previewEnabled && previewAllowed && current?.previewUri != null
    val dwell = if (canPreview) HERO_PREVIEW_DWELL_MS else HERO_AUTO_ADVANCE_MS
    val timer = remember { Animatable(0f) }
    // Restarts on every settle, so a manual swipe also resets the timer; the active dot fills with it.
    LaunchedEffect(pagerState, dragged, settled, heroes.size, dwell) {
        timer.snapTo(0f)
        if (dragged || heroes.size < 2) return@LaunchedEffect
        timer.animateTo(1f, tween(durationMillis = dwell.toInt(), easing = LinearEasing))
        pagerState.animateScrollToPage((pagerState.currentPage + 1) % heroes.size)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val hero = heroes.getOrNull(page)
            if (hero != null) {
                PhoneHeroPage(
                    hero = hero,
                    preview = canPreview && page == settled && !dragged,
                    muted = muted,
                    actions = actions,
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, bottom = HeroDotsBottom),
        ) {
            if (heroes.size > 1) {
                PagerDots(
                    count = heroes.size,
                    current = pagerState.currentPage,
                    fill = timer.value,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            AnimatedVisibility(
                visible = previewEnabled && current?.previewUri != null,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                MuteButton(muted = muted, onClick = onToggleMute)
            }
        }
    }
}

@Composable
private fun PhoneHeroPage(hero: HeroUi, preview: Boolean, muted: Boolean, actions: AppActions) {
    val openTarget: () -> Unit = {
        when (val target = hero.target) {
            is HeroTarget.Video -> actions.openVideo(target.id)
            is HeroTarget.Show -> actions.openShow(target.key)
        }
    }
    val play: () -> Unit = {
        val videoId = hero.playVideoId
        if (videoId != null) actions.play(videoId) else openTarget()
    }
    val background = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(onClickLabel = "Details", onClick = openTarget),
        ) {
            BackdropArt(artwork = hero.artwork, modifier = Modifier.fillMaxSize())
            val uri = hero.previewUri
            if (preview && uri != null) {
                var started by remember(uri) { mutableStateOf(false) }
                LaunchedEffect(uri) {
                    delay(HERO_PREVIEW_DELAY_MS)
                    started = true
                }
                if (started) HeroVideoPreview(uri = uri, startMs = hero.previewStartMs, muted = muted, modifier = Modifier.fillMaxSize())
            }
            // The image meets the black page: clear at the top, black at the bottom. A layer of
            // its own, so it covers the preview's video view too.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to Color.Transparent,
                            0.78f to background.copy(alpha = 0.75f),
                            1f to background,
                        ),
                    ),
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = HeroContentBottom),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = hero.title,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val rating = hero.rating
            val meta = hero.metaLine
            if ((rating != null && rating > 0f) || !meta.isNullOrBlank()) {
                Row(
                    Modifier.padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RatingPill(rating, onImage = true)
                    if (!meta.isNullOrBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            highlightOf(hero)?.let { highlight ->
                Text(
                    text = highlight,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = play,
                    modifier = Modifier
                        .height(50.dp)
                        .widthIn(min = 180.dp, max = 260.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    contentPadding = PaddingValues(horizontal = 28.dp),
                ) {
                    NovaIcon(R.drawable.ic_play_arrow, contentDescription = null, modifier = Modifier.size(24.dp))
                    Text(
                        text = hero.playLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                IconButton(
                    onClick = { actions.toggleWatchlist(hero.itemKey) },
                    modifier = Modifier.size(50.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.18f),
                        contentColor = Color.White,
                    ),
                ) {
                    NovaIcon(
                        if (hero.onWatchlist) R.drawable.ic_check else R.drawable.ic_add,
                        contentDescription = if (hero.onWatchlist) "Remove from Watchlist" else "Add to Watchlist",
                    )
                }
            }
        }
    }
}

/** The bold line under the meta line: Apple TV's "#1 in Comedy"; ours says where you are. */
private fun highlightOf(hero: HeroUi): String? {
    val progress = hero.progress
    return when {
        progress != null && progress > 0f -> "Continue watching"
        hero.onWatchlist -> "On your Watchlist"
        else -> null
    }
}

/** White and grey only: the active dot stretches into a pill that fills as the timer runs. */
@Composable
private fun PagerDots(count: Int, current: Int, fill: Float, modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val selected = index == current
            val width by animateDpAsState(if (selected) 22.dp else 6.dp, label = "pagerDot")
            Box(
                Modifier
                    .height(6.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.35f)),
            ) {
                if (selected) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fill.coerceIn(0.15f, 1f))
                            .background(Color.White),
                    )
                }
            }
        }
    }
}

/** A small dark round glass button, level with the dots. */
@Composable
private fun MuteButton(muted: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.45f),
            contentColor = Color.White,
        ),
    ) {
        NovaIcon(
            if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_up,
            contentDescription = if (muted) "Unmute preview" else "Mute preview",
            modifier = Modifier.size(18.dp),
        )
    }
}
