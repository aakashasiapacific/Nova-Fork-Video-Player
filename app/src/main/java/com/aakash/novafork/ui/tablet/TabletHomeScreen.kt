package com.aakash.novafork.ui.tablet

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.DeviceLayout
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.BackdropArt
import com.aakash.novafork.ui.common.DeviceAccessCard
import com.aakash.novafork.ui.common.LandscapeArt
import com.aakash.novafork.ui.common.RatingPill
import com.aakash.novafork.ui.common.ScanBanner
import com.aakash.novafork.ui.common.SectionHeader
import com.aakash.novafork.ui.common.VideoMenuBox
import com.aakash.novafork.ui.common.bottomScrim
import com.aakash.novafork.ui.common.deviceAccessNotice
import com.aakash.novafork.ui.common.startScrim
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.DialogUi
import com.aakash.novafork.ui.model.HeroTarget
import com.aakash.novafork.ui.model.HeroUi
import com.aakash.novafork.ui.model.HomeUi
import com.aakash.novafork.ui.model.LibraryFilter
import com.aakash.novafork.ui.model.PermissionUi
import com.aakash.novafork.ui.model.ScanUi
import com.aakash.novafork.ui.model.ShowCardUi
import com.aakash.novafork.ui.model.TopDestination
import com.aakash.novafork.ui.model.VideoCardUi
import kotlinx.coroutines.delay

private const val HERO_ROTATE_MS = 9_000L
private val HeroBottomPadding = 36.dp
private val ThumbSpacing = 10.dp

@Composable
internal fun TabletHomeScreen(
    home: HomeUi,
    scan: ScanUi,
    permission: PermissionUi,
    layout: DeviceLayout,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    TabletPullToRefresh(
        scanning = scan.running,
        onRefresh = actions::refresh,
        modifier = modifier.fillMaxSize(),
    ) {
        if (home.isEmpty) {
            HomeOnboarding(scan = scan, permission = permission, layout = layout, actions = actions)
        } else {
            HomeFeed(home = home, scan = scan, permission = permission, layout = layout, actions = actions)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Feed
// ---------------------------------------------------------------------------------------------

@Composable
private fun HomeFeed(home: HomeUi, scan: ScanUi, permission: PermissionUi, layout: DeviceLayout, actions: AppActions) {
    val dims = LocalNovaDimens.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heroHeight = if (constraints.hasBoundedHeight) (maxHeight * 0.56f).coerceIn(360.dp, 580.dp) else 480.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = navigationBarBottom() + dims.sectionSpacing),
        ) {
            if (home.hero.isNotEmpty()) {
                item(key = "hero", contentType = "hero") {
                    TabletHero(heroes = home.hero, height = heroHeight, layout = layout, scan = scan, actions = actions)
                }
            } else {
                item(key = "header", contentType = "header") {
                    Column(Modifier.statusBarsPadding()) {
                        ScanBanner(scan)
                        Text(
                            text = "Home",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(start = dims.gutter, end = dims.gutter, top = 24.dp),
                        )
                    }
                }
            }
            val deviceNotice = deviceAccessNotice(permission)
            if (deviceNotice != null) {
                // Shown above folders' and shares' posters too: device videos missing must never be silent.
                item(key = "deviceAccess", contentType = "deviceAccess") {
                    DeviceAccessCard(
                        notice = deviceNotice,
                        onAllow = actions::requestPermission,
                        onOpenSettings = actions::openAppSettings,
                        modifier = Modifier
                            .padding(start = dims.gutter, end = dims.gutter, top = 24.dp)
                            .widthIn(max = 720.dp),
                    )
                }
            }
            if (home.continueWatching.isNotEmpty()) {
                item(key = "continue", contentType = "landscapeRow") {
                    HomeSection(title = "Continue watching") {
                        ContinueWatchingRow(home.continueWatching, actions)
                    }
                }
            }
            if (home.recentlyAdded.isNotEmpty()) {
                item(key = "recent", contentType = "posterRow") {
                    HomeSection(title = "Recently added") {
                        VideoPosterRow(home.recentlyAdded, actions)
                    }
                }
            }
            if (home.movies.isNotEmpty()) {
                item(key = "movies", contentType = "posterRow") {
                    HomeSection(title = "Movies", onSeeAll = { actions.seeAll(LibraryFilter.MOVIES) }) {
                        VideoPosterRow(home.movies, actions)
                    }
                }
            }
            if (home.shows.isNotEmpty()) {
                item(key = "shows", contentType = "posterRow") {
                    HomeSection(title = "TV shows", onSeeAll = { actions.seeAll(LibraryFilter.SHOWS) }) {
                        ShowPosterRow(home.shows, actions)
                    }
                }
            }
            if (home.videos.isNotEmpty()) {
                item(key = "videos", contentType = "posterRow") {
                    HomeSection(title = "Videos", onSeeAll = { actions.seeAll(LibraryFilter.VIDEOS) }) {
                        VideoPosterRow(home.videos, actions)
                    }
                }
            }
        }
    }
}

