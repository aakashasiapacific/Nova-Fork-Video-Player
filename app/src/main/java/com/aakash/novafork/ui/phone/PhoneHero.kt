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
internal const val HERO_AUTO_ADVANCE_MS = 7_000L

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
    // A short clip (a reel, a phone video) plays right in the hero: no poster, Play or "+".
    if (hero.previewUri != null) {
        ClipHeroPage(hero = hero, preview = preview, muted = muted)
        return
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
    val background = MaterialTheme.colorScheme.background
    val posterShape = RoundedCornerShape(10.dp)
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(
        Modifier
            .fillMaxSize()
            .clickable(onClickLabel = "Details", onClick = openTarget),
    ) {
        // The backdrop at 16:9 below the status bar (clear of the punch hole); a sliver of it
        // runs on under the status bar, darkened, so the bar isn't a blank strip.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(statusBar + maxWidth * 9f / 16f),
            ) {
                WideHeroArt(artwork = hero.artwork, modifier = Modifier.fillMaxSize(), alignment = Alignment.BottomCenter)
                Box(
                    Modifier
                        .padding(top = statusBar)
                        .fillMaxSize(),
                ) {
                    HeroClip(hero = hero, preview = preview, muted = muted)
                }
                StatusBarShade(statusBar)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.7f to Color.Transparent,
                                1f to background,
                            ),
                        ),
                )
            }
        }
        // Poster and title under the backdrop, never over its subject.
        Row(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                Modifier
                    .width(DetailsPosterWidth)
                    .aspectRatio(2f / 3f)
                    .shadow(12.dp, posterShape)
                    .clip(posterShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, Color.White.copy(alpha = 0.12f), posterShape),
            ) {
                PosterArt(artwork = hero.artwork, title = hero.title, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                HeroTitle(
                    title = hero.title,
                    logoUrl = hero.artwork.logoUrl,
                    textStyle = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Start,
                    logoAlignment = Alignment.BottomStart,
                    maxWidthFraction = 1f,
                    logoHeight = 64.dp,
                )
                val rating = hero.rating
                val meta = hero.metaLine
                if (rating != null && rating > 0f) {
                    RatingPill(rating, onImage = true, modifier = Modifier.padding(top = 8.dp))
                }
                if (!meta.isNullOrBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                highlightOf(hero)?.let { highlight ->
                    Text(
                        text = highlight,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        HeroActions(
            hero = hero,
            play = play,
            actions = actions,
            fillPlay = true,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp),
        )
    }
}

/** Darkens the status bar strip of a picture that runs under it: soft, darker at the very top. */
@Composable
private fun StatusBarShade(statusBar: Dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(statusBar * 2)
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.85f),
                    0.5f to Color.Black.copy(alpha = 0.55f),
                    1f to Color.Transparent,
                ),
            ),
    )
}

/**
 * A short clip's hero page, kept minimal: the clip (its first frame until it plays) fills the
 * hero below the status bar, with one small caption at the lower left; the mute button sits by
 * the dots. Clips rarely have real titles, so no big title, poster, Play or "+".
 */
@Composable
private fun ClipHeroPage(hero: HeroUi, preview: Boolean, muted: Boolean) {
    val background = MaterialTheme.colorScheme.background
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // The hero takes the clip's own shape (see PhoneHeroPager's pageHeight) below the status
    // bar; the frame runs on under the bar, shaded, so it isn't a blank strip.
    Box(Modifier.fillMaxSize()) {
        BackdropArt(artwork = hero.artwork, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .padding(top = statusBar)
                .fillMaxSize(),
        ) {
            HeroClip(hero = hero, preview = preview, muted = muted)
        }
        StatusBarShade(statusBar)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.75f to Color.Transparent,
                        1f to background,
                    ),
                ),
        )
        Text(
            text = listOfNotNull(hero.title, hero.metaLine?.takeIf { it.isNotBlank() }).joinToString(" · "),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 72.dp, bottom = HeroDotsBottom + 34.dp),
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

private val DetailsPosterWidth = 104.dp

/**
 * How tall the phone hero is below its 16:9 backdrop: the poster and title row, the Play row
 * and the dots. The hero's height is the status bar plus width × 9/16 plus this.
 */
internal val DetailsHeroBelowBackdrop = 300.dp

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
