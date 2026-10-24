
import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.FilterConfiguration
import org.cyclonedx.Version
import org.cyclonedx.gradle.CyclonedxDirectTask
import org.cyclonedx.model.Component
import org.cyclonedx.model.LicenseChoice
import org.cyclonedx.model.license.Expression
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.image.BufferedImage
import java.io.FileInputStream
import java.util.Properties
import javax.imageio.ImageIO

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
    id("com.google.devtools.ksp") version "2.3.12"
    // Roborazzi drives the JVM screenshot-test task (recordRoborazzi{Flavor}Debug)
    // used by the .github/workflows/screenshots.yaml job. Runs on top of
    // Robolectric Native Graphics — no device or emulator required.
    id("io.github.takahirom.roborazzi") version "1.76.0"
    // Consumes the baseline + startup profiles emitted by :baselineprofile and
    // bakes them into the release AAB / APK for ProfileInstaller to hand to
    // ART at install time. Version pinned at the root build script.
    id("androidx.baselineprofile")
    // CycloneDX SBOM of what the app ships; the release workflow attaches it
    // to each GitHub release.
    id("org.cyclonedx.bom") version "3.5.1"
    // Kotlin classes for the Protobuf schema in src/main/proto (carts, trips,
    // fees on disk): plain generated code, no reflection.
    id("com.squareup.wire") version "7.1.0"
}

