package com.aakash.novafork.ui.tablet

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.DeviceLayout
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.GlassCircleButton
import com.aakash.novafork.ui.common.GlassMenuItem
import com.aakash.novafork.ui.common.GlassStyle
import com.aakash.novafork.ui.common.NovaGlyphs
import com.aakash.novafork.ui.common.PrimaryCapsuleButton
import com.aakash.novafork.ui.common.TextAction
import com.aakash.novafork.ui.common.metaLine
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.MediaKind
import com.aakash.novafork.ui.model.VideoDetailsUi
import com.aakash.novafork.ui.theme.NovaColors
import com.aakash.novafork.ui.theme.NovaType

/**
 * Movie / episode / home video / stream page: full-screen artwork, title, meta and badges, the
 * Play capsule with Start Over, Watched and Identify circles, the description; under the fold
 * Up Next, About and Information.
 */
@Composable
internal fun TabletVideoDetailsScreen(
    details: VideoDetailsUi,
    layout: DeviceLayout,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    val isStream = details.kind == MediaKind.STREAM
    val started = (details.progress ?: 0f) > 0f
    val showKey = details.showKey
    TabletDetailsPage(
        artwork = details.artwork,
        title = details.title,
        onBack = actions::back,
        menu = detailsMenu(details, actions),
        modifier = modifier,
        fold = {
            if (details.kind == MediaKind.EPISODE) {
                val eyebrow = metaLine(details.showTitle, episodeTag(details.season, details.episode))
                if (eyebrow.isNotEmpty()) {
                    DetailsEyebrow(eyebrow, onClick = if (showKey != null) ({ actions.openShow(showKey) }) else null)
                }
            }
            DetailsTitle(details.title)
            DetailsMeta(
                meta = metaLine(details.genres.firstOrNull(), details.year?.toString(), details.runtimeLabel),
                quality = qualityBadge(details.resolutionLabel),
                rating = details.rating,
            )
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrimaryCapsuleButton(text = details.playLabel, onClick = { actions.play(details.id) })
                if (started) {
                    Spacer(Modifier.width(12.dp))
                    GlassCircleButton(
                        onClick = { actions.play(details.id, startOver = true) },
                        icon = R.drawable.ic_restart_alt,
                        contentDescription = "Start Over",
                        style = GlassStyle.Clear,
                    )
                }
                if (!isStream) {
                    Spacer(Modifier.width(12.dp))
                    GlassCircleButton(
                        onClick = { actions.setWatched(details.id, !details.watched) },
                        icon = NovaGlyphs.Check,
                        contentDescription = if (details.watched) "Mark as Unwatched" else "Mark as Watched",
                        selected = details.watched,
                        style = GlassStyle.Clear,
                    )
                    Spacer(Modifier.width(12.dp))
                    GlassCircleButton(
                        onClick = { actions.identify(details.id) },
                        icon = R.drawable.ic_edit,
                        contentDescription = "Identify",
                        style = GlassStyle.Clear,
                    )
                }
            }
            ResumeLine(
                progress = details.progress,
                label = remainingLabel(details.progress, details.durationMs, details.positionMs),
            )
            if (details.overview.isNullOrBlank() && !isStream && !details.isMatched) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "No description for this one yet. If the title looks wrong, Identify it to pick the right match.",
                    style = NovaType.subhead,
                    color = NovaColors.OnMediaSecondary,
                    modifier = Modifier.widthIn(max = LocalNovaDimens.current.descriptionMaxWidth),
                )
                TextAction("Identify…", onClick = { actions.identify(details.id) })
            } else {
                DetailsOverview(details.overview, details.title)
            }
        },
        below = {
            val next = details.nextEpisode
            if (next != null) {
                detailsSection(key = "upNext", title = "Up Next") {
                    TabletWideCard(
                        artwork = next.artwork,
                        title = next.title,
                        caption = metaLine(next.episodeTag(), minutesLabel(next.durationMs)),
                        onClick = { actions.play(next.id) },
                        onClickLabel = "Play",
                        progress = next.progress,
                        watched = next.watched,
                        menu = videoMenu(next, actions),
                        modifier = Modifier
                            .padding(horizontal = LocalNovaDimens.current.gutter)
                            .width(LocalNovaDimens.current.episodeCardWidth),
                    )
                }
            }
            aboutSection(
                title = details.title,
                overview = details.overview,
                subtitle = metaLine(details.tagline, details.genres.joinToString(", ")),
            )
            informationSection(
                listOf(
                    "Released" to (details.airDate ?: details.year?.toString()),
                    "Run Time" to details.runtimeLabel,
                    "Genres" to details.genres.joinToString(", "),
                    "Original Title" to details.originalTitle?.takeUnless { it.equals(details.title, ignoreCase = true) },
                    "Episode" to details.episodeCode,
                    "Resolution" to details.resolutionLabel,
                    "File" to details.fileName,
                    "Size" to details.sizeLabel,
                    "Location" to details.location,
                    "Source" to details.sourceLabel,
                ),
            )
        },
    )
}

/** The "…" menu: what the circles do, plus Go to Show for episodes and Remove for streams. */
private fun detailsMenu(details: VideoDetailsUi, actions: AppActions): List<GlassMenuItem> = buildList {
    val showKey = details.showKey
    if (details.kind == MediaKind.EPISODE && showKey != null) {
        add(GlassMenuItem("Go to Show", icon = R.drawable.ic_tv) { actions.openShow(showKey) })
    }
    if (details.kind != MediaKind.STREAM) {
        add(
            GlassMenuItem(
                label = if (details.watched) "Mark as Unwatched" else "Mark as Watched",
                icon = if (details.watched) R.drawable.ic_visibility_off else R.drawable.ic_check_circle,
            ) { actions.setWatched(details.id, !details.watched) },
        )
        add(GlassMenuItem("Identify…", icon = R.drawable.ic_edit) { actions.identify(details.id) })
    }
}
