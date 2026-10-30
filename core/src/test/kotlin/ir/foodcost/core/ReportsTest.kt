package ir.foodcost.core

import ir.foodcost.core.F.D1
import ir.foodcost.core.F.D2
import ir.foodcost.core.F.iid
import ir.foodcost.core.F.rid
import ir.foodcost.core.model.Catalog
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.pricing.PriceStatus
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.core.pricing.PricingSettings
import ir.foodcost.core.units.Dimension
import ir.foodcost.core.units.Uom
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportsTest {

    private val kebab = F.recipe("kebab", F.version(listOf(F.item("meat", "1000")))) // 3,500,000
    private val salad = F.recipe("salad", F.version(listOf(F.item("cucumber", "300"))), category = "salad") // 150,000

    private val catalog = Catalog(
        listOf(F.ingredient("meat"), F.ingredient("cucumber"), F.ingredient("soda", Dimension.COUNT)),
        listOf(
            F.price("meat", "1", Uom.KILOGRAM, 3_500_000),
            F.price("cucumber", "1", Uom.KILOGRAM, 500_000),
            F.price("soda", "24", Uom.PIECE, 10_800_000),
        ),
        listOf(
            kebab,
            salad,
            F.recipe("soda", F.version(listOf(F.item("soda", "1", Uom.PIECE)))),
            F.recipe("special", F.version()),
            F.recipe("rice", F.version(listOf(F.item("cucumber", "1"))), kind = RecipeKind.PREP),
            F.recipe(
                "wedding",
                F.version(listOf(F.sub("kebab", "0.5"), F.sub("salad", "1"))),
                kind = RecipeKind.EVENT_PACKAGE,
                category = "event",
            ),
        ),
        listOf(F.menuPrice("kebab", 10_000_000), F.menuPrice("salad", 600_000)),
    )

    private val pricing = PricingSettings(
        methodByCategory = mapOf(
            "salad" to PricingMethod.TargetFoodCost(30.bd),
            "event" to PricingMethod.TargetFoodCost(40.bd),
        ),
    )

    @Test
    fun `the menu report lists every item but preparations, with the items to reprice`() {
        val report = FoodCostCalculator(catalog, pricing = pricing).menu(D1)
        assertEquals(listOf("kebab", "salad", "soda", "special", "wedding"), report.items.map { it.recipe.name })
        assertEquals(PriceStatus.UNDER, report.items[0].status) // 10,000,000 against 11,000,000
        assertEquals(PriceStatus.OK, report.items[1].status) // 600,000 against 550,000
        assertEquals(PriceStatus.NO_PRICE, report.items[2].status)
        assertEquals(PriceStatus.EMPTY, report.items[3].status)
        assertEquals(listOf("kebab"), report.needsRepricing.map { it.recipe.name })
        assertEquals(2, report.count(PriceStatus.NO_PRICE))
    }

    @Test
    fun `an event quote gives the price per guest and what to buy for every guest`() {
        val quote = FoodCostCalculator(catalog, pricing = pricing).quoteEvent(rid("wedding"), 200, D1)
        assertAmount("1900000", quote.evaluation.cost.foodCostPerPortion) // half a kebab and a salad
        assertEquals(5_300_000L, quote.pricePerGuest) // 4,750,000 net, 5,225,000 with VAT, rounded up
        assertEquals(1_060_000_000L, quote.total)

        val meat = quote.requirements.items.first { it.ingredient.id == iid("meat") }
        assertAmount("100000", meat.purchaseQty) // 100 kg
        assertAmount("100", meat.packages)
        assertAmount("60000", quote.requirements.items.first { it.ingredient.id == iid("cucumber") }.purchaseQty)
        assertAmount("380000000", quote.requirements.ingredientCost)
        assertAmount("380000000", quote.foodCostTotal)
    }

    @Test
    fun `a cost change is split into what the price did and what the quantity did`() {
        val changed = catalog
            .withPrices(F.price("meat", "1", Uom.KILOGRAM, 4_200_000, from = D2))
            .withRecipes(F.recipe("kebab", kebab.versions.single(), F.version(listOf(F.item("meat", "900")), from = D2)))
        val calc = FoodCostCalculator(changed, pricing = pricing)

        val change = calc.costChange(rid("kebab"), D1, D2)
        assertAmount("280000", change.delta) // 3,500,000 to 3,780,000
        assertAmount("8", change.deltaPercent)
        val meat = change.effects.single()
        assertAmount("630000", meat.priceEffect) // 900 g × 700 more per gram
        assertAmount("-350000", meat.quantityEffect) // 100 g less × 3,500
        assertAmount("0", change.otherEffect)

        val all = calc.costChanges(D1, D2)
        assertEquals(listOf("kebab", "wedding"), all.map { it.recipe.name })
        assertTrue(all.all { it.delta.signum() > 0 })
    }

    @Test
    fun `an ingredient added later shows its whole cost as a quantity effect at the old price`() {
        val changed = catalog
            .withPrices(F.price("cucumber", "1", Uom.KILOGRAM, 600_000, from = D2))
            .withRecipes(F.recipe("kebab", kebab.versions.single(), F.version(listOf(F.item("meat", "1000"), F.item("cucumber", "100")), from = D2)))
        val change = FoodCostCalculator(changed).costChange(rid("kebab"), D1, D2)
        val cucumber = change.effects.single()
        assertAmount("50000", cucumber.quantityEffect) // 100 g × 500 before
        assertAmount("10000", cucumber.priceEffect) // 100 g × 100 more after
        assertAmount("60000", change.delta)
    }
}
