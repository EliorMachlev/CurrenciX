package com.eliormachlev.currencix.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext

class FeeCalculatorTest {
    private fun bd(s: String) = BigDecimal(s)

    private fun globalExchange(
        id: String,
        percent: String,
        markup: Boolean = true,
        isActive: Boolean = true,
        name: String = "",
        feeSide: FeeSide = FeeSide.ORIGINAL,
    ) = Fee.GlobalExchange(
        id = id,
        name = name,
        percent = bd(percent),
        isMarkup = markup,
        isActive = isActive,
        feeSide = feeSide,
    )

    private fun globalBank(
        id: String,
        percent: String,
        markup: Boolean = true,
        isActive: Boolean = true,
        name: String = "",
        feeSide: FeeSide = FeeSide.ORIGINAL,
    ) = Fee.GlobalBank(
        id = id,
        name = name,
        percent = bd(percent),
        isMarkup = markup,
        isActive = isActive,
        feeSide = feeSide,
    )

    private fun pair(
        id: String,
        percent: String,
        from: String,
        to: String,
        bothWays: Boolean = false,
        markup: Boolean = true,
        isActive: Boolean = true,
        name: String = "",
        feeSide: FeeSide = FeeSide.ORIGINAL,
    ) = Fee.SpecificPair(
        id = id,
        name = name,
        percent = bd(percent),
        isMarkup = markup,
        from = from,
        to = to,
        bothWays = bothWays,
        isActive = isActive,
        feeSide = feeSide,
    )

    private fun near(
        expected: String,
        actual: BigDecimal,
        tolerance: String = "0.000001",
    ) {
        val diff = (bd(expected) - actual).abs()
        assertTrue(
            "expected ~$expected but got $actual",
            diff <= bd(tolerance),
        )
    }

    private fun combined(
        fees: List<Fee>,
        base: Currency?,
        dest: Currency?,
        activeExchangeId: String? = null,
        activeBankId: String? = null,
    ): BigDecimal =
        FeeCalculator
            .sideStacks(fees, base, dest, activeExchangeId, activeBankId)
            .combined

    @Test
    fun `empty fee list returns identity stack`() {
        val stack = combined(emptyList(), Currency.USD, Currency.EUR)
        assertEquals(0, stack.compareTo(BigDecimal.ONE))
    }

    @Test
    fun `global exchange markup applies to any pair`() {
        val fees = listOf(globalExchange("g", "2"))
        near("1.02", combined(fees, Currency.USD, Currency.EUR))
    }

    @Test
    fun `global fees multiply together`() {
        val fees = listOf(globalExchange("g", "2"), globalBank("b", "1"))
        // 1.02 * 1.01 = 1.0302
        near("1.0302", combined(fees, Currency.USD, Currency.EUR))
    }

    @Test
    fun `discount (isMarkup=false) subtracts`() {
        val fees = listOf(globalExchange("g", "5", markup = false))
        near("0.95", combined(fees, Currency.USD, Currency.EUR))
    }

    @Test
    fun `specific pair matches only its direction by default`() {
        val fees = listOf(pair("p", "3", from = "USD", to = "EUR"))
        near("1.03", combined(fees, Currency.USD, Currency.EUR))
        // reverse: no match, back to identity
        assertEquals(
            0,
            combined(fees, Currency.EUR, Currency.USD).compareTo(BigDecimal.ONE),
        )
    }

    @Test
    fun `specific pair with bothWays matches reverse direction`() {
        val fees = listOf(pair("p", "3", from = "USD", to = "EUR", bothWays = true))
        near("1.03", combined(fees, Currency.EUR, Currency.USD))
    }

    @Test
    fun `null currency yields no specific-pair matches`() {
        val fees = listOf(pair("p", "3", from = "USD", to = "EUR"))
        assertEquals(0, combined(fees, null, Currency.EUR).compareTo(BigDecimal.ONE))
        assertEquals(0, combined(fees, Currency.USD, null).compareTo(BigDecimal.ONE))
    }

    @Test
    fun `null currency does not suppress global fees`() {
        val fees = listOf(globalExchange("g", "2"))
        near("1.02", combined(fees, null, null))
    }

    @Test
    fun `stackFactor is commutative product of factors`() {
        val a = listOf(globalExchange("a", "2"), globalExchange("b", "3"))
        val b = listOf(globalExchange("b", "3"), globalExchange("a", "2"))
        assertEquals(
            0,
            FeeCalculator.stackFactor(a).compareTo(FeeCalculator.stackFactor(b)),
        )
    }

    @Test
    fun `applicableFees filters specific pairs but keeps globals`() {
        val fees =
            listOf(
                globalExchange("g", "2"),
                pair("p1", "1", from = "USD", to = "EUR"),
                pair("p2", "1", from = "GBP", to = "JPY"),
            )
        val applicable = FeeCalculator.applicableFees(fees, Currency.USD, Currency.EUR)
        assertEquals(2, applicable.size)
        assertTrue(applicable.any { it.id == "g" })
        assertTrue(applicable.any { it.id == "p1" })
    }

