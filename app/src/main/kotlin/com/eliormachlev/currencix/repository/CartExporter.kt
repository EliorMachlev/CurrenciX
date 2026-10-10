package com.eliormachlev.currencix.repository

import android.content.Context
import android.net.Uri
import com.eliormachlev.currencix.model.SavedCart
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.time.Instant

// Single-cart file envelope. Shares the header keys (version / app /
// createdAt) with [BackupManager], but the payload is one [SavedCart]
// under `cart` instead of the full-app namespaces block.
internal const val CART_FILE_SCHEMA_VERSION = 1
private const val CART_FILE_KEY_TYPE = "type"
private const val CART_FILE_KEY_PAYLOAD = "cart"
private const val CART_FILE_TYPE = "cart"

sealed class CartFileResult {
    data object Success : CartFileResult()

    data class Loaded(
        val cart: SavedCart,
    ) : CartFileResult()

    /** [reason] is for the user; [detail] (English, technical) is for the log. */
    data class Failure(
        val reason: FileFailure,
        val detail: String? = null,
    ) : CartFileResult()
}

/**
 * Reads and writes a single [SavedCart] to a user-chosen file via SAF.
 * Plaintext JSON — cart data isn't secret, and keeping it readable lets a
 * user peek at the file with any text editor.
 */
class CartExporter(
    private val context: Context,
) {
    fun export(
        uri: Uri,
        cart: SavedCart,
    ): CartFileResult =
        try {
            val root =
                JSONObject().apply {
                    put(BACKUP_KEY_VERSION, CART_FILE_SCHEMA_VERSION)
                    put(BACKUP_KEY_APP, BACKUP_APP_ID)
                    put(CART_FILE_KEY_TYPE, CART_FILE_TYPE)
                    put(BACKUP_KEY_CREATED_AT, Instant.now().toString())
                    put(CART_FILE_KEY_PAYLOAD, serializeCart(cart))
                }
            val bytes = root.toString(2).toByteArray(Charsets.UTF_8)
            val written = context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            if (written == null) CartFileResult.Failure(FileFailure.CANNOT_OPEN) else CartFileResult.Success
        } catch (e: IOException) {
            CartFileResult.Failure(FileFailure.READ_WRITE, e.message)
        } catch (e: SecurityException) {
            CartFileResult.Failure(FileFailure.NO_PERMISSION, e.message)
        }

    fun import(uri: Uri): CartFileResult =
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null) {
                CartFileResult.Failure(FileFailure.CANNOT_OPEN)
            } else {
                decode(JSONObject(String(bytes, Charsets.UTF_8)))
            }
        } catch (e: IOException) {
            CartFileResult.Failure(FileFailure.READ_WRITE, e.message)
        } catch (e: JSONException) {
            CartFileResult.Failure(FileFailure.DAMAGED, e.message)
        } catch (e: SecurityException) {
            CartFileResult.Failure(FileFailure.NO_PERMISSION, e.message)
        }

    // The cart in a cart file's JSON, or why it isn't one.
    private fun decode(root: JSONObject): CartFileResult {
        val version = root.optInt(BACKUP_KEY_VERSION, -1)
        return when {
            version != CART_FILE_SCHEMA_VERSION ->
                CartFileResult.Failure(
                    FileFailure.UNSUPPORTED_VERSION,
                    "Unsupported cart version: $version",
                )
            // `type` is checked so a full-app backup dropped in by mistake
            // is rejected before it's parsed as a cart.
            root.optString(CART_FILE_KEY_TYPE) != CART_FILE_TYPE -> CartFileResult.Failure(FileFailure.NOT_A_CART, "Not a cart file")
            else ->
                parseCart(root.optJSONObject(CART_FILE_KEY_PAYLOAD))?.let(CartFileResult::Loaded)
                    ?: CartFileResult.Failure(FileFailure.DAMAGED, "Malformed cart payload")
        }
    }
}
