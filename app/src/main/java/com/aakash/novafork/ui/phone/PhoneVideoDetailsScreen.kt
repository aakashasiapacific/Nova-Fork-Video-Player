package com.aakash.novafork.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.core.format.Formats
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.EndOfListGap
import com.aakash.novafork.ui.common.IosButton
import com.aakash.novafork.ui.common.IosColors
import com.aakash.novafork.ui.common.IosTextButton
import com.aakash.novafork.ui.common.LocalBottomBarSpace
import com.aakash.novafork.ui.common.LocalHud
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.NovaMenuItem
import com.aakash.novafork.ui.common.SectionHeader
import com.aakash.novafork.ui.common.metaLine
import com.aakash.novafork.ui.common.rememberEndsAt
import com.aakash.novafork.ui.common.resumeFill
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.MediaKind
import com.aakash.novafork.ui.model.VideoDetailsUi
import com.aakash.novafork.ui.theme.NovaColors
import kotlin.math.roundToInt

@Composable
fun PhoneVideoDetailsScreen(
    details: VideoDetailsUi,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    val dims = LocalNovaDimens.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val backdropHeight = detailsBackdropHeight(maxHeight)
        val listState = rememberLazyListState()
        val titleCollapse = rememberTitleCollapse(listState, backdropHeight)
        val tint = rememberDetailsTint(details.artwork)
        val hasOverview = !details.tagline.isNullOrBlank() || !details.overview.isNullOrBlank()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = EndOfListGap + LocalBottomBarSpace.current),
        ) {
            item(key = "header") {
                PhoneDetailsHeader(
                    scrolled = { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 0f },
                    artwork = details.artwork,
                    title = details.title,
                    originalTitle = details.originalTitle,
                    meta = metaLine(details.year?.toString(), details.runtimeLabel, details.episodeCode),
                    rating = details.rating,
                    genres = details.genres,
                    maxBackdropHeight = backdropHeight,
                    modifier = Modifier.tintBand(tint, from = 1f, to = 0.8f),
                )
            }
            item(key = "play") {
                PlaySection(
                    details = details,
                    actions = actions,
                    modifier = Modifier.tintBand(tint, from = 0.8f, to = if (hasOverview) 0.55f else 0f).padding(start = dims.gutter, end = dims.gutter, top = 20.dp),
                )
            }
            item(key = "overview") {
                OverviewBlock(
                    tagline = details.tagline,
                    overview = details.overview,
                    modifier = Modifier.tintBand(tint, from = 0.55f, to = 0f).padding(start = dims.gutter, end = dims.gutter, top = 16.dp),
                )
            }
            val showKey = details.showKey
            val showTitle = details.showTitle
            if (showKey != null && !showTitle.isNullOrBlank()) {
                item(key = "show") {
                    FromShowCard(
                        showTitle = showTitle,
                        onClick = { actions.openShow(showKey) },
                        modifier = Modifier.padding(start = dims.gutter, end = dims.gutter, top = 16.dp),
                    )
                }
            }
            val next = details.nextEpisode
            if (next != null) {
                item(key = "next") {
                    Column(Modifier.padding(top = dims.sectionSpacing)) {
                        SectionHeader(
                            title = "Next Episode",
                            modifier = Modifier.padding(start = dims.gutter, end = dims.gutter, bottom = 10.dp),
                        )
                        PhoneLandscapeCard(
                            card = next,
                            onClick = { actions.play(next.id) },
                            onLongClick = { actions.openVideo(next.id) },
                            modifier = Modifier.padding(horizontal = dims.gutter),
                            title = next.title,
                            subtitle = metaLine(next.subtitle, durationLabel(next.durationMs)).ifEmpty { null },
                        )
                    }
                }
            }
            // An unidentified video is usually not a movie or show at all: no nagging card;
            // Identify stays in the ⋮ menu.
            item(key = "file") {
                FileInfo(
                    details = details,
                    modifier = Modifier.padding(start = dims.gutter, end = dims.gutter, top = dims.sectionSpacing),
                )
            }
        }
        PhoneDetailsTopBar(
            onBack = actions::back,
            modifier = Modifier.align(Alignment.TopStart),
            title = details.title,
            collapsed = titleCollapse,
            menu = { close ->
                NovaMenuItem(
                    text = { Text("Identify…") },
                    leadingIcon = { NovaIcon(R.drawable.ic_search, contentDescription = null) },
                    onClick = {
                        close()
                        actions.identify(details.id)
                    },
                )
                NovaMenuItem(
                    text = { Text("Change Artwork…") },
                    leadingIcon = { NovaIcon(R.drawable.ic_image, contentDescription = null) },
                    onClick = {
                        close()
                        actions.changeArtwork(details.id)
                    },
                )
                NovaMenuItem(
                    text = { Text(if (details.watched) "Mark as Unwatched" else "Mark as Watched") },
                    leadingIcon = {
                        NovaIcon(
                            if (details.watched) R.drawable.ic_visibility_off else R.drawable.ic_check_circle,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        close()
                        actions.setWatched(details.id, !details.watched)
                    },
                )
                NovaMenuItem(
                    text = { Text("Add to Playlist") },
                    leadingIcon = { NovaIcon(R.drawable.ic_playlist_add, contentDescription = null) },
                    onClick = {
                        close()
                        actions.addToPlaylist(listOf(details.id), details.title)
                    },
                )
            },
        )
    }
}

