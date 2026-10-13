package com.aakash.novafork.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.DeviceLayout
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.adaptive.heroHeightFor
import com.aakash.novafork.ui.common.AboutCard
import com.aakash.novafork.ui.common.BackButton
import com.aakash.novafork.ui.common.BackdropArt
import com.aakash.novafork.ui.common.Badge
import com.aakash.novafork.ui.common.ExpandableText
import com.aakash.novafork.ui.common.GlassMenuButton
import com.aakash.novafork.ui.common.GlassMenuItem
import com.aakash.novafork.ui.common.GlassNavBar
import com.aakash.novafork.ui.common.GlassPage
import com.aakash.novafork.ui.common.GlassSheet
import com.aakash.novafork.ui.common.GlassStyle
import com.aakash.novafork.ui.common.InfoGrid
import com.aakash.novafork.ui.common.NavBarDefaults
import com.aakash.novafork.ui.common.PrimaryCapsuleButton
import com.aakash.novafork.ui.common.ProgressBar
import com.aakash.novafork.ui.common.RatingBadge
import com.aakash.novafork.ui.common.RowHeader
import com.aakash.novafork.ui.common.SheetDetent
import com.aakash.novafork.ui.common.SmallCapsText
import com.aakash.novafork.ui.common.TextAction
import com.aakash.novafork.ui.common.bottomScrim
import com.aakash.novafork.ui.common.glassTabBarClearance
import com.aakash.novafork.ui.common.topScrim
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.Artwork
import com.aakash.novafork.ui.model.EpisodeUi
import com.aakash.novafork.ui.theme.NovaColors
import com.aakash.novafork.ui.theme.NovaPlayerTheme
import com.aakash.novafork.ui.theme.NovaType

/*
 * The phone details page (movie, video, episode, show): full-bleed artwork as tall as the screen
 * is wide × 1.25 with a gradient to black, glass back / "…" circles over it, the title and the
 * full-width white Play capsule over its bottom, then the description, info line, badges and
 * the About / Information sections. Always dark, like the Apple TV app's product pages.
 */

/**
 * Frame of a details page. [titleBlock] sits centred over the bottom of the artwork; [content]
 * adds the sections below it. The nav bar's glass and inline [title] fade in once the artwork has
 * scrolled away; until then the circles are Clear glass over the picture.
 */
@Composable
internal fun PhoneDetailsPage(
    title: String,
    artwork: Artwork,
    menuItems: List<GlassMenuItem>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    titleBlock: @Composable ColumnScope.() -> Unit,
    content: LazyListScope.() -> Unit,
) {
    NovaPlayerTheme {
        val list = rememberLazyListState()
        val density = LocalDensity.current
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        BoxWithConstraints(modifier.fillMaxSize()) {
            val artHeight = heroHeightFor(DeviceLayout.PHONE, maxWidth, maxHeight).coerceAtMost(maxHeight)
            val thresholdPx = with(density) { (artHeight - statusTop - NavBarDefaults.Height).toPx() }.coerceAtLeast(0f)
            val collapsed by remember(list, thresholdPx) {
                derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > thresholdPx }
            }
            val style = if (collapsed) GlassStyle.Regular else GlassStyle.Clear
            GlassPage(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                chrome = {
                    GlassNavBar(
                        title = title,
                        showBackground = collapsed,
                        leading = { BackButton(onClick = onBack, style = style) },
                        trailing = {
                            if (menuItems.isNotEmpty()) GlassMenuButton(items = menuItems, style = style)
                        },
                    )
                },
            ) {
                LazyColumn(
                    state = list,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentPadding = PaddingValues(bottom = glassTabBarClearance()),
                ) {
                    item(key = "artwork", contentType = "artwork") {
                        DetailsArtwork(artwork = artwork, height = artHeight, titleBlock = titleBlock)
                    }
                    content()
                }
            }
        }
    }
}

@Composable
private fun DetailsArtwork(artwork: Artwork, height: Dp, titleBlock: @Composable ColumnScope.() -> Unit) {
    val dims = LocalNovaDimens.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        BackdropArt(
            artwork = artwork,
            modifier = Modifier
                .matchParentSize()
                .bottomScrim(Color.Black, startFraction = 0.55f)
                .topScrim(),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = dims.heroMargin),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = titleBlock,
        )
    }
}

/**
 * Title (Title 1 bold, centred), an optional small-caps [eyebrow] above it and a centred
 * [subtitle] (genres) below, then the full-width white Play capsule.
 */
