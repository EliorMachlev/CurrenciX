package com.eliormachlev.currencix.repository

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.eliormachlev.currencix.repository.json.JsonObject
import com.eliormachlev.currencix.repository.json.MalformedJsonException
import com.eliormachlev.currencix.repository.json.buildJsonObject
import com.eliormachlev.currencix.repository.json.jsonText
import com.eliormachlev.currencix.repository.json.parseJsonObject
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.prefStore
import com.eliormachlev.currencix.util.AppDispatchers
import com.eliormachlev.currencix.util.restartApp
import com.eliormachlev.currencix.util.wipe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.security.GeneralSecurityException
import java.time.Instant

// Backup schema version. Bump when the on-disk format changes in a
// non-backwards-compatible way; readers must reject unknown versions.
internal const val BACKUP_SCHEMA_VERSION = 1

private const val TAG = "BackupManager"

// Backup file top-level keys. [BACKUP_KEY_VERSION], [BACKUP_KEY_APP], and
// [BACKUP_KEY_CREATED_AT] live in [BackupSchema] so [CartExporter] can share
// the same envelope shape.
private const val KEY_NAMESPACES = "namespaces"
private const val KEY_ENCRYPTION = "encryption"

// Per-entry keys inside each namespace: {"type": "int", "value": 42}.
private const val KEY_TYPE = "type"
private const val KEY_VALUE = "value"

/**
 * Each preference type a backup carries: its "type" tag ([wire]), which
 * stored values it is ([holds]), its JSON payload, and how it's put back.
 */
private enum class BackupValueType(
    val wire: String,
) {
    STRING("string") {
        override fun holds(value: Any) = value is String

        override fun restore(
            editor: MutablePreferences,
            key: String,
            entry: JsonObject,
        ) {
            editor[stringPreferencesKey(key)] = entry.optString(KEY_VALUE)
        }
    },
    INT("int") {
        override fun holds(value: Any) = value is Int

        override fun restore(
            editor: MutablePreferences,
            key: String,
            entry: JsonObject,
        ) {
            editor[intPreferencesKey(key)] = entry.optInt(KEY_VALUE)
        }
    },
    LONG("long") {
        override fun holds(value: Any) = value is Long

        override fun restore(
            editor: MutablePreferences,
            key: String,
            entry: JsonObject,
        ) {
            editor[longPreferencesKey(key)] = entry.optLong(KEY_VALUE)
        }
    },
    FLOAT("float") {
        override fun holds(value: Any) = value is Float

        // JSON has no float: written as a double.
        override fun payload(value: Any): Any = (value as Float).toDouble()

        override fun restore(
            editor: MutablePreferences,
            key: String,
            entry: JsonObject,
        ) {
            editor[floatPreferencesKey(key)] = entry.optDouble(KEY_VALUE).toFloat()
        }
    },
    BOOLEAN("boolean") {
        override fun holds(value: Any) = value is Boolean

        override fun restore(
            editor: MutablePreferences,
            key: String,
            entry: JsonObject,
        ) {
            editor[booleanPreferencesKey(key)] = entry.optBoolean(KEY_VALUE)
        }
    },
    STRING_SET("stringSet") {
        override fun holds(value: Any) = value is Set<*>

        override fun payload(value: Any): Any = (value as Set<*>).filterIsInstance<String>()

        override fun restore(
            editor: MutablePreferences,
            key: String,
            entry: JsonObject,
        ) {
            entry.optArray(KEY_VALUE)?.let { values ->
                editor[stringSetPreferencesKey(key)] = values.filterIsInstance<String>().toHashSet()
            }
        }
    },
    ;

    abstract fun holds(value: Any): Boolean

    open fun payload(value: Any): Any = value

    abstract fun restore(
        editor: MutablePreferences,
        key: String,
        entry: JsonObject,
    )

    companion object {
        fun of(value: Any): BackupValueType? = entries.firstOrNull { it.holds(value) }

        fun fromWire(wire: String): BackupValueType? = entries.firstOrNull { it.wire == wire }
    }
}

// DataStore namespaces that carry user-authored state worth backing up. The
// "rates" namespace is intentionally excluded — it's a network cache that
// regenerates itself and would bloat backups. Wire-format names are stable
// (they match the historical SharedPreferences file basenames) so backups
// produced by older builds still restore into the DataStore-backed shape.
private val BACKUP_NAMESPACES: List<PersistenceKey> = PersistenceKey.backupNamespaces

