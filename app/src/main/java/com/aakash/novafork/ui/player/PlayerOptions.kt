package com.aakash.novafork.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.data.AspectMode
import com.aakash.novafork.ui.common.NovaChip
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.model.TrackUi

// Option rows shared by the phone track sheet and the tablet side panel.

private const val SUBTITLE_DELAY_STEP_MS = 100L

@Composable
internal fun OptionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
    )
}

/** Radio list of audio or subtitle tracks. */
@Composable
internal fun TrackOptions(
    tracks: List<TrackUi>,
    emptyText: String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tracks.isEmpty()) {
        Text(
            text = emptyText,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalContentColor.current.copy(alpha = 0.7f),
            modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    } else {
        Column(modifier.selectableGroup()) {
            tracks.forEach { track ->
                key(track.id) {
                    OptionRow(
                        selected = track.selected,
                        label = trackLabel(track),
                        onClick = { onSelect(track.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionRow(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Spacer(Modifier.width(14.dp))
            NovaIcon(R.drawable.ic_check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun AddSubtitleRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(text = "Add subtitle file…", style = MaterialTheme.typography.bodyLarge)
    }
}

/** "Subtitle delay" with −/+ 100 ms steps; positive shows subtitles later. */
@Composable
internal fun SubtitleDelayRow(delayMs: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = "Subtitle delay", style = MaterialTheme.typography.bodyLarge)
        Row(
            modifier = Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = { onChange(delayMs - SUBTITLE_DELAY_STEP_MS) },
                modifier = Modifier.semantics { contentDescription = "Show subtitles 100 ms earlier" },
            ) {
                Text(text = "−", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = formatDelay(delayMs),
                style = MaterialTheme.typography.titleMedium.merge(TextStyle(fontFeatureSettings = "tnum")),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 104.dp),
            )
            FilledTonalIconButton(
                onClick = { onChange(delayMs + SUBTITLE_DELAY_STEP_MS) },
                modifier = Modifier.semantics { contentDescription = "Show subtitles 100 ms later" },
            ) {
                Text(text = "+", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { onChange(0L) }, enabled = delayMs != 0L) { Text("Reset") }
        }
    }
}

@Composable
internal fun SpeedChips(rate: Float, onSelect: (Float) -> Unit, modifier: Modifier = Modifier) {
    ChoiceChips(
        options = SpeedPresets,
        isSelected = { isRate(rate, it) },
        label = { formatRate(it) },
        onSelect = onSelect,
        modifier = modifier,
    )
}

@Composable
internal fun AspectChips(aspect: AspectMode, onSelect: (AspectMode) -> Unit, modifier: Modifier = Modifier) {
    ChoiceChips(
        options = AspectMode.entries,
        isSelected = { it == aspect },
        label = { it.label },
        onSelect = onSelect,
        modifier = modifier,
    )
}

@Composable
private fun <T> ChoiceChips(
    options: List<T>,
    isSelected: (T) -> Boolean,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val selected = isSelected(option)
            NovaChip(
                selected = selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}
