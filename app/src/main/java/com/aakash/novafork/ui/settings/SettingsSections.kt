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
import com.aakash.novafork.ui.model.PlayerButton
import com.aakash.novafork.data.AspectMode
import com.aakash.novafork.data.DetailsHeaderStyle
import com.aakash.novafork.data.HeroStyle
import com.aakash.novafork.data.LibraryLayout
import com.aakash.novafork.data.PosterColumns
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
import com.aakash.novafork.ui.model.LibraryFilter
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

/** The grey line under a section's name on the top page: its current state, short and true. */
internal fun sectionSummary(section: SettingsSection, state: SettingsUi): String {
    val settings = state.settings
    return when (section) {
        SettingsSection.LIBRARY -> {
            val folders = state.sources.count { it.type == SourceType.FOLDER }
            val shares = state.sources.count { it.type.isNetworkShare || it.type.isMediaServer }
            listOfNotNull(
                if (folders > 0) (if (folders == 1) "1 folder" else "$folders folders") else null,
                if (shares > 0) (if (shares == 1) "1 network share" else "$shares network shares") else null,
            ).joinToString(" · ").ifEmpty { if (settings.scanDeviceStorage) "Device storage" else "No sources" }
        }
        SettingsSection.ARTWORK -> {
            val language = TmdbLanguages.firstOrNull { it.value == settings.tmdbLanguage }?.label ?: settings.tmdbLanguage
            val key = when (state.keyStatus) {
                KeyStatus.VALID -> "Key working"
                KeyStatus.INVALID -> "Key not accepted"
                KeyStatus.UNREACHABLE -> "Can't reach TMDB"
                KeyStatus.MISSING -> "No key"
                KeyStatus.UNKNOWN, KeyStatus.CHECKING -> null
            }
            listOfNotNull(language, key).joinToString(" · ")
        }
        SettingsSection.PLAYER ->
            "Hardware decoding ${settings.hardwareDecoding.label} · Seek ${settings.seekStepSeconds} s"
        SettingsSection.APPEARANCE ->
            "Layout ${settings.layoutMode.label} · Haptics ${if (settings.haptics) "on" else "off"}"
        SettingsSection.ABOUT ->
            if (state.versionName.isBlank()) "Diagnostics and credits" else "Version ${state.versionName}"
    }
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
                actionRow("duplicates", R.drawable.ic_video_library, "Find duplicates") { actions.openLibrary(LibraryFilter.DUPLICATES) },
                actionRow("hidden", R.drawable.ic_visibility_off, "Hidden Videos") { actions.navigate(Route.HiddenVideos) },
                actionRow("health", R.drawable.ic_check_circle, "Library Health") { actions.navigate(Route.LibraryHealth) },
                actionRow("export-library", R.drawable.ic_video_library, "Export Library List") { actions.exportLibrary() },
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
            footer = "All files access lets subtitles and posters next to device videos load; Android hides them without it.",
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
                        onScan = { actions.scanSource(folder.id) },
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
                        onScan = { actions.scanSource(share.id) },
                    )
                }
            } + actionRow("add-share", R.drawable.ic_lan, "Add Network Source", onClick = actions::addNetworkSource),
            footer = "SMB, FTP, SFTP, NFS, WebDAV, DLNA, Jellyfin, Emby or Plex.",
        ),
        SettingsGroup(
            key = "rescan",
            title = null,
            rows = listOf(actionRow("rescan", R.drawable.ic_refresh, "Rescan library", onClick = actions::refresh)),
            footer = "Looks for new, changed and removed videos in every source. Nova also does this each time it opens.",
        ),
        SettingsGroup(
            key = "scan-schedule",
            title = "Scan schedule",
            rows = listOf(
                choiceRow("scan-every", R.drawable.ic_refresh, "Scan in the background", ScanSchedules, state.settings.scanEveryHours) { hours ->
                    actions.updateSettings { it.copy(scanEveryHours = hours) }
                },
                switchRow("new-episode-alerts", R.drawable.ic_tv, "New episode note", state.settings.newEpisodeAlerts) { on ->
                    actions.updateSettings { it.copy(newEpisodeAlerts = on) }
                },
            ),
            footer = "Background scans run on any network when the battery isn't low. The note is quiet (no sound) and " +
                "lists episodes that arrived for shows you're watching; it needs a background scan.",
        ),
        downloadsGroup(actions),
        storageGroup(state.space, state.duplicateBytes, actions),
        wakeNasGroup(state.settings, actions),
        dataUsageGroup(),
        watchStatsGroup(state.watch),
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
private fun SourceRow(shape: Shape, source: SourceUi, onEdit: (() -> Unit)?, onRemove: () -> Unit, onScan: (() -> Unit)? = null) {
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
                if (onScan != null && !source.isScanning) {
                    NovaMenuItem(
                        text = { Text("Scan Now") },
                        leadingIcon = { NovaIcon(R.drawable.ic_refresh, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onScan()
                        },
                    )
                }
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
            footer = "Free key: themoviedb.org → Settings → API." +
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
            footer = "Changing the language downloads titles and posters again. If posters don't load, try the " +
                "api.tmdb.org server or Secure DNS. Local artwork is poster.jpg and fanart.jpg.",
        ),
        SettingsGroup(
            key = "clear",
            title = null,
            rows = listOf(
                SettingsRow("clear-metadata", "Clear metadata", R.drawable.ic_hide_image) { shape ->
                    ClearMetadataRow(shape, onConfirm = actions::clearMetadata)
                },
            ),
            footer = "Downloads posters and details again. Your videos and watch history stay.",
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

private val SubtitleSizes = listOf(
    Option(75, "Small"),
    Option(100, "Default"),
    Option(130, "Large"),
    Option(160, "Extra large"),
)

private val SubtitleColors = listOf(
    Option(0xFFFFFF, "White"),
    Option(0xFFE14D, "Yellow"),
    Option(0x6FE3F5, "Cyan"),
    Option(0x8CE68C, "Green"),
    Option(0xC8C8C8, "Grey"),
)

private val ScanSchedules = listOf(
    Option(0, "Only when Nova opens"),
    Option(6, "Every 6 hours"),
    Option(12, "Every 12 hours"),
    Option(24, "Once a day"),
)

private val NetworkBuffers = listOf(
    Option(1500, "Short (1.5 s)"),
    Option(3000, "Normal (3 s)"),
    Option(6000, "Long (6 s)"),
    Option(12_000, "Extra long (12 s)"),
)

private val SubtitlePositions = listOf(
    Option(0, "Default"),
    Option(60, "Raised"),
    Option(120, "Higher"),
)

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

/** Android's own font families, which libVLC's text renderer finds by name. */
private val SubtitleFonts = listOf(
    Option("", "Default"),
    Option("sans-serif", "Sans serif"),
    Option("serif", "Serif"),
    Option("monospace", "Monospace"),
    Option("sans-serif-condensed", "Condensed"),
)

private val SubtitleOutlines = listOf(Option(0, "None"), Option(2, "Thin"), Option(4, "Normal"), Option(6, "Thick"))

private val DeinterlaceChoices = listOf(Option(-1, "Automatic"), Option(0, "Off"), Option(1, "On"))

private val DoubleTapZones = listOf(Option(25, "Narrow"), Option(33, "Normal"), Option(40, "Wide"), Option(50, "Half each"))

/** Preferences › Player › Player Buttons: a switch per button the player can do without. */
private fun playerButtonsGroup(settings: Settings, actions: AppActions): SettingsGroup {
    val hidden = settings.hiddenPlayerButtons.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    return SettingsGroup(
        key = "player-buttons",
        title = "Player buttons",
        rows = PlayerButton.entries.map { button ->
            switchRow("player-button-${button.key}", R.drawable.ic_grid_view, button.label, button.key !in hidden) { shown ->
                val next = if (shown) hidden - button.key else hidden + button.key
                actions.updateSettings { it.copy(hiddenPlayerButtons = next.sorted().joinToString(",")) }
            }
        },
        footer = "Turned off, a button leaves the player. Cast still shows while something is casting.",
    )
}

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
            switchRow("stream-stats", R.drawable.ic_hd, "Stream stats", settings.showStreamStats) { on ->
                actions.updateSettings { it.copy(showStreamStats = on) }
            },
            choiceRow("network-buffer", R.drawable.ic_hd, "Network buffer", NetworkBuffers, settings.networkBufferMs, tile = IosPalette.Gray) { ms ->
                actions.updateSettings { it.copy(networkBufferMs = ms) }
            },
            choiceRow("deinterlace", R.drawable.ic_hd, "Deinterlace", DeinterlaceChoices, settings.deinterlace, tile = IosPalette.Gray) { mode ->
                actions.updateSettings { it.copy(deinterlace = mode) }
            },
            choiceRow("double-tap-zone", R.drawable.ic_replay, "Double-tap area", DoubleTapZones, settings.doubleTapZonePercent) { percent ->
                actions.updateSettings { it.copy(doubleTapZonePercent = percent) }
            },
        ),
        footer = "Software only can help when a video shows green frames or stutters. " +
            "Double-tap the video's sides to seek by this step. A longer network buffer rides out a slow connection " +
            "and delays the start a little.",
    ),
    playerButtonsGroup(settings, actions),
    SettingsGroup(
        key = "background",
        title = null,
        rows = listOf(
            switchRow("background-audio", R.drawable.ic_volume_up, "Background play", settings.backgroundAudio) { on ->
                actions.updateSettings { it.copy(backgroundAudio = on) }
            },
        ),
        footer = "Keeps a video's sound playing after you leave the player. Off: leaving pauses it.",
    ),
    SettingsGroup(
        key = "pip-on-leave",
        title = null,
        rows = listOf(
            switchRow("pip-on-leave", R.drawable.ic_picture_in_picture_alt, "Picture in Picture on Leave", settings.pipOnLeave) { on ->
                actions.updateSettings { it.copy(pipOnLeave = on) }
            },
        ),
        footer = "Leaving the app while a video plays shrinks it into a floating window.",
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
            switchRow("fetch-missing-subtitles", R.drawable.ic_subtitles, "Fetch missing subtitles", settings.fetchMissingSubtitles) { on ->
                actions.updateSettings { it.copy(fetchMissingSubtitles = on) }
            },
            choiceRow("audio-language", R.drawable.ic_audiotrack, "Audio language", TrackLanguages, settings.preferredAudioLanguage) { language ->
                actions.updateSettings { it.copy(preferredAudioLanguage = language) }
            },
            switchRow("captions-when-muted", R.drawable.ic_subtitles, "Captions when muted", settings.captionsWhenMuted) { on ->
                actions.updateSettings { it.copy(captionsWhenMuted = on) }
            },
            switchRow("forced-subtitles", R.drawable.ic_subtitles, "Forced subtitles only", settings.forcedSubtitlesOnly) { on ->
                actions.updateSettings { it.copy(forcedSubtitlesOnly = on) }
            },
        ),
        footer = "Change the encoding if subtitles show garbled letters. Fetch missing subtitles adds one from " +
            "OpenSubtitles when a video has none in your language (needs the key below).",
    ),
    SettingsGroup(
        key = "subtitle-style",
        title = "Subtitle style",
        rows = listOf(
            choiceRow("subtitle-size", R.drawable.ic_subtitles, "Size", SubtitleSizes, settings.subtitleSize) { size ->
                actions.updateSettings { it.copy(subtitleSize = size) }
            },
            switchRow("subtitle-box", R.drawable.ic_subtitles, "Background box", settings.subtitleBackground) { on ->
                actions.updateSettings { it.copy(subtitleBackground = on) }
            },
            choiceRow("subtitle-color", R.drawable.ic_subtitles, "Colour", SubtitleColors, settings.subtitleColor) { color ->
                actions.updateSettings { it.copy(subtitleColor = color) }
            },
            switchRow("subtitle-bold", R.drawable.ic_subtitles, "Bold", settings.subtitleBold) { on ->
                actions.updateSettings { it.copy(subtitleBold = on) }
            },
            choiceRow("subtitle-position", R.drawable.ic_subtitles, "Position", SubtitlePositions, settings.subtitleMargin) { margin ->
                actions.updateSettings { it.copy(subtitleMargin = margin) }
            },
            choiceRow("subtitle-font", R.drawable.ic_subtitles, "Font", SubtitleFonts, settings.subtitleFont) { font ->
                actions.updateSettings { it.copy(subtitleFont = font) }
            },
            choiceRow("subtitle-outline", R.drawable.ic_subtitles, "Outline", SubtitleOutlines, settings.subtitleOutline) { outline ->
                actions.updateSettings { it.copy(subtitleOutline = outline) }
            },
        ),
        footer = "Applies from the next video you open.",
    ),
    SettingsGroup(
        key = "music",
        title = "Music",
        rows = listOf(
            choiceRow("crossfade", R.drawable.ic_music_notes, "Crossfade", CrossfadeChoices, settings.crossfadeSeconds) { seconds ->
                actions.updateSettings { it.copy(crossfadeSeconds = seconds) }
            },
            switchRow("music-autoplay", R.drawable.ic_repeat, "Autoplay", settings.musicAutoplay, tile = IosPalette.Pink) { on ->
                actions.updateSettings { it.copy(musicAutoplay = on) }
            },
            switchRow("autoplay-albums", R.drawable.ic_album, "Album After Album", settings.autoplayAlbums) { on ->
                actions.updateSettings { it.copy(autoplayAlbums = on) }
            },
            switchRow("sound-check", R.drawable.ic_volume_up, "Sound Check", settings.soundCheck) { on ->
                actions.updateSettings { it.copy(soundCheck = on) }
            },
            switchRow("online-lyrics", R.drawable.ic_music_notes, "Lyrics from LRCLIB", settings.onlineLyrics) { on ->
                actions.updateSettings { it.copy(onlineLyrics = on) }
            },
            switchRow("online-music-art", R.drawable.ic_album, "Artist Photos and Covers", settings.onlineMusicArt) { on ->
                actions.updateSettings { it.copy(onlineMusicArt = on) }
            },
        ),
        footer = "Autoplay keeps the music going with similar songs when the queue ends; with Album After Album, a whole album ends into another album. Sound Check evens out song volumes from their ReplayGain tags. Lyrics from LRCLIB looks up synced lyrics for songs without a .lrc file (the title and artist are sent to lrclib.net). Artist Photos and Covers fetches artist pictures and covers for albums without one from Deezer.",
    ),
) + subtitleDownloadGroups(settings, actions)

