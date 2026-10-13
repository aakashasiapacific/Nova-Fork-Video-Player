package com.aakash.novafork.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.theme.LocalNovaPalette
import com.aakash.novafork.ui.theme.NovaColors
import com.aakash.novafork.ui.theme.NovaMotion
import com.aakash.novafork.ui.theme.NovaType

/** The iOS press response: the control shrinks to 0.96 and dims to 0.85 while held. */
@Composable
fun Modifier.pressEffect(
    interactionSource: InteractionSource,
    scale: Float = NovaMotion.PressScale,
    alpha: Float = NovaMotion.PressAlpha,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val s = animateFloatAsState(if (pressed) scale else 1f, NovaMotion.press(), label = "pressScale")
    val a = animateFloatAsState(if (pressed) alpha else 1f, NovaMotion.press(), label = "pressAlpha")
    return this.graphicsLayer {
        scaleX = s.value
        scaleY = s.value
        this.alpha = a.value
    }
}

/**
 * clickable with no ripple and the iOS press response. Adds long-press (with haptics) when
 * [onLongClick] is set. Put it BEFORE clip/background/glass so the whole control scales.
 */
@Composable
fun Modifier.novaClickable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    pressScale: Float = NovaMotion.PressScale,
    pressAlpha: Float = NovaMotion.PressAlpha,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    return this
        .pressEffect(source, pressScale, pressAlpha)
        .combinedClickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

/** Context-menu lift: the long-pressed card grows to 1.03 while its menu is open. */
@Composable
fun Modifier.contextLift(lifted: Boolean): Modifier {
    val s = animateFloatAsState(if (lifted) NovaMotion.LiftScale else 1f, NovaMotion.bouncy(), label = "lift")
    return this.graphicsLayer {
        scaleX = s.value
        scaleY = s.value
    }
}

/** Filled, slightly rounded play triangle (SF "play.fill"). */
@Composable
fun PlayGlyph(modifier: Modifier = Modifier, color: Color = Color.Black, size: Dp = 16.dp) {
    Icon(NovaGlyphs.Play, contentDescription = null, modifier = modifier.size(size), tint = color)
}

/**
 * The white "▶ Play" capsule: black Headline text with a filled play glyph (or [icon]) on the
 * left. 50dp tall on phone, 56dp on iPad; auto width with a 140dp minimum. Pass
 * Modifier.fillMaxWidth() for the full-width details button.
 */
@Composable
fun PrimaryCapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    playGlyph: Boolean = true,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    height: Dp = LocalNovaDimens.current.primaryButtonHeight,
    minWidth: Dp = 140.dp,
    containerColor: Color = Color.White,
    contentColor: Color = Color.Black,
) {
    Row(
        modifier
            .widthIn(min = minWidth)
            .height(height)
            .novaClickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(containerColor)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp), tint = contentColor)
            Spacer(Modifier.width(8.dp))
        } else if (playGlyph) {
            PlayGlyph(color = contentColor, size = if (height >= 56.dp) 18.dp else 16.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = NovaType.headline,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A glass capsule with a Subhead semibold label: "Sort ▾" ([trailingChevron]), "Season 1 ▾",
 * category capsules on iPad Library ([selected] = solid label-coloured pill), player menus.
 */
@Composable
fun GlassCapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    trailingChevron: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 40.dp,
    style: GlassStyle = GlassStyle.Regular,
    glass: GlassState? = LocalGlassState.current,
) {
    val palette = LocalNovaPalette.current
    val onMedia = style == GlassStyle.Clear
    val content = when {
        selected && onMedia -> Color.Black
        selected -> palette.background
        onMedia -> Color.White
        else -> palette.label
    }
    val surface = if (selected) {
        Modifier
            .clip(CircleShape)
            .background(if (onMedia) Color.White else palette.label)
    } else {
        Modifier.glass(CircleShape, glass, style, elevation = 4.dp)
    }
    Row(
        modifier
            .height(height)
            .novaClickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .then(surface)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp), tint = content)
            Spacer(Modifier.width(6.dp))
        }
        Text(text = text, style = NovaType.subheadSemibold, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailingChevron) {
            Spacer(Modifier.width(5.dp))
            Icon(NovaGlyphs.ChevronDown, contentDescription = null, modifier = Modifier.size(11.dp), tint = content)
        }
    }
}

/** Light lens drawn behind a selected glass control (tab items, selected circles). */
@Composable
internal fun lensColor(onMedia: Boolean = false): Color =
    if (onMedia || LocalNovaPalette.current.dark) Color(0x29FFFFFF) else Color(0x14000000)

