# Features

## Converter

**Keypad** (`MainKeypad`, shared with the cart): three columns of tiles beside an operator rail (÷ × − + over a two-row =). `CalculatorInputState` drives it: = makes the result the new amount; keys that would do nothing are dimmed and inert (`KeyAvailability`: a second ".", % with no number, ")" with nothing to close, = with no calculation); each kind of key has its own vibration. The expanded keypad adds 00 / 0 / 000. From 600 dp wide it keeps to one side at 420 dp, with a button to move it across (`keypadOnLeft`). Expressions are evaluated by `util/Arithmetic.kt` (see [build.md](../build.md#dependency-choices)).

- **The pair's pills** (`CurrencyPairRow`): every place showing the pair (converter, cart, chart, Quick conversions) shows two pills with the swap button between, left to right in every language. They're shared elements, so opening the cart or chart flies them across.
- **Recent pairs.** A pair on screen for 2 s is recorded (`RecentPairs`, at most 6, newest first; a pair and its swap are one). Chips under the card switch in one tap through `MainViewModel.setCurrencyPair`, which writes both sides at once. A long-press removes one (asked first, then Undo, as a long-press can land on the wrong chip). With a status pill on the card the chips are compact, giving the pill's height back; without fees the space the fee chain would take is reserved under the card (`FeeChainReserve`), so the chips sit at one height either way. The expanded keypad leaves no room for them; pairs are still recorded.
- **First-run pair:** the phone region's currency from USD (or from EUR where USD is home); USD → EUR without a known region (`HomeCurrency`). It's the stored pair's default.
- **Fallback in use:** the status pill names it; tapping explains that the next refresh tries the main provider first, and offers to change provider.
- **Rate age:** "5 min ago" / "Yesterday" while recent (24 h, or 7 days for date-only providers), ticking each minute; long-press shows the full time.
- **Compare cards:** with two or more card fees, a sheet prices each for the amount on screen, cheapest first; tapping one makes it the fee in force (`compareCards`).
- **Paste amount:** a chip while the clipboard holds text another app copied in the last 10 minutes. Only the clip's description is read until it's tapped (so Android shows no paste notice for opening the app); `PriceParser` then finds the price and its currency becomes the "from" side. A clip is offered once, and the app labels its own copies so they're never offered back. The clipboard always comes from the application context (`clipboardOf`): one taken from the activity kept every destroyed `MainActivity` alive.
- **Currency picker:** the recent pairs' currencies as chips over the unfiltered list; search matches code, name and the countries using the currency, in the app's language and English (`CurrencyCountries`).
- **Percent fields** (`PercentTextField`) keep "%" at the right edge in every language.

## Abroad

Each time the converter comes to the front (`AbroadOfferHost`), it compares the mobile network's country (`networkCountryIso`, no permission, stays on the phone) with the phone's home region. Abroad, in a country with its own currency the pair doesn't show, it offers local → home (`AbroadSheet`); the answer is remembered for that country and forgotten once home, so each trip is asked once. Without a mobile network nothing is offered.

## Outside the app

- **Convert currency** in any app's text-selection menu (`ConvertTextActivity`, `ACTION_PROCESS_TEXT`): a sheet over that app, from cached rates, no network. `PriceParser` reads "€49.99", "49,99 €", "USD 1,234.56"; a shared symbol prefers the user's currencies, codes count only in capitals, letter symbols only as whole words. It converts into the converter's destination, or back to its base when already in the destination.
- **Launcher shortcuts** (`AppShortcuts`): the top three recent pairs and the cart. Dynamic, since a static shortcut needs a fixed package name and debug's differs.
- **Quick conversions** (`QuickConversionsSheet`): the pair at round amounts, 1 to 1,000 times what a US dollar buys, as a power of ten (`quickAmountScale`), or the user's own amounts for that currency (`QuickAmountSettings`, in backups, at most 16 per currency). The typed amount joins them as a tinted tile. With a fee, each tile shows the result and fee over the total with the fee; results round to the user's decimal places. Tap puts an amount in the converter; long-press copies, changes it or keeps the typed one. Share sends the tiles as an image plus text (`shareImageOrText`).
- **Quick Settings tile** (`RateTileService`): the pair and its cached rate; tap refreshes (one-off `RateRefreshWorker`, which asks the tile to redraw), long-press opens the app.
- **Widget** (Glance, `CurrencyWidget`): follows the converter's pair or keeps its own (`WidgetConfigureActivity`, not exported; configuration is optional, so a launcher that can't open it still gets a working widget). From 260 dp wide it draws a 30-day trend line. Buttons step the amount (×1 to ×1K, `WidgetAmounts`) and swap its pair, which from then on is its own. On Android 15+ the picker shows a live preview, republished after rate or pair changes at most hourly (the system rate-limits these).
- All of these launch the app through `ConverterLaunch`. `MainActivity` is exported, so every extra is validated and anything unreadable dropped (fuzzed: `fuzzLaunchIntent`).

## Cart

Items in one currency converted into another (`CartItemRow`): the name edited in place, the price at the end (tap for the keypad), the calculation and the fee-free converted price under it. Each row has a quantity (1–99); **Next** on the keypad moves to the next row's price. Long-press (or TalkBack actions) for Duplicate, Move to top, Delete. **Shopping list** turns drag handles into ticks and adds **In basket**, the ticked share of the total (`basketShareOf`, so tip and fees count in proportion). The totals card collapses, and says **Rates from <date>** once rates are over 4 days old. Share offers text, CSV, PDF and an image (the PDF's drawing as a PNG).

