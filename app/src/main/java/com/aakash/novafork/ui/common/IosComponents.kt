package com.aakash.novafork.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.aakash.novafork.R

/** iOS dark-mode system colours the iOS-style controls below are drawn with. */
object IosColors {
    /** systemGreen: an "on" switch. */
    val Green = Color(0xFF30D158)
    /** An "off" switch track (systemFill, dark). */
    val SwitchOff = Color(0xFF39393D)
    /** secondarySystemGroupedBackground: grouped list cards, sheets. */
    val GroupedCard = Color(0xFF1C1C1E)
    /** Alerts and menus (the dark material behind iOS's blur). */
    val Popover = Color(0xFF252527)
    /** Hairline separators. */
    val Separator = Color(0xFF38383A)
    /** secondaryLabel: section headers, values. */
    val SecondaryLabel = Color(0xFF8E8E93)
    /** tertiaryLabel: chevrons. */
    val TertiaryLabel = Color(0xFF5A5A5F)
    /** systemRed: destructive actions. */
    val Red = Color(0xFFFF453A)
}

/** The 0.5pt line iOS draws between rows. */
val Hairline = 0.5.dp

/**
 * iOS's switch: a 51×31 green pill with a white knob that slides across. [onCheckedChange] null
 * means the row around it does the toggling (as SwitchRow does) and the switch only shows state.
 */
@Composable
fun IosSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val track by animateColorAsState(if (checked) IosColors.Green else IosColors.SwitchOff, label = "switchTrack")
    val knobX by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 700f),
        label = "switchKnob",
    )
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
    } else {
        Modifier
    }
    Box(
        modifier
            .then(toggle)
            .size(width = 51.dp, height = 31.dp)
            .background(if (enabled) track else track.copy(alpha = 0.5f), CircleShape),
    ) {
        Box(
            Modifier
                .offset(x = knobX, y = 2.dp)
                .size(27.dp)
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/**
 * An iOS alert in place of Material's AlertDialog (same parameters, so call sites only change
 * the name): centred bold title, centred message, and the buttons as full-width cells under a
 * hairline, Cancel on the left. [icon] is accepted and not drawn, since iOS alerts have none.
 * Set [form] when [text] holds text fields so their text stays start-aligned.
 */
@Composable
fun NovaAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
    form: Boolean = false,
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest, modifier = modifier, properties = properties) {
        Surface(shape = RoundedCornerShape(24.dp), color = IosColors.Popover, contentColor = Color.White) {
            Column {
                // Weighted like Material's alert so a tall body (a form, a list) scrolls inside
                // the dialog and never pushes the buttons off screen.
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (title != null) {
                        ProvideTextStyle(MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center)) { title() }
                    }
                    if (text != null) {
                        if (title != null) Spacer(Modifier.height(6.dp))
                        val style = if (form) {
                            MaterialTheme.typography.bodyMedium
                        } else {
                            MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center)
                        }
                        Box(
                            Modifier
                                .weight(1f, fill = false)
                                .fillMaxWidth(),
                            contentAlignment = if (form) Alignment.TopStart else Alignment.TopCenter,
                        ) {
                            ProvideTextStyle(style) { text() }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(Hairline).background(IosColors.Separator))
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (dismissButton != null) {
                        AlertCell(bold = false, content = dismissButton)
                        Box(Modifier.width(Hairline).height(48.dp).background(IosColors.Separator))
                    }
                    AlertCell(bold = true, content = confirmButton)
                }
            }
        }
    }
}

@Composable
private fun RowScope.AlertCell(bold: Boolean, content: @Composable () -> Unit) {
    // Material's TextButton sets labelLarge itself, so the alert's 17pt button text goes in
    // through the theme: semibold for the default action, regular for Cancel, as on iOS.
    val typography = MaterialTheme.typography
    val label = typography.bodyLarge.copy(fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
        MaterialTheme(colorScheme = MaterialTheme.colorScheme, typography = typography.copy(labelLarge = label)) {
            content()
        }
    }
}

