package com.aakash.novafork.core.webdav

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** One `<response>` of a WebDAV multistatus body, with the properties of its successful propstats. */
data class DavResponse(
    /** The href as the server sent it (entities resolved, still percent-encoded); a path or a full URL. */
    val href: String,
    /** The status of the response itself (a `<status>` directly inside `<response>`), when it has one. */
    val status: Int? = null,
    val isCollection: Boolean = false,
    val contentLength: Long? = null,
    /** Epoch ms. */
    val lastModified: Long? = null,
    val contentType: String? = null,
    val displayName: String? = null,
)

/**
 * Reads PROPFIND answers (RFC 4918 multistatus) the way real servers write them: any namespace
 * prefix or none (Nextcloud "d:", Apache "D:" and "lp1:", IIS full-URL hrefs, x/net/webdav as used
 * by rclone), properties split over several propstats, a missing or unbound DAV namespace.
 *
 * The scanner is a small tolerant one of its own: no DTD is read, so no external entity is ever
 * resolved, and a body cut off half way still gives the responses before the cut.
 */
object WebDavMultistatus {
    private const val DAV_NAMESPACE = "DAV:"
    private const val DIRECTORY_TYPE = "httpd/unix-directory"

    fun parse(xml: String): List<DavResponse> {
        val handler = MultistatusHandler()
        XmlScanner.scan(xml, handler)
        return handler.responses
    }

    /** "HTTP/1.1 404 Not Found" → 404; null when there is no three-digit code. */
    internal fun statusCode(line: String): Int? =
        line.trim().split(' ', '\t').firstNotNullOfOrNull { part -> part.takeIf { it.length == 3 }?.toIntOrNull() }

    /** RFC 1123 dates ("Sun, 06 Nov 1994 08:49:37 GMT", what getlastmodified holds) and ISO 8601 as a fallback. */
    internal fun parseDate(text: String): Long? {
        val value = text.trim()
        if (value.isEmpty()) return null
        return try {
            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            try {
                OffsetDateTime.parse(value).toInstant().toEpochMilli()
            } catch (e: DateTimeParseException) {
                try {
                    Instant.parse(value).toEpochMilli()
                } catch (e: DateTimeParseException) {
                    null
                }
            }
        }
    }

    private class Element(
        /** Local name, lower case. */
        val name: String,
        /** In the DAV: namespace, or in none a sloppy server forgot to declare. */
        val isDav: Boolean,
        /** The qualified name as written, to match its end tag. */
        val qualifiedName: String,
        /** Prefix → namespace declared on this element, null when it declares none. */
        val bindings: Map<String, String>?,
    )

    /** Properties collected from one propstat; kept only when its status is 2xx. */
    private class Props {
        var isCollection: Boolean? = null
        var contentLength: Long? = null
        var lastModified: Long? = null
        var contentType: String? = null
        var displayName: String? = null
        var status: Int? = null
    }

    private class ResponseBuilder {
        var href: String? = null
        var status: Int? = null
        var isCollection = false
        var collectionByType = false
        var contentLength: Long? = null
        var lastModified: Long? = null
        var contentType: String? = null
        var displayName: String? = null

        fun absorb(props: Props) {
            props.isCollection?.let { if (it) isCollection = true }
            props.contentLength?.let { contentLength = it }
            props.lastModified?.let { lastModified = it }
            props.contentType?.let { contentType = it }
            props.displayName?.let { displayName = it }
        }
    }

    private class MultistatusHandler : XmlScanner.Handler {
        val responses = ArrayList<DavResponse>()
        private val stack = ArrayList<Element>()
        private val text = StringBuilder()
        private var response: ResponseBuilder? = null
        private var props: Props? = null

        override fun start(name: String, attributes: List<Pair<String, String>>) {
            var bindings: HashMap<String, String>? = null
            for ((key, value) in attributes) {
                val declared = when {
                    key == "xmlns" -> ""
                    key.startsWith("xmlns:") -> key.substring(6)
                    else -> continue
                }
                val map = bindings ?: HashMap()
                map[declared] = value.trim()
                bindings = map
            }
            val prefix = if (':' in name) name.substringBefore(':') else ""
            val local = name.substringAfter(':').lowercase(Locale.ROOT)
            val namespace = bindings?.get(prefix) ?: resolve(prefix)
            val isDav = namespace == null || namespace.isEmpty() || namespace.equals(DAV_NAMESPACE, ignoreCase = true)
            val element = Element(local, isDav, name, bindings)
            stack.add(element)
            text.setLength(0)
            if (!isDav) {
                // A collection marker in another namespace still marks a collection.
                if (local == "collection" && within("resourcetype")) props?.isCollection = true
                return
            }
            when (local) {
                "response" -> {
                    response = ResponseBuilder()
                    props = null
                }
                "propstat" -> if (response != null) props = Props()
                // A prop outside any propstat: kept like one without a status.
                "prop" -> if (response != null && props == null) props = Props()
                "resourcetype" -> props?.let { if (it.isCollection == null) it.isCollection = false }
                "collection" -> if (within("resourcetype")) props?.isCollection = true
            }
        }

