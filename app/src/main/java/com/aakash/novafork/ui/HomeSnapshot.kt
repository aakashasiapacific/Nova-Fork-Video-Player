package com.aakash.novafork.ui

import com.aakash.novafork.ui.model.Artwork
import com.aakash.novafork.ui.model.HeroTarget
import com.aakash.novafork.ui.model.HeroUi
import com.aakash.novafork.ui.model.HomeUi
import com.aakash.novafork.ui.model.LibraryCounts
import com.aakash.novafork.ui.model.MediaKind
import com.aakash.novafork.ui.model.ShowCardUi
import com.aakash.novafork.ui.model.VideoCardUi
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * The last Home page, kept on disk so the next start draws it at once and the real library
 * replaces it a moment later (the way VLC opens on what it already knows). Only what Home
 * shows is kept: the banner and the rows. Anything unreadable gives null and the app starts
 * the usual way.
 */
object HomeSnapshot {
    private const val VERSION = 1
    private val json = Json { ignoreUnknownKeys = true }

    fun file(dir: File): File = File(dir, "home_snapshot.json")

    fun encode(home: HomeUi): String = buildJsonObject {
        put("v", VERSION)
        put("hero", buildJsonArray { home.hero.forEach { add(hero(it)) } })
        put("continueWatching", cards(home.continueWatching))
        put("upNext", cards(home.upNext))
        put("recentlyAdded", cards(home.recentlyAdded))
        put("movies", cards(home.movies))
        put("videos", cards(home.videos))
        put("shows", buildJsonArray { home.shows.forEach { add(show(it)) } })
        put("favorites", home.libraryCounts.favorites)
        put("all", home.libraryCounts.all)
    }.toString()

    fun decode(text: String): HomeUi? = try {
        val o = json.parseToJsonElement(text).jsonObject
        if (o["v"]?.int() != VERSION) {
            null
        } else {
            val heroes = o.list("hero").mapNotNull { hero(it) }
            val showCards = o.list("shows").mapNotNull { show(it) }
            val cw = o.list("continueWatching").mapNotNull { card(it) }
            val next = o.list("upNext").mapNotNull { card(it) }
            val recent = o.list("recentlyAdded").mapNotNull { card(it) }
            val movies = o.list("movies").mapNotNull { card(it) }
            val videos = o.list("videos").mapNotNull { card(it) }
            if (heroes.isEmpty() && cw.isEmpty() && recent.isEmpty() && movies.isEmpty() && showCards.isEmpty()) {
                null
            } else {
                HomeUi(
                    hero = heroes,
                    continueWatching = cw,
                    upNext = next,
                    recentlyAdded = recent,
                    movies = movies,
                    shows = showCards,
                    videos = videos,
                    isEmpty = false,
                    libraryCounts = LibraryCounts(favorites = o["favorites"]?.int() ?: 0, all = o["all"]?.int() ?: 0),
                )
            }
        }
    } catch (e: Exception) {
        null
    }

    fun read(dir: File): HomeUi? = try {
        file(dir).takeIf { it.isFile }?.readText()?.let(::decode)
    } catch (e: Exception) {
        null
    }

    fun write(dir: File, home: HomeUi) {
        if (home.isEmpty) return
        try {
            val target = file(dir)
            val temp = File(dir, "home_snapshot.tmp")
            temp.writeText(encode(home))
            if (!temp.renameTo(target)) {
                target.writeText(temp.readText())
                temp.delete()
            }
        } catch (e: Exception) {
            // The next start just opens the usual way.
        }
    }

    // Encoding

    private fun cards(list: List<VideoCardUi>): JsonArray = buildJsonArray { list.forEach { add(card(it)) } }

    private fun art(a: Artwork): JsonObject = buildJsonObject {
        put("poster", a.posterUrl)
        put("backdrop", a.backdropUrl)
        put("still", a.stillUrl)
        put("frame", a.frameSource)
        put("tall", a.tallUrl)
        put("logo", a.logoUrl)
        put("aspect", a.frameAspect)
        put("seed", a.seed)
        put("frameAt", a.frameAtMs)
    }

    private fun card(c: VideoCardUi): JsonObject = buildJsonObject {
        put("id", c.id)
        put("title", c.title)
        put("subtitle", c.subtitle)
        put("art", art(c.artwork))
        put("kind", c.kind.name)
        put("year", c.year)
        put("rating", c.rating)
        put("duration", c.durationMs)
        put("progress", c.progress)
        put("watched", c.watched)
        put("showTitle", c.showTitle)
        put("season", c.season)
        put("episode", c.episode)
        put("source", c.sourceLabel)
        put("sourceId", c.sourceId)
        put("newEpisode", c.newEpisode)
        put("uhd", c.uhd)
    }