/**
 * iOS's context menu in place of Material's DropdownMenu: a wide, rounded, dark popover with
 * 17pt rows. Pair with [NovaMenuItem].
 */
@Composable
fun NovaDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = 250.dp),
        offset = offset,
        shape = RoundedCornerShape(16.dp),
        containerColor = IosColors.Popover,
        tonalElevation = 0.dp,
        shadowElevation = 16.dp,
        content = content,
    )
}

/** A row of [NovaDropdownMenu]: 17pt label, white glyph, red when [destructive]. */
@Composable
fun NovaMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val tint = if (destructive) IosColors.Red else Color.White
    DropdownMenuItem(
        text = { ProvideTextStyle(MaterialTheme.typography.bodyLarge) { text() } },
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        colors = MenuDefaults.itemColors(
            textColor = tint,
            leadingIconColor = tint,
            trailingIconColor = tint,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
    )
}

/** iOS's sheet grabber: a 36×5 grey capsule. */
@Composable
fun IosGrabber() {
    Box(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 36.dp, height = 5.dp).background(Color(0xFF5A5A5E), CircleShape))
    }
}

/** iOS sheets round their top corners more than Material's. */
val IosSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/**
 * A capsule filter, as the TV and Music apps draw them: white with black text when selected,
 * dark grey otherwise, no checkmark. Same parameters as Material's FilterChip.
 */
@Composable
fun NovaChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        selected = selected,
        shape = CircleShape,
        color = if (selected) Color.White else Color(0xFF2C2C2E),
        contentColor = if (selected) Color.Black else Color.White,
        modifier = modifier.semantics { role = Role.Tab },
    ) {
        Row(
            Modifier
                .heightIn(min = 34.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (leadingIcon != null) {
                Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) { leadingIcon() }
            }
            ProvideTextStyle(MaterialTheme.typography.labelLarge) { label() }
        }
    }
}

/**
 * iOS's activity indicator: eight spokes whose brightness steps round the circle. Takes
 * Material's CircularProgressIndicator parameters; [strokeWidth] is ignored, the spokes scale
 * with the size.
 */
@Composable
fun IosSpinner(
    modifier: Modifier = Modifier,
    color: Color = IosColors.SecondaryLabel,
    @Suppress("UNUSED_PARAMETER") strokeWidth: Dp = 0.dp,
) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val step by transition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 800, easing = LinearEasing)),
        label = "spinnerStep",
    )
    Canvas(modifier.size(40.dp)) {
        val r = size.minDimension / 2f
        val spoke = r * 0.42f
        val width = r * 0.2f
        val lead = step.toInt()
        for (i in 0 until 8) {
            val age = (lead - i + 8) % 8
            val angle = Math.toRadians(i * 45.0 - 90.0)
            val dx = kotlin.math.cos(angle).toFloat()
            val dy = kotlin.math.sin(angle).toFloat()
            drawLine(
                color = color.copy(alpha = color.alpha * (1f - age * 0.11f)),
                start = center + Offset(dx * (r - spoke), dy * (r - spoke)),
                end = center + Offset(dx * (r - width / 2f), dy * (r - width / 2f)),
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** iOS's segmented control: a grey track with the chosen segment raised in a lighter grey. */
@Composable
fun IosSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .heightIn(min = 34.dp)
            .background(Color(0xFF1C1C1E), RoundedCornerShape(10.dp))
            .padding(2.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 30.dp)
                    .background(if (selected) Color(0xFF636366) else Color.Transparent, RoundedCornerShape(8.dp))
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }
    }
}

/** iOS's selection circle for picking several items: blue with a white check when chosen. */
@Composable
fun IosCheckCircle(checked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(22.dp)
            .then(
                if (checked) {
                    Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier.border(1.5.dp, IosColors.TertiaryLabel, CircleShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            NovaIcon(R.drawable.ic_check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
        }
    }
}