    @Test
    fun `high precision preserved via DECIMAL128`() {
        val fees = listOf(globalExchange("g", "0.1"))
        val stack = combined(fees, Currency.USD, Currency.EUR)
        // 1 + 0.1/100 = 1.001 exactly
        assertEquals(0, stack.round(MathContext.DECIMAL128).compareTo(bd("1.001")))
    }

    @Test
    fun `inactive fees are skipped entirely`() {
        val fees =
            listOf(
                globalExchange("g", "2", isActive = false),
                globalBank("b", "1", isActive = false),
                pair("p", "3", from = "USD", to = "EUR", isActive = false),
            )
        assertEquals(0, combined(fees, Currency.USD, Currency.EUR).compareTo(BigDecimal.ONE))
    }

    @Test
    fun `active picker selects one of several global exchange fees`() {
        val fees =
            listOf(
                globalExchange("wise", "2"),
                globalExchange("revolut", "5"),
            )
        // Wise picked → 1.02 (not multiplicative 1.02 * 1.05 = 1.071)
        near("1.02", combined(fees, null, null, activeExchangeId = "wise"))
        near("1.05", combined(fees, null, null, activeExchangeId = "revolut"))
    }

    @Test
    fun `active picker falls back to first active when id is null`() {
        val fees =
            listOf(
                globalExchange("wise", "2"),
                globalExchange("revolut", "5"),
            )
        near("1.02", combined(fees, null, null, activeExchangeId = null))
    }

    @Test
    fun `active picker skips inactive matches then falls back`() {
        val fees =
            listOf(
                globalExchange("wise", "2", isActive = false),
                globalExchange("revolut", "5"),
            )
        // Picking the inactive one still falls through to the first *active* entry.
        near("1.05", combined(fees, null, null, activeExchangeId = "wise"))
    }

    @Test
    fun `active picker id that doesn't exist falls back to first active`() {
        val fees =
            listOf(
                globalExchange("wise", "2"),
                globalExchange("revolut", "5"),
            )
        near("1.02", combined(fees, null, null, activeExchangeId = "ghost"))
    }

    @Test
    fun `single active bank picker independent of exchange picker`() {
        val fees =
            listOf(
                globalExchange("wise", "2"),
                globalExchange("revolut", "5"),
                globalBank("chase", "1"),
                globalBank("amex", "3"),
            )
        // wise (1.02) * amex (1.03) = 1.0506
        near(
            "1.0506",
            combined(fees, null, null, activeExchangeId = "wise", activeBankId = "amex"),
        )
    }

    @Test
    fun `specific-pair fees still stack together regardless of picker`() {
        val fees =
            listOf(
                pair("p1", "2", from = "USD", to = "EUR"),
                pair("p2", "1", from = "USD", to = "EUR"),
            )
        // both apply: 1.02 * 1.01 = 1.0302
        near("1.0302", combined(fees, Currency.USD, Currency.EUR))
    }

    @Test
    fun `fees split into per-side stacks by their feeSide`() {
        val fees =
            listOf(
                globalExchange("orig", "2", feeSide = FeeSide.ORIGINAL),
                globalBank("conv", "3", feeSide = FeeSide.CONVERTED),
            )
        val sides = FeeCalculator.sideStacks(fees, Currency.USD, Currency.EUR)
        near("1.02", sides.original)
        near("1.03", sides.converted)
        near("1.0506", sides.combined)
    }

    @Test
    fun `all-original fees leave converted side neutral and vice-versa`() {
        val original =
            listOf(
                globalExchange("g", "2", feeSide = FeeSide.ORIGINAL),
                pair("p", "1", from = "USD", to = "EUR", feeSide = FeeSide.ORIGINAL),
            )
        val sidesOriginal = FeeCalculator.sideStacks(original, Currency.USD, Currency.EUR)
        near("1.0302", sidesOriginal.original)
        assertEquals(0, sidesOriginal.converted.compareTo(BigDecimal.ONE))

        val converted =
            listOf(
                globalExchange("g", "2", feeSide = FeeSide.CONVERTED),
                pair("p", "1", from = "USD", to = "EUR", feeSide = FeeSide.CONVERTED),
            )
        val sidesConverted = FeeCalculator.sideStacks(converted, Currency.USD, Currency.EUR)
        assertEquals(0, sidesConverted.original.compareTo(BigDecimal.ONE))
        near("1.0302", sidesConverted.converted)
    }

    @Test
    fun `SideStacks NEUTRAL and isNeutral`() {
        assertTrue(SideStacks.NEUTRAL.isNeutral())
        assertTrue(SideStacks(BigDecimal.ONE, BigDecimal.ONE).isNeutral())
        assertTrue(!SideStacks(bd("1.01"), BigDecimal.ONE).isNeutral())
        assertTrue(!SideStacks(BigDecimal.ONE, bd("0.99")).isNeutral())
    }
}
