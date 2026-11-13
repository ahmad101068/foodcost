package ir.foodcost.presentation

import ir.foodcost.core.costing.CostingRules
import ir.foodcost.core.money.DisplayCurrency
import ir.foodcost.core.money.PriceRounding
import ir.foodcost.core.pricing.*
import ir.foodcost.data.clock.HybridClock
import ir.foodcost.data.ledger.Ledger
import ir.foodcost.data.ops.Actor
import ir.foodcost.data.ops.Role
import ir.foodcost.data.store.InMemoryOperationStore
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.*

class PresentationPricingAudit20261113Test {
    private val date = LocalDate.of(2026, 11, 13)
    private val money = MoneyFormat(DisplayCurrency.TOMAN)
    private val pricing = PricingSettings(scope = PricingScope.GENERAL, defaultMethod = PricingMethod.TargetFoodCost(BigDecimal(40)),
        vatPercent = BigDecimal.ZERO, rounding = PriceRounding(1))
    private fun ledger(): Ledger {
        var wall = 1_000_000L
        return Ledger(InMemoryOperationStore(), HybridClock("audit") { wall++ }, Actor("owner", Role.OWNER)).also {
            it.saveSettings(pricing = pricing, costing = CostingRules(defaultMiscPercent = BigDecimal.ZERO), effectiveFrom = date)
        }
    }
    private fun input() = QuickInput(dishName = "Audit dish", menuPrice = "250000", miscPercent = BigDecimal.ZERO,
        rows = listOf(QuickRow("Food", "100000", "1000")),
        extras = listOf(QuickExtra("Wage", "10000"), QuickExtra("Box", "20000", true)))

    @Test fun persistedMethodConversionsReachTheSamePriceInTheActualCalculator() {
        val ledger = ledger()
        val original = SettingsInput.of(ledger.state.pricing, ledger.state.costing, money)
        var previous: String? = null
        for (method in MethodChoice.entries) {
            val converted = original.withMethod(method)
            val checked = assertIs<Checked.Valid<SettingsDraft>>(converted.checkPart(SettingsPart.PRICING, ledger.state.pricing, ledger.state.costing, money))
            ledger.saveSettings(pricing = checked.value.pricing, effectiveFrom = date)
            val result = QuickCalculator.compute(input(), ledger.state, money, date)
            println("AUDIT_CONVERSION method=$method percent=${converted.percent} suggestion=${result.suggestedRials}")
            assertEquals(2_500_000L, result.suggestedRials)
            if (previous != null) assertEquals(previous, result.suggested)
            previous = result.suggested
        }
    }

    @Test fun foodOnlyTargetAndActualFullCostMarginUseDifferentBases() {
        val ledger = ledger()
        val settings = SettingsInput.of(ledger.state.pricing, ledger.state.costing, money)
        val result = QuickCalculator.compute(input(), ledger.state, money, date)
        println("AUDIT_BASES equivalentHint=${settings.equivalents()} actualFoodCost=${result.foodCostPercent} actualMargin=${result.margin} actualMarkup=${result.markup} policy=${result.method}")
        assertEquals("۴۰٪", result.foodCostPercent)
        assertEquals("۵۶٪", result.margin)
        assertEquals("۱۲۷٫۳٪", result.markup)
        assertTrue(assertNotNull(settings.equivalents()).contains("۶۰٪"))
        assertTrue(assertNotNull(settings.equivalents()).contains("۱۵۰٪"))
    }

    @Test fun commissionAcceptedByTheSettingsFormMustNotCrashTheCalculator() {
        val ledger = ledger()
        val settings = SettingsInput.of(ledger.state.pricing, ledger.state.costing, money).copy(onlineCommission = "99." + "9".repeat(40))
        val checked = assertIs<Checked.Valid<SettingsDraft>>(settings.checkPart(SettingsPart.COSTS, ledger.state.pricing, ledger.state.costing, money))
        println("AUDIT_ACCEPTED_COMMISSION value=${checked.value.pricing.online.commissionPercent}")
        ledger.saveSettings(pricing = checked.value.pricing, effectiveFrom = date)
        val result = QuickCalculator.compute(input(), ledger.state, money, date)
        assertTrue(result.channels.isNotEmpty())
        assertNull(result.channels.last().priceForSameRemaining)
    }
}
