package com.aakash.novafork.core.library

/**
 * Bonus material for a movie (trailers, featurettes, deleted scenes...) as Plex and Jellyfin name
 * it: files in an "Extras"-like subfolder of the movie's folder, or "<Movie>-trailer.mkv" next to
 * "<Movie>.mkv". Pure, so the matching can be tested without a library.
 */
object ExtraFiles {
    /** A video as far as matching cares. [folder] is any consistent path of its directory. */
    class Entry(
        val id: Long,
        val sourceId: Long,
        val folder: String,
        val fileName: String,
        /** Only movies can own extras. */
        val isMovie: Boolean,
    )

    private val EXTRAS_FOLDERS = setOf(
        "extras", "extra", "featurettes", "behind the scenes", "deleted scenes", "trailers",
        "interviews", "shorts", "scenes", "other",
    )

    /** Plex's file-name suffixes: "Movie (2010)-trailer.mkv". */
    private val SUFFIX = Regex(
        """^(.+?)\s*-\s*(trailer|featurette|behindthescenes|deleted|interview|scene|short|other)$""",
        RegexOption.IGNORE_CASE,
    )

    /** Jellyfin also takes a bare "trailer.mkv" beside the movie. */
    private val BARE_NAMES = setOf("trailer", "trailers")

    private fun segments(folder: String): List<String> = folder.trim('/').split('/').filter { it.isNotEmpty() }

    private fun isExtrasFolder(segment: String): Boolean = segment.trim().lowercase() in EXTRAS_FOLDERS

    /** [folder] with its trailing extras folders removed, or null when it is not inside one. */
    private fun movieFolderOf(folder: String): String? {
        val parts = segments(folder)
        var keep = parts.size
        while (keep > 0 && isExtrasFolder(parts[keep - 1])) keep--
        return if (keep == parts.size) null else parts.subList(0, keep).joinToString("/")
    }

    /** The movie name a Plex-suffixed [baseName] belongs to ("Dune (2021)-trailer" → "Dune (2021)"), else null. */
    fun suffixedMovieName(baseName: String): String? = SUFFIX.matchEntire(baseName.trim())?.groupValues?.get(1)?.trim()

    private fun isExtraLike(entry: Entry): Boolean {
        val base = VideoFiles.baseName(entry.fileName)
        return movieFolderOf(entry.folder) != null || suffixedMovieName(base) != null || base.trim().lowercase() in BARE_NAMES
    }

    /**
     * Extra id → the id of the movie it belongs to. A file matches only a movie of the same
     * source: the one in the folder above its extras folder (when exactly one movie lives there),
     * or the one beside it whose file name is the part before the Plex suffix. Anything that
     * cannot be matched is absent, so it stays an ordinary video.
     */
    fun match(entries: List<Entry>): Map<Long, Long> {
        val movies = entries.filter { it.isMovie && !isExtraLike(it) }
        if (movies.isEmpty()) return emptyMap()
        val moviesByFolder = movies.groupBy { it.sourceId to segments(it.folder).joinToString("/").lowercase() }
        val result = HashMap<Long, Long>()
        for (entry in entries) {
            if (!isExtraLike(entry)) continue
            val aboveExtras = movieFolderOf(entry.folder)
            val movie = if (aboveExtras != null) {
                moviesByFolder[entry.sourceId to aboveExtras.lowercase()]?.singleOrNull()
            } else {
                val beside = moviesByFolder[entry.sourceId to segments(entry.folder).joinToString("/").lowercase()].orEmpty()
                val name = suffixedMovieName(VideoFiles.baseName(entry.fileName))
                if (name != null) {
                    beside.firstOrNull { VideoFiles.baseName(it.fileName).trim().equals(name, ignoreCase = true) }
                } else {
                    beside.singleOrNull()
                }
            }
            if (movie != null) result[entry.id] = movie.id
        }
        return result
    }
}
