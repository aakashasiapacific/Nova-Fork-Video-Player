package com.aakash.novafork.core.library

object VideoFiles {
    val EXTENSIONS: Set<String> = setOf(
        "mkv", "mp4", "m4v", "avi", "mov", "wmv", "flv", "webm", "ts", "m2ts", "mts", "mpg", "mpeg",
        "3gp", "3g2", "ogv", "vob", "divx", "rmvb", "rm", "asf", "f4v", "mxf", "dv", "iso",
    )

    fun extension(name: String): String = name.substringAfterLast('.', "").lowercase()
    fun baseName(name: String): String = if ('.' in name) name.substringBeforeLast('.') else name
    fun isVideo(name: String): Boolean = extension(name) in EXTENSIONS && !name.startsWith("._")

    /** Folders never worth scanning: hidden ones, "sample", "extras"-like junk, Android system dirs. */
    fun isIgnoredFolder(name: String): Boolean {
        val n = name.lowercase()
        return n.startsWith(".") || n == "android" || n == "@eadir" || n == "\$recycle.bin" ||
            n == "system volume information" || n == "lost+found" || n == "sample" || n == "samples"
    }

    /**
     * Whether a folder is skipped, given its path from the storage (or picked folder) root, root
     * first. Like [isIgnoredFolder] for every part, except that "Android/media" is kept: on Android
     * 11+ messaging apps keep their videos there
     * ("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video"). The rest of "Android"
     * (data, obb) stays skipped; "Android" itself is not, so a folder walk can reach "media".
     */
    fun isIgnoredPath(segments: List<String>): Boolean {
        val parts = segments.filter { it.isNotEmpty() }
        parts.forEachIndexed { i, name ->
            if (i == 0 && name.equals("android", ignoreCase = true)) {
                val next = parts.getOrNull(1)
                if (next != null && !next.equals("media", ignoreCase = true)) return true
            } else if (isIgnoredFolder(name)) {
                return true
            }
        }
        return false
    }

    /** Sample clips that ship with releases ("movie-sample.mkv", "sample.mkv"). */
    fun isSample(name: String): Boolean {
        val b = baseName(name).lowercase()
        return b == "sample" || b.endsWith("-sample") || b.endsWith(".sample") || b.startsWith("sample-")
    }
}
