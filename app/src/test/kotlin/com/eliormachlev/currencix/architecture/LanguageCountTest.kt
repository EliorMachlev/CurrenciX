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

    private fun statedIn(): List<File> =
        listOf(
            "README.md",
            "docs/llms-full.txt",
            "docs/markDown/engineering/contributing.md",
            "docs/markDown/legal/accessibility-statement.md",
        ).map { File(root, it) } +
            File(root, "docs").listFiles().orEmpty().filter { it.extension == "html" } +
            File(root, "fastlane/metadata/android").walkTopDown().filter { it.name == "full_description.txt" }

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
                BADGE.findAll(text).map { Claim(name, it.groupValues[1].toInt(), "languages") }.toList()
        }

    @Test
    fun `the languages the app ships are found`() {
        assertTrue("Only $languages found: is the res folder where it was?", languages > MIN_LANGUAGES)
    }

    @Test
    fun `every stated number of languages is the number of res folders`() {
        val found = claims()
        assertTrue("No language count found in $LANGUAGE_FILES: did the wording change?", found.isNotEmpty())
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

        // "32 languages", "31 translations" and "31 languages besides English".
        val CLAIM = Regex("""\b(\d+) (languages(?! besides English)|translations|languages besides English)""")
        val BADGE = Regex("""badge/languages-(\d+)-""")
    }
}
