package com.aakash.novafork.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.data.AspectMode
import com.aakash.novafork.data.HwDecoding
import com.aakash.novafork.data.LayoutMode
import com.aakash.novafork.data.Settings
import com.aakash.novafork.data.ThemeMode
import com.aakash.novafork.data.TmdbHost
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.DialogUi
import com.aakash.novafork.ui.model.KeyStatus
import com.aakash.novafork.ui.model.SettingsUi
import com.aakash.novafork.ui.model.SourceUi
import com.aakash.novafork.ui.theme.NovaColors

internal enum class SettingsSection(val title: String, val summary: String, @DrawableRes val icon: Int) {
    LIBRARY("Library", "Device storage, folders and network shares", R.drawable.ic_video_library),
    ARTWORK("Artwork & metadata", "TMDB key, language and server", R.drawable.ic_image),
    PLAYER("Player", "Decoding, subtitles and audio", R.drawable.ic_smart_display),
    APPEARANCE("Appearance", "Theme and layout", R.drawable.ic_dark_mode),
    ABOUT("About", "Version and credits", R.drawable.ic_info),
}

/** The groups of one section. The first group has no title: the section's title stands for it. */
internal fun settingsGroups(section: SettingsSection, state: SettingsUi, actions: AppActions): List<SettingsGroup> =
    when (section) {
        SettingsSection.LIBRARY -> libraryGroups(state, actions)
        SettingsSection.ARTWORK -> artworkGroups(state, actions)
        SettingsSection.PLAYER -> playerGroups(state.settings, actions)
        SettingsSection.APPEARANCE -> appearanceGroups(state.settings, actions)
        SettingsSection.ABOUT -> aboutGroups(state)
    }

// Library

private fun libraryGroups(state: SettingsUi, actions: AppActions): List<SettingsGroup> {
    val settings = state.settings
    val folders = state.sources.filter { it.type == SourceType.FOLDER }
    val shares = state.sources.filter { it.type == SourceType.SMB }
    return listOf(
        SettingsGroup(
            key = "library",
            title = null,
            rows = listOf(
                SettingsRow("scan-device") { shape ->
                    SwitchRow(
                        shape = shape,
                        icon = R.drawable.ic_phone_android,
                        title = "Scan device storage",
                        summary = "Find the videos in internal storage and on SD cards",
                        checked = settings.scanDeviceStorage,
                        onCheckedChange = { on -> actions.updateSettings { it.copy(scanDeviceStorage = on) } },
                    )
                },
                SettingsRow("all-files") { shape ->
                    AllFilesAccessRow(
                        shape = shape,
                        granted = state.allFilesAccess,
                        canRequest = state.canRequestAllFilesAccess,
                        onRequest = actions::requestAllFilesAccess,
                    )
                },
            ),
        ),
        SettingsGroup(
            key = "folders",
            title = "Folders",
            rows = folders.map { folder ->
                SettingsRow("folder-${folder.id}") { shape ->
                    SourceRow(
                        shape = shape,
                        source = folder,
                        onEdit = null,
                        onRemove = { actions.showDialog(DialogUi.ConfirmRemoveSource(folder)) },
                    )
                }
            } + SettingsRow("add-folder") { shape ->
                ActionRow(
                    shape = shape,
                    icon = R.drawable.ic_create_new_folder,
                    title = "Add folder",
                    summary = "Pick a folder on this device, a USB drive or an SD card",
                    onClick = actions::addFolder,
                )
            },
        ),
        SettingsGroup(
            key = "shares",
            title = "Network shares",
            rows = shares.map { share ->
                SettingsRow("share-${share.id}") { shape ->
                    SourceRow(
                        shape = shape,
                        source = share,
                        onEdit = { actions.editSource(share) },
                        onRemove = { actions.showDialog(DialogUi.ConfirmRemoveSource(share)) },
                    )
                }
            } + SettingsRow("add-share") { shape ->
                ActionRow(
                    shape = shape,
                    icon = R.drawable.ic_lan,
                    title = "Add network share",
                    summary = "SMB / Windows share on a NAS or a computer",
                    onClick = { actions.showDialog(DialogUi.SmbServer()) },
                )
            },
        ),
        SettingsGroup(
            key = "rescan",
            title = null,
            rows = listOf(
                SettingsRow("rescan") { shape ->
                    ActionRow(
                        shape = shape,
                        icon = R.drawable.ic_refresh,
                        title = "Rescan library",
                        summary = "Look for new, changed and removed videos in every source",
                        onClick = actions::refresh,
                    )
                },
            ),
        ),
    )
}

