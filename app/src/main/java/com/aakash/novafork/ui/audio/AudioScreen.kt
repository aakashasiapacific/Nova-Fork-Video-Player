package com.aakash.novafork.ui.audio

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.aakash.novafork.R
import com.aakash.novafork.data.db.MusicPlaylistRow
import com.aakash.novafork.data.db.SongEntity
import com.aakash.novafork.data.music.Album
import com.aakash.novafork.data.music.MusicLibrary
import com.aakash.novafork.data.music.MusicLibraryData
import com.aakash.novafork.data.music.MusicScanState
import com.aakash.novafork.music.MusicPlayerState
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.model.RepeatMode

/**
 * A plain, temporary Audio tab for testing the music engine on a phone: the library by songs,
 * albums, artists, genres, playlists and recently added, and a mini player with lyrics. The
 * Apple Music-style screens replace it once the design is settled.
 */
@Composable
fun AudioScreen(modifier: Modifier = Modifier, viewModel: MusicViewModel = viewModel()) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val scan by viewModel.scan.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val player by viewModel.playerState.collectAsStateWithLifecycle()
    val hasAccess by viewModel.hasAccess.collectAsStateWithLifecycle()
    val openPlaylist by viewModel.openPlaylist.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAccessChecked() }
    val access = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.onAccessChecked() }
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
    )
}

private enum class AudioTab(val label: String) {
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    GENRES("Genres"),
    PLAYLISTS("Playlists"),
    RECENT("Recently Added"),
}

