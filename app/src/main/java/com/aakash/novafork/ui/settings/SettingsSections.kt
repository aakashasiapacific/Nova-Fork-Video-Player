package com.aakash.novafork.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.data.AspectMode
import com.aakash.novafork.data.HwDecoding
import com.aakash.novafork.data.LayoutMode
import com.aakash.novafork.data.Settings
import com.aakash.novafork.data.TmdbHost
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.ui.common.IosColors
import com.aakash.novafork.ui.common.IosIconButton
import com.aakash.novafork.ui.common.IosSpinner
import com.aakash.novafork.ui.common.IosTextButton
import com.aakash.novafork.ui.common.NovaAlertDialog
import com.aakash.novafork.ui.common.NovaDropdownMenu
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.NovaMenuItem
import com.aakash.novafork.ui.common.NovaTextField
import com.aakash.novafork.ui.common.sourceTypeIcon
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.DialogUi
import com.aakash.novafork.ui.model.KeyStatus
import com.aakash.novafork.ui.model.Route
import com.aakash.novafork.ui.model.SettingsUi
import com.aakash.novafork.ui.model.SourceUi
import com.aakash.novafork.ui.theme.IosPalette
import com.aakash.novafork.ui.theme.NovaColors

internal enum class SettingsSection(val title: String, val summary: String, @DrawableRes val icon: Int, val tile: Color) {
    LIBRARY("Sources", "Device storage, folders, network shares and playlists", R.drawable.ic_video_library, IosPalette.Blue),
    ARTWORK("Artwork & metadata", "TMDB key, language and server", R.drawable.ic_image, IosPalette.Purple),
    PLAYER("Player", "Decoding, subtitles and audio", R.drawable.ic_smart_display, IosPalette.Orange),
    APPEARANCE("Appearance", "Layout and glass", R.drawable.ic_dark_mode, IosPalette.Indigo),
    ABOUT("About", "Version, library diagnostics and credits", R.drawable.ic_info, IosPalette.Gray),
}

/** The groups of one section. The first group has no title: the section's title stands for it. */
internal fun settingsGroups(section: SettingsSection, state: SettingsUi, actions: AppActions): List<SettingsGroup> =
    when (section) {
        SettingsSection.LIBRARY -> libraryGroups(state, actions)
        SettingsSection.ARTWORK -> artworkGroups(state, actions)
        SettingsSection.PLAYER -> playerGroups(state.settings, actions)
        SettingsSection.APPEARANCE -> appearanceGroups(state.settings, actions)
        SettingsSection.ABOUT -> aboutGroups(state, actions)
    }

// Library

private fun libraryGroups(state: SettingsUi, actions: AppActions): List<SettingsGroup> {
    val settings = state.settings
    val folders = state.sources.filter { it.type == SourceType.FOLDER }
    val network = state.sources.filter { it.type.isNetworkShare || it.type.isMediaServer }
    return listOf(
        SettingsGroup(
            key = "library",
            title = null,
            rows = listOf(
                switchRow("scan-device", R.drawable.ic_phone_android, "Scan device storage", settings.scanDeviceStorage, tile = IosPalette.Gray) { on ->
                    actions.updateSettings { it.copy(scanDeviceStorage = on) }
                },
                actionRow("browse", R.drawable.ic_folder_open, "Browse sources and playlists") { actions.navigate(Route.Browse) },
                SettingsRow("all-files", "All files access", R.drawable.ic_folder_special) { shape ->
                    AllFilesAccessRow(
                        shape = shape,
                        granted = state.allFilesAccess,
                        canRequest = state.canRequestAllFilesAccess,
                        onRequest = actions::requestAllFilesAccess,
                    )
                },
            ),
            // iOS Settings explains a row under its group, not inside the row.
            footer = "Scanning finds the videos in internal storage and on SD cards. All files access lets " +
                "subtitles and posters next to device videos load: Android hides those files from apps without it.",
        ),
        SettingsGroup(
            key = "folders",
            title = "Folders",
            rows = folders.map { folder ->
                SettingsRow("folder-${folder.id}", folder.name, sourceTypeIcon(folder.type)) { shape ->
                    SourceRow(
                        shape = shape,
                        source = folder,
                        onEdit = null,
                        onRemove = { actions.showDialog(DialogUi.ConfirmRemoveSource(folder)) },
                    )
                }
            } + actionRow("add-folder", R.drawable.ic_create_new_folder, "Add Folder", onClick = actions::addFolder),
            footer = "Folders on this device, a USB drive or an SD card.",
        ),
        SettingsGroup(
            key = "shares",
            title = "Network",
            rows = network.map { share ->
                SettingsRow("share-${share.id}", share.name, sourceTypeIcon(share.type)) { shape ->
                    SourceRow(
                        shape = shape,
                        source = share,
                        onEdit = { actions.editSource(share) },
                        onRemove = { actions.showDialog(DialogUi.ConfirmRemoveSource(share)) },
                    )
                }
            } + actionRow("add-share", R.drawable.ic_lan, "Add Network Source", onClick = actions::addNetworkSource),
            footer = "SMB, FTP, SFTP, NFS, WebDAV, DLNA, Jellyfin, Emby or Plex.",
        ),
        SettingsGroup(
            key = "rescan",
            title = null,
            rows = listOf(actionRow("rescan", R.drawable.ic_refresh, "Rescan library", onClick = actions::refresh)),
            footer = "Looks for new, changed and removed videos in every source. Nova also does this quietly each time it opens.",
        ),
        backupGroup(actions),
    )
}

