package com.aakash.novafork.ui.audio

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.ui.layout.onSizeChanged
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
import coil.request.ImageRequest
import com.aakash.novafork.R
import com.aakash.novafork.data.db.SongEntity
import com.aakash.novafork.data.music.MusicLibrary
import com.aakash.novafork.music.MusicPlayerState
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.glass
import com.aakash.novafork.ui.model.RepeatMode
import com.aakash.novafork.ui.theme.NovaColors
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

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
fun NowPlayingHost(
    open: Boolean,
    player: MusicPlayerState,
    actions: AudioActions,
    favorite: Boolean,
    onDismiss: () -> Unit,
    upNext: List<SongEntity> = emptyList(),
) {
    LaunchedEffect(player.active) { if (!player.active && open) onDismiss() }
    AnimatedVisibility(
        visible = open && player.active,
        enter = slideInVertically(tween(320)) { it },
        exit = slideOutVertically(tween(260)) { it },
    ) {
        NowPlayingScreen(player = player, actions = actions, onDismiss = onDismiss, favorite = favorite, upNext = upNext)
    }
}

/** The songs after the playing one, looked up in the library for Now Playing's queue. */
@Composable
fun rememberUpNext(viewModel: MusicViewModel, player: MusicPlayerState): List<SongEntity> {
    val library by viewModel.library.collectAsStateWithLifecycle()
    return remember(player.upNext, library) {
        val byId = library.songs.associateBy { it.id }
        player.upNext.mapNotNull { byId[it] }
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
    // As in Apple Music, a sideways swipe on the mini player skips: left for the next song,
    // right for the one before. The song slides with the finger and springs back.
    var swipe by remember { mutableFloatStateOf(0f) }
    val skipPx = with(LocalDensity.current) { 64.dp.toPx() }
    val swipeState = rememberDraggableState { delta -> swipe = (swipe + delta).coerceIn(-skipPx * 1.6f, skipPx * 1.6f) }
    Row(
        modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .glass(RoundedCornerShape(percent = 50))
            .clickable(onClickLabel = "Open Now Playing", onClick = onOpen)
            .draggable(
                state = swipeState,
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity ->
                    when {
                        swipe < -skipPx && player.hasNext -> actions.next()
                        swipe > skipPx -> actions.previous()
                    }
                    animate(swipe, 0f, velocity, spring(dampingRatio = 0.7f, stiffness = 500f)) { value, _ -> swipe = value }
                },
            )
            .padding(start = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(now?.artUri, size = 42, corner = 8, modifier = Modifier.offset { IntOffset((swipe * 0.5f).roundToInt(), 0) })
        Column(
            Modifier
                .weight(1f)
                .offset { IntOffset(swipe.roundToInt(), 0) }
                .graphicsLayer { alpha = 1f - (kotlin.math.abs(swipe) / (skipPx * 2.2f)).coerceIn(0f, 0.7f) }
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
    /** The songs queued after this one (see [rememberUpNext]). */
    upNext: List<SongEntity> = emptyList(),
) {
    BackHandler(onBack = onDismiss)
    val now = player.nowPlaying
    var pane by rememberSaveable { mutableStateOf(NowPlayingPane.ART) }
    // Pulled down with the finger, frame by frame, without a coroutine per move; let go past a
    // third of the way down or with a flick and it carries on off screen from where it is, else
    // it springs back. Its top corners round as it leaves, like an iOS sheet.
    val density = LocalDensity.current
    val dismissPx = with(density) { 140.dp.toPx() }
    val flingPx = with(density) { 900.dp.toPx() }
    var dragY by remember { mutableFloatStateOf(0f) }
    var heightPx by remember { mutableFloatStateOf(0f) }
    val dragState = rememberDraggableState { delta -> dragY = (dragY + delta).coerceAtLeast(0f) }
    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { heightPx = it.height.toFloat() }
            .graphicsLayer {
                translationY = dragY
                val corner = (dragY / dismissPx).coerceIn(0f, 1f) * 28.dp.toPx()
                shape = RoundedCornerShape(topStart = corner, topEnd = corner)
                clip = dragY > 0f
            }
            .background(Color.Black)
            // Drag down anywhere to close (lyrics scroll and the sliders keep their own drags).
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    if (dragY > dismissPx || velocity > flingPx) {
                        animate(dragY, heightPx.coerceAtLeast(dragY), velocity, tween(220, easing = FastOutLinearInEasing)) { value, _ ->
                            dragY = value
                        }
                        onDismiss()
                    } else {
                        animate(dragY, 0f, velocity, spring(dampingRatio = 0.8f, stiffness = 500f)) { value, _ -> dragY = value }
                    }
                },
            ),
    ) {
        // Apple Music's living background: the cover in colour, slowly turning. It is drawn from a
        // 32px copy of the cover, which upscaled is as soft as a blur and costs nothing per frame
        // (a real blur redrawn while the screen turns and is dragged made the drag stutter).
        val drift = rememberInfiniteTransition(label = "drift")
        val turn by drift.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 90_000, easing = LinearEasing)),
            label = "driftTurn",
        )
        val context = LocalContext.current
        val tinyCover = remember(now?.artUri) {
            now?.artUri?.let { ImageRequest.Builder(context).data(it).size(BACKDROP_PX).build() }
        }
        AsyncImage(
            model = tinyCover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.8f
                    scaleY = 1.8f
                    rotationZ = turn
                },
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
            // No grabber line (owner): the whole screen still drags down to close.
            Spacer(Modifier.height(16.dp))
            if (pane != NowPlayingPane.ART) {
                // Lyrics and the queue keep the song in a small header, as in Apple Music.
                Row(Modifier.padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(now?.artUri, size = 64, corner = 8)
                    SongTitles(player, Modifier.weight(1f).padding(start = 14.dp), small = true)
                    if (now != null) FavoriteButton(favorite) { actions.toggleFavorite(now.songId) }
                }
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
                        NowPlayingPane.QUEUE -> QueuePane(player, actions, upNext)
                    }
                }
            }
            if (pane == NowPlayingPane.ART) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SongTitles(player, Modifier.weight(1f))
                    if (now != null) FavoriteButton(favorite) { actions.toggleFavorite(now.songId) }
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
                BigControl(R.drawable.ic_skip_previous, "Previous", size = 40, onClick = actions::previous)
                BigControl(
                    if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                    if (player.isPlaying) "Pause" else "Play",
                    size = 54,
                    onClick = actions::togglePlay,
                )
                BigControl(R.drawable.ic_skip_next, "Next", size = 40, enabled = player.hasNext, onClick = actions::next)
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                PaneButton(
                    icon = if (pane == NowPlayingPane.LYRICS) R.drawable.ic_lyrics_on else R.drawable.ic_lyrics,
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

/** Title (scrolling when it is too long, as in Apple Music) and artist, or the player's error. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongTitles(player: MusicPlayerState, modifier: Modifier = Modifier, small: Boolean = false) {
    val now = player.nowPlaying
    Column(modifier) {
        Text(
            now?.title ?: "Loading…",
            fontSize = if (small) 17.sp else 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.basicMarquee(initialDelayMillis = 2_000),
        )
        Text(
            player.error ?: listOfNotNull(now?.artist, now?.album.takeUnless { small }).joinToString(" — "),
            fontSize = if (small) 15.sp else 18.sp,
            color = if (player.error != null) NovaColors.AppleMusicRed else Color.White.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Apple Music's star: a small translucent circle (an IconButton would grow to 48dp and stick out
 * past the scrubber's edge).
 */
@Composable
private fun FavoriteButton(favorite: Boolean, onClick: () -> Unit) {
    val label = if (favorite) "Remove from Favorites" else "Add to Favorites"
    Box(
        Modifier
            .padding(start = 12.dp)
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        NovaIcon(
            if (favorite) R.drawable.ic_star else R.drawable.ic_star_outline,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
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
            val current = index == active || !lyrics.synced
            // Apple Music's lyrics: big, bold, left-aligned; the lines around the sung one dim
            // and soften so the eye stays on it.
            val alpha by animateFloatAsState(if (current) 1f else 0.32f, tween(300), label = "lyricAlpha")
            Text(
                line.text.ifEmpty { "♪" },
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                color = Color.White.copy(alpha = alpha),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = lyrics.synced && time != null) { if (time != null) actions.seekTo(time) }
                    .padding(vertical = 10.dp)
                    .then(if (current) Modifier else Modifier.blur(1.5.dp)),
            )
        }
    }
}

