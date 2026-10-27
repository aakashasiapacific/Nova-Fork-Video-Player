package com.aakash.novafork.ui.settings

import android.os.LocaleList
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import com.aakash.novafork.ui.common.IosIconButton
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.NovaTextField
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.theme.IosPalette
import com.aakash.novafork.ui.theme.NovaColors
import java.util.Locale

/**
 * Settings › Player › Subtitle downloads: the OpenSubtitles.com API key (the built-in one when
 * empty) and an optional account for a bigger daily quota, then the languages and automatic
 * downloads. What each needs explaining is in the groups' footers.
 */
internal fun subtitleDownloadGroups(settings: Settings, actions: AppActions): List<SettingsGroup> = listOf(
    SettingsGroup(
        key = "subtitle-downloads",
        title = "Subtitle downloads",
        rows = listOf(
            SettingsRow("opensubtitles-key", "OpenSubtitles API key", R.drawable.ic_key) { shape ->
                OpenSubtitlesKeyRow(shape, saved = settings.opensubtitlesApiKey, actions = actions)
            },
            SettingsRow("opensubtitles-account", "OpenSubtitles account", R.drawable.ic_lock, IosPalette.Gray) { shape ->
                OpenSubtitlesAccountRow(shape, settings, actions)
            },
        ),
        footer = "$keyNote Signed in to an account, you can download more subtitles a day.",
    ),
    SettingsGroup(
        key = "subtitle-download-languages",
        title = null,
        rows = listOf(
            SettingsRow("subtitle-languages", "Download languages", R.drawable.ic_language) { shape ->
                SubtitleLanguagesRow(shape, settings, actions)
            },
            switchRow(
                "auto-download-subtitles",
                R.drawable.ic_cloud,
                "Download automatically",
                settings.autoDownloadSubtitles,
                tile = IosPalette.Blue,
            ) { on -> actions.updateSettings { it.copy(autoDownloadSubtitles = on) } },
        ),
        // Exactly what the player will use: text that names no language falls back to the automatic ones.
        footer = "Codes or names, most wanted first, e.g. \"en, fr\" or \"Hindi, English\". Searching in " +
            "${languagesLabel(settings.subtitleLanguages, settings)}. Automatic downloads fetch one subtitle " +
            "sure to fit when a video has none in your first language. In the player: Subtitles → Download subtitles.",
    ),
)

private val hasBuiltInKey: Boolean get() = BuildConfig.OPENSUBTITLES_API_KEY.isNotBlank()

/** Where a key comes from, and whether one is needed at all. */
private val keyNote: String
    get() = if (hasBuiltInKey) {
        "Optional: a key of your own (opensubtitles.com → API consumers) replaces the built-in one."
    } else {
        "Free key: opensubtitles.com → your profile → API consumers. Needed to download subtitles."
    }

private fun deviceLocales(): List<Locale> {
    val locales = LocaleList.getDefault()
    return (0 until locales.size()).mapNotNull { locales.get(it) }
}

/** The languages [text] asks for, as names ("English, French"); the automatic ones when it names none. */
private fun languagesLabel(text: String, settings: Settings): String =
    SubtitleDownloadRules.languagesLabel(
        SubtitleDownloadRules.languages(text, settings.preferredSubtitleLanguage, deviceLocales()),
    )

/** A row's icon and title on one line, over what the row holds (a text field). */
@Composable
private fun FormRowTitle(@DrawableRes icon: Int,title: String, tile: Color = tileColor(icon), trailing: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IconTextGap)) {
        RowIcon(icon, tile)
        RowTitle(title, Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun OpenSubtitlesKeyRow(shape: Shape, saved: String, actions: AppActions) {
    // The field shows the stored key until it is edited; after that it owns the text, so the
    // round trip through the store never moves the cursor or drops typed characters.
    var edited by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val text = edited ?: saved
    RowSurface(shape = shape) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FormRowTitle(R.drawable.ic_key, "OpenSubtitles API key") {
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
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FormRowTitle(R.drawable.ic_lock, "OpenSubtitles account", tile = IosPalette.Gray)
            NovaTextField(
                value = editedUser ?: settings.opensubtitlesUsername,
                onValueChange = { value ->
                    editedUser = value
                    actions.updateSettings { it.copy(opensubtitlesUsername = value.trim()) }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Username") },
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
                placeholder = { Text("Password") },
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
    val automatic = remember(settings.preferredSubtitleLanguage) { languagesLabel("", settings) }
    RowSurface(shape = shape) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FormRowTitle(R.drawable.ic_language, "Download languages")
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
        }
    }
}

@Composable
private fun VisibilityToggle(visible: Boolean, what: String, onToggle: () -> Unit) {
    IosIconButton(onClick = onToggle) {
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
