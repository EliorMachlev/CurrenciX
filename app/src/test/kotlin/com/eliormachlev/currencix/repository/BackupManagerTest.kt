package com.eliormachlev.currencix.repository

import android.app.Application
import android.net.Uri
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.prefStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

private const val PASSWORD = "correct horse"
private val PROBE = stringPreferencesKey("probe")

/**
 * Backups round-trip, plain and encrypted, and files written by earlier
 * versions still open: the two fixtures under `resources/backup` were
 * produced before the cipher moved to Tink (Argon2id and the older PBKDF2
 * key derivation), each holding `probe` under the `prefs` namespace.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackupManagerTest {
    @get:Rule val files = TemporaryFolder()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private var restarts = 0
    private val manager = BackupManager(app) { restarts++ }
    private val prefs = PersistenceKey.APP.prefStore(app)

    private fun setProbe(value: String) = runBlocking { prefs.editAndAwait { this[PROBE] = value } }

    private fun probe(): String? = prefs.snapshot()[PROBE]

    private fun newFile(): Uri = Uri.fromFile(files.newFile())

    private fun fileWith(json: String): Uri = Uri.fromFile(files.newFile().apply { writeText(json) })

    private fun fixture(name: String): Uri = fileWith(checkNotNull(javaClass.getResource("/backup/$name")).readText())

    private fun failure(result: BackupResult): String = (result as BackupResult.Failure).message

    @Before
    fun reset() {
        setProbe("before")
    }

    @Test
    fun `a plain backup restores what was exported, then restarts`() {
        val uri = newFile()
        assertEquals(BackupResult.Success, manager.export(uri))
        assertFalse(manager.isEncrypted(uri))
        setProbe("changed since")

        assertEquals(BackupResult.Success, manager.import(uri))
        assertEquals("before", probe())
        assertEquals(1, restarts)
    }

    @Test
    fun `an encrypted backup hides its content and restores with the password`() {
        val uri = newFile()
        assertEquals(BackupResult.Success, manager.export(uri, PASSWORD.toCharArray()))
        val file = JSONObject(File(checkNotNull(uri.path)).readText())
        assertTrue(manager.isEncrypted(uri))
        assertFalse("no plaintext namespaces", file.has("namespaces"))
        assertFalse("the content isn't readable", "before" in file.toString())
        setProbe("changed since")

        assertEquals(BackupResult.Success, manager.import(uri, PASSWORD.toCharArray()))
        assertEquals("before", probe())
    }

    @Test
    fun `two exports of the same data never share a salt or nonce`() {
        val first = newFile().also { manager.export(it, PASSWORD.toCharArray()) }
        val second = newFile().also { manager.export(it, PASSWORD.toCharArray()) }
        val encryption = { uri: Uri -> JSONObject(File(checkNotNull(uri.path)).readText()).getJSONObject("encryption") }

        assertFalse(encryption(first).getString("salt") == encryption(second).getString("salt"))
        assertFalse(encryption(first).getString("iv") == encryption(second).getString("iv"))
        assertFalse(encryption(first).getString("ciphertext") == encryption(second).getString("ciphertext"))
    }

    @Test
    fun `an encrypted backup asks for a password, and rejects a wrong one`() {
        val uri = newFile().also { manager.export(it, PASSWORD.toCharArray()) }
        setProbe("changed since")

        assertEquals(BackupResult.PasswordRequired, manager.import(uri))
        assertEquals(BackupResult.PasswordRequired, manager.import(uri, CharArray(0)))
        assertEquals(BackupResult.WrongPassword, manager.import(uri, "wrong".toCharArray()))
        assertEquals("nothing was restored", "changed since", probe())
        assertEquals(0, restarts)
    }

    @Test
    fun `a tampered ciphertext is rejected like a wrong password`() {
        val uri = newFile().also { manager.export(it, PASSWORD.toCharArray()) }
        val file = File(checkNotNull(uri.path))
        val root = JSONObject(file.readText())
        val encryption = root.getJSONObject("encryption")
        val ciphertext = encryption.getString("ciphertext")
        encryption.put("ciphertext", (if (ciphertext.first() == 'A') "B" else "A") + ciphertext.drop(1))
        file.writeText(root.toString())

        assertEquals(BackupResult.WrongPassword, manager.import(uri, PASSWORD.toCharArray()))
    }

    @Test
    fun `passwords are wiped once used`() {
        val exportPassword = PASSWORD.toCharArray()
        val uri = newFile().also { manager.export(it, exportPassword) }
        val importPassword = PASSWORD.toCharArray()
        manager.import(uri, importPassword)

        assertTrue(exportPassword.all { it == '\u0000' })
        assertTrue(importPassword.all { it == '\u0000' })
    }

    @Test
    fun `a backup encrypted by an earlier version still opens`() {
        assertEquals(BackupResult.Success, manager.import(fixture("argon2id-v1.json"), PASSWORD.toCharArray()))
        assertEquals("golden", probe())
    }

    @Test
    fun `a backup from the PBKDF2 era still opens`() {
        assertEquals(BackupResult.Success, manager.import(fixture("pbkdf2-legacy.json"), PASSWORD.toCharArray()))
        assertEquals("legacy", probe())
    }

    @Test
    fun `files that aren't a usable backup fail with a reason`() {
        assertEquals("Unsupported backup version: 7", failure(manager.import(fileWith("""{"version":7}"""))))
        assertEquals("Unsupported backup version: -1", failure(manager.import(fileWith("{}"))))
        assertTrue(manager.import(fileWith("""{"version":$BACKUP_SCHEMA_VERSION}""")) is BackupResult.Failure)
        assertTrue(manager.import(fileWith("not json")) is BackupResult.Failure)
        assertTrue(manager.import(Uri.fromFile(File(files.root, "missing.json"))) is BackupResult.Failure)
        assertEquals("before", probe())
        assertEquals(0, restarts)
    }

    @Test
    fun `an encryption block this version can't read is refused`() {
        // 12 zero bytes: the nonce length the cipher takes.
        val nonce = "AAAAAAAAAAAAAAAA"
        val block = { kdf: String, cipher: String, iv: String ->
            """{"version":$BACKUP_SCHEMA_VERSION,"encryption":{"kdf":"$kdf","cipher":"$cipher","iterations":1,""" +
                """"salt":"AAAA","iv":"$iv","ciphertext":"AAAA"}}"""
        }
        val refusal = { json: String -> failure(manager.import(fileWith(json), "x".toCharArray())) }

        assertEquals("Unsupported cipher: ROT13", refusal(block("ARGON2ID-v1.3", "ROT13", nonce)))
        assertEquals("Unsupported KDF: MD5", refusal(block("MD5", "AES-256-GCM", nonce)))
        assertEquals("Invalid Argon2 parameters", refusal(block("ARGON2ID-v1.3", "AES-256-GCM", nonce)))
        assertEquals("Unsupported nonce length: 3", refusal(block("ARGON2ID-v1.3", "AES-256-GCM", "AAAA")))
        assertEquals("Missing iv", refusal(block("ARGON2ID-v1.3", "AES-256-GCM", "")))
    }
}