        override fun text(value: String) {
            text.append(value)
        }

        override fun end(name: String) {
            // Tolerate sloppy nesting: close back to the matching element, ignore a stray end tag.
            val index = stack.indexOfLast { it.qualifiedName == name }
                .takeIf { it >= 0 }
                ?: stack.indexOfLast { it.name == name.substringAfter(':').lowercase(Locale.ROOT) }
            if (index < 0) return
            while (stack.size > index + 1) close(stack.removeAt(stack.size - 1), "")
            close(stack.removeAt(stack.size - 1), text.toString())
            text.setLength(0)
        }

        override fun finish() {
            // A body cut off inside a response still gives what that response had so far.
            while (stack.isNotEmpty()) close(stack.removeAt(stack.size - 1), "")
        }

        private fun close(element: Element, content: String) {
            if (!element.isDav) return
            val parent = stack.lastOrNull()?.takeIf { it.isDav }?.name
            val current = response
            val currentProps = props
            when (element.name) {
                "href" -> if (parent == "response" && current != null && current.href == null) {
                    current.href = content.trim()
                }
                "status" -> when (parent) {
                    "response" -> current?.status = statusCode(content)
                    "propstat" -> currentProps?.status = statusCode(content)
                }
                "getcontentlength" -> if (currentProps != null && parent == "prop") {
                    currentProps.contentLength = content.trim().toLongOrNull()?.takeIf { it >= 0 }
                }
                "getlastmodified" -> if (currentProps != null && parent == "prop") {
                    currentProps.lastModified = parseDate(content)
                }
                "getcontenttype" -> if (currentProps != null && parent == "prop") {
                    currentProps.contentType = content.trim().takeIf { it.isNotEmpty() }
                }
                "displayname" -> if (currentProps != null && parent == "prop") {
                    currentProps.displayName = content.trim().takeIf { it.isNotEmpty() }
                }
                "propstat" -> {
                    if (current != null && currentProps != null) {
                        val status = currentProps.status
                        if (status == null || status in 200..299) current.absorb(currentProps)
                    }
                    props = null
                }
                "response" -> {
                    if (current != null && currentProps != null) {
                        val status = currentProps.status
                        if (status == null || status in 200..299) current.absorb(currentProps)
                    }
                    val href = current?.href
                    if (current != null && !href.isNullOrEmpty()) {
                        val type = current.contentType
                        val isDirectoryType = type != null && type.substringBefore(';').trim()
                            .equals(DIRECTORY_TYPE, ignoreCase = true)
                        responses += DavResponse(
                            href = href,
                            status = current.status,
                            isCollection = current.isCollection || isDirectoryType,
                            contentLength = current.contentLength,
                            lastModified = current.lastModified,
                            contentType = type?.takeUnless { isDirectoryType },
                            displayName = current.displayName,
                        )
                    }
                    response = null
                    props = null
                }
            }
        }

        private fun within(name: String): Boolean = stack.size >= 2 && stack[stack.size - 2].name == name

        private fun resolve(prefix: String): String? {
            for (i in stack.indices.reversed()) {
                stack[i].bindings?.get(prefix)?.let { return it }
            }
            return null
        }
    }
}

/**
 * A forgiving XML tokenizer: start tags with attributes, end tags, text with the predefined and
 * numeric entities resolved, CDATA. Declarations, processing instructions and comments are
 * skipped; a DOCTYPE (with its internal subset) is skipped unread.
 */
internal object XmlScanner {
    interface Handler {
        fun start(name: String, attributes: List<Pair<String, String>>)
        fun end(name: String)
        fun text(value: String)
        fun finish()
    }

