package com.eliormachlev.currencix.repository

import android.util.Base64
import com.google.crypto.tink.subtle.AesGcmJce
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.json.JSONObject
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

// Encryption block keys.
private const val ENC_KDF = "kdf"
private const val ENC_ITERATIONS = "iterations"
private const val ENC_SALT = "salt"
private const val ENC_CIPHER = "cipher"
private const val ENC_IV = "iv"
private const val ENC_CIPHERTEXT = "ciphertext"

// Argon2id-only block keys.
private const val ENC_MEMORY_KIB = "memoryKib"
private const val ENC_PARALLELISM = "parallelism"

// KDF & cipher identifiers stored verbatim in the file so a future reader can
// reject algorithms it doesn't understand instead of silently mis-decrypting.
// New exports use ARGON2ID_ID; PBKDF2_ID is retained only as a reader path
// for files exported by an earlier revision.
private const val ARGON2ID_ID = "ARGON2ID-v1.3"
private const val PBKDF2_ID = "PBKDF2-HMAC-SHA256"
private const val CIPHER_ID = "AES-256-GCM"

private const val KEY_LENGTH_BITS = 256
private const val KEY_LENGTH_BYTES = KEY_LENGTH_BITS / 8

// Argon2id parameters. OWASP 2023 gives several equivalent-strength profiles;
// we pick m=32 MiB, t=3, p=1 as a balance between mid-range Android RAM
// headroom and cost to an offline attacker with GPU/ASIC. Memory-hardness is
// the property that resists quantum speedups (Grover parallelizes compute,
// not memory bandwidth).
private val DEFAULT_ARGON2 = Argon2Cost(memoryKib = 32 * 1024, iterations = 3, parallelism = 1)

private const val SALT_LENGTH_BYTES = 32

// AES-GCM's nonce: 96 bits, which is what Tink generates and expects.
private const val IV_LENGTH_BYTES = 12

private val NO_ASSOCIATED_DATA = ByteArray(0)

/** The supplied password doesn't open the backup (or the file was altered): its authentication tag fails. */
internal class WrongPasswordException(
    cause: Throwable,
) : GeneralSecurityException("The password does not open this backup", cause)

private class Argon2Cost(
    val memoryKib: Int,
    val iterations: Int,
    val parallelism: Int,
)

/**
 * Password-based encryption of a backup's content: an AES-256-GCM key
 * derived from the password with Argon2id, stored as a self-describing JSON
 * block (KDF and its parameters, salt, nonce, ciphertext).
 *
 * The cipher itself is Tink's: it draws a fresh random nonce for every
 * encryption, so nonce handling isn't this class's to get wrong. A fresh
 * salt per export also means a fresh key per export.
 */
