package com.aakash.novafork.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.common.Hairline
import com.aakash.novafork.ui.common.IosColors
import com.aakash.novafork.ui.common.IosSwitch
import com.aakash.novafork.ui.common.NovaDropdownMenu
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.NovaMenuItem

/** One row of a settings group; [content] gets the row's shape (the group's card rounds the ends). */
internal class SettingsRow(val key: String, val content: @Composable (Shape) -> Unit)

/** A titled run of rows drawn as one inset-grouped card. */
internal class SettingsGroup(
    val key: String,
    val title: String?,
    val rows: List<SettingsRow>,
    val footer: String? = null,
)

/** A choice in a ChoiceRow's menu. */
internal data class Option<T>(val value: T, val label: String)

private val CardCorner = 20.dp
private val TileSize = 29.dp
private val RowPaddingStart = 16.dp
private val IconTextGap = 14.dp
/** Separators start under the row's text, as in iOS's inset-grouped lists. */
private val SeparatorInset = RowPaddingStart + TileSize + IconTextGap

/**
 * A titled run of rows drawn as iOS draws a Settings group: one rounded card, rows split by
 * hairlines that start under the text, a grey caps header above and a grey note below.
 */
@Composable
internal fun SettingsGroupBlock(group: SettingsGroup, title: String?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.bodySmall,
                color = IosColors.SecondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 7.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CardCorner))
                .background(IosColors.GroupedCard),
        ) {
            group.rows.forEachIndexed { index, row ->
                if (index > 0) {
                    Box(
                        Modifier
                            .padding(start = SeparatorInset)
                            .fillMaxWidth()
                            .height(Hairline)
                            .background(IosColors.Separator),
                    )
                }
                key(row.key) { row.content(RectangleShape) }
            }
        }
        if (group.footer != null) {
            Text(
                text = group.footer,
                style = MaterialTheme.typography.bodySmall,
                color = IosColors.SecondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp),
            )
        }
    }
}

/**
 * The container every row sits in (the group's card draws the background). Clickable when
 * [onClick] is set; [interaction] is for other input (a toggle).
 */
@Composable
internal fun RowSurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    interaction: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(shape = shape, color = Color.Transparent, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .then(interaction)
                .heightIn(min = 48.dp)
                .padding(start = RowPaddingStart, end = 16.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IconTextGap),
            content = content,
        )
    }
}

/** iOS Settings' icon: a white glyph on a small rounded tile in the row's colour. */
@Composable
internal fun RowIcon(@DrawableRes icon: Int) {
    IconTile(icon, tileColor(icon))
}

@Composable
internal fun IconTile(@DrawableRes icon: Int, color: Color, size: Dp = TileSize) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        NovaIcon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.66f))
    }
}

/** Tile colours grouped the way iOS Settings groups its own: files blue, network indigo, etc. */
internal fun tileColor(@DrawableRes icon: Int): Color = when (icon) {
    R.drawable.ic_folder, R.drawable.ic_folder_open, R.drawable.ic_folder_special, R.drawable.ic_create_new_folder,
    R.drawable.ic_storage, R.drawable.ic_sd_storage, R.drawable.ic_phone_android, R.drawable.ic_refresh,
    -> TileBlue
    R.drawable.ic_lan, R.drawable.ic_dns, R.drawable.ic_cloud, R.drawable.ic_computer, R.drawable.ic_wifi,
    R.drawable.ic_link, R.drawable.ic_public, R.drawable.ic_language, R.drawable.ic_live_tv,
    -> TileIndigo
    R.drawable.ic_smart_display, R.drawable.ic_play_arrow, R.drawable.ic_skip_next, R.drawable.ic_replay,
    R.drawable.ic_history, R.drawable.ic_aspect_ratio, R.drawable.ic_hd, R.drawable.ic_theaters, R.drawable.ic_movie,
    R.drawable.ic_video_library, R.drawable.ic_speed, R.drawable.ic_screen_rotation,
    -> TileOrange
    R.drawable.ic_subtitles, R.drawable.ic_audiotrack, R.drawable.ic_volume_up, R.drawable.ic_music_notes,
    -> TilePink
    R.drawable.ic_dark_mode, R.drawable.ic_image, R.drawable.ic_hide_image, R.drawable.ic_grid_view,
    R.drawable.ic_visibility, R.drawable.ic_visibility_off, R.drawable.ic_tune, R.drawable.ic_edit,
    -> TilePurple
    R.drawable.ic_key, R.drawable.ic_lock, R.drawable.ic_check_circle,
    -> TileGreen
    R.drawable.ic_delete, R.drawable.ic_restart_alt, R.drawable.ic_error,
    -> TileRed
    else -> TileGrey
}

private val TileBlue = Color(0xFF0A84FF)
private val TileIndigo = Color(0xFF5E5CE6)
private val TileOrange = Color(0xFFFF9F0A)
private val TilePink = Color(0xFFFF375F)
private val TilePurple = Color(0xFFBF5AF2)
private val TileGreen = Color(0xFF30D158)
private val TileRed = Color(0xFFFF453A)
private val TileGrey = Color(0xFF8E8E93)

/** Title with up to two lines of explanation under it. */
@Composable
internal fun RowTexts(title: String, summary: String?, modifier: Modifier = Modifier, note: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        if (!summary.isNullOrBlank()) {
            Text(summary, style = MaterialTheme.typography.bodySmall, color = IosColors.SecondaryLabel)
        }
        if (!note.isNullOrBlank()) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = IosColors.SecondaryLabel)
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
        IosSwitch(checked = checked, onCheckedChange = null)
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
        NovaIcon(R.drawable.ic_chevron_right, contentDescription = null, tint = IosColors.TertiaryLabel, modifier = Modifier.size(15.dp))
    }
}

@Composable
internal fun InfoRow(shape: Shape, @DrawableRes icon: Int, title: String, summary: String?) {
    RowSurface(shape = shape) {
        RowIcon(icon)
        RowTexts(title, summary, Modifier.weight(1f))
    }
}

/**
 * iOS's pop-up button row: the title on the left, the current choice in grey on the right with
 * the up-down chevron, and a menu of the choices (a check by the current one) on tap.
 */
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
        RowTexts(title, null, Modifier.weight(1f), note = note)
        Box {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = current,
                    style = MaterialTheme.typography.bodyLarge,
                    color = IosColors.SecondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 150.dp),
                )
                NovaIcon(R.drawable.ic_chevron_up_down, contentDescription = null, tint = IosColors.SecondaryLabel, modifier = Modifier.size(14.dp))
            }
            NovaDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { option ->
                    NovaMenuItem(
                        text = { Text(option.label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = {
                            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                                if (option.value == selected) {
                                    NovaIcon(R.drawable.ic_check, contentDescription = "Selected", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        onClick = {
                            open = false
                            if (option.value != selected) onSelect(option.value)
                        },
                    )
                }
            }
        }
    }
}
