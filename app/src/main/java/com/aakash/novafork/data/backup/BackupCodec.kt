package com.aakash.novafork.data.backup

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.ByteArrayInputStream

/** Backup files as JSON: written readable, read leniently (unknown fields ignored), checked first. */
object BackupCodec {
    internal const val NOT_A_BACKUP = "This file isn't a Nova Fork backup"
    private const val DAMAGED = "This backup file is damaged and can't be restored"
    private const val NEWER = "This backup was made by a newer version of Nova Fork. Update the app, then restore it."

    /** What says a file is a backup, and of which format; read on its own, the rest of the file skipped. */
    @Serializable
    private class Header(val format: String? = null, val formatVersion: Int? = null)

    @OptIn(ExperimentalSerializationApi::class)
    internal val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
    }

    fun encode(backup: NovaBackup): String = json.encodeToString(NovaBackup.serializer(), backup)

    /**
     * Reads a backup. Anything that is not a backup, one of a newer format than
     * [BACKUP_FORMAT_VERSION], or a damaged one throws [BackupFormatException] with a message for people.
     */
    fun decode(text: String): NovaBackup = decode(text.toByteArray(Charsets.UTF_8))

    /**
     * [decode] for the UTF-8 JSON in [bytes] from [offset] on. The JSON is read straight into the
     * backup, never held as a String or a tree of elements: a file near the size limit costs little
     * more memory than its bytes and what it holds.
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun decode(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): NovaBackup {
        val header = try {
            json.decodeFromStream(Header.serializer(), ByteArrayInputStream(bytes, offset, length))
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException.
            throw BackupFormatException(NOT_A_BACKUP, e)
        }
        if (header.format != BACKUP_FORMAT) throw BackupFormatException(NOT_A_BACKUP)
        val version = header.formatVersion ?: throw BackupFormatException(NOT_A_BACKUP)
        if (version > BACKUP_FORMAT_VERSION) throw BackupFormatException(NEWER)
        if (version < 1) throw BackupFormatException(NOT_A_BACKUP)
        return try {
            json.decodeFromStream(NovaBackup.serializer(), ByteArrayInputStream(bytes, offset, length))
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException(DAMAGED, e)
        }
    }
}