@Composable
internal fun ColumnScope.DetailsTitle(
    title: String,
    subtitle: String?,
    playLabel: String,
    onPlay: () -> Unit,
    eyebrow: String? = null,
    playEnabled: Boolean = true,
) {
    if (!eyebrow.isNullOrBlank()) {
        SmallCapsText(eyebrow, color = NovaColors.OnMediaSecondary)
        Spacer(Modifier.height(4.dp))
    }
    Text(
        text = title,
        style = NovaType.title1,
        color = NovaColors.OnMedia,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    if (!subtitle.isNullOrBlank()) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = NovaType.subhead,
            color = NovaColors.OnMediaSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Spacer(Modifier.height(16.dp))
    PrimaryCapsuleButton(
        text = playLabel,
        onClick = onPlay,
        enabled = playEnabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Under the Resume button: a thin progress bar, "1h 41m left" and a "Start Over" text button. */
@Composable
internal fun DetailsResumeRow(progress: Float, remaining: String?, onStartOver: () -> Unit, modifier: Modifier = Modifier) {
    val dims = LocalNovaDimens.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = dims.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressBar(progress = progress, modifier = Modifier.width(64.dp))
        if (remaining != null) {
            Spacer(Modifier.width(8.dp))
            Text(text = remaining, style = NovaType.footnote, color = NovaColors.secondaryLabel, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        TextAction(text = "Start Over", onClick = onStartOver, style = NovaType.subheadSemibold)
    }
}

/**
 * The description (3 lines + "MORE"), then the centred info line ("Drama · 2024 · 2 hr 46 min")
 * and the badge row ("4K", "★ 8.2"). MORE opens the whole text in a sheet titled [title].
 */
@Composable
internal fun DetailsSummary(
    title: String,
    description: String?,
    infoLine: String,
    quality: String?,
    rating: Float?,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dims = LocalNovaDimens.current
    Column(modifier.fillMaxWidth()) {
        if (!description.isNullOrBlank()) {
            ExpandableText(
                text = description,
                modifier = Modifier
                    .padding(horizontal = dims.gutter)
                    .fillMaxWidth(),
                fadeColor = Color.Black,
                onMore = onMore,
            )
            Spacer(Modifier.height(16.dp))
        }
        if (infoLine.isNotBlank()) {
            Text(
                text = infoLine,
                style = NovaType.footnote,
                color = NovaColors.secondaryLabel,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dims.gutter),
            )
        }
        val hasRating = rating != null && !rating.isNaN() && rating > 0f
        if (quality != null || hasRating) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (quality != null) Badge(quality)
                RatingBadge(rating)
            }
        }
    }
    // Keeps the sheet title in the composition only when there is a description to show.
    if (description.isNullOrBlank()) return
    title.length
}

/** "About" (title, genres and the whole overview in a card) and "Information" (2-column grid). */
@Composable
internal fun DetailsAboutAndInfo(
    title: String,
    genres: List<String>,
    overview: String?,
    info: List<Pair<String, String?>>,
    modifier: Modifier = Modifier,
) {
    val dims = LocalNovaDimens.current
    Column(modifier.fillMaxWidth()) {
        if (!overview.isNullOrBlank()) {
            RowHeader("About")
            AboutCard(
                title = title,
                body = overview,
                subtitle = genres.joinToString(", ").ifEmpty { null },
                modifier = Modifier
                    .padding(horizontal = dims.gutter)
                    .fillMaxWidth(),
            )
            Spacer(Modifier.height(dims.sectionSpacing))
        }
        if (info.any { !it.second.isNullOrBlank() }) {
            RowHeader("Information")
            InfoGrid(info, columns = 2, modifier = Modifier.padding(horizontal = dims.gutter))
        }
    }
}

/** The full description in a sheet (the Apple TV app's "MORE"). */
@Composable
internal fun DetailsTextSheet(title: String, text: String, onClose: () -> Unit) {
    GlassSheet(onDismissRequest = onClose, detent = SheetDetent.Medium, title = title) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
        ) {
            Text(text = text, style = NovaType.body, color = NovaColors.label)
        }
    }
}

/** Long-press menu of an episode card: Play, Go to Details, Mark as Watched, Identify… */
internal fun episodeMenuItems(episode: EpisodeUi, actions: AppActions): List<GlassMenuItem> = buildList {
    add(GlassMenuItem("Play", icon = R.drawable.ic_play_arrow) { actions.play(episode.videoId) })
    val progress = episode.progress
    if (progress != null && progress > 0f) {
        add(GlassMenuItem("Play from Beginning", icon = R.drawable.ic_restart_alt) { actions.play(episode.videoId, startOver = true) })
    }
    add(GlassMenuItem("Go to Details", icon = R.drawable.ic_info) { actions.openVideo(episode.videoId) })
    add(
        GlassMenuItem(
            label = if (episode.watched) "Mark as Unwatched" else "Mark as Watched",
            icon = if (episode.watched) R.drawable.ic_visibility_off else R.drawable.ic_check_circle,
            startsSection = true,
        ) { actions.setWatched(episode.videoId, !episode.watched) },
    )
    add(GlassMenuItem("Identify…", icon = R.drawable.ic_search) { actions.identify(episode.videoId) })
}
