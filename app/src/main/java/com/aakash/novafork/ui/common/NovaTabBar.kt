package com.aakash.novafork.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.model.TopDestination
import com.aakash.novafork.ui.theme.NovaColors

/**
 * The tab capsule shared by the phone's bottom dock and the tablet's top bar (iOS 26 / iPad
 * Apple TV): a dark-grey glass capsule with a lighter grey pill behind the selected tab, whose
 * icon and label take the tab's colour; the others are white. A finger dragged along it moves
 * the pill, and the tab under the finger opens when it lifts. Size comes from [modifier].
 */
@Composable
internal fun NovaTabCapsule(selected: TopDestination, onSelect: (TopDestination) -> Unit, modifier: Modifier = Modifier) {
    val tabs = TopDestination.entries
    val currentOnSelect by rememberUpdatedState(onSelect)
    var dragX by remember { mutableStateOf<Float?>(null) }
    BoxWithConstraints(
        modifier
            .glass(RoundedCornerShape(percent = 50))
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
        val pillX by animateDpAsState(slot * active, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow), label = "tabPill")
        Box(
            Modifier
                .offset(x = pillX)
                .size(width = slot, height = maxHeight)
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
                            onClick = { onSelect(tab) },
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
    Box(
        modifier
            .size(size)
            .glass(CircleShape)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** iOS's dark glass: a translucent dark-grey fill, a hairline light edge and a soft shadow. */
internal fun Modifier.glass(shape: Shape): Modifier = this
    .shadow(elevation = 12.dp, shape = shape, ambientColor = Color.Black, spotColor = Color.Black)
    .clip(shape)
    .background(GlassColor)
    .border(0.5.dp, Color.White.copy(alpha = 0.10f), shape)

private val PillInset = 5.dp
private val GlassColor = Color(0xF21C1C1E)
private val SelectedPillColor = Color(0xFF3A3A3C)

/**
 * The selected tab's colour, as the owner chose: Video and Preferences iOS blue, Audio Apple
 * Music red, Search white.
 */
internal fun TopDestination.activeColor(): Color = when (this) {
    TopDestination.VIDEO, TopDestination.SETTINGS -> NovaColors.IosBlue
    TopDestination.AUDIO -> NovaColors.AppleMusicRed
    TopDestination.SEARCH -> Color.White
}

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
    TopDestination.VIDEO -> R.drawable.ic_tv
    TopDestination.AUDIO -> R.drawable.ic_music_notes
    TopDestination.SETTINGS -> R.drawable.ic_person
    TopDestination.SEARCH -> R.drawable.ic_search
}
