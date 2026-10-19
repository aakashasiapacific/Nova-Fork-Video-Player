package com.aakash.novafork.ui.audio

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.aakash.novafork.R
import com.aakash.novafork.music.MusicPlayerState
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.glass
import com.aakash.novafork.ui.model.RepeatMode
import com.aakash.novafork.ui.theme.NovaColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The real app turns this on (MainActivity): the music mini player and Now Playing then live in
 * the app shell, over every tab. Previews and screenshot tests have no music engine.
 */
val LocalMusicEnabled = staticCompositionLocalOf { false }

/**
 * The app shell's music: the shared [MusicViewModel] and its player state, or null where music
 * is off (previews, tests).
 */
@Composable
fun rememberMusicShell(): Pair<MusicViewModel, MusicPlayerState>? {
    if (!LocalMusicEnabled.current) return null
    val viewModel: MusicViewModel = viewModel()
    val player by viewModel.playerState.collectAsStateWithLifecycle()
    return viewModel to player
}

/**
 * Now Playing over the whole app, sliding up from the bottom; closes itself when the player
 * does.
 */
@Composable
fun NowPlayingHost(open: Boolean, player: MusicPlayerState, actions: AudioActions, favorite: Boolean, onDismiss: () -> Unit) {
    LaunchedEffect(player.active) { if (!player.active && open) onDismiss() }
    AnimatedVisibility(
        visible = open && player.active,
        enter = slideInVertically(tween(320)) { it },
        exit = slideOutVertically(tween(260)) { it },
    ) {
        NowPlayingScreen(player = player, actions = actions, onDismiss = onDismiss, favorite = favorite)
    }
}

/** The floating mini player's height; the space pages leave for it comes from the shell. */
val MiniPlayerHeight = 60.dp

/**
 * Apple Music's mini player: a floating glass capsule with the cover, the song and artist, and
 * play/pause and next. Tapping it opens Now Playing.
 */