/** Off, then one to eight seconds. */
private val CrossfadeChoices = listOf(Option(0, "Off")) + (1..8).map { Option(it, if (it == 1) "1 second" else "$it seconds") }

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
            choiceRow(
                "start-tab",
                R.drawable.ic_home,
                "Open On",
                listOf(Option("VIDEO", "Video"), Option("AUDIO", "Audio"), Option("SEARCH", "Search")),
                settings.startTab,
            ) { tab -> actions.updateSettings { it.copy(startTab = tab) } },
            switchRow("spoiler-safe", R.drawable.ic_visibility_off, "Spoiler-Safe Episodes", settings.spoilerSafe) { on ->
                actions.updateSettings { it.copy(spoilerSafe = on) }
            },
        ),
        footer = "Automatic uses the tablet layout from 600dp wide. Open On picks the tab the app starts on. Spoiler-Safe Episodes blurs the stills and hides the summaries of episodes not started yet.",
    ),
    homeRowsGroup(settings, actions),
    SettingsGroup(
        key = "headers",
        title = null,
        rows = listOf(
            choiceRow(
                "heroStyle",
                R.drawable.ic_movie,
                "Home hero",
                HeroStyle.entries.map { Option(it, it.label) },
                settings.heroStyle,
            ) { style -> actions.updateSettings { it.copy(heroStyle = style) } },
            choiceRow(
                "detailsHeader",
                R.drawable.ic_tv,
                "Title page header",
                DetailsHeaderStyle.entries.map { Option(it, it.label) },
                settings.detailsHeaderStyle,
            ) { style -> actions.updateSettings { it.copy(detailsHeaderStyle = style) } },
        ),
        footer = "Backdrop is Apple TV's look: the picture fills the top and the title sits over it.",
    ),
    SettingsGroup(
        key = "libraryLook",
        title = "Library",
        rows = listOf(
            choiceRow(
                "libraryLayout",
                R.drawable.ic_video_library,
                "Layout",
                LibraryLayout.entries.map { Option(it, it.label) },
                settings.libraryLayout,
            ) { layout -> actions.updateSettings { it.copy(libraryLayout = layout) } },
            choiceRow(
                "posterColumns",
                R.drawable.ic_video_library,
                "Posters per row",
                PosterColumns.entries.map { Option(it, it.label) },
                settings.posterColumns,
            ) { columns -> actions.updateSettings { it.copy(posterColumns = columns) } },
        ),
        footer = "Automatic fits posters to the screen; pinching a grid still resizes it.",
    ),
    SettingsGroup(
        key = "glass",
        title = "Glass",
        rows = listOf(
            SettingsRow("glass", "Glass", R.drawable.ic_brightness_6) { shape ->
                GlassRow(shape, tint = settings.glassTint) { tint -> actions.updateSettings { it.copy(glassTint = tint) } }
            },
        ),
        footer = "How see-through buttons and bars are. Tinted reads best over bright pictures.",
    ),
    SettingsGroup(
        key = "haptics",
        title = null,
        rows = listOf(
            switchRow("haptics", R.drawable.ic_tune, "Haptics", settings.haptics) { on ->
                actions.updateSettings { it.copy(haptics = on) }
            },
        ),
        footer = "Light ticks when you tap, swipe and scrub.",
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
            actionRow("share-diagnostics", R.drawable.ic_info, "Share Player Diagnostics", onClick = actions::shareDiagnostics),
        ),
        footer = "Library diagnostics shows what Nova sees in each source. Player diagnostics shares the app and " +
            "device versions with the player's recent events, for a bug report; nothing is sent by itself.",
    ),
    whatsNewGroup(state.versionName),
    SettingsGroup(
        key = "credits",
        title = "Credits",
        rows = listOf(
            infoRow("libvlc", R.drawable.ic_play_arrow, "Playback by libVLC", "LGPL"),
            infoRow("tmdb", R.drawable.ic_movie, "Metadata from TMDB", null, tile = IosPalette.Teal),
            infoRow("lrclib", R.drawable.ic_music_notes, "Lyrics from LRCLIB", null),
            infoRow("deezer", R.drawable.ic_album, "Artist photos from Deezer", null),
        ),
        footer = "libVLC is licensed under the LGPL. This product uses the TMDB API but is not endorsed or " +
            "certified by TMDB.",
    ),
)
