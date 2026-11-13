package ir.foodcost.presentation

import ir.foodcost.core.FoodCostCalculator
import ir.foodcost.core.costing.CostingRules
import ir.foodcost.core.model.Catalog
import ir.foodcost.core.model.ComponentRef
import ir.foodcost.core.model.ExtraCost
import ir.foodcost.core.model.Ingredient
import ir.foodcost.core.model.IngredientId
import ir.foodcost.core.model.PurchasePrice
import ir.foodcost.core.model.Recipe
import ir.foodcost.core.model.RecipeId
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.model.RecipeLine
import ir.foodcost.core.model.RecipeVersion
import ir.foodcost.core.money.DisplayCurrency
import ir.foodcost.core.money.MAX_RIALS
import ir.foodcost.core.money.PriceRounding
import ir.foodcost.core.pricing.CostBasis
import ir.foodcost.core.pricing.PriceSuggestion
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.core.pricing.PricingSettings
import ir.foodcost.core.units.Dimension
import ir.foodcost.core.units.Uom
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsConversionTest {
    private val money = MoneyFormat(DisplayCurrency.RIAL)
    private val costing = CostingRules(defaultMiscPercent = BigDecimal.ZERO)
    private val pricing = PricingSettings()

    private fun input(method: MethodChoice, percent: String, basis: CostBasis = CostBasis.FOOD_COST): SettingsInput =
        SettingsInput.of(pricing, costing, money).copy(method = method, percent = percent, basis = basis)

    private fun checked(input: SettingsInput): Checked<SettingsDraft> = input.check(pricing, costing, money)

    private fun saved(input: SettingsInput): SettingsDraft = assertIs<Checked.Valid<SettingsDraft>>(checked(input)).value

    private fun invalidPercent(input: SettingsInput) {
        assertTrue(Field.PERCENT in assertIs<Checked.Invalid>(checked(input)).errors, input.toString())
    }

    private fun assertAmount(expected: String, actual: BigDecimal?, tolerance: String = "0") {
        val amount = assertNotNull(actual)
        assertTrue(
            (BigDecimal(expected) - amount).abs() <= BigDecimal(tolerance),
            "Expected $expected within $tolerance, got $amount",
        )
    }

    @Test
    fun `all six conversions represent the same price on the food basis`() {
        val percentages = mapOf(
            MethodChoice.FOOD_COST to "32",
            MethodChoice.MARGIN to "68",
            MethodChoice.MARKUP to "212.5",
        )
        for ((from, percent) in percentages) {
            val original = input(from, percent)
            for ((to, expected) in percentages.filterKeys { it != from }) {
                val converted = original.withMethod(to)
                assertEquals(to, converted.method)
                assertAmount(expected, Numbers.parse(converted.percent))
                assertFalse(converted.carried)
                assertEquals(CostBasis.FOOD_COST, converted.basis)
                assertEquals(suggestion(original).menuPrice, suggestion(converted).menuPrice)
            }
        }
    }

    @Test
    fun `complementary percentages retain every typed decimal`() {
        val food = input(MethodChoice.FOOD_COST, "۳۳٫۳۳۳۳۳۳۳")
        val margin = food.withMethod(MethodChoice.MARGIN)
        assertAmount("66.6666667", Numbers.parse(margin.percent))
        assertAmount("33.3333333", Numbers.parse(margin.withMethod(MethodChoice.FOOD_COST).percent))
        assertAmount(
            "0.001",
            Numbers.parse(input(MethodChoice.MARGIN, "99.999").withMethod(MethodChoice.FOOD_COST).percent),
        )
        assertAmount(
            "99.999",
            Numbers.parse(input(MethodChoice.FOOD_COST, "0.001").withMethod(MethodChoice.MARGIN).percent),
        )
    }

    @Test
    fun `recurring markup conversions retain calculation precision`() {
        val food = input(MethodChoice.FOOD_COST, "35")
        val markup = food.withMethod(MethodChoice.MARKUP)
        assertAmount("185.7142857142857142857142857142857", Numbers.parse(markup.percent), "1E-28")
        assertAmount("35", Numbers.parse(markup.withMethod(MethodChoice.FOOD_COST).percent), "1E-28")
        assertAmount("65", Numbers.parse(markup.withMethod(MethodChoice.MARGIN).percent), "1E-28")
        assertAmount(
            "33.33333333333333333333333333333333",
            Numbers.parse(input(MethodChoice.MARGIN, "25").withMethod(MethodChoice.MARKUP).percent),
            "1E-29",
        )
        assertEquals("یعنی حاشیهٔ سود ۶۵٪ و مارک‌آپ ۱۸۵٫۷٪، روی مواد اولیه", food.equivalents())
    }

    @Test
    fun `a tiny markup remains a positive margin instead of disappearing in a subtraction`() {
        val tiny = "0.0000000000000000000000000000000000000001"
        val margin = input(MethodChoice.MARKUP, tiny).withMethod(MethodChoice.MARGIN)
        assertFalse(margin.carried)
        assertAmount(tiny, Numbers.parse(margin.percent), "1E-70")
        assertTrue(assertNotNull(Numbers.parse(margin.percent)).signum() > 0)
        assertIs<PricingMethod.TargetMargin>(saved(margin).pricing.defaultMethod)
    }

    @Test
    fun `a tiny food cost remains positive when converted through markup`() {
        val original = input(MethodChoice.FOOD_COST, "0.00000000000000000001")
        val markup = original.withMethod(MethodChoice.MARKUP)
        val back = markup.withMethod(MethodChoice.FOOD_COST)
        assertFalse(markup.carried)
        assertFalse(back.carried)
        assertAmount("0.00000000000000000001", Numbers.parse(back.percent), "1E-50")
        saved(back)
    }

    @Test
    fun `an extremely small food cost converts to margin without a division by zero`() {
        val tiny = "0.0000000000000000000000000000000000000001"
        val food = input(MethodChoice.FOOD_COST, tiny)
        val margin = food.withMethod(MethodChoice.MARGIN)
        assertFalse(margin.carried)
        assertTrue(assertNotNull(Numbers.parse(margin.percent)) < BigDecimal(100))
        val original = suggestion(food)
        val converted = suggestion(margin)
        assertTrue(original.tooLarge)
        assertTrue(converted.tooLarge)
        assertEquals(MAX_RIALS, original.menuPrice)
        assertEquals(original.menuPrice, converted.menuPrice)
        assertAmount(tiny, Numbers.parse(margin.withMethod(MethodChoice.FOOD_COST).percent))
    }

    @Test
    fun `zero margin and zero markup convert to each other but have no valid food cost target`() {
        for ((from, to) in listOf(
            MethodChoice.MARGIN to MethodChoice.MARKUP,
            MethodChoice.MARKUP to MethodChoice.MARGIN,
        )) {
            val zero = input(from, "0")
            saved(zero)
            val converted = zero.withMethod(to)
            assertFalse(converted.carried)
            assertAmount("0", Numbers.parse(converted.percent))
            saved(converted)

            assertNull(SettingsInput.equivalent(BigDecimal.ZERO, from, MethodChoice.FOOD_COST))
            val food = zero.withMethod(MethodChoice.FOOD_COST)
            assertTrue(food.carried)
            invalidPercent(food)
            assertNull(food.equivalents())
        }
    }

    @Test
    fun `invalid source percentages never become valid by changing methods`() {
        val invalid = listOf(
            MethodChoice.FOOD_COST to "0",
            MethodChoice.FOOD_COST to "100",
            MethodChoice.FOOD_COST to "150",
            MethodChoice.FOOD_COST to "-1",
            MethodChoice.MARGIN to "100",
            MethodChoice.MARGIN to "150",
            MethodChoice.MARGIN to "-1",
            MethodChoice.MARKUP to "-1",
        )
        for ((from, percent) in invalid) {
            val original = input(from, percent)
            invalidPercent(original)
            assertNull(original.equivalents())
            for (to in MethodChoice.entries.filter { it != from }) {
                assertNull(SettingsInput.equivalent(BigDecimal(percent), from, to))
                val converted = original.withMethod(to)
                assertEquals(percent, converted.percent)
                assertTrue(converted.carried, "$from $percent to $to")
                invalidPercent(converted)
                assertNull(converted.equivalents())
            }
        }
    }

    @Test
    fun `unreadable numbers remain unfinished across method changes`() {
        for (percent in listOf("", "abc", "-")) {
            val changed = input(MethodChoice.FOOD_COST, percent).withMethod(MethodChoice.MARKUP)
            assertEquals(percent, changed.percent)
            assertTrue(changed.carried)
            invalidPercent(changed)
            assertNull(changed.equivalents())
        }
    }

    @Test
    fun `markup one hundred is valid even though food cost and margin one hundred are not`() {
        val markup = input(MethodChoice.MARKUP, "100")
        assertIs<PricingMethod.Markup>(saved(markup).pricing.defaultMethod)
        for (method in listOf(MethodChoice.FOOD_COST, MethodChoice.MARGIN)) {
            val converted = markup.withMethod(method)
            assertFalse(converted.carried)
            assertAmount("50", Numbers.parse(converted.percent))
            saved(converted)
        }
    }

    @Test
    fun `a full cost percentage cannot be saved or reinterpreted after switching to food cost`() {
        for (method in listOf(MethodChoice.MARGIN, MethodChoice.MARKUP)) {
            val original = input(method, "30", CostBasis.FULL_COST)
            var carried = original.withMethod(MethodChoice.FOOD_COST)
            assertEquals("30", carried.percent)
            assertTrue(carried.carried)
            invalidPercent(carried)
            assertNull(carried.equivalents())

            for (next in listOf(MethodChoice.MARGIN, MethodChoice.MARKUP, MethodChoice.FOOD_COST, method)) {
                carried = carried.withMethod(next)
                assertEquals("30", carried.percent)
                assertTrue(carried.carried)
                invalidPercent(carried)
                assertNull(carried.equivalents())
            }
        }
    }

    @Test
    fun `entering a percentage explicitly clears the carried state and resumes conversion`() {
        val carried = input(MethodChoice.MARGIN, "30", CostBasis.FULL_COST).withMethod(MethodChoice.FOOD_COST)
        val edited = carried.copy(percent = "35", carried = false)
        assertEquals(PricingMethod.TargetFoodCost(BigDecimal(35)), saved(edited).pricing.defaultMethod)
        val margin = edited.withMethod(MethodChoice.MARGIN)
        assertFalse(margin.carried)
        assertEquals(CostBasis.FOOD_COST, margin.basis)
        assertAmount("65", Numbers.parse(margin.percent))
        assertIs<PricingMethod.TargetMargin>(saved(margin).pricing.defaultMethod)
    }

    @Test
    fun `changing methods keeps the suggested menu price at a real rounding boundary`() {
        val food = input(MethodChoice.FOOD_COST, "35")
        // At 318,182 rials food cost, 10% VAT puts the exact price just above 1,000,000.
        // Rounding the markup to 185.71% used to pull it below that boundary and lose 100,000.
        val expected = suggestion(food)
        assertEquals(1_100_000L, expected.menuPrice)
        for (method in MethodChoice.entries) {
            val converted = food.withMethod(method)
            assertEquals(expected.menuPrice, suggestion(converted).menuPrice, method.toString())
        }
        var current = food
        repeat(20) {
            for (method in listOf(MethodChoice.MARKUP, MethodChoice.MARGIN, MethodChoice.FOOD_COST)) {
                current = current.withMethod(method)
                assertEquals(expected.menuPrice, suggestion(current).menuPrice)
            }
        }
    }

    @Test
    fun `margin and markup keep the full cost basis and its suggested price`() {
        val margin = input(MethodChoice.MARGIN, "30", CostBasis.FULL_COST)
            .copy(monthlyFixed = "1000000", monthlyPortions = "10")
        val markup = margin.withMethod(MethodChoice.MARKUP)
        assertEquals(CostBasis.FULL_COST, markup.basis)
        assertAmount("42.85714285714285714285714285714286", Numbers.parse(markup.percent), "1E-29")
        assertIs<PricingMethod.Markup>(saved(markup).pricing.defaultMethod).also {
            assertEquals(CostBasis.FULL_COST, it.basis)
        }
        val originalPrice = suggestion(margin, extraRials = 60_000).menuPrice
        assertEquals(originalPrice, suggestion(markup, extraRials = 60_000).menuPrice)
        val back = markup.withMethod(MethodChoice.MARGIN)
        assertEquals(CostBasis.FULL_COST, back.basis)
        assertAmount("30", Numbers.parse(back.percent), "1E-29")
        assertEquals(originalPrice, suggestion(back, extraRials = 60_000).menuPrice)
        assertEquals("یعنی مارک‌آپ ۴۲٫۹٪، روی همهٔ هزینه‌ها", margin.equivalents())
    }

    @Test
    fun `twenty full cost round trips restore the exact percentage originally entered`() {
        var current = input(MethodChoice.MARGIN, "30", CostBasis.FULL_COST)
        repeat(20) {
            current = current.withMethod(MethodChoice.MARKUP).withMethod(MethodChoice.MARGIN)
            assertEquals("30", current.percent)
            assertEquals(CostBasis.FULL_COST, current.basis)
            assertEquals(PricingMethod.TargetMargin(BigDecimal(30), CostBasis.FULL_COST), saved(current).pricing.defaultMethod)
        }
    }

    @Test
    fun `copying a new percentage uses that number instead of the previous conversion history`() {
        val markup = input(MethodChoice.MARGIN, "30", CostBasis.FULL_COST).withMethod(MethodChoice.MARKUP)
        val edited = markup.copy(percent = "50")
        val margin = edited.withMethod(MethodChoice.MARGIN)
        assertAmount("33.33333333333333333333333333333333", Numbers.parse(margin.percent), "1E-29")
        assertEquals(CostBasis.FULL_COST, margin.basis)
        assertEquals("50", margin.withMethod(MethodChoice.MARKUP).percent)
        assertEquals(suggestion(input(MethodChoice.MARKUP, "50", CostBasis.FULL_COST)).menuPrice, suggestion(margin).menuPrice)
    }

    @Test
    fun `copying a new basis starts conversions from the percentage on that basis`() {
        val markup = input(MethodChoice.MARGIN, "30", CostBasis.FULL_COST).withMethod(MethodChoice.MARKUP)
        val changedBasis = markup.copy(basis = CostBasis.FOOD_COST)
        val fresh = input(MethodChoice.MARKUP, markup.percent, CostBasis.FOOD_COST)
        val converted = changedBasis.withMethod(MethodChoice.MARGIN)
        assertEquals(fresh.withMethod(MethodChoice.MARGIN).percent, converted.percent)
        assertEquals(CostBasis.FOOD_COST, converted.basis)
        assertEquals(markup.percent, converted.withMethod(MethodChoice.MARKUP).percent)
        assertEquals(suggestion(fresh).menuPrice, suggestion(converted).menuPrice)
        saved(converted)
    }

    @Test
    fun `clearing conversion history after manual input starts from the displayed number`() {
        val markup = input(MethodChoice.MARGIN, "30", CostBasis.FULL_COST).withMethod(MethodChoice.MARKUP)
        val edited = markup.copy(percent = markup.percent, carried = false, conversionOrigin = null)
        val fresh = input(MethodChoice.MARKUP, markup.percent, CostBasis.FULL_COST)
        assertNull(edited.conversionOrigin)
        val margin = edited.withMethod(MethodChoice.MARGIN)
        assertEquals(fresh.withMethod(MethodChoice.MARGIN).percent, margin.percent)
        assertEquals(markup.percent, margin.withMethod(MethodChoice.MARKUP).percent)
        saved(margin)
    }

    @Test
    fun `reopening saved settings starts a new conversion sequence from the saved method`() {
        val markup = input(MethodChoice.MARGIN, "30", CostBasis.FULL_COST).withMethod(MethodChoice.MARKUP)
        val draft = saved(markup)
        val reopened = SettingsInput.of(draft.pricing, draft.costing, money)
        assertEquals(MethodChoice.MARKUP, reopened.method)
        assertEquals(markup.percent, reopened.percent)
        assertNull(reopened.conversionOrigin)
        val fresh = input(MethodChoice.MARKUP, reopened.percent, CostBasis.FULL_COST)
        val margin = reopened.withMethod(MethodChoice.MARGIN)
        assertEquals(fresh.withMethod(MethodChoice.MARGIN).percent, margin.percent)
        assertEquals(reopened.percent, margin.withMethod(MethodChoice.MARKUP).percent)
        assertEquals(suggestion(reopened).menuPrice, suggestion(margin).menuPrice)
    }

    private fun suggestion(input: SettingsInput, extraRials: Long = 0): PriceSuggestion {
        val date = LocalDate.of(2026, 11, 10)
        val ingredient = IngredientId("food")
        val dish = RecipeId("dish")
        val catalog = Catalog(
            ingredients = listOf(Ingredient(ingredient, "Food", Dimension.MASS)),
            prices = listOf(PurchasePrice(ingredient, date, BigDecimal.ONE, Uom.KILOGRAM, 318_182)),
            recipes = listOf(
                Recipe(
                    dish,
                    "Dish",
                    RecipeKind.MENU_ITEM,
                    listOf(
                        RecipeVersion(
                            date,
                            batchLines = listOf(RecipeLine(ComponentRef.IngredientRef(ingredient), BigDecimal(1000), Uom.GRAM)),
                            extras = listOf(ExtraCost("Other cost", extraRials)),
                        ),
                    ),
                ),
            ),
        )
        val draft = saved(input)
        assertEquals(PriceRounding(100_000, PriceRounding.Direction.UP), draft.pricing.rounding)
        return FoodCostCalculator(catalog, draft.costing, draft.pricing).evaluate(dish, date).suggestion
    }
}