/**
 * Apple Music's queue: "Playing Next" with square shuffle and repeat toggles beside it, then the
 * songs to come (tap one to play it), and Stop at the end.
 */
@Composable
private fun QueuePane(player: MusicPlayerState, actions: AudioActions, upNext: List<SongEntity>) {
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "header") {
            Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Playing Next", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    if (player.queueSize > 0) {
                        Text(
                            "Song ${player.queueIndex + 1} of ${player.queueSize}",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                }
                SquareToggle(R.drawable.ic_shuffle, "Shuffle", on = player.shuffle, onClick = actions::toggleShuffle)
                Spacer(Modifier.width(8.dp))
                SquareToggle(
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
        }
        if (upNext.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Nothing queued after this song.",
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
        itemsIndexed(upNext, key = { index, song -> "$index:${song.id}" }) { index, song ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { actions.skipAhead(index + 1) }
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(MusicLibrary.artUri(song), size = 44, corner = 6)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(song.title, fontSize = 16.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        song.artist ?: song.albumArtist ?: "Unknown artist",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        item(key = "stop") {
            Text(
                "Stop Playing",
                color = NovaColors.AppleMusicRed,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = actions::closePlayer)
                    .padding(vertical = 8.dp),
            )
        }
    }
}

/** Apple Music's queue toggles: a rounded square, white with a dark glyph when on. */
@Composable
private fun SquareToggle(@DrawableRes icon: Int, label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 44.dp, height = 34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.15f))
            .clickable(onClickLabel = label, role = Role.Switch, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        NovaIcon(icon, contentDescription = label, tint = if (on) Color.Black else Color.White, modifier = Modifier.size(18.dp))
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

/** The size the Now Playing background is drawn from: small enough to look blurred when upscaled. */
private const val BACKDROP_PX = 32

internal fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
