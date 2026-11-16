package com.aakash.novafork.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Preferences › Appearance › Haptics: with it off, every tick in [content] is silenced at once
 * (all of them go through Compose's LocalHapticFeedback).
 */
@Composable
fun HapticsSetting(enabled: Boolean, content: @Composable () -> Unit) {
    if (enabled) {
        content()
    } else {
        CompositionLocalProvider(LocalHapticFeedback provides SilentHaptics, content = content)
    }
}

private object SilentHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}
