package com.eliormachlev.currencix.util

import java.io.IOException

/**
 * Thrown when a request completed but returned a non-2xx status. Distinct
 * class so the repository's error handler can format an "HTTP nnn" message
 * without inspecting message strings. Network-layer failures (timeouts,
 * DNS, socket resets) propagate as their original exception types so the
 * same handler can pattern-match on them (SocketTimeoutException etc.).
 */
class ApiHttpError(
    val statusCode: Int,
    message: String? = null,
) : IOException(message ?: "HTTP $statusCode")
