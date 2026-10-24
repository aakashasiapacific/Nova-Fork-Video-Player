package com.aakash.novafork.ui.audio

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aakash.novafork.R
import com.aakash.novafork.data.db.MusicPlaylistRow
import com.aakash.novafork.data.db.SongEntity
import com.aakash.novafork.data.music.Album
import com.aakash.novafork.data.music.Artist
import com.aakash.novafork.data.music.MusicLibrary
import com.aakash.novafork.data.music.MusicLibraryData
import com.aakash.novafork.data.music.MusicScanState
import com.aakash.novafork.music.MusicPlayerState
import com.aakash.novafork.ui.common.CollapsedTitleBar
import com.aakash.novafork.ui.common.IosColors
import com.aakash.novafork.ui.common.IosGrabber
import com.aakash.novafork.ui.common.IosSheetShape
import com.aakash.novafork.ui.common.IosSpinner
import com.aakash.novafork.ui.common.LocalBottomBarSpace
import com.aakash.novafork.ui.common.LocalPagePull
import com.aakash.novafork.ui.common.NovaAlertDialog
import com.aakash.novafork.ui.common.NovaDropdownMenu
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.NovaMenuItem
import com.aakash.novafork.ui.common.NovaTextField
import com.aakash.novafork.ui.common.SwipeToDelete
import com.aakash.novafork.ui.theme.AudioTheme
import com.aakash.novafork.ui.theme.NovaColors

/**
 * The Audio tab, after Apple Music's Library (dark): a big "Library" title, Playlists / Artists /
 * Albums / Songs / Genres, then a Recently Added grid of covers; each opens its own page (album,
 * artist, genre, playlist pages with red Play and Shuffle). The mini player and Now Playing live
 * in the app shell (see [MiniPlayerBar], [NowPlayingScreen]).
 */
@Composable
fun AudioScreen(modifier: Modifier = Modifier, viewModel: MusicViewModel = viewModel()) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val scan by viewModel.scan.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val player by viewModel.playerState.collectAsStateWithLifecycle()
    val hasAccess by viewModel.hasAccess.collectAsStateWithLifecycle()
    val openPlaylist by viewModel.openPlaylist.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val pageRequest by viewModel.pageRequest.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAccessChecked() }
    val access = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.onAccessChecked() }
    AudioTheme {
        AudioContent(
            library = library,
            scan = scan,
            playlists = playlists,
            player = player,
            hasAccess = hasAccess,
            openPlaylist = openPlaylist,
            actions = viewModel,
            onRequestAccess = { access.launch(viewModel.permission) },
            modifier = modifier,
            favorites = favorites,
            pageRequest = pageRequest,
            onPageRequestHandled = viewModel::pageRequestHandled,
        )
    }
}

/** A page of the Audio tab: its route ("songs", "album:<key>" …) and its back-button label. */
private const val HOME = "home"
private const val PLAYLISTS = "playlists"
private const val FAVORITES = "favorites"
private const val ARTISTS = "artists"
private const val ALBUMS = "albums"
private const val SONGS = "songs"
private const val GENRES = "genres"
private const val ALBUM = "album:"
private const val ARTIST = "artist:"
private const val GENRE = "genre:"
private const val STACK_SEPARATOR = "\u0001"

/** The artist page's header row, which its picture's drift is measured by. */
private const val ARTIST_HERO_KEY = "hero"

/** The Audio tab's page for an album or an artist, for [MusicViewModel.requestPage]. */
internal fun albumPage(albumKey: String): String = ALBUM + albumKey
internal fun artistPage(artistKey: String): String = ARTIST + artistKey

