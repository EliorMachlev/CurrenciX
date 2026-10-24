package com.eliormachlev.currencix.repository

import android.app.Application
import android.net.Uri
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.CurrencyPair
import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.model.RateAlert
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.model.Trip
import com.eliormachlev.currencix.repository.json.parseJsonObject
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.prefStore
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.runBlocking
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
import java.math.BigDecimal

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

    // The technical detail of a refusal; the user sees only its reason.
    private fun failure(result: BackupResult): String? = (result as BackupResult.Failure).detail

    private fun reason(result: BackupResult): FileFailure = (result as BackupResult.Failure).reason

    @Before
    fun reset() {
        setProbe("before")
    }

    @Test
    fun `a plain backup restores what was exported, then restarts`() =
        runBlocking<Unit> {
            val uri = newFile()
            assertEquals(BackupResult.Success, manager.export(uri))
            assertFalse(manager.isEncrypted(uri))
            setProbe("changed since")

            assertEquals(BackupResult.Success, manager.import(uri))
            assertEquals("before", probe())
            assertEquals(1, restarts)
        }

    @Test
    fun `carts, trips, fees and rate alerts go into a backup and come back from it`() =
        runBlocking<Unit> {
            val carts = CartStore(app)
            val fees = FeeStore(app)
            val alerts = RateAlertStore(app)
            val alert = RateAlert.towards(CurrencyPair(Currency.USD, Currency.ILS), BigDecimal("3.5"), BigDecimal("3.6"))
            val cart = SavedCart("c1", "Market", "EUR", listOf(CartItem("i1", "bread", "2.5", quantity = 2)), createdAt = 1L, tripId = "t1")
            val trip = Trip("t1", "Rome", Currency.ILS, BigDecimal("900"))
            val fee = Fee.SpecificPair("f1", "Card", BigDecimal("1.5"), "EUR", "ILS", bothWays = true)
            carts.currentCart.set(cart.copy(id = "working"))
            carts.savedCarts.set(listOf(cart))
            carts.trips.set(listOf(trip))
            fees.fees.set(persistentListOf(fee))
            alerts.alerts.set(listOf(alert))
            val uri = newFile().also { manager.export(it, PASSWORD.toCharArray()) }

            carts.currentCart.set(null)
            carts.savedCarts.set(emptyList())
            carts.trips.set(emptyList())
            fees.fees.set(persistentListOf())
            alerts.alerts.set(emptyList())
            assertEquals(BackupResult.Success, manager.import(uri, PASSWORD.toCharArray()))

            assertEquals(cart.copy(id = "working"), carts.currentCart.get())
            assertEquals(listOf(cart), carts.savedCarts.get())
            assertEquals(listOf(trip), carts.trips.get())
            assertEquals(listOf(fee), fees.fees.get())
            assertEquals(listOf(alert), alerts.alerts.get())
        }

    @Test
    fun `an encrypted backup hides its content and restores with the password`() =
        runBlocking<Unit> {
            val uri = newFile()
            assertEquals(BackupResult.Success, manager.export(uri, PASSWORD.toCharArray()))
            val text = File(checkNotNull(uri.path)).readText()
            assertTrue(manager.isEncrypted(uri))
            assertFalse("no plaintext namespaces", parseJsonObject(text).has("namespaces"))
            assertFalse("the content isn't readable", "before" in text)
            setProbe("changed since")

            assertEquals(BackupResult.Success, manager.import(uri, PASSWORD.toCharArray()))
            assertEquals("before", probe())
        }

    @Test
    fun `two exports of the same data never share a salt or nonce`() =
        runBlocking<Unit> {
            val first = newFile().also { manager.export(it, PASSWORD.toCharArray()) }
            val second = newFile().also { manager.export(it, PASSWORD.toCharArray()) }
            val encryption = { uri: Uri -> checkNotNull(parseJsonObject(File(checkNotNull(uri.path)).readText()).optObject("encryption")) }

            listOf("salt", "iv", "ciphertext").forEach { field ->
                assertFalse(field, encryption(first).optString(field) == encryption(second).optString(field))
            }
        }

    @Test
    fun `an encrypted backup asks for a password, and rejects a wrong one`() =
        runBlocking<Unit> {
            val uri = newFile().also { manager.export(it, PASSWORD.toCharArray()) }
            setProbe("changed since")

            assertEquals(BackupResult.PasswordRequired, manager.import(uri))
            assertEquals(BackupResult.PasswordRequired, manager.import(uri, CharArray(0)))
            assertEquals(BackupResult.WrongPassword, manager.import(uri, "wrong".toCharArray()))
            assertEquals("nothing was restored", "changed since", probe())
            assertEquals(0, restarts)
        }

    @Test
    fun `a tampered ciphertext is rejected like a wrong password`() =
        runBlocking<Unit> {
            val uri = newFile().also { manager.export(it, PASSWORD.toCharArray()) }
            val file = File(checkNotNull(uri.path))
            val ciphertext = checkNotNull(parseJsonObject(file.readText()).optObject("encryption")).optString("ciphertext")
            val tampered = (if (ciphertext.first() == 'A') "B" else "A") + ciphertext.drop(1)
            file.writeText(file.readText().replace(ciphertext, tampered))

            assertEquals(BackupResult.WrongPassword, manager.import(uri, PASSWORD.toCharArray()))
        }

    @Test
    fun `passwords are wiped once used`() =
        runBlocking<Unit> {
            val exportPassword = PASSWORD.toCharArray()
            val uri = newFile().also { manager.export(it, exportPassword) }
            val importPassword = PASSWORD.toCharArray()
            manager.import(uri, importPassword)

            assertTrue(exportPassword.all { it == '\u0000' })
            assertTrue(importPassword.all { it == '\u0000' })
        }

    @Test
    fun `a backup encrypted by an earlier version still opens`() =
        runBlocking<Unit> {
            assertEquals(BackupResult.Success, manager.import(fixture("argon2id-v1.json"), PASSWORD.toCharArray()))
            assertEquals("golden", probe())
        }

    @Test
    fun `a backup from the PBKDF2 era still opens`() =
        runBlocking<Unit> {
            assertEquals(BackupResult.Success, manager.import(fixture("pbkdf2-legacy.json"), PASSWORD.toCharArray()))
            assertEquals("legacy", probe())
        }

    @Test
    fun `files that aren't a usable backup fail with a reason`() =
        runBlocking<Unit> {
            assertEquals("Unsupported backup version: 7", failure(manager.import(fileWith("""{"version":7}"""))))
            assertEquals(FileFailure.UNSUPPORTED_VERSION, reason(manager.import(fileWith("""{"version":7}"""))))
            assertEquals("Unsupported backup version: -1", failure(manager.import(fileWith("{}"))))
            assertTrue(manager.import(fileWith("""{"version":$BACKUP_SCHEMA_VERSION}""")) is BackupResult.Failure)
            assertTrue(manager.import(fileWith("not json")) is BackupResult.Failure)
            assertTrue(manager.import(Uri.fromFile(File(files.root, "missing.json"))) is BackupResult.Failure)
            assertEquals("before", probe())
            assertEquals(0, restarts)
        }

    @Test
    fun `an encryption block this version can't read is refused`() =
        runBlocking<Unit> {
            // 12 zero bytes: the nonce length the cipher takes.
            val nonce = "AAAAAAAAAAAAAAAA"
            val block = { kdf: String, cipher: String, iv: String ->
                """{"version":$BACKUP_SCHEMA_VERSION,"encryption":{"kdf":"$kdf","cipher":"$cipher","iterations":1,""" +
                    """"salt":"AAAA","iv":"$iv","ciphertext":"AAAA"}}"""
            }
            val refusal: suspend (String) -> String? = { json -> failure(manager.import(fileWith(json), "x".toCharArray())) }

            assertEquals("Unsupported cipher: ROT13", refusal(block("ARGON2ID-v1.3", "ROT13", nonce)))
            assertEquals("Unsupported KDF: MD5", refusal(block("MD5", "AES-256-GCM", nonce)))
            assertEquals("Invalid Argon2 parameters", refusal(block("ARGON2ID-v1.3", "AES-256-GCM", nonce)))
            assertEquals("Unsupported nonce length: 3", refusal(block("ARGON2ID-v1.3", "AES-256-GCM", "AAAA")))
            assertEquals("Missing iv", refusal(block("ARGON2ID-v1.3", "AES-256-GCM", "")))
            assertEquals(FileFailure.DECRYPTION, reason(manager.import(fileWith(block("MD5", "AES-256-GCM", nonce)), "x".toCharArray())))
        }
}
