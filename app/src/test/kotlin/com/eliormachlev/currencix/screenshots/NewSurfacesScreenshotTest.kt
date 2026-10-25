package com.eliormachlev.currencix.screenshots

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.MutableLiveData
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.AbroadOffer
import com.eliormachlev.currencix.model.CartExtras
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.CurrencyPair
import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.model.Person
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.model.Trip
import com.eliormachlev.currencix.model.compareCards
import com.eliormachlev.currencix.ocr.TessLanguage
import com.eliormachlev.currencix.util.ReceiptLine
import com.eliormachlev.currencix.view.alert.RateAlertSheet
import com.eliormachlev.currencix.view.cart.ReceiptSetup
import com.eliormachlev.currencix.view.cart.compose.CartExtrasSheet
import com.eliormachlev.currencix.view.cart.compose.CartFooterCard
import com.eliormachlev.currencix.view.cart.compose.CartItemsList
import com.eliormachlev.currencix.view.cart.compose.CartTotals
import com.eliormachlev.currencix.view.cart.compose.NO_PAIR_ACTIONS
import com.eliormachlev.currencix.view.cart.compose.NO_REORDER
import com.eliormachlev.currencix.view.cart.compose.ReceiptLanguageOfferSheet
import com.eliormachlev.currencix.view.cart.compose.ReceiptReviewSheet
import com.eliormachlev.currencix.view.cart.compose.ReceiptSetupOfferSheet
import com.eliormachlev.currencix.view.cart.compose.SavedCartActions
import com.eliormachlev.currencix.view.cart.compose.SavedCartsList
import com.eliormachlev.currencix.view.cart.compose.TripSheet
import com.eliormachlev.currencix.view.cart.compose.cartItemActions
import com.eliormachlev.currencix.view.cart.compose.cartSources
import com.eliormachlev.currencix.view.compose.PairChipContent
import com.eliormachlev.currencix.view.compose.ReadingDirection
import com.eliormachlev.currencix.view.compose.RemoveFromHistorySheet
import com.eliormachlev.currencix.view.convert.ConvertTextSheet
import com.eliormachlev.currencix.view.convert.SelectionConversion
import com.eliormachlev.currencix.view.main.compose.AbroadSheet
import com.eliormachlev.currencix.view.main.compose.CompareCardsSheet
import com.eliormachlev.currencix.view.main.compose.RecentPairsRow
import com.eliormachlev.currencix.view.main.spinner.CurrencyPickerContent
import com.eliormachlev.currencix.view.main.spinner.SearchableCurrencyPicker
import com.eliormachlev.currencix.view.main.spinner.pickerActions
import com.eliormachlev.currencix.view.preference.compose.KeepLanguageSheet
import com.eliormachlev.currencix.view.preference.compose.ReceiptLanguagesList
import com.eliormachlev.currencix.viewmodel.cart.TripSummary
import com.eliormachlev.currencix.viewmodel.preference.ReceiptLanguageRow
import com.eliormachlev.currencix.viewmodel.preference.ReceiptLanguagesState
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

// The surfaces added with recent pairs, cart sections and extras, the
// text-selection popup. (Wallpaper colors aren't here:
// Robolectric has no wallpaper palette, so they'd render as paper and ink.)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.PIXEL_5, application = Application::class)
class NewSurfacesScreenshotTest {
    @get:Rule val shots = ScreenshotRule()

    // What a long-press on a recent pair opens.
    @Test fun removeFromHistorySheet() =
        shots.captureMatrix("remove_from_history_sheet") {
            RemoveFromHistorySheet(
                message = stringResource(R.string.recent_remove_pair, "SEK", "ILS"),
                onConfirm = {},
                onDismiss = {},
            ) { PairChipContent(Currency.SEK, Currency.ILS) }
        }

    @Test fun recentPairs() =
        shots.captureMatrix("recent_pairs") {
            RecentPairsRow(pairs = RECENT_PAIRS, onPick = {}, onRemove = {}, contentPadding = PaddingValues(16.dp))
        }

