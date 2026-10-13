package com.aakash.novafork.ui.tablet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aakash.novafork.data.ThemeMode
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.AboutCard
import com.aakash.novafork.ui.common.BackButton
import com.aakash.novafork.ui.common.BackdropArt
import com.aakash.novafork.ui.common.Badge
import com.aakash.novafork.ui.common.ExpandableText
import com.aakash.novafork.ui.common.GlassBackdrop
import com.aakash.novafork.ui.common.GlassMenuButton
import com.aakash.novafork.ui.common.GlassMenuItem
import com.aakash.novafork.ui.common.GlassNavBar
import com.aakash.novafork.ui.common.GlassPage
import com.aakash.novafork.ui.common.GlassSheet
import com.aakash.novafork.ui.common.GlassStyle
import com.aakash.novafork.ui.common.InfoGrid
import com.aakash.novafork.ui.common.LocalGlassState
import com.aakash.novafork.ui.common.ProgressBar
import com.aakash.novafork.ui.common.RatingBadge
import com.aakash.novafork.ui.common.RowHeader
import com.aakash.novafork.ui.common.SheetDetent
import com.aakash.novafork.ui.common.SmallCapsText
import com.aakash.novafork.ui.common.bottomScrim
import com.aakash.novafork.ui.common.novaClickable
import com.aakash.novafork.ui.common.rememberGlassState
import com.aakash.novafork.ui.common.startScrim
import com.aakash.novafork.ui.common.topScrim
import com.aakash.novafork.ui.model.Artwork
import com.aakash.novafork.ui.theme.NovaColors
import com.aakash.novafork.ui.theme.NovaTheme
import com.aakash.novafork.ui.theme.NovaType

/** Height of the gradient that carries the artwork into the black part of the page. */
private val FoldFade = 120.dp

/**
 * The iPad details page: the artwork fills the whole page behind heavy bottom and leading
 * gradients; [fold] sits bottom-left on it (48dp margins) and [below] continues on black under
 * the fold (Episodes, About, Information). Back "‹" and "…" ([menu]) float at the top as Clear
 * glass and turn into a glass bar with the inline [title] once the fold scrolls away.
 * Always dark, as the Apple TV app's details are.
 */
@Composable
internal fun TabletDetailsPage(
    artwork: Artwork,
    title: String,
    onBack: () -> Unit,
    menu: List<GlassMenuItem>,
    modifier: Modifier = Modifier,
    fold: @Composable ColumnScope.() -> Unit,
    below: LazyListScope.() -> Unit,
) {
    NovaTheme(ThemeMode.DARK) {
        val dims = LocalNovaDimens.current
        val list = rememberLazyListState()
        // The circles next to Play blur the artwork, not the whole page.
        val artGlass = rememberGlassState()
        BoxWithConstraints(
            modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            val foldHeight = if (constraints.hasBoundedHeight) maxHeight else 720.dp
            val collapseAtPx = with(LocalDensity.current) { (foldHeight * 0.55f).roundToPx() }
            val collapsed by remember(list, collapseAtPx) {
                derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > collapseAtPx }
            }
            val buttonStyle = if (collapsed) GlassStyle.Regular else GlassStyle.Clear
            GlassPage(
                modifier = Modifier.fillMaxSize(),
                chrome = {
                    GlassNavBar(
                        title = title,
                        showBackground = collapsed,
                        horizontalPadding = dims.gutter - 16.dp,
                        leading = { BackButton(onClick = onBack, style = buttonStyle) },
                        trailing = {
                            if (menu.isNotEmpty()) GlassMenuButton(items = menu, style = buttonStyle)
                        },
                    )
                },
            ) {
                GlassBackdrop(artGlass, Modifier.fillMaxSize()) {
                    BackdropArt(
                        artwork = artwork,
                        modifier = Modifier
                            .fillMaxSize()
                            .topScrim(Color.Black.copy(alpha = 0.4f), endFraction = 0.18f)
                            .startScrim(Color.Black.copy(alpha = 0.78f), endFraction = 0.72f)
                            .bottomScrim(Color.Black, startFraction = 0.32f),
                    )
                }
                LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = navigationBarBottom() + 48.dp),
                ) {
                    item(key = "fold", contentType = "fold") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(foldHeight),
                        ) {
                            CompositionLocalProvider(LocalGlassState provides artGlass) {
                                Column(
                                    Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(start = dims.heroMargin, end = dims.heroMargin, bottom = dims.heroMargin)
                                        .widthIn(max = 760.dp),
                                    content = fold,
                                )
                            }
                        }
                    }
                    item(key = "fade", contentType = "fade") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(FoldFade)
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black))),
                        )
                    }
                    below()
                }
            }
        }
    }
}