@Composable
fun AudioContent(
    library: MusicLibraryData,
    scan: MusicScanState,
    playlists: List<MusicPlaylistRow>,
    player: MusicPlayerState,
    hasAccess: Boolean,
    openPlaylist: OpenPlaylist?,
    actions: AudioActions,
    onRequestAccess: () -> Unit,
    modifier: Modifier = Modifier,
    /** Starred song ids, most recently starred first. */
    favorites: List<Long> = emptyList(),
    /** A page asked for from outside the tab ([albumPage], [artistPage]); opened over Library. */
    pageRequest: String? = null,
    onPageRequestHandled: () -> Unit = {},
) {
    // The pages opened over Library, as one saveable string.
    var stackText by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(pageRequest) {
        if (pageRequest != null) {
            stackText = pageRequest
            onPageRequestHandled()
        }
    }
    val stack = if (stackText.isEmpty()) emptyList() else stackText.split(STACK_SEPARATOR)
    fun open(page: String) {
        stackText = (stack + page).joinToString(STACK_SEPARATOR)
    }
    fun back() {
        if (openPlaylist != null) actions.closePlaylist() else stackText = stack.dropLast(1).joinToString(STACK_SEPARATOR)
    }
    var addingSong by remember { mutableStateOf<SongEntity?>(null) }
    BackHandler(enabled = stack.isNotEmpty() || openPlaylist != null) { back() }

    val page = if (openPlaylist != null) "playlist:${openPlaylist.id}" else stack.lastOrNull() ?: HOME
    val backLabel = when {
        openPlaylist != null -> pageTitle(stack.lastOrNull() ?: HOME, library)
        stack.size >= 2 -> pageTitle(stack[stack.size - 2], library)
        else -> "Library"
    }
    val nowPlayingId = player.nowPlaying?.songId
    val ctx = PageContext(
        library = library,
        actions = actions,
        nowPlayingId = nowPlayingId,
        backLabel = backLabel,
        onBack = ::back,
        onOpen = ::open,
        onAdd = { addingSong = it },
        favorites = favorites.toSet(),
        isPlaying = player.isPlaying,
    )
    // On a page opened over Library a swipe right goes back, as on iOS (and is not taken for a
    // tab change).
    var backSwipe by remember { mutableFloatStateOf(0f) }
    val backPx = with(LocalDensity.current) { 90.dp.toPx() }
    val canGoBack = stack.isNotEmpty() || openPlaylist != null
    // The gesture outlives recompositions; this keeps it calling back() for the stack as it is now.
    val currentBack by rememberUpdatedState { back() }
    AnimatedContent(
        targetState = page,
        modifier = modifier
            .fillMaxSize()
            .pointerInput(canGoBack) {
                if (!canGoBack) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (backSwipe > backPx) currentBack()
                        backSwipe = 0f
                    },
                    onDragCancel = { backSwipe = 0f },
                ) { change, amount ->
                    change.consume()
                    backSwipe = (backSwipe + amount).coerceAtLeast(0f)
                }
            }
            .graphicsLayer { translationX = backSwipe * 0.6f },
        transitionSpec = {
            val forward = targetState != HOME && (initialState == HOME || targetState.length >= initialState.length)
            (slideInHorizontally(tween(260)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(tween(260)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(160)))
        },
        label = "audioPage",
    ) { shown ->
        when {
            shown == HOME -> LibraryHome(library, scan, hasAccess, onRequestAccess, ctx)
            shown.startsWith("playlist:") && openPlaylist != null -> PlaylistPage(openPlaylist, ctx)
            shown == PLAYLISTS -> PlaylistsPage(playlists, ctx)
            shown == FAVORITES -> {
                val byId = library.songs.associateBy { it.id }
                FavoritesPage(favorites.mapNotNull { byId[it] }, ctx)
            }
            shown == ARTISTS -> ArtistsPage(library.artists, ctx)
            shown == ALBUMS -> AlbumsPage("Albums", library.albums, ctx)
            shown == SONGS -> SongsPage("Songs", library.songs, ctx)
            shown == GENRES -> GenresPage(ctx)
            shown.startsWith(ALBUM) -> library.albums.firstOrNull { it.key == shown.removePrefix(ALBUM) }
                ?.let { AlbumPage(it, ctx) } ?: Missing(ctx)
            shown.startsWith(ARTIST) -> library.artists.firstOrNull { it.key == shown.removePrefix(ARTIST) }
                ?.let { ArtistPage(it, ctx) } ?: Missing(ctx)
            shown.startsWith(GENRE) -> library.genres.firstOrNull { it.name == shown.removePrefix(GENRE) }
                ?.let { SongsPage(it.name, it.songs, ctx) } ?: Missing(ctx)
            else -> Missing(ctx)
        }
    }

    addingSong?.let { song ->
        AddToPlaylistDialog(
            song = song,
            playlists = playlists,
            library = library,
            onPick = { id, name ->
                actions.addToPlaylist(song, id, name)
                addingSong = null
            },
            onDismiss = { addingSong = null },
        )
    }
}

/** What every page needs: the library, actions, navigation and the playing song. */
private class PageContext(
    val library: MusicLibraryData,
    val actions: AudioActions,
    val nowPlayingId: Long?,
    val backLabel: String,
    val onBack: () -> Unit,
    val onOpen: (String) -> Unit,
    val onAdd: (SongEntity) -> Unit,
    /** Starred songs: a small star in their rows, Favorite or Unfavorite in their menus. */
    val favorites: Set<Long> = emptySet(),
    /** The playing song's bars move only while it plays. */
    val isPlaying: Boolean = false,
)

private fun pageTitle(page: String, library: MusicLibraryData): String = when {
    page == HOME -> "Library"
    page == PLAYLISTS -> "Playlists"
    page == FAVORITES -> "Favorites"
    page == ARTISTS -> "Artists"
    page == ALBUMS -> "Albums"
    page == SONGS -> "Songs"
    page == GENRES -> "Genres"
    page.startsWith(ALBUM) -> library.albums.firstOrNull { it.key == page.removePrefix(ALBUM) }?.title ?: "Back"
    page.startsWith(ARTIST) -> library.artists.firstOrNull { it.key == page.removePrefix(ARTIST) }?.name ?: "Back"
    page.startsWith(GENRE) -> page.removePrefix(GENRE)
    else -> "Back"
}