@Composable
private fun AllFilesAccessRow(shape: Shape, granted: Boolean, canRequest: Boolean, onRequest: () -> Unit) {
    RowSurface(shape = shape) {
        RowIcon(R.drawable.ic_folder_special)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("All files access", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = when {
                    granted -> "Allowed"
                    canRequest -> "Not allowed"
                    else -> "Comes with access to videos"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (granted) NovaColors.Mint else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Lets subtitles and posters next to device videos load. Android hides those files from apps without it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (canRequest) {
            if (granted) {
                TextButton(onClick = onRequest) { Text("Manage") }
            } else {
                FilledTonalButton(onClick = onRequest) { Text("Allow") }
            }
        }
    }
}

@Composable
private fun SourceRow(shape: Shape, source: SourceUi, onEdit: (() -> Unit)?, onRemove: () -> Unit) {
    RowSurface(shape = shape, onClick = onEdit) {
        RowIcon(if (source.type == SourceType.SMB) R.drawable.ic_dns else R.drawable.ic_folder)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(source.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // A folder picked at the top of a volume is usually named like its path.
            if (!source.detail.equals(source.name, ignoreCase = true)) {
                Text(
                    text = source.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val error = source.error
            Text(
                text = when {
                    source.isScanning -> "Scanning…"
                    error != null -> error
                    source.videoCount == 1 -> "1 video"
                    else -> "${source.videoCount} videos"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (error != null && !source.isScanning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onEdit != null) {
            IconButton(onClick = onEdit) { NovaIcon(R.drawable.ic_edit, contentDescription = "Edit ${source.name}") }
        }
        IconButton(onClick = onRemove) { NovaIcon(R.drawable.ic_delete, contentDescription = "Remove ${source.name}") }
    }
}

// Artwork & metadata

private val TmdbLanguages = listOf(
    Option("en-US", "English (US)"),
    Option("en-GB", "English (UK)"),
    Option("hi-IN", "Hindi"),
    Option("ta-IN", "Tamil"),
    Option("te-IN", "Telugu"),
    Option("ml-IN", "Malayalam"),
    Option("kn-IN", "Kannada"),
    Option("bn-IN", "Bengali"),
    Option("mr-IN", "Marathi"),
    Option("es-ES", "Spanish"),
    Option("fr-FR", "French"),
    Option("de-DE", "German"),
    Option("it-IT", "Italian"),
    Option("pt-BR", "Portuguese (Brazil)"),
    Option("ja-JP", "Japanese"),
    Option("ko-KR", "Korean"),
    Option("zh-CN", "Chinese (Simplified)"),
    Option("ru-RU", "Russian"),
)

private fun artworkGroups(state: SettingsUi, actions: AppActions): List<SettingsGroup> {
    val settings = state.settings
    return listOf(
        SettingsGroup(
            key = "tmdb",
            title = null,
            rows = listOf(
                SettingsRow("tmdb-key") { shape -> TmdbKeyRow(shape, state, actions) },
                SettingsRow("tmdb-language") { shape ->
                    ChoiceRow(
                        shape = shape,
                        icon = R.drawable.ic_language,
                        title = "Language",
                        options = TmdbLanguages,
                        selected = settings.tmdbLanguage,
                        onSelect = { language -> actions.updateSettings { it.copy(tmdbLanguage = language) } },
                        note = "Titles, overviews and posters in this language. Changing it downloads them again.",
                    )
                },
                SettingsRow("tmdb-host") { shape ->
                    ChoiceRow(
                        shape = shape,
                        icon = R.drawable.ic_dns,
                        title = "Server",
                        options = TmdbHost.entries.map { Option(it, it.label) },
                        selected = settings.tmdbHost,
                        onSelect = { host -> actions.updateSettings { it.copy(tmdbHost = host) } },
                        note = "Try api.tmdb.org if posters don't load on your network",
                    )
                },
                SettingsRow("secure-dns") { shape ->
                    SwitchRow(
                        shape = shape,
                        icon = R.drawable.ic_public,
                        title = "Secure DNS",
                        summary = "Helps when your ISP blocks TMDB",
                        checked = settings.secureDns,
                        onCheckedChange = { on -> actions.updateSettings { it.copy(secureDns = on) } },
                    )
                },
                SettingsRow("prefer-local") { shape ->
                    SwitchRow(
                        shape = shape,
                        icon = R.drawable.ic_image,
                        title = "Prefer local artwork",
                        summary = "Use poster.jpg and fanart.jpg next to your videos even when TMDB has artwork",
                        checked = settings.preferLocalArtwork,
                        onCheckedChange = { on -> actions.updateSettings { it.copy(preferLocalArtwork = on) } },
                    )
                },
                SettingsRow("clear-metadata") { shape -> ClearMetadataRow(shape, onConfirm = actions::clearMetadata) },
            ),
        ),
    )
}

@Composable
private fun TmdbKeyRow(shape: Shape, state: SettingsUi, actions: AppActions) {
    val saved = state.settings.tmdbApiKey
    // The field shows the stored key until it is edited; after that it owns the text, so the
    // round trip through the store never moves the cursor or drops typed characters.
    var edited by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    RowSurface(shape = shape) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RowIcon(R.drawable.ic_key)
                Spacer(Modifier.width(16.dp))
                Text("TMDB API key", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                KeyStatusPill(state.keyStatus)
            }
            OutlinedTextField(
                value = edited ?: saved,
                onValueChange = { text ->
                    edited = text
                    actions.updateSettings { it.copy(tmdbApiKey = text.trim()) }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(if (state.hasBuiltInKey) "Using the built-in key" else "Paste your key")
                },
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        actions.checkTmdbKey()
                    },
                ),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        NovaIcon(
                            if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                            contentDescription = if (visible) "Hide key" else "Show key",
                        )
                    }
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Free key: themoviedb.org → Settings → API. Paste the API key or the Read Access Token.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.hasBuiltInKey) {
                        Text(
                            text = "A built-in key is used when this is empty",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                val checking = state.keyStatus == KeyStatus.CHECKING
                if (checking) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                TextButton(
                    onClick = actions::checkTmdbKey,
                    enabled = !checking && state.keyStatus != KeyStatus.MISSING,
                ) {
                    Text("Check")
                }
            }
        }
    }
}

@Composable
private fun KeyStatusPill(status: KeyStatus) {
    val (label, color) = when (status) {
        KeyStatus.MISSING -> "No key" to MaterialTheme.colorScheme.onSurfaceVariant
        KeyStatus.UNKNOWN -> "Not checked" to MaterialTheme.colorScheme.onSurfaceVariant
        KeyStatus.CHECKING -> "Checking…" to MaterialTheme.colorScheme.onSurfaceVariant
        KeyStatus.VALID -> "Working" to NovaColors.Mint
        KeyStatus.INVALID -> "Key not accepted" to MaterialTheme.colorScheme.error
        KeyStatus.UNREACHABLE -> "Can't reach TMDB" to MaterialTheme.colorScheme.tertiary
    }
    val icon = when (status) {
        KeyStatus.VALID -> R.drawable.ic_check_circle
        KeyStatus.INVALID, KeyStatus.UNREACHABLE -> R.drawable.ic_error
        KeyStatus.MISSING, KeyStatus.UNKNOWN, KeyStatus.CHECKING -> null
    }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f), contentColor = color) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) NovaIcon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = color)
            Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

