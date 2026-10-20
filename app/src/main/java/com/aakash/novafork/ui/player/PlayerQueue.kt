package com.aakash.novafork.ui.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.common.IosColors
import com.aakash.novafork.ui.common.IosGrabber
import com.aakash.novafork.ui.common.IosSheetShape
import com.aakash.novafork.ui.common.IosSpinner
import com.aakash.novafork.ui.common.LandscapeArt
import com.aakash.novafork.ui.common.NovaChip
import com.aakash.novafork.ui.model.CastDeviceUi
import com.aakash.novafork.ui.model.PlayerActions
import com.aakash.novafork.ui.model.PlayerUiState
import com.aakash.novafork.ui.model.QueueItemUi
import com.aakash.novafork.ui.model.RepeatMode

/** Phone: the queue or the cast devices in a modal bottom sheet. */
@Composable
internal fun PlayerListSheet(
    sheet: PlayerSheet,
    state: PlayerUiState,
    actions: PlayerActions,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = IosColors.GroupedCard,
        shape = IosSheetShape,
        dragHandle = { IosGrabber() },
    ) {
        Text(
            text = sheet.title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 4.dp),
        )
        PlayerSheetContent(sheet = sheet, state = state, actions = actions, onDismiss = onDismiss)
    }
}

/** What [sheet] lists; shared by the phone sheet and the tablet side panel. */
@Composable
internal fun PlayerSheetContent(
    sheet: PlayerSheet,
    state: PlayerUiState,
    actions: PlayerActions,
    onDismiss: () -> Unit,
) {
    when (sheet) {
        PlayerSheet.QUEUE -> QueueOptions(state = state, actions = actions, onDismiss = onDismiss)
        PlayerSheet.CAST -> CastOptions(state = state, actions = actions, onDismiss = onDismiss)
    }
}

/** Shuffle and repeat chips, then the queue in play order with the playing item marked; a tap plays that item. */
@Composable
private fun QueueOptions(state: PlayerUiState, actions: PlayerActions, onDismiss: () -> Unit) {
    val queue = state.queue
    val playing = state.queueIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = playing)
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.canShuffle) {
                NovaChip(
                    selected = state.shuffle,
                    onClick = actions::toggleShuffle,
                    label = { Text(if (state.shuffle) "Shuffle on" else "Shuffle off") },
                    leadingIcon = { ChipIcon(R.drawable.ic_shuffle) },
                )
            }
            NovaChip(
                selected = state.repeatMode != RepeatMode.OFF,
                onClick = actions::cycleRepeat,
                label = { Text(state.repeatMode.label) },
                leadingIcon = { ChipIcon(state.repeatMode.iconRes()) },
            )
        }
        if (queue.isEmpty()) {
            Text(
                text = "Only this video is playing. Play a folder, a season or a playlist to queue more.",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                itemsIndexed(queue, key = { index, item -> "$index:${item.videoId}" }) { index, item ->
                    QueueRow(
                        position = index + 1,
                        item = item,
                        playing = index == playing,
                        onClick = {
                            onDismiss()
                            if (index != playing) actions.playQueueItem(index)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChipIcon(@DrawableRes icon: Int) {
    Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
}

@Composable
private fun QueueRow(position: Int, item: QueueItemUi, playing: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (playing) accent.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(role = Role.Button, onClickLabel = if (playing) null else "Play", onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.widthIn(min = 24.dp), contentAlignment = Alignment.Center) {
            if (playing) {
                Icon(
                    painter = painterResource(R.drawable.ic_play_arrow),
                    contentDescription = "Playing",
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text(
                    text = position.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .width(96.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            LandscapeArt(artwork = item.artwork, title = item.title, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (playing) accent else LocalContentColor.current,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = item.subtitle
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Cast devices found on the network; the one in use is checked, with "Play on this device" to stop. */
@Composable
private fun CastOptions(state: PlayerUiState, actions: PlayerActions, onDismiss: () -> Unit) {
    val castingTo = state.castingTo
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
    ) {
        if (castingTo != null) {
            Text(
                text = "Playing on $castingTo",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            CastRow(
                icon = R.drawable.ic_phone_android,
                label = "Stop casting",
                detail = "Play on this device again",
                onClick = {
                    onDismiss()
                    actions.stopCasting()
                },
            )
        }
        OptionHeader("Devices on your network")
        if (state.castDevices.isEmpty()) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IosSpinner(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(14.dp))
                Text(
                    text = "Looking for Chromecasts and TVs on this Wi-Fi…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                )
            }
        } else {
            state.castDevices.forEach { device ->
                CastDeviceRow(
                    device = device,
                    active = device.name == castingTo,
                    onClick = {
                        onDismiss()
                        if (device.name != castingTo) actions.startCasting(device.id)
                    },
                )
            }
        }
        CastRow(
            icon = R.drawable.ic_refresh,
            label = "Search Again",
            detail = null,
            onClick = actions::scanForCastDevices,
        )
    }
}

@Composable
private fun CastDeviceRow(device: CastDeviceUi, active: Boolean, onClick: () -> Unit) {
    CastRow(
        icon = if (active) R.drawable.ic_cast_connected else R.drawable.ic_tv,
        label = device.name,
        detail = if (active) "Casting" else null,
        onClick = onClick,
        highlighted = active,
    )
}

@Composable
private fun CastRow(
    @DrawableRes icon: Int,
    label: String,
    detail: String?,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    val tint = if (highlighted) MaterialTheme.colorScheme.primary else LocalContentColor.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = tint,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/**
 * Drawn over the (empty) video surface while the video plays on a cast device: above the centre,
 * clear of the transport buttons. In picture-in-picture only the icon and the device name fit.
 */
@Composable
internal fun CastingPlaceholder(deviceName: String, title: String, compact: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black)) {
        if (compact) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_cast_connected),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = deviceName,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .align(BiasAlignment(horizontalBias = 0f, verticalBias = -0.45f))
                    .padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_cast_connected),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Casting to $deviceName",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (title.isNotBlank()) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
