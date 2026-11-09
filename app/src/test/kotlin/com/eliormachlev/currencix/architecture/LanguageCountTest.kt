package com.eliormachlev.currencix.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The number of languages is said in the README and its badge, the website,
 * the store listing and a few docs. Written by hand it drifts (the store
 * listing once said 33), so the count comes from the `res/values-*` folders
 * and every place that states it is checked against that.
 */
class LanguageCountTest {
    private val root = repositoryRoot()

    // English (the default `values`) plus one per `values-<language>` folder: `night` is a theme,
    // and `values-en-rGB` only sets British formats, so neither is a language.
    private val languages: Int =
        1 + File(root, "app/src/main/res").listFiles().orEmpty().count { it.isDirectory && isTranslation(it.name) }

    private fun isTranslation(folder: String): Boolean =
        folder.startsWith("values-") && folder != "values-night" && !folder.startsWith("values-en-")

    // Listed one by one: a page that moves or is renamed fails the test (it can't be read) rather than dropping out of it.
    private fun statedIn(): List<File> = STATED_IN.map { File(root, it) }

    // A figure and what it counts: all languages, or the translations besides English.
    private data class Claim(
        val file: String,
        val figure: Int,
        val counts: String,
    )

    private fun claims(): List<Claim> =
        statedIn().flatMap { file ->
            val text = file.readText()
            val name = file.relativeTo(root).path
            CLAIM.findAll(text).map { Claim(name, it.groupValues[1].toInt(), it.groupValues[2]) }.toList() +
                LABELLED.flatMap { it.findAll(text) }.map { Claim(name, it.groupValues[1].toInt(), "languages") }.toList()
        }

    @Test
    fun `the languages the app ships are found`() {
        assertTrue("Only $languages found: is the res folder where it was?", languages > MIN_LANGUAGES)
    }

    @Test
    fun `every stated number of languages is the number of res folders`() {
        val found = claims()
        assertTrue("No language count found in $LANGUAGE_FILES: did the wording change?", found.isNotEmpty())
        val files = found.map { it.file }.toSet()
        assertEquals("These files no longer state a language count:", emptyList<String>(), STATED_IN.filterNot { it in files })
        val wrong =
            found.filter { claim ->
                val expected = if (claim.counts == "languages") languages else languages - 1
                claim.figure != expected
            }
        assertEquals(
            "Languages are counted from res/values-* ($languages with English). Update these:\n" + wrong.joinToString("\n"),
            emptyList<Claim>(),
            wrong,
        )
    }

    private companion object {
        const val MIN_LANGUAGES = 10
        const val LANGUAGE_FILES = "the README, website, store listing and docs"

        val STATED_IN =
            listOf(
                "README.md",
                "docs/index.html",
                "docs/features.html",
                "docs/privacy.html",
                "docs/providers.html",
                "docs/llms-full.txt",
                "docs/markDown/engineering/contributing.md",
                "docs/markDown/legal/accessibility-statement.md",
                "fastlane/metadata/android/en-US/full_description.txt",
            )

        // "32 languages", "31 translations" and "31 languages besides English".
        val CLAIM = Regex("""\b(\d+) (languages(?! besides English)|translations|languages besides English)""")

        // The badge, the website's "Languages 32" row and its llms-full.txt line.
        val LABELLED =
            listOf(
                Regex("""badge/languages-(\d+)-"""),
                Regex("""Languages</span><span class="value">(\d+)<"""),
                Regex("""^- Languages (\d+)$""", RegexOption.MULTILINE),
            )
    }
}
