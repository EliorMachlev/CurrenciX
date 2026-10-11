import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Tesseract, wrapped: the on-device text recognizer receipt scanning uses,
// with its ~7 MB of native code per CPU type. The F-Droid app carries it;
// the Play app gets it from the on-demand :ocr feature module instead.
plugins {
    id("com.android.library")
}

android {
    namespace = "com.eliormachlev.currencix.ocr.engine"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }
    buildToolsVersion = "37.0.0"

    defaultConfig {
        minSdk { version = release(33) }
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_21)
        targetCompatibility(JavaVersion.VERSION_21)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    // Tesseract 5 for Android (Apache-2.0), from JitPack; see settings.gradle.
    api("com.github.adaptech-cz.Tesseract4Android:tesseract4android:4.8.0")
}