// ---------------------------------------------------------------------------------------------
// Pages
// ---------------------------------------------------------------------------------------------

/**
 * Every page is one grid (2 columns of covers; rows span both), padded for the floating bars.
 * Once its big title has scrolled away, [title] shows small and centred in a bar at the top, as
 * iOS's large titles collapse.
 */
@Composable
private fun AudioPage(title: String?, content: LazyGridScope.() -> Unit) {
    val gridState = rememberLazyGridState()
    val collapsed by remember { derivedStateOf { gridState.firstVisibleItemIndex > 1 } }
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalPageGrid provides gridState) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = 24.dp + LocalBottomBarSpace.current),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                content = content,
            )
        }
        if (title != null) CollapsedTitleBar(visible = collapsed, title = title)
    }
}

/** The grid of the page being shown, for headers that move with its scrolling. */
private val LocalPageGrid = staticCompositionLocalOf<LazyGridState?> { null }

private fun LazyGridScope.row(key: Any? = null, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun LibraryHome(
    library: MusicLibraryData,
    scan: MusicScanState,
    hasAccess: Boolean,
    onRequestAccess: () -> Unit,
    ctx: PageContext,
) {
    AudioPage("Library") {
        row("title") {
            Row(Modifier.padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Library", fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                MoreButton("Library options") { close ->
                    NovaMenuItem(
                        text = { Text("Rescan Library") },
                        leadingIcon = { NovaIcon(R.drawable.ic_refresh, contentDescription = null) },
                        enabled = !scan.scanning,
                        onClick = {
                            close()
                            ctx.actions.rescan()
                        },
                    )
                }
            }
        }
        if (scan.scanning) {
            row("scanning") {
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IosSpinner(Modifier.size(16.dp))
                    Text("Updating library…", fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        // Without access the card below says it all; the scan's own error would only repeat it.
        if (hasAccess) scan.error?.let { error -> row("error") { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
        if (!hasAccess) row("access") { AccessCard(onRequestAccess) }
        // Apple Music's Library, cut down to what the owner uses: playlists, favorites, albums.
        row("playlists") { CategoryRow(R.drawable.ic_playlist_play, "Playlists") { ctx.onOpen(PLAYLISTS) } }
        row("favorites") { CategoryRow(R.drawable.ic_star, "Favorites", divider = false) { ctx.onOpen(FAVORITES) } }
        if (library.recentlyAdded.isNotEmpty()) {
            row("albumsTitle") { SectionTitle("Albums") }
            // Newest first.
            items(library.recentlyAdded, key = { "album:" + it.key }) { album ->
                AlbumTile(album) { ctx.onOpen(ALBUM + album.key) }
            }
        } else if (!scan.scanning && hasAccess) {
            row("empty") { Hint("No music found. Songs on this phone and in folders added under Preferences › Sources show up here.") }
        }
    }
}

@Composable
private fun PlaylistsPage(playlists: List<MusicPlaylistRow>, ctx: PageContext) {
    AudioPage("Playlists") {
        header("Playlists", ctx)
        // Apple Music's first row: a red "+" tile and New Playlist….
        row("new") {
            var naming by rememberSaveable { mutableStateOf(false) }
            ListRow(
                title = "New Playlist…",
                titleColor = NovaColors.AppleMusicRed,
                art = {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1C1C1E)),
                        contentAlignment = Alignment.Center,
                    ) {
                        NovaIcon(R.drawable.ic_add, contentDescription = null, tint = NovaColors.AppleMusicRed, modifier = Modifier.size(24.dp))
                    }
                },
                chevron = false,
                onClick = { naming = true },
            )
            if (naming) {
                NewPlaylistDialog(
                    onCreate = { name ->
                        naming = false
                        ctx.actions.createPlaylist(name)
                    },
                    onDismiss = { naming = false },
                )
            }
        }
        if (playlists.isEmpty()) row("empty") { Hint("No playlists yet. Make one here, or use “Add to a Playlist…” in a song's menu.") }
        playlists.forEach { playlist ->
            row("p:${playlist.id}") {
                ListRow(
                    title = playlist.name,
                    subtitle = if (playlist.songCount == 1) "1 song" else "${playlist.songCount} songs",
                    art = {
                        // The same 2×2 grid of album covers as the playlist's own page, small.
                        val ids = playlist.coverSongIds?.split(',')?.mapNotNull { it.toLongOrNull() }
                            ?: listOfNotNull(playlist.coverSongId)
                        val songs = ids.mapNotNull { id -> ctx.library.songs.firstOrNull { it.id == id } }
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(6.dp))) { PlaylistArtwork(songs, corner = 6) }
                    },
                    onClick = { ctx.actions.showPlaylist(playlist) },
                )
            }
        }
    }
}