/** One line: whether access is allowed, and a tap to allow or manage it where Android can. */
@Composable
private fun AllFilesAccessRow(shape: Shape, granted: Boolean, canRequest: Boolean, onRequest: () -> Unit) {
    RowSurface(shape = shape, onClick = if (canRequest) onRequest else null) {
        RowIcon(R.drawable.ic_folder_special)
        RowTitle("All files access", Modifier.weight(1f))
        RowValue(
            when {
                granted -> "Allowed"
                canRequest -> "Not allowed"
                else -> "With video access"
            },
        )
        if (canRequest) RowChevron()
    }
}

/** A source in one line: its name, then how many videos (or that it scans, or what went wrong). */
@Composable
private fun SourceRow(shape: Shape, source: SourceUi, onEdit: (() -> Unit)?, onRemove: () -> Unit) {
    RowSurface(shape = shape, onClick = onEdit) {
        RowIcon(sourceTypeIcon(source.type))
        RowTitle(source.name, Modifier.weight(1f))
        val error = source.error
        RowValue(
            text = when {
                source.isScanning -> "Scanning…"
                error != null -> error
                source.videoCount == 1 -> "1 video"
                else -> "${source.videoCount} videos"
            },
            color = if (error != null && !source.isScanning) MaterialTheme.colorScheme.error else IosColors.SecondaryLabel,
        )
        // One "…" menu instead of a pencil and a bin on every row, as iOS keeps a list's actions.
        var menuOpen by remember { mutableStateOf(false) }
        Box {
            IosIconButton(onClick = { menuOpen = true }) {
                NovaIcon(R.drawable.ic_more_vert, contentDescription = "Options for ${source.name}")
            }
            NovaDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (onEdit != null) {
                    NovaMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { NovaIcon(R.drawable.ic_edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        },
                    )
                }
                NovaMenuItem(
                    text = { Text("Remove") },
                    destructive = true,
                    leadingIcon = { NovaIcon(R.drawable.ic_delete, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onRemove()
                    },
                )
            }
        }
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
                SettingsRow("tmdb-key", "TMDB API key", R.drawable.ic_key) { shape -> TmdbKeyRow(shape, state, actions) },
            ),
            footer = "Free key: themoviedb.org → Settings → API. Paste the API key or the Read Access Token." +
                if (state.hasBuiltInKey) " A built-in key is used when this is empty." else "",
        ),
        SettingsGroup(
            key = "tmdb-options",
            title = null,
            rows = listOf(
                choiceRow("tmdb-language", R.drawable.ic_language, "Language", TmdbLanguages, settings.tmdbLanguage) { language ->
                    actions.updateSettings { it.copy(tmdbLanguage = language) }
                },
                choiceRow("tmdb-host", R.drawable.ic_dns, "Server", TmdbHost.entries.map { Option(it, it.label) }, settings.tmdbHost) { host ->
                    actions.updateSettings { it.copy(tmdbHost = host) }
                },
                switchRow("secure-dns", R.drawable.ic_public, "Secure DNS", settings.secureDns) { on ->
                    actions.updateSettings { it.copy(secureDns = on) }
                },
                switchRow("prefer-local", R.drawable.ic_image, "Prefer local artwork", settings.preferLocalArtwork) { on ->
                    actions.updateSettings { it.copy(preferLocalArtwork = on) }
                },
            ),
            footer = "Titles, overviews and posters come in this language; changing it downloads them again. " +
                "If posters don't load on your network, try the api.tmdb.org server or Secure DNS. " +
                "Local artwork is poster.jpg and fanart.jpg next to your videos.",
        ),
        SettingsGroup(
            key = "clear",
            title = null,
            rows = listOf(
                SettingsRow("clear-metadata", "Clear metadata", R.drawable.ic_hide_image) { shape ->
                    ClearMetadataRow(shape, onConfirm = actions::clearMetadata)
                },
            ),
            footer = "Forgets downloaded posters, details and matches, then fetches them again. Your videos and watch history stay.",
        ),
    )
}

