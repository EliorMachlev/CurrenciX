package com.eliormachlev.currencix.ocr

import java.util.Locale

/**
 * The Tesseract languages the app reads receipts in: English (bundled in
 * assets/tessdata) and one per app language, downloaded when needed. [code]
 * names the data file ("heb" → heb.traineddata); [sha256] is that file at
 * tessdata_fast's 4.1.0 tag, checked after every download so a changed or
 * corrupted file is never used.
 */
enum class TessLanguage(
    val code: String,
    val sha256: String,
) {
    ENGLISH("eng", "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2"),
    ARABIC("ara", "e3206d3dc87fd50c24a0fb9f01838615911d25168f4e64415244b67d2bb3e729"),
    BULGARIAN("bul", "aebc9b0fcc8cfaf8a9f38a02bb7b85052bd850744696a2c11cf0081820e5b21e"),
    BENGALI("ben", "31163084c279aaebd376216f0c3d5c17ad4b5fee8db49dae79c20000b5de5964"),
    CATALAN("cat", "250db73cd5b380d2798581295dc12f20d0828cdb335a65d833d12dfdbf57117d"),
    CZECH("ces", "934bcaf97ef3348413263331131c9fa7f55f30db333c711929c124fb635f7e1b"),
    DANISH("dan", "acb1fd074487a31d1294fcdfd7d7c673467ffd8aeacb2ccd61ebcbf04eb4e2fa"),
    GERMAN("deu", "19d219bbb6672c869d20a9636c6816a81eb9a71796cb93ebe0cb1530e2cdb22d"),
    GREEK("ell", "4fba8a0b461038d51f1c20d043d4f2ac38c4e778f1b90830847f7bd8fa3ba726"),
    ESPERANTO("epo", "71181a6a07af3812aeedfa1aa993623424f4b8a6aac3e271b36ec11774e674d9"),
    SPANISH("spa", "6f2e04d02774a18f01bed44b1111f2cd7f3ba7ac9dc4373cd3f898a40ea6b464"),
    ESTONIAN("est", "515d4a773682b286369511e83fe412bcff16a92a886f99c761c1d760a7e30456"),
    PERSIAN("fas", "db1c0a91208aff00d3cf1ed2c1d23f76419afd5f024688b4f71adc3f2ce4a505"),
    FRENCH("fra", "ced037562e8c80c13122dece28dd477d399af80911a28791a66a63ac1e3445ca"),
    CROATIAN("hrv", "9e515d9832ce259dbab550b1cc6b998f8b929faf2edacaaca981b05adb130571"),
    HUNGARIAN("hun", "35067e7cfe102dcdc953f9a758fdfaa6296b17a1ee6d874ee780fa306430b9fb"),
    INDONESIAN("ind", "69786901da87ab8766c1ea7fbb10b28f2110c14da3f6c8f2735df131fba95d88"),
    ICELANDIC("isl", "5ec828c363f3f0062c3caf08216677cf5f3c0c981b05fab445123a16b98f10a9"),
    ITALIAN("ita", "b8f89e1e785118dac4d51ae042c029a64edb5c3ee42ef73027a6d412748d8827"),
    HEBREW("heb", "11f9e43ab227f786352a50f75c94c2e9906f1baba86d93276da19da7ce0904db"),
    MALAYALAM("mal", "bd05cbf1b197e7810d2903419aedb06f9ef77bfedf50b358673c1d18d707cdb4"),
    NORWEGIAN("nor", "0451eb4f8049ae78196806bf878a389a2f40f1386fe038568cf4441226ba6ef2"),
    DUTCH("nld", "ced0e5e046a84c908a6aa7accbef9a232c4a5d9a8276691b81c6ee64d02963f6"),
    POLISH("pol", "c4476cdbc0e33d898d32345122b7be1cbf85ace15f920f06c7714756e1ef79b2"),
    PORTUGUESE("por", "c4932b937207a9514b7514d518b931a99938c02a28a5a5a553f8599ed58b7deb"),
    RUSSIAN("rus", "e16e5e036cce1d9ec2b00063cf8b54472625b9e14d893a169e2b0dedeb4df225"),
    SWEDISH("swe", "f7304988d41f833efebcc2d529df54b1903ecebbc3da1faabd19a0fddd4fe586"),
    TURKISH("tur", "7393381111e1152420fc4092cb44eef4237580d21b92bf30d7d221aad192c6b7"),
    UKRAINIAN("ukr", "d59e53e2bded32f4445f124b4b00240fcac7e8044c003ab822ccb94f0b3db59b"),
    VIETNAMESE("vie", "79df64caf7bcfb2a27df5042ecb6121e196eada34da774956995747636d5bfa1"),
    CHINESE_SIMPLIFIED("chi_sim", "a5fcb6f0db1e1d6d8522f39db4e848f05984669172e584e8d76b6b3141e1f730"),
    CHINESE_TRADITIONAL("chi_tra", "529c5b5797d64b126065cd55f2bb4c7fd7b15790798091b1ff259941a829330b"),
    ;

    /** Where to download it: tessdata_fast's 4.1.0 tag, so the file matches [sha256]. */
    val url: String get() = "$TESSDATA_BASE_URL/$code.traineddata"

    companion object {
        private const val TESSDATA_BASE_URL = "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/4.1.0"

        // Language (ISO 639, old and new codes alike) → data file; Chinese is by script below.
        private val BY_LANGUAGE =
            mapOf(
                "ar" to ARABIC,
                "bg" to BULGARIAN,
                "bn" to BENGALI,
                "ca" to CATALAN,
                "cs" to CZECH,
                "da" to DANISH,
                "de" to GERMAN,
                "el" to GREEK,
                "eo" to ESPERANTO,
                "es" to SPANISH,
                "et" to ESTONIAN,
                "fa" to PERSIAN,
                "fr" to FRENCH,
                "hr" to CROATIAN,
                "hu" to HUNGARIAN,
                "in" to INDONESIAN,
                "id" to INDONESIAN,
                "is" to ICELANDIC,
                "it" to ITALIAN,
                "iw" to HEBREW,
                "he" to HEBREW,
                "ml" to MALAYALAM,
                "nb" to NORWEGIAN,
                "no" to NORWEGIAN,
                "nl" to DUTCH,
                "pl" to POLISH,
                "pt" to PORTUGUESE,
                "ru" to RUSSIAN,
                "sv" to SWEDISH,
                "tr" to TURKISH,
                "uk" to UKRAINIAN,
                "vi" to VIETNAMESE,
            )

        // Where Chinese is written in traditional characters.
        private val TRADITIONAL_REGIONS = setOf("TW", "HK", "MO")
        private const val TRADITIONAL_SCRIPT = "Hant"

        /** The language to read [locale]'s receipts in besides English; null for English or one Tesseract doesn't have here. */
        fun forLocale(locale: Locale): TessLanguage? =
            when (locale.language) {
                "zh" -> {
                    if (locale.script == TRADITIONAL_SCRIPT ||
                        locale.country in TRADITIONAL_REGIONS
                    ) {
                        CHINESE_TRADITIONAL
                    } else {
                        CHINESE_SIMPLIFIED
                    }
                }

                else -> {
                    BY_LANGUAGE[locale.language]
                }
            }
    }
}
