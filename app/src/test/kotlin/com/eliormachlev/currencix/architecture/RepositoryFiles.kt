package com.eliormachlev.currencix.architecture

import java.io.File

/** The checkout's root (where `gradlew` is), whichever directory the tests run from. */
internal fun repositoryRoot(): File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "gradlew").exists() }