/** The key field under its title and status; what a key is and where to get one is in the group's footer. */
@Composable
private fun TmdbKeyRow(shape: Shape, state: SettingsUi, actions: AppActions) {
    val saved = state.settings.tmdbApiKey
    // The field shows the stored key until it is edited; after that it owns the text, so the
    // round trip through the store never moves the cursor or drops typed characters.
    var edited by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    RowSurface(shape = shape) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IconTextGap)) {
                RowIcon(R.drawable.ic_key)
                RowTitle("TMDB API key", Modifier.weight(1f))
                KeyStatusPill(state.keyStatus)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                NovaTextField(
                    value = edited ?: saved,
                    onValueChange = { text ->
                        edited = text
                        actions.updateSettings { it.copy(tmdbApiKey = text.trim()) }
                    },
                    modifier = Modifier.weight(1f),
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
                        IosIconButton(onClick = { visible = !visible }) {
                            NovaIcon(
                                if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                                contentDescription = if (visible) "Hide key" else "Show key",
                            )
                        }
                    },
                )
                Spacer(Modifier.width(8.dp))
                val checking = state.keyStatus == KeyStatus.CHECKING
                if (checking) {
                    IosSpinner(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                IosTextButton(
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

/** iOS's destructive row: the title in red, no chevron, and an alert before anything goes. */
@Composable
private fun ClearMetadataRow(shape: Shape, onConfirm: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    RowSurface(shape = shape, onClick = { confirming = true }) {
        RowIcon(R.drawable.ic_hide_image)
        Text(
            text = "Clear metadata",
            style = MaterialTheme.typography.bodyLarge,
            color = IosColors.Red,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
    if (confirming) {
        NovaAlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Clear metadata?") },
            text = {
                Text("Posters, backdrops and details from TMDB are removed and downloaded again. Your videos and watch history stay.")
            },
            confirmButton = {
                IosTextButton(
                    onClick = {
                        confirming = false
                        onConfirm()
                    },
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                IosTextButton(onClick = { confirming = false }) { Text("Cancel") }
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

/**
 * The player's settings, each tile coloured by what it is about (owner: a page of orange tiles
 * did not look like iOS): playback orange, the screen blue, sound pink, subtitles red.
 */
private fun playerGroups(settings: Settings, actions: AppActions): List<SettingsGroup> = listOf(
    SettingsGroup(
        key = "playback",
        title = null,
        rows = listOf(
            choiceRow(
                "hw-decoding",
                R.drawable.ic_hd,
                "Hardware decoding",
                HwDecoding.entries.map { Option(it, it.label) },
                settings.hardwareDecoding,
                tile = IosPalette.Gray,
            ) { mode -> actions.updateSettings { it.copy(hardwareDecoding = mode) } },
            choiceRow(
                "aspect",
                R.drawable.ic_aspect_ratio,
                "Default aspect",
                AspectMode.entries.map { Option(it, it.label) },
                settings.defaultAspect,
                tile = IosPalette.Blue,
            ) { mode -> actions.updateSettings { it.copy(defaultAspect = mode) } },
            choiceRow("seek-step", R.drawable.ic_replay, "Seek step", SeekSteps, settings.seekStepSeconds) { seconds ->
                actions.updateSettings { it.copy(seekStepSeconds = seconds) }
            },
            switchRow("resume", R.drawable.ic_history, "Resume playback", settings.resumePlayback, tile = IosPalette.Indigo) { on ->
                actions.updateSettings { it.copy(resumePlayback = on) }
            },
            switchRow("autoplay-next", R.drawable.ic_skip_next, "Auto-play next episode", settings.autoPlayNext) { on ->
                actions.updateSettings { it.copy(autoPlayNext = on) }
            },
        ),
        footer = "Software only decoding can help when a video shows green frames or stutters. " +
            "Double-tap the sides of the video to jump back or forward by the seek step.",
    ),
    SettingsGroup(
        key = "background",
        title = null,
        rows = listOf(
            switchRow("background-audio", R.drawable.ic_volume_up, "Background play", settings.backgroundAudio) { on ->
                actions.updateSettings { it.copy(backgroundAudio = on) }
            },
        ),
        footer = "Keeps a video's sound playing after you leave the player, with controls in a notification. " +
            "Off: leaving the player pauses the video.",
    ),
    SettingsGroup(
        key = "tracks",
        title = "Subtitles and audio",
        rows = listOf(
            switchRow("auto-subtitles", R.drawable.ic_subtitles, "Auto-load subtitles", settings.autoLoadSubtitles) { on ->
                actions.updateSettings { it.copy(autoLoadSubtitles = on) }
            },
            choiceRow("subtitle-encoding", R.drawable.ic_language, "Subtitle encoding", SubtitleEncodings, settings.subtitleEncoding) { encoding ->
                actions.updateSettings { it.copy(subtitleEncoding = encoding) }
            },
            choiceRow("subtitle-language", R.drawable.ic_subtitles, "Subtitle language", TrackLanguages, settings.preferredSubtitleLanguage) { language ->
                actions.updateSettings { it.copy(preferredSubtitleLanguage = language) }
            },
            choiceRow("audio-language", R.drawable.ic_audiotrack, "Audio language", TrackLanguages, settings.preferredAudioLanguage) { language ->
                actions.updateSettings { it.copy(preferredAudioLanguage = language) }
            },
        ),
        footer = "Auto-load picks up .srt and other subtitle files next to a video. Change the encoding if " +
            "subtitles show garbled letters. The languages are chosen first when a video has several tracks.",
    ),
) + subtitleDownloadGroups(settings, actions)

// Appearance

private fun appearanceGroups(settings: Settings, actions: AppActions): List<SettingsGroup> = listOf(
    SettingsGroup(
        key = "appearance",
        title = null,
        rows = listOf(
            choiceRow(
                "layout",
                R.drawable.ic_grid_view,
                "Layout",
                LayoutMode.entries.map { Option(it, it.label) },
                settings.layoutMode,
                tile = IosPalette.Blue,
            ) { mode -> actions.updateSettings { it.copy(layoutMode = mode) } },
        ),
        footer = "Automatic uses the tablet layout when the screen's smallest side is at least 600dp.",
    ),
    SettingsGroup(
        key = "glass",
        title = "Glass",
        rows = listOf(
            SettingsRow("glass", "Glass", R.drawable.ic_brightness_6) { shape ->
                GlassRow(shape, tint = settings.glassTint) { tint -> actions.updateSettings { it.copy(glassTint = tint) } }
            },
        ),
        footer = "How see-through the glass buttons and bars are, as on iOS 27. Tinted reads best over bright pictures.",
    ),
)

// About

private fun aboutGroups(state: SettingsUi, actions: AppActions): List<SettingsGroup> = listOf(
    SettingsGroup(
        key = "about",
        title = null,
        rows = listOf(
            infoRow("version", R.drawable.ic_info, "Version", state.versionName.ifBlank { "Unknown" }),
            actionRow("diagnostics", R.drawable.ic_tune, "Library diagnostics", onClick = actions::openLibraryDiagnostics),
        ),
        footer = "Diagnostics show what Nova sees: storage access, the videos Android lists and each source, to copy or share.",
    ),
    SettingsGroup(
        key = "credits",
        title = "Credits",
        rows = listOf(
            infoRow("libvlc", R.drawable.ic_play_arrow, "Playback by libVLC", "LGPL"),
            infoRow("tmdb", R.drawable.ic_movie, "Metadata from TMDB", null, tile = IosPalette.Teal),
        ),
        footer = "libVLC is the engine behind VLC, licensed under the LGPL. " +
            "This product uses the TMDB API but is not endorsed or certified by TMDB.",
    ),
)
