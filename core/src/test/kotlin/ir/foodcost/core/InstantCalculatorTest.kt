package ir.foodcost.core

import ir.foodcost.core.pricing.InstantCalculator
import ir.foodcost.core.pricing.InstantQuoteInput
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class InstantCalculatorTest {
    @Test fun `net price, food cost, margin and markup stay distinct`() {
        val q = InstantCalculator.calculate(InstantQuoteInput(
            foodCostPerPortion = "1000000".bd,
            extraCostPerPortion = "500000".bd,
            menuPrice = "5000000".bd,
            targetFoodCostPercent = "25".bd,
            portions = "3".bd,
        ))
        assertAmount("1500000", q.enteredCostPerPortion)
        assertAmount("20", q.foodCostPercent)
        assertAmount("70", q.enteredCostMarginPercent)
        assertAmount("233.333333", q.enteredCostMarkupPercent)
        assertAmount("3500000", q.remainderPerPortion)
        assertAmount("4500000", q.totalEnteredCost)
        assertAmount("15000000", q.totalNetRevenue)
        assertAmount("4000000", q.targetMenuPrice)
    }

    @Test fun `menu price with VAT is converted to net sale only when configured`() {
        val inclusive = InstantCalculator.calculate(InstantQuoteInput(
            foodCostPerPortion = "1000000".bd,
            menuPrice = "5500000".bd,
            targetFoodCostPercent = "25".bd,
            vatPercent = "10".bd,
            menuPriceIncludesVat = true,
        ))
        assertAmount("5000000", inclusive.netPricePerPortion)
        assertAmount("4400000", inclusive.targetMenuPrice)
        assertAmount("20", inclusive.foodCostPercent)
    }

    @Test fun `no entered selling price leaves ratios unknown rather than making them zero`() {
        val q = InstantCalculator.calculate(InstantQuoteInput(foodCostPerPortion = "250000".bd))
        assertNull(q.netPricePerPortion)
        assertNull(q.foodCostPercent)
        assertNull(q.enteredCostMarginPercent)
        assertNull(q.remainderPerPortion)
    }

    @Test fun `effective purchase costs cannot overflow signed rials`() {
        assertFailsWith<IllegalArgumentException> {
            ir.foodcost.core.model.PurchasePrice(
                ingredientId = ir.foodcost.core.model.IngredientId("test"),
                effectiveFrom = java.time.LocalDate.of(2026, 1, 1),
                packageQty = BigDecimal.ONE,
                packageUom = ir.foodcost.core.units.Uom.KILOGRAM,
                grossRials = Long.MAX_VALUE,
                deliveryRials = 1L,
            )
        }
    }

    @Test fun `invalid price, targets, costs and counts are rejected`() {
        assertFailsWith<IllegalArgumentException> { InstantQuoteInput(foodCostPerPortion = "-1".bd) }
        assertFailsWith<IllegalArgumentException> { InstantQuoteInput(foodCostPerPortion = "1".bd, menuPrice = "0".bd) }
        assertFailsWith<IllegalArgumentException> { InstantQuoteInput(foodCostPerPortion = "1".bd, extraCostPerPortion = "-1".bd) }
        assertFailsWith<IllegalArgumentException> { InstantQuoteInput(foodCostPerPortion = "1".bd, portions = "0".bd) }
        assertFailsWith<IllegalArgumentException> { InstantQuoteInput(foodCostPerPortion = "1".bd, targetFoodCostPercent = "100".bd) }
    }
}
