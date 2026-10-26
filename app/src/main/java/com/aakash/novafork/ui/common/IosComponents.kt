package com.aakash.novafork.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.aakash.novafork.R
import com.aakash.novafork.ui.theme.IosPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** iOS dark-mode system colours the iOS-style controls below are drawn with. */
object IosColors {
    /** systemGreen. */
    val Green = IosPalette.Green
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
    val Red = IosPalette.Red
}

/** The 0.5pt line iOS draws between rows. */
val Hairline = 0.5.dp

/**
 * iOS 26/27's Liquid Glass switch: a long, slim track in the accent blue (owner: blue, not
 * green) with a white pill-shaped knob, and a light tick each time it flips. While it is held,
 * and while it slides, the knob turns to clear glass, grows a little past the track and
 * stretches the way it travels (its leading end races ahead, the trailing end follows a beat
 * later), then settles back into the solid white pill. [onCheckedChange] null means the row
 * around it does the toggling (as SwitchRow does) and the switch only shows state; such a row can
 * pass its [interactionSource] so holding the row turns the knob to glass too.
 */
@Composable
fun IosSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val haptics = LocalHapticFeedback.current
    var shownChecked by remember { mutableStateOf(checked) }
    LaunchedEffect(checked) {
        if (checked != shownChecked) {
            shownChecked = checked
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val track by animateColorAsState(if (checked) MaterialTheme.colorScheme.primary else IosColors.SwitchOff, label = "switchTrack")
    // Where each end of the knob is along its travel, 0 (off) to 1 (on), each on its own spring.
    val target = if (checked) 1f else 0f
    val lead = spring<Float>(dampingRatio = 0.72f, stiffness = 900f)
    val follow = spring<Float>(dampingRatio = 0.8f, stiffness = 320f)
    val startEnd by animateFloatAsState(target, animationSpec = if (checked) follow else lead, label = "switchKnobStart")
    val endEnd by animateFloatAsState(target, animationSpec = if (checked) lead else follow, label = "switchKnobEnd")
    val sliding = kotlin.math.abs(startEnd - target) > 0.02f || kotlin.math.abs(endEnd - target) > 0.02f
    val glass by animateFloatAsState(
        targetValue = if (enabled && (pressed || sliding)) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f),
        label = "switchGlass",
    )
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        )
    } else {
        Modifier
    }
    Box(
        modifier
            .then(toggle)
            .size(width = SwitchWidth, height = SwitchHeight)
            .drawBehind {
                val trackRadius = CornerRadius(size.height / 2f, size.height / 2f)
                drawRoundRect(color = if (enabled) track else track.copy(alpha = track.alpha * 0.5f), cornerRadius = trackRadius)
                val inset = KnobInset.toPx()
                val knobWidth = KnobWidth.toPx()
                val travel = size.width - knobWidth - inset * 2f
                // Glass grows the knob past the track's edges, as iOS's does.
                val grow = KnobGlassGrow.toPx() * glass
                val left = inset + startEnd * travel - grow
                val right = inset + knobWidth + endEnd * travel + grow
                val top = inset - grow
                val bottom = size.height - inset + grow
                val knobSize = Size(right - left, bottom - top)
                val knobRadius = CornerRadius(knobSize.height / 2f, knobSize.height / 2f)
                val topLeft = Offset(left, top)
                // A soft shadow under the solid knob, gone once it is glass.
                val shadow = (1f - glass) * 0.22f
                if (shadow > 0f) {
                    drawRoundRect(
                        color = Color.Black.copy(alpha = shadow),
                        topLeft = topLeft + Offset(0f, 1.dp.toPx()),
                        size = knobSize,
                        cornerRadius = knobRadius,
                    )
                }
                drawRoundRect(
                    color = Color.White.copy(alpha = if (enabled) 1f - 0.72f * glass else 0.7f),
                    topLeft = topLeft,
                    size = knobSize,
                    cornerRadius = knobRadius,
                )
                if (glass > 0f) {
                    // The glass's edge: bright along the top where the light falls, dimmer below.
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.9f * glass), Color.White.copy(alpha = 0.3f * glass)),
                            startY = top,
                            endY = bottom,
                        ),
                        topLeft = topLeft,
                        size = knobSize,
                        cornerRadius = knobRadius,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            },
    )
}

