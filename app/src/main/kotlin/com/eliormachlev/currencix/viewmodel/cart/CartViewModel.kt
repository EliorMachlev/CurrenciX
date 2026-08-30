package com.eliormachlev.currencix.viewmodel.cart

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.model.FeeCalculator
import com.eliormachlev.currencix.model.KeyboardType
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.model.SideStacks
import com.eliormachlev.currencix.repository.Database
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class CartViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val db = Database(app)
    private val ratesCache = CartRatesCache(db)

    // The session cart. Backed by the `_cart_current_json` pref, so it
    // survives process death but can be cleared explicitly.
    private val current: MutableLiveData<SavedCart> = MutableLiveData(loadCurrentOrEmpty())

    override fun onCleared() {
        ratesCache.clear()
        super.onCleared()
    }

    fun getCurrentCart(): LiveData<SavedCart> = current

    fun getSavedCarts(): LiveData<List<SavedCart>> = db.getSavedCarts()

    /**
     * Synchronous snapshot for one-shot menu flows (Load / Manage) — the
     * LiveData accessor is a fresh instance per call and never gets observed
     * from those flows, so its `.value` is always null.
     */
    fun getSavedCartsSnapshot(): List<SavedCart> = db.getSavedCartsBlocking()

    fun getFees(): LiveData<List<Fee>> = ratesCache.fees

    fun getExchangeRates(): LiveData<ExchangeRates?> = ratesCache.rates

    /**
     * Currently-selected keyboard type. Same source of truth as the main
     * screen so the cart's slide-up keypad shows the same layout the user
     * picked, and cart taps route to the system IME when that pref is on.
     */
    val keyboardType: LiveData<KeyboardType> = db.getKeyboardType()
    val isExtendedKeypadEnabled: LiveData<Boolean> =
        keyboardType.map { it == KeyboardType.EXPANDED }

    /** Shared with the main screen — same preference gates haptics everywhere. */
    val isHapticFeedbackEnabled: LiveData<Boolean> = db.isHapticFeedbackEnabled()

    // Memoize the derived LiveData instances. Returning a fresh instance from
    // each getter left synchronous `.value` reads at null (the caller's instance
    // has no active observer, so its MediatorLiveData never advances) — which
    // silently zeroed the fee-extra rows even though the observed instance had
    // the right value.
    private val baseCurrency: LiveData<Currency> by lazy { current.map { resolveCurrency(it.currency) } }
    private val destinationCurrency: LiveData<Currency> by lazy {
        current.map { resolveCurrency(it.destinationCurrency ?: it.currency) }
    }
    private val subtotal: LiveData<BigDecimal> by lazy { current.map { subtotalOf(it) } }
    private val total: LiveData<BigDecimal> by lazy {
        MediatorLiveData<BigDecimal>().apply {
            val recompute = {
                value =
                    totalOf(
                        current.value,
                        ratesCache.fees.value.orEmpty(),
                        ratesCache.rates.value,
                        ratesCache.lastActiveExchangeId,
                        ratesCache.lastActiveBankId,
                    )
            }
            addSource(current) { recompute() }
            addSource(ratesCache.fees) { recompute() }
            addSource(ratesCache.rates) { recompute() }
        }
    }

    fun getBaseCurrency(): LiveData<Currency> = baseCurrency

    /** Destination for the running total. Falls back to base when unset. */
    fun getDestinationCurrency(): LiveData<Currency> = destinationCurrency

    /** Subtotal from summing every item's evaluated expression in the base currency. */
    fun getSubtotal(): LiveData<BigDecimal> = subtotal

    /**
     * Total in the destination currency: subtotal → converted at cached rates
     * → reduced by the CONVERTED-side fee stack. ORIGINAL-side fees don't
     * change the displayed total; they surface separately as "true cost" on
     * the base side.
     */
    fun getTotal(): LiveData<BigDecimal> = total

    fun addItem(
        name: String,
        expression: String,
    ) {
        mutate { cart ->
            cart.copy(
                items =
                    cart.items +
                        CartItem(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            expression = expression,
                        ),
            )
        }
    }

    fun updateItem(
        id: String,
        name: String,
        expression: String,
    ) {
        mutateItem(id) { it.copy(name = name, expression = expression) }
    }

    fun removeItem(id: String) {
        mutate { cart ->
            val filtered = cart.items.filter { it.id != id }
            if (filtered.size == cart.items.size) cart else cart.copy(items = filtered)
        }
    }

    fun togglePinned(id: String) {
        mutateItem(id) { it.copy(pinned = !it.pinned) }
    }

    // Move [fromId] to the current position of [toId]. No-op on same id, missing
    // id, or same slot — the drag-reorder gesture fires this even on a release
    // without movement, and we don't want to churn the LiveData or disk write.
    fun reorderItem(
        fromId: String,
        toId: String,
    ) {
        if (fromId == toId) return
        mutate { cart ->
            var fromIdx = -1
            var toIdx = -1
            cart.items.forEachIndexed { i, item ->
                when (item.id) {
                    fromId -> fromIdx = i
                    toId -> toIdx = i
                }
            }
            if (fromIdx < 0 || toIdx < 0 || fromIdx == toIdx) return@mutate cart
            val reordered = cart.items.toMutableList()
            val moved = reordered.removeAt(fromIdx)
            reordered.add(toIdx, moved)
            cart.copy(items = reordered)
        }
    }

    fun setBaseCurrency(currency: Currency) {
        mutate { it.copy(currency = currency.iso4217Alpha()) }
    }

    fun setDestinationCurrency(currency: Currency) {
        mutate { it.copy(destinationCurrency = currency.iso4217Alpha()) }
    }

    /** Swap base and destination; if destination was unset, mirror the base first. */
    fun swapCurrencies() {
        mutate {
            val currentDest = it.destinationCurrency ?: it.currency
            it.copy(currency = currentDest, destinationCurrency = it.currency)
        }
    }

    /** Clear the cart's items but keep the currency the user picked. */
    fun clearItems() {
        mutate { it.copy(items = emptyList()) }
    }

    /**
     * Reset the cart back to a fresh, main-screen-seeded state: no items and
     * currencies re-pulled from the app-wide defaults. Preserves the cart's
     * id/name so a subsequent "Save" still targets the same persisted
     * entry — this is a content reset, not a "delete and start over".
     */
    fun resetToMainDefaults() {
        val cur = current.value ?: return
        val fresh = emptyCart()
        val next =
            cur.copy(
                items = emptyList(),
                currency = fresh.currency,
                destinationCurrency = fresh.destinationCurrency,
            )
        current.value = next
        db.setCurrentCart(next)
    }

    /**
     * Drop any unsaved edits by reverting the working cart to its on-disk
     * counterpart. Carts that were never saved fall back to a fresh, main-
     * seeded cart (there's nothing on disk to restore).
     */
    fun discardChanges() {
        val cur = current.value ?: return
        val restored = if (cur.id.isNotEmpty()) findSaved(cur.id) ?: emptyCart() else emptyCart()
        setCurrent(restored)
    }

    /**
     * Persist the current cart as a named entry. If [id] matches an existing
     * saved cart, that entry is replaced (rename / update semantics).
     */
    fun saveCurrentAs(
        name: String,
        id: String? = null,
    ) {
        val cart = current.value ?: return
        val saved =
            cart.copy(
                id = id ?: UUID.randomUUID().toString(),
                name = name,
                createdAt = System.currentTimeMillis(),
            )
        db.saveCart(saved)
        // Keep the current cart in sync with what was just persisted so a
        // subsequent "Save as" reuses the same id (overwrite semantics).
        setCurrent(saved)
    }

    /**
     * Overwrite the working cart's persisted entry with its current state.
     * Returns false when the cart was never saved (no id) so callers can
     * route to "Save as" as a fallback.
     */
    fun saveCurrent(): Boolean {
        val cart = current.value ?: return false
        if (cart.id.isEmpty()) return false
        val saved = cart.copy(createdAt = System.currentTimeMillis())
        db.saveCart(saved)
        setCurrent(saved)
        return true
    }

    fun loadSaved(id: String) {
        val saved = findSaved(id) ?: return
        // Treat "loaded" as a fresh session — the loaded cart becomes the
        // current cart, but its stored id/name are kept so a subsequent
        // "Save" overwrites the same entry.
        setCurrent(saved)
    }

    fun deleteSaved(id: String) = db.deleteSavedCart(id)

    /**
     * Compare the working cart against its persisted counterpart (matched by
     * id) to decide if there are unsaved edits. A cart with no id counts as
     * dirty as soon as the user has added anything — nothing on disk to fall
     * back to.
     */
    fun hasUnsavedChanges(): Boolean {
        val cur = current.value ?: return false
        if (cur.id.isEmpty()) return cur.items.isNotEmpty()
        val saved = findSaved(cur.id) ?: return true
        return cur.items != saved.items ||
            cur.name != saved.name ||
            cur.currency != saved.currency ||
            cur.destinationCurrency != saved.destinationCurrency
    }

    /** Rename a saved cart in place. No-op if the id isn't found. */
    fun renameSaved(
        id: String,
        name: String,
    ) {
        val existing = findSaved(id) ?: return
        db.saveCart(existing.copy(name = name))
        // Keep the current cart's displayed name in sync if it's the same one.
        if (current.value?.id == id) {
            setCurrent((current.value ?: return).copy(name = name))
        }
    }

    private fun findSaved(id: String): SavedCart? = db.getSavedCartsBlocking().firstOrNull { it.id == id }

    /**
     * Replace the current cart wholesale (used by "Load" and by the file
     * importer). Persists immediately so the change survives process death.
     */
    fun setCurrent(cart: SavedCart) {
        current.value = cart
        db.setCurrentCart(cart)
    }

    /**
     * Per-side fee stacks for the current base/destination pair. Doesn't
     * depend on cart items, so the UI can show inline fee annotations even
     * for an empty cart (same shape as the main screen).
     */
    fun currentSideStacks(): SideStacks {
        val cart = current.value ?: return SideStacks.NEUTRAL
        val (base, dest) = cart.resolvedPair()
        return FeeCalculator.sideStacks(
            ratesCache.lastFees,
            base,
            dest,
            ratesCache.lastActiveExchangeId,
            ratesCache.lastActiveBankId,
        )
    }

    /** Snapshot used by the "Share" flow — computed against the latest fees & rates. */
    fun snapshotForShare(): CartSnapshot? {
        val cart = current.value ?: return null
        if (cart.items.isEmpty()) return null
        val evaluated = cart.items.map { it to evaluateItem(it) }
        val subtotal = evaluated.fold(BigDecimal.ZERO) { acc, (_, value) -> acc + value }
        val (base, dest) = cart.resolvedPair()
        val stacks =
            FeeCalculator.sideStacks(
                ratesCache.lastFees,
                base,
                dest,
                ratesCache.lastActiveExchangeId,
                ratesCache.lastActiveBankId,
            )
        val converted = convertAmount(subtotal, base, dest, ratesCache.lastRates)
        val total = applyConvertedStack(converted, stacks.converted)
        return CartSnapshot(
            cart,
            evaluated,
            subtotal,
            converted,
            stacks,
            total,
            ratesCache.lastFees,
            base,
            dest,
            providerName =
                ratesCache.lastRates
                    ?.provider
                    ?.getName()
                    ?.toString(),
            ratesDate = ratesCache.lastRates?.date,
        )
    }

    private fun mutate(transform: (SavedCart) -> SavedCart) {
        val prev = current.value ?: emptyCart()
        val next = transform(prev)
        // Identity short-circuit: callers that decide the mutation is a no-op
        // return the same instance (`return@mutate cart`) to skip the LiveData
        // emission and disk write.
        if (next === prev) return
        current.value = next
        db.setCurrentCart(next)
    }

    private inline fun mutateItem(
        id: String,
        crossinline transform: (CartItem) -> CartItem,
    ) {
        mutate { cart ->
            var changed = false
            val items =
                cart.items.map {
                    if (it.id == id) {
                        changed = true
                        transform(it)
                    } else {
                        it
                    }
                }
            if (changed) cart.copy(items = items) else cart
        }
    }

    // Every cold start begins with a fresh cart that inherits the main
    // screen's currency pair. Within the same process (e.g. the user backs
    // out of the cart and re-opens it) we keep the working cart so nothing
    // they typed is lost.
    private fun loadCurrentOrEmpty(): SavedCart {
        if (!coldStartConsumed) {
            coldStartConsumed = true
            db.setCurrentCart(null)
            return emptyCart()
        }
        return db.getCurrentCartBlocking() ?: emptyCart()
    }

    private companion object {
        // Process-scoped: resets to false every time the process is (re)created.
        @Volatile
        var coldStartConsumed: Boolean = false
    }

    private fun emptyCart(): SavedCart {
        // Read the main-screen picks synchronously — the LiveData accessors
        // return null until observed, which is why an unobserved lookup here
        // used to fall back to USD even when the user was on a different pair.
        val base =
            db.getLastBaseCurrencyBlocking()?.iso4217Alpha()
                ?: Currency.USD.iso4217Alpha()
        val dest = db.getLastDestinationCurrencyBlocking()?.iso4217Alpha()
        return SavedCart(
            id = "",
            name = "",
            currency = base,
            destinationCurrency = dest.takeIf { it != null && it != base },
            items = emptyList(),
            createdAt = System.currentTimeMillis(),
        )
    }
}

data class CartSnapshot(
    val cart: SavedCart,
    val evaluatedItems: List<Pair<CartItem, BigDecimal>>,
    /** Sum of evaluated items in the base currency. */
    val subtotal: BigDecimal,
    /** Subtotal after currency conversion, before fees. Equals [subtotal] when base == dest. */
    val convertedSubtotal: BigDecimal,
    /** Per-side fee stacks for the current base/destination pair. */
    val sideStacks: SideStacks,
    /** Final displayed total in the destination currency. */
    val total: BigDecimal,
    val fees: List<Fee>,
    val baseCurrency: Currency,
    val destinationCurrency: Currency,
    /** Name of the exchange-rate provider that produced the current rates (e.g. "Frankfurter App"). */
    val providerName: String? = null,
    /** Date the exchange rates were published by the provider. */
    val ratesDate: LocalDate? = null,
) {
    val isConverting: Boolean get() = baseCurrency != destinationCurrency
}
