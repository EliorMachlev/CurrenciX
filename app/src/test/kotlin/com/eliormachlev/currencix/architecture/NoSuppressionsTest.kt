package com.eliormachlev.currencix.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Warnings get fixed, not hidden (docs/markDown/engineering/contributing.md, "No
 * Suppressions, No Cosmetic Workarounds"). This walks the repository's
 * sources, build scripts, resources and workflows and fails on anything that
 * silences a compiler or an analyzer — other than the exceptions recorded in
 * docs/markDown/engineering/exceptions.md, which are listed in [EXCEPTIONS] line for line.
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
            assertTrue(
                "${exception.path}: the ${exception.rule} exclusion must be on ${exception.subject}",
                exception.placement.containsMatchIn(File(root, exception.path).readText()),
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

    @Test
    fun `jactionlint appears only on its recorded lines`() {
        // A config file can turn a rule off, and an ignore flag, comment or baseline can hide a
        // finding. Rather than parse every way a shell line can run it (a path, PATH, a YAML
        // folded or continued command, an action), every line outside a comment that names
        // jactionlint must be one recorded here. The run line among them is pinned whole, which
        // keeps --no-ignores and --config-file=/dev/null on it and rules out an ignore however its
        // flag is spelled (-i x, -ix, -i=x, --ignore=x).
        val mentions =
            scannedTree()
                .filter { it.isFile && it.isScanned() }
                .flatMap { file ->
                    val path = file.relativeTo(root).path
                    file
                        .readLines()
                        .map { it.trim() }
                        .filter { JACTIONLINT_WORD.containsMatchIn(it) && !it.startsWith("#") && !it.startsWith("//") }
                        .map { "$path: $it" }
                }.toList()
        assertEquals(
            "jactionlint may be named only on its recorded lines",
            JACTIONLINT_LINES.map { "$JACTIONLINT_WORKFLOW: $it" },
            mentions,
        )
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
    private data class Suppression(
        val path: String,
        val lineNumber: Int,
        val text: String,
    )

    /**
     * One permitted suppression: the exact [line], in [path], silencing
     * [rule] for [subject]; [placement] matches the file only while the line
     * still sits on that subject.
     */
    private data class RecordedException(
        val path: String,
        val line: String,
        val rule: String,
        val subject: String,
        val placement: Regex,
    )

    private companion object {
        const val OWN_FILE = "NoSuppressionsTest.kt"
        const val EXCEPTIONS_DOC = "docs/markDown/engineering/exceptions.md"

        const val MANIFEST = "app/src/main/AndroidManifest.xml"
        const val EXPORTED_ACTIVITY_RULE = "java.android.security.exported_activity.exported_activity"

        const val LEAK_DETECTION = "app/src/debug/kotlin/com/eliormachlev/currencix/leaks/LeakDetection.kt"
        const val VENDOR_FIELD = "ResourcesImpl.mAppContext"

        const val APP_BUILD = "app/build.gradle.kts"
        const val PROFILE_BUILD = "baselineprofile/build.gradle.kts"
        const val UNSTABLE_API_RULE = "UnstableApiUsage"
        const val BENCHMARK_ERRORS = "androidx.benchmark.suppressErrors"
        const val EMULATOR_LINE = "testInstrumentationRunnerArguments[\"$BENCHMARK_ERRORS\"] = \"EMULATOR\""

        const val OWASP_SUPPRESSIONS = "config/owasp/suppressions.xml"
        const val OWASP_WORKFLOW = ".github/workflows/owasp-dependency-check.yaml"
        const val SEMGREP_WORKFLOW = ".github/workflows/semgrep.yaml"
        const val MUTABLE_TAG_RULE = "yaml.github-actions.security.github-actions-mutable-action-tag.github-actions-mutable-action-tag"
        const val SELF_REPOSITORY = "`$/`"
        const val JACTIONLINT_WORKFLOW = ".github/workflows/jactionlint.yaml"
        const val JACTIONLINT_RUN =
            "run: '\"\$RUNNER_TEMP/jactionlint\" --profile default --strict-exit --no-ignores --config-file=/dev/null --format github'"

        // jactionlint as a word: not JACTIONLINT_VERSION, not the jactionlint_<version> archive.
        val JACTIONLINT_WORD = Regex("""\bjactionlint\b""")

        // Every line outside a comment that may name it, in file order: the workflow's name and
        // job, its install step (the release URL and extracting the binary), and the one run line.
        val JACTIONLINT_LINES =
            listOf(
                "name: jactionlint",
                "jactionlint:",
                "- name: Install jactionlint",
                "\"https://github.com/jdx/jactionlint/releases/download/v\${JACTIONLINT_VERSION}/\$archive\"",
                "tar -xzf \"\$RUNNER_TEMP/\$archive\" -C \"\$RUNNER_TEMP\" jactionlint",
                JACTIONLINT_RUN,
            )
        const val WIRE_CVE = "CVE-2018-8909"
        const val WIRE_SERVER_CVE = "CVE-2023-22737"
        const val WIRE_RUNTIME = "wire-runtime"

        const val CODEQL_EXCEPTIONS = ".github/codeql/exceptions.yml"
        const val CODEQL_WORKFLOW = ".github/workflows/codeql.yaml"
        const val CODEQL_FILTER = ".github/scripts/codeql-exceptions.py"
        const val USELESS_NULL_CHECK = "java/useless-null-check"
        const val QUICK_CONVERSIONS = "QuickConversionsSheet"
        const val SOURCES = "app/src/main/kotlin/com/eliormachlev/currencix"

        // Classes whose superclass also carries the Compose compiler's $stable (exceptions.md #10).
        val STABLE_SHADOWING =
            listOf(
                "repository/persistence/PrefStore",
                "view/widget/CurrencyWidget",
            )

        // Files with data objects, whose generated equals() casts to a temporary it never reads (#11).
        val DATA_OBJECT_EQUALS =
            listOf(
                "repository/BackupManager",
                "repository/CartExporter",
                "view/cart/ReceiptScan",
                "view/convert/SelectionConversion",
                "view/main/ConverterLaunch",
                "view/navigation/Screen",
                "view/preference/compose/FeesScreen",
                "view/preference/compose/PreferenceScreen",
                "view/preference/compose/ReceiptLanguagesScreen",
                "viewmodel/cart/TripChecklist",
                "viewmodel/preference/BackupStateHolder",
            )

        const val VERIFICATION_METADATA = "gradle/verification-metadata.xml"
        const val TYPOS_CONFIG = "_typos.toml"
        const val TYPOS_WORDS = "[default.extend-words]"

        // The correct words typos takes for misspellings (exceptions.md #16).
        val TYPOS_ALLOWED = listOf("infor", "mis")

        // Licensee's allowances beyond its SPDX identifiers (exceptions.md #17, #18).
        const val LICENSEE_RULE = "Licensee"
        const val BOUNCY_CASTLE_LICENCE = "allowUrl(\"https://www.bouncycastle.org/licence.html\")"
        const val TESSERACT = "tesseract4android"
        const val TESSERACT_ALLOWANCE = "allowDependency(\"com.github.adaptech-cz.Tesseract4Android\", \"$TESSERACT\", \"4.8.0\")"
        const val TRUSTED_ARTIFACTS = "trusted-artifacts"

        // The files only an IDE sync downloads, accepted without a checksum (exceptions.md #9).
        val IDE_DOCUMENTATION = listOf("sources", "javadoc")

        fun trustLine(kind: String) = """<trust file=".*-$kind[.]jar" regex="true"/>"""

        // A Dependency-Check CVE excluded for Square's Wire runtime only (exceptions.md #7).
        fun wireSuppression(cve: String) =
            RecordedException(
                OWASP_SUPPRESSIONS,
                "<suppress>",
                cve,
                WIRE_RUNTIME,
                Regex(
                    """<suppress>\s*<notes>[^<]*</notes>\s*<filePath regex="true">[^<]*""" +
                        Regex.escape("com\\.squareup\\.wire/$WIRE_RUNTIME") +
                        """[^<]*</filePath>\s*<cve>""" + cve + "</cve>\\s*</suppress>",
                ),
            )

        // One CodeQL finding taken out of the results: its rule, the source file
        // (under SOURCES, without .kt), its line and a piece of its message.
        fun codeqlException(
            rule: String,
            source: String,
            line: Int,
            message: String,
        ): RecordedException {
            val marker = "- excluded-rule: $rule"
            return RecordedException(
                CODEQL_EXCEPTIONS,
                marker,
                rule,
                source.substringAfterLast('/'),
                Regex(
                    Regex.escape(marker) + """\s*file: """ + Regex.escape("$SOURCES/$source.kt") +
                        """\s*line: $line\s*message-contains: """ + Regex.escape(message),
                ),
            )
        }

        // An @Incubating AGP API kept on purpose: the marker (numbered as in
        // EXCEPTIONS_DOC) directly above the one statement that uses it.
        fun incubating(
            path: String,
            number: Int,
            subject: String,
            statement: String,
        ): RecordedException {
            val marker = "@Suppress(\"$UNSTABLE_API_RULE\") // exceptions.md #$number"
            return RecordedException(
                path,
                marker,
                UNSTABLE_API_RULE,
                subject,
                Regex(Regex.escape(marker) + """\s*""" + Regex.escape(statement)),
            )
        }

        // The whole list. Adding to it is a maintainer's decision, recorded
        // in EXCEPTIONS_DOC with the reason no real fix exists.
        val EXCEPTIONS =
            listOf("MainActivity", "ConvertTextActivity").map { activity ->
                val marker = "<!-- nosemgrep: $EXPORTED_ACTIVITY_RULE -->"
                RecordedException(
                    MANIFEST,
                    marker,
                    EXPORTED_ACTIVITY_RULE,
                    activity,
                    // The marker directly above that activity's element.
                    Regex(Regex.escape(marker) + """\s*<activity\s+android:name="[\w.]*\.""" + activity + "\""),
                )
            } +
                RecordedException(
                    LEAK_DETECTION,
                    "IgnoredReferenceMatcher(vendorAppContext),",
                    "LeakCanary IgnoredReferenceMatcher",
                    VENDOR_FIELD,
                    // Only that one static field is ignored.
                    Regex(Regex.escape("""StaticFieldPattern("android.content.res.ResourcesImpl", "mAppContext")""")),
                ) +
                listOf(
                    incubating(PROFILE_BUILD, 4, "ManagedVirtualDevice", "testOptions.managedDevices.localDevices.create(managedDevice) {"),
                    incubating(PROFILE_BUILD, 5, "testedApks", "v.instrumentationRunnerArguments.put("),
                    incubating(APP_BUILD, 6, "generateLocaleConfig", "generateLocaleConfig = true"),
                ) +
                RecordedException(
                    PROFILE_BUILD,
                    EMULATOR_LINE,
                    BENCHMARK_ERRORS,
                    "EMULATOR",
                    // The emulator check alone, never a list of errors.
                    Regex(Regex.escape(EMULATOR_LINE) + "\\s*$", RegexOption.MULTILINE),
                ) +
                // Each CVE, for Square's Wire runtime only.
                listOf(WIRE_CVE, WIRE_SERVER_CVE).map(::wireSuppression) +
                listOf(
                    RecordedException(
                        SEMGREP_WORKFLOW,
                        "--exclude-rule=$MUTABLE_TAG_RULE \\",
                        MUTABLE_TAG_RULE,
                        SELF_REPOSITORY,
                        // This one rule, nothing else on the line.
                        Regex(Regex.escape("--exclude-rule=$MUTABLE_TAG_RULE \\") + "\\s*$", RegexOption.MULTILINE),
                    ),
                    RecordedException(
                        OWASP_WORKFLOW,
                        "--suppression $OWASP_SUPPRESSIONS",
                        WIRE_CVE,
                        WIRE_RUNTIME,
                        // The file above and nothing else.
                        Regex(Regex.escape("--suppression $OWASP_SUPPRESSIONS") + "\\s*$", RegexOption.MULTILINE),
                    ),
                ) +
                // Each finding: its file, its line (1: code the compiler generates) and its message.
                codeqlException(USELESS_NULL_CHECK, "view/main/compose/$QUICK_CONVERSIONS", 1, "tmpEnum") +
                STABLE_SHADOWING.map { codeqlException("java/field-masks-super-field", it, 1, "\$stable") } +
                DATA_OBJECT_EQUALS.map { codeqlException("java/local-variable-is-never-read", it, 1, "tmp0_other_with_cast") } +
                codeqlException(
                    "java/reference-equality-of-boxed-types",
                    "view/timeline/TimelineChart",
                    523,
                    "reference comparison of boxed numerical values",
                ) +
                listOf(
                    RecordedException(
                        CODEQL_WORKFLOW,
                        "run: python3 $CODEQL_FILTER $CODEQL_EXCEPTIONS sarif",
                        USELESS_NULL_CHECK,
                        QUICK_CONVERSIONS,
                        // The file above, on the Kotlin results only.
                        Regex(
                            Regex.escape("if: matrix.language == 'java-kotlin'") + """\s*""" +
                                Regex.escape("run: python3 $CODEQL_FILTER $CODEQL_EXCEPTIONS sarif"),
                        ),
                    ),
                ) +
                listOf(BOUNCY_CASTLE_LICENCE to "bcprov", TESSERACT_ALLOWANCE to TESSERACT).map { (line, subject) ->
                    RecordedException(
                        APP_BUILD,
                        line,
                        LICENSEE_RULE,
                        subject,
                        // Inside the licensee block.
                        Regex(Regex.escape("licensee {") + "[^}]*" + Regex.escape(line)),
                    )
                } +
                IDE_DOCUMENTATION.map { kind ->
                    RecordedException(
                        VERIFICATION_METADATA,
                        trustLine(kind),
                        TRUSTED_ARTIFACTS,
                        "-$kind.jar",
                        // Inside the configuration's trusted-artifacts block, which holds only these two,
                        // in either order: Gradle sorts them when it writes the file from scratch.
                        Regex(
                            listOf(IDE_DOCUMENTATION, IDE_DOCUMENTATION.reversed()).joinToString("|") { order ->
                                (listOf("<$TRUSTED_ARTIFACTS>") + order.map(::trustLine) + "</$TRUSTED_ARTIFACTS>")
                                    .joinToString("""\s*""") { Regex.escape(it) }
                            },
                        ),
                    )
                } +
                RecordedException(
                    TYPOS_CONFIG,
                    TYPOS_WORDS,
                    "typos extend-words",
                    "mis-issued",
                    // The section holds those two words and nothing else, and is the file's last.
                    Regex(
                        Regex.escape(TYPOS_WORDS) +
                            TYPOS_ALLOWED.joinToString("") { word -> """\s*""" + Regex.escape("$word = \"$word\"") } +
                            """\s*\z""",
                    ),
                )

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
                // LeakCanary told to stop reporting a reference
                Regex("""IgnoredReferenceMatcher\("""),
                // OWASP Dependency-Check: a finding excluded, and the file of them handed in
                Regex("""<suppress\b"""),
                Regex("""--suppression\b"""),
                // CodeQL: a finding listed for removal, and the step that removes the listed ones
                Regex("""excluded-rule:"""),
                Regex("""codeql-exceptions\.py\s+\S"""),
                // A workflow step whose failure doesn't fail its job
                Regex("""continue-on-error:\s*(true|\$\{\{)"""),
                // typos: words added to its dictionary, or files and patterns it skips
                Regex("""\bextend-(words|identifiers|exclude|ignore-re|ignore-identifiers-re)\b"""),
                // Licensee: a license or library allowed beyond the SPDX identifiers
                Regex("""\b(allowUrl|allowDependency|ignoreDependencies)\("""),
                // Workflow linters: ShellCheck (run by jactionlint on each run: script), jactionlint
                // (an -i / --ignore / -ignore flag, or its # jactionlint / # actionlint ignore= comment), zizmor
                Regex("""shellcheck\s+disable="""),
                Regex("""actionlint\b.*\s(?!-init-config\b)(-[A-Za-z]*i|--ignore\b)"""),
                Regex("""#\s*j?actionlint\s+ignore="""),
                Regex("""zizmor:\s*ignore"""),
                // Gradle dependency verification: files accepted without a checksum
                Regex("""<(trust|ignored-key|trusted-key)\s"""),
                // AndroidX Benchmark: a configuration error turned into a warning
                Regex("""androidx\.benchmark\.suppressErrors"""),
            )

        // detekt's baseline-<module>.xml / detekt-baseline.xml and lint's lint-baseline.xml.
        val BASELINE_FILE = Regex("""(lint-|detekt-)?baseline(-[\w.]+)?\.xml""")
    }
}
