package com.eliormachlev.currencix.architecture

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

// The language the unqualified res/values strings are written in.
private const val DEFAULT_LOCALE = "en-US"

// res/values-* folders that aren't translations.
private val NOT_A_LANGUAGE = setOf("night")

// Old ISO codes Android's resource folders still use, and their BCP 47 tags.
private val LEGACY_LANGUAGE_CODES = mapOf("in" to "id", "iw" to "he", "ji" to "yi")

private val QUALIFIER = Regex("""([a-z]{2,3})(?:-r([A-Z]{2}))?""")
private val LOCALE_NAME = Regex("""android:name="([^"]+)"""")

/**
 * res/xml/locales_config.xml (the per-app language list the system shows)
 * lists exactly the app's translations: the default locale and one per
 * res/values-* folder. It's written by hand rather than generated, as AGP's
 * generator is still incubating, so this keeps it from falling out of step.
 */
class LocaleConfigTest {
    private val res = File(repositoryRoot(), "app/src/main/res")

    @Test
    fun `the language list is the default locale plus every translation folder`() {
        val translations =
            res
                .listFiles { file -> file.isDirectory && file.name.startsWith("values-") }
                .orEmpty()
                .map { it.name.removePrefix("values-") }
                .filterNot { it in NOT_A_LANGUAGE }
                .map(::languageTag)
        val listed = LOCALE_NAME.findAll(File(res, "xml/locales_config.xml").readText()).map { it.groupValues[1] }.toList()

        assertEquals((translations + DEFAULT_LOCALE).sorted(), listed.sorted())
        assertEquals("each language once", listed.distinct(), listed)
    }

    // "pt-rBR" → "pt-BR", "iw" → "he".
    private fun languageTag(qualifier: String): String {
        val (language, region) =
            requireNotNull(
                QUALIFIER.matchEntire(qualifier),
            ) { "not a language folder: values-$qualifier" }.destructured
        val tag = LEGACY_LANGUAGE_CODES[language] ?: language
        return if (region.isEmpty()) tag else "$tag-$region"
    }
}