@Composable
private fun ArtistsPage(artists: List<Artist>, ctx: PageContext) {
    AudioPage("Artists") {
        header("Artists", ctx)
        artists.forEach { artist ->
            row("a:${artist.key}") {
                ListRow(
                    title = artist.name,
                    art = { Cover(artist.albums.firstOrNull()?.artUri, size = 48, round = true) },
                    onClick = { ctx.onOpen(ARTIST + artist.key) },
                )
            }
        }
    }
}

@Composable
private fun AlbumsPage(title: String, albums: List<Album>, ctx: PageContext) {
    AudioPage(title) {
        header(title, ctx)
        items(albums, key = { it.key }) { album -> AlbumTile(album) { ctx.onOpen(ALBUM + album.key) } }
    }
}

@Composable
private fun GenresPage(ctx: PageContext) {
    AudioPage("Genres") {
        header("Genres", ctx)
        ctx.library.genres.forEach { genre ->
            row("g:${genre.name}") {
                ListRow(
                    title = genre.name,
                    subtitle = if (genre.songs.size == 1) "1 song" else "${genre.songs.size} songs",
                    onClick = { ctx.onOpen(GENRE + genre.name) },
                )
            }
        }
    }
}

/** Starred songs, most recently starred first; the red star takes one out. */
@Composable
private fun FavoritesPage(songs: List<SongEntity>, ctx: PageContext) {
    AudioPage("Favorites") {
        header("Favorites", ctx)
        if (songs.isEmpty()) row("empty") { Hint("No favorites yet. Tap the star in Now Playing to add a song.") }
        if (songs.isNotEmpty()) row("play") { PlayShuffle(songs, ctx.actions) }
        songs.forEachIndexed { index, song ->
            row("f:${song.id}") {
                SongRow(song = song, ctx = ctx, onClick = { ctx.actions.play(songs, index, false) })
            }
        }
    }
}

/** Songs, a genre: big title, Play and Shuffle, then the songs with their covers. */
@Composable
private fun SongsPage(title: String, songs: List<SongEntity>, ctx: PageContext) {
    AudioPage(title) {
        header(title, ctx)
        if (songs.isNotEmpty()) row("play") { PlayShuffle(songs, ctx.actions) }
        songRows(songs, ctx)
    }
}

