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
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.model.afterDrop
import com.eliormachlev.currencix.repository.Database
import kotlinx.collections.immutable.ImmutableList
import java.math.BigDecimal
import java.math.MathContext
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

    /** Display-safe name of the working cart. Empty string when unnamed / unset. */
    fun currentCartName(): String = current.value?.name.orEmpty()

    /** Whether the working cart has a persisted counterpart to overwrite. */
    fun currentCartHasId(): Boolean = current.value?.id?.isNotEmpty() == true

    /** Items on the working cart. Empty list when the cart is unset. */
    fun currentCartItems(): List<CartItem> = current.value?.items.orEmpty()

    fun getSavedCarts(): LiveData<List<SavedCart>> = db.getSavedCarts()

    /**
     * Synchronous snapshot for one-shot menu flows (Load / Manage) — the
     * LiveData accessor is a fresh instance per call and never gets observed
     * from those flows, so its `.value` is always null.
     */
    fun getSavedCartsSnapshot(): List<SavedCart> = db.getSavedCartsBlocking()

    fun getFees(): LiveData<ImmutableList<Fee>> = ratesCache.fees

    fun getExchangeRates(): LiveData<ExchangeRates?> = ratesCache.rates

    /**
     * Same source of truth as the main screen so the cart's slide-up keypad
     * shows the same layout the user picked.
     */
    val isExpandedKeypadEnabled: LiveData<Boolean> = db.getExpandedKeypadEnabled()

    /** Shared with the main screen — same preference gates haptics everywhere. */
    val isHapticFeedbackEnabled: LiveData<Boolean> = db.isHapticFeedbackEnabled()

    // Memoize the derived LiveData instances. Returning a fresh instance from
    // each getter left synchronous `.value` reads at null (the caller's instance
    // has no active observer, so its MediatorLiveData never advances) — which
    // silently zeroed the fee-extra rows even though the observed instance had
    // the right value.
    private val baseCurrencyLive: LiveData<Currency> by lazy { current.map { resolveCurrency(it.currency) } }
    private val destinationCurrencyLive: LiveData<Currency> by lazy {
        current.map { resolveCurrency(it.destinationCurrency ?: it.currency) }
    }
    private val subtotalLive: LiveData<BigDecimal> by lazy { current.map { subtotalOf(it) } }
    private val convertedSubtotalLive: LiveData<BigDecimal> by lazy {
        MediatorLiveData<BigDecimal>().apply {
            val recompute = {
                value = convertedSubtotalOf(current.value, ratesCache.rates.value)
            }
            addSource(current) { recompute() }
            addSource(ratesCache.rates) { recompute() }
        }
    }
    private val totalLive: LiveData<BigDecimal> by lazy {
        MediatorLiveData<BigDecimal>().apply {
            val recompute = {
                value = totalOf(current.value, ratesCache.rates.value, currentFeeStack())
            }
            addSource(current) { recompute() }
            addSource(ratesCache.rates) { recompute() }
            addSource(ratesCache.fees) { recompute() }
        }
    }

    fun getBaseCurrency(): LiveData<Currency> = baseCurrencyLive

    /** Destination for the running total. Falls back to base when unset. */
    fun getDestinationCurrency(): LiveData<Currency> = destinationCurrencyLive

    /** Subtotal from summing every item's evaluated expression in the base currency. */
    fun getSubtotal(): LiveData<BigDecimal> = subtotalLive

    /**
     * Fee-free destination subtotal — subtotal after currency conversion but
     * before the fee stack is applied. Used by the footer's fee-annotation
     * row so the delta reads in destination units (where the fee is actually
     * charged).
     */
    fun getConvertedSubtotal(): LiveData<BigDecimal> = convertedSubtotalLive

    /**
     * Total in the destination currency: subtotal → converted at cached
     * rates → inflated by the fee stack. Real-world FX fees are charged on
     * the post-conversion amount, so the fee lands here rather than on the
     * base-side subtotal.
     */
    fun getTotal(): LiveData<BigDecimal> = totalLive

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

    /**
     * Puts a removed [item] back at [index] (clamped to the list) — Undo for
     * a delete. No-op if an item with its id is already there.
     */
    fun restoreItem(
        item: CartItem,
        index: Int,
    ) {
        mutate { cart ->
            if (cart.items.any { it.id == item.id }) return@mutate cart
            val items = cart.items.toMutableList()
            items.add(index.coerceIn(0, items.size), item)
            cart.copy(items = items)
        }
    }

    fun togglePinned(id: String) {
        mutateItem(id) { it.copy(pinned = !it.pinned) }
    }

    /**
     * Applies a finished drag: [movedId] was dropped where it sits in
     * [displayOrder] (see [afterDrop]). A release without movement changes
     * nothing and skips the write.
     */
    fun commitDrag(
        displayOrder: List<String>,
        movedId: String,
    ) {
        mutate { cart ->
            val items = cart.items.afterDrop(displayOrder, movedId)
            if (items == cart.items) cart else cart.copy(items = items)
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

    /**
     * Wipe every item and re-seed the currency pair from [mainBase] / [mainDest]
     * when supplied (delivered by the activity's intent extras), else from the
     * persisted app-wide defaults. Preserves the cart's id/name so a subsequent
     * "Save" still targets the same persisted entry — this is a content reset,
     * not a "delete and start over".
     */
    fun clearCart(
        mainBase: Currency? = null,
        mainDest: Currency? = null,
    ) {
        val cur = current.value ?: return
        val (base, dest) = resolveSeedPair(mainBase, mainDest)
        val next =
            cur.copy(
                items = emptyList(),
                currency = base.iso4217Alpha(),
                destinationCurrency = dest.iso4217Alpha(),
            )
        current.value = next
        db.setCurrentCart(next)
    }

    /**
     * Overlay main's currently-visible pair onto a fresh cart. Called by
     * the cart screen with the pair the converter showed when it opened the
     * cart — trusts the caller's
     * pair over what emptyCart() guessed from prefs, and skips the overwrite
     * once the user has typed anything so we don't stomp their work.
     */
    fun seedFromMain(
        mainBase: Currency?,
        mainDest: Currency?,
    ) {
        if (mainBase == null && mainDest == null) return
        val cur = current.value ?: return
        if (cur.items.isNotEmpty()) return
        val (base, dest) = resolveSeedPair(mainBase, mainDest)
        val next = cur.copy(currency = base.iso4217Alpha(), destinationCurrency = dest.iso4217Alpha())
        if (next == cur) return
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
     * Multiplicative fee stack for the current base/destination pair.
     * Doesn't depend on cart items, so the UI can show inline fee annotations
     * even for an empty cart (same shape as the main screen).
     */
    fun currentFeeStack(): BigDecimal {
        val cart = current.value ?: return BigDecimal.ONE
        val (base, dest) = cart.resolvedPair()
        return ratesCache.feeStackFor(base, dest)
    }

    /** Snapshot used by the "Share" flow — computed against the latest fees & rates. */
    fun snapshotForShare(): CartSnapshot? {
        val cart = current.value ?: return null
        if (cart.items.isEmpty()) return null
        // Freeze fees + rates up front so a mid-flow refresh can't mix
        // recomputed fees with the pre-refresh rate table.
        val ratesSnapshot = ratesCache.snapshot()
        val evaluated = cart.items.map { it to evaluateItem(it) }
        val subtotal = evaluated.fold(BigDecimal.ZERO) { acc, (_, value) -> acc + value }
        val (base, dest) = cart.resolvedPair()
        val feeStack = ratesSnapshot.feeStackFor(base, dest)
        val converted = convertAmount(subtotal, base, dest, ratesSnapshot.rates)
        val total = converted.multiply(feeStack, MathContext.DECIMAL128)
        return CartSnapshot(
            cart,
            evaluated,
            subtotal,
            converted,
            feeStack,
            total,
            ratesSnapshot.fees,
            base,
            dest,
            providerName =
                ratesSnapshot.rates
                    ?.provider
                    ?.getName(getApplication())
                    ?.toString(),
            ratesDate = ratesSnapshot.rates?.date,
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
        val (base, dest) = resolveSeedPair(null, null)
        return SavedCart(
            id = "",
            name = "",
            currency = base.iso4217Alpha(),
            destinationCurrency = dest.iso4217Alpha(),
            items = emptyList(),
            createdAt = System.currentTimeMillis(),
        )
    }

    /**
     * Pick a base/destination pair for a fresh cart. Prefers explicit values
     * from the caller (intent extras from main), falls back to persisted
     * prefs, and always enforces "sides must differ" via [distinctFrom].
     */
    private fun resolveSeedPair(
        mainBase: Currency?,
        mainDest: Currency?,
    ): Pair<Currency, Currency> {
        // Read the persisted picks synchronously — the LiveData accessors
        // return null until observed, which is why an unobserved lookup here
        // used to fall back to USD even when the user was on a different pair.
        val base = mainBase ?: db.getLastBaseCurrencyBlocking() ?: Currency.USD
        val proposedDest = mainDest ?: db.getLastDestinationCurrencyBlocking()
        val dest = proposedDest?.takeIf { it != base } ?: distinctFrom(base)
        return base to dest
    }

    // Belt-and-braces fallback for [emptyCart]. Prefer the first currency from
    // the cached rates that isn't [base] — that way the pair we seed matches
    // something the user's active provider actually quotes. Falls back to
    // USD/EUR only when no rates are cached (e.g. clean install before the
    // first refresh).
    private fun distinctFrom(base: Currency): Currency {
        db.getRateListBlocking().firstOrNull { it.currency != base }?.let { return it.currency }
        return if (base == Currency.USD) Currency.EUR else Currency.USD
    }
}

data class CartSnapshot(
    val cart: SavedCart,
    val evaluatedItems: List<Pair<CartItem, BigDecimal>>,
    /** Sum of evaluated items in the base currency. */
    val subtotal: BigDecimal,
    /** Subtotal after currency conversion. Equals [subtotal] when base == dest. */
    val convertedSubtotal: BigDecimal,
    /** Multiplicative fee stack for the current base/destination pair. */
    val feeStack: BigDecimal,
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