    // The shorter chips, while the converter's status pill takes room above them.
    @Test fun recentPairsCompact() =
        shots.captureMatrix("recent_pairs_compact") {
            RecentPairsRow(
                pairs = RECENT_PAIRS,
                onPick = {},
                onRemove = {},
                contentPadding = PaddingValues(16.dp),
                onPaste = {},
                compact = true,
            )
        }

    // As at the top of Quick conversions, a sheet that follows Hebrew's
    // direction: the pairs still read from → to, left to right.
    @Test fun recentPairsInReadingDirection() =
        shots.captureMatrix("recent_pairs_reading_direction") {
            ReadingDirection {
                RecentPairsRow(pairs = RECENT_PAIRS, onPick = {}, onRemove = {}, contentPadding = PaddingValues(16.dp))
            }
        }

    // A recent copy on the clipboard: "Paste amount" leads the row.
    @Test fun recentPairsWithPaste() =
        shots.captureMatrix("recent_pairs_paste") {
            RecentPairsRow(pairs = RECENT_PAIRS, onPick = {}, onRemove = {}, contentPadding = PaddingValues(16.dp), onPaste = {})
        }

    @Test fun currencyPickerRecents() =
        shots.captureMatrix("currency_picker_recents") {
            SearchableCurrencyPicker(
                content =
                    CurrencyPickerContent(
                        rates = RATES,
                        stars = persistentListOf(Currency.EUR),
                        recents = persistentListOf(Currency.ILS, Currency.GBP, Currency.JPY),
                        disabledCurrency = Currency.USD,
                    ),
                actions = pickerActions(),
            )
        }

    @Test fun cartSections() =
        shots.captureMatrix("cart_sections") {
            // Laid out the way the language reads, as CartScreen does.
            ReadingDirection {
                CartItemsList(
                    sources = cartSources(MutableLiveData(CART_ITEMS)),
                    actions = cartItemActions(),
                    reorder = NO_REORDER,
                    onAddItem = {},
                    onBackgroundTap = {},
                )
            }
        }

    @Test fun cartFooterExtras() =
        shots.captureMatrix("cart_footer_extras") {
            // Laid out the way the language reads, as CartScreen does.
            ReadingDirection {
                CartFooterCard(
                    totals =
                        CartTotals(
                            baseCurrency = Currency.USD,
                            destCurrency = Currency.ILS,
                            subtotal = BigDecimal("100"),
                            convertedSubtotal = BigDecimal("419.75"),
                            total = BigDecimal("427.15"),
                            feeStack = BigDecimal("1.0176"),
                            extras = CartExtras(tipPercent = BigDecimal("15"), splitWays = 3, budget = BigDecimal("400")),
                            tip = BigDecimal("15"),
                        ),
                    pair = NO_PAIR_ACTIONS,
                    onEditExtras = {},
                )
            }
        }

    // Shopping-list mode with old rates: the basket under the total, and the
    // rates' date at the foot.
    @Test fun cartFooterBasket() =
        shots.captureMatrix("cart_footer_basket") {
            ReadingDirection {
                CartFooterCard(
                    totals = SAMPLE_TOTALS.copy(basket = BigDecimal("181.60"), oldRatesDate = "02/10/26"),
                    pair = NO_PAIR_ACTIONS,
                    onEditExtras = {},
                )
            }
        }

    // Collapsed: the currency row and the total only.
    @Test fun cartFooterCollapsed() =
        shots.captureMatrix("cart_footer_collapsed") {
            ReadingDirection {
                CartFooterCard(totals = SAMPLE_TOTALS, pair = NO_PAIR_ACTIONS, onEditExtras = {}, collapsed = true)
            }
        }

    // Setting a rate alert below today's rate: "…3.5000 ILS or less".
    @Test fun rateAlertSheet() =
        shots.captureMatrix("rate_alert_sheet") {
            RateAlertSheet(pair = CurrencyPair(Currency.USD, Currency.ILS), current = BigDecimal("3.5"), onSet = {}, onDismiss = {})
        }

    // Split by person: each one's share under the total.
    @Test fun cartFooterPeople() =
        shots.captureMatrix("cart_footer_people") {
            ReadingDirection {
                CartFooterCard(
                    totals =
                        SAMPLE_TOTALS.copy(
                            shares = listOf(Person("d", "Dana") to BigDecimal("231.20"), Person("a", "Avi") to BigDecimal("195.95")),
                        ),
                    pair = NO_PAIR_ACTIONS,
                    onEditExtras = {},
                )
            }
        }

