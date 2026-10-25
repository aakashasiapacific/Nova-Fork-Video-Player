package com.aakash.novafork.ui.audio

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aakash.novafork.R
import com.aakash.novafork.data.db.SongEntity
import com.aakash.novafork.data.music.MusicLibrary
import com.aakash.novafork.music.MusicPlayerState
import com.aakash.novafork.ui.common.IosIconButton
import com.aakash.novafork.ui.common.NovaDropdownMenu
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.NovaMenuItem
import com.aakash.novafork.ui.common.frostedGlass
import com.aakash.novafork.ui.model.RepeatMode
import com.aakash.novafork.ui.theme.AudioTheme
import com.aakash.novafork.ui.theme.NovaColors
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    menu: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
    sheet: NowPlayingSheet? = null,
) {
    LaunchedEffect(player.active) { if (!player.active && open) onDismiss() }
    // One number, 0 closed to 1 open, moves everything: the sheet's place, the finger's drag on
    // it, and (through [sheet]) the page behind being pushed back, as an iOS sheet does.
    val visible = open && player.active
    val progress = remember { Animatable(0f) }
    var drag by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(visible) {
        // Opened by pulling the mini player up: it carries on from where the finger left it.
        val pulled = sheet?.pull ?: 0f
        if (visible && pulled > 0f) {
            progress.snapTo(pulled)
            sheet?.pull = 0f
        }
        progress.animateTo(
            if (visible) 1f else 0f,
            if (visible) spring(dampingRatio = 0.86f, stiffness = 300f) else spring(dampingRatio = 1f, stiffness = 380f),
        )
    }
    val fraction = (progress.value - drag + (sheet?.pull ?: 0f)).coerceIn(0f, 1f)
    SideEffect { sheet?.openFraction = fraction }
    DisposableEffect(sheet) { onDispose { sheet?.openFraction = 0f } }
    if (fraction > 0f || visible) {
        NowPlayingScreen(
            player = player,
            actions = actions,
            onDismiss = onDismiss,
            favorite = favorite,
            upNext = upNext,
            menu = menu,
            progress = fraction,
            onDragBy = { delta -> drag = (drag + delta).coerceIn(0f, progress.value) },
            onRelease = { velocity, dismiss ->
                scope.launch {
                    progress.snapTo(fraction)
                    drag = 0f
                    if (dismiss) {
                        onDismiss()
                    } else {
                        progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 500f), initialVelocity = -velocity)
                    }
                }
            },
        )
    }
}

/**
 * How far Now Playing is open, 0 (closed) to 1 (open); the shell pushes its page back with it.
 * It also knows where the big cover and the mini player's cover sit, so that while Now Playing
 * opens or closes one cover flies between them ([CoverFlight]) as in Apple Music.
 */
@Stable
class NowPlayingSheet {
    var openFraction by mutableFloatStateOf(0f)

    /** How far the mini player is being pulled up toward Now Playing (0 to 1), by the finger. */
    var pull by mutableFloatStateOf(0f)

    /** Now Playing's height, which its slide is measured in. */
    var heightPx by mutableFloatStateOf(0f)

    /** The big cover where it sits when Now Playing is fully open; null when not shown. */
    var bigCover by mutableStateOf<Rect?>(null)

    /** The mini player's cover; null when there is no mini player. */
    var miniCover by mutableStateOf<Rect?>(null)

    /** Now Playing's own layout, which the big cover is measured within (its slide left out). */
    var sheetCoordinates: LayoutCoordinates? = null

    /** Between open and closed, with both ends known: [CoverFlight] draws the cover. */
    val flying: Boolean
        get() = openFraction > 0f && openFraction < 1f && bigCover != null && miniCover != null
}

/** The shell's sheet, for the covers to report where they are; null outside the phone shell. */
val LocalNowPlayingSheet = staticCompositionLocalOf<NowPlayingSheet?> { null }

