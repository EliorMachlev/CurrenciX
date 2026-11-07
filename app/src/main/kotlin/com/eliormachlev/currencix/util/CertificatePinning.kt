package com.eliormachlev.currencix.util

import java.security.cert.CertificateException

// What Android's trust manager says when a host's verified chain holds none of
// the keys pinned for it in res/xml/network_security_config.xml
// (NetworkSecurityTrustManager.checkPins). The handshake fails with an
// SSLHandshakeException carrying it as a cause.
private const val PIN_FAILURE = "Pin verification failed"

// How far down the causes to look: the handshake wraps it once or twice.
private const val MAX_CAUSES = 8

/**
 * Whether this is a refused pin: a host serving a chain through none of its
 * pinned certificate authorities. A provider that changed CAs, or someone in
 * between. It fails the same way on every try, until an app update brings
 * new pins.
 */
internal fun Throwable.isPinFailure(): Boolean =
    generateSequence(this) { it.cause }.take(MAX_CAUSES).any { it is CertificateException && it.message == PIN_FAILURE }
