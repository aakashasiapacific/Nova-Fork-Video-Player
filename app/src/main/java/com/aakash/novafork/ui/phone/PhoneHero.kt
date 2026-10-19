package com.aakash.novafork.ui.phone

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.ArtImage
import com.aakash.novafork.ui.common.ArtPlaceholder
import com.aakash.novafork.ui.common.BackdropArt
import com.aakash.novafork.ui.common.HeroTitle
import com.aakash.novafork.ui.common.HeroVideoPreview
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.PosterArt
import com.aakash.novafork.ui.common.RatingPill
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.Artwork
import com.aakash.novafork.ui.model.HeroTarget
import com.aakash.novafork.ui.model.HeroUi
import kotlinx.coroutines.delay

/** A hero without a preview moves on after this long, like Apple TV's. */
internal const val HERO_AUTO_ADVANCE_MS = 3_500L

/** With a preview playing it stays longer, so there is time to watch. */
internal const val HERO_PREVIEW_DWELL_MS = 30_000L

/** The preview starts after the hero has been still for this long. */
internal const val HERO_PREVIEW_DELAY_MS = 2_500L

/** The dot row and the mute button sit this far above the hero's bottom edge. */
private val HeroDotsBottom = 16.dp
private val HeroContentBottom = 52.dp
private val DotsRowHeight = 44.dp

/**
 * The Home hero: from the very top of the screen (under a clear status bar) down to [height],
 * swiped page by page. On a phone each page looks like the title's own page: backdrop on top,
 * poster with title, meta line and highlight beside it, then a white Play pill and a round "+"
 * (watchlist). The dots are white and grey, the active one a pill that fills as the
 * auto-advance timer runs; the mute button for clips sits at the end of their row.
 *
 * [wide] (tablets, landscape phones): the backdrop fills the hero and the title, logo and
 * buttons sit at the lower left, as on the iPad.
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
    wide: Boolean = false,
    contentStart: Dp = 24.dp,
    /**
     * Each page's own height (a reel is taller than a film's backdrop); the hero glides between
     * them as pages change. Null: always [height].
     */
    pageHeight: ((HeroUi) -> Dp)? = null,
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
    val targetHeight = pageHeight?.let { forPage -> heroes.getOrNull(pagerState.currentPage)?.let(forPage) } ?: height
    val shownHeight by animateDpAsState(targetHeight, tween(durationMillis = 320), label = "heroHeight")
    Box(
        Modifier
            .fillMaxWidth()
            .height(shownHeight),
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val hero = heroes.getOrNull(page)
            if (hero != null) {
                PhoneHeroPage(
                    hero = hero,
                    preview = canPreview && page == settled && !dragged,
                    muted = muted,
                    actions = actions,
                    wide = wide,
                    contentStart = contentStart,
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, bottom = HeroDotsBottom)
                // One fixed row height: the dots stay put whether or not the mute button shows.
                .height(DotsRowHeight),
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
private fun PhoneHeroPage(hero: HeroUi, preview: Boolean, muted: Boolean, actions: AppActions, wide: Boolean, contentStart: Dp) {
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
    if (!wide) {
        DetailsStyleHeroPage(hero = hero, preview = preview, muted = muted, openTarget = openTarget, play = play, actions = actions)
        return
    }
    val background = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(onClickLabel = "Details", onClick = openTarget),
        ) {
            WideHeroArt(artwork = hero.artwork, modifier = Modifier.fillMaxSize())
            HeroClip(hero = hero, preview = preview, muted = muted)
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
            if (wide) {
                // The iPad's darker left side, where the title and buttons sit.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0f to background.copy(alpha = 0.7f),
                                0.45f to background.copy(alpha = 0.25f),
                                0.7f to Color.Transparent,
                            ),
                        ),
                )
            }
        }
        Column(
            if (wide) {
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = contentStart, end = 24.dp, bottom = HeroContentBottom)
                    .widthIn(max = 480.dp)
            } else {
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = contentStart, end = 24.dp, bottom = HeroContentBottom)
            },
            horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally,
        ) {
            HeroTitle(
                title = hero.title,
                logoUrl = hero.artwork.logoUrl,
                textStyle = MaterialTheme.typography.displaySmall,
                textAlign = if (wide) TextAlign.Start else TextAlign.Center,
                logoAlignment = if (wide) Alignment.BottomStart else Alignment.BottomCenter,
                maxWidthFraction = if (wide) 0.8f else 0.72f,
                logoHeight = if (wide) 110.dp else 96.dp,
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
            val overview = hero.overview
            if (wide && !overview.isNullOrBlank()) {
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            HeroActions(hero = hero, play = play, actions = actions)
        }
    }
}

/**
 * The phone hero in the style of a title's own page (the owner's pick): the landscape backdrop
 * across the top, fading into the black page, then the poster on the left with the title (its
 * logo when TMDB has one), meta line and highlight beside it, and the Play pill and "+" below.
 */