private fun AppActions.seeAll(filter: LibraryFilter) {
    selectTab(TopDestination.LIBRARY)
    setLibraryFilter(filter)
}

private fun AppActions.openHero(target: HeroTarget) {
    when (target) {
        is HeroTarget.Video -> openVideo(target.id)
        is HeroTarget.Show -> openShow(target.key)
    }
}

@Composable
private fun HomeSection(title: String, onSeeAll: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val dims = LocalNovaDimens.current
    Column(Modifier.padding(top = dims.sectionSpacing)) {
        SectionHeader(
            title = title,
            modifier = Modifier.padding(start = dims.gutter, end = dims.gutter - 8.dp),
            actionLabel = if (onSeeAll != null) "See all" else null,
            onAction = onSeeAll,
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun ContinueWatchingRow(cards: List<VideoCardUi>, actions: AppActions) {
    val dims = LocalNovaDimens.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = dims.gutter, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
    ) {
        items(cards, key = { it.id }) { card ->
            VideoMenuBox(videoId = card.id, title = card.primaryLine(), actions = actions) { openMenu ->
                TabletLandscapeCard(
                    card = card,
                    onClick = { actions.play(card.id) },
                    onLongClick = openMenu,
                    modifier = Modifier.width(dims.landscapeCardWidth),
                )
            }
        }
    }
}

@Composable
private fun VideoPosterRow(cards: List<VideoCardUi>, actions: AppActions) {
    val dims = LocalNovaDimens.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = dims.gutter, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
    ) {
        items(cards, key = { it.id }) { card ->
            TabletVideoPosterCard(
                card = card,
                onOpen = { actions.openVideo(it) },
                onPlay = { actions.play(it) },
                modifier = Modifier.width(dims.posterWidth),
            )
        }
    }
}

@Composable
private fun ShowPosterRow(shows: List<ShowCardUi>, actions: AppActions) {
    val dims = LocalNovaDimens.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = dims.gutter, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
    ) {
        items(shows, key = { it.key }) { show ->
            TabletShowPosterCard(
                card = show,
                onClick = { actions.openShow(show.key) },
                modifier = Modifier.width(dims.posterWidth),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Hero
// ---------------------------------------------------------------------------------------------

@Composable
private fun TabletHero(heroes: List<HeroUi>, height: Dp, layout: DeviceLayout, scan: ScanUi, actions: AppActions) {
    val dims = LocalNovaDimens.current
    val posterWidth = if (layout.isExpanded) 190.dp else 150.dp
    val thumbWidth = if (layout.isExpanded) 150.dp else 120.dp
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val index = selected.coerceIn(0, heroes.lastIndex)
    val hasThumbnails = heroes.size > 1
    if (hasThumbnails) {
        // Restarted by every change of index, so picking a thumbnail also resets the timer.
        LaunchedEffect(index, heroes.size) {
            delay(HERO_ROTATE_MS)
            selected = (index + 1) % heroes.size
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        Crossfade(targetState = index, animationSpec = tween(durationMillis = 700), label = "hero") { i ->
            val hero = heroes.getOrNull(i)
            if (hero != null) {
                HeroSlide(
                    hero = hero,
                    posterWidth = posterWidth,
                    endReserve = if (hasThumbnails) thumbWidth + 32.dp else 0.dp,
                    actions = actions,
                )
            }
        }
        if (hasThumbnails) {
            HeroThumbnails(
                heroes = heroes,
                selected = index,
                thumbWidth = thumbWidth,
                heroHeight = height,
                onSelect = { selected = it },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = dims.gutter, bottom = HeroBottomPadding),
            )
        }
        ScanBanner(
            scan = scan,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding(),
        )
    }
}

@Composable
private fun HeroSlide(hero: HeroUi, posterWidth: Dp, endReserve: Dp, actions: AppActions) {
    val dims = LocalNovaDimens.current
    val background = MaterialTheme.colorScheme.background
    val onBackground = MaterialTheme.colorScheme.onBackground
    Box(Modifier.fillMaxSize()) {
        BackdropArt(
            artwork = hero.artwork,
            modifier = Modifier
                .fillMaxSize()
                .startScrim(background, endFraction = 0.75f)
                .bottomScrim(background, startFraction = 0.4f),
        )
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = dims.gutter, end = dims.gutter + endReserve, bottom = HeroBottomPadding),
            verticalAlignment = Alignment.Bottom,
        ) {
            TabletDetailsPoster(artwork = hero.artwork, title = hero.title, width = posterWidth)
            Spacer(Modifier.width(28.dp))
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .widthIn(max = 560.dp),
            ) {
                Text(
                    text = hero.title,
                    style = MaterialTheme.typography.displaySmall,
                    color = onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = hero.metaLine
                if (!meta.isNullOrBlank() || (hero.rating ?: 0f) > 0f) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (!meta.isNullOrBlank()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.titleSmall,
                                color = onBackground.copy(alpha = 0.82f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        RatingPill(hero.rating)
                    }
                }
                // The meta line often already lists the genres; only show the ones it doesn't.
                val genres = hero.genres.filterNot { meta?.contains(it, ignoreCase = true) == true }.take(3)
                if (genres.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        genres.forEach { TabletTag(it) }
                    }
                }
                if (!hero.overview.isNullOrBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = hero.overview,
                        style = MaterialTheme.typography.bodyLarge,
                        color = onBackground.copy(alpha = 0.82f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TabletPlayButton(
                        label = hero.playLabel,
                        onClick = {
                            val id = hero.playVideoId
                            if (id != null) actions.play(id) else actions.openHero(hero.target)
                        },
                    )
                    FilledTonalButton(
                        onClick = { actions.openHero(hero.target) },
                        modifier = Modifier.height(48.dp),
                    ) {
                        ButtonLabel(R.drawable.ic_info, "More info")
                    }
                }
            }
        }
    }
}

/** A column of 16:9 thumbnails at the bottom-end; shows as many as fit, scrolled to the selection. */
@Composable
private fun HeroThumbnails(
    heroes: List<HeroUi>,
    selected: Int,
    thumbWidth: Dp,
    heroHeight: Dp,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val thumbHeight = thumbWidth * 9f / 16f
    val room = heroHeight - statusBarTop() - HeroBottomPadding - 48.dp
    val fit = ((room + ThumbSpacing) / (thumbHeight + ThumbSpacing)).toInt().coerceIn(1, heroes.size)
    val first = (selected - fit + 1).coerceIn(0, heroes.size - fit)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ThumbSpacing)) {
        for (i in first until first + fit) {
            HeroThumbnail(
                hero = heroes[i],
                selected = i == selected,
                onClick = { onSelect(i) },
                modifier = Modifier.width(thumbWidth),
            )
        }
    }
}