@Composable
private fun ClearMetadataRow(shape: Shape, onConfirm: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    ActionRow(
        shape = shape,
        icon = R.drawable.ic_hide_image,
        title = "Clear metadata",
        summary = "Forget downloaded posters, details and matches, then fetch them again",
        onClick = { confirming = true },
    )
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Clear metadata?") },
            text = {
                Text("Posters, backdrops and details from TMDB are removed and downloaded again. Your videos and watch history stay.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onConfirm()
                    },
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("Cancel") }
            },
        )
    }
}

// Player

private val SubtitleEncodings = listOf(
    Option("", "Automatic"),
    Option("UTF-8", "UTF-8 (Unicode)"),
    Option("Windows-1252", "Windows-1252 (Western European)"),
    Option("Windows-1250", "Windows-1250 (Central European)"),
    Option("Windows-1251", "Windows-1251 (Cyrillic)"),
    Option("Windows-1256", "Windows-1256 (Arabic)"),
    Option("ISO-8859-1", "ISO-8859-1 (Latin-1)"),
    Option("ISO-8859-9", "ISO-8859-9 (Turkish)"),
    Option("GB18030", "GB18030 (Simplified Chinese)"),
    Option("Big5", "Big5 (Traditional Chinese)"),
    Option("Shift_JIS", "Shift_JIS (Japanese)"),
    Option("EUC-KR", "EUC-KR (Korean)"),
)