wire {
    kotlin {}
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

base {
    archivesName.set("com.eliormachlev.currencix-v12300")
}

// The last digit of each APK's version code: versionCode × 10 + this, so
// the per-CPU APKs of one release differ (Android and F-Droid need that)
// and a phone that runs several types (64-bit ARM also runs 32-bit) gets
// the higher, better fit. The universal APK and the app bundle keep 0.
val abiVersionDigits = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86" to 3, "x86_64" to 4)
val abiVersionMultiplier = 10

// Each per-CPU APK's version name says which it is ("1.23.0-arm64"), for
// people: Settings and Android's app info show it. Stores go by the codes.
val abiVersionLabels = mapOf("armeabi-v7a" to "arm32", "arm64-v8a" to "arm64", "x86" to "x86", "x86_64" to "x64")

// Whether this Gradle run builds an app bundle (Play), which takes no APK splits.
// The Jazzer fuzz tests: left out of every unit-test run, or run alone
// with -PfuzzTests (see the unitTests filter below).
val fuzzTestClass = "com.eliormachlev.currencix.FuzzTest"

val buildsBundle = gradle.startParameter.taskNames.any { it.substringAfterLast(':').startsWith("bundle") }

android {
    namespace = "com.eliormachlev.currencix"
    // 37.2: Compose 1.13 needs at least API 37.1 to compile against.
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.eliormachlev.currencix"
        minSdk { version = release(33) }
        targetSdk { version = release(37) }
        // SemVer
        versionName = "1.23.0"
        versionCode = 12300
    }

    signingConfigs {
        create("release") {
            if (getSecret("KEYSTORE_FILE") != null) {
                storeFile = File(getSecret("KEYSTORE_FILE")!!)
                storePassword = getSecret("KEYSTORE_PASSWORD")
                keyAlias = getSecret("KEYSTORE_KEY_ALIAS")
                keyPassword = getSecret("KEYSTORE_KEY_PASSWORD")
            }
        }
        // Shared debug keystore checked into the repo so debug APKs built on
        // any machine (CI or local) share a signature and can upgrade cleanly
        // instead of tripping INSTALL_FAILED_UPDATE_INCOMPATIBLE. Credentials
        // are the Android SDK defaults — non-secret by design.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // Only apply the release signing config when a keystore is
            // actually present — CI's F-Droid reproducibility job builds
            // release unsigned and would otherwise fail
            // validateSigningRelease with "Keystore file not set".
            if (getSecret("KEYSTORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        debug {
            applicationIdSuffix = ".debug"
            // Commit SHA sourced from -PdebugCommitSha (CI passes it) or, as
            // a fallback, the local git HEAD short SHA so a locally-built APK
            // still knows which commit it came from. versionName encodes the
            // SHA (e.g. "1.23.0-abc1234"); if git isn't available, keep the
            // "[DEBUG]" tag.
            val commitSha = providers.gradleProperty("debugCommitSha").orNull ?: gitShortSha()
            versionNameSuffix = if (commitSha != null) "-$commitSha" else " [DEBUG]"
            buildConfigField("String", "COMMIT_SHA", "\"${commitSha ?: ""}\"")
            // CI passes -PprUrl=<pr html_url> for pull_request builds so the
            // in-app "Release notes" entry can deep-link back to the exact PR
            // the APK was built from. When absent the client falls back to
            // the commit URL, and finally to the repo pulls page.
            val prUrl = project.findProperty("prUrl") as String? ?: ""
            buildConfigField("String", "PR_URL", "\"$prUrl\"")
        }
    }

    // Receipt scanning's recognizer, on demand for Play (see :ocr). Bundles
    // only: AGP can't make per-CPU APKs with a feature module (it links the
    // feature against one base APK), and only Play installs features anyway.
    if (buildsBundle) dynamicFeatures += setOf(":ocr")

    flavorDimensions.add("version")
    productFlavors {
        create("play") {
            dimension = "version"
        }
        create("fdroid") {
            dimension = "version"
        }
    }

    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_21)
        targetCompatibility(JavaVersion.VERSION_21)
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric (Roborazzi's rendering engine) needs merged resources +
        // AndroidManifest on the JVM test classpath to instantiate Application
        // and resolve @string / @color references at screenshot capture time.
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.useJUnitPlatform()
            it.testLogging {
                events("failed")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                showStackTraces = true
                showCauses = true
            }
            // Jazzer's `@FuzzTest` (jazzer-junit) installs a JVM-wide
            // ClassFileTransformer that injects `JazzerInternal` references
            // into every class loaded after it. Robolectric's SandboxClassLoader
            // then re-loads test/production classes in its own sandbox where
            // `JazzerInternal` is not visible, and the injected calls blow up
            // with `NoClassDefFoundError` inside our Roborazzi screenshot tests.
            // Excluding FuzzTest keeps Jazzer's agent from attaching. With
            // -PfuzzTests the run is the other way round: the fuzz tests
            // alone (CI's fuzz job; JAZZER_FUZZ=1 fuzzes rather than only
            // replaying the saved inputs; see docs/markDown/build-and-flavors.md).
            val fuzzOnly = providers.gradleProperty("fuzzTests").isPresent
            it.filter {
                if (fuzzOnly) {
                    includeTestsMatching(fuzzTestClass)
                } else {
                    excludeTestsMatching(fuzzTestClass)
                }
            }
        }
    }

    // The per-app language list the system shows is generated from the
    // res/values-* folders, so it can't fall out of step with the
    // translations that exist. res/resources.properties names the locale
    // the unqualified resources are written in. AGP still marks the switch
    // @Incubating; it's kept on purpose (it saves a hand-kept list and the
    // test that would guard it), a recorded exception.
    androidResources {
        @Suppress("UnstableApiUsage") // exceptions.md #6
        generateLocaleConfig = true
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    // One APK per CPU type (each phone needs one: receipt scanning's native
    // OCR code is about 7 MB per type), plus a universal APK for people who
    // download by hand. F-Droid builds one recipe entry per type and its app
    // installs the one that fits; Play builds from the app bundle, which
    // splits by type itself. Version codes: see abiVersionDigits above.
    splits {
        abi {
            // Not while building the app bundle: AGP can't make both at once
            // (issuetracker.google.com/402800800), and Play splits the bundle itself.
            isEnable = !buildsBundle
            reset()
            // Written out (not from abiVersionDigits) so lint can read which types are built.
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    // Native libraries compressed in the APK: about 12 MB smaller to download
    // with all four types; Android unpacks them on install.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters.find { it.filterType == FilterConfiguration.FilterType.ABI }?.identifier
            val base = android.defaultConfig.versionCode ?: return@forEach
            output.versionCode.set(base * abiVersionMultiplier + (abiVersionDigits[abi] ?: 0))
            val label = abiVersionLabels[abi] ?: return@forEach
            val name = output.versionName.orNull ?: return@forEach
            output.versionName.set("$name-$label")
        }
        // The version on the splash screen: the universal APK's, without an ABI label.
        val universal = variant.outputs.first { output -> output.filters.none { it.filterType == FilterConfiguration.FilterType.ABI } }
        val splashVersion =
            tasks.register<GenerateSplashVersion>("generateSplashVersion${variant.name.replaceFirstChar(Char::uppercaseChar)}") {
                versionName.set(universal.versionName)
                font.set(layout.projectDirectory.file("src/main/res/font/space_grotesk_medium.ttf"))
                // PaperInkMuted and InkForegroundMuted (view/compose/theme/Color.kt).
                lightColor.set(0xFF6B675FL)
                darkColor.set(0xFFA9A49AL)
            }
        variant.sources.res?.addGeneratedSourceDirectory(splashVersion, GenerateSplashVersion::outputDirectory)
    }
    // Release APKs re-signed with the post-quantum hybrid signature once its
    // keys are configured (see "Post-quantum signing" below).
    val hybridKeys = HybridSigningKeys.fromSecrets(::getSecret)
    if (hybridKeys != null) {
        val apksignerJar = sdkComponents.sdkDirectory.map { it.file("build-tools/${android.buildToolsVersion}/lib/apksigner.jar") }
        val nativeLibs = sdkComponents.sdkDirectory.map { it.dir("build-tools/${android.buildToolsVersion}/lib64") }
        onVariants(selector().withBuildType("release")) { variant ->
            val signing =
                tasks.register<SignApksHybrid>("signApksHybrid${variant.name.replaceFirstChar(Char::uppercaseChar)}") {
                    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(SignApksHybrid.JDK_VERSION)) })
                    this.apksignerJar.set(apksignerJar)
                    this.nativeLibs.set(nativeLibs)
                    keys.set(hybridKeys)
                }
            val request =
                variant.artifacts
                    .use(signing)
                    .wiredWithDirectories(SignApksHybrid::inputApks, SignApksHybrid::outputApks)
                    .toTransformMany(SingleArtifact.APK)
            signing.configure { transformationRequest.set(request) }
        }
    }
}