@Composable
private fun PlaySection(details: VideoDetailsUi, actions: AppActions, modifier: Modifier = Modifier) {
    val inProgress = (details.progress ?: 0f) > 0f
    Column(modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IosButton(
                onClick = { actions.play(details.id) },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    // How far it was watched shows in the button itself, as on Apple TV.
                    .resumeFill(details.progress),
                colors = ButtonDefaults.buttonColors(containerColor = NovaColors.IosBlue, contentColor = Color.White),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                NovaIcon(R.drawable.ic_play_arrow, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(
                    details.playLabel,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val progress = details.progress
        val duration = details.durationMs
        // Apple TV's "Ends at 10:42 PM": when it would finish if played now.
        val endsAt = rememberEndsAt(duration?.takeIf { it > 0 }?.let { it - if (inProgress) details.positionMs else 0L })
        if (progress != null && progress > 0f) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (duration != null && duration > 0) {
                        metaLine(Formats.remaining(details.positionMs, duration), endsAt)
                    } else {
                        "${(progress.coerceIn(0f, 1f) * 100).roundToInt()}% watched"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IosTextButton(onClick = { actions.play(details.id, startOver = true) }) {
                    NovaIcon(R.drawable.ic_restart_alt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Start Over")
                }
            }
        } else if (endsAt != null) {
            Text(
                endsAt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun FromShowCard(showTitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalNovaDimens.current.cardRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NovaIcon(R.drawable.ic_tv, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("From", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(showTitle, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            NovaIcon(R.drawable.ic_chevron_right, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The facts about the file, laid out like the Information section at the bottom of an Apple TV
 * page: no card, a small white heading over each grey value, the short ones two to a line.
 */
@Composable
private fun FileInfo(details: VideoDetailsUi, modifier: Modifier = Modifier) {
    val wide = listOfNotNull(
        details.fileName.takeIf { it.isNotBlank() }?.let { "File" to it },
        details.location.takeIf { it.isNotBlank() }?.let { "Location" to it },
    )
    val short = listOfNotNull(
        details.sizeLabel?.takeIf { it.isNotBlank() }?.let { "Size" to it },
        details.resolutionLabel?.takeIf { it.isNotBlank() }?.let { "Video" to it },
        details.sourceLabel.takeIf { it.isNotBlank() }?.let { "Source" to it },
    )
    if (wide.isEmpty() && short.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(title = "Information")
        wide.forEach { (label, value) -> Fact(label, value, copyable = label == "File") }
        short.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach { (label, value) -> Fact(label, value, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** A label over its value; a [copyable] one is copied by a long press, with a tick and "Copied". */
@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier, copyable: Boolean = false) {
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val hud = LocalHud.current
    val copy = if (copyable) {
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = {},
                onLongClickLabel = "Copy $label",
                onLongClick = {
                    clipboard.setText(AnnotatedString(value))
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    hud?.show(R.drawable.ic_check, "Copied")
                },
            )
    } else {
        Modifier
    }
    Column(modifier.then(copy), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = IosColors.SecondaryLabel)
    }
}
