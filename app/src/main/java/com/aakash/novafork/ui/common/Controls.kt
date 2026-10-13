package com.aakash.novafork.ui.common

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.aakash.novafork.ui.theme.LocalNovaPalette
import com.aakash.novafork.ui.theme.NovaMotion
import com.aakash.novafork.ui.theme.NovaRadius
import com.aakash.novafork.ui.theme.NovaType
import kotlin.math.roundToInt

/** iOS 26 switch metrics. */
private val ToggleWidth = 63.dp
private val ToggleHeight = 28.dp
private val ToggleInset = 2.dp
private val KnobHeight = ToggleHeight - ToggleInset * 2
private val KnobWidth = 37.dp
private val KnobPressedWidth = 45.dp

/**
 * The iOS 26 switch: a 63×28 capsule track, green when on, with a white pill knob that stretches
 * while held and springs across. Pass onCheckedChange = null when the whole row handles the tap.
 */
@Composable
fun IosToggle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = LocalNovaPalette.current
    val view = LocalView.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val track by animateColorAsState(if (checked) palette.green else palette.toggleOff, tween(200), label = "toggleTrack")
    val knobWidth by animateDpAsState(
        if (pressed) KnobPressedWidth else KnobWidth,
        spring(dampingRatio = 0.8f, stiffness = 700f),
        label = "toggleKnobWidth",
    )
    val x by animateDpAsState(
        if (checked) ToggleWidth - ToggleInset * 2 - knobWidth else 0.dp,
        spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "toggleKnobX",
    )
    val interaction = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onCheckedChange(it)
            },
        )
    } else {
        Modifier
    }
    Box(
        modifier
            .size(width = ToggleWidth, height = ToggleHeight)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(track)
            .then(interaction)
            .padding(ToggleInset),
    ) {
        Box(
            Modifier
                .offset(x = x)
                .size(width = knobWidth, height = KnobHeight)
                .shadow(2.5.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.22f))
                .background(Color.White, CircleShape),
        )
    }
}

/**
 * iOS segmented control: a capsule track with a raised thumb that slides to the selected
 * segment. Segments share the width equally, so give it a width (fillMaxWidth or width()).
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
) {
    if (options.isEmpty()) return
    val palette = LocalNovaPalette.current
    val density = LocalDensity.current
    val count = options.size
    val index = selectedIndex.coerceIn(0, count - 1)
    var widthPx by remember { mutableIntStateOf(0) }
    val insetPx = with(density) { 2.dp.toPx() }
    val segmentPx = if (widthPx > 0) (widthPx - insetPx * 2f) / count else 0f
    val thumbX = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(index, segmentPx) {
        if (segmentPx <= 0f) return@LaunchedEffect
        val target = index * segmentPx
        if (!placed) {
            thumbX.snapTo(target)
            placed = true
        } else {
            thumbX.animateTo(target, NovaMotion.standard())
        }
    }
    Box(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(palette.fill)
            .onSizeChanged { widthPx = it.width }
            .padding(2.dp),
    ) {
        if (placed && segmentPx > 0f) {
            Box(
                Modifier
                    .offset { IntOffset(thumbX.value.roundToInt(), 0) }
                    .width(with(density) { segmentPx.toDp() })
                    .fillMaxHeight()
                    .shadow(3.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.18f))
                    .background(palette.segmentThumb, CircleShape),
            )
        }
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                val selected = i == index
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = selected,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(i) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = if (selected) NovaType.footnoteSemibold else NovaType.footnote.copy(fontWeight = FontWeight.Medium),
                        color = palette.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        }
    }
}

/**
 * The iOS search field: 36dp, 10dp corners, magnifier, Body text, "Movies, Shows, and More"
 * placeholder and a clear button while it has text. Show a TextAction("Cancel") next to it
 * while it is focused ([onFocusChange]).
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Movies, Shows, and More",
    autoFocus: Boolean = false,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocusChange: (Boolean) -> Unit = {},
    onSubmit: (String) -> Unit = {},
) {
    val palette = LocalNovaPalette.current
    val keyboard = LocalSoftwareKeyboardController.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChange(it.isFocused) },
        textStyle = NovaType.body.copy(color = palette.label),
        singleLine = true,
        cursorBrush = SolidColor(palette.tint),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            onSubmit(value)
            keyboard?.hide()
        }),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(NovaRadius.Field))
                    .background(palette.searchField)
                    .padding(start = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(NovaGlyphs.Search, contentDescription = null, modifier = Modifier.size(17.dp), tint = palette.secondaryLabel)
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = NovaType.body,
                            color = palette.secondaryLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    FieldClearButton(onClick = { onValueChange("") }, glyphColor = palette.searchField)
                }
            }
        },
    )
    if (autoFocus) {
        LaunchedEffect(Unit) {
            try {
                focusRequester.requestFocus()
            } catch (e: IllegalStateException) {
                // Not attached yet (e.g. while the page animates in); the user can tap the field.
            }
        }
    }
}

/** The grey "⊗" that clears a text or search field (30dp touch target around a 17dp circle). */
@Composable
internal fun FieldClearButton(onClick: () -> Unit, glyphColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(30.dp)
            .novaClickable(pressScale = 1f, pressAlpha = 0.5f, onClick = onClick)
            .semantics { contentDescription = "Clear text" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(17.dp)
                .clip(CircleShape)
                .background(LocalNovaPalette.current.tertiaryLabel),
            contentAlignment = Alignment.Center,
        ) {
            Icon(NovaGlyphs.Close, contentDescription = null, modifier = Modifier.size(9.dp), tint = glyphColor)
        }
    }
}