sealed interface BackupResult {
    data object Success : BackupResult

    /** [reason] is for the user; [detail] (English, technical) is for the log. */
    data class Failure(
        val reason: FileFailure,
        val detail: String? = null,
    ) : BackupResult

    // Import saw an encrypted file and needs a password from the user.
    // The manager itself never prompts — the caller drives the UI.
    data object PasswordRequired : BackupResult

    // Import saw an encrypted file, tried the supplied password, and the
    // GCM tag failed to verify. Distinct from a generic failure so the UI
    // can re-prompt without treating the file as corrupt.
    data object WrongPassword : BackupResult
}

class BackupManager(
    private val context: Context,
    // Reading and writing the file and deriving the key from a password
    // (Argon2, deliberately slow) happen here, never on the main thread.
    private val io: CoroutineDispatcher = AppDispatchers.production.io,
    // What runs once a restore has replaced the stored state (tests pass a recorder).
    private val restart: (Context) -> Unit = ::restartApp,
) {
    private val crypto = BackupCrypto()

    /**
     * Write a backup to [uri]. If [password] is non-null and non-empty, the
     * `namespaces` block is encrypted (see [BackupCrypto]); otherwise it is
     * written as plain JSON.
     */
    suspend fun export(
        uri: Uri,
        password: CharArray? = null,
    ): BackupResult = withContext(io) { exportNow(uri, password) }

    private fun exportNow(
        uri: Uri,
        password: CharArray?,
    ): BackupResult =
        try {
            val payload = jsonText(buildBackupJson(password), pretty = true).toByteArray(Charsets.UTF_8)
            val written = context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(payload) }
            if (written == null) BackupResult.Failure(FileFailure.CANNOT_OPEN) else BackupResult.Success
        } catch (e: IOException) {
            BackupResult.Failure(FileFailure.READ_WRITE, e.message)
        } catch (e: SecurityException) {
            BackupResult.Failure(FileFailure.NO_PERMISSION, e.message)
        } catch (e: GeneralSecurityException) {
            BackupResult.Failure(FileFailure.ENCRYPTION, e.message)
        } finally {
            password?.wipe()
        }

    /**
     * Read a backup from [uri] and restore it into the DataStore namespaces.
     *
     * Returns [BackupResult.PasswordRequired] if the file is encrypted and no
     * password was supplied, or [BackupResult.WrongPassword] if the supplied
     * password fails authentication. The caller is expected to re-invoke
     * with a password in either case.
     */
    suspend fun import(
        uri: Uri,
        password: CharArray? = null,
    ): BackupResult = withContext(io) { importNow(uri, password) }

    private suspend fun importNow(
        uri: Uri,
        password: CharArray?,
    ): BackupResult =
        try {
            val root = readJson(uri)
            if (root == null) BackupResult.Failure(FileFailure.CANNOT_OPEN) else restore(root, password)
        } catch (e: WrongPasswordException) {
            Timber.tag(TAG).i(e, "Backup password rejected")
            BackupResult.WrongPassword
        } catch (e: IOException) {
            BackupResult.Failure(FileFailure.READ_WRITE, e.message)
        } catch (e: MalformedJsonException) {
            BackupResult.Failure(FileFailure.DAMAGED, e.message)
        } catch (e: SecurityException) {
            BackupResult.Failure(FileFailure.NO_PERMISSION, e.message)
        } catch (e: GeneralSecurityException) {
            BackupResult.Failure(FileFailure.DECRYPTION, e.message)
        } finally {
            password?.wipe()
        }

    /** True if the file at [uri] is a valid encrypted backup. */
    suspend fun isEncrypted(uri: Uri): Boolean = withContext(io) { isEncryptedNow(uri) }

    private fun isEncryptedNow(uri: Uri): Boolean =
        try {
            readJson(uri)?.has(KEY_ENCRYPTION) == true
        } catch (e: IOException) {
            Timber.tag(TAG).w(e, "Could not read the backup file")
            false
        } catch (e: MalformedJsonException) {
            Timber.tag(TAG).w(e, "The backup file is not JSON")
            false
        }

    // The file's JSON; null when the provider gives no stream for it.
    private fun readJson(uri: Uri): JsonObject? =
        context.contentResolver
            .openInputStream(uri)
            ?.use { it.readBytes() }
            ?.let { parseJsonObject(String(it, Charsets.UTF_8)) }

    private suspend fun restore(
        root: JsonObject,
        password: CharArray?,
    ): BackupResult {
        val version = root.optInt(BACKUP_KEY_VERSION, -1)
        val namespaces = if (version == BACKUP_SCHEMA_VERSION) extractNamespaces(root, password) else null
        return when {
            version != BACKUP_SCHEMA_VERSION -> {
                BackupResult.Failure(
                    FileFailure.UNSUPPORTED_VERSION,
                    "Unsupported backup version: $version",
                )
            }

            namespaces == null -> {
                BackupResult.PasswordRequired
            }

            else -> {
                restoreNamespaces(namespaces)
                // Restore replaced every backed-up namespace on disk; long-lived
                // in-memory PrefStore caches (and every LiveData built on them)
                // now hold stale values. A full process restart is the simplest,
                // safest way to hydrate the app from the restored state without
                // reasoning about which observer resubscribes first.
                restart(context)
                BackupResult.Success
            }
        }
    }

    private fun buildBackupJson(password: CharArray?): Map<String, Any> {
        val namespaces =
            buildJsonObject {
                BACKUP_NAMESPACES.forEach { key -> put(key.fileName, serializeNamespace(key.prefStore(context).snapshot())) }
                SAVED_DATA_SECTIONS.forEach { section -> put(section.key, section.export(context)) }
            }
        return buildJsonObject {
            put(BACKUP_KEY_VERSION, BACKUP_SCHEMA_VERSION)
            put(BACKUP_KEY_CREATED_AT, Instant.now().toString())
            put(BACKUP_KEY_APP, BACKUP_APP_ID)
            if (password != null && password.isNotEmpty()) {
                put(KEY_ENCRYPTION, crypto.encrypt(jsonText(namespaces).toByteArray(Charsets.UTF_8), password))
            } else {
                put(KEY_NAMESPACES, namespaces)
            }
        }
    }

    /**
     * @return the `namespaces` object (decrypted if need be), or `null` if
     * the file is encrypted and [password] is null/empty (caller must prompt).
     */
    private fun extractNamespaces(
        root: JsonObject,
        password: CharArray?,
    ): JsonObject? {
        val encBlock = root.optObject(KEY_ENCRYPTION)
        return when {
            encBlock == null -> root.optObject(KEY_NAMESPACES) ?: throw MalformedJsonException("Missing 'namespaces' section")
            password == null || password.isEmpty() -> null
            else -> parseJsonObject(String(crypto.decrypt(encBlock, password), Charsets.UTF_8))
        }
    }

    private fun serializeNamespace(prefs: Preferences): Map<String, Any> =
        buildJsonObject {
            prefs.asMap().forEach { (key, value) ->
                serializeEntry(value)?.let { put(key.name, it) }
            }
        }

    // {"type": …, "value": …} for a stored value; null for a type backups don't carry.
    private fun serializeEntry(value: Any): Map<String, Any>? {
        val type = BackupValueType.of(value) ?: return null
        return mapOf(KEY_TYPE to type.wire, KEY_VALUE to type.payload(value))
    }

    // Every write lands on disk before this returns: the restart that
    // follows a restore must find the restored state, not writes still in
    // flight on the PrefStore write pump.
    private suspend fun restoreNamespaces(namespaces: JsonObject) {
        SAVED_DATA_SECTIONS.forEach { section ->
            namespaces.optObject(section.key)?.let { section.restore(context, it) }
        }
        BACKUP_NAMESPACES.forEach { key ->
            val nsData = namespaces.optObject(key.fileName) ?: return@forEach
            key.prefStore(context).editAndAwait {
                clear()
                nsData.keys.forEach { entryKey ->
                    nsData.optObject(entryKey)?.let { entry -> applyEntry(this, entryKey, entry) }
                }
            }
        }
    }

    private fun applyEntry(
        editor: MutablePreferences,
        key: String,
        entry: JsonObject,
    ) {
        BackupValueType.fromWire(entry.optString(KEY_TYPE))?.restore(editor, key, entry)
    }
}
