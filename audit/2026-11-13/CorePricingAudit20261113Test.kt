package ir.foodcost.core

import ir.foodcost.core.model.Catalog
import ir.foodcost.core.model.ExtraCost
import ir.foodcost.core.model.SalesChannel
import ir.foodcost.core.money.PriceRounding
import ir.foodcost.core.pricing.*
import ir.foodcost.core.units.Uom
import java.math.BigDecimal
import java.math.MathContext
import org.junit.jupiter.api.Test
import kotlin.test.*

/** Independent audit against the unmodified e90ff8b production sources. All money is rials. */
class CorePricingAudit20261113Test {
    private val oracle = MathContext(80)
    private fun calculator(settings: PricingSettings = PricingSettings()): FoodCostCalculator {
        val catalog = Catalog(
            listOf(F.ingredient("audit-food")),
            listOf(F.price("audit-food", "1", Uom.KILOGRAM, 1_000_000)),
            listOf(F.recipe("audit-dish", F.version(listOf(F.item("audit-food", "1000"))).copy(
                extras = listOf(ExtraCost("Wage", 100_000), ExtraCost("Box", 200_000, takeawayOnly = true)),
            ))),
        )
        return FoodCostCalculator(catalog, pricing = settings)
    }
    private fun close(expected: BigDecimal, actual: BigDecimal, name: String) {
        assertTrue(expected.subtract(actual).abs() <= BigDecimal("0.000000000001"), "$name: expected=$expected actual=$actual")
    }

    @Test fun independentFoodBasisOracleForAllThreeMethodsAndVat() {
        val hundred = BigDecimal(100)
        for (text in listOf("1", "5", "10", "20", "25", "30", "32", "35", "40", "50", "65", "75", "90", "99", "99.999")) {
            val foodPercent = BigDecimal(text)
            val margin = hundred.subtract(foodPercent)
            val markup = margin.multiply(hundred).divide(foodPercent, oracle)
            val expected = BigDecimal(1_000_000).multiply(hundred).divide(foodPercent, oracle)
            for (vatIncluded in listOf(false, true)) {
                for (method in listOf(PricingMethod.TargetFoodCost(foodPercent), PricingMethod.TargetMargin(margin), PricingMethod.Markup(markup))) {
                    val settings = PricingSettings(defaultMethod = method, menuPriceIncludesVat = vatIncluded, rounding = PriceRounding(1))
                    val calc = calculator(settings)
                    val suggestion = calc.evaluate(F.rid("audit-dish"), F.D1).suggestion
                    close(expected, suggestion.netPrice, "$text $method")
                    close(expected.multiply(if (vatIncluded) BigDecimal("1.1") else BigDecimal.ONE), suggestion.grossPrice, "VAT $method")
                }
            }
        }
    }

    @Test fun fullCostGoalsIncludeWagesAndOverheadButExcludeTakeawayBoxInDineIn() {
        val markup = BigDecimal(200).divide(BigDecimal(3), oracle)
        for (method in listOf(PricingMethod.TargetMargin(BigDecimal(40), CostBasis.FULL_COST), PricingMethod.Markup(markup, CostBasis.FULL_COST))) {
            val calc = calculator(PricingSettings(defaultMethod = method, rounding = PriceRounding(1), overhead = Overhead(3_000_000, BigDecimal(30))))
            val evaluation = calc.evaluate(F.rid("audit-dish"), F.D1)
            close(BigDecimal(1_200_000), evaluation.suggestion.fullCost, "Full cost")
            close(BigDecimal(2_000_000), evaluation.suggestion.netPrice, "Full-cost target")
            val sale = calc.pricer.sale(evaluation.cost, SalesChannel.DINE_IN, 2_200_000)
            close(BigDecimal(40), assertNotNull(sale.marginPercent), "Actual full-cost margin")
        }
    }

    private fun verifySameRemaining(direction: PriceRounding.Direction) {
        val calc = calculator(PricingSettings(rounding = PriceRounding(100_000, direction)))
        val cost = calc.cost(F.rid("audit-dish"), F.D1)
        val before = calc.pricer.sale(cost, SalesChannel.DINE_IN, 2_200_000)
        val quote = assertNotNull(calc.pricer.priceLeaving(before.remaining, cost, SalesChannel.TAKEAWAY))
        val after = calc.pricer.sale(cost, SalesChannel.TAKEAWAY, quote)
        println("AUDIT_SAME_REMAINING direction=$direction target=${before.remaining} quote=$quote actual=${after.remaining} shortfall=${before.remaining.subtract(after.remaining)}")
        assertTrue(after.remaining >= before.remaining, "Same-remaining quote must retain at least ${before.remaining}, got ${after.remaining} at $quote with $direction")
    }

    @Test fun sameRemainingQuoteWithRoundingUp() = verifySameRemaining(PriceRounding.Direction.UP)
    @Test fun sameRemainingQuoteWithRoundingNearest() = verifySameRemaining(PriceRounding.Direction.NEAREST)
    @Test fun sameRemainingQuoteWithRoundingDown() = verifySameRemaining(PriceRounding.Direction.DOWN)

    @Test fun minimumGrossProfitMustRemainAMinimumAfterMenuRounding() {
        val failures = mutableListOf<String>()
        for (direction in listOf(PriceRounding.Direction.NEAREST, PriceRounding.Direction.DOWN)) {
            val calc = calculator(PricingSettings(defaultMethod = PricingMethod.Markup(BigDecimal.ZERO), minimumGrossProfitRials = 200_000,
                rounding = PriceRounding(100_000, direction)))
            val suggestion = calc.evaluate(F.rid("audit-dish"), F.D1).suggestion
            println("AUDIT_MINIMUM direction=$direction menu=${suggestion.menuPrice} grossProfit=${suggestion.grossProfit} minimum=200000")
            if (suggestion.grossProfit < BigDecimal(200_000)) failures += "$direction: ${suggestion.grossProfit} < 200000"
        }
        assertTrue(failures.isEmpty(), failures.joinToString())
    }

    @Test fun validCommissionExtremelyCloseTo100ReturnsNoAffordableQuoteWithoutCrashing() {
        val almost100 = BigDecimal("99." + "9".repeat(40))
        val calc = calculator(PricingSettings(online = OnlineTerms(commissionPercent = almost100)))
        val cost = calc.cost(F.rid("audit-dish"), F.D1)
        val target = calc.pricer.sale(cost, SalesChannel.DINE_IN, 2_200_000).remaining
        assertNull(calc.pricer.priceLeaving(target, cost, SalesChannel.ONLINE))
    }

    @Test fun validDiscountExtremelyCloseTo100ReturnsNoAffordableQuoteWithoutCrashing() {
        val almost100 = BigDecimal("99." + "9".repeat(40))
        val calc = calculator(PricingSettings(online = OnlineTerms(discountPercent = almost100)))
        val cost = calc.cost(F.rid("audit-dish"), F.D1)
        val target = calc.pricer.sale(cost, SalesChannel.DINE_IN, 2_200_000).remaining
        assertNull(calc.pricer.priceLeaving(target, cost, SalesChannel.ONLINE))
    }
}
