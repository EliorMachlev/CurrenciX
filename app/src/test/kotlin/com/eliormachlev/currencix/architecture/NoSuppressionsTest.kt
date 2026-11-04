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

        const val OWASP_SUPPRESSIONS = "config/owasp/suppressions.xml"
        const val OWASP_WORKFLOW = ".github/workflows/owasp-dependency-check.yaml"
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
                "model/adapter/AdapterUtils",
                "model/adapter/AllRatesTodayAdapters",
                "model/adapter/BankOfCanadaRatesAdapter",
                "model/adapter/BankOfCanadaTimelineAdapter",
                "model/adapter/BankOfIsraelRatesAdapter",
                "model/adapter/FrankfurterAppRatesAdapter",
                "model/adapter/FrankfurterAppTimelineAdapter",
                "model/adapter/InforEuroRatesAdapter",
                "model/adapter/InforEuroTimelineAdapter",
                "model/adapter/LocalDateAdapter",
                "model/adapter/OpenExchangeratesRatesAdapter",
                "repository/persistence/PrefStore",
                "view/widget/CurrencyWidget",
            )

        // Files with data objects, whose generated equals() casts to a temporary it never reads (#11).
        val DATA_OBJECT_EQUALS =
            listOf(
                "ocr/OcrEngine",
                "repository/BackupManager",
                "repository/CartExporter",
                "view/convert/SelectionConversion",
                "view/main/ConverterLaunch",
                "view/navigation/Screen",
                "view/preference/compose/FeesScreen",
                "view/preference/compose/PreferenceScreen",
                "view/preference/compose/ReceiptLanguagesScreen",
                "viewmodel/cart/TripChecklist",
                "viewmodel/preference/BackupViewModel",
            )

        const val VERIFICATION_METADATA = "gradle/verification-metadata.xml"
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
                // Each CVE, for Square's Wire runtime only.
                listOf(WIRE_CVE, WIRE_SERVER_CVE).map(::wireSuppression) +
                listOf(
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
                    522,
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
                IDE_DOCUMENTATION.map { kind ->
                    RecordedException(
                        VERIFICATION_METADATA,
                        trustLine(kind),
                        TRUSTED_ARTIFACTS,
                        "-$kind.jar",
                        // Inside the configuration's trusted-artifacts block, which holds only these two.
                        Regex(
                            (listOf("<$TRUSTED_ARTIFACTS>") + IDE_DOCUMENTATION.map(::trustLine) + "</$TRUSTED_ARTIFACTS>")
                                .joinToString("""\s*""") { Regex.escape(it) },
                        ),
                    )
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
                // Workflow linters: ShellCheck (run by actionlint on each run: script), actionlint, zizmor
                Regex("""shellcheck\s+disable="""),
                Regex("""actionlint\b.*\s-ignore\b"""),
                Regex("""zizmor:\s*ignore"""),
                // Gradle dependency verification: files accepted without a checksum
                Regex("""<(trust|ignored-key|trusted-key)\s"""),
            )

        // detekt's baseline-<module>.xml / detekt-baseline.xml and lint's lint-baseline.xml.
        val BASELINE_FILE = Regex("""(lint-|detekt-)?baseline(-[\w.]+)?\.xml""")
    }
}