dependencies {
    // kotlin
    implementation("androidx.core:core-ktx:1.19.1")
    // kotlinx.collections.immutable: exposes @Immutable persistent collection
    // types (ImmutableList / PersistentList / ...) so Compose stability
    // inference can skip recomposition of composables whose only "unstable"
    // input was a plain `List<T>`. Adopted on Compose-facing state per #161.
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.5.2")
    // Material You from the wallpaper itself (view/compose/theme/WallpaperColors.kt):
    // Google's Material color utilities in plain Kotlin, building the tonal
    // palette from the wallpaper's main colour where the phone's own system
    // colours don't follow the wallpaper (MIUI / HyperOS). No Compose in it.
    implementation("com.materialkolor:material-color-utilities:6.0.0")
    // persistence: DataStore Preferences replaces SharedPreferences across every
    // namespace (see repository/persistence/PersistenceKey.kt). The `-preferences`
    // artifact pulls `datastore-preferences-core` transitively and provides the
    // Android-aware `preferencesDataStore` delegate. The plain artifact holds
    // what the user saves (carts, trips, fees) as Protobuf, one typed file per
    // kind (repository/persistence/SavedDataFile.kt).
    val datastoreVersion = "1.3.0"
    implementation("androidx.datastore:datastore-preferences:$datastoreVersion")
    implementation("androidx.datastore:datastore:$datastoreVersion")
    // profileinstaller: reads the baseline + startup profiles baked in by the
    // androidx.baselineprofile plugin (generated by :baselineprofile) and
    // hands them to ART at install time so first-frame + first-interaction
    // paths are precompiled. Version pinned explicitly.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    // support libs
    val appCompatVersion = "1.8.0"
    implementation("androidx.appcompat:appcompat:$appCompatVersion")
    implementation("androidx.appcompat:appcompat-resources:$appCompatVersion")
    // Splash screen: androidx compat wrapper around Android 12+ SplashScreen API.
    // Closes the black-frame gap between launcher tap and first Compose frame,
    // and hands off to the in-app wordmark reveal (#155). Pinned rather than
    // dropped to the BOM default so upgrades are explicit.
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.2")
    val livecycleVersion = "2.11.0"
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$livecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$livecycleVersion")
    // The app lock: when the app as a whole leaves and comes back (view/lock/AppLock).
    implementation("androidx.lifecycle:lifecycle-process:$livecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$livecycleVersion")
    implementation("androidx.window:window:1.5.1")
    // downloader: OkHttp is the sole HTTP client. Timber-bridged logging
    // interceptor is wired up in HttpClientProvider; provider modules call
    // the shared instance via the HttpClientProvider.fetch extension.
    val okHttpVersion = "5.5.0"
    implementation("com.squareup.okhttp3:okhttp:$okHttpVersion")
    implementation("com.squareup.okhttp3:logging-interceptor:$okHttpVersion")
    // Chucker: in-app HTTP inspector for debug builds. The real library is
    // wired only into debug via the ChuckerInterceptorProvider source-set
    // split (src/debug vs src/release); release ships the library-no-op
    // artifact so the class references still resolve at compile time but
    // no UI / storage code is dragged into the shipped APK.
    val chuckerVersion = "4.3.1"
    debugImplementation("com.github.chuckerteam.chucker:library:$chuckerVersion")
    releaseImplementation("com.github.chuckerteam.chucker:library-no-op:$chuckerVersion")
    // Moshi without its reflection module (moshi-kotlin pulls in kotlin-reflect):
    // every JSON type has a generated adapter (@JsonClass, by KSP) or one of
    // the app's own (model/adapter, repository/json).
    val moshiVersion = "1.15.2"
    implementation("com.squareup.moshi:moshi:$moshiVersion")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:$moshiVersion")
    // Retrofit: type-safe HTTP interfaces layered on top of the shared OkHttp
    // client (see util/RetrofitProvider.kt). Migrated one provider at a time
    // starting with Frankfurter (#158) — subsequent JSON providers follow as
    // separate PRs. converter-moshi reuses our existing Moshi adapters so
    // custom (De)serializers keep working unchanged.
    val retrofitVersion = "3.0.0"
    implementation("com.squareup.retrofit2:retrofit:$retrofitVersion")
    implementation("com.squareup.retrofit2:converter-moshi:$retrofitVersion")
    // compose (hosts the Vico chart plus migrated UI surfaces via ComposeView).
    // The pre-release BOM: Compose 1.13.0-beta01 and Material 3 1.5.0-beta01,
    // for their text-field keyboard (IME) and list-prefetch fixes. Back to
    // `compose-bom` once both are stable.
    val composeBomVersion = "2026.10.00"
    implementation(platform("androidx.compose:compose-bom-alpha:$composeBomVersion"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$livecycleVersion")
    // Bridges StateFlow → Compose (`collectAsStateWithLifecycle`), which is
    // lifecycle-aware in a way `collectAsState` isn't: it pauses collection
    // when the host goes to STOPPED and resumes on STARTED. Used by the
    // StateFlow-based ViewModels (see #149 pilot in PreferenceViewModel).
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$livecycleVersion")
    // Navigation 3: the whole app is one Activity; screens are NavDisplay
    // entries over a Compose-owned back stack (view/navigation). The ViewModel
    // decorator scopes each screen's ViewModel to its back-stack entry, so a
    // popped screen's state is cleared exactly as a finished Activity's was.
    val navigation3Version = "1.2.0"
    implementation("androidx.navigation3:navigation3-runtime:$navigation3Version")
    implementation("androidx.navigation3:navigation3-ui:$navigation3Version")
    implementation("androidx.lifecycle:lifecycle-viewmodel-navigation3:$livecycleVersion")
    // glance: home-screen widget composed instead of RemoteViews-driven.
    val glanceVersion = "1.2.0"
    implementation("androidx.glance:glance-appwidget:$glanceVersion")
    // WorkManager: periodic background refresh of exchange rates (#151).
    // Provider-aware TTL — see worker/RateRefreshScheduler.kt. Default off;
    // opt-in via Settings until #147 onboarding wires the hero opt-in.
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    // Receipt scanning: Tesseract OCR on the device (:ocr-engine), built into
    // the F-Droid app and installed on demand from :ocr in the Play app
    // through Play Feature Delivery. The F-Droid app ships English data in
    // its assets; other languages (and English, on Play) download in the
    // background (ocr/TessdataDownloadWorker).
    "fdroidImplementation"(project(":ocr-engine"))
    "playImplementation"("com.google.android.play:feature-delivery-ktx:2.1.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    // charts
    val vicoVersion = "3.3.1"
    implementation("com.patrykandpatrick.vico:compose:$vicoVersion")
    // drag-to-reorder for LazyColumn — used by the currency picker's Starred
    // section (#142) and the cart list (#141). Provides `ReorderableItem` +
    // `longPressDraggableHandle` so cart rows can pair the drag handle with a
    // sibling SwipeToDismissBox. Pinned to a stable tag so upstream drop-in
    // changes can't move the API out from under us — see
    // docs/markDown/contributing.md on version pins.
    implementation("sh.calvin.reorderable:reorderable:3.1.0")
    // crypto: BouncyCastle provides pure-Java Argon2id, the key derivation
    // for password-based backup encryption (quantum-resistant KDF).
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    // crypto: Tink (Apache-2.0) runs the AES-GCM cipher for backups and owns
    // its nonce handling, instead of a hand-driven javax.crypto.Cipher.
    implementation("com.google.crypto.tink:tink-android:1.24.0")
    // logging: Timber routes to a rotating file tree written under filesDir/logs.
    // Local-only — no remote crash / analytics sink.
    implementation("com.jakewharton.timber:timber:5.0.1")
    // leak detection: LeakCanary is debug-only and auto-installs via its own
    // ContentProvider — no Application wiring needed. Safety net for the
    // upcoming Phase 1–3 migrations; never shipped in release/F-Droid builds.
    // 3.0 is still an alpha, but 2.14 dates from April 2024; debug-only.
    debugImplementation("com.squareup.leakcanary:leakcanary-android:3.0-alpha-9")
    // perf: JankStats attaches per-Activity in debug builds and logs jank
    // frames via Timber. Source-set split (src/debug vs src/release) means
    // the release variant sees a no-op installer and this dep is stripped —
    // zero overhead in shipped APKs. No telemetry sink.
    debugImplementation("androidx.metrics:metrics-performance:1.0.0")
    // test
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.24.0")
    // core-testing provides InstantTaskExecutorRule so LiveData setValue can
    // run on the JVM test thread without hitting the main-thread assertion.
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    // fuzzing
    val junitVersion = "6.1.3"
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:$junitVersion")
    testImplementation("com.code-intelligence:jazzer-junit:0.30.0")
    // screenshot testing — pure JVM path via Robolectric Native Graphics, so
    // CI can render every Compose surface without an emulator. The vintage
    // engine (already above) runs Robolectric's JUnit 4 test runner under
    // useJUnitPlatform().
    val roborazziVersion = "1.76.0"
    testImplementation("io.github.takahirom.roborazzi:roborazzi:$roborazziVersion")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:$roborazziVersion")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.compose.ui:ui-test-manifest")
    // architecture: Konsist encodes MVVM layer boundaries as JUnit tests so
    // Phase 1+ rewrites can't silently break the View / ViewModel / Repository
    // / Model separation. Runs on the plain JVM (no Android / Robolectric).
    testImplementation("com.lemonappdev:konsist:0.18.1")
    // Pulls the generated baseline + startup profiles from the :baselineprofile
    // module into every :app variant. The androidx.baselineprofile plugin
    // registers this configuration and rewires assemble* tasks accordingly.
    "baselineProfile"(project(":baselineprofile"))
}

// Baseline-profile consumer settings: emit *both* the baseline profile and
// the startup profile in a single generator run (single instrumentation pass,
// two output files). Startup profile shrinks the DEX view of first-launch
// code so it lands earlier in the DEX file → shorter cold-start seek.
baselineProfile {
    automaticGenerationDuringBuild = false
    saveInSrc = true
}

// Best-effort short git SHA for the currently checked-out HEAD. Returns null
// if git isn't installed, the repo isn't a git checkout, or the command
// fails for any reason — callers treat that as "no commit context". Run
// through providers.exec so the configuration cache tracks it as an input
// (a new HEAD reconfigures) instead of refusing to store the build.
fun gitShortSha(): String? =
    try {
        providers
            .exec {
                commandLine("git", "rev-parse", "--short", "HEAD")
                workingDir = rootDir
                isIgnoreExitValue = true
            }.standardOutput.asText
            .get()
            .trim()
            .takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

fun getSecret(key: String): String? {
    val secretsFile: File = rootProject.file("secrets.properties")
    return if (secretsFile.exists()) {
        val props = Properties()
        props.load(FileInputStream(secretsFile))
        props.getProperty(key)
    } else {
        null
    }
}

// Post-quantum signing ///////////////////////////////////////////////////////////////////////////

/**
 * The keys for APK Signature Scheme v3.2, Android 17's post-quantum "hybrid"
 * signature: the app's own release key ([standalone], `KEYSTORE_*`), which
 * Android 16 and earlier keep verifying, plus a hybrid pair that Android 17+
 * verifies, a new classical key and an ML-DSA key, each with its lineage (the
 * certificate history that ties it to the release key, so updates install
 * over each other). Made once with `scripts/create-hybrid-signing-keys.sh`.
 */
data class HybridSigningKeys(
    val standalone: File,
    val standaloneAlias: String,
    val standalonePassword: String,
    val standaloneKeyPassword: String,
    val hybrid: File,
    val hybridPassword: String,
    val classicalAlias: String,
    val pqcAlias: String,
    val classicalLineage: File,
    val pqcLineage: File,
) : java.io.Serializable {
    // The passwords are never part of a log or an error.
    override fun toString() = "HybridSigningKeys(standalone=$standalone, hybrid=$hybrid)"

    companion object {
        private val HYBRID_SECRETS =
            listOf("HYBRID_KEYSTORE_FILE", "HYBRID_KEYSTORE_PASSWORD", "HYBRID_CLASSICAL_LINEAGE", "HYBRID_PQC_LINEAGE")
        private val RELEASE_SECRETS = listOf("KEYSTORE_FILE", "KEYSTORE_PASSWORD", "KEYSTORE_KEY_ALIAS")

        /**
         * The keys named in `secrets.properties`, or null when none of the
         * hybrid ones are there. Some but not all of them fails the build:
         * a release meant to be hybrid must never quietly go out without it.
         */
        fun fromSecrets(secret: (String) -> String?): HybridSigningKeys? {
            if (HYBRID_SECRETS.none { secret(it) != null }) return null
            val wanted = HYBRID_SECRETS + RELEASE_SECRETS
            val present = wanted.mapNotNull { key -> secret(key)?.let { key to it } }.toMap()
            val missing = wanted - present.keys
            if (missing.isNotEmpty()) {
                throw GradleException(
                    "Hybrid APK signing is half set up: secrets.properties lacks ${missing.joinToString()}",
                )
            }

            // Every key in [wanted] is in [present] by now.
            fun required(key: String) = present.getValue(key)
            return HybridSigningKeys(
                standalone = File(required("KEYSTORE_FILE")),
                standaloneAlias = required("KEYSTORE_KEY_ALIAS"),
                standalonePassword = required("KEYSTORE_PASSWORD"),
                standaloneKeyPassword = secret("KEYSTORE_KEY_PASSWORD") ?: required("KEYSTORE_PASSWORD"),
                hybrid = File(required("HYBRID_KEYSTORE_FILE")),
                hybridPassword = required("HYBRID_KEYSTORE_PASSWORD"),
                classicalAlias = secret("HYBRID_CLASSICAL_KEY_ALIAS") ?: "hybrid-classical",
                pqcAlias = secret("HYBRID_PQC_KEY_ALIAS") ?: "hybrid-pqc",
                classicalLineage = File(required("HYBRID_CLASSICAL_LINEAGE")),
                pqcLineage = File(required("HYBRID_PQC_LINEAGE")),
            )
        }
    }
}

/**
 * Re-signs a release variant's APKs (already signed with the release key by
 * the Android Gradle plugin, which can't make a hybrid signature) with
 * `apksigner`: the release key for Android 16 and earlier, the hybrid
 * classical + ML-DSA pair for Android 17 and later, then verifies each one.
 * Wired as a transform of the APK artifact, so `assemble`, `install` and the
 * output folder all get the hybrid-signed APKs, never the others.
 */
abstract class SignApksHybrid : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputApks: DirectoryProperty

    @get:OutputDirectory
    abstract val outputApks: DirectoryProperty

    @get:Internal
    abstract val transformationRequest: Property<ArtifactTransformationRequest<SignApksHybrid>>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val apksignerJar: RegularFileProperty

    @get:Internal
    abstract val nativeLibs: DirectoryProperty

    // apksigner runs on its own JDK, new enough for ML-DSA keys, whatever JDK Gradle runs on.
    @get:Nested
    abstract val javaLauncher: Property<JavaLauncher>

    // The keystores and lineages are read by apksigner; a changed key re-signs.
    @get:Internal
    abstract val keys: Property<HybridSigningKeys>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    val keyFiles get() = keys.map { listOf(it.standalone, it.hybrid, it.classicalLineage, it.pqcLineage) }

    @get:Input
    val keyAliases get() = keys.map { listOf(it.standaloneAlias, it.classicalAlias, it.pqcAlias) }

    @TaskAction
    fun sign() {
        val k = keys.get()
        transformationRequest.get().submit(this) { artifact ->
            val input = File(artifact.outputFile)
            val output = outputApks.file(input.name).get().asFile
            apksigner(
                "sign",
                "--ks",
                k.standalone.path,
                "--ks-key-alias",
                k.standaloneAlias,
                "--ks-pass",
                "env:$ENV_STANDALONE_PASSWORD",
                "--key-pass",
                "env:$ENV_STANDALONE_KEY_PASSWORD",
                "--next-signer",
                "--ks",
                k.hybrid.path,
                "--ks-key-alias",
                k.classicalAlias,
                "--ks-pass",
                "env:$ENV_HYBRID_PASSWORD",
                "--signer-lineage",
                k.classicalLineage.path,
                "--hybrid-signer-role",
                "classical",
                "--next-signer",
                "--ks",
                k.hybrid.path,
                "--ks-key-alias",
                k.pqcAlias,
                "--ks-pass",
                "env:$ENV_HYBRID_PASSWORD",
                "--signer-lineage",
                k.pqcLineage.path,
                "--hybrid-signer-role",
                "pqc",
                "--out",
                output.path,
                input.path,
            )
            apksigner("verify", "--verbose", output.path)
            output
        }
    }

    private fun apksigner(vararg args: String) {
        val k = keys.get()
        val java =
            javaLauncher
                .get()
                .executablePath.asFile.path
        val command =
            listOf(
                java,
                "--enable-native-access=ALL-UNNAMED",
                "-Djava.library.path=${nativeLibs.get().asFile}",
                "-jar",
                apksignerJar.get().asFile.path,
            ) +
                args
        val process =
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .apply {
                    environment()[ENV_STANDALONE_PASSWORD] = k.standalonePassword
                    environment()[ENV_STANDALONE_KEY_PASSWORD] = k.standaloneKeyPassword
                    environment()[ENV_HYBRID_PASSWORD] = k.hybridPassword
                }.start()
        val log = process.inputStream.bufferedReader().readText()
        if (process.waitFor() != 0) throw GradleException("apksigner ${args.first()} failed:\n$log")
        logger.info(log)
    }

    companion object {
        // Reading ML-DSA keys takes JDK 24 or later, where keytool and the JCA
        // know the algorithm; 25 is the long-term release after it.
        const val JDK_VERSION = 25

        // The passwords reach apksigner through its environment, never its command line.
        private const val ENV_STANDALONE_PASSWORD = "CURRENCIX_STANDALONE_PASSWORD"
        private const val ENV_STANDALONE_KEY_PASSWORD = "CURRENCIX_STANDALONE_KEY_PASSWORD"
        private const val ENV_HYBRID_PASSWORD = "CURRENCIX_HYBRID_PASSWORD"
    }
}

// Splash version ////////////////////////////////////////////////////////////////////////////////

/**
 * Draws the splash screen's branding image, `drawable/splash_version`: the
 * app's version ("v1.23.0") in the app's font, centred in the 200 × 80 dp
 * the splash gives it, in a light-mode and a dark-mode colour. Android's
 * splash takes a drawable there, not text, so the version is drawn here, at
 * build time, as an extra-extra-extra-high-density PNG (which Android
 * scales down for other screens): as a vector path its glyphs would be far
 * too long a path to draw well.
 */
abstract class GenerateSplashVersion : DefaultTask() {
    @get:Input
    abstract val versionName: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val font: RegularFileProperty

    // ARGB, as in a colour resource: on paper, then on ink.
    @get:Input
    abstract val lightColor: Property<Long>

    @get:Input
    abstract val darkColor: Property<Long>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val glyphs =
            Font
                .createFont(Font.TRUETYPE_FONT, font.get().asFile)
                .deriveFont(FONT_SIZE * SCALE)
                .createGlyphVector(FontRenderContext(null, true, true), "v${versionName.get()}")
        val bounds = glyphs.visualBounds
        val outline =
            glyphs.getOutline(
                ((WIDTH * SCALE - bounds.width) / 2 - bounds.x).toFloat(),
                ((HEIGHT * SCALE - bounds.height) / 2 - bounds.y).toFloat(),
            )
        // Only this run's images: nothing left from an earlier version of the task.
        outputDirectory
            .get()
            .asFile
            .listFiles()
            ?.forEach { it.deleteRecursively() }
        mapOf("drawable-xxxhdpi" to lightColor.get(), "drawable-night-xxxhdpi" to darkColor.get()).forEach { (folder, color) ->
            val image = BufferedImage(WIDTH * SCALE, HEIGHT * SCALE, BufferedImage.TYPE_INT_ARGB)
            image.createGraphics().apply {
                setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                this.color = Color(color.toInt(), true)
                fill(outline)
                dispose()
            }
            val dir =
                outputDirectory
                    .get()
                    .dir(folder)
                    .asFile
                    .apply { mkdirs() }
            ImageIO.write(image, "png", dir.resolve("splash_version.png"))
        }
    }

    companion object {
        // The branding image's size on the splash screen, in dp, and the
        // pixels per dp of the density it's drawn for (xxxhdpi).
        const val WIDTH = 200
        const val HEIGHT = 80
        const val SCALE = 4
        const val FONT_SIZE = 15f
    }
}

// versionCode <-> versionName /////////////////////////////////////////////////////////////////////

// Checks that versionCode and versionName match. Needed because of F-Droid:
// both have to be hard-coded and can't be assigned dynamically, so at least
// the build checks that they agree.
tasks.register("checkVersion") {
    doLast {
        val versionCode: Int? = android.defaultConfig.versionCode
        val correctVersionCode: Int = generateVersionCode(android.defaultConfig.versionName!!)
        if (versionCode != correctVersionCode) {
            throw GradleException(
                "versionCode and versionName don't match: versionCode should be $correctVersionCode. Is $versionCode.",
            )
        }
    }
}
tasks.findByName("assemble")!!.dependsOn(tasks.findByName("checkVersion")!!)

// Checks that a fastlane changelog for the current version is present.
tasks.register("checkFastlaneChangelog") {
    doLast {
        val versionCode: Int? = android.defaultConfig.versionCode
        val changelogFile: File =
            file("$rootDir/fastlane/metadata/android/en-US/changelogs/$versionCode.txt")
        if (!changelogFile.exists()) {
            throw GradleException(
                "Fastlane changelog missing: expecting file '$changelogFile'",
            )
        }
    }
}
tasks.findByName("build")!!.dependsOn(tasks.findByName("checkFastlaneChangelog")!!)

/**
 * Generates a versionCode based on the given semVer String.
 *
 * @param semVer e.g. 1.3.1
 * @return e.g. 10301 (-> 1 03 01)
 */
fun generateVersionCode(semVer: String): Int =
    semVer
        .split('.')
        .map { Integer.parseInt(it) }
        .reduce { sum, value -> sum * 100 + value }

val composeReports = layout.buildDirectory.dir("compose-reports").takeIf { providers.gradleProperty("composeReports").isPresent }

// The SBOM the release workflow attaches to each GitHub release: every
// library the F-Droid release APK ships (its runtime classpath, direct and
// transitive), with versions, hashes and licences, as CycloneDX JSON.
// `./gradlew :app:cyclonedxDirectBom` writes it to app/build/reports/sbom/.
val sbomConfiguration = "fdroidReleaseRuntimeClasspath"
val sbomFileName = "currencix-sbom.cdx"

tasks.named<CyclonedxDirectTask>("cyclonedxDirectBom") {
    includeConfigs.set(listOf(sbomConfiguration))
    projectType.set(Component.Type.APPLICATION)
    schemaVersion.set(Version.VERSION_16)
    componentGroup.set("com.eliormachlev")
    componentName.set("CurrenciX")
    componentVersion.set(android.defaultConfig.versionName)
    licenseChoice.set(LicenseChoice().apply { addExpression(Expression("GPL-3.0-or-later")) })
    jsonOutput.set(layout.buildDirectory.file("reports/sbom/$sbomFileName.json"))
    xmlOutput.set(layout.buildDirectory.file("reports/sbom/$sbomFileName.xml"))
}

// JDK value types (BigDecimal, java.time) count as stable, so composables
// taking rates and dates can skip recomposing (see compose-stability.conf).
composeCompiler {
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose-stability.conf"))
    // -PcomposeReports: the compiler's report of each composable and how
    // stable its parameters are (build/compose-reports/*-composables.txt),
    // which CI's stability check reads. Off otherwise, to keep builds lean.
    if (composeReports != null) reportsDestination.set(composeReports)
}

// The report is something compiling writes, so it's declared as an output of
// the compile: a compile taken from the build cache (as on a PR that changes
// no code) then brings its report back instead of leaving none.
if (composeReports != null) {
    tasks.withType<KotlinCompile>().configureEach { outputs.dir(composeReports) }
}
