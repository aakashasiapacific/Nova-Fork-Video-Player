package com.aakash.novafork.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.data.EQUALIZER_CUSTOM
import com.aakash.novafork.data.Settings
import com.aakash.novafork.player.AudioEffects
import com.aakash.novafork.ui.settings.IosSlider
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The equaliser, for songs and videos alike (it is one setting): a switch, then libVLC's presets
 * with a tick on the chosen one and "Custom", whose ten bands get a slider each. Every change is
 * saved at once ([onUpdate]) and heard at once by whichever player is playing.
 */
@Composable
fun EqualizerSheet(settings: Settings, onUpdate: ((Settings) -> Settings) -> Unit, onDismiss: () -> Unit) {
    val presets = remember { AudioEffects.presetNames() + EQUALIZER_CUSTOM }
    val frequencies = remember { AudioEffects.bandFrequencies() }
    val chosen = settings.equalizerPreset
    val on = chosen.isNotBlank()
    // What the switch turns back on: the last preset chosen.
    var lastPreset by rememberSaveable { mutableStateOf(chosen.ifBlank { presets.first() }) }
    IosSheet(onDismissRequest = onDismiss, title = "Equaliser") { _ ->
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = SheetMaxHeight)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
        ) {
            EqualizerCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = RowHeight)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Equaliser", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IosSwitch(
                        checked = on,
                        onCheckedChange = { enable -> onUpdate { it.copy(equalizerPreset = if (enable) lastPreset else "") } },
                    )
                }
            }
            if (on) {
                SectionLabel("Presets")
                EqualizerCard {
                    presets.forEachIndexed { index, name ->
                        if (index > 0) RowDivider()
                        PresetRow(
                            name = name,
                            chosen = name == chosen,
                            onClick = {
                                lastPreset = name
                                onUpdate { current ->
                                    if (name == EQUALIZER_CUSTOM && current.equalizerBands.isBlank()) {
                                        // The person's own bands start from what they were just hearing.
                                        val start = AudioEffects.presetGains(current.equalizerPreset)
                                        current.copy(equalizerPreset = name, equalizerBands = AudioEffects.formatBands(start))
                                    } else {
                                        current.copy(equalizerPreset = name)
                                    }
                                }
                            },
                        )
                    }
                }
                if (chosen == EQUALIZER_CUSTOM) {
                    SectionLabel("Custom")
                    EqualizerCard {
                        CustomBands(
                            csv = settings.equalizerBands,
                            frequencies = frequencies,
                            onChange = { csv -> onUpdate { it.copy(equalizerBands = csv) } },
                        )
                    }
                }
            }
        }
    }
}

/** A preset: its name, and a tick when it is the one playing. */
@Composable
private fun PresetRow(name: String, chosen: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (chosen) {
            NovaIcon(R.drawable.ic_check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * The ten bands of "Custom": the frequency, a slider from -20 to +20 dB and its value. A slider
 * saves when the finger lifts, so dragging doesn't write the setting on every move.
 */
@Composable
private fun CustomBands(csv: String, frequencies: List<Float>, onChange: (String) -> Unit) {
    val gains = remember(csv, frequencies.size) { AudioEffects.parseBands(csv, frequencies.size).toMutableStateList() }
    Column(Modifier.padding(vertical = 6.dp)) {
        frequencies.forEachIndexed { band, hz ->
            val label = AudioEffects.frequencyLabel(hz)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(BandRowHeight)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = IosColors.SecondaryLabel,
                    maxLines = 1,
                    modifier = Modifier.width(BandLabelWidth),
                )
                IosSlider(
                    value = (gains[band] - AudioEffects.MIN_GAIN) / (AudioEffects.MAX_GAIN - AudioEffects.MIN_GAIN),
                    onValueChange = { fraction ->
                        gains[band] = AudioEffects.MIN_GAIN + fraction * (AudioEffects.MAX_GAIN - AudioEffects.MIN_GAIN)
                    },
                    onValueChangeFinished = { onChange(AudioEffects.formatBands(gains)) },
                    description = "$label gain",
                    modifier = Modifier.weight(1f),
                )
                Text(
                    gainLabel(gains[band]),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.width(BandValueWidth),
                )
            }
        }
    }
}

/** "+4 dB", "−3 dB", "0 dB". */
private fun gainLabel(gain: Float): String {
    val whole = gain.roundToInt()
    return when {
        whole > 0 -> "+$whole dB"
        whole < 0 -> String.format(Locale.ROOT, "−%d dB", -whole)
        else -> "0 dB"
    }
}

@Composable
private fun EqualizerCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCorner))
            .background(Color.White.copy(alpha = 0.07f)),
        content = content,
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = IosColors.SecondaryLabel,
        modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 8.dp),
    )
}

@Composable
private fun RowDivider() {
    Box(
        Modifier
            .padding(start = 16.dp)
            .fillMaxWidth()
            .height(Hairline)
            .background(IosColors.Separator),
    )
}

private val RowHeight = 48.dp
private val BandRowHeight = 44.dp
private val BandLabelWidth = 60.dp
private val BandValueWidth = 52.dp
private val CardCorner = 18.dp

/** Taller lists scroll inside the sheet. */
private val SheetMaxHeight = 560.dp
