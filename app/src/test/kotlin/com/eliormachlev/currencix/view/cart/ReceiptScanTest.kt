package com.eliormachlev.currencix.view.cart

import com.eliormachlev.currencix.ocr.TessLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Locale

// Nothing is downloaded for reading receipts until the user says yes to it
// by name: the scan asks first, once per language, and remembers the answer.
class ReceiptScanTest {
    private val installed = mutableSetOf<TessLanguage>()
    private val answers = mutableMapOf<TessLanguage, Boolean>()
    private val untils = mutableMapOf<TessLanguage, Instant?>()
    private var recognizer = true
    private var setupsStarted = 0
    private var photoChoices = 0

    private fun scan(local: TessLanguage? = TessLanguage.HEBREW) =
        ReceiptScan().apply {
            candidates = listOfNotNull(local)
            isInstalled = { it in installed }
            isRecognizerHere = { recognizer }
            answerFor = { answers[it] }
            answer = { language, allowed, until ->
                answers[language] = allowed
                untils[language] = until
            }
            startSetup = { setupsStarted++ }
        }

    @Test
    fun `the first scan names what it would download, the app language unticked, and starts nothing`() {
        recognizer = false
        val scan = scan()
        scan.begin { photoChoices++ }
        assertEquals(ReceiptSetup.Offer(reader = true, english = true, language = TessLanguage.HEBREW), scan.setup)
        assertEquals(0, setupsStarted)
        assertEquals(0, photoChoices)
        assertTrue(answers.isEmpty())
    }

    @Test
    fun `accepting without the tick downloads English only and remembers the no`() {
        val scan = scan()
        scan.begin { photoChoices++ }
        scan.acceptSetup(withLanguage = false)
        assertEquals(mapOf(TessLanguage.ENGLISH to true, TessLanguage.HEBREW to false), answers)
        assertEquals(1, setupsStarted)
    }

    @Test
    fun `closing the first offer downloads nothing and decides nothing`() {
        val scan = scan()
        scan.begin { photoChoices++ }
        scan.dismissSetup()
        assertNull(scan.setup)
        assertEquals(0, setupsStarted)
        assertTrue(answers.isEmpty())
    }

    @Test
    fun `with English here, a language not asked about gets its own question`() {
        installed += TessLanguage.ENGLISH
        val scan = scan()
        scan.begin { photoChoices++ }
        assertEquals(ReceiptSetup.LanguageOffer(TessLanguage.HEBREW), scan.setup)
        scan.answerLanguage(download = false)
        assertEquals(false, answers[TessLanguage.HEBREW])
        assertEquals("the scan goes on in English", 1, photoChoices)

        scan.begin { photoChoices++ }
        assertNull("asked once", scan.setup)
        assertEquals(2, photoChoices)
    }

    @Test
    fun `a language said no to can be offered again from the review`() {
        installed += TessLanguage.ENGLISH
        answers[TessLanguage.HEBREW] = false
        val scan = scan()
        scan.languageDeclined = TessLanguage.HEBREW
        scan.lines = emptyList()
        scan.offerLanguageAgain()
        assertEquals(ReceiptSetup.LanguageOffer(TessLanguage.HEBREW), scan.setup)
        scan.answerLanguage(download = true)
        assertEquals(true, answers[TessLanguage.HEBREW])
        assertNull(scan.languageDeclined)
        assertTrue(scan.languagePending)
        assertEquals("no new scan follows", 0, photoChoices)
    }

    @Test
    fun `English as the app language asks nothing once English is here`() {
        installed += TessLanguage.ENGLISH
        val scan = scan(local = null)
        scan.begin { photoChoices++ }
        assertNull(scan.setup)
        assertEquals(1, photoChoices)
    }

    @Test
    fun `the trip's language comes first, kept until the trip ends`() {
        installed += TessLanguage.ENGLISH
        val tripEnd = Instant.parse("2026-11-14T14:59:59Z")
        val scan =
            scan().apply {
                candidates = listOf(TessLanguage.KOREAN, TessLanguage.HEBREW)
                untilFor = { if (it == TessLanguage.KOREAN) tripEnd else null }
            }
        scan.begin { photoChoices++ }
        assertEquals(ReceiptSetup.LanguageOffer(TessLanguage.KOREAN, tripEnd), scan.setup)
        scan.answerLanguage(download = true)
        assertEquals(tripEnd, untils[TessLanguage.KOREAN])

        // Korean answered, Hebrew is next.
        scan.begin { photoChoices++ }
        assertEquals(ReceiptSetup.LanguageOffer(TessLanguage.HEBREW, null), scan.setup)
    }

    @Test
    fun `every language has a size to show and a name in the user's language`() {
        TessLanguage.entries.forEach { assertTrue(it.name, it.bytes > 0) }
        assertEquals("Hebrew", TessLanguage.HEBREW.displayName(Locale.ENGLISH))
        assertEquals("Chinese (Simplified)", TessLanguage.CHINESE_SIMPLIFIED.displayName(Locale.ENGLISH))
    }
}