@Composable
private fun HeroThumbnail(hero: HeroUi, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val dim by animateFloatAsState(targetValue = if (selected) 0f else 0.45f, label = "thumbDim")
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .shadow(6.dp, shape)
            .outlineIf(selected, Color.White, shape)
            .clickable(onClickLabel = hero.title, onClick = onClick),
    ) {
        LandscapeArt(
            artwork = hero.artwork,
            title = hero.title,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    drawRect(Color.Black.copy(alpha = dim))
                },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Empty library
// ---------------------------------------------------------------------------------------------

@Composable
private fun HomeOnboarding(scan: ScanUi, permission: PermissionUi, layout: DeviceLayout, actions: AppActions) {
    val dims = LocalNovaDimens.current
    // Scrollable so pull-to-refresh works, centred while it fits.
    CenteredScrollColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = dims.gutter,
            end = dims.gutter,
            top = statusBarTop() + 24.dp,
            bottom = navigationBarBottom() + 24.dp,
        ),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 1040.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            if (layout.isExpanded) {
                Row(Modifier.padding(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { PosterCollage() }
                    Spacer(Modifier.width(48.dp))
                    OnboardingText(scan, permission, actions, Modifier.weight(1f))
                }
            } else {
                Column(Modifier.padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PosterCollage()
                    Spacer(Modifier.height(36.dp))
                    OnboardingText(scan, permission, actions, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun OnboardingText(scan: ScanUi, permission: PermissionUi, actions: AppActions, modifier: Modifier = Modifier) {
    val title: String
    val body: String
    when {
        !permission.granted -> {
            title = "Your videos, beautifully organised"
            body = "Nova finds the movies, shows and clips on this tablet, in folders you pick and on " +
                "network shares, then dresses them up with posters and details."
        }
        scan.running -> {
            title = "Looking for videos…"
            body = scan.label ?: "This can take a moment the first time."
        }
        permission.partial -> {
            title = "No videos yet"
            body = "Nova can only see the videos you picked. Allow access to all of them, add a folder, " +
                "connect to a network share or open a stream."
        }
        else -> {
            title = "No videos yet"
            body = "Nothing turned up on this tablet. Add a folder, connect to a network share or open a stream."
        }
    }
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (scan.running) {
            Spacer(Modifier.height(20.dp))
            ScanBanner(scan.copy(label = null), Modifier.widthIn(max = 360.dp))
        }
        Spacer(Modifier.height(28.dp))
        Column(
            Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!permission.granted || permission.partial) {
                Button(onClick = actions::requestPermission, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    ButtonLabel(
                        R.drawable.ic_video_library,
                        if (permission.partial) "Allow access to all videos" else "Allow access to videos",
                    )
                }
            }
            FilledTonalButton(onClick = actions::addFolder, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                ButtonLabel(R.drawable.ic_create_new_folder, "Add a folder")
            }
            FilledTonalButton(
                onClick = actions::addNetworkSource,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                ButtonLabel(R.drawable.ic_lan, "Add a network source")
            }
            OutlinedButton(
                onClick = { actions.showDialog(DialogUi.OpenUrl) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                ButtonLabel(R.drawable.ic_link, "Open a network stream")
            }
        }
    }
}
