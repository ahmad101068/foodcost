package ir.foodcost.presentation

import ir.foodcost.core.FoodCostCalculator
import ir.foodcost.core.costing.CostingRules
import ir.foodcost.core.model.*
import ir.foodcost.core.money.DisplayCurrency
import ir.foodcost.core.pricing.*
import ir.foodcost.core.units.Dimension
import ir.foodcost.core.units.Uom
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.*

class CategoryConversionTest {
    private val money = MoneyFormat(DisplayCurrency.TOMAN)
    private val pricing = PricingSettings(scope = PricingScope.MENU_SECTIONS)
    private val costing = CostingRules(defaultMiscPercent = BigDecimal.ZERO)
    private fun checked(category: CategoryTargetInput, current: PricingSettings = pricing) =
        SettingsInput.of(current, costing, money).copy(categories = listOf(category))
            .checkPart(SettingsPart.SECTIONS, current, costing, money)
    private fun saved(category: CategoryTargetInput) = assertIs<Checked.Valid<SettingsDraft>>(checked(category)).value.pricing

    private fun suggestion(category: CategoryTargetInput): Long {
        val today = LocalDate.of(2026, 11, 13)
        val food = IngredientId("food")
        val dish = RecipeId("dish")
        val catalog = Catalog(listOf(Ingredient(food, "Food", Dimension.MASS)),
            listOf(PurchasePrice(food, today, BigDecimal.ONE, Uom.KILOGRAM, 318_182)),
            listOf(Recipe(dish, "Dish", RecipeKind.MENU_ITEM, listOf(RecipeVersion(today,
                batchLines = listOf(RecipeLine(ComponentRef.IngredientRef(food), BigDecimal(1000), Uom.GRAM)),
                extras = listOf(ExtraCost("Wage", 50_000)))), category = category.name)))
        return FoodCostCalculator(catalog, costing, saved(category)).evaluate(dish, today).suggestion.menuPrice
    }

    @Test fun allSixSectionConversionsRetainTheirPercentageAndMenuPrice() {
        val values = mapOf(MethodChoice.FOOD_COST to "32", MethodChoice.MARGIN to "68", MethodChoice.MARKUP to "212.5")
        for ((from, percent) in values) for ((to, expected) in values) {
            val initial = CategoryTargetInput("Main", percent, from)
            val converted = initial.withMethod(to)
            assertEquals(0, BigDecimal(expected).compareTo(assertNotNull(Numbers.parse(converted.percent))))
            assertEquals(CostBasis.FOOD_COST, converted.basis)
            assertFalse(converted.carried)
            assertEquals(suggestion(initial), suggestion(converted))
        }
    }

    @Test fun repeatedSectionSwitchesKeepTheExactInputAndRoundingBoundary() {
        val original = CategoryTargetInput("Main", "35")
        var current = original
        repeat(20) {
            current = current.withMethod(MethodChoice.MARKUP)
            assertTrue(current.percent.startsWith("185.7142857142857142857142857142857"))
            assertEquals(suggestion(original), suggestion(current))
            current = current.withMethod(MethodChoice.MARGIN).withMethod(MethodChoice.FOOD_COST)
            assertEquals("35", current.percent)
            assertEquals(suggestion(original), suggestion(current))
        }
    }

    @Test fun fullCostSectionConversionsRetainTheirBasisAndOriginalPercentage() {
        val original = CategoryTargetInput("Main", "30", MethodChoice.MARGIN, CostBasis.FULL_COST)
        var current = original
        repeat(20) {
            current = current.withMethod(MethodChoice.MARKUP)
            assertEquals(CostBasis.FULL_COST, current.basis)
            assertEquals(suggestion(original), suggestion(current))
            current = current.withMethod(MethodChoice.MARGIN)
            assertEquals("30", current.percent)
            assertEquals(CostBasis.FULL_COST, current.basis)
        }
        assertTrue(assertNotNull(current.equivalents()).contains("روی همهٔ هزینه‌ها"))
    }

    @Test fun changingFromFullCostToFoodRequiresManualConfirmationEvenForANumericValue() {
        val original = CategoryTargetInput("Main", "30", MethodChoice.MARGIN, CostBasis.FULL_COST)
        val changed = original.withMethod(MethodChoice.FOOD_COST)
        assertEquals("30", changed.percent)
        assertTrue(changed.carried)
        assertNull(changed.equivalents())
        assertTrue(Field.CATEGORY in assertIs<Checked.Invalid>(checked(changed)).errors)
        val changedBack = changed.withMethod(MethodChoice.MARKUP)
        assertTrue(changedBack.carried)
        assertIs<Checked.Invalid>(checked(changedBack))
        assertIs<Checked.Invalid>(checked(changed.withBasis(CostBasis.FOOD_COST)))
        val entered = changed.withPercent("40")
        assertFalse(entered.carried)
        assertEquals(CostBasis.FOOD_COST, entered.basis)
        assertIs<Checked.Valid<SettingsDraft>>(checked(entered))
        assertEquals("60", entered.withMethod(MethodChoice.MARGIN).percent)
        assertEquals(CostBasis.FOOD_COST, entered.withMethod(MethodChoice.MARGIN).basis)
    }

