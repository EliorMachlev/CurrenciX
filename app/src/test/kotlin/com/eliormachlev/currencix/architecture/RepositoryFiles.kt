package com.eliormachlev.currencix.architecture

import java.io.File

/** The repository's root (the folder holding `gradlew`), whichever module's folder a test runs in. */
internal fun repositoryRoot(): File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "gradlew").exists() }