internal class BackupCrypto(
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    /** [plaintext] encrypted under [password], as the block to store in the file. */
    @Throws(GeneralSecurityException::class)
    fun encrypt(
        plaintext: ByteArray,
        password: CharArray,
    ): JSONObject {
        val salt = ByteArray(SALT_LENGTH_BYTES).also(secureRandom::nextBytes)
        // Tink returns nonce + ciphertext (with its tag) as one array; the
        // file keeps them in two fields.
        val sealed = withKey(deriveKeyArgon2id(password, salt, DEFAULT_ARGON2)) { it.encrypt(plaintext, NO_ASSOCIATED_DATA) }
        return JSONObject().apply {
            put(ENC_KDF, ARGON2ID_ID)
            put(ENC_MEMORY_KIB, DEFAULT_ARGON2.memoryKib)
            put(ENC_ITERATIONS, DEFAULT_ARGON2.iterations)
            put(ENC_PARALLELISM, DEFAULT_ARGON2.parallelism)
            put(ENC_SALT, base64(salt))
            put(ENC_CIPHER, CIPHER_ID)
            put(ENC_IV, base64(sealed.copyOfRange(0, IV_LENGTH_BYTES)))
            put(ENC_CIPHERTEXT, base64(sealed.copyOfRange(IV_LENGTH_BYTES, sealed.size)))
        }
    }

    /**
     * The content of an encryption [block], opened with [password].
     *
     * @throws WrongPasswordException when the password (or the file) fails authentication
     * @throws GeneralSecurityException when the block uses something this version can't read
     */
    @Throws(GeneralSecurityException::class)
    fun decrypt(
        block: JSONObject,
        password: CharArray,
    ): ByteArray {
        val cipherId = block.optString(ENC_CIPHER)
        requireSupported(cipherId == CIPHER_ID) { "Unsupported cipher: $cipherId" }
        val iv = block.bytes(ENC_IV)
        requireSupported(iv.size == IV_LENGTH_BYTES) { "Unsupported nonce length: ${iv.size}" }
        val sealed = iv + block.bytes(ENC_CIPHERTEXT)
        return withKey(deriveKey(block, password)) { cipher ->
            try {
                cipher.decrypt(sealed, NO_ASSOCIATED_DATA)
            } catch (e: AEADBadTagException) {
                throw WrongPasswordException(e)
            }
        }
    }

    // Runs [use] with the cipher for [key], then wipes the key.
    private inline fun <T> withKey(
        key: ByteArray,
        use: (AesGcmJce) -> T,
    ): T =
        try {
            use(AesGcmJce(key))
        } finally {
            key.fill(0)
        }

    // The key for [block], by whichever derivation the block names.
    private fun deriveKey(
        block: JSONObject,
        password: CharArray,
    ): ByteArray {
        val salt = block.bytes(ENC_SALT)
        return when (val kdf = block.optString(ENC_KDF)) {
            ARGON2ID_ID -> deriveKeyArgon2id(password, salt, argon2Cost(block))
            PBKDF2_ID -> deriveKeyPbkdf2(password, salt, block.optInt(ENC_ITERATIONS, -1))
            else -> unsupported("Unsupported KDF: $kdf")
        }
    }

    private fun argon2Cost(block: JSONObject): Argon2Cost {
        val cost =
            Argon2Cost(
                memoryKib = block.optInt(ENC_MEMORY_KIB, -1),
                iterations = block.optInt(ENC_ITERATIONS, -1),
                parallelism = block.optInt(ENC_PARALLELISM, -1),
            )
        requireSupported(minOf(cost.memoryKib, cost.iterations, cost.parallelism) > 0) { "Invalid Argon2 parameters" }
        return cost
    }

    /**
     * Argon2id is memory-hard, which is the property that neutralises the
     * √N speedup Grover's algorithm gives a quantum attacker against a
     * password-guessing loop — parallelism gains from Grover don't help
     * when memory bandwidth dominates the cost of each guess.
     */
    private fun deriveKeyArgon2id(
        password: CharArray,
        salt: ByteArray,
        cost: Argon2Cost,
    ): ByteArray {
        val passwordBytes = password.toUtf8Bytes()
        try {
            val params =
                Argon2Parameters
                    .Builder(Argon2Parameters.ARGON2_id)
                    .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                    .withSalt(salt)
                    .withMemoryAsKB(cost.memoryKib)
                    .withIterations(cost.iterations)
                    .withParallelism(cost.parallelism)
                    .build()
            val generator = Argon2BytesGenerator().apply { init(params) }
            return ByteArray(KEY_LENGTH_BYTES).also { generator.generateBytes(passwordBytes, it) }
        } finally {
            passwordBytes.fill(0)
        }
    }

    private fun deriveKeyPbkdf2(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
    ): ByteArray {
        requireSupported(iterations > 0) { "Invalid iteration count" }
        val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * UTF-8 encode without going through String (which would linger in the
     * String pool). CharArray → ByteArray via NIO CharBuffer.
     */
    private fun CharArray.toUtf8Bytes(): ByteArray {
        val byteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(this))
        return ByteArray(byteBuffer.remaining()).also(byteBuffer::get)
    }

    private fun base64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun JSONObject.bytes(key: String): ByteArray {
        val encoded = optString(key)
        requireSupported(encoded.isNotEmpty()) { "Missing $key" }
        return Base64.decode(encoded, Base64.DEFAULT)
    }
}

// A block this version can't read is a security failure, not a crash.
private fun unsupported(message: String): Nothing = throw GeneralSecurityException(message)

private inline fun requireSupported(
    supported: Boolean,
    message: () -> String,
) {
    if (!supported) unsupported(message())
}