- **Pins and drag order.** Storage keeps the user's order; pinned-first is applied on screen only (`CartItemsList`), so unpinning returns a row to its place. With pins there are two sections, each reordered on its own; on drop, `commitDrag` moves only that row in storage.
- **Extras** (`CartExtras`): tip / tax on top of the items, before fees (a card's fee is charged on everything paid); an even split; a budget showing what's left, red once over. **Split by person**: up to 12 people, each item for someone or shared; each pays their items plus an equal part of the shared ones, as a share of the subtotal so tip and fees count in proportion (`personSharesOf`).

## Trips

Saved carts group into trips (`Trip`: name, home currency, optional budget, destination, start and end). A trip's header shows its carts' total in the home currency at today's rates with the card fees for paying in each cart's currency from home (`summarizeTrips`), its dates or **Ended**, and the cost a day so far against the budget's share. A cart's 🧳 button moves it; ✎ on a header edits or deletes the trip (its carts stay). The saved cart's trip is authoritative: saving over it keeps the trip. Every read applies `Trip.withPlan` (a known destination that isn't home; an end before the start dropped).

**Before you go** (`TripChecklistSheet`, for a trip with a destination that isn't over) lists what to fix while on Wi-Fi: fresh rates for the destination's currency, its receipt language, and a card fee for paying there.

## Receipt scanning

**Scan receipt** takes a photo with the system camera (`TakePicture`, through the `FileProvider`) or picks one (`PickVisualMedia`), so the app has no camera permission. The photo is decoded at most 2400 px long, turned upright from its EXIF, and read on the phone by Tesseract (`readReceipt`), off the main thread. `ReceiptParser` makes items of lines ending (or starting) with a price, gives a words-free quantity line the name above it, and drops totals, tax, payment and change lines by their usual words. The review sheet lists them ticked; the ticked ones become rows. The photo is deleted once read.

- **Where the recognizer lives** (`OcrEngine`, per flavor): the F-Droid app carries `:ocr-engine` (Tesseract4Android). The Play app installs it on demand as the feature module `:ocr` (Play Feature Delivery), so people who never scan don't download ~7 MB of native code per CPU type; it's then loaded without a restart (`SplitCompat`, `SplitInstallHelper.loadLibrary`, `TesseractRecognizer` by reflection, since the base can't link against a module it may lack). The feature module exists only in bundle builds.
- **Languages.** None ships, and none downloads without the user's say. The first scan offers what's needed, each item with its size (the Play reader and English; the app's language as a separate, unticked choice). Each answer is remembered (`ReceiptLanguageSettings`), so a language is asked about once. A cart in a trip, or in a currency with a language of its own, suggests that language first (`TessLanguage.forCurrency`, a curated map: KRW → Korean; none for EUR or USD), kept until the trip ends. **Settings → Receipt languages** adds any of 85 languages, kept for good or until a chosen time; `TessdataExpiryWorker` deletes what's due (also checked before every scan). Receipts are read in the app's language, then the added ones, then English. Downloads run on WorkManager from tessdata_fast's 4.1.0 tag and are kept only when their SHA-256 matches (`Tessdata.install`).

## Chart

Rendered by [Vico](https://github.com/patrykandpatrick/vico) straight into Compose (`TimelineChart`), observing the ViewModel's `LiveData`. Vico replaced SparkView, which was unmaintained and needed a hand-rolled adapter, baseline and scrub handling.

- **Thinning.** At most two points per pair of pixels (`thinForWidth`: each bucket's low and high), so five years of days draw and scrub as a few hundred points with every peak kept; the chart is measured before it's drawn so it never gets the whole series. A new stretch draws in from the start edge (`drawIn`), a clip, no recomposition.
- **Options** (`ChartSettings`: grid, axes labels, extremes, period change, all on by default) swap a Vico component for `null`, which Vico treats as "don't draw".
- **Periods:** week, month, year, 5 years, Custom (Material's date-range picker, up to 10 years). The ViewModel filters one cached window per pair and asks for older history only when a span starts before it (`fetchCovering`). A span with no rates shows a notice instead of the chart, and every statistic handles the empty set.
- **Readouts:** max / min / average / median tiles; the change since the range's start; one neutral line on the latest rate against the period's average (a fact, not advice). Scrubbing shows a bubble; on lift, **Convert at <date>** and **Alert at <rate>** chips in their own strip below the card, so the chart never resizes and a shared chart doesn't carry them. TalkBack hears the chart as a sentence (`timelineSummary`). When the provider has no timeline, the card explains why and offers Try again or Change provider. **Share chart** sends the card as a PNG (`LayerCapture`, as the converter's share).
- **Decimals scale to the data**, ignoring the user's decimal-places setting: `(max − min).getSignificantDecimalPlaces(3)`, capped at 7. A low-volatility pair moves in the 3rd–4th decimal, so 2 places would make min, max and average identical. Intentional; don't "fix" it without weighing that.

## Rate alerts

"Tell me when 1 USD is 3.50 ILS or less": a `RateAlert` (pair, threshold, direction from where the rate stood when set), from the chart or a recent pair's long-press, listed under Settings → Exchange rates → Rate alerts, in `alerts.pb` and backups. After every rate update, app or worker, `ExchangeRatesRepository.store` runs `checkRateAlerts`: each alert reached is taken out in one edit (`takeReached`, so it fires once even if checks overlap) and posted as a notification that opens the pair's chart over the converter. On Android 17+ the rate in the title takes the system's safe / danger colors. Nothing is checked for a pinned past date or while notifications are off (the alerts wait). Setting the first alert asks for `POST_NOTIFICATIONS` and turns background refresh on, since that's what brings rates.

## Problems, kept on the phone

The app sends nothing about itself. **About this app → Report a problem** shares, through an app the user picks, the app and Android versions, the phone model and each recent problem: crashes in the app's code, written by an uncaught-exception handler that then passes the crash on (`filesDir/problems/`, the latest five), and freezes and native crashes from Android's `ApplicationExitInfo`. The row is dimmed while there's nothing to share.