/** What the tab shows instead of a list: one album, artist or genre ("album:<key>" …). */
private const val ALBUM = "album:"
private const val ARTIST = "artist:"
private const val GENRE = "genre:"

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
) {
    var tabIndex by rememberSaveable { mutableStateOf(0) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var addingSong by remember { mutableStateOf<SongEntity?>(null) }
    val tab = AudioTab.entries[tabIndex]
    BackHandler(enabled = detail != null || openPlaylist != null) {
        if (openPlaylist != null) actions.closePlaylist() else detail = null
    }
    val onAdd: (SongEntity) -> Unit = { addingSong = it }

    Column(modifier.statusBarsPadding()) {
        Header(library = library, scan = scan, onRescan = actions::rescan)
        if (!hasAccess) AccessCard(onRequestAccess)
        ScrollableTabRow(selectedTabIndex = tabIndex, edgePadding = 16.dp) {
            AudioTab.entries.forEachIndexed { index, entry ->
                Tab(
                    selected = index == tabIndex,
                    onClick = {
                        tabIndex = index
                        detail = null
                        actions.closePlaylist()
                    },
                    text = { Text(entry.label) },
                )
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            val shown = detail
            when {
                openPlaylist != null -> playlistDetail(openPlaylist, actions, onAdd)
                shown != null && shown.startsWith(ALBUM) ->
                    library.albums.firstOrNull { it.key == shown.removePrefix(ALBUM) }?.let { albumDetail(it, actions, onAdd) }
                shown != null && shown.startsWith(ARTIST) ->
                    library.artists.firstOrNull { it.key == shown.removePrefix(ARTIST) }?.let { artist ->
                        item { DetailHeader(title = artist.name, subtitle = "${artist.albums.size} albums · ${artist.songs.size} songs") }
                        item { PlayButtons(onPlay = { actions.play(artist.songs, 0, false) }, onShuffle = { actions.play(artist.songs, 0, true) }) }
                        item { SectionTitle("Albums") }
                        items(artist.albums, key = { "a:" + it.key }) { album -> AlbumRow(album) { detail = ALBUM + album.key } }
                        item { SectionTitle("Top songs") }
                        songItems(artist.songs.take(TOP_SONGS), actions, onAdd)
                    }
                shown != null && shown.startsWith(GENRE) ->
                    library.genres.firstOrNull { it.name == shown.removePrefix(GENRE) }?.let { genre ->
                        item { DetailHeader(title = genre.name, subtitle = "${genre.songs.size} songs") }
                        item { PlayButtons(onPlay = { actions.play(genre.songs, 0, false) }, onShuffle = { actions.play(genre.songs, 0, true) }) }
                        songItems(genre.songs, actions, onAdd)
                    }
                else -> when (tab) {
                    AudioTab.SONGS -> {
                        if (library.songs.isNotEmpty()) {
                            item {
                                PlayButtons(
                                    onPlay = { actions.play(library.songs, 0, false) },
                                    onShuffle = { actions.play(library.songs, 0, true) },
                                )
                            }
                        }
                        songItems(library.songs, actions, onAdd)
                    }
                    AudioTab.ALBUMS -> items(library.albums, key = { it.key }) { album -> AlbumRow(album) { detail = ALBUM + album.key } }
                    AudioTab.ARTISTS -> items(library.artists, key = { it.key }) { artist ->
                        ListItem(
                            headlineContent = { Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text("${artist.albums.size} albums · ${artist.songs.size} songs") },
                            leadingContent = { Art(artist.albums.firstOrNull()?.artUri, round = true) },
                            modifier = Modifier.clickable { detail = ARTIST + artist.key },
                        )
                    }
                    AudioTab.GENRES -> items(library.genres, key = { it.name }) { genre ->
                        ListItem(
                            headlineContent = { Text(genre.name) },
                            supportingContent = { Text("${genre.songs.size} songs") },
                            modifier = Modifier.clickable { detail = GENRE + genre.name },
                        )
                    }
                    AudioTab.PLAYLISTS -> {
                        if (playlists.isEmpty()) item { Hint("No playlists yet. Use + on a song to make one.") }
                        items(playlists, key = { it.id }) { row ->
                            ListItem(
                                headlineContent = { Text(row.name) },
                                supportingContent = { Text("${row.songCount} songs") },
                                leadingContent = { NovaIcon(R.drawable.ic_playlist_play, contentDescription = null) },
                                modifier = Modifier.clickable { actions.showPlaylist(row) },
                            )
                        }
                    }
                    AudioTab.RECENT -> items(library.recentlyAdded, key = { it.key }) { album ->
                        AlbumRow(album) { detail = ALBUM + album.key }
                    }
                }
            }
            if (library.songs.isEmpty() && !scan.scanning && hasAccess) {
                item { Hint("No music found. Songs on this phone and in folders added under Browse show up here.") }
            }
        }
        if (player.active) MiniPlayer(player, actions)
    }

    addingSong?.let { song ->
        AddToPlaylistDialog(
            song = song,
            playlists = playlists,
            onPick = { id, name ->
                actions.addToPlaylist(song, id, name)
                addingSong = null
            },
            onDismiss = { addingSong = null },
        )
    }
}

@Composable
private fun Header(library: MusicLibraryData, scan: MusicScanState, onRescan: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Audio", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Test screen · ${library.songs.size} songs · ${library.albums.size} albums · ${library.artists.size} artists",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRescan, enabled = !scan.scanning) {
            NovaIcon(R.drawable.ic_refresh, contentDescription = "Rescan music")
        }
    }
    if (scan.scanning) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
    scan.error?.let {
        Text(
            it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun AccessCard(onRequestAccess: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Allow access to music", style = MaterialTheme.typography.titleMedium)
            Text(
                "Nova Fork needs permission to see the songs on this phone.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Button(onClick = onRequestAccess) { Text("Allow") }
        }
    }
}

private fun LazyListScope.songItems(songs: List<SongEntity>, actions: AudioActions, onAdd: (SongEntity) -> Unit, numbered: Boolean = false) {
    itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
        SongRow(song = song, numbered = numbered, onClick = { actions.play(songs, index, false) }, trailing = {
            IconButton(onClick = { onAdd(song) }) { NovaIcon(R.drawable.ic_playlist_add, contentDescription = "Add to a playlist") }
        })
    }
}

private fun LazyListScope.albumDetail(album: Album, actions: AudioActions, onAdd: (SongEntity) -> Unit) {
    item {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Art(album.artUri, size = 120)
            Column(Modifier.padding(start = 16.dp)) {
                Text(album.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(album.artist, color = MaterialTheme.colorScheme.primary)
                Text(
                    listOfNotNull(album.year?.toString(), "${album.songs.size} songs").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    item { PlayButtons(onPlay = { actions.play(album.songs, 0, false) }, onShuffle = { actions.play(album.songs, 0, true) }) }
    songItems(album.songs, actions, onAdd, numbered = true)
}

private fun LazyListScope.playlistDetail(playlist: OpenPlaylist, actions: AudioActions, onAdd: (SongEntity) -> Unit) {
    item { DetailHeader(title = playlist.name, subtitle = "${playlist.songs.size} songs") }
    item {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { actions.play(playlist.songs, 0, false) }, enabled = playlist.songs.isNotEmpty()) { Text("Play") }
            OutlinedButton(onClick = { actions.play(playlist.songs, 0, true) }, enabled = playlist.songs.isNotEmpty()) { Text("Shuffle") }
            TextButton(onClick = { actions.deletePlaylist(playlist.id) }) { Text("Delete playlist") }
        }
    }
    itemsIndexed(playlist.songs, key = { _, song -> song.id }) { index, song ->
        SongRow(song = song, numbered = false, onClick = { actions.play(playlist.songs, index, false) }, trailing = {
            IconButton(onClick = { actions.removeFromPlaylist(song) }) { NovaIcon(R.drawable.ic_close, contentDescription = "Remove") }
        })
    }
}

@Composable
private fun SongRow(song: SongEntity, numbered: Boolean, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    ListItem(
        headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                listOfNotNull(song.artist ?: song.albumArtist, song.album, song.durationMs?.let(::formatTime)).joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            if (numbered) {
                Text(song.trackNumber?.toString() ?: "–", modifier = Modifier.width(28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Art(MusicLibrary.artUri(song))
            }
        },
        trailingContent = trailing,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun AlbumRow(album: Album, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(listOfNotNull(album.artist, album.year?.toString()).joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = { Art(album.artUri, size = 56) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun Art(uri: String?, size: Int = 48, round: Boolean = false) {
    val shape = RoundedCornerShape(if (round) (size / 2).dp else 6.dp)
    Box(
        Modifier.size(size.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        NovaIcon(R.drawable.ic_audiotrack, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (uri != null) {
            AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun DetailHeader(title: String, subtitle: String) {
    Column(Modifier.padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun PlayButtons(onPlay: () -> Unit, onShuffle: () -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onPlay) {
            NovaIcon(R.drawable.ic_play_arrow, contentDescription = null)
            Text("Play", modifier = Modifier.padding(start = 4.dp))
        }
        OutlinedButton(onClick = onShuffle) {
            NovaIcon(R.drawable.ic_shuffle, contentDescription = null)
            Text("Shuffle", modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp),
    )
}

@Composable
private fun MiniPlayer(player: MusicPlayerState, actions: AudioActions) {
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    val now = player.nowPlaying
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider()
            Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Art(now?.artUri)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(now?.title ?: "Loading…", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(
                        player.error ?: now?.artist.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (player.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = actions::previous) { NovaIcon(R.drawable.ic_skip_previous, contentDescription = "Previous") }
                IconButton(onClick = actions::togglePlay) {
                    NovaIcon(
                        if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                        contentDescription = if (player.isPlaying) "Pause" else "Play",
                    )
                }
                IconButton(onClick = actions::next, enabled = player.hasNext) { NovaIcon(R.drawable.ic_skip_next, contentDescription = "Next") }
            }
            val duration = player.durationMs.coerceAtLeast(1L).toFloat()
            Slider(
                value = (scrubbing ?: player.positionMs.toFloat()).coerceIn(0f, duration),
                onValueChange = { scrubbing = it },
                onValueChangeFinished = {
                    scrubbing?.let { actions.seekTo(it.toLong()) }
                    scrubbing = null
                },
                valueRange = 0f..duration,
                enabled = player.durationMs > 0,
                modifier = Modifier.padding(horizontal = 16.dp).height(32.dp),
            )
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatTime((scrubbing?.toLong() ?: player.positionMs))} / ${formatTime(player.durationMs)} · ${player.queueIndex + 1} of ${player.queueSize}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                )
                IconButton(onClick = actions::toggleShuffle) {
                    NovaIcon(
                        R.drawable.ic_shuffle,
                        contentDescription = "Shuffle",
                        tint = if (player.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = actions::cycleRepeat) {
                    NovaIcon(
                        if (player.repeat == RepeatMode.ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat,
                        contentDescription = "Repeat",
                        tint = if (player.repeat != RepeatMode.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { showLyrics = !showLyrics }, enabled = player.lyrics != null) {
                    Text(if (showLyrics) "Hide lyrics" else "Lyrics")
                }
                IconButton(onClick = actions::closePlayer) { NovaIcon(R.drawable.ic_close, contentDescription = "Close") }
            }
            val lyrics = player.lyrics
            if (showLyrics && lyrics != null) {
                val active = lyrics.activeIndex(player.positionMs)
                val listState = rememberLazyListState()
                LaunchedEffect(active) { if (active > 0) listState.animateScrollToItem((active - 1).coerceAtLeast(0)) }
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().height(160.dp).padding(horizontal = 16.dp)) {
                    itemsIndexed(lyrics.lines) { index, line ->
                        Text(
                            line.text.ifEmpty { "♪" },
                            style = if (index == active) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                            fontWeight = if (index == active) FontWeight.Bold else FontWeight.Normal,
                            color = if (index == active || !lyrics.synced) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun AddToPlaylistDialog(
    song: SongEntity,
    playlists: List<MusicPlaylistRow>,
    onPick: (playlistId: Long?, newName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add “${song.title}” to") },
        text = {
            Column {
                playlists.forEach { row ->
                    Text(
                        row.name,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(row.id, row.name) }.padding(vertical = 12.dp),
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("New playlist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(null, name) }, enabled = name.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private const val TOP_SONGS = 10

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