private val SwitchWidth = 64.dp
private val SwitchHeight = 28.dp
private val KnobWidth = 38.dp
private val KnobInset = 2.dp
private val KnobGlassGrow = 3.dp

/**
 * An iOS 26/27 alert in place of Material's AlertDialog (same parameters, so call sites only
 * change the name): a rounder card, the title and message set from the left, and the buttons as
 * capsules side by side under them, Cancel (grey) on the left and the action (accent blue) on the
 * right; a lone button is a grey capsule across the card. [icon] is accepted and not drawn, since
 * iOS alerts have none. Set [form] when [text] holds text fields.
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
    @Suppress("UNUSED_PARAMETER") form: Boolean = false,
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest, modifier = modifier, properties = properties) {
        Surface(shape = RoundedCornerShape(AlertCorner), color = IosColors.Popover, contentColor = Color.White) {
            Column {
                // Weighted like Material's alert so a tall body (a form, a list) scrolls inside
                // the dialog and never pushes the buttons off screen.
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 18.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    if (title != null) {
                        ProvideTextStyle(MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Start)) { title() }
                    }
                    if (text != null) {
                        if (title != null) Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier
                                .weight(1f, fill = false)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.TopStart,
                        ) {
                            ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Start)) { text() }
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (dismissButton != null) {
                        AlertCapsule(preferred = false, content = dismissButton)
                        AlertCapsule(preferred = true, content = confirmButton)
                    } else {
                        AlertCapsule(preferred = false, content = confirmButton)
                    }
                }
            }
        }
    }
}

/**
 * One of the alert's capsules: accent blue for the [preferred] action, grey otherwise. The call
 * sites hand in Material text buttons, whose label takes the theme's primary colour and its
 * labelLarge style, so the white 17pt label goes in through the theme (semibold for the action,
 * medium for Cancel), and the button is laid out over the whole capsule so all of it takes the
 * tap.
 */
@Composable
private fun RowScope.AlertCapsule(preferred: Boolean, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val label = typography.bodyLarge.copy(fontWeight = if (preferred) FontWeight.SemiBold else FontWeight.Medium)
    Box(
        Modifier
            .weight(1f)
            .height(AlertButtonHeight)
            .clip(CircleShape)
            .background(if (preferred) scheme.primary else AlertButtonGrey),
        contentAlignment = Alignment.Center,
    ) {
        MaterialTheme(colorScheme = scheme.copy(primary = Color.White), typography = typography.copy(labelLarge = label)) {
            Layout(content = content) { measurables, constraints ->
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                val placeables = measurables.map { it.measure(Constraints.fixed(width, height)) }
                layout(width, height) { placeables.forEach { it.place(0, 0) } }
            }
        }
    }
}

private val AlertCorner = 34.dp
private val AlertButtonHeight = 48.dp
private val AlertButtonGrey = Color(0xFF3A3A3C)

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
    // iOS's grey row highlight while pressed (the ripple is off).
    val press = remember { MutableInteractionSource() }
    DropdownMenuItem(
        text = { ProvideTextStyle(MaterialTheme.typography.bodyLarge) { text() } },
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp).indication(press, LocalIndication.current),
        interactionSource = press,
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
 * with the size. With [reveal] (0 to 1) it stands still and shows that share of its spokes, one
 * by one, as iOS's refresh control does while it is being pulled.
 */