/** Apple Music's album page: big cover, title, red artist, details, Play/Shuffle and the tracks. */
@Composable
private fun AlbumPage(album: Album, ctx: PageContext) {
    AudioPage(album.title) {
        row("back") { BackRow(ctx) { close -> QueueItems(album.songs, ctx, close) } }
        row("cover") {
            // A soft glow of the cover's own colours spreads behind it (owner), as the Home hero's.
            val glow = rememberCoverTint(album.artUri)
            Column(
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val center = Offset(size.width / 2f, size.width * 0.4f)
                        val radius = size.width * 0.85f
                        drawCircle(
                            Brush.radialGradient(listOf(glow.copy(alpha = 0.55f * glow.alpha), Color.Transparent), center = center, radius = radius),
                            radius = radius,
                            center = center,
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(0.72f)
                        .aspectRatio(1f)
                        .shadow(18.dp, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp)),
                ) {
                    Cover(album.artUri, size = 0, corner = 10, modifier = Modifier.fillMaxSize())
                }
                Text(
                    album.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Text(
                    album.artist,
                    fontSize = 20.sp,
                    color = NovaColors.AppleMusicRed,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clickable { ctx.onOpen(ARTIST + album.artistKey) },
                )
                val genre = album.songs.firstNotNullOfOrNull { it.genre }
                // Apple Music's caps line: "ROCK · 1975".
                val details = listOfNotNull(genre, album.year?.toString()).joinToString(" · ").uppercase()
                if (details.isNotEmpty()) {
                    Text(
                        details,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp,
                        color = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        row("play") { PlayShuffle(album.songs, ctx.actions) }
        album.songs.forEachIndexed { index, song ->
            row("t:${song.id}") {
                TrackRow(song = song, ctx = ctx, onClick = { ctx.actions.play(album.songs, index, false) })
            }
        }
        row("footer") { Footer(album.songs) }
        // Apple Music ends an album with the artist's other albums.
        val more = ctx.library.artists.firstOrNull { it.key == album.artistKey }?.albums?.filter { it.key != album.key }.orEmpty()
        if (more.isNotEmpty()) {
            row("moreTitle") { SectionTitle("More by ${album.artist}") }
            row("more") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(more, key = { it.key }) { other ->
                        AlbumTile(other, Modifier.width(150.dp)) { ctx.onOpen(ALBUM + other.key) }
                    }
                }
            }
        }
    }
}

/**
 * Apple Music's artist header: a big picture (their newest cover, as there are no artist photos)
 * fading into black, the name over it, and the round red Play button. As the page scrolls the
 * picture drifts at half speed inside its frame, and pulling the page down past its top makes the
 * whole header grow into the gap.
 */
@Composable
private fun ArtistHero(artist: Artist, ctx: PageContext) {
    val grid = LocalPageGrid.current
    val pull = LocalPagePull.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp)
            .aspectRatio(1.15f)
            .graphicsLayer {
                val grow = 1f + (pull() / size.height).coerceIn(0f, 1f) * 0.5f
                scaleX = grow
                scaleY = grow
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .clip(RoundedCornerShape(14.dp)),
    ) {
        Cover(
            artist.albums.firstOrNull()?.artUri,
            size = 0,
            corner = 0,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Once the header's top goes up under the top edge, the picture lags half
                    // of the way (so its own top edge is never in sight).
                    val hero = grid?.layoutInfo?.visibleItemsInfo?.firstOrNull { it.key == ARTIST_HERO_KEY }
                    val under = if (hero == null) 0f else (-hero.offset.y).toFloat().coerceAtLeast(0f)
                    translationY = under * 0.5f
                    // A little larger, so the drift never shows an edge.
                    scaleX = 1.08f
                    scaleY = 1.08f
                },
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))),
        )
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                artist.name,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(NovaColors.AppleMusicRed)
                    .clickable(onClickLabel = "Play ${artist.name}", role = Role.Button) { ctx.actions.play(artist.songs, 0, false) },
                contentAlignment = Alignment.Center,
            ) {
                NovaIcon(R.drawable.ic_play_arrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/** An artist: their picture with Play, their albums in a row, then their top songs. */
@Composable
private fun ArtistPage(artist: Artist, ctx: PageContext) {
    AudioPage(artist.name) {
        row("back") { BackRow(ctx) { close -> QueueItems(artist.songs, ctx, close) } }
        row(ARTIST_HERO_KEY) { ArtistHero(artist, ctx) }
        if (artist.albums.isNotEmpty()) {
            row("albumsTitle") { SectionTitle("Albums") }
            row("albums") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(artist.albums, key = { it.key }) { album ->
                        AlbumTile(album, Modifier.width(150.dp)) { ctx.onOpen(ALBUM + album.key) }
                    }
                }
            }
        }
        row("songsTitle") { SectionTitle("Top Songs") }
        songRows(artist.songs.take(TOP_SONGS), ctx)
    }
}

@Composable
private fun PlaylistPage(playlist: OpenPlaylist, ctx: PageContext) {
    AudioPage(playlist.name) {
        row("back") {
            BackRow(ctx) { close ->
                QueueItems(playlist.songs, ctx, close)
                NovaMenuItem(
                    text = { Text("Delete Playlist") },
                    destructive = true,
                    leadingIcon = { NovaIcon(R.drawable.ic_delete, contentDescription = null) },
                    onClick = {
                        close()
                        ctx.actions.deletePlaylist(playlist.id)
                    },
                )
            }
        }
        row("cover") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(0.72f)
                        .aspectRatio(1f)
                        .shadow(18.dp, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp)),
                ) {
                    PlaylistArtwork(playlist.songs)
                }
                Text(playlist.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
            }
        }
        if (playlist.songs.isEmpty()) {
            row("empty") { Hint("This playlist is empty. Use “Add to a Playlist…” in a song's menu or in Now Playing.") }
        } else {
            row("play") { PlayShuffle(playlist.songs, ctx.actions) }
        }
        playlist.songs.forEachIndexed { index, song ->
            row("s:${song.id}") {
                // As in Apple Music, a swipe left takes it out of the playlist too.
                SwipeToDelete(onDelete = { ctx.actions.removeFromPlaylist(song) }, label = "Remove") {
                    SongRow(
                        song = song,
                        ctx = ctx,
                        onClick = { ctx.actions.play(playlist.songs, index, false) },
                        onRemove = { ctx.actions.removeFromPlaylist(song) },
                    )
                }
            }
        }
        // As on an album, Apple Music ends a playlist with its length.
        if (playlist.songs.isNotEmpty()) row("footer") { Footer(playlist.songs) }
    }
}

/**
 * A playlist's artwork as Apple Music draws it: the covers of its first four albums in a 2×2
 * grid, or the first song's cover while it holds fewer than four albums.
 */
