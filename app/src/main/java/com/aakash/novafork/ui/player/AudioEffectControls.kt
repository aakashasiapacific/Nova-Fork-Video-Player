package com.aakash.novafork.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aakash.novafork.R
import com.aakash.novafork.ui.common.NovaDropdownMenu
import com.aakash.novafork.ui.common.NovaMenuDivider
import com.aakash.novafork.ui.common.NovaMenuItem
import com.aakash.novafork.ui.model.SLEEP_END_OF_VIDEO
import com.aakash.novafork.ui.model.SleepTimerUi

/**
 * What the sound buttons need from the player screen: opening the equaliser sheet, and keeping
 * the controls up while one of their menus is open (a menu is a window of its own, the player's
 * auto-hide doesn't see the finger in it).
 */
@Stable
internal class PlayerAudioHost(val openEqualizer: () -> Unit) {
    /** How many menus hold the controls up. */
    var holds by mutableIntStateOf(0)
}

internal val LocalPlayerAudioHost = staticCompositionLocalOf<PlayerAudioHost?> { null }

/** The equaliser: opens its sheet; in the accent colour while it is on. */
@Composable
internal fun EqualizerButton(on: Boolean, buttonSize: Dp = 48.dp, iconSize: Dp = 24.dp) {
    val host = LocalPlayerAudioHost.current
    PlayerIconButton(
        icon = R.drawable.ic_player_equalizer,
        contentDescription = if (on) "Equaliser, on" else "Equaliser",
        onClick = { host?.openEqualizer?.invoke() },
        buttonSize = buttonSize,
        iconSize = iconSize,
        tint = if (on) MaterialTheme.colorScheme.primary else LocalContentColor.current,
    )
}

/** Night mode on and off: voices lifted, booms softened (see AudioEffects). */
@Composable
internal fun NightModeButton(on: Boolean, onClick: () -> Unit, buttonSize: Dp = 48.dp, iconSize: Dp = 24.dp) {
    PlayerToggleButton(
        icon = R.drawable.ic_player_night,
        contentDescription = "Night mode",
        checked = on,
        onClick = onClick,
        buttonSize = buttonSize,
        iconSize = iconSize,
    )
}

/**
 * The sleep timer, as in Now Playing: a moon that opens a menu of times (and the end of the
 * video), the chosen one ticked; while it runs the moon is in the accent colour with the minutes
 * left under it.
 */
@Composable
internal fun SleepTimerButton(sleep: SleepTimerUi?, onSet: (Int) -> Unit, buttonSize: Dp = 48.dp, iconSize: Dp = 24.dp) {
    var open by remember { mutableStateOf(false) }
    val host = LocalPlayerAudioHost.current
    DisposableEffect(open, host) {
        if (open) host?.let { it.holds += 1 }
        onDispose { if (open) host?.let { it.holds -= 1 } }
    }
    Box(contentAlignment = Alignment.Center) {
        PlayerIconButton(
            icon = R.drawable.ic_player_sleep,
            contentDescription = when {
                sleep == null -> "Sleep timer"
                sleep.minutesLeft == null -> "Sleep timer, end of video"
                else -> "Sleep timer, ${sleep.minutesLeft} min left"
            },
            onClick = { open = true },
            buttonSize = buttonSize,
            iconSize = iconSize,
            tint = if (sleep != null) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
        val left = sleep?.minutesLeft
        if (left != null) {
            Text(
                "$left",
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 1.dp),
            )
        }
        NovaDropdownMenu(expanded = open, onDismissRequest = { open = false }, title = "Sleep Timer") {
            VideoSleepChoices.forEach { (minutes, label) ->
                // The end of the video stands apart from the times, as iOS 27 sections a menu.
                if (minutes == SLEEP_END_OF_VIDEO) NovaMenuDivider()
                NovaMenuItem(
                    text = { Text(label) },
                    checked = (sleep?.choice ?: 0) == minutes,
                    onClick = {
                        open = false
                        onSet(minutes)
                    },
                )
            }
        }
    }
}

/** The sleep timer's choices: minutes (0 off) and how the menu names them. */
private val VideoSleepChoices = listOf(
    0 to "Off",
    15 to "15 Minutes",
    30 to "30 Minutes",
    45 to "45 Minutes",
    60 to "1 Hour",
    SLEEP_END_OF_VIDEO to "End of Video",
)