    fun scan(xml: String, handler: Handler) {
        val n = xml.length
        var i = if (n > 0 && xml[0] == '﻿') 1 else 0
        while (i < n) {
            val open = xml.indexOf('<', i)
            if (open < 0) {
                handler.text(decodeEntities(xml.substring(i)))
                break
            }
            if (open > i) handler.text(decodeEntities(xml.substring(i, open)))
            i = when {
                xml.startsWith("<!--", open) -> after(xml, "-->", open + 4)
                xml.startsWith("<![CDATA[", open) -> {
                    val end = xml.indexOf("]]>", open + 9)
                    handler.text(xml.substring(open + 9, if (end < 0) n else end))
                    if (end < 0) n else end + 3
                }
                xml.startsWith("<?", open) -> after(xml, "?>", open + 2)
                xml.startsWith("<!", open) -> skipDeclaration(xml, open + 2)
                xml.startsWith("</", open) -> {
                    val end = xml.indexOf('>', open + 2)
                    if (end < 0) {
                        n
                    } else {
                        val name = xml.substring(open + 2, end).trim()
                        if (name.isNotEmpty()) handler.end(name)
                        end + 1
                    }
                }
                else -> startTag(xml, open, handler)
            }
        }
        handler.finish()
    }

    private fun after(xml: String, terminator: String, from: Int): Int {
        val end = xml.indexOf(terminator, from)
        return if (end < 0) xml.length else end + terminator.length
    }

    /** Skips "<!DOCTYPE … [ … ]>" and the like, brackets and quotes included. */
    private fun skipDeclaration(xml: String, from: Int): Int {
        var depth = 0
        var quote = 0.toChar()
        var i = from
        while (i < xml.length) {
            val c = xml[i]
            when {
                quote != 0.toChar() -> if (c == quote) quote = 0.toChar()
                c == '"' || c == '\'' -> quote = c
                c == '[' -> depth++
                c == ']' -> if (depth > 0) depth--
                c == '>' && depth == 0 -> return i + 1
            }
            i++
        }
        return xml.length
    }

    /** Parses "<name a='1' b="2"/>" at [open]; returns the index after it. */
    private fun startTag(xml: String, open: Int, handler: Handler): Int {
        val n = xml.length
        var i = open + 1
        val nameStart = i
        while (i < n && !xml[i].isWhitespace() && xml[i] != '>' && xml[i] != '/') i++
        val name = xml.substring(nameStart, i)
        val attributes = ArrayList<Pair<String, String>>()
        var selfClosing = false
        while (i < n) {
            val c = xml[i]
            when {
                c == '>' -> {
                    i++
                    break
                }
                c == '/' -> {
                    selfClosing = true
                    i++
                }
                c.isWhitespace() -> i++
                else -> {
                    selfClosing = false
                    val keyStart = i
                    while (i < n && !xml[i].isWhitespace() && xml[i] != '=' && xml[i] != '>' && xml[i] != '/') i++
                    val key = xml.substring(keyStart, i)
                    while (i < n && xml[i].isWhitespace()) i++
                    var value = ""
                    if (i < n && xml[i] == '=') {
                        i++
                        while (i < n && xml[i].isWhitespace()) i++
                        if (i < n && (xml[i] == '"' || xml[i] == '\'')) {
                            val quote = xml[i]
                            val end = xml.indexOf(quote, i + 1).let { if (it < 0) n else it }
                            value = decodeEntities(xml.substring(i + 1, end))
                            i = if (end < n) end + 1 else n
                        } else {
                            val valueStart = i
                            while (i < n && !xml[i].isWhitespace() && xml[i] != '>') i++
                            value = decodeEntities(xml.substring(valueStart, i))
                        }
                    }
                    if (key.isNotEmpty()) attributes += key to value
                }
            }
        }
        if (name.isEmpty()) return i
        handler.start(name, attributes)
        if (selfClosing) handler.end(name)
        return i
    }

    /** Resolves &lt; &gt; &amp; &quot; &apos; and &#…; / &#x…;; anything else stays as written. */
    fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c != '&') {
                out.append(c)
                i++
                continue
            }
            val semicolon = text.indexOf(';', i + 1)
            if (semicolon < 0 || semicolon - i > 10) {
                out.append(c)
                i++
                continue
            }
            val entity = text.substring(i + 1, semicolon)
            val replacement: String? = when {
                entity == "lt" -> "<"
                entity == "gt" -> ">"
                entity == "amp" -> "&"
                entity == "quot" -> "\""
                entity == "apos" -> "'"
                entity.startsWith("#x") || entity.startsWith("#X") -> codePoint(entity.substring(2), 16)
                entity.startsWith("#") -> codePoint(entity.substring(1), 10)
                else -> null
            }
            if (replacement == null) {
                out.append(c)
                i++
            } else {
                out.append(replacement)
                i = semicolon + 1
            }
        }
        return out.toString()
    }

    private fun codePoint(digits: String, radix: Int): String? {
        val value = digits.toIntOrNull(radix) ?: return null
        return if (Character.isValidCodePoint(value) && value != 0) String(Character.toChars(value)) else null
    }
}
