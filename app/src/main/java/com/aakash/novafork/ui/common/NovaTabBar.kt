package com.aakash.novafork.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.model.TopDestination
import com.aakash.novafork.ui.theme.NovaColors
import kotlin.math.sqrt
import kotlinx.coroutines.launch

/**
 * The tab capsule shared by the phone's bottom dock and the tablet's top bar (iOS 26 / iPad
 * Apple TV): a dark-grey glass capsule with a lighter grey pill behind the selected tab, whose
 * icon and label take the tab's colour; the others are white. A finger dragged along it moves
 * the pill, and the tab under the finger opens when it lifts. Size comes from [modifier].
 */
@Composable
internal fun NovaTabCapsule(selected: TopDestination, onSelect: (TopDestination) -> Unit, modifier: Modifier = Modifier) {
    val tabs = TopDestination.entries
    // A light tick on every tab change, as iOS's tab bar gives.
    val haptics = LocalHapticFeedback.current
    val selectWithTick: (TopDestination) -> Unit = { tab ->
        if (tab != selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onSelect(tab)
    }
    val currentOnSelect by rememberUpdatedState(selectWithTick)
    var dragX by remember { mutableStateOf<Float?>(null) }
    BoxWithConstraints(
        modifier
            .frostedGlass(RoundedCornerShape(percent = 50))
            .padding(PillInset)
            .pointerInput(tabs.size) {
                fun tabAt(x: Float): TopDestination = tabs[(x / size.width * tabs.size).toInt().coerceIn(0, tabs.lastIndex)]
                detectHorizontalDragGestures(
                    onDragStart = { dragX = it.x },
                    onDragEnd = {
                        dragX?.let { currentOnSelect(tabAt(it)) }
                        dragX = null
                    },
                    onDragCancel = { dragX = null },
                ) { change, amount ->
                    change.consume()
                    dragX = (dragX ?: change.position.x) + amount
                }
            },
    ) {
        val slot = maxWidth / tabs.size
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val active = dragX?.let { x -> (x / widthPx * tabs.size).toInt().coerceIn(0, tabs.lastIndex) } ?: tabs.indexOf(selected)
        // iOS 26's liquid pill: the edge in front races to the new tab, the one behind follows a
        // beat later, so the pill stretches as it travels and settles back into shape.
        var lastActive by remember { mutableStateOf(active) }
        val forward = active >= lastActive
        SideEffect { lastActive = active }
        val fast = spring<Dp>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium)
        val slow = spring<Dp>(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
        val left by animateDpAsState(slot * active, animationSpec = if (forward) slow else fast, label = "tabPillLeft")
        val right by animateDpAsState(slot * (active + 1), animationSpec = if (forward) fast else slow, label = "tabPillRight")
        Box(
            Modifier
                .offset(x = left)
                .size(width = (right - left).coerceAtLeast(slot * 0.5f), height = maxHeight)
                .clip(RoundedCornerShape(percent = 50))
                .background(SelectedPillColor),
        )
        Row(Modifier.fillMaxSize()) {
            tabs.forEachIndexed { index, tab ->
                val isActive = index == active
                val tint = if (isActive) tab.activeColor() else Color.White
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = tab == selected,
                            onClick = { selectWithTick(tab) },
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    TabIcon(tab = tab, active = isActive, tint = tint)
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = tint,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/** One of the shrunken bar's round glass buttons. */
@Composable
internal fun GlassCircle(onClick: () -> Unit, description: String, size: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(press, pressed = 0.9f)
            .size(size)
            .frostedGlass(CircleShape)
            .clickable(interactionSource = press, indication = LocalIndication.current, onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * iOS 27's dark glass: a dark-grey fill as see-through as Preferences' Glass slider asks
 * ([LocalGlassTint]; [minTint] keeps it at least that tinted), the iOS 27 edge ([glassRim]) and a
 * soft shadow.
 */
@Composable
internal fun Modifier.glass(shape: Shape, minTint: Float = 0f): Modifier {
    val tint = LocalGlassTint.current.coerceIn(0f, 1f).coerceAtLeast(minTint)
    // A shadow is drawn under the whole shape, so through clearer glass it would show as a dark
    // blot inside it: it fades with the tint and is gone at Clear.
    return this
        .shadow(elevation = 12.dp * tint, shape = shape, ambientColor = Color.Black, spotColor = Color.Black)
        .clip(shape)
        .background(GlassFill.copy(alpha = glassAlpha(tint)))
        .glassRim(shape)
}

/**
 * The glass of the controls that float over the pages (the mini player, round buttons). The real
 * backdrop blur (Haze) came out again: it was the one part that ran only on a phone, where the
 * library pages crashed (owner, build 107).
 */
@Composable
internal fun Modifier.frostedGlass(shape: Shape): Modifier = glass(shape)

/**
 * The video player's round buttons: a darker, never-clear glass (at least [PlayerMinTint] of
 * the slider) with the iOS 27 edge, so they stay readable over snow or a white wall while the
 * picture still shows through, as Samsung's discs let it.
 */
@Composable
internal fun Modifier.playerGlass(shape: Shape): Modifier {
    val tint = LocalGlassTint.current.coerceIn(0f, 1f).coerceAtLeast(PlayerMinTint)
    return this
        .clip(shape)
        .background(Color.Black.copy(alpha = 0.3f + 0.35f * tint))
        .glassRim(shape)
}

/**
 * iOS 27's glass edge: a fine rim that is bright along the top, where the light falls, and fades
 * round to the bottom, with a faint dark line outside it so the glass stands off bright pictures.
 * [strength] (0 to 1) fades it, for a button that fills in with colour.
 */
internal fun Modifier.glassRim(shape: Shape, strength: Float = 1f): Modifier = this
    .border(Hairline, Color.Black.copy(alpha = 0.35f * strength), shape)
    .border(
        RimWidth,
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f * strength), Color.White.copy(alpha = 0.06f * strength))),
        shape,
    )

/**
 * The fill's opacity for a slider setting: from 0.45 (clear) to 0.97 (tinted), eased so the
 * middle of the slider still reads as glass rather than a hole in the page.
 */
internal fun glassAlpha(tint: Float): Float = 0.45f + 0.52f * sqrt(tint.coerceIn(0f, 1f))

private val PillInset = 5.dp
private val GlassFill = Color(0xFF1C1C1E)
private val RimWidth = 1.dp
private const val PlayerMinTint = 0.8f
private val SelectedPillColor = Color(0xFF3A3A3C)

/**
 * The selected tab's colour, as the owner chose: Video, Search and Preferences iOS blue, Audio
 * Apple Music red.
 */
internal fun TopDestination.activeColor(): Color =
    if (this == TopDestination.AUDIO) NovaColors.AppleMusicRed else NovaColors.IosBlue

/** A tab's icon; the selected one pops in with a short spring. */
@Composable
private fun TabIcon(tab: TopDestination, active: Boolean, tint: Color) {
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "tabIcon",
    )
    NovaIcon(
        tab.tabIconRes(),
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .size(24.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    )
}

@DrawableRes
internal fun TopDestination.tabIconRes(): Int = when (this) {
    TopDestination.VIDEO -> R.drawable.ic_tab_video
    TopDestination.AUDIO -> R.drawable.ic_tab_audio
    TopDestination.SETTINGS -> R.drawable.ic_tab_settings
    TopDestination.SEARCH -> R.drawable.ic_search
}

/**
 * Swiping sideways on a tab's own page moves to the next or previous tab (owner). Anything that
 * scrolls sideways itself (the hero, poster rows, sliders) takes the swipe first, so this only
 * sees swipes on the rest of the page; the page follows the finger a little. On a page opened
 * from a tab ([enabled] false) a swipe right goes back instead, as on iOS, when [onBack] is set.
 */
@Composable
internal fun Modifier.swipeBetweenTabs(
    top: TopDestination,
    enabled: Boolean,
    onSelect: (TopDestination) -> Unit,
    onBack: (() -> Unit)? = null,
): Modifier {
    val tabs = TopDestination.entries
    var offset by remember { mutableFloatStateOf(0f) }
    val thresholdPx = with(LocalDensity.current) { 90.dp.toPx() }
    val scope = rememberCoroutineScope()
    val select by rememberUpdatedState(onSelect)
    val back by rememberUpdatedState(onBack)
    val haptics = LocalHapticFeedback.current
    return this
        .pointerInput(top, enabled, onBack != null) {
            if (!enabled && back == null) return@pointerInput
            detectHorizontalDragGestures(
                onDragEnd = {
                    val index = tabs.indexOf(top)
                    val goBack = !enabled && offset > thresholdPx
                    val target = when {
                        !enabled -> null
                        offset < -thresholdPx -> tabs.getOrNull(index + 1)
                        offset > thresholdPx -> tabs.getOrNull(index - 1)
                        else -> null
                    }
                    if (goBack) {
                        offset = 0f
                        back?.invoke()
                    } else if (target != null) {
                        offset = 0f
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        select(target)
                    } else {
                        scope.launch { animate(offset, 0f, animationSpec = spring(dampingRatio = 0.8f)) { value, _ -> offset = value } }
                    }
                },
                onDragCancel = { offset = 0f },
            ) { change, amount ->
                change.consume()
                // Going back only follows a swipe to the right.
                offset = if (enabled) offset + amount else (offset + amount).coerceAtLeast(0f)
            }
        }
        .graphicsLayer { translationX = offset * if (enabled) 0.3f else 0.6f }
}

/** A tab change slides in from the side the new tab lies on; anything else crossfades. */
internal fun tabSlide(from: TopDestination, to: TopDestination, fade: ContentTransform): ContentTransform {
    val tabs = TopDestination.entries
    if (from == to) return fade
    val direction = if (tabs.indexOf(to) > tabs.indexOf(from)) 1 else -1
    return (slideInHorizontally(tween(280)) { it * direction / 4 } + fadeIn(tween(220))) togetherWith
        (slideOutHorizontally(tween(280)) { -it * direction / 4 } + fadeOut(tween(160)))
}
