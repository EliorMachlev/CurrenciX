package com.eliormachlev.currencix.view.scan

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// On-device ML Kit text recognition (Latin script: prices' digits and
// symbols read fine whatever the language). The model comes with Play
// services, so the APK doesn't carry it.
internal val flavorTextReader: TextReader =
    TextReader { context, image ->
        runCatching { InputImage.fromFilePath(context, image) }.fold(
            onSuccess = { input ->
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                suspendCancellableCoroutine { cont ->
                    recognizer
                        .process(input)
                        .addOnSuccessListener { cont.resume(Result.success(it.text)) }
                        .addOnFailureListener { cont.resume(Result.failure(it)) }
                    cont.invokeOnCancellation { recognizer.close() }
                }
            },
            onFailure = { Result.failure(it) },
        )
    }