@Composable
fun IosSpinner(
    modifier: Modifier = Modifier,
    color: Color = IosColors.SecondaryLabel,
    @Suppress("UNUSED_PARAMETER") strokeWidth: Dp = 0.dp,
    reveal: Float? = null,
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
        val shown = if (reveal == null) 8 else kotlin.math.ceil(reveal.coerceIn(0f, 1f) * 8f).toInt()
        for (i in 0 until shown) {
            val age = if (reveal == null) (lead - i + 8) % 8 else 0
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

/**
 * iOS 26/27's segmented control: a dark capsule track with the chosen segment on a glass pill.
 * Choosing another slides the pill across, stretching as it goes (the edge in front races ahead,
 * the one behind follows a beat later) and settling with a small bounce, as the tab bar's does.
 */
@Composable
fun IosSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .heightIn(min = 36.dp)
            .background(SegmentTrack, CircleShape)
            .padding(SegmentInset),
    ) {
        val slot = maxWidth / options.size.coerceAtLeast(1)
        val index = selectedIndex.coerceAtMost(options.lastIndex)
        val shownIndex = index.coerceAtLeast(0)
        var lastIndex by remember { mutableIntStateOf(shownIndex) }
        val forward = shownIndex >= lastIndex
        SideEffect { lastIndex = shownIndex }
        val lead = spring<Dp>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium)
        val follow = spring<Dp>(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
        val left by animateDpAsState(slot * shownIndex, animationSpec = if (forward) follow else lead, label = "segmentPillLeft")
        val right by animateDpAsState(slot * (shownIndex + 1), animationSpec = if (forward) lead else follow, label = "segmentPillRight")
        if (index >= 0) {
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier
                        .offset(x = left)
                        .width((right - left).coerceAtLeast(slot * 0.5f))
                        .fillMaxHeight()
                        .shadow(4.dp, CircleShape)
                        .background(SegmentPill, CircleShape)
                        .glassRim(CircleShape),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { optionIndex, option ->
                val selected = optionIndex == index
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 32.dp)
                        .clip(CircleShape)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(optionIndex) })
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
}

private val SegmentInset = 2.dp
private val SegmentTrack = Color(0xFF1C1C1E)
private val SegmentPill = Color(0xFF5A5A5E)

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

/**
 * iOS's text field in place of Material's OutlinedTextField (same parameters): a rounded grey
 * field with no outline, its [label] as a small grey caption above (iOS forms have no floating
 * labels), [placeholder] inside while empty, and [supportingText] below, red when [isError].
 */
@Composable
fun NovaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = TextStyle.Default,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
) {
    val caption = MaterialTheme.typography.bodySmall
    val noteColor = if (isError) IosColors.Red else IosColors.SecondaryLabel
    Column(modifier) {
        if (label != null) {
            Box(Modifier.padding(start = 4.dp, bottom = 5.dp)) {
                CompositionLocalProvider(LocalContentColor provides noteColor) {
                    ProvideTextStyle(caption.copy(color = noteColor), content = label)
                }
            }
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = MaterialTheme.typography.bodyLarge.merge(textStyle).copy(
                color = if (enabled) Color.White else IosColors.SecondaryLabel,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .background(Color(0xFF2C2C2E), RoundedCornerShape(10.dp))
                        .then(if (isError) Modifier.border(1.dp, IosColors.Red, RoundedCornerShape(10.dp)) else Modifier)
                        .padding(start = if (leadingIcon != null) 6.dp else 12.dp, end = if (trailingIcon != null) 0.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leadingIcon != null) {
                        CompositionLocalProvider(LocalContentColor provides IosColors.SecondaryLabel) {
                            Box(Modifier.padding(end = 6.dp).size(24.dp), contentAlignment = Alignment.Center) { leadingIcon() }
                        }
                    }
                    Box(Modifier.weight(1f).padding(vertical = 11.dp), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) {
                            CompositionLocalProvider(LocalContentColor provides IosColors.TertiaryLabel) {
                                ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(color = Color(0xFF6E6E73)), content = placeholder)
                            }
                        }
                        inner()
                    }
                    if (trailingIcon != null) {
                        CompositionLocalProvider(LocalContentColor provides IosColors.SecondaryLabel, content = trailingIcon)
                    }
                }
            },
        )
        if (supportingText != null) {
            Box(Modifier.padding(start = 4.dp, top = 5.dp)) {
                CompositionLocalProvider(LocalContentColor provides noteColor) {
                    ProvideTextStyle(caption.copy(color = noteColor), content = supportingText)
                }
            }
        }
    }
}