/**
 * The one cover in flight between Now Playing and the mini player: it travels in a straight
 * line between the two, shrinking as it goes, in step with the sheet from start to end. Opening
 * runs the same path backwards. Drawn by the shell over all.
 */
@Composable
fun CoverFlight(artUri: String?) {
    val sheet = LocalNowPlayingSheet.current ?: return
    if (artUri == null) return
    val big = sheet.bigCover ?: return
    val context = LocalContext.current
    // Loaded ahead at one fixed size and laid out once at the big cover's size; the flight only
    // moves and scales it in the draw phase, in the same frame the real covers hide (owner: no
    // blank frame when the drag starts, no reload or dark box as it shrinks).
    val request = remember(artUri) {
        ImageRequest.Builder(context).data(artUri).size(FLIGHT_PX).crossfade(false).build()
    }
    val density = LocalDensity.current
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(with(density) { big.width.toDp() }, with(density) { big.height.toDp() })
            .graphicsLayer {
                val mini = sheet.miniCover
                if (!sheet.flying || mini == null) {
                    alpha = 0f
                    return@graphicsLayer
                }
                // Apple Music's flight: one straight diagonal from the big cover's place to the
                // mini player's, place, size and corners all moving together with the sheet from
                // the first pixel (owner: it used to ride down with the sheet, then swing across).
                val t = (1f - sheet.openFraction).coerceIn(0f, 1f)
                fun mix(a: Float, b: Float) = a + (b - a) * t
                alpha = 1f
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = mix(big.left, mini.left)
                translationY = mix(big.top, mini.top)
                scaleX = mix(1f, mini.width / big.width)
                scaleY = mix(1f, mini.height / big.height)
                // The corner as seen, 12dp to the mini player's 8dp, through the scale.
                shape = RoundedCornerShape(mix(12f, 8f).dp.toPx() / scaleX.coerceAtLeast(0.01f))
                clip = true
            },
    )
}

/** How long the mini player takes to fade from one song to the next. */
private const val MINI_FADE_MS = 280

/** What the big cover shows, and where in the queue, so a skip knows which way to slide. */
/** [ranOut]: the song before played to its end (an album playing through), not skipped. */
private data class CoverSlide(val artUri: String?, val songId: Long?, val index: Int, val ranOut: Boolean = false)

/** Whether the song before the current one played to its end, kept per song. */
private class SongChange {
    var songId: Long? = null
    var nearEnd = false
    var ranOut = false
}

/** How long lyrics scrolled by hand stay lit and unfollowed after the finger lifts. */
private const val LYRICS_BROWSE_MS = 3_000L

/** How much larger the cover lands before it settles. */
private const val SETTLE_FROM = 1.035f

/** The size the flying cover is decoded at, sharp enough for the big cover. */
private const val FLIGHT_PX = 720

/**
 * Now Playing for the app shell: the star, the queue, and Apple Music's "…" with Add to a
 * Playlist…, Go to Album and Go to Artist (which close Now Playing and open the page in the Audio
 * tab, [onShowAudio] switching to it).
 */
