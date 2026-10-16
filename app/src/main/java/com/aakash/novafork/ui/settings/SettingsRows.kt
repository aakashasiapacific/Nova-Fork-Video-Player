package com.aakash.novafork.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aakash.novafork.ui.common.NovaIcon

/** One row of a settings group; [content] gets the row's shape (rounder at the group's ends). */
internal class SettingsRow(val key: String, val content: @Composable (Shape) -> Unit)

/** A titled run of rows drawn as one rounded block. */
internal class SettingsGroup(
    val key: String,
    val title: String?,
    val rows: List<SettingsRow>,
    val footer: String? = null,
)

/** A choice in a radio list. */
internal data class Option<T>(val value: T, val label: String)

private val OuterCorner = 20.dp
private val InnerCorner = 4.dp

private fun rowShape(index: Int, count: Int): Shape = RoundedCornerShape(
    topStart = if (index == 0) OuterCorner else InnerCorner,
    topEnd = if (index == 0) OuterCorner else InnerCorner,
    bottomStart = if (index == count - 1) OuterCorner else InnerCorner,
    bottomEnd = if (index == count - 1) OuterCorner else InnerCorner,
)

@Composable
internal fun SettingsGroupBlock(group: SettingsGroup, title: String?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            group.rows.forEachIndexed { index, row ->
                key(row.key) { row.content(rowShape(index, group.rows.size)) }
            }
        }
        if (group.footer != null) {
            Text(
                text = group.footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
    }
}

/**
 * The rounded container every row sits in. Clickable when [onClick] is set; [interaction] is for
 * other input (a toggle) and, like the click, stays inside the rounded shape.
 */
@Composable
internal fun RowSurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    interaction: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .then(interaction)
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
internal fun RowIcon(@DrawableRes icon: Int) {
    NovaIcon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
}

/** Title with up to two lines of explanation under it. */
@Composable
internal fun RowTexts(title: String, summary: String?, modifier: Modifier = Modifier, note: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        if (!summary.isNullOrBlank()) {
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!note.isNullOrBlank()) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SwitchRow(
    shape: Shape,
    @DrawableRes icon: Int,
    title: String,
    summary: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    RowSurface(
        shape = shape,
        interaction = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        RowIcon(icon)
        RowTexts(title, summary, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
internal fun ActionRow(
    shape: Shape,
    @DrawableRes icon: Int,
    title: String,
    summary: String?,
    onClick: () -> Unit,
) {
    RowSurface(shape = shape, onClick = onClick) {
        RowIcon(icon)
        RowTexts(title, summary, Modifier.weight(1f))
    }
}

@Composable
internal fun InfoRow(shape: Shape, @DrawableRes icon: Int, title: String, summary: String?) {
    RowSurface(shape = shape) {
        RowIcon(icon)
        RowTexts(title, summary, Modifier.weight(1f))
    }
}

/** Shows the current choice; tapping opens a radio list. */
@Composable
internal fun <T> ChoiceRow(
    shape: Shape,
    @DrawableRes icon: Int,
    title: String,
    options: List<Option<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    note: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.value == selected }?.label ?: selected.toString()
    RowSurface(shape = shape, onClick = { open = true }) {
        RowIcon(icon)
        RowTexts(title, current, Modifier.weight(1f), note = note)
    }
    if (open) {
        ChoiceDialog(
            title = title,
            options = options,
            selected = selected,
            onSelect = { value ->
                open = false
                if (value != selected) onSelect(value)
            },
            onDismiss = { open = false },
        )
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Option<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .selectableGroup()
                    .verticalScroll(rememberScrollState()),
            ) {
                options.forEach { option ->
                    val isSelected = option.value == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option.value) })
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = isSelected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
