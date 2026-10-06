package com.eliormachlev.currencix.architecture

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Warnings get fixed, not hidden (docs/markDown/contributing.md, "No
 * Suppressions, No Cosmetic Workarounds"). This walks the repository's
 * sources, build scripts, resources and workflows and fails on anything that
 * silences a compiler or an analyzer.
 */
class NoSuppressionsTest {
    @Test
    fun `no source, script, resource or workflow silences a warning`() {
        val root = repositoryRoot()
        val hits =
            root
                .walkTopDown()
                .onEnter { it.name !in SKIPPED_DIRECTORIES }
                .filter { it.isFile && it.isScanned() }
                .flatMap { file -> file.suppressions().map { "${file.relativeTo(root)}:$it" } }
                .toList()
        assertEquals("Fix the cause instead of hiding the warning:\n" + hits.joinToString("\n"), emptyList<String>(), hits)
    }

    @Test
    fun `no analyzer baseline exists`() {
        val root = repositoryRoot()
        val baselines =
            root
                .walkTopDown()
                .onEnter { it.name !in SKIPPED_DIRECTORIES }
                .filter { it.isFile && BASELINE_FILE.matches(it.name) }
                .map { it.relativeTo(root).path }
                .toList()
        assertEquals("A baseline swallows findings; fix them instead:\n" + baselines.joinToString("\n"), emptyList<String>(), baselines)
    }

    private fun File.isScanned(): Boolean = (extension in SCANNED_EXTENSIONS || name in SCANNED_NAMES) && name != OWN_FILE

    // "line: marker" for each line of this file that carries one.
    private fun File.suppressions(): List<String> =
        readLines().mapIndexedNotNull {
            index,
            line,
            ->
            MARKERS.firstOrNull { it.containsMatchIn(line) }?.let { "${index + 1}: ${it.pattern}" }
        }

    // Unit tests run from the module directory; the repository is the first
    // parent holding the Gradle wrapper.
    private fun repositoryRoot(): File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "gradlew").exists() }

    private companion object {
        const val OWN_FILE = "NoSuppressionsTest.kt"

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
            )

        // detekt's baseline-<module>.xml / detekt-baseline.xml and lint's lint-baseline.xml.
        val BASELINE_FILE = Regex("""(lint-|detekt-)?baseline(-[\w.]+)?\.xml""")
    }
}
