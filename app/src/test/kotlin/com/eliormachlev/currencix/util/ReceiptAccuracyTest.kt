package com.eliormachlev.currencix.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

private const val CORPUS_DIR = "receipts"
private const val EXPECTED_MARKER = "=== expected"
private const val ITEM_SEPARATOR = " | "

// The share of a language's items that must come out right (see [Score.accuracy]).
// Every language is held to all of them; a format the parser can't read yet is
// listed here with the floor it is at, to be raised when it is fixed.
private const val DEFAULT_FLOOR = 1.0
private val KNOWN_GAPS =
    mapOf(
        // Prices without cents ("Bread 12") aren't read: the parser needs a decimal part.
        "en_nocents" to 0.0,
    )

/**
 * How well [ReceiptParser] reads a fixed set of synthetic receipts (no real
 * shop or person), in several languages, number formats and scripts. Each
 * file in `src/test/resources/receipts` is the text OCR gives for a receipt
 * and, under `=== expected`, the items it should give ("name | price"). The
 * score is per language (the file name up to its first "-"), and the table
 * is in the test's output so a change shows what it did to each one.
 *
 * This measures the parser on text. How well the OCR engine reads a photo
 * into that text is not measured here: it needs the native engine and real
 * images.
 */
class ReceiptAccuracyTest {
    private class Sample(
        val language: String,
        val text: String,
        val expected: List<String>,
    )

    private class Score(
        var expected: Int = 0,
        var correct: Int = 0,
        var spurious: Int = 0,
    ) {
        // Right items out of those that should be there plus those that shouldn't.
        val accuracy get() = if (expected + spurious == 0) 1.0 else correct.toDouble() / (expected + spurious)
    }

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("$CORPUS_DIR/$name")) { "Missing receipt sample $name" }
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    // The samples listed in index.txt, so a file that isn't listed isn't silently skipped or found by a directory walk.
    private fun samples(): List<Sample> =
        resource("index.txt").lines().filter(String::isNotBlank).map { name ->
            val body = resource("$name.txt")
            val text = body.substringBefore(EXPECTED_MARKER)
            val expected =
                body
                    .substringAfter(EXPECTED_MARKER)
                    .lines()
                    .map(String::trim)
                    .filter(String::isNotEmpty)
            Sample(name.substringBefore('-'), text, expected)
        }

    private fun scoreOf(sample: Sample): Score {
        val found = ReceiptParser.parse(sample.text).map { "${it.name}$ITEM_SEPARATOR${it.price.toPlainString()}" }.toMutableList()
        val score = Score(expected = sample.expected.size)
        sample.expected.forEach { item -> if (found.remove(item)) score.correct++ }
        score.spurious = found.size
        return score
    }

    private fun scores(): Map<String, Score> {
        val byLanguage = sortedMapOf<String, Score>()
        samples().forEach { sample ->
            val score = scoreOf(sample)
            val total = byLanguage.getOrPut(sample.language) { Score() }
            total.expected += score.expected
            total.correct += score.correct
            total.spurious += score.spurious
        }
        return byLanguage
    }

    private fun report(scores: Map<String, Score>): String =
        scores.entries.joinToString("\n", prefix = "Receipt parsing accuracy\n") { (language, s) ->
            "  %-12s %5.1f%%  (%d of %d items right, %d extra)".format(
                Locale.ROOT,
                language,
                s.accuracy * 100,
                s.correct,
                s.expected,
                s.spurious,
            )
        }

    @Test
    fun `the corpus covers several languages and every sample has expected items`() {
        val samples = samples()
        assertTrue("Only ${samples.map { it.language }.distinct().size} languages", samples.map { it.language }.distinct().size >= 8)
        samples.forEach { assertTrue("A sample for ${it.language} has no expected items", it.expected.isNotEmpty()) }
    }

    @Test
    fun `each language reads its receipts at least as well as it did`() {
        val scores = scores()
        val report = report(scores)
        println(report)
        val below = scores.filter { (language, s) -> s.accuracy < (KNOWN_GAPS[language] ?: DEFAULT_FLOOR) }
        assertEquals("Below its floor:\n$report", emptyMap<String, Score>().keys, below.keys)
    }
}
