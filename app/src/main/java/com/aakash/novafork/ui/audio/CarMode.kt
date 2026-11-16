package com.aakash.novafork.ui.audio

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aakash.novafork.R
import com.aakash.novafork.music.MusicPlayerState
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.theme.NovaColors

/**
 * Car Mode: the whole screen black with the song in big type and three huge buttons (previous,
 * play or pause, next) that are easy to hit without looking. Back or Done leaves it.
 */
@Composable
fun CarMode(player: MusicPlayerState, actions: AudioActions, onDone: () -> Unit) {
    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .safeDrawingPadding()
                .padding(24.dp),
        ) {
            Text(
                "Done",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = NovaColors.AppleMusicRed,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "Leave Car Mode", role = Role.Button, onClick = onDone)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    player.nowPlaying?.title.orEmpty(),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    player.nowPlaying?.artist.orEmpty(),
                    fontSize = 22.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(56.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    CarButton(R.drawable.ic_skip_previous, "Previous", 96.dp, onClick = actions::previous)
                    CarButton(
                        if (player.playRequested) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                        if (player.playRequested) "Pause" else "Play",
                        128.dp,
                        filled = true,
                        onClick = actions::togglePlay,
                    )
                    CarButton(R.drawable.ic_skip_next, "Next", 96.dp, onClick = actions::next)
                }
            }
        }
    }
}

@Composable
private fun CarButton(
    @DrawableRes icon: Int,
    label: String,
    size: androidx.compose.ui.unit.Dp,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) NovaColors.AppleMusicRed else Color.White.copy(alpha = 0.12f))
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        NovaIcon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(size * 0.45f))
    }
}
