package com.aakash.novafork.data.backup

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** Backup files as JSON: written readable, read leniently (unknown fields ignored), checked first. */
object BackupCodec {
    private const val NOT_A_BACKUP = "This file isn't a Nova Fork backup"
    private const val DAMAGED = "This backup file is damaged and can't be restored"
    private const val NEWER = "This backup was made by a newer version of Nova Fork. Update the app, then restore it."

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
    fun decode(text: String): NovaBackup {
        val root = try {
            json.parseToJsonElement(text)
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException.
            throw BackupFormatException(NOT_A_BACKUP, e)
        }
        val fields = root as? JsonObject ?: throw BackupFormatException(NOT_A_BACKUP)
        if ((fields["format"] as? JsonPrimitive)?.contentOrNull != BACKUP_FORMAT) throw BackupFormatException(NOT_A_BACKUP)
        val version = (fields["formatVersion"] as? JsonPrimitive)?.intOrNull ?: throw BackupFormatException(NOT_A_BACKUP)
        if (version > BACKUP_FORMAT_VERSION) throw BackupFormatException(NEWER)
        if (version < 1) throw BackupFormatException(NOT_A_BACKUP)
        return try {
            json.decodeFromJsonElement(NovaBackup.serializer(), fields)
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException(DAMAGED, e)
        }
    }
}