@Composable
fun ShellNowPlaying(
    viewModel: MusicViewModel,
    player: MusicPlayerState,
    open: Boolean,
    onDismiss: () -> Unit,
    onShowAudio: () -> Unit,
    sheet: NowPlayingSheet? = null,
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val song = remember(player.nowPlaying?.songId, library) { library.songs.firstOrNull { it.id == player.nowPlaying?.songId } }
    var adding by remember { mutableStateOf<SongEntity?>(null) }
    NowPlayingHost(
        open = open,
        player = player,
        actions = viewModel,
        favorite = player.nowPlaying?.songId in favorites,
        upNext = rememberUpNext(viewModel, player),
        onDismiss = onDismiss,
        sheet = sheet,
        menu = song?.let { current ->
            { close ->
                NovaMenuItem(
                    text = { Text("Add to a Playlist…") },
                    leadingIcon = { NovaIcon(R.drawable.ic_playlist_add, contentDescription = null) },
                    onClick = {
                        close()
                        adding = current
                    },
                )
                NovaMenuItem(
                    text = { Text("Go to Album") },
                    leadingIcon = { NovaIcon(R.drawable.ic_album, contentDescription = null) },
                    onClick = {
                        close()
                        viewModel.requestPage(albumPage(current.albumKey))
                        onShowAudio()
                        onDismiss()
                    },
                )
                NovaMenuItem(
                    text = { Text("Go to Artist") },
                    leadingIcon = { NovaIcon(R.drawable.ic_artist, contentDescription = null) },
                    onClick = {
                        close()
                        viewModel.requestPage(artistPage(current.artistKey))
                        onShowAudio()
                        onDismiss()
                    },
                )
            }
        },
    )
    adding?.let { picked ->
        AudioTheme {
            AddToPlaylistDialog(
                song = picked,
                playlists = playlists,
                library = library,
                onPick = { id, name ->
                    viewModel.addToPlaylist(picked, id, name)
                    adding = null
                },
                onDismiss = { adding = null },
            )
        }
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

/** The docked (old Apple Music) mini player's height on the phone. */
val MiniPlayerDockedHeight = 64.dp

/**
 * Apple Music's mini player: a floating glass capsule with the cover, the song and artist, and
 * play/pause and next. Tapping it opens Now Playing. [docked] (phone, owner: the old tab bar wants
 * the old mini player): iOS 13–17's instead, a full-width strip sitting on the tab bar in its
 * material (the shell paints it), a bigger cover and the title alone on one line.
 */
@Composable
fun MiniPlayerBar(
    player: MusicPlayerState,
    actions: AudioActions,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    /** In the shrunk tab bar: no Next button, so the song's title has room. */
    compact: Boolean = false,
    docked: Boolean = false,
) {
    val now = player.nowPlaying
    // As in Apple Music, a sideways swipe on the mini player skips: left for the next song,
    // right for the one before. The song slides with the finger and springs back.
    var swipe by remember { mutableFloatStateOf(0f) }
    val skipPx = with(LocalDensity.current) { 64.dp.toPx() }
    val swipeState = rememberDraggableState { delta -> swipe = (swipe + delta).coerceIn(-skipPx * 1.6f, skipPx * 1.6f) }
    val haptics = LocalHapticFeedback.current
    Row(
        modifier
            .fillMaxWidth()
            .height(if (docked) MiniPlayerDockedHeight else MiniPlayerHeight)
            .then(if (docked) Modifier else Modifier.frostedGlass(RoundedCornerShape(percent = 50)))
            .clickable(onClickLabel = "Open Now Playing", onClick = onOpen)
            .draggable(
                state = swipeState,
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity ->
                    when {
                        swipe < -skipPx && player.hasNext -> {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            actions.next()
                        }
                        swipe > skipPx -> {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            actions.previous()
                        }
                    }
                    animate(swipe, 0f, velocity, spring(dampingRatio = 0.7f, stiffness = 500f)) { value, _ -> swipe = value }
                },
            )
            .padding(start = if (docked) 16.dp else 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val coverSize = if (docked) 48 else 42
        val coverCorner = if (docked) 6 else 8
        val sheet = LocalNowPlayingSheet.current
        DisposableEffect(sheet) { onDispose { sheet?.miniCover = null } }
        Box(
            Modifier
                .offset { IntOffset((swipe * 0.5f).roundToInt(), 0) }
                .onGloballyPositioned { if (sheet != null) sheet.miniCover = it.boundsInRoot() }
                // The flying cover lands here; this one shows once Now Playing is closed.
                .graphicsLayer { alpha = if (sheet != null && sheet.openFraction > 0f && sheet.bigCover != null) 0f else 1f },
            contentAlignment = Alignment.Center,
        ) {
            // A new song's cover and name fade in over the old ones rather than switching.
            Crossfade(targetState = now?.artUri, animationSpec = tween(MINI_FADE_MS), label = "miniCover") { art ->
                Cover(art, size = coverSize, corner = coverCorner)
            }
            // Little bars over the cover: dancing while it plays, still when paused.
            if (now != null) {
                Box(
                    Modifier
                        .size(coverSize.dp)
                        .clip(RoundedCornerShape(coverCorner.dp))
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    PlayingBars(playing = player.isPlaying, color = Color.White)
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .offset { IntOffset(swipe.roundToInt(), 0) }
                .graphicsLayer { alpha = 1f - (kotlin.math.abs(swipe) / (skipPx * 2.2f)).coerceIn(0f, 0.7f) }
                .padding(horizontal = 12.dp),
        ) {
            Crossfade(targetState = now?.title ?: "Loading…", animationSpec = tween(MINI_FADE_MS), label = "miniTitle") { title ->
                Text(
                    title,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                    fontWeight = if (docked) FontWeight.Normal else FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // The old docked one names only the song, unless something went wrong.
            if (!docked || player.error != null) {
                Text(
                    player.error ?: now?.artist.orEmpty(),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = if (player.error != null) NovaColors.AppleMusicRed else Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IosIconButton(onClick = actions::togglePlay) {
            NovaIcon(
                if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                contentDescription = if (player.isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
        if (!compact) {
            IosIconButton(onClick = actions::next, enabled = player.hasNext) {
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
    /** Apple Music's "…" beside the star; null = none (previews). */
    menu: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
    /** How far it is open, 0 to 1 ([NowPlayingHost] moves it); 1 stands still. */
    progress: Float = 1f,
    /** The finger pulled it down by this part of the screen's height. */
    onDragBy: (Float) -> Unit = {},
    /** The finger let go, moving down at this many screen heights a second; close it or not. */
    onRelease: (velocity: Float, dismiss: Boolean) -> Unit = { _, dismiss -> if (dismiss) onDismiss() },
) {
    BackHandler(onBack = onDismiss)
    val now = player.nowPlaying
    var pane by rememberSaveable { mutableStateOf(NowPlayingPane.ART) }
    // Pulled down with the finger, frame by frame; let go past 140dp or with a flick and it
    // closes, else it springs back ([NowPlayingHost] runs both). Its top corners round as it
    // leaves, like an iOS sheet, and near the bottom it fades into the mini player.
    val density = LocalDensity.current
    val dismissPx = with(density) { 140.dp.toPx() }
    val flingPx = with(density) { 900.dp.toPx() }
    var heightPx by remember { mutableFloatStateOf(0f) }
    val sheet = LocalNowPlayingSheet.current
    val offsetPx = (1f - progress) * heightPx
    val dragState = rememberDraggableState { delta -> if (heightPx > 0f) onDragBy(delta / heightPx) }
    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged {
                heightPx = it.height.toFloat()
                sheet?.heightPx = heightPx
            }
            .graphicsLayer {
                translationY = offsetPx
                alpha = (progress / 0.3f).coerceIn(0f, 1f)
                val corner = (offsetPx / dismissPx).coerceIn(0f, 1f) * 28.dp.toPx()
                shape = RoundedCornerShape(topStart = corner, topEnd = corner)
                clip = offsetPx > 0f
            }
            .onGloballyPositioned { sheet?.sheetCoordinates = it }
            .background(Color.Black)
            // Drag down anywhere to close (lyrics scroll and the sliders keep their own drags).
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    val dismiss = offsetPx > dismissPx || velocity > flingPx
                    onRelease(if (heightPx > 0f) velocity / heightPx else 0f, dismiss)
                },
            ),
    ) {
        CoverGradient(now?.artUri)
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
                    if (menu != null) NowPlayingMore(menu)
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
                    if (menu != null) NowPlayingMore(menu)
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

/**
 * Title and artist, each scrolling sideways when too long for its line (a pause, a slow pass,
 * a pause), as in Apple Music; or the player's error.
 */
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
        val error = player.error
        Text(
            error ?: listOfNotNull(now?.artist, now?.album.takeUnless { small }).joinToString(" — "),
            fontSize = if (small) 15.sp else 18.sp,
            color = if (error != null) NovaColors.AppleMusicRed else Color.White.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = if (error != null) TextOverflow.Ellipsis else TextOverflow.Clip,
            modifier = if (error != null) Modifier else Modifier.basicMarquee(initialDelayMillis = 2_000),
        )
    }
}

/** Apple Music's "…" beside the star: the same small translucent circle, opening [menu]. */
@Composable
private fun NowPlayingMore(menu: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .padding(start = 10.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f))
                .clickable(onClickLabel = "More", role = Role.Button) { open = true },
            contentAlignment = Alignment.Center,
        ) {
            NovaIcon(R.drawable.ic_more_vert, contentDescription = "More", tint = Color.White, modifier = Modifier.size(18.dp))
        }
        NovaDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            menu { open = false }
        }
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
    // Apple Music: paused, the cover sinks back small with a light shadow; playing, it springs
    // forward with a deep one.
    val scale by animateFloatAsState(
        if (player.isPlaying) 1f else 0.8f,
        spring(dampingRatio = 0.62f, stiffness = 300f),
        label = "coverScale",
    )
    val shadow by animateDpAsState(if (player.isPlaying) 30.dp else 8.dp, spring(stiffness = 300f), label = "coverShadow")
    val sheet = LocalNowPlayingSheet.current
    DisposableEffect(sheet) { onDispose { sheet?.bigCover = null } }
    // As Now Playing finishes opening, the cover that flew in settles with a small spring, as
    // Apple Music's does.
    val settle = remember { Animatable(1f) }
    val landed by remember(sheet) { derivedStateOf { sheet == null || sheet.openFraction >= 1f } }
    LaunchedEffect(landed) {
        if (landed && sheet != null) {
            settle.snapTo(SETTLE_FROM)
            settle.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f))
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        Box(
            Modifier
                .size(side)
                .graphicsLayer {
                    scaleX = scale * settle.value
                    scaleY = scale * settle.value
                    // While the cover flies to or from the mini player, the flying one is shown,
                    // shadow and all (a hidden cover's shadow was the dark box around it).
                    val flying = sheet?.flying == true
                    alpha = if (flying) 0f else 1f
                    shadowElevation = if (flying) 0f else shadow.toPx()
                    shape = RoundedCornerShape(12.dp)
                }
                .onGloballyPositioned { coordinates ->
                    // Where it sits within Now Playing, so the sheet's own slide is left out.
                    val root = sheet?.sheetCoordinates
                    if (sheet != null && root != null && root.isAttached && coordinates.isAttached) {
                        sheet.bigCover = root.localBoundingBoxOf(coordinates, clipBounds = false)
                    }
                }
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2C2C2E)),
            contentAlignment = Alignment.Center,
        ) {
            NovaIcon(R.drawable.ic_music_notes, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(side / 3))
            // Skipping slides the old cover out and the new one in from the side it comes from
            // (next from the right, previous from the left), as Apple Music does.
            val change = remember { SongChange() }
            val songId = player.nowPlaying?.songId
            if (songId != change.songId) {
                change.ranOut = change.songId != null && change.nearEnd
                change.songId = songId
            }
            change.nearEnd = player.durationMs > 0 && player.positionMs >= player.durationMs - RAN_OUT_MARGIN_MS
            AnimatedContent(
                targetState = CoverSlide(player.nowPlaying?.artUri, songId, player.queueIndex, change.ranOut),
                transitionSpec = {
                    // Played through into the next song: a slow crossfade (one album into the next).
                    if (targetState.ranOut) {
                        fadeIn(tween(ALBUM_FADE_MS)) togetherWith fadeOut(tween(ALBUM_FADE_MS))
                    } else {
                        val forward = targetState.index >= initialState.index
                        val slide = spring<IntOffset>(dampingRatio = 0.9f, stiffness = 420f)
                        (slideInHorizontally(slide) { if (forward) it else -it } + fadeIn(tween(160))) togetherWith
                            (slideOutHorizontally(slide) { if (forward) -it else it } + fadeOut(tween(160)))
                    }
                },
                contentKey = { it.songId },
                modifier = Modifier.fillMaxSize(),
                label = "coverSlide",
            ) { cover ->
                AsyncImage(
                    model = cover.artUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** How close to its end a song counts as played through when the next one starts. */
private const val RAN_OUT_MARGIN_MS = 2_500L
private const val ALBUM_FADE_MS = 600

/**
 * Big, bold lyric lines; synced ones light up as the song plays and a tap jumps to them (with a
 * tick, playing again if paused). Scrolling them by hand lights every line up for reading and
 * stops the follow for a moment, as in Apple Music.
 */
@Composable
private fun LyricsPane(player: MusicPlayerState, actions: AudioActions) {
    val lyrics = player.lyrics
    if (lyrics == null) {
        Text("No lyrics for this song", color = Color.White.copy(alpha = 0.6f), fontSize = 18.sp)
        return
    }
    val active = lyrics.activeIndex(player.positionMs)
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var browsing by remember { mutableStateOf(false) }
    LaunchedEffect(dragged) {
        if (dragged) {
            browsing = true
        } else if (browsing) {
            delay(LYRICS_BROWSE_MS)
            browsing = false
        }
    }
    LaunchedEffect(active, browsing) { if (active > 0 && !browsing) listState.animateScrollToItem((active - 1).coerceAtLeast(0)) }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(lyrics.lines) { index, line ->
            val time = line.timeMs
            // Apple Music's lyrics: big, bold, left-aligned. The sung line is bright and full
            // size; the further a line is from it, the dimmer, smaller and softer it gets, and
            // each change glides rather than jumps.
            val distance = if (lyrics.synced && active >= 0 && !browsing) kotlin.math.abs(index - active) else 0
            val alpha by animateFloatAsState(
                when (distance) {
                    0 -> 1f
                    1 -> 0.5f
                    2 -> 0.34f
                    else -> 0.24f
                },
                tween(450),
                label = "lyricAlpha",
            )
            val scale by animateFloatAsState(if (distance == 0) 1f else 0.94f, spring(dampingRatio = 0.8f, stiffness = 250f), label = "lyricScale")
            val blur by animateDpAsState((distance.coerceAtMost(3) * 1.4f).dp, tween(450), label = "lyricBlur")
            Text(
                line.text.ifEmpty { "♪" },
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                color = Color.White.copy(alpha = alpha),
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = lyrics.synced && time != null) {
                        if (time != null) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            actions.seekTo(time)
                            if (!player.isPlaying) actions.togglePlay()
                            browsing = false
                        }
                    }
                    .padding(vertical = 10.dp)
                    .then(if (blur > 0.dp) Modifier.blur(blur) else Modifier),
            )
        }
    }
}

/**
 * Apple Music's queue: "Playing Next" with square shuffle and repeat toggles beside it, then the
 * songs to come (tap one to play it; hold one, or drag its handle, to move it: it lifts and the
 * others slide aside, with a tick at each new place), and Stop at the end.
 */
@Composable
private fun QueuePane(player: MusicPlayerState, actions: AudioActions, upNext: List<SongEntity>) {
    val haptics = LocalHapticFeedback.current
    // The row being dragged ([dragFrom], -1 for none), how far, and the place it would drop at.
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableFloatStateOf(1f) }
    val lastIndex = upNext.lastIndex.coerceAtLeast(0)
    // Read when called: the gesture callbacks outlive the composition that made them.
    fun target(): Int = if (dragFrom < 0) -1 else (dragFrom + (dragOffset / rowHeight).roundToInt()).coerceIn(0, lastIndex)
    val dragTo = target()
    LaunchedEffect(dragTo) { if (dragTo >= 0 && dragFrom >= 0 && dragTo != dragFrom) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    fun drop() {
        val to = target()
        if (dragFrom >= 0 && to >= 0 && to != dragFrom) actions.moveUpNext(dragFrom + 1, to + 1)
        dragFrom = -1
        dragOffset = 0f
    }
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
            val dragged = index == dragFrom
            // The others make room for the dragged one at its new place.
            val shift by animateFloatAsState(
                when {
                    dragFrom < 0 || dragged -> 0f
                    dragFrom < dragTo && index in (dragFrom + 1)..dragTo -> -rowHeight
                    dragFrom > dragTo && index in dragTo until dragFrom -> rowHeight
                    else -> 0f
                },
                spring(dampingRatio = 0.85f, stiffness = 600f),
                label = "queueShift",
            )
            val lift by animateFloatAsState(if (dragged) 1.03f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "queueLift")
            val reorder = Modifier.pointerInput(index, upNext.size) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragFrom = index
                        dragOffset = 0f
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = { drop() },
                    onDragCancel = {
                        dragFrom = -1
                        dragOffset = 0f
                    },
                ) { change, amount ->
                    change.consume()
                    dragOffset += amount.y
                }
            }
            Row(
                Modifier
                    .onSizeChanged { rowHeight = it.height.toFloat().coerceAtLeast(1f) }
                    .zIndex(if (dragged) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (dragged) dragOffset else shift
                        scaleX = lift
                        scaleY = lift
                        shadowElevation = if (dragged) 12.dp.toPx() else 0f
                        shape = RoundedCornerShape(8.dp)
                    }
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (dragged) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                    .then(reorder)
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
                // Apple Music's handle: dragging it moves the song straight away.
                NovaIcon(
                    R.drawable.ic_drag_handle,
                    contentDescription = "Move ${song.title}",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(40.dp)
                        .pointerInput(index, upNext.size) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragFrom = index
                                    dragOffset = 0f
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragEnd = { drop() },
                                onDragCancel = {
                                    dragFrom = -1
                                    dragOffset = 0f
                                },
                            ) { change, amount ->
                                change.consume()
                                dragOffset += amount
                            }
                        }
                        .padding(10.dp),
                )
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
    // While held, the times grow and brighten so they can be read past the thumb, and the ends
    // of the song give a light tick, as Apple Music's scrubber does.
    val held = scrubbing != null
    val grow by animateFloatAsState(if (held) 1.2f else 1f, spring(dampingRatio = 0.7f, stiffness = 400f), label = "timeGrow")
    val timeAlpha by animateFloatAsState(if (held) 1f else 0.6f, tween(150), label = "timeAlpha")
    val haptics = LocalHapticFeedback.current
    val atEnd = scrubbing?.let { it <= 0f || it >= 1f } == true
    LaunchedEffect(atEnd) { if (atEnd) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        val shownMs = (fraction * duration).toLong()
        Text(
            formatTime(shownMs),
            fontSize = 12.sp,
            color = Color.White.copy(alpha = timeAlpha),
            modifier = Modifier
                .weight(1f)
                .graphicsLayer {
                    scaleX = grow
                    scaleY = grow
                    transformOrigin = TransformOrigin(0f, 0f)
                },
        )
        // A tap switches it between the time left and the song's whole length (Apple Music).
        var showLength by rememberSaveable { mutableStateOf(false) }
        Text(
            if (showLength) formatTime(player.durationMs.coerceAtLeast(0)) else "-" + formatTime((player.durationMs - shownMs).coerceAtLeast(0)),
            fontSize = 12.sp,
            color = Color.White.copy(alpha = timeAlpha),
            modifier = Modifier
                .graphicsLayer {
                    scaleX = grow
                    scaleY = grow
                    transformOrigin = TransformOrigin(1f, 0f)
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = if (showLength) "Show time left" else "Show song length",
                ) { showLength = !showLength },
        )
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
    IosIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size((size + 20).dp)) {
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
    IosIconButton(
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
            // Without a set size (a cell of a cover grid, a big cover) the note takes 40% of the box.
            modifier = if (size > 0) Modifier.size((size * 0.4f).dp) else Modifier.fillMaxSize(0.4f),
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
