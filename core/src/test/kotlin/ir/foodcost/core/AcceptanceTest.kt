package ir.foodcost.core

import ir.foodcost.core.F.D1
import ir.foodcost.core.F.D2
import ir.foodcost.core.F.rid
import ir.foodcost.core.costing.LatestPurchasePrice
import ir.foodcost.core.costing.UnitCostResult
import ir.foodcost.core.model.Catalog
import ir.foodcost.core.model.PurchasePrice
import ir.foodcost.core.model.QtyBasis
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.money.DisplayCurrency
import ir.foodcost.core.period.InventoryMath
import ir.foodcost.core.period.PeriodLock
import ir.foodcost.core.period.PeriodLockedException
import ir.foodcost.core.pricing.Metrics
import ir.foodcost.core.reports.MenuSnapshot
import ir.foodcost.core.units.Dimension
import ir.foodcost.core.units.UnitConverter
import ir.foodcost.core.units.Uom
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The acceptance tests of the product plan, by code: T01–T07 from the spec, C04–C05 added for the product. */
class AcceptanceTest {

    @Test
    fun `T01 one kilo at 400,000 toman with 80 percent yield makes four 200 g portions at 100,000 toman`() {
        val catalog = Catalog(
            listOf(F.ingredient("meat", yields = listOf("0.8"))),
            listOf(F.price("meat", "1", Uom.KILOGRAM, DisplayCurrency.TOMAN.toRials("400000".bd))),
            listOf(F.recipe("dish", F.version(listOf(F.item("meat", "200"))))),
        )
        val calc = FoodCostCalculator(catalog)

        val cost = calc.cost(rid("dish"), D1)
        assertTrue(cost.isComplete)
        assertEquals("100000".bd, DisplayCurrency.TOMAN.display(cost.foodCostPerPortion))

        val need = calc.requirements(rid("dish"), 4.bd, D1).items.single()
        assertAmount("1000", need.purchaseQty)
        assertAmount("800", need.usableQty)
        assertAmount("1", need.packages)
    }

    @Test
    fun `T02 cost 100,000 and sale 150,000 give a 33 point 33 percent margin and a 50 percent markup`() {
        val cost = DisplayCurrency.TOMAN.toRials("100000".bd).bd
        val sale = DisplayCurrency.TOMAN.toRials("150000".bd).bd
        assertAmount("33.3333", Metrics.grossMarginPercent(sale, cost))
        assertAmount("50", Metrics.markupPercent(sale, cost))
        assertAmount("66.6667", Metrics.foodCostPercent(cost, sale))
    }

    @Test
    fun `T03 opening 20M plus purchases 80M minus closing 30M is a consumption of 70M`() {
        assertEquals(
            70_000_000L,
            InventoryMath.periodConsumption(openingRials = 20_000_000, netPurchasesRials = 80_000_000, closingRials = 30_000_000),
        )
    }

    @Test
    fun `T04 later prices and recipe versions do not change a closed period`() {
        val base = Catalog(
            listOf(F.ingredient("rice")),
            listOf(F.price("rice", "10", Uom.KILOGRAM, 46_000_000)),
            listOf(F.recipe("chelow", F.version(listOf(F.item("rice", "150"))))),
            listOf(F.menuPrice("chelow", 2_000_000)),
        )
        val closed = MenuSnapshot.of(FoodCostCalculator(base).menu(D1))
        val lock = PeriodLock(lockedThrough = D1)

        val newPrice = F.price("rice", "10", Uom.KILOGRAM, 55_000_000, from = D2)
        val newVersion = F.version(listOf(F.item("rice", "180")), from = D2)
        lock.check(newPrice)
        lock.check(newVersion)
        val edited = base.withPrices(newPrice)
            .withRecipes(F.recipe("chelow", F.version(listOf(F.item("rice", "150"))), newVersion))

        assertEquals(closed, MenuSnapshot.of(FoodCostCalculator(edited).menu(D1)))
        assertEquals(690_000L, closed.items.single().foodCostRials)
        assertEquals(990_000L, MenuSnapshot.of(FoodCostCalculator(edited).menu(D2)).items.single().foodCostRials)

        assertFailsWith<PeriodLockedException> { lock.check(F.price("rice", "10", Uom.KILOGRAM, 50_000_000, from = D1)) }
        assertFailsWith<PeriodLockedException> { lock.check(F.menuPrice("chelow", 2_500_000, from = D1)) }
    }

