package com.aakash.novafork.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.common.IosColors
import com.aakash.novafork.ui.theme.IosPalette

/**
 * Appearance › Glass, as iOS 27's Liquid Glass slider: from Clear (the page shows through the
 * glass buttons and bars) to Tinted (nearly solid). A capsule of glass over a picture above the
 * slider shows the look as it moves; the setting is saved when the finger lifts.
 */
@Composable
internal fun GlassRow(shape: Shape, tint: Float, onChange: (Float) -> Unit) {
    var shown by remember(tint) { mutableFloatStateOf(tint) }
    RowSurface(shape = shape) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IconTextGap)) {
                RowIcon(R.drawable.ic_brightness_6)
                RowTitle("Glass", Modifier.weight(1f))
            }
            GlassPreview(shown)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SliderEnd("Clear")
                IosSlider(
                    value = shown,
                    onValueChange = { shown = it },
                    onValueChangeFinished = { onChange(shown) },
                    description = "Glass",
                    modifier = Modifier.weight(1f),
                )
                SliderEnd("Tinted")
            }
        }
    }
}

@Composable
private fun SliderEnd(label: String) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = IosColors.SecondaryLabel, maxLines = 1)
}

/**
 * A strip of colour (a stand-in for a bright picture) with a glass capsule on it, as see-through
 * as [tint] makes the app's glass.
 */
@Composable
private fun GlassPreview(tint: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PreviewPicture),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 150.dp, height = 44.dp)
                .clip(CircleShape)
                .background(PreviewGlass.copy(alpha = previewAlpha(tint)))
                .border(0.5.dp, Color.White.copy(alpha = 0.22f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("Glass", style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
    }
}

/** The preview's own reading of the tint, from barely there to nearly solid. */
private fun previewAlpha(tint: Float): Float = 0.25f + 0.72f * tint.coerceIn(0f, 1f)

private val PreviewGlass = Color(0xFF1C1C1E)
private val PreviewPicture = Brush.linearGradient(
    listOf(IosPalette.Orange, IosPalette.Pink, IosPalette.Indigo, IosPalette.Cyan),
)

/**
 * iOS 26/27's slider: a thin track filled in the accent up to a white capsule knob, which turns
 * to glass and grows a little while the finger holds it. Tap or drag anywhere along it;
 * [onValueChangeFinished] runs when the finger lifts.
 */
@Composable
internal fun IosSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    var held by remember { mutableStateOf(false) }
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    val knobScale by animateFloatAsState(
        targetValue = if (held) 1.15f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "sliderKnobScale",
    )
    val knobColor by animateColorAsState(if (held) KnobGlass else Color.White, label = "sliderKnobColor")
    val accent = MaterialTheme.colorScheme.primary
    val shown = value.coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier
            .height(32.dp)
            .semantics {
                if (description != null) contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
                setProgress { target ->
                    change(target.coerceIn(0f, 1f))
                    finished()
                    true
                }
            }
            .pointerInput(Unit) {
                fun at(x: Float): Float {
                    val knob = KnobWidth.toPx()
                    return ((x - knob / 2f) / (size.width - knob).coerceAtLeast(1f)).coerceIn(0f, 1f)
                }
                detectTapGestures(
                    onPress = {
                        held = true
                        tryAwaitRelease()
                        held = false
                    },
                    onTap = { offset ->
                        change(at(offset.x))
                        finished()
                    },
                )
            }
            .pointerInput(Unit) {
                fun at(x: Float): Float {
                    val knob = KnobWidth.toPx()
                    return ((x - knob / 2f) / (size.width - knob).coerceAtLeast(1f)).coerceIn(0f, 1f)
                }
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        held = true
                        change(at(offset.x))
                    },
                    onDragEnd = {
                        held = false
                        finished()
                    },
                    onDragCancel = {
                        held = false
                        finished()
                    },
                ) { pointer, _ ->
                    pointer.consume()
                    change(at(pointer.position.x))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val travel = maxWidth - KnobWidth
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(TrackHeight),
        ) {
            val radius = CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(color = TrackColor, cornerRadius = radius)
            val knob = KnobWidth.toPx()
            val filled = knob / 2f + (size.width - knob) * shown
            drawRoundRect(color = accent, size = Size(filled, size.height), cornerRadius = radius)
        }
        Box(
            Modifier
                .offset(x = travel * shown)
                .size(width = KnobWidth, height = KnobHeight)
                .graphicsLayer {
                    scaleX = knobScale
                    scaleY = knobScale
                }
                .shadow(if (held) 0.dp else 4.dp, CircleShape)
                .background(knobColor, CircleShape)
                .border(0.5.dp, Color.White.copy(alpha = if (held) 0.7f else 0f), CircleShape),
        )
    }
}

private val KnobWidth = 38.dp
private val KnobHeight = 24.dp
private val TrackHeight = 4.dp
private val TrackColor = Color(0xFF39393D)
/** The held knob: clear glass, the track showing through. */
private val KnobGlass = Color.White.copy(alpha = 0.35f)
