package com.aakash.novafork.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.model.PlayerActions
import com.aakash.novafork.ui.model.PlayerUiState
import com.aakash.novafork.ui.theme.NovaColors

private val PanelWidth = 360.dp

/**
 * Tablet side panel with every track and playback option, scrolled to [section]. Slides in from
 * the end edge; null closes it. A tap outside the panel closes it too.
 */
@Composable
internal fun TrackPanel(
    section: PlayerPanel?,
    state: PlayerUiState,
    actions: PlayerActions,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shownSection = rememberLatestNonNull(section) ?: PlayerPanel.AUDIO
    // Slide from the end edge: right in LTR, left in RTL.
    val fromEnd = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1

    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(visible = section != null, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Close panel",
                        onClick = onDismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visible = section != null,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            enter = slideInHorizontally { it * fromEnd } + fadeIn(),
            exit = slideOutHorizontally { it * fromEnd } + fadeOut(),
        ) {
            PanelContent(
                section = shownSection,
                state = state,
                actions = actions,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun PanelContent(
    section: PlayerPanel,
    state: PlayerUiState,
    actions: PlayerActions,
    onDismiss: () -> Unit,
) {
    // Items are PlayerPanel.entries in order, so a section's ordinal is its list index.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = section.ordinal)
    LaunchedEffect(section) {
        listState.animateScrollToItem(section.ordinal)
    }

    Surface(
        modifier = Modifier
            .width(PanelWidth)
            .fillMaxHeight(),
        shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
        color = NovaColors.PlayerSurface,
        contentColor = Color.White,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Playback settings",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                PlayerIconButton(icon = R.drawable.ic_close, contentDescription = "Close", onClick = onDismiss)
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(PlayerPanel.entries, key = { it.name }) { item ->
                    when (item) {
                        PlayerPanel.AUDIO -> Column {
                            OptionHeader("Audio")
                            TrackOptions(
                                tracks = state.audioTracks,
                                emptyText = "No audio tracks",
                                onSelect = actions::selectAudioTrack,
                            )
                        }
                        PlayerPanel.SUBTITLES -> Column {
                            OptionHeader("Subtitles")
                            TrackOptions(
                                tracks = state.subtitleTracks,
                                emptyText = "No subtitles in this video",
                                onSelect = actions::selectSubtitleTrack,
                            )
                            AddSubtitleRow(
                                onClick = {
                                    onDismiss()
                                    actions.pickSubtitleFile()
                                },
                            )
                            SubtitleDelayRow(delayMs = state.subtitleDelayMs, onChange = actions::setSubtitleDelay)
                        }
                        PlayerPanel.SPEED -> Column {
                            OptionHeader("Speed")
                            SpeedChips(rate = state.rate, onSelect = actions::setRate)
                        }
                        PlayerPanel.ASPECT -> Column {
                            OptionHeader("Aspect ratio")
                            AspectChips(aspect = state.aspect, onSelect = actions::setAspect)
                        }
                    }
                }
            }
        }
    }
}
