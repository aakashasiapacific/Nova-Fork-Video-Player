package com.aakash.novafork.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.aakash.novafork.ui.theme.NovaColors

/**
 * iOS's action sheet, for confirming a delete (owner): it rises from the bottom, in thumb reach,
 * with [title] and [message] small and grey above the red [actionLabel], and Cancel on its own
 * below. A tap outside or back cancels.
 */
@Composable
fun IosActionSheet(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    val shown = remember { MutableTransitionState(false) }.apply { targetState = true }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // The window's own dim appears at once; ours fades in with the sheet instead.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        val scrim = remember { Animatable(0f) }
        LaunchedEffect(Unit) { scrim.animateTo(SCRIM_ALPHA, tween(250)) }
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color.Black.copy(alpha = scrim.value)) }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visibleState = shown,
                enter = slideInVertically(spring(dampingRatio = 0.9f, stiffness = 500f)) { it } + fadeIn(tween(150)),
            ) {
                Column(
                    Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(8.dp)
                        // Taps on the sheet itself don't fall through to the cancel behind it.
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.fillMaxWidth().clip(SheetShape).background(SheetColor)) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = IosColors.SecondaryLabel, textAlign = TextAlign.Center)
                            Text(message, fontSize = 13.sp, color = IosColors.SecondaryLabel, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
                        }
                        Box(Modifier.fillMaxWidth().height(Hairline).background(IosColors.Separator))
                        SheetButton(actionLabel, color = if (destructive) IosColors.Red else NovaColors.IosBlue, bold = false, onClick = onAction)
                    }
                    Box(Modifier.fillMaxWidth().clip(SheetShape).background(SheetColor)) {
                        SheetButton("Cancel", color = NovaColors.IosBlue, bold = true, onClick = onDismiss)
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetButton(label: String, color: Color, bold: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(57.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 20.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, color = color)
    }
}

private val SheetShape = RoundedCornerShape(14.dp)
private const val SCRIM_ALPHA = 0.45f

/** iOS's dark action sheet material. */
private val SheetColor = Color(0xFF2C2C2E)