@Composable
private fun PlaylistArtwork(songs: List<SongEntity>, corner: Int = 10) {
    val albums = songs.distinctBy { it.albumKey }
    if (albums.size < 4) {
        Cover(songs.firstOrNull()?.let(MusicLibrary::artUri), size = 0, corner = corner, modifier = Modifier.fillMaxSize())
        return
    }
    Column(Modifier.fillMaxSize()) {
        albums.take(4).chunked(2).forEach { pair ->
            Row(Modifier.weight(1f)) {
                pair.forEach { song ->
                    Cover(MusicLibrary.artUri(song), size = 0, corner = 0, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun Missing(ctx: PageContext) {
    AudioPage(null) {
        row("back") { BackRow(ctx) }
        row("hint") { Hint("This is no longer in your library.") }
    }
}

// ---------------------------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------------------------

/** A page's back link ("‹ Library", in red) and its big title. */
private fun LazyGridScope.header(title: String, ctx: PageContext) {
    row("back") { BackRow(ctx) }
    row("title") {
        Text(
            title,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
    }
}

/** "‹ Library" in red; with [menu], Apple Music's red "…" circle on the right. */
@Composable
private fun BackRow(ctx: PageContext, menu: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = "Back", onClick = ctx.onBack)
                .padding(vertical = 8.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NovaIcon(R.drawable.ic_arrow_back, contentDescription = null, tint = NovaColors.AppleMusicRed, modifier = Modifier.size(20.dp))
            Text(
                ctx.backLabel,
                color = NovaColors.AppleMusicRed,
                fontSize = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        if (menu != null) {
            Spacer(Modifier.weight(1f))
            MoreButton("More", content = menu)
        }
    }
}

/** Apple Music's "…": a red ellipsis on a grey circle that opens a menu. */
@Composable
private fun MoreButton(label: String, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color(0xFF1C1C1E))
                .clickable(onClickLabel = label, role = Role.Button) { open = true },
            contentAlignment = Alignment.Center,
        ) {
            NovaIcon(R.drawable.ic_more_vert, contentDescription = label, tint = NovaColors.AppleMusicRed, modifier = Modifier.size(18.dp))
        }
        NovaDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            content { open = false }
        }
    }
}

/** Play Next and Play Last for a list of songs (an album, a playlist). */
@Composable
private fun QueueItems(songs: List<SongEntity>, ctx: PageContext, close: () -> Unit) {
    NovaMenuItem(
        text = { Text("Play Next") },
        leadingIcon = { NovaIcon(R.drawable.ic_play_next, contentDescription = null) },
        enabled = songs.isNotEmpty(),
        onClick = {
            close()
            ctx.actions.enqueue(songs, next = true)
        },
    )
    NovaMenuItem(
        text = { Text("Play Last") },
        leadingIcon = { NovaIcon(R.drawable.ic_play_last, contentDescription = null) },
        enabled = songs.isNotEmpty(),
        onClick = {
            close()
            ctx.actions.enqueue(songs, next = false)
        },
    )
}

@Composable
private fun CategoryRow(@DrawableRes icon: Int, label: String, divider: Boolean = true, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            NovaIcon(icon, contentDescription = null, tint = NovaColors.AppleMusicRed, modifier = Modifier.size(26.dp))
            Text(label, fontSize = 21.sp, modifier = Modifier.padding(start = 16.dp).weight(1f))
            NovaIcon(R.drawable.ic_chevron_right, contentDescription = null, tint = Color.White.copy(alpha = 0.3f))
        }
        if (divider) HorizontalDivider(Modifier.padding(start = 42.dp), thickness = 0.5.dp, color = Color.White.copy(alpha = 0.15f))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 28.dp, bottom = 12.dp))
}

/** Square cover, title and artist below: Recently Added, Albums, an artist's albums. */
@Composable
private fun AlbumTile(album: Album, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .padding(bottom = 18.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            Cover(album.artUri, size = 0, modifier = Modifier.fillMaxSize())
        }
        Text(
            album.title,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(album.artist, fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Apple Music's Play and Shuffle: two grey pills with red icon and label, side by side. */
@Composable
private fun PlayShuffle(songs: List<SongEntity>, actions: AudioActions) {
    Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        GreyPill(R.drawable.ic_play_arrow, "Play", Modifier.weight(1f)) { actions.play(songs, 0, false) }
        GreyPill(R.drawable.ic_shuffle, "Shuffle", Modifier.weight(1f)) { actions.play(songs, 0, true) }
    }
}

@Composable
private fun GreyPill(@DrawableRes icon: Int, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1C1C1E))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NovaIcon(icon, contentDescription = null, tint = NovaColors.AppleMusicRed, modifier = Modifier.size(22.dp))
        Text(label, color = NovaColors.AppleMusicRed, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
    }
}

private fun LazyGridScope.songRows(songs: List<SongEntity>, ctx: PageContext) {
    itemsIndexed(songs, key = { _, song -> "song:${song.id}" }, span = { _, _ -> GridItemSpan(maxLineSpan) }) { index, song ->
        SongRow(song = song, ctx = ctx, onClick = { ctx.actions.play(songs, index, false) })
    }
}

