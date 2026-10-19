package com.eliormachlev.currencix.repository

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.model.SavedCart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CartExporterTest {
    @get:Rule val files = TemporaryFolder()

    private val exporter = CartExporter(ApplicationProvider.getApplicationContext<Application>())
    private val cart =
        SavedCart(
            id = "cart-1",
            name = "Groceries",
            currency = "USD",
            items = listOf(CartItem(id = "1", name = "Coffee", expression = "4.50", pinned = true)),
            createdAt = 1_710_000_000_000,
            destinationCurrency = "ILS",
        )

    private fun fileWith(json: String): Uri = Uri.fromFile(files.newFile().apply { writeText(json) })

    // The technical detail of a refusal; the user sees only its reason.
    private fun failure(json: String): String? = (exporter.import(fileWith(json)) as CartFileResult.Failure).detail

    private fun reason(json: String): FileFailure = (exporter.import(fileWith(json)) as CartFileResult.Failure).reason

    @Test
    fun `an exported cart imports back unchanged`() {
        val uri = Uri.fromFile(files.newFile())
        assertEquals(CartFileResult.Success, exporter.export(uri, cart))
        assertEquals(CartFileResult.Loaded(cart), exporter.import(uri))
    }

    @Test
    fun `a file of another version is refused`() {
        assertEquals("Unsupported cart version: 99", failure("""{"version":99,"type":"cart","cart":{}}"""))
        assertEquals("Unsupported cart version: -1", failure("""{"type":"cart"}"""))
    }

    @Test
    fun `a file that isn't a cart is refused before its payload is read`() {
        assertEquals("Not a cart file", failure("""{"version":$CART_FILE_SCHEMA_VERSION,"type":"backup","cart":{}}"""))
        assertEquals(FileFailure.NOT_A_CART, reason("""{"version":$CART_FILE_SCHEMA_VERSION,"type":"backup","cart":{}}"""))
    }

    @Test
    fun `a cart file without a cart is refused`() {
        assertEquals("Malformed cart payload", failure("""{"version":$CART_FILE_SCHEMA_VERSION,"type":"cart"}"""))
        assertEquals(FileFailure.DAMAGED, reason("""{"version":$CART_FILE_SCHEMA_VERSION,"type":"cart"}"""))
    }

    @Test
    fun `unreadable input is a failure, not a crash`() {
        assertTrue(exporter.import(fileWith("not json")) is CartFileResult.Failure)
        assertTrue(exporter.import(Uri.fromFile(File(files.root, "missing.json"))) is CartFileResult.Failure)
    }
}
