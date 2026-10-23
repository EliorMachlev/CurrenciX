package com.eliormachlev.currencix.viewmodel.cart

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import com.eliormachlev.currencix.model.CartExtras
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.model.Person
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.newId
import kotlinx.collections.immutable.ImmutableList
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

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
    }

    fun getCurrentCart(): LiveData<SavedCart> = current

    /** Display-safe name of the working cart. Empty string when unnamed / unset. */
    fun currentCartName(): String = current.value?.name.orEmpty()

    /** Whether the working cart has a persisted counterpart to overwrite. */
    fun currentCartHasId(): Boolean = current.value?.id?.isNotEmpty() == true

    /** Items on the working cart. Empty list when the cart is unset. */
    fun currentCartItems(): List<CartItem> = current.value?.items.orEmpty()

    fun getSavedCarts(): LiveData<List<SavedCart>> = db.carts.savedCarts.liveData

    /**
     * Synchronous snapshot for one-shot menu flows (Load / Manage) — the
     * LiveData accessor is a fresh instance per call and never gets observed
     * from those flows, so its `.value` is always null.
     */
    fun getSavedCartsSnapshot(): List<SavedCart> = db.carts.savedCarts.get()

    fun getFees(): LiveData<ImmutableList<Fee>> = ratesCache.fees

    fun getExchangeRates(): LiveData<ExchangeRates?> = ratesCache.rates

    /**
     * Same source of truth as the main screen so the cart's slide-up keypad
     * shows the same layout the user picked.
     */
    val isExpandedKeypadEnabled: LiveData<Boolean> = db.display.expandedKeypad.liveData

    /** Shared with the main screen — same preference gates haptics everywhere. */
    val isHapticFeedbackEnabled: LiveData<Boolean> = db.display.hapticFeedback.liveData

    /** The totals card shows only the total; remembered across carts and launches. */
    val isTotalsCollapsed: LiveData<Boolean> = db.display.cartTotalsCollapsed.liveData

    fun setTotalsCollapsed(collapsed: Boolean) = db.display.cartTotalsCollapsed.set(collapsed)

    /** The user's date format, for the rates' date on the totals card. */
    val dateFormat: LiveData<String> = db.display.dateFormat.liveData

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
    private val extrasLive: LiveData<CartExtras> by lazy { current.map { it.extras } }
    private val tipLive: LiveData<BigDecimal> by lazy { current.map { tipOf(it) } }
    private val convertedSubtotalLive: LiveData<BigDecimal> by lazy {
        MediatorLiveData<BigDecimal>().apply {
            val recompute = {
                value = convertedSubtotalOf(current.value, ratesCache.rates.value)
            }
            addSource(current) { recompute() }
            addSource(ratesCache.rates) { recompute() }
        }
    }
    private val rowConversionLive: LiveData<RowConversion?> by lazy {
        MediatorLiveData<RowConversion?>().apply {
            val recompute = { value = RowConversion.of(current.value, ratesCache.rates.value) }
            addSource(current) { recompute() }
            addSource(ratesCache.rates) { recompute() }
        }
    }
    private val shoppingModeLive: LiveData<Boolean> by lazy { current.map { it.shoppingMode } }
    private val peopleLive: LiveData<List<Person>> by lazy { current.map { it.extras.people } }
    private val personSharesLive: LiveData<List<Pair<Person, BigDecimal>>> by lazy {
        MediatorLiveData<List<Pair<Person, BigDecimal>>>().apply {
            val recompute = { value = personSharesOf(current.value, totalLive.value ?: BigDecimal.ZERO) }
            addSource(current) { recompute() }
            addSource(totalLive) { recompute() }
        }
    }
    private val basketTotalLive: LiveData<BigDecimal> by lazy {
        MediatorLiveData<BigDecimal>().apply {
            val recompute = { value = basketShareOf(current.value, totalLive.value ?: BigDecimal.ZERO) }
            addSource(current) { recompute() }
            addSource(totalLive) { recompute() }
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

    /** Tip / tax, split and budget (see [CartExtras]). */
    fun getExtras(): LiveData<CartExtras> = extrasLive

    /** The tip / tax on top of the items, in the base currency. */
    fun getTip(): LiveData<BigDecimal> = tipLive

    /** Sets the extras; items for someone no longer in the cart go back to being shared. */
    fun setExtras(extras: CartExtras) {
        mutate { cart ->
            if (cart.extras == extras) return@mutate cart
            val ids = extras.people.mapTo(mutableSetOf()) { it.id }
            val items = cart.items.map { if (it.personId != null && it.personId !in ids) it.copy(personId = null) else it }
            cart.copy(extras = extras, items = items)
        }
    }

    /** The cart's people, when it splits by person; empty otherwise. */
    fun getPeople(): LiveData<List<Person>> = peopleLive

    /** What each person pays of the total, when the cart splits by person; empty otherwise. */
    fun getPersonShares(): LiveData<List<Pair<Person, BigDecimal>>> = personSharesLive

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

    /** Each row's price in the destination currency (see [RowConversion]). */
    fun getRowConversion(): LiveData<RowConversion?> = rowConversionLive

    /**
     * Total in the destination currency: subtotal → converted at cached
     * rates → inflated by the fee stack. Real-world FX fees are charged on
     * the post-conversion amount, so the fee lands here rather than on the
     * base-side subtotal.
     */
    fun getTotal(): LiveData<BigDecimal> = totalLive

    /** Edits to the working cart's rows: add, edit, delete, pin, reorder. */
    val rows = CartRowEdits(::mutate)

    /** The trips saved carts are grouped into, with their spending. */
    val trips = CartTrips(db, ratesCache)

    /** Turns shopping-list mode on or off; ticks stay for the next time it's on. */
    fun setShoppingMode(enabled: Boolean) {
        mutate { cart -> if (cart.shoppingMode == enabled) cart else cart.copy(shoppingMode = enabled) }
    }

    /** Whether the current cart is in shopping-list mode. */
    fun isShoppingMode(): LiveData<Boolean> = shoppingModeLive

    /**
     * What the ticked items come to, in the destination currency, as a share
     * of the total (tip and fees included in proportion). Zero with nothing
     * ticked.
     */
    fun getBasketTotal(): LiveData<BigDecimal> = basketTotalLive

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
        db.carts.currentCart.set(next)
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
        db.carts.currentCart.set(next)
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
                id = id ?: newId(),
                name = name,
                createdAt = System.currentTimeMillis(),
            )
        db.carts.saveCart(saved)
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
        db.carts.saveCart(saved)
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

    fun deleteSaved(id: String) = db.carts.deleteSavedCart(id)

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
        db.carts.saveCart(existing.copy(name = name))
        // Keep the current cart's displayed name in sync if it's the same one.
        if (current.value?.id == id) {
            setCurrent((current.value ?: return).copy(name = name))
        }
    }

    private fun findSaved(id: String): SavedCart? =
        db.carts.savedCarts
            .get()
            .firstOrNull { it.id == id }

    /**
     * Replace the current cart wholesale (used by "Load" and by the file
     * importer). Persists immediately so the change survives process death.
     */
    fun setCurrent(cart: SavedCart) {
        current.value = cart
        db.carts.currentCart.set(cart)
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
        val tip = tipOf(cart)
        val converted = convertAmount(subtotal + tip, base, dest, ratesSnapshot.rates)
        val total = converted.multiply(feeStack, MathContext.DECIMAL128)
        return CartSnapshot(
            cart,
            evaluated,
            subtotal,
            tip,
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
        db.carts.currentCart.set(next)
    }

    // Every cold start begins with a fresh cart that inherits the main
    // screen's currency pair. Within the same process (e.g. the user backs
    // out of the cart and re-opens it) we keep the working cart so nothing
    // they typed is lost.
    private fun loadCurrentOrEmpty(): SavedCart {
        if (!coldStartConsumed) {
            coldStartConsumed = true
            db.carts.currentCart.set(null)
            return emptyCart()
        }
        return db.carts.currentCart.get() ?: emptyCart()
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
        val base = mainBase ?: db.lastState.lastBaseCurrency.get() ?: Currency.USD
        val proposedDest = mainDest ?: db.lastState.lastDestinationCurrency.get()
        val dest = proposedDest?.takeIf { it != base } ?: distinctFrom(base)
        return base to dest
    }

    // Belt-and-braces fallback for [emptyCart]. Prefer the first currency from
    // the cached rates that isn't [base] — that way the pair we seed matches
    // something the user's active provider actually quotes. Falls back to
    // USD/EUR only when no rates are cached (e.g. clean install before the
    // first refresh).
    private fun distinctFrom(base: Currency): Currency {
        db.rates
            .getRateListBlocking()
            .firstOrNull { it.currency != base }
            ?.let { return it.currency }
        return if (base == Currency.USD) Currency.EUR else Currency.USD
    }
}

data class CartSnapshot(
    val cart: SavedCart,
    val evaluatedItems: List<Pair<CartItem, BigDecimal>>,
    /** Sum of evaluated items in the base currency. */
    val subtotal: BigDecimal,
    /** Tip / tax on top of [subtotal], in the base currency (zero without one). */
    val tip: BigDecimal,
    /** Subtotal plus tip after currency conversion. Equals subtotal + tip when base == dest. */
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
