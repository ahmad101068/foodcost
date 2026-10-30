package ir.foodcost.core

import ir.foodcost.core.F.D1
import ir.foodcost.core.F.rid
import ir.foodcost.core.model.Catalog
import ir.foodcost.core.money.PriceRounding
import ir.foodcost.core.money.PriceRounding.Direction
import ir.foodcost.core.pricing.CostBasis
import ir.foodcost.core.pricing.Metrics
import ir.foodcost.core.pricing.Overhead
import ir.foodcost.core.pricing.PriceStatus
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.core.pricing.PricingSettings
import ir.foodcost.core.units.Uom
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PricingTest {

    /** One dish whose portion costs exactly [costRials]: 1 kg of an ingredient bought at that price. */
    private fun dish(costRials: Long, menuPrice: Long? = null, category: String? = null, pricing: PricingMethod? = null) = Catalog(
        listOf(F.ingredient("food")),
        listOf(F.price("food", "1", Uom.KILOGRAM, costRials)),
        listOf(F.recipe("dish", F.version(listOf(F.item("food", "1000"))), category = category, pricing = pricing)),
        listOfNotNull(menuPrice?.let { F.menuPrice("dish", it) }),
    )

    @Test
    fun `a 35 percent target with 10 percent VAT rounds up to the next 100,000 and keeps exact steps`() {
        val exact = FoodCostCalculator(dish(3_500_000)).evaluate(rid("dish"), D1).suggestion
        assertAmount("10000000", exact.netPrice)
        assertEquals(11_000_000L, exact.menuPrice) // exactly on a step: not pushed to 11,100,000

        val joojeh = FoodCostCalculator(dish(3_321_968)).evaluate(rid("dish"), D1).suggestion
        assertEquals(10_500_000L, joojeh.menuPrice) // 10,440,471 rounded up
        assertAmount("34.8016", joojeh.foodCostPercent) // of the net price at the rounded menu price
    }

    @Test
    fun `rounding ignores the noise of repeating divisions`() {
        val rounding = PriceRounding(100_000, Direction.UP)
        assertEquals(10_000_000L, rounding.apply("10000000.0000000000000000000000001".bd))
        assertEquals(10_100_000L, rounding.apply("10000000.01".bd))
        assertEquals(10_400_000L, PriceRounding(100_000, Direction.NEAREST).apply("10449999".bd))
        assertEquals(10_400_000L, PriceRounding(100_000, Direction.DOWN).apply("10499999".bd))
        assertEquals(100_000L, PriceRounding(100_000, Direction.NEAREST).apply("30000".bd)) // never below one step
        assertEquals(0L, rounding.apply(0.bd))
    }

    @Test
    fun `a margin on the full cost includes the overhead share`() {
        val settings = PricingSettings(
            defaultMethod = PricingMethod.TargetMargin(30.bd, CostBasis.FULL_COST),
            menuPriceIncludesVat = false,
            rounding = PriceRounding(10_000, Direction.UP),
            overhead = Overhead(monthlyFixedRials = 600_000_000, monthlyPortions = 4_000.bd),
        )
        val s = FoodCostCalculator(dish(1_000_000), pricing = settings).evaluate(rid("dish"), D1).suggestion
        assertAmount("150000", s.overhead)
        assertAmount("1642857.1429", s.netPrice) // 1,150,000 ÷ 0.7
        assertEquals(1_650_000L, s.menuPrice)
        assertAmount("500000", s.profitAfterOverhead)
    }

    @Test
    fun `a markup multiplies the cost`() {
        val settings = PricingSettings(defaultMethod = PricingMethod.Markup(150.bd), rounding = PriceRounding())
        val s = FoodCostCalculator(dish(1_000_000), pricing = settings).evaluate(rid("dish"), D1).suggestion
        assertAmount("2500000", s.netPrice)
        assertEquals(2_750_000L, s.menuPrice)
        assertAmount("250000", s.vat)
    }

    @Test
    fun `without VAT in menu prices the suggestion is the net price`() {
        val settings = PricingSettings(menuPriceIncludesVat = false)
        assertEquals(10_000_000L, FoodCostCalculator(dish(3_500_000), pricing = settings).evaluate(rid("dish"), D1).suggestion.menuPrice)
    }

    @Test
    fun `a minimum gross profit lifts prices of cheap items`() {
        val settings = PricingSettings(minimumGrossProfitRials = 500_000)
        val s = FoodCostCalculator(dish(100_000), pricing = settings).evaluate(rid("dish"), D1).suggestion
        assertAmount("600000", s.netPrice) // 100,000 cost + 500,000 profit beats 285,714
        assertEquals(700_000L, s.menuPrice) // 660,000 with VAT, rounded up
    }

    @Test
    fun `the item's own rule beats its category's, which beats the general one`() {
        val settings = PricingSettings(methodByCategory = mapOf("drinks" to PricingMethod.TargetFoodCost(40.bd)))
        assertEquals(PricingMethod.TargetFoodCost(35.bd), settings.methodFor(dish(1).recipes.values.single()))
        assertEquals(PricingMethod.TargetFoodCost(40.bd), settings.methodFor(dish(1, category = "drinks").recipes.values.single()))
        val own = PricingMethod.Markup(100.bd)
        assertEquals(own, settings.methodFor(dish(1, category = "drinks", pricing = own).recipes.values.single()))
    }

    @Test
    fun `the status compares the current price with the unrounded suggestion`() {
        // The suggestion before rounding is 11,000,000.
        fun status(current: Long) = FoodCostCalculator(dish(3_500_000, current)).evaluate(rid("dish"), D1).status
        assertEquals(PriceStatus.OK, status(11_000_000))
        assertEquals(PriceStatus.OK, status(10_890_000)) // 99%
        assertEquals(PriceStatus.NEAR, status(10_450_000)) // 95%
        assertEquals(PriceStatus.UNDER, status(10_000_000)) // 91%
        assertEquals(PriceStatus.NO_PRICE, FoodCostCalculator(dish(3_500_000)).evaluate(rid("dish"), D1).status)
    }

    @Test
    fun `measures at the current price`() {
        val e = FoodCostCalculator(dish(3_500_000, 13_200_000)).evaluate(rid("dish"), D1)
        assertAmount("12000000", e.currentNet)
        assertAmount("29.1667", e.foodCostPercent)
        assertAmount("70.8333", e.grossMarginPercent)
        assertAmount("242.8571", e.markupPercent)
        assertAmount("8500000", e.grossProfit)
        assertAmount("-16.6667", e.changePercent) // the suggestion is 11,000,000
    }

    @Test
    fun `a measure with nothing to divide by is null, not zero`() {
        assertNull(Metrics.foodCostPercent(1_000.bd, 0.bd))
        assertNull(Metrics.grossMarginPercent((-5).bd, 1_000.bd))
        assertNull(Metrics.markupPercent(1_000.bd, 0.bd))
    }
}