/** A section under the fold: Title 2 header ("About") on black, then [content]. */
internal fun LazyListScope.detailsSection(key: String, title: String?, content: @Composable ColumnScope.() -> Unit) {
    item(key = key, contentType = "section") {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(bottom = LocalNovaDimens.current.sectionSpacing),
        ) {
            if (title != null) RowHeader(title)
            content()
        }
    }
}

/** Show title and episode over the title ("SEVERANCE · S1, E3"); tappable when [onClick] is set. */
@Composable
internal fun DetailsEyebrow(text: String, onClick: (() -> Unit)?) {
    val click = if (onClick != null) Modifier.novaClickable(pressScale = 1f, pressAlpha = 0.5f, onClick = onClick) else Modifier
    SmallCapsText(
        text = text,
        color = NovaColors.OnMediaSecondary,
        modifier = click.padding(bottom = 6.dp),
    )
}

/** The 48sp title of the details page. */
@Composable
internal fun DetailsTitle(title: String) {
    Text(
        text = title,
        style = NovaType.heroTitle,
        color = NovaColors.OnMedia,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** "Drama · 2024 · 2h 46m" followed by the outlined badges ("4K", "★ 8.2"). */
@Composable
internal fun DetailsMeta(meta: String, quality: String? = null, rating: Float? = null) {
    val hasRating = rating != null && rating > 0f
    if (meta.isBlank() && quality == null && !hasRating) return
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                style = NovaType.subheadSemibold,
                color = NovaColors.OnMediaSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (quality != null) Badge(quality, color = NovaColors.OnMediaSecondary)
        RatingBadge(rating, color = NovaColors.OnMediaSecondary)
    }
}

/** Thin progress capsule with "1h 41m left" under the Resume button. */
@Composable
internal fun ResumeLine(progress: Float?, label: String?) {
    if (progress == null || progress <= 0f) return
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        ProgressBar(progress = progress, modifier = Modifier.width(160.dp))
        if (!label.isNullOrBlank()) {
            Spacer(Modifier.width(12.dp))
            Text(label, style = NovaType.footnote, color = NovaColors.OnMediaSecondary, maxLines = 1)
        }
    }
}

/**
 * The description (max 600dp, 3 lines + "MORE"). "MORE" opens the whole text in a sheet, as the
 * Apple TV app does.
 */
@Composable
internal fun DetailsOverview(text: String?, title: String) {
    if (text.isNullOrBlank()) return
    var open by rememberSaveable { mutableStateOf(false) }
    Spacer(Modifier.height(18.dp))
    ExpandableText(
        text = text,
        modifier = Modifier.widthIn(max = LocalNovaDimens.current.descriptionMaxWidth),
        color = NovaColors.OnMediaSecondary,
        moreColor = NovaColors.OnMedia,
        fadeColor = Color.Black,
        onMore = { open = true },
    )
    if (open) {
        GlassSheet(onDismissRequest = { open = false }, detent = SheetDetent.Medium, title = title) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            ) {
                Text(text = text, style = NovaType.body, color = NovaColors.label)
            }
        }
    }
}

/** "About": the overview in a card (max 720dp wide). Nothing when there is no text. */
internal fun LazyListScope.aboutSection(title: String, overview: String?, subtitle: String?) {
    if (overview.isNullOrBlank()) return
    detailsSection(key = "about", title = "About") {
        val dims = LocalNovaDimens.current
        AboutCard(
            title = title,
            body = overview,
            subtitle = subtitle?.takeIf { it.isNotBlank() },
            modifier = Modifier
                .padding(horizontal = dims.gutter)
                .widthIn(max = 720.dp)
                .fillMaxWidth(),
        )
    }
}

/** "Information": key/value pairs in three columns. Nothing when every value is blank. */
internal fun LazyListScope.informationSection(entries: List<Pair<String, String?>>) {
    if (entries.none { !it.second.isNullOrBlank() }) return
    detailsSection(key = "information", title = "Information") {
        InfoGrid(
            entries = entries,
            columns = 3,
            modifier = Modifier
                .padding(horizontal = LocalNovaDimens.current.gutter)
                .widthIn(max = 960.dp),
            keyColor = NovaColors.OnMedia,
            valueColor = NovaColors.OnMediaSecondary,
        )
    }
}