    // Two cards for USD → ILS: the cheaper first, the one in use ticked.
    @Test fun compareCardsSheet() =
        shots.captureMatrix("compare_cards_sheet") {
            val visa = Fee.GlobalBank("visa", "Visa abroad", BigDecimal("2.5"))
            val revolut = Fee.GlobalBank("rev", "Revolut", BigDecimal("0.5"))
            CompareCardsSheet(
                costs = compareCards(listOf(visa, revolut), Currency.USD, Currency.ILS, null, BigDecimal("365")),
                currency = Currency.ILS,
                activeCardId = "visa",
                onPick = {},
                onDismiss = {},
            )
        }

    // Saved carts grouped by trip: the trip's spending against its budget, then the rest.
    @Test fun savedCartsTrips() =
        shots.captureMatrix("saved_carts_trips") {
            val trip = Trip("t1", "Japan 2026", Currency.ILS, BigDecimal("3000"))
            // Laid out the way the language reads, as in its sheet.
            ReadingDirection { SavedCartsTripsPreview(trip) }
        }

    @androidx.compose.runtime.Composable
    private fun SavedCartsTripsPreview(trip: Trip) =
        SavedCartsList(
            items =
                listOf(
                    SavedCart("c1", "Ramen night", "JPY", listOf(CartItem("1", "Ramen", "1200")), 0, tripId = "t1"),
                    SavedCart("c2", "Weekly shop", "ILS", listOf(CartItem("2", "Milk", "6.90")), 0),
                ),
            trips = listOf(TripSummary(trip, BigDecimal("1234.50"), 1)),
            actions = SavedCartActions(onPick = {}, onRename = {}, onDelete = {}),
        )

    // A scanned receipt's items, all ticked, waiting to be added.
    @Test fun receiptReviewSheet() =
        shots.captureMatrix("receipt_review_sheet") {
            ReceiptReviewSheet(
                lines =
                    listOf(
                        ReceiptLine("Milk 1L", BigDecimal("6.90")),
                        ReceiptLine("Bread whole wheat", BigDecimal("12.50")),
                        ReceiptLine("Bananas", BigDecimal("7.00")),
                    ),
                currency = Currency.ILS,
                languagePending = false,
                onAdd = {},
                onDismiss = {},
            )
        }

    // The first scan: what it would download, each with its size, the app's language a separate tick.
    @Test fun receiptSetupOfferSheet() =
        shots.captureMatrix("receipt_setup_offer_sheet") {
            ReceiptSetupOfferSheet(
                offer = ReceiptSetup.Offer(reader = true, english = true, language = TessLanguage.HEBREW),
                onDownload = {},
                onDismiss = {},
            )
        }

    // The app's language on its own: download it, or English only.
    @Test fun receiptLanguageOfferSheet() =
        shots.captureMatrix("receipt_language_offer_sheet") {
            ReceiptLanguageOfferSheet(language = TessLanguage.HEBREW, onAnswer = {})
        }

    // Settings → Receipt languages: English kept, Korean until a trip's end, Japanese downloading.
    @Test fun receiptLanguagesList() =
        shots.captureMatrix("receipt_languages_list") {
            ReceiptLanguagesList(
                state =
                    ReceiptLanguagesState(
                        rows =
                            listOf(
                                ReceiptLanguageRow(TessLanguage.ENGLISH, keepUntil = null, downloading = false),
                                ReceiptLanguageRow(
                                    TessLanguage.KOREAN,
                                    keepUntil = Instant.parse("2026-11-14T21:59:00Z"),
                                    downloading = false,
                                ),
                                ReceiptLanguageRow(TessLanguage.JAPANESE, keepUntil = null, downloading = true),
                            ),
                        available = listOf(TessLanguage.THAI),
                    ),
                onAdd = {},
                onEdit = {},
                onDelete = {},
            )
        }