/**
 * iOS-style notice in place of Material's snackbar: a glass capsule that drops in under the
 * status bar for a few seconds. A newer [message] replaces the one on screen; [onShown] runs
 * once a message has had its time.
 */
@Composable
fun IosToastHost(message: String?, onShown: () -> Unit, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(message) {
        if (message != null) {
            shown = message
            visible = true
            delay(TOAST_MS)
            visible = false
            onShown()
        }
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { -it * 2 } + fadeIn(),
        exit = slideOutVertically(tween(220)) { -it * 2 } + fadeOut(tween(180)),
    ) {
        Text(
            text = shown.orEmpty(),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 3,
            modifier = Modifier
                .widthIn(max = 360.dp)
                .glass(RoundedCornerShape(percent = 50))
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

private const val TOAST_MS = 2_600L

/**
 * One step of a folder path, as iOS's Files app writes it: folders above in blue text that goes
 * there, the current one in white; [icon] marks the source at the start.
 */
@Composable
fun PathCrumb(label: String, current: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
    val color = if (current) Color.White else MaterialTheme.colorScheme.primary
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = !current, role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            NovaIcon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(end = 6.dp).size(18.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * iOS's progress view in place of Material's LinearProgressIndicator (no gap, no end dot): a
 * rounded track with a solid fill. Without [progress] a short segment glides along the track.
 * The caller's height wins; 4dp otherwise.
 */
@Composable
fun IosProgressBar(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = Color.White.copy(alpha = 0.2f),
) {
    val glide = if (progress == null) {
        val transition = rememberInfiniteTransition(label = "progressGlide")
        transition.animateFloat(
            initialValue = -0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 1_100, easing = LinearEasing)),
            label = "progressGlideX",
        )
    } else {
        null
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(4.dp),
    ) {
        val radius = androidx.compose.ui.geometry.CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(color = trackColor, cornerRadius = radius)
        if (progress != null) {
            val width = size.width * progress().coerceIn(0f, 1f)
            if (width > 0f) drawRoundRect(color = color, size = androidx.compose.ui.geometry.Size(width, size.height), cornerRadius = radius)
        } else {
            val start = (glide?.value ?: 0f) * size.width
            val end = (start + size.width * 0.3f).coerceAtMost(size.width)
            val left = start.coerceAtLeast(0f)
            if (end > left) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, 0f),
                    size = androidx.compose.ui.geometry.Size(end - left, size.height),
                    cornerRadius = radius,
                )
            }
        }
    }
}

/**
 * iOS's swipe to delete, in iOS 26/27's form: swiping a row left uncovers a red capsule with
 * Delete in it, set in from the row's edges and growing with the swipe; letting go past most of
 * the row deletes at once. A tap on the capsule deletes; a swipe back or a short swipe hides it.
 * [background] fills the row so the capsule stays behind it.
 */