/**
 * A thin capsule slider (player volume, brightness): 4dp track that grows to 8dp while dragged,
 * white fill. [value] is 0–1. Tap or drag anywhere on its 28dp touch height.
 */
@Composable
fun ThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
    color: Color = Color.White,
    trackColor: Color = Color(0x4DFFFFFF),
    trackHeight: Dp = 4.dp,
    activeTrackHeight: Dp = 8.dp,
    contentDescription: String? = null,
) {
    var dragging by remember { mutableStateOf(false) }
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    val barHeight by animateDpAsState(if (dragging) activeTrackHeight else trackHeight, NovaMotion.standard(), label = "sliderHeight")
    val v = value.coerceIn(0f, 1f)
    val input = if (enabled) {
        Modifier
            .pointerInput(Unit) {
                detectTapGestures(onTap = { pos ->
                    if (size.width > 0) change((pos.x / size.width).coerceIn(0f, 1f))
                    finished?.invoke()
                })
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { pos ->
                        dragging = true
                        if (size.width > 0) change((pos.x / size.width).coerceIn(0f, 1f))
                    },
                    onDragEnd = {
                        dragging = false
                        finished?.invoke()
                    },
                    onDragCancel = {
                        dragging = false
                        finished?.invoke()
                    },
                ) { pointer, _ ->
                    pointer.consume()
                    if (size.width > 0) change((pointer.position.x / size.width).coerceIn(0f, 1f))
                }
            }
    } else {
        Modifier
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(v, 0f..1f)
                if (enabled) {
                    setProgress { target ->
                        change(target.coerceIn(0f, 1f))
                        finished?.invoke()
                        true
                    }
                }
            }
            .then(input),
    ) {
        val h = barHeight.toPx()
        val top = (size.height - h) / 2f
        val radius = CornerRadius(h / 2f)
        drawRoundRect(trackColor, topLeft = Offset(0f, top), size = Size(size.width, h), cornerRadius = radius)
        if (v > 0f) {
            drawRoundRect(color, topLeft = Offset(0f, top), size = Size(maxOf(size.width * v, h), h), cornerRadius = radius)
        }
    }
}

/** iOS stepper: "−" and "+" halves of a 94×32 capsule (subtitle delay, speed). */
@Composable
fun Stepper(
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    decrementEnabled: Boolean = true,
    incrementEnabled: Boolean = true,
    decrementDescription: String = "Decrease",
    incrementDescription: String = "Increase",
) {
    val palette = LocalNovaPalette.current
    Row(
        modifier
            .size(width = 94.dp, height = 32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(palette.fill),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperHalf("−", decrementDescription, decrementEnabled, onDecrement, Modifier.weight(1f))
        Box(
            Modifier
                .width(1.dp)
                .height(18.dp)
                .background(palette.separator),
        )
        StepperHalf("+", incrementDescription, incrementEnabled, onIncrement, Modifier.weight(1f))
    }
}

@Composable
private fun StepperHalf(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val palette = LocalNovaPalette.current
    Box(
        modifier
            .fillMaxHeight()
            .novaClickable(enabled = enabled, pressScale = 1f, pressAlpha = 0.4f, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            style = NovaType.title3.copy(fontWeight = FontWeight.Normal),
            color = if (enabled) palette.label else palette.tertiaryLabel,
        )
    }
}