/**
 * A song with its cover, as in Apple Music's lists: the playing one in red with moving bars over
 * its cover, a small star when it is a favorite, and "…" (or a long press) for its menu.
 * [onRemove] adds Remove from Playlist to the menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongRow(song: SongEntity, ctx: PageContext, onClick: () -> Unit, onRemove: (() -> Unit)? = null) {
    val playing = song.id == ctx.nowPlayingId
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    // iOS lifts the row its context menu is for.
    val lift by animateFloatAsState(if (menuOpen) 1.03f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "rowLift")
    Box {
        Column(
            Modifier.graphicsLayer {
                scaleX = lift
                scaleY = lift
            }.combinedClickable(
                onClick = onClick,
                onLongClickLabel = "More",
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuOpen = true
                },
            ),
        ) {
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    Cover(MusicLibrary.artUri(song), size = 48, corner = 6)
                    if (playing) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.45f)),
                        )
                        PlayingBars(ctx.isPlaying, color = Color.White)
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text(
                        song.title,
                        fontSize = 16.sp,
                        color = if (playing) NovaColors.AppleMusicRed else Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        song.artist ?: song.albumArtist ?: "Unknown artist",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.55f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FavoriteStar(song.id in ctx.favorites)
                RowMoreButton { menuOpen = true }
            }
            HorizontalDivider(Modifier.padding(start = 60.dp), thickness = 0.5.dp, color = Color.White.copy(alpha = 0.12f))
        }
        SongMenu(song, ctx, expanded = menuOpen, onDismiss = { menuOpen = false }, showAlbum = true, onRemove = onRemove)
    }
}

/** An album's track: its number (moving bars while playing), title, star, "…". */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(song: SongEntity, ctx: PageContext, onClick: () -> Unit) {
    val playing = song.id == ctx.nowPlayingId
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    // iOS lifts the row its context menu is for.
    val lift by animateFloatAsState(if (menuOpen) 1.03f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "rowLift")
    Box {
        Column(
            Modifier.graphicsLayer {
                scaleX = lift
                scaleY = lift
            }.combinedClickable(
                onClick = onClick,
                onLongClickLabel = "More",
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuOpen = true
                },
            ),
        ) {
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(32.dp)) {
                    if (playing) {
                        PlayingBars(ctx.isPlaying, color = NovaColors.AppleMusicRed)
                    } else {
                        Text(song.trackNumber?.toString() ?: "", fontSize = 16.sp, color = Color.White.copy(alpha = 0.55f))
                    }
                }
                Text(
                    song.title,
                    fontSize = 16.sp,
                    color = if (playing) NovaColors.AppleMusicRed else Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                FavoriteStar(song.id in ctx.favorites)
                RowMoreButton { menuOpen = true }
            }
            HorizontalDivider(Modifier.padding(start = 32.dp), thickness = 0.5.dp, color = Color.White.copy(alpha = 0.12f))
        }
        SongMenu(song, ctx, expanded = menuOpen, onDismiss = { menuOpen = false }, showAlbum = false)
    }
}

/** Apple Music's small star beside a favorite song. */
@Composable
private fun FavoriteStar(favorite: Boolean) {
    if (favorite) {
        NovaIcon(
            R.drawable.ic_star,
            contentDescription = "Favorite",
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.padding(start = 8.dp).size(13.dp),
        )
    }
}

@Composable
private fun RowMoreButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        NovaIcon(R.drawable.ic_more_vert, contentDescription = "More", tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/** A song's menu, as Apple Music's: queue it, add it to a playlist, star it, go to its album or artist. */
@Composable
private fun SongMenu(
    song: SongEntity,
    ctx: PageContext,
    expanded: Boolean,
    onDismiss: () -> Unit,
    showAlbum: Boolean,
    onRemove: (() -> Unit)? = null,
) {
    val favorite = song.id in ctx.favorites
    NovaDropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        QueueItems(listOf(song), ctx, onDismiss)
        NovaMenuItem(
            text = { Text("Add to a Playlist…") },
            leadingIcon = { NovaIcon(R.drawable.ic_playlist_add, contentDescription = null) },
            onClick = {
                onDismiss()
                ctx.onAdd(song)
            },
        )
        NovaMenuItem(
            text = { Text(if (favorite) "Undo Favorite" else "Favorite") },
            leadingIcon = { NovaIcon(if (favorite) R.drawable.ic_star_slash else R.drawable.ic_star_outline, contentDescription = null) },
            onClick = {
                onDismiss()
                ctx.actions.toggleFavorite(song.id)
            },
        )
        if (showAlbum) {
            NovaMenuItem(
                text = { Text("Go to Album") },
                leadingIcon = { NovaIcon(R.drawable.ic_album, contentDescription = null) },
                onClick = {
                    onDismiss()
                    ctx.onOpen(ALBUM + song.albumKey)
                },
            )
        }
        NovaMenuItem(
            text = { Text("Go to Artist") },
            leadingIcon = { NovaIcon(R.drawable.ic_artist, contentDescription = null) },
            onClick = {
                onDismiss()
                ctx.onOpen(ARTIST + song.artistKey)
            },
        )
        if (onRemove != null) {
            NovaMenuItem(
                text = { Text("Remove from Playlist") },
                destructive = true,
                leadingIcon = { NovaIcon(R.drawable.ic_delete, contentDescription = null) },
                onClick = {
                    onDismiss()
                    onRemove()
                },
            )
        }
    }
}

