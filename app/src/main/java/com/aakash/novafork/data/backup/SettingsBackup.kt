package com.aakash.novafork.data.backup

import android.content.SharedPreferences
import com.aakash.novafork.core.jellyfin.ServerSecrets
import com.aakash.novafork.data.LayoutMode
import com.aakash.novafork.data.PrefsSettingsStore
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * The settings file, entry by entry with its type. It is copied as a whole, so settings added to
 * PrefsSettingsStore later are backed up without changes here.
 */
internal object SettingsBackup {
    /** PrefsSettingsStore's SharedPreferences file (its PREFS_NAME). */
    const val PREFS_NAME = "settings"

    /** PrefsSettingsStore's key for Settings.layoutMode. */
    const val KEY_LAYOUT_MODE = "layout_mode"

    private val SECRET_MARKERS = listOf("api_key", "apikey", "password", "passwd", "token", "secret")

    /** API keys, passwords and tokens typed in Settings: kept out of a backup without secrets. */
    fun isSecret(key: String): Boolean {
        val name = key.lowercase()
        return SECRET_MARKERS.any { it in name }
    }

    fun export(prefs: SharedPreferences, includeSecrets: Boolean): List<BackupSetting> =
        prefs.all.entries
            .filter { (key, _) -> includeSecrets || !isSecret(key) }
            .sortedBy { it.key }
            .mapNotNull { (key, value) -> entry(key, value) }

    private fun entry(key: String, value: Any?): BackupSetting? = when (value) {
        is String -> BackupSetting(key, SettingType.STRING, JsonPrimitive(value))
        is Boolean -> BackupSetting(key, SettingType.BOOLEAN, JsonPrimitive(value))
        is Int -> BackupSetting(key, SettingType.INT, JsonPrimitive(value))
        is Long -> BackupSetting(key, SettingType.LONG, JsonPrimitive(value))
        is Float -> BackupSetting(key, SettingType.FLOAT, JsonPrimitive(value))
        is Set<*> -> {
            val strings = value.filterIsInstance<String>().sorted().map { JsonPrimitive(it) }
            BackupSetting(key, SettingType.STRING_SET, JsonArray(strings))
        }
        else -> null
    }

    /**
     * Writes [entries] over the current settings; returns how many were written. Settings the
     * backup doesn't have keep their values here: a backup without secrets leaves this device's
     * keys alone, and so does an empty secret. A backup on "Automatic" layout keeps this device's
     * own layout choice (phone or tablet is a matter of the device). Entries of the wrong shape
     * are skipped, and so are those of another type than PrefsSettingsStore stores the key with
     * (a damaged or hand-edited file, or a later version's): it could not read them back.
     */
    fun restore(prefs: SharedPreferences, entries: List<BackupSetting>): Int {
        val editor = prefs.edit()
        var written = 0
        for (entry in entries) {
            if (entry.key.isEmpty()) continue
            val expected = PrefsSettingsStore.KEY_TYPES[entry.key]
            if (expected != null && expected != entry.type) continue
            if (entry.key == KEY_LAYOUT_MODE && entry.value.string() == LayoutMode.AUTO.name) continue
            val value = entry.value
            val put = when (entry.type) {
                SettingType.STRING -> value.string()
                    ?.takeUnless { isSecret(entry.key) && it.isBlank() }
                    ?.let { editor.putString(entry.key, it) }
                SettingType.BOOLEAN -> (value as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
                    ?.let { editor.putBoolean(entry.key, it) }
                SettingType.INT -> (value as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
                    ?.let { editor.putInt(entry.key, it) }
                SettingType.LONG -> (value as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
                    ?.let { editor.putLong(entry.key, it) }
                SettingType.FLOAT -> (value as? JsonPrimitive)?.takeUnless { it.isString }?.floatOrNull
                    ?.let { editor.putFloat(entry.key, it) }
                SettingType.STRING_SET -> (value as? JsonArray)
                    ?.mapNotNull { it.string() }
                    ?.let { editor.putStringSet(entry.key, it.toSet()) }
            }
            if (put != null) written++
        }
        if (written > 0) editor.commit()
        return written
    }

    private fun JsonElement.string(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
}

/** Secrets in URLs the user typed (saved streams): a password in the user info, a token in the query. */
internal object UriSecrets {
    private val PASSWORD_IN_USER_INFO = Regex("""^([A-Za-z][A-Za-z0-9+.\-]*://)[^/?#@]*:[^/?#@]*@""")

    fun hasSecret(uri: String): Boolean = redact(uri) != uri

    /** [uri] with the user info's password gone (with its user name) and token parameters masked. */
    fun redact(uri: String): String = ServerSecrets.redact(uri.replace(PASSWORD_IN_USER_INFO, "\$1"))
}