    private fun show(s: ShowCardUi): JsonObject = buildJsonObject {
        put("key", s.key)
        put("title", s.title)
        put("art", art(s.artwork))
        put("year", s.year)
        put("rating", s.rating)
        put("seasons", s.seasonCount)
        put("episodes", s.episodeCount)
        put("unwatched", s.unwatchedCount)
    }

    private fun hero(h: HeroUi): JsonObject = buildJsonObject {
        when (val t = h.target) {
            is HeroTarget.Video -> {
                put("targetType", "video")
                put("targetId", t.id)
            }
            is HeroTarget.Show -> {
                put("targetType", "show")
                put("targetKey", t.key)
            }
        }
        put("title", h.title)
        put("overview", h.overview)
        put("art", art(h.artwork))
        put("meta", h.metaLine)
        put("rating", h.rating)
        put("genres", buildJsonArray { h.genres.forEach { add(JsonPrimitive(it)) } })
        put("progress", h.progress)
        put("playVideoId", h.playVideoId)
        put("playLabel", h.playLabel)
        put("onWatchlist", h.onWatchlist)
        put("favorite", h.favorite)
    }

    // Decoding

    private fun JsonElement.int(): Int? = (this as? JsonPrimitive)?.intOrNull
    private fun JsonObject.list(key: String): List<JsonObject> =
        (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
    private fun JsonObject.intv(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.float(key: String): Float? = (this[key] as? JsonPrimitive)?.floatOrNull
    private fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false

    private fun art(o: JsonObject?): Artwork = if (o == null) {
        Artwork()
    } else {
        Artwork(
            posterUrl = o.str("poster"),
            backdropUrl = o.str("backdrop"),
            stillUrl = o.str("still"),
            frameSource = o.str("frame"),
            tallUrl = o.str("tall"),
            logoUrl = o.str("logo"),
            frameAspect = o.float("aspect"),
            seed = o.str("seed") ?: "",
            frameAtMs = o.long("frameAt"),
        )
    }

    private fun card(o: JsonObject): VideoCardUi? {
        val id = o.long("id") ?: return null
        val title = o.str("title") ?: return null
        return VideoCardUi(
            id = id,
            title = title,
            subtitle = o.str("subtitle"),
            artwork = art(o["art"] as? JsonObject),
            kind = o.str("kind")?.let { name -> MediaKind.values().firstOrNull { it.name == name } } ?: MediaKind.VIDEO,
            year = o.intv("year"),
            rating = o.float("rating"),
            durationMs = o.long("duration"),
            progress = o.float("progress"),
            watched = o.bool("watched"),
            showTitle = o.str("showTitle"),
            season = o.intv("season"),
            episode = o.intv("episode"),
            sourceLabel = o.str("source"),
            sourceId = o.long("sourceId"),
            newEpisode = o.bool("newEpisode"),
            uhd = o.bool("uhd"),
        )
    }

    private fun show(o: JsonObject): ShowCardUi? {
        val key = o.str("key") ?: return null
        val title = o.str("title") ?: return null
        return ShowCardUi(
            key = key,
            title = title,
            artwork = art(o["art"] as? JsonObject),
            year = o.intv("year"),
            rating = o.float("rating"),
            seasonCount = o.intv("seasons") ?: 0,
            episodeCount = o.intv("episodes") ?: 0,
            unwatchedCount = o.intv("unwatched") ?: 0,
        )
    }

    private fun hero(o: JsonObject): HeroUi? {
        val target = when (o.str("targetType")) {
            "video" -> o.long("targetId")?.let { HeroTarget.Video(it) }
            "show" -> o.str("targetKey")?.let { HeroTarget.Show(it) }
            else -> null
        } ?: return null
        return HeroUi(
            target = target,
            title = o.str("title") ?: return null,
            overview = o.str("overview"),
            artwork = art(o["art"] as? JsonObject),
            metaLine = o.str("meta"),
            rating = o.float("rating"),
            genres = (o["genres"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList(),
            progress = o.float("progress"),
            playVideoId = o.long("playVideoId"),
            playLabel = o.str("playLabel") ?: "Play",
            previewUri = null,
            onWatchlist = o.bool("onWatchlist"),
            favorite = o.bool("favorite"),
        )
    }
}
