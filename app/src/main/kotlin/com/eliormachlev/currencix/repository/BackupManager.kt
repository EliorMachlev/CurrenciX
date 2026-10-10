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
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.prefStore
import com.eliormachlev.currencix.util.restartApp
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
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

private const val TYPE_STRING = "string"
private const val TYPE_INT = "int"
private const val TYPE_LONG = "long"
private const val TYPE_FLOAT = "float"
private const val TYPE_BOOLEAN = "boolean"
private const val TYPE_STRING_SET = "stringSet"

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
    // What runs once a restore has replaced the stored state (tests pass a recorder).
    private val restart: (Context) -> Unit = ::restartApp,
) {
    private val crypto = BackupCrypto()

    /**
     * Write a backup to [uri]. If [password] is non-null and non-empty, the
     * `namespaces` block is encrypted (see [BackupCrypto]); otherwise it is
     * written as plain JSON.
     */
    fun export(
        uri: Uri,
        password: CharArray? = null,
    ): BackupResult =
        try {
            val payload = buildBackupJson(password).toString(2).toByteArray(Charsets.UTF_8)
            val written = context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(payload) }
            if (written == null) BackupResult.Failure(FileFailure.CANNOT_OPEN) else BackupResult.Success
        } catch (e: IOException) {
            BackupResult.Failure(FileFailure.READ_WRITE, e.message)
        } catch (e: SecurityException) {
            BackupResult.Failure(FileFailure.NO_PERMISSION, e.message)
        } catch (e: GeneralSecurityException) {
            BackupResult.Failure(FileFailure.ENCRYPTION, e.message)
        } finally {
            password?.fill('\u0000')
        }

    /**
     * Read a backup from [uri] and restore it into the DataStore namespaces.
     *
     * Returns [BackupResult.PasswordRequired] if the file is encrypted and no
     * password was supplied, or [BackupResult.WrongPassword] if the supplied
     * password fails authentication. The caller is expected to re-invoke
     * with a password in either case.
     */
    fun import(
        uri: Uri,
        password: CharArray? = null,
    ): BackupResult =
        try {
            val root = readJson(uri)
            if (root == null) BackupResult.Failure(FileFailure.CANNOT_OPEN) else restore(root, password)
        } catch (e: WrongPasswordException) {
            Timber.tag(TAG).i(e, "Backup password rejected")
            BackupResult.WrongPassword
        } catch (e: IOException) {
            BackupResult.Failure(FileFailure.READ_WRITE, e.message)
        } catch (e: JSONException) {
            BackupResult.Failure(FileFailure.DAMAGED, e.message)
        } catch (e: SecurityException) {
            BackupResult.Failure(FileFailure.NO_PERMISSION, e.message)
        } catch (e: GeneralSecurityException) {
            BackupResult.Failure(FileFailure.DECRYPTION, e.message)
        } finally {
            password?.fill('\u0000')
        }

    /** True if the file at [uri] is a valid encrypted backup. */
    fun isEncrypted(uri: Uri): Boolean =
        try {
            readJson(uri)?.has(KEY_ENCRYPTION) == true
        } catch (e: IOException) {
            Timber.tag(TAG).w(e, "Could not read the backup file")
            false
        } catch (e: JSONException) {
            Timber.tag(TAG).w(e, "The backup file is not JSON")
            false
        }

    // The file's JSON; null when the provider gives no stream for it.
    private fun readJson(uri: Uri): JSONObject? =
        context.contentResolver
            .openInputStream(uri)
            ?.use { it.readBytes() }
            ?.let { JSONObject(String(it, Charsets.UTF_8)) }

    private fun restore(
        root: JSONObject,
        password: CharArray?,
    ): BackupResult {
        val version = root.optInt(BACKUP_KEY_VERSION, -1)
        val namespaces = if (version == BACKUP_SCHEMA_VERSION) extractNamespaces(root, password) else null
        return when {
            version != BACKUP_SCHEMA_VERSION ->
                BackupResult.Failure(
                    FileFailure.UNSUPPORTED_VERSION,
                    "Unsupported backup version: $version",
                )
            namespaces == null -> BackupResult.PasswordRequired
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

    private fun buildBackupJson(password: CharArray?): JSONObject {
        val nsObj = JSONObject()
        BACKUP_NAMESPACES.forEach { key ->
            nsObj.put(key.fileName, serializeNamespace(key.prefStore(context).snapshot()))
        }
        val root =
            JSONObject().apply {
                put(BACKUP_KEY_VERSION, BACKUP_SCHEMA_VERSION)
                put(BACKUP_KEY_CREATED_AT, Instant.now().toString())
                put(BACKUP_KEY_APP, BACKUP_APP_ID)
            }
        if (password != null && password.isNotEmpty()) {
            root.put(KEY_ENCRYPTION, crypto.encrypt(nsObj.toString().toByteArray(Charsets.UTF_8), password))
        } else {
            root.put(KEY_NAMESPACES, nsObj)
        }
        return root
    }

    /**
     * @return the `namespaces` object (decrypted if need be), or `null` if
     * the file is encrypted and [password] is null/empty (caller must prompt).
     */
    private fun extractNamespaces(
        root: JSONObject,
        password: CharArray?,
    ): JSONObject? {
        val encBlock = root.optJSONObject(KEY_ENCRYPTION)
        return when {
            encBlock == null -> root.optJSONObject(KEY_NAMESPACES) ?: throw JSONException("Missing 'namespaces' section")
            password == null || password.isEmpty() -> null
            else -> JSONObject(String(crypto.decrypt(encBlock, password), Charsets.UTF_8))
        }
    }

    private fun serializeNamespace(prefs: Preferences): JSONObject {
        val obj = JSONObject()
        prefs.asMap().forEach { (key, value) ->
            serializeEntry(value)?.let { obj.put(key.name, it) }
        }
        return obj
    }

    // {"type": …, "value": …} for a stored value; null for a type backups don't carry.
    private fun serializeEntry(value: Any?): JSONObject? {
        val typed: Pair<String, Any>? =
            when (value) {
                is String -> TYPE_STRING to value
                is Int -> TYPE_INT to value
                is Long -> TYPE_LONG to value
                is Float -> TYPE_FLOAT to value.toDouble()
                is Boolean -> TYPE_BOOLEAN to value
                is Set<*> -> TYPE_STRING_SET to JSONArray(value.filterIsInstance<String>())
                else -> null
            }
        return typed?.let { (type, payload) ->
            JSONObject().apply {
                put(KEY_TYPE, type)
                put(KEY_VALUE, payload)
            }
        }
    }

    private fun restoreNamespaces(namespaces: JSONObject) {
        // Blocking is intentional here — restore must complete before the
        // subsequent restart, so we can't return to the caller with writes
        // still in flight on the PrefStore write pump. DataStore edit is a
        // suspend function; runBlocking bridges from the plain-callback
        // import() entry point without pushing suspend up through the UI.
        runBlocking {
            BACKUP_NAMESPACES.forEach { key ->
                val nsData = namespaces.optJSONObject(key.fileName) ?: return@forEach
                key.prefStore(context).editAndAwait {
                    clear()
                    nsData.keys().forEach { entryKey ->
                        nsData.optJSONObject(entryKey)?.let { entry -> applyEntry(this, entryKey, entry) }
                    }
                }
            }
        }
    }

    private fun applyEntry(
        editor: MutablePreferences,
        key: String,
        entry: JSONObject,
    ) {
        when (entry.optString(KEY_TYPE)) {
            TYPE_STRING -> editor[stringPreferencesKey(key)] = entry.optString(KEY_VALUE)
            TYPE_INT -> editor[intPreferencesKey(key)] = entry.optInt(KEY_VALUE)
            TYPE_LONG -> editor[longPreferencesKey(key)] = entry.optLong(KEY_VALUE)
            TYPE_FLOAT -> editor[floatPreferencesKey(key)] = entry.optDouble(KEY_VALUE).toFloat()
            TYPE_BOOLEAN -> editor[booleanPreferencesKey(key)] = entry.optBoolean(KEY_VALUE)
            TYPE_STRING_SET ->
                entry.optJSONArray(KEY_VALUE)?.let { values ->
                    editor[stringSetPreferencesKey(key)] = (0 until values.length()).mapTo(HashSet()) { values.optString(it) }
                }
        }
    }
}
