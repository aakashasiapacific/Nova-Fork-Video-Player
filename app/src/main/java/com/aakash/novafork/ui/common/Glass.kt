package com.aakash.novafork.ui.common

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.aakash.novafork.ui.theme.LocalNovaPalette

/*
 * Liquid Glass (ported from Claude-Meter's Glass.kt).
 *
 * The page content is recorded into a graphics layer once per frame (glassSource). Every
 * floating control draws that layer again underneath itself, shifted so it lines up with the
 * screen, through a blur and a little extra saturation, then a tint, a specular sheen at the top
 * and a light rim. Android 12 and newer can blur a layer; older phones, Robolectric tests and
 * other windows (dialogs, popups) get a translucent fill with the same sheen and rim, so the
 * shapes read the same everywhere.
 *
 * Rule: a glass control must never sit INSIDE the source it looks through (it would draw the
 * layer into itself). GlassBackdrop therefore clears LocalGlassState for its content; provide a
 * state only to the chrome that floats over the backdrop (see docs/DESIGN.md, "Liquid Glass").
 */

object GlassConfig {
    /** Tests flip this on to check the backdrop lines up (no blur in software rendering). */
    @Volatile var forceBackdrop: Boolean = false

    val backdropSupported: Boolean
        get() = forceBackdrop ||
            (Build.VERSION.SDK_INT >= 31 && !Build.FINGERPRINT.contains("robolectric", ignoreCase = true))
}

/** What the glass looks through: one recorded layer and where it sits on screen. */
class GlassState internal constructor(val layer: GraphicsLayer) {
    var position by mutableStateOf(Offset.Zero)
        internal set

    /** Bumped after every recording so the glass above redraws with fresh content. */
    var tick by mutableLongStateOf(0L)
        internal set
}

@Composable
fun rememberGlassState(): GlassState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassState(layer) }
}

/**
 * The backdrop that glass components read by default. Null means "nothing to blur": the
 * component uses its translucent fallback. Provided by the shells for their floating chrome;
 * cleared inside GlassBackdrop, GlassSheet, IosAlert and GlassMenu.
 */
val LocalGlassState = staticCompositionLocalOf<GlassState?> { null }

/** Marks the content that glass controls float over. Put it before scroll modifiers. */
fun Modifier.glassSource(state: GlassState?): Modifier {
    if (state == null || !GlassConfig.backdropSupported) return this
    return this
        .onGloballyPositioned { state.position = it.positionInRoot() }
        .drawWithContent {
            if (size.width >= 1f && size.height >= 1f) {
                state.layer.record { this@drawWithContent.drawContent() }
                drawLayer(state.layer)
                state.tick++
            } else {
                drawContent()
            }
        }
}

/**
 * A Box whose content is recorded for the glass that floats over it. Glass components inside
 * [content] do not look through [state] (they fall back to translucent glass, or to a nested
 * GlassBackdrop provided around artwork).
 */
@Composable
fun GlassBackdrop(state: GlassState, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.glassSource(state)) {
        CompositionLocalProvider(LocalGlassState provides null) {
            content()
        }
    }
}

/**
 * A page with floating glass chrome: [content] is the backdrop, [chrome] draws on top of it and
 * sees it through LocalGlassState (tab bars, top buttons, inline title bars).
 */
@Composable
fun GlassPage(
    modifier: Modifier = Modifier,
    state: GlassState = rememberGlassState(),
    chrome: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier) {
        GlassBackdrop(state, Modifier.matchParentSize(), content)
        CompositionLocalProvider(LocalGlassState provides state) {
            chrome()
        }
    }
}

/**
 * Glass materials.
 * Regular: bars, capsules and circles over the page.
 * Clear: controls over artwork and video (hero, details, player); always dark with white glyphs.
 * Dense: menus, sheets and alerts, which carry text.
 */
enum class GlassStyle { Regular, Clear, Dense }

private class GlassTone(
    /** Opaque base under the blurred copy so nothing crisp ghosts through. */
    val base: Color,
    /** Darkens bright backdrops so white glyphs stay legible. */
    val dim: Color,
    /** The white (or tinted) wash of the material. */
    val tint: Color,
    /** Fill when there is nothing to blur. */
    val fallback: Color,
    val sheen: Color,
    val rimBright: Color,
    val rimSoft: Color,
    val shadowAmbient: Color,
    val shadowSpot: Color,
)

