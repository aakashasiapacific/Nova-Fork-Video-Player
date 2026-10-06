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

    /** Sample clips that ship with releases ("movie-sample.mkv", "sample.mkv"). */
    fun isSample(name: String): Boolean {
        val b = baseName(name).lowercase()
        return b == "sample" || b.endsWith("-sample") || b.endsWith(".sample") || b.startsWith("sample-")
    }
}
