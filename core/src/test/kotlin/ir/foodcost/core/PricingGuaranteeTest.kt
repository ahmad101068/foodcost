package ir.foodcost.core

import ir.foodcost.core.model.Catalog
import ir.foodcost.core.model.ExtraCost
import ir.foodcost.core.model.SalesChannel
import ir.foodcost.core.money.MAX_RIALS
import ir.foodcost.core.money.PriceRounding
import ir.foodcost.core.pricing.*
import ir.foodcost.core.units.Uom
import java.math.BigDecimal
import org.junit.jupiter.api.Test
import kotlin.test.*

class PricingGuaranteeTest {
    private fun calculator(settings: PricingSettings): FoodCostCalculator = FoodCostCalculator(Catalog(
        listOf(F.ingredient("food")), listOf(F.price("food", "1", Uom.KILOGRAM, 1_000_000)),
        listOf(F.recipe("dish", F.version(listOf(F.item("food", "1000"))).copy(
            extras = listOf(ExtraCost("Wage", 100_000), ExtraCost("Box", 200_000, takeawayOnly = true)),
        ))),
    ), pricing = settings)

    @Test fun everyAffordableQuotePreservesTheTargetAndDoesNotAddAnUnnecessaryStep() {
        val terms = listOf(OnlineTerms(), OnlineTerms(BigDecimal(20), BigDecimal(10)),
            OnlineTerms(BigDecimal("33.33333333333333333333333333333333"), BigDecimal("12.75")),
            OnlineTerms(BigDecimal("99.9")), OnlineTerms(discountPercent = BigDecimal("99.9")),
            OnlineTerms(BigDecimal("99.999999"), BigDecimal("99.99999")))
        for (direction in PriceRounding.Direction.entries) for (step in listOf(1L, 10L, 100_000L, 1_000_000L)) {
            for (online in terms) for (menu in listOf(2_200_000L, 6_350_000L, MAX_RIALS)) {
                val calc = calculator(PricingSettings(rounding = PriceRounding(step, direction), online = online))
                val cost = calc.cost(F.rid("dish"), F.D1)
                val target = calc.pricer.sale(cost, SalesChannel.DINE_IN, menu).remaining
                for (channel in SalesChannel.entries) {
                    val quote = calc.pricer.priceLeaving(target, cost, channel)
                    val context = "$direction step=$step terms=$online menu=$menu channel=$channel"
                    if (quote == null) {
                        assertTrue(calc.pricer.sale(cost, channel, MAX_RIALS).remaining < target, "Affordable quote missing: $context")
                    } else {
                        assertTrue(quote in 1..MAX_RIALS && quote % step == 0L, context)
                        assertTrue(calc.pricer.sale(cost, channel, quote).remaining >= target, context)
                        if (quote > step) assertTrue(calc.pricer.sale(cost, channel, quote - step).remaining < target,
                            "An extra step was added: $context quote=$quote")
                    }
                }
            }
        }
    }

    @Test fun normalMenuRoundingStillHonorsTheChosenDirectionWithoutAMinimum() {
        for ((direction, expected) in mapOf(PriceRounding.Direction.UP to 2_800_000L,
            PriceRounding.Direction.NEAREST to 2_800_000L, PriceRounding.Direction.DOWN to 2_700_000L)) {
            val calc = calculator(PricingSettings(defaultMethod = PricingMethod.TargetFoodCost(BigDecimal(40)),
                rounding = PriceRounding(100_000, direction)))
            assertEquals(expected, calc.evaluate(F.rid("dish"), F.D1).suggestion.menuPrice)
        }
    }

    @Test fun aProfitFloorBeyondTheCeilingIsUnattainableRatherThanAValidPrice() {
        val calc = calculator(PricingSettings(defaultMethod = PricingMethod.Markup(BigDecimal.ZERO),
            minimumGrossProfitRials = MAX_RIALS, rounding = PriceRounding(100_000, PriceRounding.Direction.DOWN)))
        val evaluation = calc.evaluate(F.rid("dish"), F.D1)
        assertTrue(evaluation.suggestion.tooLarge)
        assertEquals(PriceStatus.INCOMPLETE, evaluation.status)
    }

    @Test fun nearlyHundredPercentDiscountStillLeavesPositiveNetSales() {
        val almost = BigDecimal("99." + "9".repeat(40))
        val calc = calculator(PricingSettings(online = OnlineTerms(discountPercent = almost)))
        val sale = calc.pricer.sale(calc.cost(F.rid("dish"), F.D1), SalesChannel.ONLINE, 2_200_000)
        assertTrue(sale.netSales.signum() > 0)
        assertTrue(sale.discount < BigDecimal(sale.price))
    }

    @Test fun nearlyHundredPercentCommissionStillLeavesAPositiveShareBeforeOtherCosts() {
        val almost = BigDecimal("99." + "9".repeat(40))
        val calc = calculator(PricingSettings(online = OnlineTerms(commissionPercent = almost)))
        val sale = calc.pricer.sale(calc.cost(F.rid("dish"), F.D1), SalesChannel.ONLINE, 2_200_000)
        assertTrue(sale.netSales - sale.commission > BigDecimal.ZERO)
        assertTrue(sale.commission < sale.netSales)
    }
}