/** ISO 639-2 codes, as libVLC reports track languages. */
private val TrackLanguages = listOf(
    Option("", "Any"),
    Option("eng", "English"),
    Option("hin", "Hindi"),
    Option("tam", "Tamil"),
    Option("tel", "Telugu"),
    Option("mal", "Malayalam"),
    Option("kan", "Kannada"),
    Option("ben", "Bengali"),
    Option("mar", "Marathi"),
    Option("spa", "Spanish"),
    Option("fre", "French"),
    Option("ger", "German"),
    Option("jpn", "Japanese"),
    Option("kor", "Korean"),
)

private val SeekSteps = listOf(5, 10, 15, 30).map { Option(it, "$it seconds") }

private fun playerGroups(settings: Settings, actions: AppActions): List<SettingsGroup> = listOf(
    SettingsGroup(
        key = "playback",
        title = null,
        rows = listOf(
            SettingsRow("hw-decoding") { shape ->
                ChoiceRow(
                    shape = shape,
                    icon = R.drawable.ic_hd,
                    title = "Hardware decoding",
                    options = HwDecoding.entries.map { Option(it, it.label) },
                    selected = settings.hardwareDecoding,
                    onSelect = { mode -> actions.updateSettings { it.copy(hardwareDecoding = mode) } },
                    note = "Software only can help when a video shows green frames or stutters",
                )
            },
            SettingsRow("aspect") { shape ->
                ChoiceRow(
                    shape = shape,
                    icon = R.drawable.ic_aspect_ratio,
                    title = "Default aspect",
                    options = AspectMode.entries.map { Option(it, it.label) },
                    selected = settings.defaultAspect,
                    onSelect = { mode -> actions.updateSettings { it.copy(defaultAspect = mode) } },
                )
            },
            SettingsRow("resume") { shape ->
                SwitchRow(
                    shape = shape,
                    icon = R.drawable.ic_history,
                    title = "Resume playback",
                    summary = "Continue where you left off",
                    checked = settings.resumePlayback,
                    onCheckedChange = { on -> actions.updateSettings { it.copy(resumePlayback = on) } },
                )
            },
            SettingsRow("seek-step") { shape ->
                ChoiceRow(
                    shape = shape,
                    icon = R.drawable.ic_replay,
                    title = "Seek step",
                    options = SeekSteps,
                    selected = settings.seekStepSeconds,
                    onSelect = { seconds -> actions.updateSettings { it.copy(seekStepSeconds = seconds) } },
                    note = "Double-tap the sides of the video to jump back or forward",
                )
            },
            SettingsRow("autoplay-next") { shape ->
                SwitchRow(
                    shape = shape,
                    icon = R.drawable.ic_skip_next,
                    title = "Auto-play next episode",
                    summary = "Start the next episode when one ends",
                    checked = settings.autoPlayNext,
                    onCheckedChange = { on -> actions.updateSettings { it.copy(autoPlayNext = on) } },
                )
            },
            SettingsRow("background-audio") { shape ->
                SwitchRow(
                    shape = shape,
                    icon = R.drawable.ic_volume_up,
                    title = "Background audio",
                    summary = "Keep the sound playing after leaving the player, with controls in a notification",
                    checked = settings.backgroundAudio,
                    onCheckedChange = { on -> actions.updateSettings { it.copy(backgroundAudio = on) } },
                )
            },
        ),
    ),
    SettingsGroup(
        key = "tracks",
        title = "Subtitles and audio",
        rows = listOf(
            SettingsRow("auto-subtitles") { shape ->
                SwitchRow(
                    shape = shape,
                    icon = R.drawable.ic_subtitles,
                    title = "Auto-load subtitles",
                    summary = "Load .srt and other subtitle files found next to a video",
                    checked = settings.autoLoadSubtitles,
                    onCheckedChange = { on -> actions.updateSettings { it.copy(autoLoadSubtitles = on) } },
                )
            },
            SettingsRow("subtitle-encoding") { shape ->
                ChoiceRow(
                    shape = shape,
                    icon = R.drawable.ic_language,
                    title = "Subtitle encoding",
                    options = SubtitleEncodings,
                    selected = settings.subtitleEncoding,
                    onSelect = { encoding -> actions.updateSettings { it.copy(subtitleEncoding = encoding) } },
                    note = "Change it if subtitles show garbled letters",
                )
            },
            SettingsRow("subtitle-language") { shape ->
                ChoiceRow(
                    shape = shape,
                    icon = R.drawable.ic_subtitles,
                    title = "Preferred subtitle language",
                    options = TrackLanguages,
                    selected = settings.preferredSubtitleLanguage,
                    onSelect = { language -> actions.updateSettings { it.copy(preferredSubtitleLanguage = language) } },
                )
            },
            SettingsRow("audio-language") { shape ->
                ChoiceRow(
                    shape = shape,
                    icon = R.drawable.ic_audiotrack,
                    title = "Preferred audio language",
                    options = TrackLanguages,
                    selected = settings.preferredAudioLanguage,
                    onSelect = { language -> actions.updateSettings { it.copy(preferredAudioLanguage = language) } },
                )
            },
        ),
    ),
)

