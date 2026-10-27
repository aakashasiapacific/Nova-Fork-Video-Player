package com.aakash.novafork.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.DeviceLayout
import com.aakash.novafork.ui.common.CollapsedTitleBar
import com.aakash.novafork.ui.common.IosColors
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.glassRim
import com.aakash.novafork.ui.common.OnTabReselect
import com.aakash.novafork.ui.common.largeTitle
import com.aakash.novafork.ui.common.rememberLargeTitleState
import com.aakash.novafork.ui.common.stretchWithPull
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.Route
import com.aakash.novafork.ui.model.SettingsUi
import com.aakash.novafork.ui.phone.PhoneTopBar

private val PhoneMargin = 16.dp
private val TabletListWidth = 300.dp
private val TabletContentMaxWidth = 720.dp
private val GroupGap = 12.dp

/**
 * Settings for both UI families: one scrolling list of groups on a phone, a section list with the
 * selected section beside it on a tablet. [contentPadding] is the space the caller's chrome needs.
 */
@Composable
fun SettingsScreen(
    state: SettingsUi,
    actions: AppActions,
    layout: DeviceLayout,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    if (layout.isTablet) {
        TabletSettings(state, actions, modifier, contentPadding)
    } else {
        PhoneSettings(state, actions, modifier, contentPadding)
    }
}

/**
 * iOS 27 Settings (owner: the old page looked ugly): the large title, a search field that finds
 * any setting, the app's own card (as the Apple Account card), then the sections, icon and name
 * only, each opening its own page ([SettingsSectionScreen]).
 */
@Composable
private fun PhoneSettings(state: SettingsUi, actions: AppActions, modifier: Modifier, contentPadding: PaddingValues) {
    // iOS's large title: "Preferences" scrolls away and comes back small in a bar at the top.
    val listState = rememberLazyListState()
    OnTabReselect { listState.animateScrollToItem(0) }
    val titleState = rememberLargeTitleState(listState)
    var query by rememberSaveable { mutableStateOf("") }
    val hits = remember(query, state, actions) { searchSettings(query, state, actions) }
    val open: (SettingsSection) -> Unit = { actions.navigate(Route.SettingsPage(it.name)) }
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            // imePadding keeps the last results above the keyboard while searching.
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            state = listState,
            contentPadding = contentPadding.extend(horizontal = PhoneMargin, top = 8.dp, bottom = 16.dp),
        ) {
            item(key = "title") {
                Text(
                    text = "Preferences",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp).stretchWithPull().largeTitle(titleState),
                )
            }
            item(key = "search") {
                SettingsSearchField(query = query, onQueryChange = { query = it }, modifier = Modifier.padding(top = 10.dp))
            }
            if (query.isBlank()) {
                item(key = "app") {
                    AppCard(
                        versionName = state.versionName,
                        onClick = { open(SettingsSection.ABOUT) },
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }
                PhoneSectionCards.forEachIndexed { index, sections ->
                    item(key = "sections:$index") {
                        SectionCard(
                            sections = sections,
                            onOpen = open,
                            modifier = Modifier.padding(top = SectionCardGap),
                        )
                    }
                }
            } else {
                item(key = "results") {
                    SearchResults(query = query, hits = hits, onOpen = open, modifier = Modifier.padding(top = 20.dp))
                }
            }
        }
        CollapsedTitleBar(state = titleState, title = "Preferences")
    }
}

/** The top page's cards: what the library is made of, then the app itself. */
private val PhoneSectionCards = listOf(
    listOf(SettingsSection.LIBRARY, SettingsSection.ARTWORK, SettingsSection.PLAYER),
    listOf(SettingsSection.APPEARANCE, SettingsSection.ABOUT),
)
private val SectionCardGap = 28.dp

/** One card of section rows: icon tile, name and chevron, hairlines inset under the names. */
@Composable
private fun SectionCard(sections: List<SettingsSection>, onOpen: (SettingsSection) -> Unit, modifier: Modifier = Modifier) {
    SettingsCard(modifier) {
        sections.forEachIndexed { index, section ->
            if (index > 0) RowSeparator()
            RowSurface(
                shape = RoundedCornerShape(0.dp),
                onClick = { onOpen(section) },
            ) {
                RowIcon(section.icon, section.tile)
                RowTitle(section.title, Modifier.weight(1f))
                RowChevron()
            }
        }
    }
}