    @Test
    fun `T06 kilos and grams, rials and tomans, and package sizes convert without differences`() {
        assertAmount("1000", UnitConverter.toBase(1.bd, Uom.KILOGRAM, Dimension.MASS))
        assertAmount("4.608", UnitConverter.toBase(1.bd, Uom.MESGHAL, Dimension.MASS))
        assertAmount("15", UnitConverter.toBase(1.bd, Uom.TABLESPOON, Dimension.VOLUME))
        assertAmount("2.5", UnitConverter.toBase("2500".bd, Uom.MILLILITER, Dimension.VOLUME)?.over(Uom.LITER.toBase))

        val oil = F.ingredient("oil")
        val inKilos = Catalog(listOf(oil), listOf(F.price("oil", "16", Uom.KILOGRAM, 56_000_000)))
        val inGramsAndTomans = Catalog(
            listOf(oil),
            listOf(PurchasePrice(oil.id, D1, "16000".bd, Uom.GRAM, DisplayCurrency.TOMAN.toRials("5600000".bd))),
        )
        val a = assertIs<UnitCostResult.Found>(LatestPurchasePrice(inKilos).unitCost(oil, D1))
        val b = assertIs<UnitCostResult.Found>(LatestPurchasePrice(inGramsAndTomans).unitCost(oil, D1))
        assertAmount("3500", a.rialsPerBaseUnit)
        assertEquals(0, a.rialsPerBaseUnit.compareTo(b.rialsPerBaseUnit))

        assertEquals(4_000_000L, DisplayCurrency.TOMAN.toRials("400000".bd))
        assertEquals("400000".bd, DisplayCurrency.TOMAN.display(4_000_000L))
    }

    @Test
    fun `T07 waste already in the yield is not charged twice`() {
        val catalog = Catalog(
            listOf(F.ingredient("chicken", yields = listOf("0.8"))),
            listOf(F.price("chicken", "1", Uom.KILOGRAM, 6_000_000)),
            listOf(
                F.recipe("usable", F.version(listOf(F.item("chicken", "200")))),
                F.recipe("bought", F.version(listOf(F.item("chicken", "250", basis = QtyBasis.AS_PURCHASED)))),
                F.recipe("skewer", F.version(listOf(F.item("chicken", "200")), misc = "10"), kind = RecipeKind.PREP),
                F.recipe("plate", F.version(listOf(F.sub("skewer", "1")), misc = "10")),
            ),
        )
        val calc = FoodCostCalculator(catalog)

        // 200 g usable and the 250 g bought for it are the same chicken: 250 g × 6,000.
        assertAmount("1500000", calc.cost(rid("usable"), D1).foodCostPerPortion)
        assertAmount("1500000", calc.cost(rid("bought"), D1).foodCostPerPortion)

        // The plate takes the skewer's cost as it is: its yield and its 10% are not applied again.
        assertAmount("1650000", calc.cost(rid("skewer"), D1).foodCostPerPortion)
        assertAmount("1650000", calc.cost(rid("plate"), D1).foodCostPerPortion)
    }

    @Test
    fun `C04 every amount converts to the new rial and back without differences`() {
        val newRial = DisplayCurrency.NEW_RIAL
        assertEquals(12_500L, newRial.toRials("1.25".bd))
        assertEquals("1.25".bd, newRial.display(12_500L))
        assertEquals("1.2345".bd, newRial.exact(12_345L))

        for (typed in listOf("0.01", "1", "9.99", "1234.56", "100000")) {
            assertEquals(typed.bd.setScale(2), newRial.display(newRial.toRials(typed.bd)))
        }
        for (rials in listOf(1L, 99L, 12_345L, 46_000_000L, 9_000_000_000_000L)) {
            assertEquals(rials, newRial.toRials(newRial.exact(rials)))
            assertEquals(rials, DisplayCurrency.TOMAN.toRials(DisplayCurrency.TOMAN.exact(rials)))
        }
        assertFailsWith<IllegalArgumentException> { newRial.toRials("0.00001".bd) }
    }

    @Test
    fun `C05 a menu of 500 ingredients and 200 recipes is priced in under a second`() {
        val ingredients = (1..500).map { F.ingredient("i$it", yields = listOf("0.9")) }
        val prices = (1..500).map { F.price("i$it", "1", Uom.KILOGRAM, 1_000_000L + it * 1_000L) }
        val preps = (1..50).map { p ->
            F.recipe(
                "p$p",
                F.version((0 until 6).map { k -> F.item("i${(p * 7 + k) % 500 + 1}", "100") }, portions = "10", misc = "3"),
                kind = RecipeKind.PREP,
            )
        }
        val dishes = (1..150).map { d ->
            F.recipe(
                "d$d",
                F.version(
                    listOf(F.sub("p${d % 50 + 1}", "1"), F.sub("p${(d + 7) % 50 + 1}", "1")) +
                        (0 until 5).map { k -> F.item("i${(d * 11 + k) % 500 + 1}", "50") },
                    perPortion = listOf(F.item("i${d % 500 + 1}", "10")),
                    misc = "3",
                ),
            )
        }
        val menuPrices = (1..150).map { F.menuPrice("d$it", 5_000_000) }
        val calc = FoodCostCalculator(Catalog(ingredients, prices, preps + dishes, menuPrices))

        calc.menu(D1) // warm-up
        val start = System.nanoTime()
        val report = calc.menu(D1)
        val millis = (System.nanoTime() - start) / 1_000_000

        assertEquals(150, report.items.size)
        assertTrue(report.items.all { it.cost.isComplete })
        assertTrue(millis < 1_000, "pricing the menu took $millis ms")
        assertNotEquals(0, report.items.first().cost.foodCostPerPortion.signum())
    }
}
