package com.aakash.novafork.sources.media

import com.aakash.novafork.core.plex.PlexConnection
import com.aakash.novafork.core.plex.PlexResource
import com.aakash.novafork.data.ServerItemType
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Collections

/** Jellyfin and Plex servers answered from memory: what scans, Browse and playback make of them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaServerSourcesTest {
    private val requests: MutableList<Request> = Collections.synchronizedList(ArrayList())
    private val savedAuth: MutableList<String> = Collections.synchronizedList(ArrayList())

    /** A client whose every request is answered by [answer] (status, JSON body). */
    private fun server(answer: (Request) -> Pair<Int, String>): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(
            Interceptor { chain ->
                val request = chain.request()
                requests += request
                val (code, body) = answer(request)
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("HTTP $code")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            },
        ).build()

    private fun environment(http: OkHttpClient) = MediaServerEnvironment(
        baseHttp = { http },
        readInstallId = { "install-1" },
        saveAuth = { id, token, userId, serverId -> savedAuth += "$id $token $userId $serverId" },
        appVersion = "1.2.3",
    )

    // Jellyfin

    private val jellyfinSource = SourceEntity(
        id = 5,
        type = SourceType.JELLYFIN,
        name = "Jellyfin",
        location = "http://jf:8096",
        username = "anna",
        password = "secret",
    )

    private fun jellyfin(request: Request): Pair<Int, String> {
        val url = request.url
        val path = url.encodedPath
        val types = url.queryParameter("IncludeItemTypes")
        val parent = url.queryParameter("ParentId")
        return when {
            path == "/Users/AuthenticateByName" -> 200 to """{"AccessToken":"tok-1","ServerId":"srv","User":{"Id":"u1","Name":"anna"}}"""
            path == "/Users/u1" -> 200 to """{"Id":"u1","Name":"anna"}"""
            path == "/Users/u1/Views" -> 200 to """{"Items":[
                {"Id":"lib-movies","Name":"Movies","CollectionType":"movies","IsFolder":true,"ImageTags":{"Primary":"t0"}},
                {"Id":"lib-shows","Name":"Shows","CollectionType":"tvshows","IsFolder":true},
                {"Id":"lib-music","Name":"Music","CollectionType":"music","IsFolder":true}
            ],"TotalRecordCount":3}"""
            path != "/Users/u1/Items" -> 404 to ""
            parent == "lib-movies" && types == "Movie,Episode,Video" -> 200 to """{"Items":[{
                "Id":"m1","Name":"Heat","Type":"Movie","ProductionYear":1995,"Overview":"Cops and robbers.",
                "OriginalTitle":"Heat","Genres":["Crime"],"CommunityRating":8.3,"RunTimeTicks":102000000000,
                "ProviderIds":{"Tmdb":"949","Imdb":"tt0113277"},"DateCreated":"2024-01-02T03:04:05.0000000Z",
                "ImageTags":{"Primary":"p1"},"BackdropImageTags":["b1"],
                "MediaSources":[{"Id":"ms1","Path":"/media/Movies/Heat (1995)/Heat (1995).mkv","Container":"mkv","Size":4000000000,
                  "RunTimeTicks":102000000000,"MediaStreams":[{"Type":"Video","Width":1920,"Height":800}]}]
            }],"TotalRecordCount":1}"""
            parent == "lib-shows" && types == "Series" -> 200 to """{"Items":[{
                "Id":"s1","Name":"Severance","Type":"Series","IsFolder":true,"ProductionYear":2022,"Overview":"Work-life balance.",
                "ProviderIds":{"Tvdb":"371980","Tmdb":"95396"},"ImageTags":{"Primary":"sp"},"BackdropImageTags":["sb"]
            }],"TotalRecordCount":1}"""
            parent == "lib-shows" && types == "Season" -> 200 to """{"Items":[
                {"Id":"se1","Name":"Season 1","Type":"Season","IsFolder":true,"IndexNumber":1,"ImageTags":{"Primary":"sep"}}
            ],"TotalRecordCount":1}"""
            parent == "lib-shows" && types == "Movie,Episode,Video" -> 200 to """{"Items":[
                {"Id":"e2","Name":"Half Loop","Type":"Episode","SeriesId":"s1","SeriesName":"Severance","SeasonId":"se1",
                 "SeasonName":"Season 1","ParentIndexNumber":1,"IndexNumber":2,"Overview":"Mark has questions.",
                 "PremiereDate":"2022-02-18T00:00:00.0000000Z","ImageTags":{"Primary":"ep2"},
                 "MediaSources":[{"Id":"e2","Path":"/media/TV/Severance/Season 1/Severance S01E02.mkv","Size":1000}]},
                {"Id":"e3","Name":"Missing","Type":"Episode","SeriesId":"s1","LocationType":"Virtual","ParentIndexNumber":1,"IndexNumber":3}
            ],"TotalRecordCount":2}"""
            parent == "lib-shows" -> 200 to """{"Items":[{"Id":"s1","Name":"Severance","Type":"Series","IsFolder":true}],"TotalRecordCount":1}"""
            else -> 200 to """{"Items":[],"TotalRecordCount":0}"""
        }
    }

    @Test
    fun aJellyfinScanListsMoviesAndEpisodesWithTheServersMetadata() = runTest {
        val connector = JellyfinConnector(environment(server(::jellyfin)))
        val videos = JellyfinScanner(connector).scan(jellyfinSource) {}
        assertEquals(listOf("m1", "e2"), videos.map { it.remoteId })

        val movie = videos[0]
        assertEquals("http://jf:8096/Videos/m1/stream?static=true&MediaSourceId=ms1", movie.uri)
        assertEquals("Heat (1995).mkv", movie.fileName)
        assertEquals("Movies", movie.folder)
        assertEquals(4_000_000_000L, movie.sizeBytes)
        assertEquals(10_200_000L, movie.durationMs)
        assertEquals(1920, movie.width)
        assertEquals(1_704_164_645_000L, movie.dateAdded)
        val movieMeta = movie.serverMetadata!!
        assertEquals(ServerItemType.MOVIE, movieMeta.type)
        assertEquals("Heat", movieMeta.title)
        assertNull(movieMeta.originalTitle)
        assertEquals(1995, movieMeta.year)
        assertEquals(mapOf("tmdb" to "949", "imdb" to "tt0113277"), movieMeta.providerIds)
        assertEquals(170, movieMeta.runtimeMin)
        assertEquals("http://jf:8096/Items/m1/Images/Primary?maxWidth=500&quality=90&tag=p1", movieMeta.posterUrl)
        assertEquals("http://jf:8096/Items/m1/Images/Backdrop/0?maxWidth=1280&quality=90&tag=b1", movieMeta.backdropUrl)

        val episode = videos[1]
        assertEquals("Shows/Severance/Season 1", episode.folder)
        assertEquals(listOf("Season 1", "Severance", "Shows"), episode.parentFolders)
        assertEquals("Severance S01E02.mkv", episode.fileName)
        val episodeMeta = episode.serverMetadata!!
        assertEquals(ServerItemType.EPISODE, episodeMeta.type)
        assertEquals("s1", episodeMeta.id)
        assertEquals("Severance", episodeMeta.title)
        assertEquals(2022, episodeMeta.year)
        assertEquals(1, episodeMeta.season)
        assertEquals(2, episodeMeta.episode)
        assertEquals("Half Loop", episodeMeta.episodeTitle)
        assertEquals("2022-02-18", episodeMeta.episodeAirDate)
        assertEquals("95396", episodeMeta.providerIds["tmdb"])
        assertTrue(episodeMeta.posterUrl!!.startsWith("http://jf:8096/Items/s1/Images/Primary?"))
        assertTrue(episodeMeta.seasonPosterUrl!!.startsWith("http://jf:8096/Items/se1/Images/Primary?"))
        assertTrue(episodeMeta.episodeStillUrl!!.startsWith("http://jf:8096/Items/e2/Images/Primary?"))

        // Signed in once, as this install, and the sign-in kept with the source; music never asked for.
        assertEquals(listOf("5 tok-1 u1 srv"), savedAuth)
        val signIn = requests.first { it.url.encodedPath == "/Users/AuthenticateByName" }
        assertTrue(signIn.header("X-Emby-Authorization")!!.contains("DeviceId=\"install-1\""))
        assertTrue(requests.none { it.url.queryParameter("ParentId") == "lib-music" })
        assertTrue(requests.drop(1).all { it.header("Authorization")!!.contains("Token=\"tok-1\"") })
        assertTrue(requests.none { it.url.queryParameter("api_key") != null })
    }

    @Test
    fun jellyfinBrowsingAndPlaying() = runTest {
        val connector = JellyfinConnector(environment(server(::jellyfin)))
        val source = jellyfinSource.copy(token = "tok-1", userId = "u1")
        val browser = JellyfinBrowser(connector)
        assertEquals(listOf("Movies", "Shows"), browser.list(source, "").map { it.name })
        val shows = browser.list(source, "lib-shows")
        assertEquals(listOf("s1"), shows.map { it.path })
        assertTrue(shows.single().isDirectory)

        val uri = "http://jf:8096/Videos/m1/stream?static=true&MediaSourceId=ms1"
        val playUri = JellyfinPlayback(connector).playUri(source, uri)
        assertEquals("$uri&api_key=tok-1&ApiKey=tok-1", playUri)
        // A stored token that works needs no sign-in.
        assertTrue(savedAuth.isEmpty())
        // Somewhere else gets no token.
        assertEquals("http://other:8096/Videos/m1/stream", JellyfinPlayback(connector).playUri(source, "http://other:8096/Videos/m1/stream"))
    }

    @Test
    fun aRefusedTokenSignsInAgain() = runTest {
        val http = server { request ->
            val token = request.header("Authorization").orEmpty()
            if (request.url.encodedPath != "/Users/AuthenticateByName" && "tok-old" in token) 401 to "" else jellyfin(request)
        }
        val connector = JellyfinConnector(environment(http))
        val libraries = JellyfinBrowser(connector).list(jellyfinSource.copy(token = "tok-old", userId = "u1"), "")
        assertEquals(2, libraries.size)
        assertEquals(listOf("5 tok-1 u1 srv"), savedAuth)
    }

    // Plex

    private val plexSource = SourceEntity(id = 7, type = SourceType.PLEX, name = "Plex", location = "http://plex:32400", token = "plex-tok")

    private fun plex(request: Request): Pair<Int, String> {
        val url = request.url
        val type = url.queryParameter("type")
        return when (url.encodedPath) {
            "/identity" -> 200 to """{"MediaContainer":{"machineIdentifier":"machine-1","version":"1.40"}}"""
            "/" -> 200 to """{"MediaContainer":{"friendlyName":"Den","machineIdentifier":"machine-1"}}"""
            "/library/sections" -> 200 to """{"MediaContainer":{"size":4,"Directory":[
                {"key":"1","title":"Movies","type":"movie","agent":"tv.plex.agents.movie"},
                {"key":"2","title":"TV Shows","type":"show","agent":"tv.plex.agents.series"},
                {"key":"3","title":"Home Videos","type":"movie","agent":"com.plexapp.agents.none"},
                {"key":"4","title":"Music","type":"artist"}
            ]}}"""
            "/library/sections/1/all" -> 200 to """{"MediaContainer":{"size":1,"totalSize":1,"Metadata":[{
                "ratingKey":"101","key":"/library/metadata/101","type":"movie","title":"Heat","year":1995,
                "summary":"Cops and robbers.","audienceRating":8.3,"thumb":"/library/metadata/101/thumb/1700000000",
                "art":"/library/metadata/101/art/1700000000","duration":10200000,"addedAt":1700000000,
                "Guid":[{"id":"imdb://tt0113277"},{"id":"tmdb://949"}],"Genre":[{"tag":"Crime"}],
                "Media":[{"id":201,"duration":10200000,"width":1920,"height":800,"container":"mkv",
                  "Part":[{"id":301,"key":"/library/parts/301/1700000000/file.mkv","file":"/data/Movies/Heat (1995).mkv","size":4000000000}]}]
            }]}}"""
            "/library/sections/2/all" -> if (type == "2") {
                200 to """{"MediaContainer":{"size":1,"Metadata":[{"ratingKey":"500","type":"show","title":"Severance","year":2022,
                    "summary":"Work-life balance.","thumb":"/library/metadata/500/thumb/1","Guid":[{"id":"tvdb://371980"}]}]}}"""
            } else {
                200 to """{"MediaContainer":{"size":1,"Metadata":[{"ratingKey":"502","type":"episode","title":"Half Loop",
                    "grandparentRatingKey":"500","grandparentTitle":"Severance","parentTitle":"Season 1","parentIndex":1,"index":2,
                    "parentThumb":"/library/metadata/501/thumb/1","thumb":"/library/metadata/502/thumb/1","originallyAvailableAt":"2022-02-18",
                    "Media":[{"Part":[{"id":302,"key":"/library/parts/302/1700000000/file.mkv","file":"/data/TV/Severance S01E02.mkv"}]}]}]}}"""
            }
            "/library/sections/3/all" -> 200 to """{"MediaContainer":{"size":1,"Metadata":[{"ratingKey":"900","type":"movie","title":"Birthday 2019",
                "Media":[{"Part":[{"id":303,"key":"/library/parts/303/1/file.mp4","file":"/data/Home/Birthday 2019.mp4"}]}]}]}}"""
            "/library/metadata/500/children" -> 200 to """{"MediaContainer":{"size":1,"Metadata":[
                {"ratingKey":"501","key":"/library/metadata/501/children","type":"season","title":"Season 1","index":1}]}}"""
            else -> 404 to ""
        }
    }

    @Test
    fun aPlexScanListsMoviesEpisodesAndHomeVideos() = runTest {
        val connector = PlexConnector(environment(server(::plex)), plexTvUrl = "http://plex-tv.invalid/")
        val videos = PlexScanner(connector).scan(plexSource) {}
        assertEquals(listOf("101", "502", "900"), videos.map { it.remoteId })

        val movie = videos[0]
        assertEquals("http://plex:32400/library/parts/301/1700000000/file.mkv", movie.uri)
        assertEquals("Heat (1995).mkv", movie.fileName)
        assertEquals("Movies", movie.folder)
        assertEquals(1_700_000_000_000L, movie.dateAdded)
        val movieMeta = movie.serverMetadata!!
        assertEquals("Heat", movieMeta.title)
        assertEquals(mapOf("imdb" to "tt0113277", "tmdb" to "949"), movieMeta.providerIds)
        assertEquals(listOf("Crime"), movieMeta.genres)
        val poster = movieMeta.posterUrl!!
        assertTrue(poster, poster.startsWith("http://plex:32400/photo/:/transcode?width=500&height=750"))
        assertTrue(poster, poster.contains("X-Plex-Token=plex-tok"))

        val episode = videos[1]
        assertEquals("TV Shows/Severance/Season 1", episode.folder)
        val episodeMeta = episode.serverMetadata!!
        assertEquals(ServerItemType.EPISODE, episodeMeta.type)
        assertEquals("500", episodeMeta.id)
        assertEquals("Severance", episodeMeta.title)
        assertEquals(2022, episodeMeta.year)
        assertEquals(1, episodeMeta.season)
        assertEquals(2, episodeMeta.episode)
        assertEquals("371980", episodeMeta.providerIds["tvdb"])
        assertNotNull(episodeMeta.seasonPosterUrl)

        // Personal media has no agent: its names are parsed as usual.
        assertEquals("Home Videos", videos[2].folder)
        assertNull(videos[2].serverMetadata)

        // The token goes in a header (and into image URLs), never into a stored URI.
        assertTrue(requests.all { it.header("X-Plex-Token") == "plex-tok" })
        assertTrue(requests.all { it.header("X-Plex-Client-Identifier") == "install-1" })
        assertTrue(videos.none { "plex-tok" in it.uri })
        assertTrue(requests.none { it.url.encodedPath == "/library/sections/4/all" })
        assertEquals(listOf("7 plex-tok null machine-1"), savedAuth)
    }

    @Test
    fun plexBrowsingAndPlaying() = runTest {
        val connector = PlexConnector(environment(server(::plex)), plexTvUrl = "http://plex-tv.invalid/")
        val source = plexSource.copy(serverId = "machine-1")
        val browser = PlexBrowser(connector)
        val sections = browser.list(source, "")
        assertEquals(listOf("Movies", "TV Shows", "Home Videos"), sections.map { it.name })
        assertEquals("/library/sections/1/all", sections[0].path)
        val movies = browser.list(source, "/library/sections/1/all")
        assertFalse(movies.single().isDirectory)
        assertEquals("http://plex:32400/library/parts/301/1700000000/file.mkv", movies.single().playUri)
        val seasons = browser.list(source, "/library/metadata/500/children")
        assertEquals("/library/metadata/501/children", seasons.single().path)
        assertTrue(seasons.single().isDirectory)

        val playUri = PlexPlayback(connector).playUri(source, "http://plex:32400/library/parts/301/1700000000/file.mkv")
        assertTrue(playUri, playUri.startsWith("http://plex:32400/library/parts/301/1700000000/file.mkv?"))
        assertTrue(playUri, playUri.endsWith("X-Plex-Token=plex-tok"))
    }

    @Test
    fun aPlexServerFromPlexTvBecomesASource() {
        val server = PlexResource(
            name = "Den",
            clientIdentifier = "machine-1",
            provides = "server",
            accessToken = "server-tok",
            connections = listOf(
                PlexConnection(uri = "https://1-2-3-4.abc.plex.direct:8443", relay = true),
                PlexConnection(uri = "https://5-6-7-8.abc.plex.direct:32400", local = false),
                PlexConnection(uri = "https://192-168-1-5.abc.plex.direct:32400", local = true),
            ),
        )
        assertEquals("https://192-168-1-5.abc.plex.direct:32400", PlexSignIn.address(server))
        val signIn = PlexSignIn(environment(server { 404 to "" }))
        val input = signIn.sourceInput(server, accountToken = "account-tok")!!
        assertEquals(SourceType.PLEX, input.type)
        assertEquals("Den", input.name)
        assertEquals("server-tok", input.token)
        assertEquals("machine-1", input.serverId)
        assertNull(signIn.sourceInput(server.copy(connections = emptyList()), "account-tok"))
    }

    @Test
    fun logsNeverCarryTokens() {
        val message = MediaServerLog.scrub(
            "GET http://jf:8096/Videos/1/stream?api_key=abc123&ApiKey=abc123 and http://plex:32400/x?X-Plex-Token=zzz9",
        )
        assertFalse(message, "abc123" in message)
        assertFalse(message, "zzz9" in message)
    }
}
