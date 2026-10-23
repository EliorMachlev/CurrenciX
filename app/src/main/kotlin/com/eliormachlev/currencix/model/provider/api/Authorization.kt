package com.eliormachlev.currencix.model.provider.api

/**
 * The header a keyed provider's key travels in, never the URL: a URL lands in
 * the HTTP log and is stored with every cached response, a header is neither
 * (and debug builds' HTTP inspector redacts it).
 */
internal const val AUTHORIZATION = "Authorization"