@Composable
private fun DetailsStyleHeroPage(
    hero: HeroUi,
    preview: Boolean,
    muted: Boolean,
    openTarget: () -> Unit,
    play: () -> Unit,
    actions: AppActions,
) {
    val dims = LocalNovaDimens.current
    // The same header as the title's own page, so opening it keeps everything in place.
    Column(
        Modifier
            .fillMaxSize()
            .clickable(onClickLabel = "Details", onClick = openTarget),
    ) {
        BackdropPosterHeader(artwork = hero.artwork, title = hero.title) {
            HeroTitle(
                title = hero.title,
                logoUrl = hero.artwork.logoUrl,
                textStyle = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Start,
                logoAlignment = Alignment.BottomStart,
                maxWidthFraction = 1f,
                logoHeight = 48.dp,
            )
            val meta = hero.metaLine
            if (!meta.isNullOrBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            // Rating and "Continue watching" share a line, so the block stays short.
            val rating = hero.rating
            val highlight = highlightOf(hero)
            if ((rating != null && rating > 0f) || highlight != null) {
                Row(
                    Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RatingPill(rating, onImage = true)
                    if (highlight != null) {
                        Text(
                            text = highlight,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        HeroActions(
            hero = hero,
            play = play,
            actions = actions,
            fillPlay = true,
            modifier = Modifier.padding(start = dims.gutter, end = dims.gutter, top = HeroActionsGap),
        )
    }
}

/** After a short still moment, the hero's short clip ([HeroUi.previewUri]) plays over its art. */
@Composable
private fun HeroClip(hero: HeroUi, preview: Boolean, muted: Boolean) {
    val uri = hero.previewUri
    if (preview && uri != null) {
        var started by remember(uri) { mutableStateOf(false) }
        LaunchedEffect(uri) {
            delay(HERO_PREVIEW_DELAY_MS)
            started = true
        }
        if (started) HeroVideoPreview(uri = uri, startMs = hero.previewStartMs, muted = muted, modifier = Modifier.fillMaxSize())
    }
}

private val HeroActionsGap = 18.dp
private val HeroButtonHeight = 50.dp

/** The dots sit this far under the Play row. */
private val HeroDotsGap = 26.dp

/**
 * How tall the phone hero is below its 16:9 backdrop, sized to what is in it and no more: the
 * poster's part under the backdrop, the Play row, then the dots (centred in their row, which
 * ends [HeroDotsBottom] above the hero's bottom). The hero's height is the status bar plus
 * width × 9/16 plus this.
 */
internal fun heroBelowBackdrop(posterWidth: Dp): Dp =
    posterWidth * 1.5f - PosterOverlap + HeroActionsGap + HeroButtonHeight + HeroDotsGap + DotsRowHeight / 2 + HeroDotsBottom

/**
 * The hero's buttons: the white Play pill, a round star (Favorites) and a round "+"
 * (Watchlist). [fillPlay]: Play takes the row's free width (phone); otherwise a set width.
 */
@Composable
private fun HeroActions(
    hero: HeroUi,
    play: () -> Unit,
    actions: AppActions,
    modifier: Modifier = Modifier,
    fillPlay: Boolean = false,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = play,
            modifier = (if (fillPlay) Modifier.weight(1f) else Modifier.widthIn(min = 160.dp, max = 240.dp)).height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            contentPadding = PaddingValues(horizontal = 24.dp),
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
        RoundHeroButton(
            icon = if (hero.favorite) R.drawable.ic_star else R.drawable.ic_star_outline,
            description = if (hero.favorite) "Remove from Favorites" else "Add to Favorites",
            onClick = { actions.toggleFavorite(hero.itemKey) },
        )
        RoundHeroButton(
            icon = if (hero.onWatchlist) R.drawable.ic_check else R.drawable.ic_add,
            description = if (hero.onWatchlist) "Remove from Watchlist" else "Add to Watchlist",
            onClick = { actions.toggleWatchlist(hero.itemKey) },
        )
    }
}

@Composable
private fun RoundHeroButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(50.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.White.copy(alpha = 0.18f),
            contentColor = Color.White,
        ),
    ) {
        NovaIcon(icon, contentDescription = description)
    }
}

/**
 * A wide hero's picture: the landscape backdrop (or still, or a frame of the video) fills it,
 * as on the iPad; the portrait key art is the last resort.
 */
@Composable
private fun WideHeroArt(artwork: Artwork, modifier: Modifier = Modifier, alignment: Alignment = Alignment.TopCenter) {
    val candidates = listOfNotNull(artwork.backdropUrl, artwork.stillUrl, artwork.frameSource, artwork.tallUrl, artwork.posterUrl).distinct()
    ArtImage(
        candidates = candidates,
        contentDescription = null,
        modifier = modifier,
        frameSource = artwork.frameSource,
        alignment = alignment,
    ) {
        ArtPlaceholder(seed = artwork.seed, title = null, modifier = Modifier.fillMaxSize())
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

/** Apple TV's mute button (owner's reference): a solid dark-grey circle with a white speaker, level with the dots. */
@Composable
private fun MuteButton(muted: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(42.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color(0xE62C2C2E),
            contentColor = Color.White,
        ),
    ) {
        NovaIcon(
            if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_up,
            contentDescription = if (muted) "Unmute preview" else "Mute preview",
            modifier = Modifier.size(22.dp),
        )
    }
}