/**
 * The Apple Account card's place, for the app itself: its icon, name and version; it opens
 * About.
 */
@Composable
private fun AppCard(versionName: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SettingsCard(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Open About", onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppIcon(AppIconSize)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Nova Fork", style = MaterialTheme.typography.titleLarge, maxLines = 1)
                Text(
                    text = if (versionName.isBlank()) "Videos and music" else "Version $versionName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IosColors.SecondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RowChevron()
        }
    }
}

private val AppIconSize = 58.dp

/** The launcher icon as the home screen shows it: the white mark on its blue gradient square, in glass. */
@Composable
internal fun AppIcon(size: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.225f)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(LauncherTop, LauncherBottom)))
            .glassSheen()
            .glassRim(shape, strength = 0.9f),
        contentAlignment = Alignment.Center,
    ) {
        // The foreground layer is drawn for a 108dp canvas with the mark in its middle 72dp.
        NovaIcon(
            R.drawable.ic_launcher_foreground,
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.requiredSize(size * 1.5f),
        )
    }
}

/** ic_launcher_background's gradient. */
private val LauncherTop = Color(0xFF409CFF)
private val LauncherBottom = Color(0xFF0058D0)

/**
 * iOS 27's search field: a dark capsule with the magnifier, "Search" until something is typed,
 * and a clear button while there is text.
 */
@Composable
internal fun SettingsSearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(SearchFieldHeight)
                    .background(IosColors.GroupedCard, CircleShape)
                    .padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NovaIcon(R.drawable.ic_search, contentDescription = null, tint = IosColors.SecondaryLabel, modifier = Modifier.size(18.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) {
                        Text("Search", style = MaterialTheme.typography.bodyLarge, color = IosColors.SecondaryLabel, maxLines = 1)
                    }
                    inner()
                }
                if (query.isNotEmpty()) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable(onClickLabel = "Clear search") { onQueryChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(17.dp)
                                .background(IosColors.SecondaryLabel, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            NovaIcon(R.drawable.ic_close, contentDescription = null, tint = IosColors.GroupedCard, modifier = Modifier.size(11.dp))
                        }
                    }
                }
            }
        },
    )
}

private val SearchFieldHeight = 40.dp

/** A setting found by search: the row, the group and the section it lives in. */
internal class SettingsHit(val section: SettingsSection, val group: SettingsGroup, val row: SettingsRow)

/**
 * Every setting whose name (or its group's or section's name) holds each word of [query], in the
 * order Preferences shows them.
 */
internal fun searchSettings(query: String, state: SettingsUi, actions: AppActions): List<SettingsHit> {
    val words = query.trim().lowercase().split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    return SettingsSection.entries.flatMap { section ->
        settingsGroups(section, state, actions).flatMap { group ->
            group.rows.mapNotNull { row ->
                val title = row.title ?: return@mapNotNull null
                val names = listOfNotNull(title, group.title, section.title).joinToString(" ").lowercase()
                if (words.all { it in names }) SettingsHit(section, group, row) else null
            }
        }
    }
}

/**
 * Search's results as iOS Settings lists them: one card, each setting's tile and name with where
 * it lives in grey under it; a tap opens that section. "No Results" when nothing matches.
 */
@Composable
internal fun SearchResults(query: String, hits: List<SettingsHit>, onOpen: (SettingsSection) -> Unit, modifier: Modifier = Modifier) {
    if (hits.isEmpty()) {
        Text(
            text = "No Results for “${query.trim()}”",
            style = MaterialTheme.typography.bodyLarge,
            color = IosColors.SecondaryLabel,
            textAlign = TextAlign.Center,
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 40.dp),
        )
        return
    }
    SettingsCard(modifier) {
        hits.forEachIndexed { index, hit ->
            if (index > 0) RowSeparator()
            key(hit.section.name, hit.group.key, hit.row.key) {
                SearchHitRow(hit, onClick = { onOpen(hit.section) })
            }
        }
    }
}

