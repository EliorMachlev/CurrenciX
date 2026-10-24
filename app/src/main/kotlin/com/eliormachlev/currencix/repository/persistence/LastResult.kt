package com.eliormachlev.currencix.repository.persistence

import java.util.concurrent.atomic.AtomicReference

/**
 * [compute], remembering its last input and result: asked again with the
 * [same] input, it answers without computing. Safe from any thread (one
 * atomic swap; two threads may both compute once, and either answer stands).
 *
 * The saved settings use it so a JSON value (a cart, the saved carts, the
 * fees) is parsed only when its stored text changes, not on every read and
 * every write to its store, and encoded once per write.
 */
internal class LastResult<I, O>(
    private val same: (I, I) -> Boolean,
    private val compute: (I) -> O,
) {
    private val last = AtomicReference<Pair<I, O>?>(null)

    operator fun invoke(input: I): O {
        last.get()?.let { (previous, result) -> if (same(previous, input)) return result }
        return compute(input).also { last.set(input to it) }
    }
}
