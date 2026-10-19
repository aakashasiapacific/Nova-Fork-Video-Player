package com.aakash.novafork.ui.settings

import android.os.LocaleList
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.aakash.novafork.BuildConfig
import com.aakash.novafork.R
import com.aakash.novafork.data.Settings
import com.aakash.novafork.player.SubtitleDownloadRules
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.NovaTextField
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.theme.NovaColors

/**
 * Settings › Player › Subtitle downloads: the OpenSubtitles.com API key (the built-in one when
 * empty), an optional account for a bigger daily quota, the languages and automatic downloads.
 */
internal fun subtitleDownloadGroup(settings: Settings, actions: AppActions): SettingsGroup = SettingsGroup(
    key = "subtitle-downloads",
    title = "Subtitle downloads",
    rows = listOf(
        SettingsRow("opensubtitles-key") { shape ->
            OpenSubtitlesKeyRow(shape, saved = settings.opensubtitlesApiKey, actions = actions)
        },
        SettingsRow("opensubtitles-account") { shape -> OpenSubtitlesAccountRow(shape, settings, actions) },
        SettingsRow("subtitle-languages") { shape -> SubtitleLanguagesRow(shape, settings, actions) },
        SettingsRow("auto-download-subtitles") { shape ->
            SwitchRow(
                shape = shape,
                icon = R.drawable.ic_cloud,
                title = "Download subtitles automatically",
                summary = "When a video has none in your first language, fetch one sure to fit: " +
                    "the best match for a video matched with TMDB, else one timed for the file",
                checked = settings.autoDownloadSubtitles,
                onCheckedChange = { on -> actions.updateSettings { it.copy(autoDownloadSubtitles = on) } },
            )
        },
    ),
    footer = "Subtitles come from OpenSubtitles.com. In the player: Subtitles → Download subtitles. " +
        "Downloads are kept with the video for next time.",
)

private val hasBuiltInKey: Boolean get() = BuildConfig.OPENSUBTITLES_API_KEY.isNotBlank()

@Composable
private fun OpenSubtitlesKeyRow(shape: Shape, saved: String, actions: AppActions) {
    // The field shows the stored key until it is edited; after that it owns the text, so the
    // round trip through the store never moves the cursor or drops typed characters.
    var edited by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val text = edited ?: saved
    RowSurface(shape = shape) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RowIcon(R.drawable.ic_key)
                Spacer(Modifier.width(16.dp))
                Text("OpenSubtitles API key", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                when {
                    text.isNotBlank() -> StatusPill("Your key", NovaColors.Mint)
                    hasBuiltInKey -> StatusPill("Built in", NovaColors.Mint)
                    else -> StatusPill("No key", MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            NovaTextField(
                value = text,
                onValueChange = { value ->
                    edited = value
                    actions.updateSettings { it.copy(opensubtitlesApiKey = value.trim()) }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(if (hasBuiltInKey) "Using the built-in key" else "Paste your key") },
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                trailingIcon = { VisibilityToggle(visible = visible, what = "key", onToggle = { visible = !visible }) },
            )
            Text(
                text = if (hasBuiltInKey) {
                    "Optional: a key of your own (opensubtitles.com → API consumers) replaces the built-in one."
                } else {
                    "Free key: opensubtitles.com → your profile → API consumers. Needed to download subtitles."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OpenSubtitlesAccountRow(shape: Shape, settings: Settings, actions: AppActions) {
    var editedUser by remember { mutableStateOf<String?>(null) }
    var editedPassword by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    RowSurface(shape = shape) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RowIcon(R.drawable.ic_lock)
                Spacer(Modifier.width(16.dp))
                RowTexts(
                    title = "OpenSubtitles account",
                    summary = "Optional. Signed in, you can download more subtitles a day.",
                    modifier = Modifier.weight(1f),
                )
            }
            NovaTextField(
                value = editedUser ?: settings.opensubtitlesUsername,
                onValueChange = { value ->
                    editedUser = value
                    actions.updateSettings { it.copy(opensubtitlesUsername = value.trim()) }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Username") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            )
            NovaTextField(
                value = editedPassword ?: settings.opensubtitlesPassword,
                onValueChange = { value ->
                    editedPassword = value
                    actions.updateSettings { it.copy(opensubtitlesPassword = value) }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                trailingIcon = { VisibilityToggle(visible = visible, what = "password", onToggle = { visible = !visible }) },
            )
        }
    }
}

@Composable
private fun SubtitleLanguagesRow(shape: Shape, settings: Settings, actions: AppActions) {
    var edited by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    val text = edited ?: settings.subtitleLanguages
    val deviceLocales = remember {
        val locales = LocaleList.getDefault()
        (0 until locales.size()).mapNotNull { locales.get(it) }
    }
    val automatic = remember(settings.preferredSubtitleLanguage, deviceLocales) {
        SubtitleDownloadRules.languagesLabel(
            SubtitleDownloadRules.languages("", settings.preferredSubtitleLanguage, deviceLocales),
        )
    }
    // Exactly what the player will use: text that names no language falls back to the automatic ones.
    val effective = remember(text, settings.preferredSubtitleLanguage, deviceLocales) {
        SubtitleDownloadRules.languagesLabel(
            SubtitleDownloadRules.languages(text, settings.preferredSubtitleLanguage, deviceLocales),
        )
    }
    RowSurface(shape = shape) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RowIcon(R.drawable.ic_language)
                Spacer(Modifier.width(16.dp))
                Text("Download languages", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
            NovaTextField(
                value = text,
                onValueChange = { value ->
                    edited = value
                    actions.updateSettings { it.copy(subtitleLanguages = value) }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Automatic: $automatic") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            )
            Text(
                text = "Codes or names, most wanted first, e.g. \"en, fr\" or \"Hindi, English\". Searching in $effective.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VisibilityToggle(visible: Boolean, what: String, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        NovaIcon(
            if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
            contentDescription = if (visible) "Hide $what" else "Show $what",
        )
    }
}

@Composable
private fun StatusPill(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f), contentColor = color) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