@Composable
fun SwipeToDelete(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Delete",
    background: Color = MaterialTheme.colorScheme.background,
    content: @Composable () -> Unit,
) {
    var offset by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(0f) }
    val revealPx = with(LocalDensity.current) { 88.dp.toPx() }
    val scope = rememberCoroutineScope()
    val state = rememberDraggableState { delta -> offset = (offset + delta).coerceIn(-width, 0f) }
    // As on iOS, a tick when the swipe passes the point where letting go deletes the row.
    val haptics = LocalHapticFeedback.current
    val armed = width > 0f && offset < -width * FullSwipe
    LaunchedEffect(armed) { if (armed) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    fun settle(target: Float, then: () -> Unit = {}) {
        scope.launch {
            animate(offset, target, animationSpec = spring(dampingRatio = 0.85f, stiffness = 600f)) { value, _ -> offset = value }
            then()
        }
    }
    Box(
        modifier
            .onSizeChanged { width = it.width.toFloat() }
            .clipToBounds(),
    ) {
        // The capsule fills the gap the row leaves as it slides, less a margin all round.
        val gapPx = with(LocalDensity.current) { SwipePillGap.toPx() }
        val pillWidthPx = (-offset - gapPx * 2f).coerceAtLeast(0f)
        Box(
            Modifier
                .matchParentSize()
                .padding(vertical = SwipePillGap, horizontal = SwipePillGap),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (pillWidthPx > 0f) {
                Box(
                    Modifier
                        .layout { measurable, constraints ->
                            val pillWidth = pillWidthPx.toInt().coerceAtMost(constraints.maxWidth)
                            val placeable = measurable.measure(Constraints.fixed(pillWidth, constraints.maxHeight))
                            layout(pillWidth, constraints.maxHeight) { placeable.place(0, 0) }
                        }
                        .clip(CircleShape)
                        .background(IosColors.Red)
                        .clickable(enabled = offset < 0f, onClickLabel = label, role = Role.Button) {
                            settle(-width) {
                                onDelete()
                                offset = 0f
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.graphicsLayer { alpha = (pillWidthPx / revealPx).coerceIn(0f, 1f) },
                    )
                }
            }
        }
        Box(
            Modifier
                .graphicsLayer { translationX = offset }
                .background(background)
                .draggable(
                    state = state,
                    orientation = Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        when {
                            offset < -width * FullSwipe -> settle(-width) {
                                onDelete()
                                offset = 0f
                            }
                            offset < -revealPx / 2 || velocity < -1_500f -> settle(-revealPx)
                            else -> settle(0f)
                        }
                    },
                ),
        ) { content() }
    }
}

/** How far along its width a row must be swiped for letting go to delete it. */
private const val FullSwipe = 0.6f

/** The margin round a swipe action's capsule. */
private val SwipePillGap = 6.dp

/**
 * iOS's collapsed large title: once a page's big title has scrolled away, the title shows small
 * and centred in a dark bar under the status bar, fading in and out.
 */
@Composable
fun CollapsedTitleBar(visible: Boolean, title: String, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = visible, modifier = modifier, enter = fadeIn(tween(160)), exit = fadeOut(tween(160))) {
        Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.92f))) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 56.dp, vertical = 12.dp),
            )
            Box(Modifier.fillMaxWidth().height(Hairline).background(IosColors.Separator))
        }
    }
}

/** iOS 26's press: the button shrinks a little while held and springs back on release. */
@Composable
fun Modifier.pressScale(source: InteractionSource, pressed: Float = 0.94f): Modifier {
    val down by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (down) pressed else 1f, spring(dampingRatio = 0.6f, stiffness = 700f), label = "pressScale")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Apple TV's poster press: while held the picture darkens a little (with [pressScale] shrinking
 * the card), and lights up again on release.
 */
@Composable
fun Modifier.pressDim(source: InteractionSource): Modifier {
    val down by source.collectIsPressedAsState()
    val dim by animateFloatAsState(if (down) 0.22f else 0f, tween(if (down) 90 else 220), label = "pressDim")
    return drawWithContent {
        drawContent()
        if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
    }
}

/**
 * Apple TV's Resume button: the part of the pill as far as the video was watched ([progress],
 * 0 to 1) is a shade lighter. Nothing for a video not started. Goes on a pill-shaped button.
 */
fun Modifier.resumeFill(progress: Float?): Modifier {
    val watched = progress?.coerceIn(0f, 1f) ?: 0f
    if (watched <= 0f) return this
    return clip(RoundedCornerShape(percent = 50)).drawWithContent {
        drawContent()
        drawRect(Color.White.copy(alpha = 0.18f), size = size.copy(width = size.width * watched))
    }
}
