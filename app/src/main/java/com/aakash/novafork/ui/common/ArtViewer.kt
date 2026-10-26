package com.aakash.novafork.ui.common

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/**
 * A poster or cover seen large (owner: hold it to see it): it grows out to fill the screen over the
 * page, blurred and dimmed behind it (the blur from Android 12), and a tap anywhere puts it back.
 * [art] draws the picture into the modifier it is given.
 */
@Composable
fun ArtViewer(onDismiss: () -> Unit, art: @Composable (Modifier) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(VIEWER_DIM)
            if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply { blurBehindRadius = VIEWER_BLUR_PX }
            }
        }
        val grow = remember { Animatable(0.85f) }
        LaunchedEffect(Unit) { grow.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 380f)) }
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            art(
                Modifier.graphicsLayer {
                    scaleX = grow.value
                    scaleY = grow.value
                    alpha = ((grow.value - 0.85f) / 0.15f).coerceIn(0f, 1f)
                },
            )
        }
    }
}

private const val VIEWER_DIM = 0.7f
private const val VIEWER_BLUR_PX = 80