@Composable
private fun SearchHitRow(hit: SettingsHit, onClick: () -> Unit) {
    val icon = hit.row.icon
    RowSurface(shape = RoundedCornerShape(0.dp), onClick = onClick) {
        if (icon != null) RowIcon(icon, hit.row.tile ?: tileColor(icon))
        Column(
            Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            RowTitle(hit.row.title.orEmpty())
            Text(
                text = listOfNotNull(hit.section.title, hit.group.title).joinToString(" › "),
                style = MaterialTheme.typography.bodySmall,
                color = IosColors.SecondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RowChevron()
    }
}

/**
 * One section of Preferences on its own page (phone), opened from the top page as iOS Settings
 * opens its rows: a bar with the way back and the section's name, then its groups, well apart.
 */
@Composable
fun SettingsSectionScreen(
    section: String,
    state: SettingsUi,
    actions: AppActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val shown = SettingsSection.entries.firstOrNull { it.name == section } ?: SettingsSection.LIBRARY
    val groups = remember(shown, state, actions) { settingsGroups(shown, state, actions) }
    Column(modifier.fillMaxSize()) {
        PhoneTopBar(title = shown.title, onBack = actions::back)
        LazyColumn(
            // imePadding keeps the TMDB key field above the keyboard: the window does not resize edge-to-edge.
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = contentPadding.extend(horizontal = PhoneMargin, top = 4.dp, bottom = 16.dp),
        ) {
            groups.forEachIndexed { index, group ->
                item(key = group.key) {
                    SettingsGroupBlock(
                        group = group,
                        title = group.title,
                        modifier = Modifier.padding(top = if (index == 0) 8.dp else SectionGroupGap),
                    )
                }
            }
        }
    }
}

/** Room between a section page's groups (iOS leaves about this much). */
private val SectionGroupGap = 24.dp

@Composable
private fun TabletSettings(state: SettingsUi, actions: AppActions, modifier: Modifier, contentPadding: PaddingValues) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val hits = remember(query, state, actions) { searchSettings(query, state, actions) }
    val selected = SettingsSection.entries.getOrElse(selectedIndex) { SettingsSection.LIBRARY }
    val groups = remember(selected, state, actions) { settingsGroups(selected, state, actions) }
    Row(modifier.fillMaxSize()) {
        Column(
            Modifier
                .width(TabletListWidth)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding.extend(horizontal = 16.dp, top = 24.dp, bottom = 16.dp)),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Preferences",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 12.dp, bottom = 12.dp),
            )
            SettingsSearchField(query = query, onQueryChange = { query = it }, modifier = Modifier.padding(bottom = 12.dp))
            if (query.isBlank()) {
                SettingsSection.entries.forEach { section ->
                    SectionListItem(
                        section = section,
                        selected = section == selected,
                        onClick = { selectedIndex = section.ordinal },
                    )
                }
            } else {
                SearchResults(query = query, hits = hits, onOpen = { selectedIndex = it.ordinal })
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = TabletContentMaxWidth)
                    .fillMaxSize()
                    .imePadding(),
                contentPadding = contentPadding.extend(horizontal = 24.dp, top = 24.dp, bottom = 24.dp),
            ) {
                item(key = "${selected.name}/title") {
                    Text(
                        text = selected.title,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                    )
                }
                groups.forEachIndexed { index, group ->
                    item(key = "${selected.name}/${group.key}") {
                        SettingsGroupBlock(
                            group = group,
                            title = group.title,
                            modifier = Modifier.padding(top = if (index == 0 || group.title != null) 0.dp else GroupGap),
                        )
                    }
                }
            }
        }
    }
}

/** iPadOS Settings' sidebar row: icon tile and title, filled blue when selected. */
@Composable
private fun SectionListItem(section: SettingsSection, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) colors.primary else Color.Transparent,
        contentColor = Color.White,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .heightIn(min = 52.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(section.icon, section.tile)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(section.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = section.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) Color.White.copy(alpha = 0.8f) else IosColors.SecondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** [this] plus fixed margins on each side. */
@Composable
private fun PaddingValues.extend(horizontal: Dp, top: Dp, bottom: Dp): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction) + horizontal,
        top = calculateTopPadding() + top,
        end = calculateEndPadding(direction) + horizontal,
        bottom = calculateBottomPadding() + bottom,
    )
}