/**
 * A glass circle (icon only), 50dp on phone / 56dp on iPad by default. Slot version: put any
 * glyph or a short label ("1×") in [content].
 */
@Composable
fun GlassCircleButton(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = LocalNovaDimens.current.glassButtonSize,
    enabled: Boolean = true,
    selected: Boolean = false,
    style: GlassStyle = GlassStyle.Regular,
    glass: GlassState? = LocalGlassState.current,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val lens = lensColor(style == GlassStyle.Clear)
    Box(
        modifier
            .size(size)
            .novaClickable(enabled = enabled, onLongClick = onLongClick, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .glass(CircleShape, glass, style, elevation = 6.dp)
            .then(if (selected) Modifier.background(lens) else Modifier)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Glass circle with a res/drawable icon. [tint] defaults to label (white on Clear glass). */
@Composable
fun GlassCircleButton(
    onClick: () -> Unit,
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = LocalNovaDimens.current.glassButtonSize,
    iconSize: Dp = size * 0.44f,
    tint: Color = Color.Unspecified,
    enabled: Boolean = true,
    selected: Boolean = false,
    style: GlassStyle = GlassStyle.Regular,
    glass: GlassState? = LocalGlassState.current,
    onLongClick: (() -> Unit)? = null,
) {
    val color = glyphTint(tint, style)
    GlassCircleButton(
        onClick = onClick,
        contentDescription = contentDescription,
        modifier = modifier,
        size = size,
        enabled = enabled,
        selected = selected,
        style = style,
        glass = glass,
        onLongClick = onLongClick,
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(iconSize), tint = color)
    }
}

/** Glass circle with a NovaGlyphs vector. */
@Composable
fun GlassCircleButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = LocalNovaDimens.current.glassButtonSize,
    iconSize: Dp = size * 0.4f,
    tint: Color = Color.Unspecified,
    enabled: Boolean = true,
    selected: Boolean = false,
    style: GlassStyle = GlassStyle.Regular,
    glass: GlassState? = LocalGlassState.current,
    onLongClick: (() -> Unit)? = null,
) {
    val color = glyphTint(tint, style)
    GlassCircleButton(
        onClick = onClick,
        contentDescription = contentDescription,
        modifier = modifier,
        size = size,
        enabled = enabled,
        selected = selected,
        style = style,
        glass = glass,
        onLongClick = onLongClick,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize), tint = color)
    }
}

@Composable
private fun glyphTint(tint: Color, style: GlassStyle): Color = when {
    tint != Color.Unspecified -> tint
    style == GlassStyle.Clear -> Color.White
    else -> LocalNovaPalette.current.label
}

/** Glass circle with a person glyph, top-right on Home/Library (phone) and the iPad tab bar → Settings. */
@Composable
fun AccountButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = LocalNovaDimens.current.toolbarButtonSize,
    selected: Boolean = false,
    style: GlassStyle = GlassStyle.Regular,
    glass: GlassState? = LocalGlassState.current,
) {
    GlassCircleButton(
        onClick = onClick,
        icon = NovaGlyphs.Person,
        contentDescription = "Settings",
        modifier = modifier,
        size = size,
        iconSize = size * 0.46f,
        selected = selected,
        style = style,
        glass = glass,
    )
}

/** Glass circle with "‹" for pushed pages. Use GlassStyle.Clear over artwork. */
@Composable
fun BackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = LocalNovaDimens.current.toolbarButtonSize,
    style: GlassStyle = GlassStyle.Regular,
    glass: GlassState? = LocalGlassState.current,
) {
    GlassCircleButton(
        onClick = onClick,
        icon = NovaGlyphs.Back,
        contentDescription = "Back",
        modifier = modifier,
        size = size,
        iconSize = size * 0.42f,
        style = style,
        glass = glass,
    )
}

/**
 * iOS text button: tint-coloured (blue) text that dims while pressed. "Start Over", "Cancel",
 * "Add Folder" style actions; pass color = Color.White and NovaType.smallCaps for "MORE".
 */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = NovaColors.tint,
    style: TextStyle = NovaType.body,
    enabled: Boolean = true,
) {
    Text(
        text = text,
        modifier = modifier
            .novaClickable(enabled = enabled, pressScale = 1f, pressAlpha = 0.35f, onClick = onClick)
            .alpha(if (enabled) 1f else 0.35f)
            .padding(vertical = 8.dp),
        color = color,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
