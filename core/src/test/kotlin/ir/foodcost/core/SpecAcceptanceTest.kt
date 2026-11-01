package ir.foodcost.core

import ir.foodcost.core.F.D1
import ir.foodcost.core.F.D2
import ir.foodcost.core.F.rid
import ir.foodcost.core.model.Catalog
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.model.SalesChannel
import ir.foodcost.core.money.DisplayCurrency
import ir.foodcost.core.money.PriceRounding
import ir.foodcost.core.pricing.CostBasis
import ir.foodcost.core.pricing.Metrics
import ir.foodcost.core.pricing.OnlineTerms
import ir.foodcost.core.pricing.PriceStatus
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.core.pricing.PricingSettings
import ir.foodcost.core.reports.MenuSnapshot
import ir.foodcost.core.units.Uom
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

/**
 * The financial tests of the FoodCost Pro brief (section 27, A–J), each with the brief's own numbers
 * in toman. Prices are compared before rounding, as the brief states them.
 */
class SpecAcceptanceTest {
    private val toman = DisplayCurrency.TOMAN

    private fun t(amount: String) = toman.toRials(amount.bd)

    /** Net prices only: no VAT, and a rounding step too small to matter. */
    private fun netPricing(method: PricingMethod = PricingMethod.TargetFoodCost(35.bd), online: OnlineTerms = OnlineTerms()) =
        PricingSettings(defaultMethod = method, menuPriceIncludesVat = false, rounding = PriceRounding(10, PriceRounding.Direction.UP), online = online)

    /** One dish whose portion costs exactly [costRials]. */
    private fun dish(costRials: Long, menuPrice: Long? = null) = Catalog(
        listOf(F.ingredient("food")),
        listOf(F.price("food", "1", Uom.KILOGRAM, costRials)),
        listOf(F.recipe("dish", F.version(listOf(F.item("food", "1000"))))),
        listOfNotNull(menuPrice?.let { F.menuPrice("dish", it) }),
    )

    @Test
    fun `A 1 kg of meat at 400,000 toman with 20 percent trim gives 200 g clean for 100,000`() {
        val catalog = Catalog(
            listOf(F.ingredient("meat", yields = listOf("0.8"))),
            listOf(F.price("meat", "1", Uom.KILOGRAM, t("400000"))),
            listOf(F.recipe("dish", F.version(listOf(F.item("meat", "200"))))),
        )
        assertAmount("100000", toman.exact(FoodCostCalculator(catalog).cost(rid("dish"), D1).foodCostPerPortion))
    }

    @Test
    fun `B a cost of 100,000 at a 30 percent food cost target sells for 333,333 point 33 before rounding`() {
        val calc = FoodCostCalculator(dish(t("100000")), pricing = netPricing(PricingMethod.TargetFoodCost(30.bd)))
        assertAmount("333333.3333", toman.exact(calc.evaluate(rid("dish"), D1).suggestion.netPrice))
    }

    @Test
    fun `C a cost of 200,000 sold for 300,000 net is a 33 point 33 percent margin and a 50 percent markup`() {
        assertAmount("33.3333", Metrics.grossMarginPercent(t("300000").bd, t("200000").bd))
        assertAmount("50", Metrics.markupPercent(t("300000").bd, t("200000").bd))
    }

    @Test
    fun `D a 10 percent discount on 400,000 with 100,000 of food is a 27 point 78 percent food cost`() {
        val calc = FoodCostCalculator(dish(t("100000")), pricing = netPricing(online = OnlineTerms(discountPercent = 10.bd)))
        val sale = calc.pricer.sale(calc.cost(rid("dish"), D1), SalesChannel.ONLINE, t("400000"))
        assertAmount("360000", toman.exact(sale.netSales))
        assertAmount("40000", toman.exact(sale.discount))
        assertAmount("27.7778", sale.foodCostPercent)
    }

    @Test
    fun `E a 240,000 sauce batch of 2 kg losing 20 percent costs 7,500 for a 50 g portion`() {
        val catalog = Catalog(
            listOf(F.ingredient("base")),
            listOf(F.price("base", "2", Uom.KILOGRAM, t("240000"))),
            listOf(
                F.recipe("sauce", F.version(listOf(F.item("base", "2000")), output = "1600" to Uom.GRAM), kind = RecipeKind.PREP),
                F.recipe("dish", F.version(listOf(F.sub("sauce", "50", Uom.GRAM)))),
            ),
        )
        assertAmount("7500", toman.exact(FoodCostCalculator(catalog).cost(rid("dish"), D1).foodCostPerPortion))
    }

    @Test
    fun `F a cost of 250,000 at a 40 percent margin sells for 416,666 point 67 before rounding`() {
        val calc = FoodCostCalculator(dish(t("250000")), pricing = netPricing(PricingMethod.TargetMargin(40.bd, CostBasis.FOOD_COST)))
        assertAmount("416666.6667", toman.exact(calc.evaluate(rid("dish"), D1).suggestion.netPrice))
    }

    @Test
    fun `G 550,000 with 10 percent VAT included is 500,000 of net sales`() {
        val calc = FoodCostCalculator(dish(t("100000"), menuPrice = t("550000")), pricing = PricingSettings(vatPercent = 10.bd))
        assertAmount("500000", toman.exact(calc.evaluate(rid("dish"), D1).currentNet!!))
        assertAmount("500000", toman.exact(calc.pricer.sale(calc.cost(rid("dish"), D1), SalesChannel.DINE_IN, t("550000")).netSales))
    }

    @Test
    fun `H dearer meat reaches the kebab mix and every plate made with it`() {
        val catalog = Catalog(
            listOf(F.ingredient("meat")),
            listOf(F.price("meat", "1", Uom.KILOGRAM, t("1000000")), F.price("meat", "1", Uom.KILOGRAM, t("1200000"), from = D2)),
            listOf(
                F.recipe("mix", F.version(listOf(F.item("meat", "1000")), output = "1000" to Uom.GRAM), kind = RecipeKind.PREP),
                F.recipe("plate", F.version(listOf(F.sub("mix", "200", Uom.GRAM)))),
            ),
        )
        val calc = FoodCostCalculator(catalog)
        assertAmount("200000", toman.exact(calc.cost(rid("plate"), D1).foodCostPerPortion))
        assertAmount("240000", toman.exact(calc.cost(rid("plate"), D2).foodCostPerPortion))
    }

    @Test
    fun `I a recipe with an ingredient that has no price is not a valid result`() {
        val catalog = Catalog(
            listOf(F.ingredient("meat"), F.ingredient("saffron")),
            listOf(F.price("meat", "1", Uom.KILOGRAM, t("1000000"))),
            listOf(F.recipe("dish", F.version(listOf(F.item("meat", "200"), F.item("saffron", "1"))))),
        )
        val evaluation = FoodCostCalculator(catalog).evaluate(rid("dish"), D1)
        assertFalse(evaluation.cost.isComplete)
        assertEquals(PriceStatus.INCOMPLETE, evaluation.status)
    }

    @Test
    fun `J a later price change leaves an earlier snapshot as it was`() {
        val before = dish(t("1000000"))
        val snapshot = MenuSnapshot.of(FoodCostCalculator(before).menu(D1))
        val after = Catalog(
            before.ingredients.values,
            before.prices + F.price("food", "1", Uom.KILOGRAM, t("2000000"), from = D2),
            before.recipes.values,
        )
        assertNotEquals(snapshot, MenuSnapshot.of(FoodCostCalculator(after).menu(D2)))
        assertEquals(snapshot, MenuSnapshot.of(FoodCostCalculator(after).menu(D1)))
    }
}
