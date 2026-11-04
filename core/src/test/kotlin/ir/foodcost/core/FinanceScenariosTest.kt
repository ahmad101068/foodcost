package ir.foodcost.core

import ir.foodcost.core.model.Catalog
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.model.SalesChannel
import ir.foodcost.core.money.DisplayCurrency
import ir.foodcost.core.pricing.Metrics
import ir.foodcost.core.pricing.OnlineTerms
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.core.pricing.PricingSettings
import ir.foodcost.core.units.Dimension
import ir.foodcost.core.units.UnitConverter
import ir.foodcost.core.units.Uom
import java.math.BigDecimal
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The financial scenarios of the 0.7.4 audit's own probe (FinanceProbe.kt, F01 to F11), kept in the
 * project so they run with every change. Each expected figure is worked out by hand, outside the
 * code under test, and compared within the probe's tolerance. F12 to F16 need the ledger and are in
 * the data module's FinanceScenariosLedgerTest.
 */
class FinanceScenariosTest {
    private fun assertClose(expected: String, actual: BigDecimal?) {
        assertTrue(actual != null && (actual - expected.bd).abs() < "0.00005".bd, "expected $expected, got $actual")
    }

    // One kilo of meat at 4,000,000 rials, 20% lost in cleaning, 200 g clean used: 250 g bought.
    @Test
    fun `F01 200 g clean of meat with a 20 percent loss costs 1,000,000 rials`() {
        val catalog = Catalog(
            listOf(F.ingredient("meat", yields = listOf("0.8"))),
            listOf(F.price("meat", "1", Uom.KILOGRAM, 4_000_000)),
            listOf(F.recipe("dish", F.version(listOf(F.item("meat", "200"))))),
        )
        assertClose("1000000", FoodCostCalculator(catalog).cost(F.rid("dish"), F.D1).foodCostPerPortion)
    }

    private val oneKilo = Catalog(
        listOf(F.ingredient("food")),
        listOf(F.price("food", "1", Uom.KILOGRAM, 1_000_000)),
        listOf(F.recipe("dish", F.version(listOf(F.item("food", "1000"))))),
    )

    // A cost of 1,000,000 at a 30% food cost target, prices without VAT: 1,000,000 / 0.3.
    @Test
    fun `F02 a 30 percent food cost target on a cost of 1,000,000 rials is 3,333,333 point 33 net`() {
        val calc = FoodCostCalculator(oneKilo, pricing = PricingSettings(defaultMethod = PricingMethod.TargetFoodCost(30.bd), menuPriceIncludesVat = false))
        assertClose("3333333.333333333333333333333333333", calc.evaluate(F.rid("dish"), F.D1).suggestion.netPrice)
    }

    // A sale of 4,000,000 online with a 10% discount: 3,600,000 net, and 1,000,000 / 3,600,000.
    @Test
    fun `F03 and F04 a 10 percent discount on 4,000,000 rials nets 3,600,000 and a 27 point 78 percent food cost`() {
        val online = FoodCostCalculator(oneKilo, pricing = PricingSettings(online = OnlineTerms(discountPercent = 10.bd), menuPriceIncludesVat = false))
        val sale = online.pricer.sale(online.cost(F.rid("dish"), F.D1), SalesChannel.ONLINE, 4_000_000)
        assertClose("3600000", sale.netSales)
        assertClose("27.777777777777", sale.foodCostPercent)
    }

    // A sauce of 2 kg at 1,200,000 a kilo gives 1,600 g; 50 g of it is 2,400,000 × 50 / 1,600.
    @Test
    fun `F05 50 g of a sauce whose 2 kg batch gives 1,600 g costs 75,000 rials`() {
        val catalog = Catalog(
            listOf(F.ingredient("saucebase")),
            listOf(F.price("saucebase", "2", Uom.KILOGRAM, 2_400_000)),
            listOf(
                F.recipe("sauce", F.version(listOf(F.item("saucebase", "2000")), output = "1600" to Uom.GRAM), kind = RecipeKind.PREP),
                F.recipe("meal", F.version(listOf(F.sub("sauce", "50", Uom.GRAM)))),
            ),
        )
        assertClose("75000", FoodCostCalculator(catalog).cost(F.rid("meal"), F.D1).foodCostPerPortion)
    }

    // Sold at 3,000,000 with a cost of 2,000,000: 1,000,000 / 3,000,000 margin, 1,000,000 / 2,000,000 markup.
    @Test
    fun `F06 and F07 a sale of 3,000,000 on a cost of 2,000,000 is a 33 point 33 percent margin and a 50 percent markup`() {
        assertClose("33.333333333333", Metrics.grossMarginPercent(3_000_000.bd, 2_000_000.bd))
        assertClose("50", Metrics.markupPercent(3_000_000.bd, 2_000_000.bd))
    }

    @Test
    fun `F08 and F09 one kilo is 1,000 g and two liters are 2,000 ml`() {
        assertEquals(1000.bd, UnitConverter.toBase(1.bd, Uom.KILOGRAM, Dimension.MASS))
        assertEquals(2000.bd, UnitConverter.toBase(2.bd, Uom.LITER, Dimension.VOLUME))
    }

    @Test
    fun `F10 400,000 toman are 4,000,000 rials`() {
        assertEquals(4_000_000L, DisplayCurrency.TOMAN.toRials(400000.bd))
    }

    @Test
    fun `F11 a food cost with no sales is no number, not zero`() {
        assertNull(Metrics.foodCostPercent(1000.bd, BigDecimal.ZERO))
    }
}
