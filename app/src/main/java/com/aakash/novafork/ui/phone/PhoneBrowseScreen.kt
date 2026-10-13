package com.aakash.novafork.ui.phone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.ui.common.ActivityIndicator
import com.aakash.novafork.ui.common.GlassMenuItem
import com.aakash.novafork.ui.common.InsetGroup
import com.aakash.novafork.ui.common.InsetGroupDefaults
import com.aakash.novafork.ui.common.LargeTitleHeader
import com.aakash.novafork.ui.common.ListRow
import com.aakash.novafork.ui.common.ListRowIcon
import com.aakash.novafork.ui.common.ScanBanner
import com.aakash.novafork.ui.common.SwipeToDeleteRow
import com.aakash.novafork.ui.common.glassTabBarClearance
import com.aakash.novafork.ui.common.rememberTitleCollapsed
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.BrowseUi
import com.aakash.novafork.ui.model.DialogUi
import com.aakash.novafork.ui.model.PermissionUi
import com.aakash.novafork.ui.model.ScanUi
import com.aakash.novafork.ui.model.SourceUi
import com.aakash.novafork.ui.model.VideoCardUi
import com.aakash.novafork.ui.theme.NovaColors

/**
 * Browse: the user's sources as iOS inset-grouped sections, like the Settings app: ON THIS
 * DEVICE, FOLDERS (+ Add Folder), NETWORK SHARES (+ Add Network Share) and STREAMS (+ Add Stream,
 * Open Network Stream). Folders, shares and saved streams swipe to delete (after a confirmation);
 * Device Storage never does — removing it would only wipe its watch history until the next scan
 * brought it back.
 */
@Composable
internal fun PhoneBrowseScreen(
    browse: BrowseUi,
    scan: ScanUi,
    permission: PermissionUi,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    val collapsed = rememberTitleCollapsed(list)
    val sources = browse.sources
    val device = remember(sources) { sources.filter { it.type == SourceType.DEVICE } }
    val folders = remember(sources) { sources.filter { it.type == SourceType.FOLDER } }
    val shares = remember(sources) { sources.filter { it.type == SourceType.SMB } }
    var pending by remember { mutableStateOf<PendingRemoval?>(null) }
    val askRemove: (SourceUi) -> Unit = { pending = PendingRemoval(it.id, it.name, it.type) }
    val askRemoveStream: (VideoCardUi) -> Unit = { card ->
        val sourceId = card.sourceId
        if (sourceId != null) pending = PendingRemoval(sourceId, card.title, SourceType.STREAM)
    }

    PhoneLargeTitlePage(modifier = modifier, title = "Browse", collapsed = collapsed) {
        PhoneRefreshBox(scanning = scan.running, onRefresh = actions::refresh) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = glassTabBarClearance()),
            ) {
                item(key = "title") { LargeTitleHeader("Browse", collapsed = collapsed) }
                item(key = "scan") { ScanBanner(scan) }

                if (device.isNotEmpty() || !permission.granted) {
                    item(key = "device") {
                        InsetGroup(header = "On This Device") {
                            device.forEachIndexed { index, source ->
                                SourceRow(
                                    source = source,
                                    actions = actions,
                                    onRemove = null,
                                    showDivider = index < device.lastIndex || !permission.granted,
                                )
                            }
                            if (!permission.granted) {
                                ListRow(
                                    title = "Allow Access to Videos",
                                    titleColor = NovaColors.tint,
                                    showDivider = false,
                                    onClick = actions::requestPermission,
                                )
                            }
                        }
                    }
                    item(key = "deviceGap") { Spacer(Modifier.height(InsetGroupDefaults.SectionSpacing)) }
                }

                item(key = "folders") {
                    InsetGroup(header = "Folders") {
                        folders.forEach { source ->
                            SourceRow(source = source, actions = actions, onRemove = askRemove, showDivider = true)
                        }
                        ListRow(
                            title = "Add Folder",
                            titleColor = NovaColors.tint,
                            showDivider = false,
                            onClick = actions::addFolder,
                        )
                    }
                }
                item(key = "foldersGap") { Spacer(Modifier.height(InsetGroupDefaults.SectionSpacing)) }

                item(key = "shares") {
                    InsetGroup(header = "Network Shares") {
                        shares.forEach { source ->
                            SourceRow(source = source, actions = actions, onRemove = askRemove, showDivider = true)
                        }
                        ListRow(
                            title = "Add Network Share",
                            titleColor = NovaColors.tint,
                            showDivider = false,
                            onClick = { actions.showDialog(DialogUi.SmbServer()) },
                        )
                    }
                }
                item(key = "sharesGap") { Spacer(Modifier.height(InsetGroupDefaults.SectionSpacing)) }

                item(key = "streams") {
                    InsetGroup(
                        header = "Streams",
                        footer = "Saved streams also appear in Library. Open Network Stream plays a URL without saving it.",
                    ) {
                        browse.streams.forEach { stream ->
                            StreamRow(stream = stream, actions = actions, onRemove = askRemoveStream)
                        }
                        ListRow(
                            title = "Add Stream",
                            titleColor = NovaColors.tint,
                            onClick = { actions.showDialog(DialogUi.AddStream) },
                        )
                        ListRow(
                            title = "Open Network Stream",
                            titleColor = NovaColors.tint,
                            showDivider = false,
                            onClick = { actions.showDialog(DialogUi.OpenUrl) },
                        )
                    }
                }
            }
        }
    }

    pending?.let { removal ->
        RemoveSourceAlert(removal, actions, onClose = { pending = null })
    }
}

