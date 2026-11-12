package com.aakash.novafork.ui.settings

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.aakash.novafork.R
import com.aakash.novafork.data.Settings
import com.aakash.novafork.ui.common.HomeRow
import com.aakash.novafork.ui.common.IosColors
import com.aakash.novafork.ui.common.IosSwitch
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.model.AppActions

/**
 * Preferences › Appearance › Home Rows: Home's rows, each with a switch, dragged by their handle
 * into the order Home shows them in.
 */
internal fun homeRowsGroup(settings: Settings, actions: AppActions): SettingsGroup = SettingsGroup(
    key = "home-rows",
    title = "Home rows",
    rows = listOf(
        SettingsRow("home-rows", "Home Rows", R.drawable.ic_home) { shape ->
            HomeRowsEditor(
                order = HomeRow.ordered(settings.homeRowOrder),
                hidden = HomeRow.hidden(settings.homeHiddenRows),
                onOrder = { rows -> actions.updateSettings { it.copy(homeRowOrder = rows.joinToString(",") { row -> row.key }) } },
                onHidden = { rows -> actions.updateSettings { it.copy(homeHiddenRows = rows.joinToString(",") { row -> row.key }) } },
                shape = shape,
            )
        },
        actionRow("home-titles-restore", R.drawable.ic_visibility_off, "Show Titles Hidden from Home") { actions.restoreHomeTitles() },
    ),
    footer = "Drag a row by its handle to move it. Turned off, a row leaves Home; its page stays in the library.",
)

@Composable
private fun HomeRowsEditor(
    order: List<HomeRow>,
    hidden: Set<HomeRow>,
    onOrder: (List<HomeRow>) -> Unit,
    onHidden: (Set<HomeRow>) -> Unit,
    shape: Shape,
) {
    val haptics = LocalHapticFeedback.current
    val currentOrder by rememberUpdatedState(order)
    var dragging by remember { mutableStateOf<HomeRow?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableIntStateOf(1) }
    Column {
        order.forEachIndexed { index, row ->
            val lifted = dragging == row
            if (index > 0) RowSeparator()
            Column(
                Modifier
                    .zIndex(if (lifted) 1f else 0f)
                    .onSizeChanged { rowHeight = it.height.coerceAtLeast(1) }
                    .graphicsLayer {
                        translationY = if (lifted) offset else 0f
                        if (lifted) {
                            scaleX = 1.02f
                            scaleY = 1.02f
                        }
                    },
            ) {
                RowSurface(shape = if (index == 0 || index == order.lastIndex) shape else RectangleShape) {
                    NovaIcon(
                        R.drawable.ic_drag_handle,
                        contentDescription = "Move ${row.label}",
                        tint = IosColors.TertiaryLabel,
                        modifier = Modifier
                            .size(22.dp)
                            .pointerInput(row) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragging = row
                                        offset = 0f
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragEnd = {
                                        dragging = null
                                        offset = 0f
                                    },
                                    onDragCancel = {
                                        dragging = null
                                        offset = 0f
                                    },
                                ) { change, amount ->
                                    change.consume()
                                    offset += amount.y
                                    val rows = currentOrder
                                    val from = rows.indexOf(row)
                                    val step = (offset / rowHeight).toInt()
                                    val to = (from + step).coerceIn(0, rows.lastIndex)
                                    if (to != from) {
                                        // Moved past a neighbour: swap, and keep the finger's place on the row.
                                        offset -= (to - from) * rowHeight
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onOrder(rows.toMutableList().apply { add(to, removeAt(from)) })
                                    }
                                }
                            },
                    )
                    RowTitle(row.label, Modifier.weight(1f))
                    IosSwitch(
                        checked = row !in hidden,
                        onCheckedChange = { shown -> onHidden(if (shown) hidden - row else hidden + row) },
                    )
                }
            }
        }
    }
}
