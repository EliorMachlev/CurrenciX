package com.eliormachlev.currencix.screenshots

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.view.preference.compose.PreferenceRow
import com.eliormachlev.currencix.view.preference.compose.PreferenceSection
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal

// Fees screen — three sections mirroring FeesScreen (global exchange /
// global bank / specific pair). Bypasses FeeManagerViewModel + Database
// by feeding hand-authored Fee samples straight into the primitives, per
// the CartScreenshotTest pattern.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.PIXEL_5)
class FeesScreenScreenshotTest {
    @Test fun feesScreenPopulated() =
        captureMatrix("fees_screen_populated") {
            FeesScreenPreview(
                exchange = SAMPLE_EXCHANGE,
                bank = SAMPLE_BANK,
                pairs = SAMPLE_PAIRS,
            )
        }

    @Test fun feesScreenEmpty() =
        captureMatrix("fees_screen_empty") {
            FeesScreenPreview(exchange = null, bank = null, pairs = emptyList())
        }
}

@Composable
private fun FeesScreenPreview(
    exchange: Fee.GlobalExchange?,
    bank: Fee.GlobalBank?,
    pairs: List<Fee.SpecificPair>,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                horizontal = dimensionResource(id = R.dimen.margin2x),
                vertical = dimensionResource(id = R.dimen.margin1x),
            ),
    ) {
        item {
            PreferenceSection(text = stringResource(id = R.string.fee_section_global_exchange)) {
                if (exchange == null) {
                    PreferenceRow(title = stringResource(id = R.string.fee_empty))
                } else {
                    PreferenceRow(
                        title = exchange.name,
                        summary = formatPercent(exchange.percent),
                    )
                }
            }
        }
        item {
            PreferenceSection(text = stringResource(id = R.string.fee_section_global_bank)) {
                if (bank == null) {
                    PreferenceRow(title = stringResource(id = R.string.fee_empty))
                } else {
                    PreferenceRow(
                        title = bank.name,
                        summary = formatPercent(bank.percent),
                    )
                }
            }
        }
        item {
            PreferenceSection(text = stringResource(id = R.string.fee_section_specific_pair)) {
                if (pairs.isEmpty()) {
                    PreferenceRow(title = stringResource(id = R.string.fee_empty), enabled = false)
                } else {
                    pairs.forEach { fee ->
                        val arrow = if (fee.bothWays) "\u2194" else "\u2192"
                        PreferenceRow(
                            title = fee.name.ifBlank { "${fee.from} $arrow ${fee.to}" },
                            summary = formatPercent(fee.percent),
                        )
                    }
                }
                PreferenceRow(
                    title = stringResource(id = R.string.fee_add),
                    iconRes = R.drawable.ic_add,
                )
            }
        }
    }
}

private fun formatPercent(value: BigDecimal): String = "${value.stripTrailingZeros().toPlainString()} %"

private val SAMPLE_EXCHANGE =
    Fee.GlobalExchange(
        id = "ex-1",
        name = "Wise",
        percent = BigDecimal("0.5"),
        isActive = true,
    )

private val SAMPLE_BANK =
    Fee.GlobalBank(
        id = "bk-1",
        name = "Visa",
        percent = BigDecimal("1.75"),
        isActive = true,
    )

private val SAMPLE_PAIRS =
    listOf(
        Fee.SpecificPair(
            id = "pr-1",
            name = "Trip to Europe",
            percent = BigDecimal("2.0"),
            from = "USD",
            to = "EUR",
            bothWays = true,
        ),
        Fee.SpecificPair(
            id = "pr-2",
            name = "",
            percent = BigDecimal("0.25"),
            from = "GBP",
            to = "JPY",
            bothWays = false,
        ),
    )