/**
 * A source: coloured icon square, name over its location (or its last error), video count and
 * "›". Tap opens its folders; long press offers Rescan / Edit / Remove; swipe left deletes
 * (not for Device Storage, where [onRemove] is null).
 */
@Composable
private fun SourceRow(
    source: SourceUi,
    actions: AppActions,
    onRemove: ((SourceUi) -> Unit)?,
    showDivider: Boolean,
) {
    val menu = rememberPhoneMenuState()
    val isDevice = source.type == SourceType.DEVICE
    val items = buildList {
        add(GlassMenuItem("Rescan", icon = R.drawable.ic_refresh, enabled = !source.isScanning) { actions.rescanSource(source.id) })
        if (source.type == SourceType.SMB) {
            add(GlassMenuItem("Edit…", icon = R.drawable.ic_edit) { actions.editSource(source) })
        }
        if (onRemove != null) {
            add(GlassMenuItem("Remove…", icon = R.drawable.ic_delete, destructive = true, startsSection = true) { onRemove(source) })
        }
    }
    val error = source.error
    Box(Modifier.phoneContextTarget(menu)) {
        SwipeToDeleteRow(
            onDelete = { onRemove?.invoke(source) },
            enabled = onRemove != null,
        ) {
            ListRow(
                title = if (isDevice) "Device Storage" else source.name,
                subtitle = error ?: source.detail.takeIf { it.isNotBlank() && !isDevice },
                value = source.videoCount.toString(),
                leading = { ListRowIcon(source.type.iconRes(), source.type.iconColor()) },
                trailing = when {
                    source.isScanning -> {
                        { ActivityIndicator(size = 16.dp) }
                    }
                    error != null -> {
                        {
                            Icon(
                                painterResource(R.drawable.ic_error),
                                contentDescription = "Error",
                                modifier = Modifier.size(18.dp),
                                tint = NovaColors.red,
                            )
                        }
                    }
                    else -> null
                },
                chevron = true,
                showDivider = showDivider,
                onLongClick = { menu.open = true },
                onClick = { actions.openSource(source.id) },
            )
        }
        PhoneContextMenu(menu, items, alignEnd = true)
    }
}

/** A saved stream: orange icon square, title over its URL. Tap plays; swipe or long press removes. */
@Composable
private fun StreamRow(stream: VideoCardUi, actions: AppActions, onRemove: (VideoCardUi) -> Unit) {
    val menu = rememberPhoneMenuState()
    val removable = stream.sourceId != null
    Box(Modifier.phoneContextTarget(menu)) {
        SwipeToDeleteRow(onDelete = { onRemove(stream) }, enabled = removable) {
            ListRow(
                title = stream.title,
                subtitle = stream.sourceLabel?.takeIf { it.isNotBlank() && it != stream.title && it != "Stream" },
                leading = { ListRowIcon(R.drawable.ic_live_tv, NovaColors.orange) },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(R.drawable.ic_play_arrow),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = NovaColors.tertiaryLabel,
                        )
                        Spacer(Modifier.width(2.dp))
                    }
                },
                onLongClick = { menu.open = true },
                onClick = { actions.play(stream.id) },
            )
        }
        PhoneContextMenu(menu, videoMenuItems(stream, actions, onRemoveStream = onRemove), alignEnd = true)
    }
}

/** Settings-style icon squares: gray phone, blue folder, green network, orange stream. */
internal fun SourceType.iconRes(): Int = when (this) {
    SourceType.DEVICE -> R.drawable.ic_phone_android
    SourceType.FOLDER -> R.drawable.ic_folder
    SourceType.SMB -> R.drawable.ic_lan
    SourceType.STREAM -> R.drawable.ic_live_tv
}

@Composable
private fun SourceType.iconColor(): Color = when (this) {
    SourceType.DEVICE -> NovaColors.gray
    SourceType.FOLDER -> NovaColors.tint
    SourceType.SMB -> NovaColors.green
    SourceType.STREAM -> NovaColors.orange
}