private fun glassTone(style: GlassStyle, dark: Boolean): GlassTone = when {
    style == GlassStyle.Clear -> GlassTone(
        base = Color.Black,
        dim = Color(0x2E000000),
        tint = Color(0x17FFFFFF),
        fallback = Color(0x8C2C2C2E),
        sheen = Color(0x1FFFFFFF),
        rimBright = Color(0x66FFFFFF),
        rimSoft = Color(0x14FFFFFF),
        shadowAmbient = Color(0x4D000000),
        shadowSpot = Color(0x73000000),
    )
    style == GlassStyle.Dense && dark -> GlassTone(
        base = Color.Black,
        dim = Color.Transparent,
        tint = Color(0xB81C1C1E),
        fallback = Color(0xF21C1C1E),
        sheen = Color(0x14FFFFFF),
        rimBright = Color(0x4DFFFFFF),
        rimSoft = Color(0x0FFFFFFF),
        shadowAmbient = Color(0x4D000000),
        shadowSpot = Color(0x73000000),
    )
    style == GlassStyle.Dense -> GlassTone(
        base = Color(0xFFF2F2F7),
        dim = Color.Transparent,
        tint = Color(0xC7F9F9F9),
        fallback = Color(0xF5F9F9F9),
        sheen = Color(0x66FFFFFF),
        rimBright = Color(0xE6FFFFFF),
        rimSoft = Color(0x0F000000),
        shadowAmbient = Color(0x1A000000),
        shadowSpot = Color(0x33000000),
    )
    dark -> GlassTone(
        base = Color.Black,
        dim = Color(0x1F000000),
        tint = Color(0x1FFFFFFF),
        fallback = Color(0xE62C2C2E),
        sheen = Color(0x1AFFFFFF),
        rimBright = Color(0x59FFFFFF),
        rimSoft = Color(0x14FFFFFF),
        shadowAmbient = Color(0x4D000000),
        shadowSpot = Color(0x73000000),
    )
    else -> GlassTone(
        base = Color(0xFFF2F2F7),
        dim = Color.Transparent,
        tint = Color(0x99FFFFFF),
        fallback = Color(0xEBF9F9F9),
        sheen = Color(0x66FFFFFF),
        rimBright = Color(0xE6FFFFFF),
        rimSoft = Color(0x0F000000),
        shadowAmbient = Color(0x1A000000),
        shadowSpot = Color(0x33000000),
    )
}

/** Blur radius of the material (spec: 20–30dp). */
private val GlassBlur = 24.dp

@RequiresApi(31)
private fun blurEffect(radiusPx: Float): RenderEffect {
    val blur = android.graphics.RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
    val saturate = ColorMatrix().apply { setSaturation(1.4f) }
    return android.graphics.RenderEffect
        .createColorFilterEffect(ColorMatrixColorFilter(saturate), blur)
        .asComposeRenderEffect()
}

/**
 * Liquid Glass surface in [shape] (capsules and circles; RectangleShape for bars). [state] is the
 * backdrop it floats over; null gives the translucent fallback. Clips to [shape].
 */
@Composable
fun Modifier.glass(
    shape: Shape,
    state: GlassState? = LocalGlassState.current,
    style: GlassStyle = GlassStyle.Regular,
    elevation: Dp = 8.dp,
    rim: Boolean = true,
): Modifier {
    val dark = LocalNovaPalette.current.dark
    val tone = remember(style, dark) { glassTone(style, dark) }
    val density = LocalDensity.current
    val blurLayer = rememberGraphicsLayer()
    var position by remember { mutableStateOf(Offset.Zero) }
    // The page to look through, or null for the translucent fallback.
    val backdrop: GlassState? = if (GlassConfig.backdropSupported) state else null
    val radiusPx = with(density) { GlassBlur.toPx() }
    val padPx = with(density) { (GlassBlur + 4.dp).toPx() }
    val blurWanted = backdrop != null
    val effect = remember(radiusPx, blurWanted) {
        if (blurWanted && Build.VERSION.SDK_INT >= 31) {
            try {
                blurEffect(radiusPx)
            } catch (t: Throwable) {
                null
            }
        } else {
            null
        }
    }
    val rimWidth = with(density) { 0.5.dp.toPx() }

    return this
        .shadow(elevation, shape, clip = false, ambientColor = tone.shadowAmbient, spotColor = tone.shadowSpot)
        .onGloballyPositioned { position = it.positionInRoot() }
        .clip(shape)
        .drawBehind {
            if (backdrop != null) {
                @Suppress("UNUSED_VARIABLE")
                val frame = backdrop.tick
                val dx = backdrop.position.x - position.x
                val dy = backdrop.position.y - position.y
                // Opaque base first: the blurred copy composites over it instead of over the
                // crisp content underneath, so nothing ghosts.
                drawRect(tone.base)
                if (effect != null && !GlassConfig.forceBackdrop) {
                    // Record a padded copy of the page through the blur, then draw it back
                    // shifted so the padding falls outside the clip (no dark edges).
                    val w = (size.width + padPx * 2f).toInt()
                    val h = (size.height + padPx * 2f).toInt()
                    if (w > 0 && h > 0) {
                        blurLayer.renderEffect = effect
                        blurLayer.record(IntSize(w, h)) {
                            translate(dx + padPx, dy + padPx) { drawLayer(backdrop.layer) }
                        }
                        translate(-padPx, -padPx) { drawLayer(blurLayer) }
                    }
                } else {
                    // No blur available: the page as it is, which also lets tests check alignment.
                    translate(dx, dy) { drawLayer(backdrop.layer) }
                }
                drawRect(tone.dim)
                drawRect(tone.tint)
            } else {
                drawRect(tone.fallback)
            }
            drawSheen(tone.sheen)
            if (rim) drawRim(shape, rimWidth, tone.rimBright, tone.rimSoft)
        }
}

/** The soft specular highlight along the top edge. */
private fun DrawScope.drawSheen(color: Color) {
    drawRect(Brush.verticalGradient(0f to color, 0.45f to Color.Transparent))
}

/** A hairline that catches the light at the top-left and fades towards the bottom-right. */
private fun DrawScope.drawRim(shape: Shape, width: Float, bright: Color, soft: Color) {
    val outline = shape.createOutline(size, layoutDirection, this)
    val brush = Brush.linearGradient(
        colorStops = arrayOf(
            0f to bright,
            0.35f to bright.copy(alpha = bright.alpha * 0.35f),
            0.7f to soft,
            1f to bright.copy(alpha = bright.alpha * 0.55f),
        ),
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )
    // Half of the stroke falls outside the clip, so this reads as a [width] line inside the edge.
    drawOutline(outline, brush, style = Stroke(width * 2f))
}
