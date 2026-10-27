package com.aakash.novafork.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.common.Hairline
import com.aakash.novafork.ui.common.glassRim
import com.aakash.novafork.ui.common.IosColors
import com.aakash.novafork.ui.common.IosSwitch
import com.aakash.novafork.ui.common.NovaDropdownMenu
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.NovaMenuItem
import com.aakash.novafork.ui.theme.IosPalette

/**
 * One row of a settings group; [content] gets the row's shape (the group's card rounds the ends).
 * [title], [icon] and [tile] are what Preferences' search finds the row by and shows for it; a
 * row without a title is not found.
 */
internal class SettingsRow(
    val key: String,
    val title: String? = null,
    @DrawableRes val icon: Int? = null,
    val tile: Color? = null,
    val content: @Composable (Shape) -> Unit,
)

/**
 * A titled run of rows drawn as one inset-grouped card. What its rows need explaining goes in
 * [footer], under the card, as iOS Settings does, so every row stays one line.
 */
internal class SettingsGroup(
    val key: String,
    val title: String?,
    val rows: List<SettingsRow>,
    val footer: String? = null,
)

/** A choice in a ChoiceRow's menu. */
internal data class Option<T>(val value: T, val label: String)

/** iOS 26/27's grouped cards round more than the old 10pt. */
internal val CardCorner = 26.dp
private val TileSize = 30.dp
/** Every row is this tall, as iOS 27 Settings' rows are. */
private val RowHeight = 52.dp
private val RowPaddingStart = 16.dp
internal val IconTextGap = 14.dp
/** Separators start under the row's text, as in iOS's inset-grouped lists. */
internal val SeparatorInset = RowPaddingStart + TileSize + IconTextGap

/**
 * A titled run of rows drawn as iOS 27 draws a Settings group: one rounded card, rows split by
 * hairlines that start under the text, a grey header above and a grey note below.
 */
@Composable
internal fun SettingsGroupBlock(group: SettingsGroup, title: String?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) GroupHeader(title)
        SettingsCard {
            group.rows.forEachIndexed { index, row ->
                if (index > 0) RowSeparator()
                key(row.key) { row.content(RectangleShape) }
            }
        }
        if (group.footer != null) GroupFooter(group.footer)
    }
}

/** A group's name over its card: sentence case and semibold, as iOS 26 headers are, not caps. */
@Composable
internal fun GroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = IosColors.SecondaryLabel,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp),
    )
}

/** The grey footnote under a group's card. */
@Composable
internal fun GroupFooter(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = IosColors.SecondaryLabel,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
    )
}

/** The rounded dark card a group's rows sit on. */
@Composable
internal fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCorner))
            .background(IosColors.GroupedCard),
        content = content,
    )
}

/** The hairline between two rows, from under the text to the card's edge. */
@Composable
internal fun RowSeparator() {
    Box(
        Modifier
            .padding(start = SeparatorInset)
            .fillMaxWidth()
            .height(Hairline)
            .background(IosColors.Separator),
    )
}

/**
 * The container every row sits in (the group's card draws the background): one [RowHeight] line
 * unless what it holds (a text field) needs more. Clickable when [onClick] is set; [interaction]
 * is for other input (a toggle).
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
                .heightIn(min = RowHeight)
                // Little room above and below: a 48dp button still fits the one-line height.
                .padding(start = RowPaddingStart, end = 16.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IconTextGap),
            content = content,
        )
    }
}

/** iOS Settings' icon: a white glyph on a small rounded tile in the row's colour. */
@Composable
internal fun RowIcon(@DrawableRes icon: Int, tile: Color = tileColor(icon)) {
    IconTile(icon, tile)
}

/**
 * iOS 27's settings tile, an icon of layered glass: a rounded square in [color] shading from a
 * lighter top to a deeper foot, a soft sheen over its upper half, the glass rim (bright along the
 * top, a faint dark line outside) and the white glyph standing on its own soft shadow.
 */
@Composable
internal fun IconTile(@DrawableRes icon: Int, color: Color, size: Dp = TileSize) {
    val shape = RoundedCornerShape(size * TileCornerRatio)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(lerp(color, Color.White, TileShine), color, lerp(color, Color.Black, TileDepth)),
                ),
            )
            .glassSheen()
            .glassRim(shape, strength = 0.9f),
        contentAlignment = Alignment.Center,
    ) {
        val glyph = Modifier.size(size * 0.6f)
        // The shadow layer: the same glyph in black, a hair lower.
        NovaIcon(icon, contentDescription = null, tint = Color.Black.copy(alpha = 0.22f), modifier = glyph.offset(y = size * 0.025f))
        NovaIcon(icon, contentDescription = null, tint = Color.White, modifier = glyph)
    }
}

/** The soft light that falls on glass: white fading out over the upper half of what it is on. */
internal fun Modifier.glassSheen(): Modifier = drawWithContent {
    drawContent()
    drawRect(
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.22f),
            0.55f to Color.White.copy(alpha = 0.05f),
            1f to Color.Transparent,
            endY = size.height * 0.6f,
        ),
        size = size.copy(height = size.height * 0.6f),
    )
}

private const val TileCornerRatio = 0.3f
/** How much lighter the tile's top is than its colour, and how much deeper its foot is. */
private const val TileShine = 0.2f
private const val TileDepth = 0.14f

/**
 * Tile colours by meaning, in iOS 26/27's palette: files and storage blue, network indigo,
 * languages and the web teal, playback orange, subtitles and anything that removes red, sound
 * pink, pictures and tools purple, keys and sign-ins green; the rest grey. A row whose meaning
 * differs from its glyph's passes its own colour.
 */