/**
 * Apple Music's "now playing" bars: four thin bars that bounce while the song plays and rest
 * low while it is paused.
 */
@Composable
internal fun PlayingBars(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "bars")
    val phases = listOf(0, 180, 90, 270)
    Row(
        modifier.height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        phases.forEachIndexed { index, phase ->
            val bounce by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(durationMillis = 420 + index * 70, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(phase),
                ),
                label = "bar$index",
            )
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(if (playing) bounce else 0.25f)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color),
            )
        }
    }
}

/** "12 songs, 48 minutes" under an album. */
@Composable
private fun Footer(songs: List<SongEntity>) {
    val minutes = (songs.sumOf { it.durationMs ?: 0L } / 60_000L).toInt()
    val count = if (songs.size == 1) "1 song" else "${songs.size} songs"
    Text(
        if (minutes > 0) "$count, $minutes minutes" else count,
        fontSize = 13.sp,
        color = Color.White.copy(alpha = 0.55f),
        modifier = Modifier.padding(top = 14.dp),
    )
}

@Composable
private fun ListRow(
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    art: (@Composable () -> Unit)? = null,
    titleColor: Color = Color.White,
    chevron: Boolean = true,
) {
    Column(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (art != null) {
                art()
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f))
            }
            if (chevron) NovaIcon(R.drawable.ic_chevron_right, contentDescription = null, tint = Color.White.copy(alpha = 0.3f))
        }
        HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.12f))
    }
}

@Composable
private fun AccessCard(onRequestAccess: () -> Unit) {
    Column(
        Modifier
            .padding(vertical = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1C1C1E))
            .padding(16.dp),
    ) {
        Text("Allow access to music", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Nova Fork needs permission to see the songs on this phone.",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Button(onClick = onRequestAccess) { Text("Allow") }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 15.sp, color = Color.White.copy(alpha = 0.55f), modifier = Modifier.padding(vertical = 24.dp))
}

/** iOS's naming alert for a new playlist. */
@Composable
private fun NewPlaylistDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    NovaAlertDialog(
        form = true,
        onDismissRequest = onDismiss,
        title = { Text("New Playlist") },
        text = {
            NovaTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Playlist name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onCreate(name.trim()) }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Apple Music's Add to Playlist sheet: New Playlist… first (it opens a name field in place),
 * then every playlist with its cover and size.
 */
@Composable
internal fun AddToPlaylistDialog(
    song: SongEntity,
    playlists: List<MusicPlaylistRow>,
    library: MusicLibraryData,
    onPick: (playlistId: Long?, newName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var naming by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val songsById = remember(library) { library.songs.associateBy { it.id } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = IosColors.GroupedCard,
        shape = IosSheetShape,
        dragHandle = { IosGrabber() },
    ) {
        Column(Modifier.padding(horizontal = Gutter).padding(bottom = 24.dp)) {
            Text(
                "Add to Playlist",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                song.title,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
            if (naming) {
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NovaTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("Playlist name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onPick(null, name.trim()) }),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onPick(null, name.trim()) }, enabled = name.isNotBlank()) {
                        Text("Create", color = if (name.isNotBlank()) NovaColors.AppleMusicRed else Color.White.copy(alpha = 0.3f))
                    }
                }
            } else {
                ListRow(
                    title = "New Playlist…",
                    titleColor = NovaColors.AppleMusicRed,
                    art = {
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF2C2C2E)),
                            contentAlignment = Alignment.Center,
                        ) {
                            NovaIcon(R.drawable.ic_add, contentDescription = null, tint = NovaColors.AppleMusicRed, modifier = Modifier.size(22.dp))
                        }
                    },
                    chevron = false,
                    onClick = { naming = true },
                )
            }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                playlists.forEach { row ->
                    ListRow(
                        title = row.name,
                        subtitle = if (row.songCount == 1) "1 song" else "${row.songCount} songs",
                        art = { Cover(row.coverSongId?.let(songsById::get)?.let(MusicLibrary::artUri), size = 48, corner = 6) },
                        chevron = false,
                        onClick = { onPick(row.id, row.name) },
                    )
                }
            }
        }
    }
}

private val Gutter = 20.dp
private const val TOP_SONGS = 10
