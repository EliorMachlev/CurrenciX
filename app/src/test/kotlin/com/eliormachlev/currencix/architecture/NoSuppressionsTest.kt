package com.eliormachlev.currencix.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Warnings get fixed, not hidden (docs/markDown/contributing.md, "No
 * Suppressions, No Cosmetic Workarounds"). This walks the repository's
 * sources, build scripts, resources and workflows and fails on anything that
 * silences a compiler or an analyzer — other than the exceptions recorded in
 * docs/markDown/exceptions.md, which are listed in [EXCEPTIONS] line for line.
 */
class NoSuppressionsTest {
    private val root = repositoryRoot()

    @Test
    fun `nothing silences a warning beyond the recorded exceptions`() {
        val found = suppressions()
        val allowance = EXCEPTIONS.groupingBy { it.path to it.line }.eachCount().toMutableMap()
        val unexpected =
            found.filter { hit ->
                val key = hit.path to hit.text
                val left = allowance[key] ?: 0
                allowance[key] = left - 1
                left <= 0
            }
        assertEquals(
            "Fix the cause instead of hiding the warning (an exception needs a row in $EXCEPTIONS_DOC first):\n" +
                unexpected.joinToString("\n") { "${it.path}:${it.lineNumber}: ${it.text}" },
            emptyList<Suppression>(),
            unexpected,
        )
    }

    @Test
    fun `every recorded exception is still in the code`() {
        val found = suppressions().groupingBy { it.path to it.text }.eachCount()
        val stale =
            EXCEPTIONS
                .groupingBy { it.path to it.line }
                .eachCount()
                .filter { (key, count) -> (found[key] ?: 0) < count }
                .keys
        assertEquals(
            "Exceptions listed here but gone from the code; drop them from the list and from $EXCEPTIONS_DOC:",
            emptySet<Any>(),
            stale,
        )
    }

    @Test
    fun `every recorded exception sits on the thing it was granted for`() {
        EXCEPTIONS.forEach { exception ->
            val marked = Regex(Regex.escape(exception.line) + """\s*<activity\s+android:name="[\w.]*\.""" + exception.subject + "\"")
            assertTrue(
                "${exception.path}: the ${exception.rule} exclusion must be directly above ${exception.subject}",
                marked.containsMatchIn(File(root, exception.path).readText()),
            )
        }
    }

    @Test
    fun `every recorded exception is explained in the exceptions document`() {
        val doc = File(root, EXCEPTIONS_DOC).readText()
        EXCEPTIONS.forEach { exception ->
            assertTrue("$EXCEPTIONS_DOC doesn't mention ${exception.rule}", exception.rule in doc)
            assertTrue("$EXCEPTIONS_DOC doesn't mention ${exception.subject}", exception.subject in doc)
        }
    }

    @Test
    fun `no analyzer baseline exists`() {
        val baselines =
            scannedTree()
                .filter { it.isFile && BASELINE_FILE.matches(it.name) }
                .map { it.relativeTo(root).path }
                .toList()
        assertEquals("A baseline swallows findings; fix them instead:\n" + baselines.joinToString("\n"), emptyList<String>(), baselines)
    }

    private fun scannedTree(): Sequence<File> = root.walkTopDown().onEnter { it.name !in SKIPPED_DIRECTORIES }

    // Every line in the repository that carries a suppression marker.
    private fun suppressions(): List<Suppression> =
        scannedTree()
            .filter { it.isFile && it.isScanned() }
            .flatMap { file ->
                val path = file.relativeTo(root).path
                file.readLines().mapIndexedNotNull { index, line ->
                    if (MARKERS.any { it.containsMatchIn(line) }) Suppression(path, index + 1, line.trim()) else null
                }
            }.toList()

    private fun File.isScanned(): Boolean = (extension in SCANNED_EXTENSIONS || name in SCANNED_NAMES) && name != OWN_FILE

    // Unit tests run from the module directory; the repository is the first
    // parent holding the Gradle wrapper.
    private fun repositoryRoot(): File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "gradlew").exists() }

    private data class Suppression(
        val path: String,
        val lineNumber: Int,
        val text: String,
    )

    /** One permitted suppression: the exact [line], in [path], silencing [rule] for [subject]. */
    private data class RecordedException(
        val path: String,
        val line: String,
        val rule: String,
        val subject: String,
    )

    private companion object {
        const val OWN_FILE = "NoSuppressionsTest.kt"
        const val EXCEPTIONS_DOC = "docs/markDown/exceptions.md"

        const val MANIFEST = "app/src/main/AndroidManifest.xml"
        const val EXPORTED_ACTIVITY_RULE = "java.android.security.exported_activity.exported_activity"

        // The whole list. Adding to it is a maintainer's decision, recorded
        // in EXCEPTIONS_DOC with the reason no real fix exists.
        val EXCEPTIONS =
            listOf("MainActivity", "ConvertTextActivity").map { activity ->
                RecordedException(MANIFEST, "<!-- nosemgrep: $EXPORTED_ACTIVITY_RULE -->", EXPORTED_ACTIVITY_RULE, activity)
            }

        val SKIPPED_DIRECTORIES = setOf("build", ".gradle", ".git", ".kotlin", ".idea", "node_modules")
        val SCANNED_EXTENSIONS = setOf("kt", "kts", "java", "gradle", "xml", "pro", "yml", "yaml", "toml", "properties")
        val SCANNED_NAMES = setOf(".editorconfig")

        val MARKERS =
            listOf(
                // @Suppress, @file:Suppress, @SuppressLint, @SuppressWarnings
                Regex("""@(file:)?Suppress"""),
                Regex("""tools:ignore"""),
                Regex("""noinspection"""),
                Regex("""ktlint-disable"""),
                Regex("""nosemgrep"""),
                Regex("""-dontwarn"""),
                // lint { disable += "…" } / disable.add("…") in a build script
                Regex("""\bdisable\s*(\+=|\.add\b|\.addAll\b)"""),
                // Compiler flags that silence warnings
                Regex("""-nowarn|suppressWarnings\s*="""),
                // Analyzer flags that drop a rule or a path wholesale
                Regex("""--exclude-rule|--exclude[ =]"""),
            )

        // detekt's baseline-<module>.xml / detekt-baseline.xml and lint's lint-baseline.xml.
        val BASELINE_FILE = Regex("""(lint-|detekt-)?baseline(-[\w.]+)?\.xml""")
    }
}
