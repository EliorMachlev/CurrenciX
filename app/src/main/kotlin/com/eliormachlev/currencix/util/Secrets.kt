package com.eliormachlev.currencix.util

/** Overwrites a password held in memory once it's no longer needed. */
fun CharArray.wipe() = fill('\u0000')

/** Overwrites key or password bytes held in memory once they're no longer needed. */
fun ByteArray.wipe() = fill(0)