    @Test fun changingPercentOrBasisCannotRestoreAnOldConversionOrigin() {
        val markup = CategoryTargetInput("Main", "35").withMethod(MethodChoice.MARKUP)
        val manual = markup.withPercent("50")
        assertNull(manual.conversionOrigin)
        val food = manual.withMethod(MethodChoice.FOOD_COST)
        assertTrue(assertNotNull(Numbers.parse(food.percent)) > BigDecimal(66))
        val full = markup.withBasis(CostBasis.FULL_COST).withMethod(MethodChoice.MARGIN)
        assertEquals(CostBasis.FULL_COST, full.basis)
        assertNull(full.withMethod(MethodChoice.FOOD_COST).equivalents())
        assertTrue(full.withMethod(MethodChoice.FOOD_COST).carried)
    }

    @Test fun invalidSectionPercentagesCannotBecomeValidByChangingMethods() {
        for ((method, value) in listOf(MethodChoice.FOOD_COST to "0", MethodChoice.FOOD_COST to "100",
            MethodChoice.MARGIN to "100", MethodChoice.MARKUP to "-1", MethodChoice.FOOD_COST to "abc")) {
            val original = CategoryTargetInput("Main", value, method)
            for (other in MethodChoice.entries.filter { it != method }) {
                val changed = original.withMethod(other)
                assertEquals(value, changed.percent)
                assertTrue(changed.carried)
                assertIs<Checked.Invalid>(checked(changed))
            }
        }
        val zero = CategoryTargetInput("Main", "0", MethodChoice.MARGIN).withMethod(MethodChoice.MARKUP)
        assertEquals("0", zero.percent)
        assertFalse(zero.carried)
        assertIs<Checked.Valid<SettingsDraft>>(checked(zero))
        assertTrue(zero.withMethod(MethodChoice.FOOD_COST).carried)
    }

    @Test fun choosingAnExplicitMethodForAnInheritedSectionRequiresItsOwnInput() {
        val inherited = CategoryTargetInput("Main", useGeneral = true)
        val explicit = inherited.withMethod(MethodChoice.MARGIN)
        assertFalse(explicit.useGeneral)
        assertFalse(explicit.carried)
        assertEquals("", explicit.percent)
        assertIs<Checked.Invalid>(checked(explicit))
        assertIs<Checked.Valid<SettingsDraft>>(checked(explicit.withPercent("60")))
    }

    @Test fun dormantOrOtherPageDraftsCannotSaveAnUnconfirmedSectionConversion() {
        val current = pricing.copy(methodByCategory = mapOf("Main" to PricingMethod.TargetMargin(BigDecimal(30), CostBasis.FULL_COST)))
        val saved = SettingsInput.of(current, costing, money)
        val changed = saved.withCategory(0, saved.categories[0].withMethod(MethodChoice.FOOD_COST))
        assertIs<Checked.Invalid>(changed.checkPart(SettingsPart.SECTIONS, current, costing, money))
        for (part in listOf(SettingsPart.PRICING, SettingsPart.COSTS)) {
            val result = assertIs<Checked.Valid<SettingsDraft>>(changed.checkPart(part, current, costing, money))
            assertEquals(current.methodByCategory, result.value.pricing.methodByCategory)
        }
        val general = current.copy(scope = PricingScope.GENERAL)
        val dormant = assertIs<Checked.Valid<SettingsDraft>>(changed.checkPart(SettingsPart.SECTIONS, general, costing, money))
        assertEquals(current.methodByCategory, dormant.value.pricing.methodByCategory)
    }

    @Test fun percentageHintsAndPolicyLabelsAlwaysIdentifyTheirCostBasis() {
        assertTrue(assertNotNull(CategoryTargetInput("Main", "40").equivalents()).contains("روی مواد اولیه"))
        for (basis in CostBasis.entries) {
            val caption = if (basis == CostBasis.FOOD_COST) "روی مواد اولیه" else "روی همهٔ هزینه‌ها"
            assertTrue(Labels.method(PricingMethod.TargetMargin(BigDecimal(40), basis)).contains(caption))
            assertTrue(Labels.method(PricingMethod.Markup(BigDecimal(150), basis)).contains(caption))
        }
    }
}
