package ir.foodcost.core

import ir.foodcost.core.F.D1
import ir.foodcost.core.F.rid
import ir.foodcost.core.model.Catalog
import ir.foodcost.core.model.ExtraCost
import ir.foodcost.core.model.SalesChannel
import ir.foodcost.core.money.PriceRounding
import ir.foodcost.core.money.PriceRounding.Direction
import ir.foodcost.core.pricing.CostBasis
import ir.foodcost.core.pricing.Metrics
import ir.foodcost.core.pricing.OnlineTerms
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

    /** A plate of 300,000 toman of food with a 15,000 box for takeaway and 20,000 of wages, all in rials. */
    private val plate = Catalog(
        listOf(F.ingredient("food")),
        listOf(F.price("food", "1", Uom.KILOGRAM, 3_000_000)),
        listOf(
            F.recipe(
                "plate",
                F.version(listOf(F.item("food", "1000"))).copy(
                    extras = listOf(ExtraCost("ظرف و کیسه", 150_000, takeawayOnly = true), ExtraCost("دستمزد", 200_000)),
                ),
            ),
        ),
    )

    private val withVat = PricingSettings(rounding = PriceRounding(100_000, Direction.UP))

    @Test
    fun `other costs are counted once, the takeaway box only off the premises, and not in the food cost`() {
        val calc = FoodCostCalculator(plate, pricing = withVat)
        val cost = calc.cost(rid("plate"), D1)
        assertAmount("3000000", cost.foodCostPerPortion)
        assertAmount("200000", cost.extrasPerPortion(SalesChannel.DINE_IN))
        assertAmount("350000", cost.extrasPerPortion(SalesChannel.TAKEAWAY))
        assertAmount("350000", cost.extrasPerPortion(SalesChannel.ONLINE))

        // A food cost target prices the food alone; the other costs come off what is left.
        val target = calc.evaluate(rid("plate"), D1).suggestion
        assertAmount(FoodCostCalculator(dish(3_000_000), pricing = withVat).evaluate(rid("dish"), D1).suggestion.netPrice.toPlainString(), target.netPrice)

        // A margin on the full cost includes them, the box only where it is used.
        val full = withVat.copy(defaultMethod = PricingMethod.TargetMargin(30.bd, CostBasis.FULL_COST))
        val margin = FoodCostCalculator(plate, pricing = full).evaluate(rid("plate"), D1).suggestion
        assertAmount("3200000", margin.fullCost)
        assertAmount("4571428.5714", margin.netPrice)
    }

    @Test
    fun `a sale in each channel takes off its discount, VAT, commission and other costs`() {
        val online = OnlineTerms(commissionPercent = 20.bd, discountPercent = 10.bd, priceUpliftPercent = 15.bd)
        val calc = FoodCostCalculator(plate, pricing = withVat.copy(online = online))
        val cost = calc.cost(rid("plate"), D1)
        val pricer = calc.pricer

        val dineIn = pricer.sale(cost, SalesChannel.DINE_IN, 6_300_000)
        assertAmount("5727272.7273", dineIn.netSales)
        assertAmount("2527272.7273", dineIn.remaining)
        assertAmount("52.381", dineIn.foodCostPercent)

        assertAmount("2377272.7273", pricer.sale(cost, SalesChannel.TAKEAWAY, 6_300_000).remaining)

        // Online: 630,000 plus 15% is 724,500, rounded up to 730,000; 10% off, VAT out, then 20% commission.
        assertEquals(7_300_000L, pricer.channelPrice(6_300_000, SalesChannel.ONLINE))
        assertEquals(6_300_000L, pricer.channelPrice(6_300_000, SalesChannel.TAKEAWAY))
        val sale = pricer.sale(cost, SalesChannel.ONLINE, 7_300_000)
        assertAmount("730000", sale.discount)
        assertAmount("5972727.2727", sale.netSales)
        assertAmount("1194545.4545", sale.commission)
        assertAmount("4544545.4545", sale.registeredCost)
        assertAmount("1428181.8182", sale.remaining)

        // To leave online what a portion leaves in the restaurant, it should sell for 897,917, so 900,000.
        assertEquals(9_000_000L, pricer.priceLeaving(dineIn.remaining, cost, SalesChannel.ONLINE))
        val there = pricer.sale(cost, SalesChannel.ONLINE, 9_000_000).remaining
        val below = pricer.sale(cost, SalesChannel.ONLINE, 8_900_000).remaining
        assert(there >= dineIn.remaining && below < dineIn.remaining)
    }

    @Test
    fun `pricing backwards gives the most a portion may cost for its menu price`() {
        val fc = FoodCostCalculator(plate, pricing = withVat)
        val recipe = fc.catalog.recipe(rid("plate"))!!
        val max = fc.pricer.maxCost(recipe, 6_300_000)
        assertAmount("2004545.4545", max.amount) // 572,727 net at a 35% food cost
        assertEquals(CostBasis.FOOD_COST, max.basis)

        val margin = withVat.copy(defaultMethod = PricingMethod.TargetMargin(30.bd, CostBasis.FULL_COST))
        val full = FoodCostCalculator(plate, pricing = margin).pricer.maxCost(recipe, 6_300_000)
        assertAmount("4009090.9091", full.amount)
        assertEquals(CostBasis.FULL_COST, full.basis)
    }
}