internal fun tileColor(@DrawableRes icon: Int): Color = when (icon) {
    R.drawable.ic_folder, R.drawable.ic_folder_open, R.drawable.ic_folder_special, R.drawable.ic_create_new_folder,
    R.drawable.ic_storage, R.drawable.ic_sd_storage, R.drawable.ic_phone_android, R.drawable.ic_refresh,
    R.drawable.ic_video_library,
    -> IosPalette.Blue
    R.drawable.ic_lan, R.drawable.ic_dns, R.drawable.ic_cloud, R.drawable.ic_computer, R.drawable.ic_wifi,
    R.drawable.ic_link, R.drawable.ic_live_tv, R.drawable.ic_tv, R.drawable.ic_dark_mode,
    -> IosPalette.Indigo
    R.drawable.ic_language, R.drawable.ic_public,
    -> IosPalette.Teal
    R.drawable.ic_smart_display, R.drawable.ic_play_arrow, R.drawable.ic_skip_next, R.drawable.ic_replay,
    R.drawable.ic_history, R.drawable.ic_aspect_ratio, R.drawable.ic_hd, R.drawable.ic_theaters, R.drawable.ic_movie,
    R.drawable.ic_speed, R.drawable.ic_screen_rotation, R.drawable.ic_timer,
    -> IosPalette.Orange
    R.drawable.ic_subtitles, R.drawable.ic_delete, R.drawable.ic_error, R.drawable.ic_hide_image,
    -> IosPalette.Red
    R.drawable.ic_audiotrack, R.drawable.ic_volume_up, R.drawable.ic_music_notes,
    -> IosPalette.Pink
    R.drawable.ic_image, R.drawable.ic_grid_view, R.drawable.ic_visibility, R.drawable.ic_visibility_off,
    R.drawable.ic_tune, R.drawable.ic_edit,
    -> IosPalette.Purple
    R.drawable.ic_key, R.drawable.ic_lock, R.drawable.ic_check_circle, R.drawable.ic_restart_alt,
    -> IosPalette.Green
    R.drawable.ic_brightness_6,
    -> IosPalette.Cyan
    else -> IosPalette.Gray
}

/** A row's name: one line, the rest of the row's width. */
@Composable
internal fun RowTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** The grey value at a row's end ("Automatic", "Allowed"). */
@Composable
internal fun RowValue(text: String, color: Color = IosColors.SecondaryLabel) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 170.dp),
    )
}

/** The small grey chevron of a row that opens something. */
@Composable
internal fun RowChevron() {
    NovaIcon(R.drawable.ic_chevron_right, contentDescription = null, tint = IosColors.TertiaryLabel, modifier = Modifier.size(14.dp))
}

@Composable
internal fun SwitchRow(
    shape: Shape,
    @DrawableRes icon: Int,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tile: Color = tileColor(icon),
) {
    // The switch itself ticks as it flips.
    RowSurface(
        shape = shape,
        interaction = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        RowIcon(icon, tile)
        RowTitle(title, Modifier.weight(1f))
        IosSwitch(checked = checked, onCheckedChange = null)
    }
}

/** A row that opens something: a grey [value] if any, then the chevron. */
@Composable
internal fun ActionRow(
    shape: Shape,
    @DrawableRes icon: Int,
    title: String,
    onClick: () -> Unit,
    value: String? = null,
    tile: Color = tileColor(icon),
) {
    RowSurface(shape = shape, onClick = onClick) {
        RowIcon(icon, tile)
        RowTitle(title, Modifier.weight(1f))
        if (value != null) RowValue(value)
        RowChevron()
    }
}

@Composable
internal fun InfoRow(shape: Shape, @DrawableRes icon: Int, title: String, value: String?, tile: Color = tileColor(icon)) {
    RowSurface(shape = shape) {
        RowIcon(icon, tile)
        RowTitle(title, Modifier.weight(1f))
        if (value != null) RowValue(value)
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
    tile: Color = tileColor(icon),
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.value == selected }?.label ?: selected.toString()
    RowSurface(shape = shape, onClick = { open = true }) {
        RowIcon(icon, tile)
        RowTitle(title, Modifier.weight(1f))
        Box {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                RowValue(current)
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

// Rows with what search needs to find them: the same title and icon the row shows.

internal fun switchRow(
    key: String,
    @DrawableRes icon: Int,
    title: String,
    checked: Boolean,
    tile: Color = tileColor(icon),
    onCheckedChange: (Boolean) -> Unit,
): SettingsRow = SettingsRow(key, title, icon, tile) { shape ->
    SwitchRow(shape = shape, icon = icon, title = title, checked = checked, onCheckedChange = onCheckedChange, tile = tile)
}

internal fun actionRow(
    key: String,
    @DrawableRes icon: Int,
    title: String,
    value: String? = null,
    tile: Color = tileColor(icon),
    onClick: () -> Unit,
): SettingsRow = SettingsRow(key, title, icon, tile) { shape ->
    ActionRow(shape = shape, icon = icon, title = title, onClick = onClick, value = value, tile = tile)
}

internal fun infoRow(key: String, @DrawableRes icon: Int, title: String, value: String?, tile: Color = tileColor(icon)): SettingsRow =
    SettingsRow(key, title, icon, tile) { shape -> InfoRow(shape = shape, icon = icon, title = title, value = value, tile = tile) }

internal fun <T> choiceRow(
    key: String,
    @DrawableRes icon: Int,
    title: String,
    options: List<Option<T>>,
    selected: T,
    tile: Color = tileColor(icon),
    onSelect: (T) -> Unit,
): SettingsRow = SettingsRow(key, title, icon, tile) { shape ->
    ChoiceRow(shape = shape, icon = icon, title = title, options = options, selected = selected, onSelect = onSelect, tile = tile)
}
