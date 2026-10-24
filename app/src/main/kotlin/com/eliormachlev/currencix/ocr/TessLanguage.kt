package com.eliormachlev.currencix.ocr

import android.content.Context
import android.text.format.Formatter
import java.util.Locale

/**
 * The Tesseract languages the app reads receipts in: English, one per app
 * language, and more for reading receipts abroad. None ships with the app: each is downloaded the first time
 * it's needed, and only once the user has said yes to it by name
 * ([com.eliormachlev.currencix.view.cart.ReceiptScan]). [code] names the data
 * file ("heb" → heb.traineddata), [tag] is its language (for its name in the
 * user's language), [bytes] its size, shown before asking, and [sha256] that
 * file at tessdata_fast's 4.1.0 tag, checked after every download so a
 * changed or corrupted file is never used.
 */
enum class TessLanguage(
    val code: String,
    val tag: String,
    val bytes: Long,
    val sha256: String,
) {
    ENGLISH("eng", "en", 4_113_088, "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2"),
    ARABIC("ara", "ar", 1_432_056, "e3206d3dc87fd50c24a0fb9f01838615911d25168f4e64415244b67d2bb3e729"),
    BULGARIAN("bul", "bg", 1_675_212, "aebc9b0fcc8cfaf8a9f38a02bb7b85052bd850744696a2c11cf0081820e5b21e"),
    BENGALI("ben", "bn", 855_841, "31163084c279aaebd376216f0c3d5c17ad4b5fee8db49dae79c20000b5de5964"),
    CATALAN("cat", "ca", 1_146_012, "250db73cd5b380d2798581295dc12f20d0828cdb335a65d833d12dfdbf57117d"),
    CZECH("ces", "cs", 3_795_684, "934bcaf97ef3348413263331131c9fa7f55f30db333c711929c124fb635f7e1b"),
    DANISH("dan", "da", 2_580_059, "acb1fd074487a31d1294fcdfd7d7c673467ffd8aeacb2ccd61ebcbf04eb4e2fa"),
    GERMAN("deu", "de", 1_525_436, "19d219bbb6672c869d20a9636c6816a81eb9a71796cb93ebe0cb1530e2cdb22d"),
    GREEK("ell", "el", 1_419_514, "4fba8a0b461038d51f1c20d043d4f2ac38c4e778f1b90830847f7bd8fa3ba726"),
    ESPERANTO("epo", "eo", 4_728_393, "71181a6a07af3812aeedfa1aa993623424f4b8a6aac3e271b36ec11774e674d9"),
    SPANISH("spa", "es", 2_294_433, "6f2e04d02774a18f01bed44b1111f2cd7f3ba7ac9dc4373cd3f898a40ea6b464"),
    ESTONIAN("est", "et", 4_458_101, "515d4a773682b286369511e83fe412bcff16a92a886f99c761c1d760a7e30456"),
    PERSIAN("fas", "fa", 431_500, "db1c0a91208aff00d3cf1ed2c1d23f76419afd5f024688b4f71adc3f2ce4a505"),
    FRENCH("fra", "fr", 1_130_365, "ced037562e8c80c13122dece28dd477d399af80911a28791a66a63ac1e3445ca"),
    CROATIAN("hrv", "hr", 4_103_348, "9e515d9832ce259dbab550b1cc6b998f8b929faf2edacaaca981b05adb130571"),
    HUNGARIAN("hun", "hu", 5_296_273, "35067e7cfe102dcdc953f9a758fdfaa6296b17a1ee6d874ee780fa306430b9fb"),
    INDONESIAN("ind", "id", 1_122_661, "69786901da87ab8766c1ea7fbb10b28f2110c14da3f6c8f2735df131fba95d88"),
    ICELANDIC("isl", "is", 2_278_973, "5ec828c363f3f0062c3caf08216677cf5f3c0c981b05fab445123a16b98f10a9"),
    ITALIAN("ita", "it", 2_701_314, "b8f89e1e785118dac4d51ae042c029a64edb5c3ee42ef73027a6d412748d8827"),
    HEBREW("heb", "he", 961_404, "11f9e43ab227f786352a50f75c94c2e9906f1baba86d93276da19da7ce0904db"),
    MALAYALAM("mal", "ml", 5_275_996, "bd05cbf1b197e7810d2903419aedb06f9ef77bfedf50b358673c1d18d707cdb4"),
    NORWEGIAN("nor", "nb", 3_610_079, "0451eb4f8049ae78196806bf878a389a2f40f1386fe038568cf4441226ba6ef2"),
    DUTCH("nld", "nl", 6_050_296, "ced0e5e046a84c908a6aa7accbef9a232c4a5d9a8276691b81c6ee64d02963f6"),
    POLISH("pol", "pl", 4_765_518, "c4476cdbc0e33d898d32345122b7be1cbf85ace15f920f06c7714756e1ef79b2"),
    PORTUGUESE("por", "pt", 1_982_756, "c4932b937207a9514b7514d518b931a99938c02a28a5a5a553f8599ed58b7deb"),
    RUSSIAN("rus", "ru", 3_861_738, "e16e5e036cce1d9ec2b00063cf8b54472625b9e14d893a169e2b0dedeb4df225"),
    SWEDISH("swe", "sv", 4_167_034, "f7304988d41f833efebcc2d529df54b1903ecebbc3da1faabd19a0fddd4fe586"),
    TURKISH("tur", "tr", 4_550_554, "7393381111e1152420fc4092cb44eef4237580d21b92bf30d7d221aad192c6b7"),
    UKRAINIAN("ukr", "uk", 3_825_102, "d59e53e2bded32f4445f124b4b00240fcac7e8044c003ab822ccb94f0b3db59b"),
    VIETNAMESE("vie", "vi", 531_275, "79df64caf7bcfb2a27df5042ecb6121e196eada34da774956995747636d5bfa1"),
    CHINESE_SIMPLIFIED("chi_sim", "zh-Hans", 2_469_156, "a5fcb6f0db1e1d6d8522f39db4e848f05984669172e584e8d76b6b3141e1f730"),
    CHINESE_TRADITIONAL("chi_tra", "zh-Hant", 2_366_642, "529c5b5797d64b126065cd55f2bb4c7fd7b15790798091b1ff259941a829330b"),

    // Further languages anyone can add (Settings → Receipt languages), for a trip abroad.
    AFRIKAANS("afr", "af", 2_652_786, "126d480bfae95be2a911ed4916465e27bde75fea2da631e21b96762e5f239646"),
    AMHARIC("amh", "am", 5_470_094, "3ec3311833a108e07d58a1152b00c0cf1848752e4f85769d46e8ca2b718a2ccc"),
    AZERBAIJANI("aze", "az", 3_524_799, "a365310848aecb739f19369cb3831d4660fcd9345d798e91a3042455f9ccc9f0"),
    BELARUSIAN("bel", "be", 3_692_098, "9c6668a0b202f3dcfe074b64620d108e1902ca7498a40b5a11b4a3da6112d58f"),
    BOSNIAN("bos", "bs", 2_498_242, "6cc8cc87cf1afbfa6a41febb725dbadb14bed96a46685906440a6eb8a7892f04"),
    WELSH("cym", "cy", 2_208_919, "7f6ee3374749645a7c92dfe773f5c3d6492194d371712ecfd775edc53c363fb4"),
    BASQUE("eus", "eu", 5_175_921, "40e7418296c355d9fd9ca843d115c51e740089e576e887443e61704823cd6624"),
    FILIPINO("fil", "fil", 1_841_715, "2b17f68014ab44b88b5e2334ebcd0ce0090d9af7c6fbef6fab22e0bbd9a934dd"),
    FINNISH("fin", "fi", 7_865_732, "61a04cd62b507c3d9ae0e1cda399e6715ebf49dea9df47897c8acdcd3bd3e13c"),
    IRISH("gle", "ga", 1_181_824, "2fe9ba6119aac7e2a20d6cfb69ed91afe9520f41bc7e3a903f84280f2663858d"),
    GALICIAN("glg", "gl", 2_554_555, "7947619c5544d86849f563bd737ad90dbea9f5319fbd838c4747a7a1cd40b260"),
    GUJARATI("guj", "gu", 1_418_394, "fa69658614b4946a9afae8853d67e0689838803dfa3d12c2e35ec53ee6f8df34"),
    HINDI("hin", "hi", 1_122_751, "4c73ffc59d497c186b19d1e90f5d721d678ea6b2e277b719bee4e2af12271825"),
    ARMENIAN("hye", "hy", 3_463_717, "b701d0d95799a716143dedb0197504e56f27a1bb133d6607ff5778c6988cb67c"),
    JAVANESE("jav", "jv", 2_982_741, "46107b6bed3bf2d9617e5e192e52524e368242543fead1a7175c363dddd400f2"),
    JAPANESE("jpn", "ja", 2_471_260, "1f5de9236d2e85f5fdf4b3c500f2d4926f8d9449f28f5394472d9e8d83b91b4d"),
    KANNADA("kan", "kn", 3_608_331, "bd31e6b6ae93271e3bcf5383d306d8eefbb91542937cd6d735a5930c970e61d8"),
    GEORGIAN("kat", "ka", 2_524_769, "557abb6f1c68bc1b286f1bdd00bb6b82f85a427a91899807dab6c2f6c7986731"),
    KAZAKH("kaz", "kk", 4_734_644, "fcc01eed3815a42b9c6321c4c9d3606f39b166cbf95ade98b7d8d12063eae53d"),
    KHMER("khm", "km", 1_446_926, "47f110575341b322052f3becbefee61a3ecf1ef549352b5d4d33d28afe30d099"),
    KYRGYZ("kir", "ky", 9_928_497, "9777956300900b528d26932cf80693f95e75143433fb851d567194bcc38a31ae"),
    KOREAN("kor", "ko", 1_677_415, "6b85e11d9bbf07863b97b3523b1b112844c43e713df8b66418a081fd1060b3b2"),
    LAO("lao", "lo", 6_386_744, "20124962e93e68121e02c49a949d1f9df5db87dd62e5e4aa578362ca532444f8"),
    LATVIAN("lav", "lv", 2_717_247, "7d9eaf22254b381f18b806cd6cb647862a397a7221adbce3e22cc421793a8847"),
    LITHUANIAN("lit", "lt", 3_154_896, "1e383df5b055583bc01cb5764ecdf74c540753f2cb3f8205e7105361da4bc989"),
    LUXEMBOURGISH("ltz", "lb", 2_606_426, "5516a78efa30c050da7eb0727fec3de59670c19735c00678a67fdad98e4b3ec1"),
    MARATHI("mar", "mr", 2_118_233, "0ba3f2d116972e72fe9e176bc84c38e81dfb6670f4ed1f7f6c8e16a27da7cb61"),
    MACEDONIAN("mkd", "mk", 1_600_188, "58622bf154830fa62103359938564aeb8112b929759e22a48224f3ecfaac34c6"),
    MALTESE("mlt", "mt", 2_308_796, "3040b443d5d49e4183da0eaaf58d1033c3a616153d35d2342a56777f9bbb31c7"),
    MONGOLIAN("mon", "mn", 2_137_042, "a151a3806d61ac43619cd383896d551ba5c3b07388ffec6fc83c8c604d677570"),
    MALAY("msa", "ms", 1_747_801, "e41a3e5febfec50c90371eb1cbb17a48b10cad387900e3420b1f134c1b766cba"),
    BURMESE("mya", "my", 4_640_561, "02aa6c25cfe9e583fa7b5d4131eac948f962308983f0f397df077dea58212b03"),
    NEPALI("nep", "ne", 1_002_911, "280ba9450b4f21afbf5985e0de87857b75a972577c50ef0603c141ddde4f1cb8"),
    ODIA("ori", "or", 1_480_066, "36f3135e61d501a3acfad41f5fe60b8e791274fff4c5375c969fdcca980cdbac"),
    PUNJABI("pan", "pa", 497_721, "1ec0907fc3534065ea9ae190c6bb7ec9e5c74fd9d2fa996aaec7407f11ad8131"),
    PASHTO("pus", "ps", 1_772_087, "f15550bfe3a20ca781cf5afb7b2af6ee769c6521cf4bb990848e72856b484600"),
    ROMANIAN("ron", "ro", 2_376_323, "9adfde6b51ba4b97efd10ea37c3070fd3fc2bad7815e81f5c3c198cd96216cc9"),
    SINHALA("sin", "si", 1_727_413, "bc75c6df2375a30c9f7e759fa7d4b58ae3ecf9ce72668a702d01acb13e551422"),
    SLOVAK("slk", "sk", 4_427_661, "fbcc400a9c74c6a13d922fcb1211b655d1b165387b675ed75cd2dbd756b974a5"),
    SLOVENIAN("slv", "sl", 3_003_829, "b937632c17ce5fdf20535d25feec044da10a0c4c8234f4302d5606fd671cf60d"),
    ALBANIAN("sqi", "sq", 1_874_705, "1d89621d9afe8ddf35b403ecf8951edea52c3a960d241f287b1a7b2c5ba8daac"),
    SERBIAN_CYRILLIC("srp", "sr-Cyrl", 2_149_931, "aa41ae3d9cc705e60d398ab38a5c3cc8b772c0d420c7d4f0859beb13d0e321b6"),
    SERBIAN_LATIN("srp_latn", "sr-Latn", 3_281_787, "54b2e001bd8a336bdffc249c1487b8b7cb712f0a031f2551930ba050ebefa2a0"),
    SWAHILI("swa", "sw", 2_167_651, "395439d1ec308535066cbaea9b15e0e4cc81f8609170af76ab7e7e8d3ec42f3e"),
    TAMIL("tam", "ta", 3_237_963, "d02fbec24be4b07e32e80d0ccfc3b6b67a3c5d61c9d0a7c8532677990912c6ec"),
    TELUGU("tel", "te", 2_769_654, "d10691fddd5b67802e1c12800ebb321d3b8bcd8d24a2ac3ff206f93188c04ab5"),
    TAJIK("tgk", "tg", 2_602_685, "7b32ed1374649b9b44b9a20ed07f1e86c1e1004c3bd11ce182496a47a18cb321"),
    THAI("tha", "th", 1_072_600, "294227cc2d1292b0acb28d61d4115c88252b96d466ca90b417cf4cf0c67bf07c"),
    URDU("urd", "ur", 1_398_718, "62e8250ce2a994106e313a82e26a516a39e2cf159d0ce3c5b5008387fd0d555f"),
    UZBEK("uzb", "uz", 6_470_703, "0e39021eefe692906b50d9bd22e580fa57fe43fa35db6846ad8b6369ddc21ea8"),
    DHIVEHI("div", "dv", 1_774_535, "06051412588963b34b663cc59a468dbf10753329f015ccd574ee711e8c6b84c3"),
    HAITIAN_CREOLE("hat", "ht", 1_976_902, "842e489a0e0eb12c72b5cb12e9e6624aae5928de1d097d76445474c263ce8b5a"),
    TIBETAN("bod", "bo", 1_966_440, "1aab1db8dae337dfc8fa0f94a1c137c47e4c7857c3e6b30093ec726cef912a0b"),
    ;

    /** The language's name in [inLocale]'s language ("Hebrew", "עברית"; "Chinese (Simplified)"). */
    fun displayName(inLocale: Locale): String = Locale.forLanguageTag(tag).getDisplayName(inLocale)

    /** Its download's size as the system writes sizes ("961 kB"), shown before asking. */
    fun downloadSize(context: Context): String = Formatter.formatShortFileSize(context, bytes)

    /** Where to download it: tessdata_fast's 4.1.0 tag, so the file matches [sha256]. */
    val url: String get() = "$TESSDATA_BASE_URL/$code.traineddata"

    companion object {
        /** The one host language data comes from. */
        const val DOWNLOAD_HOST = "raw.githubusercontent.com"
        private const val TESSDATA_BASE_URL = "https://$DOWNLOAD_HOST/tesseract-ocr/tessdata_fast/4.1.0"

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
