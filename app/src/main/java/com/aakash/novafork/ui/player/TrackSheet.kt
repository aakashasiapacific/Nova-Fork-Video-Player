package com.aakash.novafork.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aakash.novafork.ui.common.IosColors
import com.aakash.novafork.ui.common.IosGrabber
import com.aakash.novafork.ui.common.IosSegmentedControl
import com.aakash.novafork.ui.common.IosSheetShape
import com.aakash.novafork.ui.model.PlayerActions
import com.aakash.novafork.ui.model.PlayerUiState

private val SheetTabs = listOf("Audio", "Subtitles", "Speed")
private const val TAB_AUDIO = 0
private const val TAB_SUBTITLES = 1
private const val TAB_SPEED = 2

private fun PlayerPanel.sheetTab(): Int = when (this) {
    PlayerPanel.AUDIO -> TAB_AUDIO
    PlayerPanel.SUBTITLES -> TAB_SUBTITLES
    PlayerPanel.SPEED, PlayerPanel.ASPECT -> TAB_SPEED
}

/** Phone track picker: Audio | Subtitles | Speed (with aspect ratio and subtitle delay) in a modal bottom sheet. */
@Composable
internal fun TrackSheet(
    initial: PlayerPanel,
    state: PlayerUiState,
    actions: PlayerActions,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by rememberSaveable(initial) { mutableIntStateOf(initial.sheetTab()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = IosColors.GroupedCard,
        shape = IosSheetShape,
        dragHandle = { IosGrabber() },
    ) {
        IosSegmentedControl(
            options = SheetTabs,
            selectedIndex = tab,
            onSelect = { tab = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            when (tab) {
                TAB_AUDIO -> {
                    OptionHeader("Audio track")
                    TrackOptions(
                        tracks = state.audioTracks,
                        emptyText = "No audio tracks",
                        onSelect = actions::selectAudioTrack,
                    )
                }
                TAB_SUBTITLES -> {
                    OptionHeader("Subtitles")
                    TrackOptions(
                        tracks = state.subtitleTracks,
                        emptyText = "No subtitles in this video",
                        onSelect = actions::selectSubtitleTrack,
                    )
                    AddSubtitleRow(
                        onClick = {
                            // The system picker covers the player anyway; come back to the video, not the sheet.
                            onDismiss()
                            actions.pickSubtitleFile()
                        },
                    )
                    DownloadSubtitlesRow(
                        onClick = {
                            // The download list opens in a sheet of its own.
                            onDismiss()
                            actions.searchSubtitles()
                        },
                    )
                    SubtitleDelayRow(delayMs = state.subtitleDelayMs, onChange = actions::setSubtitleDelay)
                }
                else -> {
                    OptionHeader("Playback speed")
                    SpeedChips(rate = state.rate, onSelect = actions::setRate)
                    OptionHeader("Aspect ratio")
                    AspectChips(aspect = state.aspect, onSelect = actions::setAspect)
                    // Also here so the "more" button reaches every timing setting without switching tabs.
                    if (state.subtitleTracks.isNotEmpty()) {
                        SubtitleDelayRow(
                            delayMs = state.subtitleDelayMs,
                            onChange = actions::setSubtitleDelay,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
