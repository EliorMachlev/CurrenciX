package com.eliormachlev.currencix.util

import java.util.UUID

/** A new random id for a saved thing (a cart, a cart item, a fee). */
fun newId(): String = UUID.randomUUID().toString()