@Composable
fun MiniPlayerBar(
    player: MusicPlayerState,
    actions: AudioActions,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    /** In the shrunk tab bar: no Next button, so the song's title has room. */
    compact: Boolean = false,
) {
    val now = player.nowPlaying
    Row(
        modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .glass(RoundedCornerShape(percent = 50))
            .clickable(onClickLabel = "Open Now Playing", onClick = onOpen)
            .padding(start = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(now?.artUri, size = 42, corner = 8)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                now?.title ?: "Loading…",
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                player.error ?: now?.artist.orEmpty(),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = if (player.error != null) NovaColors.AppleMusicRed else Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = actions::togglePlay) {
            NovaIcon(
                if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                contentDescription = if (player.isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
        if (!compact) {
            IconButton(onClick = actions::next, enabled = player.hasNext) {
                NovaIcon(
                    R.drawable.ic_skip_next,
                    contentDescription = "Next",
                    tint = Color.White.copy(alpha = if (player.hasNext) 1f else 0.35f),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

private enum class NowPlayingPane { ART, LYRICS, QUEUE }

/**
 * Apple Music's Now Playing, full screen: the cover blurred behind everything, the big cover
 * (smaller while paused), title and artist, a thin scrubber with times, previous / play-pause /
 * next, the system volume, and lyrics and queue buttons. Swipe down or back closes it.
 */
@Composable
fun NowPlayingScreen(
    player: MusicPlayerState,
    actions: AudioActions,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    favorite: Boolean = false,
) {
    BackHandler(onBack = onDismiss)
    val now = player.nowPlaying
    var pane by rememberSaveable { mutableStateOf(NowPlayingPane.ART) }
    val scope = rememberCoroutineScope()
    val dragY = remember { Animatable(0f) }
    val dismissPx = with(LocalDensity.current) { 140.dp.toPx() }
    Box(
        modifier
            .fillMaxSize()
            .offset { IntOffset(0, dragY.value.roundToInt().coerceAtLeast(0)) }
            .background(Color.Black)
            // Drag down anywhere to close (lyrics scroll and the sliders keep their own drags).
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (dragY.value > dismissPx) onDismiss() else dragY.animateTo(0f, spring())
                        }
                    },
                    onDragCancel = { scope.launch { dragY.animateTo(0f, spring()) } },
                ) { change, amount ->
                    change.consume()
                    scope.launch { dragY.snapTo((dragY.value + amount).coerceAtLeast(0f)) }
                }
            },
    ) {
        AsyncImage(
            model = now?.artUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(90.dp),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
        ) {
            // The grabber: a hint; the whole sheet drags down to close, as in Apple Music.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 40.dp, height = 5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.4f)),
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = pane,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                    label = "nowPlayingPane",
                ) { shown ->
                    when (shown) {
                        NowPlayingPane.ART -> BigCover(player)
                        NowPlayingPane.LYRICS -> LyricsPane(player, actions)
                        NowPlayingPane.QUEUE -> QueuePane(player, actions)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        now?.title ?: "Loading…",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        player.error ?: listOfNotNull(now?.artist, now?.album).joinToString(" — "),
                        fontSize = 18.sp,
                        color = if (player.error != null) NovaColors.AppleMusicRed else Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Apple Music's star: a small translucent circle whose right edge lines up with
                // the scrubber's (an IconButton would grow to 48dp and stick out past it).
                if (now != null) {
                    Box(
                        Modifier
                            .padding(start = 12.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable(
                                onClickLabel = if (favorite) "Remove from Favorites" else "Add to Favorites",
                                role = Role.Button,
                            ) { actions.toggleFavorite(now.songId) },
                        contentAlignment = Alignment.Center,
                    ) {
                        NovaIcon(
                            if (favorite) R.drawable.ic_star else R.drawable.ic_star_outline,
                            contentDescription = if (favorite) "Remove from Favorites" else "Add to Favorites",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Scrubber(player, actions)
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BigControl(R.drawable.ic_skip_previous, "Previous", size = 44, onClick = actions::previous)
                BigControl(
                    if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                    if (player.isPlaying) "Pause" else "Play",
                    size = 64,
                    onClick = actions::togglePlay,
                )
                BigControl(R.drawable.ic_skip_next, "Next", size = 44, enabled = player.hasNext, onClick = actions::next)
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                PaneButton(
                    icon = R.drawable.ic_subtitles,
                    label = "Lyrics",
                    active = pane == NowPlayingPane.LYRICS,
                    enabled = player.lyrics != null,
                ) { pane = if (pane == NowPlayingPane.LYRICS) NowPlayingPane.ART else NowPlayingPane.LYRICS }
                PaneButton(
                    icon = R.drawable.ic_view_list,
                    label = "Queue",
                    active = pane == NowPlayingPane.QUEUE,
                ) { pane = if (pane == NowPlayingPane.QUEUE) NowPlayingPane.ART else NowPlayingPane.QUEUE }
            }
        }
    }
}

/** The big cover; it shrinks back a little while paused, as in Apple Music. */
@Composable
private fun BigCover(player: MusicPlayerState) {
    val scale by animateFloatAsState(if (player.isPlaying) 1f else 0.82f, spring(dampingRatio = 0.7f), label = "coverScale")
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        Box(
            Modifier
                .size(side)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(24.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2C2C2E)),
            contentAlignment = Alignment.Center,
        ) {
            NovaIcon(R.drawable.ic_music_notes, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(side / 3))
            AsyncImage(
                model = player.nowPlaying?.artUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Big, bold lyric lines; synced ones light up as the song plays and a tap jumps to them. */
@Composable
private fun LyricsPane(player: MusicPlayerState, actions: AudioActions) {
    val lyrics = player.lyrics
    if (lyrics == null) {
        Text("No lyrics for this song", color = Color.White.copy(alpha = 0.6f), fontSize = 18.sp)
        return
    }
    val active = lyrics.activeIndex(player.positionMs)
    val listState = rememberLazyListState()
    LaunchedEffect(active) { if (active > 0) listState.animateScrollToItem((active - 1).coerceAtLeast(0)) }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(lyrics.lines) { index, line ->
            val time = line.timeMs
            Text(
                line.text.ifEmpty { "♪" },
                fontSize = 26.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                color = if (index == active || !lyrics.synced) Color.White else Color.White.copy(alpha = 0.35f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = lyrics.synced && time != null) { if (time != null) actions.seekTo(time) }
                    .padding(vertical = 8.dp),
            )
        }
    }
}

/** Where the queue stands, with shuffle and repeat. */
@Composable
private fun QueuePane(player: MusicPlayerState, actions: AudioActions) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Playing Next", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        if (player.queueSize > 0) {
            Text(
                "Song ${player.queueIndex + 1} of ${player.queueSize}",
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TogglePill(R.drawable.ic_shuffle, "Shuffle", on = player.shuffle, onClick = actions::toggleShuffle)
            TogglePill(
                if (player.repeat == RepeatMode.ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat,
                when (player.repeat) {
                    RepeatMode.OFF -> "Repeat"
                    RepeatMode.ALL -> "Repeat all"
                    RepeatMode.ONE -> "Repeat one"
                },
                on = player.repeat != RepeatMode.OFF,
                onClick = actions::cycleRepeat,
            )
        }
        Text(
            "Stop and close player",
            color = NovaColors.AppleMusicRed,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(top = 24.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = actions::closePlayer)
                .padding(8.dp),
        )
    }
}

@Composable
private fun TogglePill(@DrawableRes icon: Int, label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (on) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.15f))
            .clickable(role = Role.Switch, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (on) Color.Black else Color.White
        NovaIcon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, color = tint, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Apple Music's thin scrubber: grows while held, elapsed and remaining time below. */
@Composable
private fun Scrubber(player: MusicPlayerState, actions: AudioActions) {
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    val duration = player.durationMs.coerceAtLeast(1L)
    val fraction = scrubbing ?: (player.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    ThinSlider(
        value = fraction,
        enabled = player.durationMs > 0,
        onChange = { scrubbing = it },
        onChangeFinished = {
            scrubbing?.let { actions.seekTo((it * duration).toLong()) }
            scrubbing = null
        },
    )
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        val shownMs = (fraction * duration).toLong()
        Text(formatTime(shownMs), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
        Text("-" + formatTime((player.durationMs - shownMs).coerceAtLeast(0)), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
    }
}

/** A thin white track that thickens while dragged; tap or drag anywhere along it. */
@Composable
private fun ThinSlider(
    value: Float,
    onChange: (Float) -> Unit,
    onChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var held by remember { mutableStateOf(false) }
    val thickness by animateDpAsState(if (held) 10.dp else 6.dp, label = "sliderThickness")
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        held = true
                        tryAwaitRelease()
                        held = false
                    },
                    onTap = { offset ->
                        onChange((offset.x / size.width).coerceIn(0f, 1f))
                        onChangeFinished()
                    },
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { held = true },
                    onDragEnd = {
                        held = false
                        onChangeFinished()
                    },
                    onDragCancel = {
                        held = false
                        onChangeFinished()
                    },
                ) { change, _ ->
                    change.consume()
                    onChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(thickness)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.25f)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(value.coerceIn(0f, 1f))
                    .background(Color.White.copy(alpha = if (held) 1f else 0.75f)),
            )
        }
    }
}

@Composable
private fun BigControl(@DrawableRes icon: Int, description: String, size: Int, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size((size + 20).dp)) {
        NovaIcon(
            icon,
            contentDescription = description,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size(size.dp),
        )
    }
}

@Composable
private fun PaneButton(@DrawableRes icon: Int, label: String, active: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) Color.White.copy(alpha = 0.9f) else Color.Transparent),
    ) {
        NovaIcon(
            icon,
            contentDescription = label,
            tint = when {
                active -> Color.Black
                enabled -> Color.White.copy(alpha = 0.8f)
                else -> Color.White.copy(alpha = 0.3f)
            },
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * A cover image, with a note glyph while it loads or when the song has none. [size] in dp; 0
 * takes its size from [modifier] (a grid cell, the album page).
 */
@Composable
internal fun Cover(uri: String?, size: Int, corner: Int = 8, round: Boolean = false, modifier: Modifier = Modifier) {
    val shape = if (round) CircleShape else RoundedCornerShape(corner.dp)
    Box(
        (if (size > 0) modifier.size(size.dp) else modifier)
            .clip(shape)
            .background(Color(0xFF2C2C2E)),
        contentAlignment = Alignment.Center,
    ) {
        NovaIcon(
            R.drawable.ic_music_notes,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(if (size > 0) (size * 0.4f).dp else 40.dp),
        )
        if (uri != null) {
            AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

internal fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