    // Adding Korean: its size, kept for good or until a time.
    @Test fun receiptLanguageKeepSheet() =
        shots.captureMatrix("receipt_language_keep_sheet") {
            KeepLanguageSheet(
                language = TessLanguage.KOREAN,
                initialUntil = Instant.parse("2026-11-14T21:59:00Z"),
                confirmLabel = "Download",
                onConfirm = {},
                onDismiss = {},
            )
        }

    // A trip with where and when: Korea, paying in KRW, for a week in November.
    @Test fun tripSheetPlanned() =
        shots.captureMatrix("trip_sheet_planned") {
            TripSheet(
                initial =
                    Trip(
                        "t",
                        "Korea",
                        Currency.ILS,
                        BigDecimal("8000"),
                        Currency.KRW,
                        LocalDate.of(2026, 11, 7),
                        LocalDate.of(2026, 11, 14),
                    ),
                currencies = listOf(Currency.ILS, Currency.USD),
                onSave = {},
                onDismiss = {},
            )
        }

    // Landed abroad: switch the converter to the local currency?
    @Test fun abroadSheet() =
        shots.captureMatrix("abroad_sheet") {
            AbroadSheet(offer = AbroadOffer("KR", Currency.KRW, Currency.ILS), onSwitch = {}, onDismiss = {})
        }

    @Test fun cartExtrasSheet() =
        shots.captureMatrix("cart_extras_sheet") {
            CartExtrasSheet(
                initial = CartExtras(tipPercent = BigDecimal("15"), splitWays = 3, budget = BigDecimal("400")),
                destination = Currency.ILS,
                onDone = {},
                onDismiss = {},
            )
        }

    @Test fun convertSelection() =
        shots.captureMatrix("convert_selection") {
            ConvertTextSheet(
                text = "€49.99",
                conversion =
                    SelectionConversion.Converted(
                        amount = BigDecimal("49.99"),
                        from = Currency.EUR,
                        to = Currency.ILS,
                        result = BigDecimal("184.21"),
                        rate = BigDecimal("3.6849"),
                        assumedFrom = false,
                    ),
                decimals = 2,
                onOpen = {},
                onDismiss = {},
            )
        }

    @Test fun convertSelectionLargeFont() =
        shots.captureLargeFont("convert_selection") {
            ConvertTextSheet(
                text = "€49.99",
                conversion =
                    SelectionConversion.Converted(
                        amount = BigDecimal("49.99"),
                        from = Currency.EUR,
                        to = Currency.ILS,
                        result = BigDecimal("184.21"),
                        rate = BigDecimal("3.6849"),
                        assumedFrom = false,
                    ),
                decimals = 2,
                onOpen = {},
                onDismiss = {},
            )
        }

    private companion object {
        val RECENT_PAIRS =
            persistentListOf(
                CurrencyPair(Currency.USD, Currency.ILS),
                CurrencyPair(Currency.EUR, Currency.GBP),
                CurrencyPair(Currency.JPY, Currency.USD),
            )
        val RATES =
            persistentListOf(
                Rate(Currency.USD, BigDecimal.ONE),
                Rate(Currency.EUR, BigDecimal("0.88")),
                Rate(Currency.GBP, BigDecimal("0.76")),
                Rate(Currency.ILS, BigDecimal("3.68")),
                Rate(Currency.JPY, BigDecimal("148.2")),
            )
        val CART_ITEMS =
            persistentListOf(
                CartItem(id = "1", name = "Coffee", expression = "4.50"),
                CartItem(id = "2", name = "Bread", expression = "3.25 + 0.50", pinned = true),
                CartItem(id = "3", name = "Milk", expression = "2.99 × 2"),
                CartItem(id = "4", name = "Olive oil", expression = "12.90", pinned = true),
            )
    }
}

// A converted cart with a tip and a fee, for the footer's states.
private val SAMPLE_TOTALS =
    CartTotals(
        baseCurrency = Currency.USD,
        destCurrency = Currency.ILS,
        subtotal = BigDecimal("100"),
        convertedSubtotal = BigDecimal("419.75"),
        total = BigDecimal("427.15"),
        feeStack = BigDecimal("1.0176"),
        extras = CartExtras(tipPercent = BigDecimal("15")),
        tip = BigDecimal("15"),
    )
