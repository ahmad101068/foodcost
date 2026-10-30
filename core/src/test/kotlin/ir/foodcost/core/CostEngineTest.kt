package ir.foodcost.core

import ir.foodcost.core.F.D1
import ir.foodcost.core.F.D2
import ir.foodcost.core.F.iid
import ir.foodcost.core.F.rid
import ir.foodcost.core.costing.CostingRules
import ir.foodcost.core.costing.IssueType
import ir.foodcost.core.model.Catalog
import ir.foodcost.core.model.ComponentRef
import ir.foodcost.core.model.PurchasePrice
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.pricing.PriceStatus
import ir.foodcost.core.units.Dimension
import ir.foodcost.core.units.Uom
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CostEngineTest {

    private val riceAndOil = listOf(
        F.price("rice", "10", Uom.KILOGRAM, 46_000_000), // 4,600 per gram
        F.price("oil", "16", Uom.KILOGRAM, 56_000_000), // 3,500 per gram
    )

    @Test
    fun `a sub-recipe used by weight costs its share of the batch output`() {
        // 1.5 kg rice and 100 g oil make 4 kg of cooked rice; a plate takes 300 g of it.
        val catalog = Catalog(
            listOf(F.ingredient("rice"), F.ingredient("oil")),
            riceAndOil,
            listOf(
                F.recipe(
                    "chelow",
                    F.version(
                        listOf(F.item("rice", "1.5", Uom.KILOGRAM), F.item("oil", "100")),
                        portions = "13",
                        output = "4" to Uom.KILOGRAM,
                    ),
                    kind = RecipeKind.PREP,
                ),
                F.recipe("plate", F.version(listOf(F.sub("chelow", "300", Uom.GRAM)))),
            ),
        )
        val calc = FoodCostCalculator(catalog)

        val chelow = calc.cost(rid("chelow"), D1)
        assertAmount("7250000", chelow.batchCost) // 1,500 × 4,600 + 100 × 3,500
        assertAmount("1812.5", chelow.costPerOutputUnit)
        assertAmount("543750", calc.cost(rid("plate"), D1).foodCostPerPortion)
    }

    @Test
    fun `batch lines are shared by the portions and portion lines are added to each`() {
        val catalog = Catalog(
            listOf(F.ingredient("stew meat", yields = listOf("0.8")), F.ingredient("bread", Dimension.COUNT)),
            listOf(F.price("stew meat", "1", Uom.KILOGRAM, 16_000_000), F.price("bread", "10", Uom.PIECE, 1_000_000)),
            listOf(
                F.recipe(
                    "gheymeh",
                    F.version(
                        listOf(F.item("stew meat", "2000")), // 2.5 kg bought for 2 kg usable: 40,000,000
                        perPortion = listOf(F.item("bread", "1", Uom.PIECE)),
                        portions = "20",
                    ),
                ),
            ),
        )
        assertAmount("2100000", FoodCostCalculator(catalog).cost(rid("gheymeh"), D1).foodCostPerPortion)
    }

    @Test
    fun `the misc share is taken on the recipe's own ingredients`() {
        val catalog = Catalog(
            listOf(F.ingredient("rice")),
            riceAndOil.take(1),
            listOf(F.recipe("dish", F.version(listOf(F.item("rice", "100")), misc = null), category = "main")),
        )
        val defaults = FoodCostCalculator(catalog, CostingRules(defaultMiscPercent = 5.bd)).cost(rid("dish"), D1)
        assertAmount("23000", defaults.miscPerPortion) // 5% of 460,000
        assertAmount("483000", defaults.foodCostPerPortion)

        val byCategory = FoodCostCalculator(catalog, CostingRules(5.bd, mapOf("main" to 2.bd))).cost(rid("dish"), D1)
        assertAmount("469200", byCategory.foodCostPerPortion)
    }

    @Test
    fun `yield stages multiply`() {
        val catalog = Catalog(
            listOf(F.ingredient("lamb", yields = listOf("0.8", "0.75"))),
            listOf(F.price("lamb", "1", Uom.KILOGRAM, 20_000_000)),
            listOf(F.recipe("dish", F.version(listOf(F.item("lamb", "120"))))),
        )
        val cost = FoodCostCalculator(catalog).cost(rid("dish"), D1)
        assertAmount("200", cost.lines.single().purchaseQty) // 120 g usable ÷ 0.6
        assertAmount("4000000", cost.foodCostPerPortion)
    }

    @Test
    fun `pieces and volumes convert through the ingredient's own weight and density`() {
        val catalog = Catalog(
            listOf(
                F.ingredient("tomato", gramsPerPiece = "120"),
                F.ingredient("egg", Dimension.COUNT, gramsPerPiece = "60"),
                F.ingredient("oil", density = "0.92"),
            ),
            listOf(
                F.price("tomato", "1", Uom.KILOGRAM, 550_000),
                F.price("egg", "30", Uom.PIECE, 6_000_000),
                riceAndOil[1],
            ),
            listOf(
                F.recipe("tomato", F.version(listOf(F.item("tomato", "1", Uom.PIECE)))),
                F.recipe("eggs", F.version(listOf(F.item("egg", "120", Uom.GRAM)))),
                F.recipe("oil", F.version(listOf(F.item("oil", "2", Uom.TABLESPOON)))),
            ),
        )
        val calc = FoodCostCalculator(catalog)
        assertAmount("66000", calc.cost(rid("tomato"), D1).foodCostPerPortion) // 120 g × 550
        assertAmount("400000", calc.cost(rid("eggs"), D1).foodCostPerPortion) // 2 eggs × 200,000
        assertAmount("96600", calc.cost(rid("oil"), D1).foodCostPerPortion) // 30 ml × 0.92 × 3,500
    }

    @Test
    fun `a unit with no bridge is reported, not guessed`() {
        val catalog = Catalog(
            listOf(F.ingredient("oil")),
            riceAndOil.drop(1),
            listOf(F.recipe("dish", F.version(listOf(F.item("oil", "2", Uom.TABLESPOON))))),
        )
        val cost = FoodCostCalculator(catalog).cost(rid("dish"), D1)
        assertFalse(cost.isComplete)
        assertEquals(IssueType.UNIT_MISMATCH, cost.issues.single().type)
    }

    @Test
    fun `a missing price leaves the recipe incomplete with the other lines still costed`() {
        val catalog = Catalog(
            listOf(F.ingredient("rice"), F.ingredient("saffron")),
            riceAndOil.take(1),
            listOf(F.recipe("dish", F.version(listOf(F.item("rice", "100"), F.item("saffron", "0.05")))), ),
            listOf(F.menuPrice("dish", 2_000_000)),
        )
        val calc = FoodCostCalculator(catalog)
        val cost = calc.cost(rid("dish"), D1)
        assertAmount("460000", cost.foodCostPerPortion)
        assertEquals(listOf(IssueType.NO_PRICE), cost.issues.map { it.type })
        assertEquals(ComponentRef.IngredientRef(iid("saffron")), cost.issues.single().component)
        assertEquals(PriceStatus.INCOMPLETE, calc.evaluate(rid("dish"), D1).status)
    }

    @Test
    fun `a line without a quantity is reported`() {
        val catalog = Catalog(
            listOf(F.ingredient("rice")),
            riceAndOil.take(1),
            listOf(F.recipe("dish", F.version(listOf(F.item("rice", null))))),
        )
        assertEquals(IssueType.MISSING_QUANTITY, FoodCostCalculator(catalog).cost(rid("dish"), D1).issues.single().type)
    }

    @Test
    fun `the price in force on the date is used, and on one day the later entry wins`() {
        val catalog = Catalog(
            listOf(F.ingredient("rice")),
            listOf(
                F.price("rice", "10", Uom.KILOGRAM, 46_000_000),
                F.price("rice", "10", Uom.KILOGRAM, 55_000_000, from = D2),
                F.price("rice", "10", Uom.KILOGRAM, 50_000_000, from = D2),
            ),
            listOf(F.recipe("dish", F.version(listOf(F.item("rice", "100"))))),
        )
        val calc = FoodCostCalculator(catalog)
        assertNull(catalog.priceAt(iid("rice"), D1.minusDays(1)))
        assertAmount("460000", calc.cost(rid("dish"), D1).foodCostPerPortion)
        assertAmount("460000", calc.cost(rid("dish"), D2.minusDays(1)).foodCostPerPortion)
        assertAmount("500000", calc.cost(rid("dish"), D2).foodCostPerPortion)
    }

    @Test
    fun `the net purchase cost removes discounts and adds tax that is not claimed back and delivery`() {
        val price = PurchasePrice(
            iid("meat"), D1, 1.bd, Uom.KILOGRAM,
            grossRials = 10_000_000, discountRials = 500_000, taxRials = 950_000, deliveryRials = 200_000,
        )
        assertEquals(10_650_000L, price.netCostRials)
        assertEquals(9_700_000L, price.copy(taxRecoverable = true).netCostRials)
    }

    @Test
    fun `recipes that use each other are flagged and every calculation still ends`() {
        val catalog = Catalog(
            listOf(F.ingredient("rice")),
            riceAndOil.take(1),
            listOf(
                F.recipe("a", F.version(listOf(F.item("rice", "100"), F.sub("b", "1")))),
                F.recipe("b", F.version(listOf(F.item("rice", "100"), F.sub("a", "1"))), kind = RecipeKind.PREP),
                F.recipe("c", F.version(listOf(F.item("rice", "100"), F.sub("a", "1")))),
            ),
        )
        val calc = FoodCostCalculator(catalog)
        for (id in listOf("a", "b", "c")) {
            val cost = calc.cost(rid(id), D1)
            assertFalse(cost.isComplete, id)
            assertTrue(cost.issues.any { it.type == IssueType.CYCLE }, id)
            assertAmount("460000", cost.foodCostPerPortion) // only its own rice; the cyclic part counts zero
        }
        // The same answer whichever recipe is priced first.
        val sheet = calc.sheet(D1)
        assertEquals(sheet.recipe(rid("c")).foodCostPerPortion, calc.sheet(D1).also { it.recipe(rid("a")) }.recipe(rid("c")).foodCostPerPortion)
    }

    @Test
    fun `a recipe that does not exist yet on the date is empty`() {
        val catalog = Catalog(
            listOf(F.ingredient("rice")),
            riceAndOil.take(1),
            listOf(F.recipe("dish", F.version(listOf(F.item("rice", "100")), from = D2))),
            listOf(F.menuPrice("dish", 1_000_000)),
        )
        val calc = FoodCostCalculator(catalog)
        assertEquals(IssueType.NO_VERSION, calc.cost(rid("dish"), D1).issues.single().type)
        assertEquals(PriceStatus.EMPTY, calc.evaluate(rid("dish"), D1).status)
        assertTrue(calc.cost(rid("dish"), D2).isComplete)
    }

    @Test
    fun `a price change reaches every recipe that uses the ingredient, through sub-recipes too`() {
        val catalog = Catalog(
            listOf(F.ingredient("rice"), F.ingredient("oil")),
            riceAndOil,
            listOf(
                F.recipe("chelow", F.version(listOf(F.item("rice", "150"))), kind = RecipeKind.PREP),
                F.recipe("kebab plate", F.version(listOf(F.sub("chelow", "1")))),
                F.recipe("fries", F.version(listOf(F.item("oil", "20")))),
            ),
        )
        assertEquals(setOf(rid("chelow"), rid("kebab plate")), catalog.usersOf(ComponentRef.IngredientRef(iid("rice")), D1))
    }

    @Test
    fun `bad data is refused when it is entered`() {
        assertFailsWith<IllegalArgumentException> { F.ingredient("meat", yields = listOf("1.2")) }
        assertFailsWith<IllegalArgumentException> { F.version(portions = "0") }
        assertFailsWith<IllegalArgumentException> { F.recipe("dish", F.version(), F.version()) }
        assertFailsWith<IllegalArgumentException> { Catalog(listOf(F.ingredient("rice"), F.ingredient("rice"))) }
    }
}