// Appearance

private fun appearanceGroups(settings: Settings, actions: AppActions): List<SettingsGroup> = listOf(
    SettingsGroup(
        key = "appearance",
        title = null,
        rows = listOf(
            SettingsRow("theme") { shape ->
                ChoiceRow(
                    shape = shape,
                    icon = R.drawable.ic_dark_mode,
                    title = "Theme",
                    options = ThemeMode.entries.map { Option(it, it.label) },
                    selected = settings.themeMode,
                    onSelect = { mode -> actions.updateSettings { it.copy(themeMode = mode) } },
                )
            },
            SettingsRow("layout") { shape ->
                ChoiceRow(
                    shape = shape,
                    icon = R.drawable.ic_grid_view,
                    title = "Layout",
                    options = LayoutMode.entries.map { Option(it, it.label) },
                    selected = settings.layoutMode,
                    onSelect = { mode -> actions.updateSettings { it.copy(layoutMode = mode) } },
                    note = "Automatic uses the tablet layout when the screen's smallest side is at least 600dp",
                )
            },
        ),
    ),
)

// About

private fun aboutGroups(state: SettingsUi): List<SettingsGroup> = listOf(
    SettingsGroup(
        key = "about",
        title = null,
        rows = listOf(
            SettingsRow("version") { shape ->
                InfoRow(shape, R.drawable.ic_info, "Version", state.versionName.ifBlank { "Unknown" })
            },
            SettingsRow("libvlc") { shape ->
                InfoRow(shape, R.drawable.ic_play_arrow, "Playback by libVLC", "The engine behind VLC, licensed under the LGPL")
            },
            SettingsRow("tmdb") { shape ->
                InfoRow(
                    shape,
                    R.drawable.ic_movie,
                    "Metadata and artwork from TMDB",
                    "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                )
            },
        ),
    ),
)
