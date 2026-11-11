package ir.foodcost.core

import ir.foodcost.core.model.Catalog
import ir.foodcost.core.money.PriceRounding
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.core.pricing.PricingScope
import ir.foodcost.core.pricing.PricingSettings
import ir.foodcost.core.units.Uom
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PricingScopeTest {
    private val general = PricingMethod.TargetFoodCost(35.bd)
    private val section = PricingMethod.TargetFoodCost(20.bd)
    private val own = PricingMethod.Markup(100.bd)
    private val rules = PricingSettings(
        defaultMethod = general,
        methodByCategory = mapOf("main" to section),
        generalSections = listOf("dessert"),
        menuPriceIncludesVat = false,
        rounding = PriceRounding(1, PriceRounding.Direction.UP),
    )

    @Test
    fun `general scope applies one rule even to dishes with their own or their section's rule`() {
        val active = rules.copy(scope = PricingScope.GENERAL)
        for (dish in listOf(
            F.recipe("specific", category = "main", pricing = own),
            F.recipe("section", category = "main"),
            F.recipe("unmapped", category = "other"),
            F.recipe("plain"),
        )) {
            assertEquals(general, active.methodFor(dish), dish.name)
        }
        assertEquals(mapOf("main" to section), active.methodByCategory)
    }

    @Test
    fun `section scope preserves legacy precedence and uses the general fallback for unmapped foods`() {
        assertEquals(PricingScope.MENU_SECTIONS, rules.scope)
        assertEquals(own, rules.methodFor(F.recipe("specific", category = "main", pricing = own)))
        assertEquals(section, rules.methodFor(F.recipe("section", category = "main")))
        assertEquals(general, rules.methodFor(F.recipe("unmapped", category = "other")))
        assertEquals(general, rules.methodFor(F.recipe("plain")))
    }

    @Test
    fun `switching scope changes actual suggestions and restoring it restores the saved policy`() {
        val catalog = Catalog(
            ingredients = listOf(F.ingredient("food")),
            prices = listOf(F.price("food", "1", Uom.KILOGRAM, 1_000_000)),
            recipes = listOf(
                F.recipe("section", F.version(listOf(F.item("food", "1000"))), category = "main"),
                F.recipe("specific", F.version(listOf(F.item("food", "1000"))), category = "main", pricing = own),
            ),
        )
        fun price(settings: PricingSettings, id: String) =
            FoodCostCalculator(catalog, pricing = settings).evaluate(F.rid(id), F.D1).suggestion.menuPrice

        assertEquals(5_000_000L, price(rules, "section"))
        assertEquals(2_000_000L, price(rules, "specific"))
        val generalMode = rules.copy(scope = PricingScope.GENERAL)
        assertEquals(2_857_143L, price(generalMode, "section"))
        assertEquals(2_857_143L, price(generalMode, "specific"))
        val restored = generalMode.copy(scope = PricingScope.MENU_SECTIONS)
        assertEquals(rules, restored)
        assertEquals(5_000_000L, price(restored, "section"))
        assertEquals(2_000_000L, price(restored, "specific"))
    }
}
