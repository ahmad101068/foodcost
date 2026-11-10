package ir.foodcost.presentation

import ir.foodcost.core.costing.CostingRules
import ir.foodcost.core.model.ComponentRef
import ir.foodcost.core.model.Ingredient
import ir.foodcost.core.model.IngredientId
import ir.foodcost.core.model.QtyBasis
import ir.foodcost.core.model.RecipeId
import ir.foodcost.core.model.YieldStage
import ir.foodcost.core.money.DisplayCurrency
import ir.foodcost.core.money.PriceRounding
import ir.foodcost.core.pricing.CostBasis
import ir.foodcost.core.pricing.OnlineTerms
import ir.foodcost.core.pricing.Overhead
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.core.pricing.PricingSettings
import ir.foodcost.core.units.Conversions
import ir.foodcost.core.units.Dimension
import ir.foodcost.core.units.Uom
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class FormsTest {
    private val toman = MoneyFormat(DisplayCurrency.TOMAN)
    private val rice = IngredientId("rice")

    private fun <T> valid(checked: Checked<T>): T = assertIs<Checked.Valid<T>>(checked).value

    private fun errors(checked: Checked<*>): Set<Field> = assertIs<Checked.Invalid>(checked).errors.keys

    @Test
    fun `an ingredient needs a name and a yield above zero and up to 100`() {
        assertEquals(setOf(Field.NAME), errors(IngredientInput(name = " ").check()))
        assertEquals(setOf(Field.YIELD), errors(IngredientInput(name = "برنج", yieldPercent = "0").check()))
        assertEquals(setOf(Field.YIELD), errors(IngredientInput(name = "برنج", yieldPercent = "120").check()))
        assertEquals(setOf(Field.PIECE_WEIGHT), errors(IngredientInput(name = "تخم‌مرغ", gramsPerPiece = "x").check()))

        val draft = valid(IngredientInput(name = " برنج طارم ", yieldPercent = "۹۸", category = " ", gramsPerMilliliter = "0.9").check())
        assertEquals("برنج طارم", draft.name)
        assertNull(draft.category)
        assertEquals(listOf(YieldStage(IngredientInput.YIELD_STAGE, BigDecimal("0.98"))), draft.yields)
        assertEquals(Conversions(gramsPerMilliliter = BigDecimal("0.9")), draft.conversions)

        assertEquals(emptyList(), valid(IngredientInput(name = "نمک", yieldPercent = "").check()).yields)
    }

    @Test
    fun `a purchase is read from a Jalali date and an amount in the chosen currency`() {
        val input = PriceInput(date = "۱۴۰۵/۰۷/۰۹", packageQty = "10", packageUom = Uom.KILOGRAM, amount = "۴٬۶۰۰٬۰۰۰", supplier = " بنکداری ")
        val price = valid(input.check(rice, toman))
        assertEquals(LocalDate.of(2026, 10, 1), price.effectiveFrom)
        assertEquals(46_000_000L, price.grossRials)
        assertEquals("بنکداری", price.supplier)

        val ingredient = Ingredient(rice, "برنج", Dimension.MASS)
        assertEquals("هر کیلو ۴۶۰٬۰۰۰ تومان", input.unitPrice(ingredient, toman))
        val withTax = input.copy(tax = "460000")
        assertEquals("هر کیلو ۵۰۶٬۰۰۰ تومان", withTax.unitPrice(ingredient, toman))
        assertEquals("هر کیلو ۴۶۰٬۰۰۰ تومان", withTax.copy(taxRecoverable = true).unitPrice(ingredient, toman))
        assertNull(input.copy(amount = "").unitPrice(ingredient, toman))

        assertEquals(setOf(Field.DATE, Field.AMOUNT), errors(input.copy(date = "1404/12/30", amount = "").check(rice, toman)))
        assertEquals(setOf(Field.DISCOUNT), errors(input.copy(discount = "5000000").check(rice, toman)))
        assertEquals(setOf(Field.QUANTITY), errors(input.copy(packageQty = "0").check(rice, toman)))
    }

    @Test
    fun `a new purchase starts today, in kilos for things bought by weight`() {
        val input = PriceInput.new(LocalDate.of(2026, 10, 31), Dimension.MASS)
        assertEquals("۱۴۰۵/۰۸/۰۹", input.date)
        assertEquals(Uom.KILOGRAM, input.packageUom)
        assertEquals(Uom.LITER, PriceInput.new(LocalDate.of(2026, 10, 31), Dimension.VOLUME).packageUom)
    }

    @Test
    fun `a recipe line needs a quantity, and only ingredients can be measured as bought`() {
        val meat = ComponentRef.IngredientRef(IngredientId("meat"))
        val line = valid(LineInput("۲۲۰", Uom.GRAM, QtyBasis.AS_PURCHASED).check(meat))
        assertEquals(BigDecimal("220"), line.qty)
        assertEquals(QtyBasis.AS_PURCHASED, line.basis)
        assertEquals(QtyBasis.USABLE, valid(LineInput("1", Uom.PORTION, QtyBasis.AS_PURCHASED).check(ComponentRef.RecipeRef(RecipeId("r")))).basis)
        assertEquals(setOf(Field.QUANTITY), errors(LineInput("", Uom.GRAM).check(meat)))
        assertEquals(LineInput("220", Uom.GRAM, QtyBasis.AS_PURCHASED), LineInput.of(line))
    }

    @Test
    fun `a batch has portions, an optional misc share and an optional output`() {
        assertEquals(Batch(BigDecimal("40"), null, BigDecimal("4"), Uom.KILOGRAM), valid(BatchInput("40", "", "4", Uom.KILOGRAM).check()))
        assertEquals(Batch(BigDecimal.ONE, BigDecimal("2"), null, null), valid(BatchInput("1", "2", "", Uom.KILOGRAM).check()))
        assertEquals(setOf(Field.PORTIONS, Field.MISC), errors(BatchInput("0", "100").check()))
    }

    @Test
    fun `a menu price must be a positive whole number of rials`() {
        assertEquals(13_500_000L, valid(checkMenuPrice("1,350,000", toman)))
        assertEquals(setOf(Field.AMOUNT), errors(checkMenuPrice("", toman)))
        assertEquals(setOf(Field.AMOUNT), errors(checkMenuPrice("0", toman)))
        assertEquals(setOf(Field.AMOUNT), errors(checkMenuPrice("12.34", toman)))
    }

    @Test
    fun `settings are shown as they are saved and checked before saving`() {
        val input = SettingsInput.of(PricingSettings(), CostingRules(), toman)
        assertEquals(
            SettingsInput(MethodChoice.FOOD_COST, "35", "10", true, 100_000, PriceRounding.Direction.UP, "3"),
            input,
        )
        val draft = valid(input.copy(method = MethodChoice.MARGIN, percent = "۳۰", miscPercent = "2", roundingStepRials = 50_000).check(PricingSettings(), CostingRules(), toman))
        assertEquals(PricingMethod.TargetMargin(BigDecimal("30"), CostBasis.FOOD_COST), draft.pricing.defaultMethod)
        assertEquals(PriceRounding(50_000, PriceRounding.Direction.UP), draft.pricing.rounding)
        assertEquals(BigDecimal("2"), draft.costing.defaultMiscPercent)
        assertEquals(setOf(Field.PERCENT, Field.VAT), errors(input.copy(percent = "100", vatPercent = "-1").check(PricingSettings(), CostingRules(), toman)))
    }

    @Test
    fun `switching the pricing method turns the number into its equivalent, so the price stays`() {
        val foodCost = SettingsInput.of(PricingSettings(), CostingRules(), toman)
        assertEquals("یعنی حاشیهٔ سود ۶۵٪ و مارک‌آپ ۱۸۵٫۷٪", foodCost.equivalents())

        val margin = foodCost.withMethod(MethodChoice.MARGIN)
        assertEquals(MethodChoice.MARGIN to "65", margin.method to margin.percent)
        val markup = margin.withMethod(MethodChoice.MARKUP)
        assertEquals("185.71", markup.percent)
        assertEquals("یعنی فودکاست ۳۵٪ و حاشیهٔ سود ۶۵٪", markup.equivalents())
        assertEquals("35", markup.withMethod(MethodChoice.FOOD_COST).percent)

        // What cannot be read is left as typed.
        assertEquals("abc", foodCost.copy(percent = "abc").withMethod(MethodChoice.MARKUP).percent)
        assertNull(foodCost.copy(percent = "").equivalents())
    }

    @Test
    fun `general pricing displays three live equivalent rates as the user types`() {
        val base = SettingsInput.of(PricingSettings(), CostingRules(), toman)
        val margin = base.copy(method = MethodChoice.MARGIN, percent = "۳۰", basis = CostBasis.FOOD_COST)
        assertEquals(
            mapOf(
                MethodChoice.FOOD_COST to "70",
                MethodChoice.MARGIN to "30",
                MethodChoice.MARKUP to "42.86",
            ),
            margin.equivalentPercentages()?.mapValues { it.value?.toPlainString() },
        )
        val food = margin.copy(method = MethodChoice.FOOD_COST, percent = "35")
        assertEquals("65", food.equivalentPercentages()?.get(MethodChoice.MARGIN)?.toPlainString())
        assertEquals("185.71", food.equivalentPercentages()?.get(MethodChoice.MARKUP)?.toPlainString())
        val markup = margin.copy(method = MethodChoice.MARKUP, percent = "200")
        assertEquals("33.33", markup.equivalentPercentages()?.get(MethodChoice.FOOD_COST)?.toPlainString())
        assertEquals("66.67", markup.equivalentPercentages()?.get(MethodChoice.MARGIN)?.toPlainString())

        // A full-cost margin implies a share of ALL costs, not a food-cost figure.
        val full = margin.copy(basis = CostBasis.FULL_COST)
        assertEquals(null, full.equivalentPercentages()?.get(MethodChoice.FOOD_COST))
        assertEquals("42.86", full.equivalentPercentages()?.get(MethodChoice.MARKUP)?.toPlainString())
        assertEquals(null, margin.copy(percent = "").equivalentPercentages())
        assertEquals(null, margin.copy(percent = "abc").equivalentPercentages())
        assertEquals(null, margin.copy(carried = true).equivalentPercentages())
    }

    @Test
    fun `saving a menu label must not overwrite unsaved changes to general pricing`() {
        val current = PricingSettings(
            defaultMethod = PricingMethod.TargetMargin(BigDecimal("30"), CostBasis.FULL_COST),
            vatPercent = BigDecimal("10"),
            methodByCategory = mapOf("نوشیدنی گرم" to PricingMethod.TargetFoodCost(BigDecimal("20"))),
        )
        val costing = CostingRules()
        val pending = SettingsInput.of(current, costing, toman)
            .copy(method = MethodChoice.FOOD_COST, percent = "25", vatPercent = "9", onlineCommission = "35")
            .plusCategory("غذای اصلی")

        val savedMenu = valid(pending.forSaveArea(SettingsArea.MENU_SECTIONS, current, costing, toman).check(current, costing, toman))
        assertEquals(current.defaultMethod, savedMenu.pricing.defaultMethod)
        assertEquals(current.vatPercent, savedMenu.pricing.vatPercent)
        assertEquals(current.online, savedMenu.pricing.online)
        assertEquals(costing, savedMenu.costing)
        assertEquals(emptyMap(), savedMenu.pricing.methodByCategory)
        assertEquals(listOf("نوشیدنی گرم", "غذای اصلی"), savedMenu.pricing.generalSections)
    }

    @Test
    fun `saving global pricing converts old section targets to label only`() {
        val current = PricingSettings(methodByCategory = mapOf("غذای اصلی" to PricingMethod.TargetFoodCost(BigDecimal("30"))))
        val costing = CostingRules()
        val pending = SettingsInput.of(current, costing, toman)
            .withMethod(MethodChoice.MARGIN).copy(percent = "40")
            .plusCategory("نوشیدنی گرم") // Still unsaved and should not leak into global save.
        val saved = valid(pending.forSaveArea(SettingsArea.GENERAL_PRICING, current, costing, toman).check(current, costing, toman))
        assertEquals(PricingMethod.TargetMargin(BigDecimal("40")), saved.pricing.defaultMethod)
        assertEquals(emptyMap(), saved.pricing.methodByCategory)
        assertEquals(listOf("غذای اصلی"), saved.pricing.generalSections)
        assertEquals(current.online, saved.pricing.online)
    }

    @Test
    fun `full cost margin cannot silently become food target or be reconverted from carried value`() {
        val current = PricingSettings(defaultMethod = PricingMethod.TargetMargin(BigDecimal("30"), CostBasis.FULL_COST))
        val costing = CostingRules()
        val form = SettingsInput.of(current, costing, toman)
        val changed = form.withMethod(MethodChoice.FOOD_COST)
        assertEquals(true, changed.carried)
        assertEquals(setOf(Field.PERCENT), errors(changed.check(current, costing, toman)))
        assertEquals(setOf(Field.PERCENT), errors(changed.forSaveArea(SettingsArea.GENERAL_PRICING, current, costing, toman).check(current, costing, toman)))
        assertEquals("", changed.withMethod(MethodChoice.MARKUP).percent)
        val explicit = changed.copy(percent = "35", carried = false)
        assertEquals(PricingMethod.TargetFoodCost(BigDecimal("35")), valid(explicit.check(current, costing, toman)).pricing.defaultMethod)
    }

    @Test
    fun `saving online or fixed cost settings preserves global pricing and menu labels`() {
        val current = PricingSettings(
            defaultMethod = PricingMethod.TargetFoodCost(BigDecimal("35")),
            methodByCategory = mapOf("غذای اصلی" to PricingMethod.TargetFoodCost(BigDecimal("30"))),
            generalSections = listOf("پیش‌غذا"),
        )
        val costing = CostingRules()
        val staged = SettingsInput.of(current, costing, toman).copy(
            method = MethodChoice.MARGIN, percent = "60", onlineCommission = "18",
            monthlyFixed = "450000000", monthlyPortions = "6000",
        )
        for (area in listOf(SettingsArea.ONLINE, SettingsArea.FIXED_COSTS)) {
            val saved = valid(staged.forSaveArea(area, current, costing, toman).check(current, costing, toman))
            assertEquals(current.defaultMethod, saved.pricing.defaultMethod)
            assertEquals(emptyMap(), saved.pricing.methodByCategory)
            assertEquals(listOf("غذای اصلی", "پیش‌غذا"), saved.pricing.generalSections)
        }
    }

    @Test
    fun `the month's fixed costs, online terms and the margin basis are read, checked and shown back`() {
        val input = SettingsInput.of(PricingSettings(), CostingRules(), toman).copy(
            monthlyFixed = "450000000", monthlyPortions = "6000",
            onlineCommission = "20", onlineDiscount = "10", onlineUplift = "15",
        )
        assertEquals("۷۵٬۰۰۰ تومان", input.overheadShare(toman))
        val pricing = valid(input.check(PricingSettings(), CostingRules(), toman)).pricing
        assertEquals(Overhead(4_500_000_000, BigDecimal(6000)), pricing.overhead)
        assertEquals(OnlineTerms(BigDecimal(20), BigDecimal(10), BigDecimal(15)), pricing.online)
        assertEquals(input, SettingsInput.of(pricing, CostingRules(), toman))

        assertEquals(setOf(Field.MONTHLY_PORTIONS), errors(input.copy(monthlyPortions = "").check(PricingSettings(), CostingRules(), toman)))
        assertNull(valid(input.copy(monthlyFixed = "", monthlyPortions = "").check(PricingSettings(), CostingRules(), toman)).pricing.overhead)
        assertEquals(setOf(Field.COMMISSION), errors(input.copy(onlineCommission = "100").check(PricingSettings(), CostingRules(), toman)))

        // On every cost entered, a margin and a markup still convert, staying on every cost.
        val full = input.copy(method = MethodChoice.MARGIN, percent = "30", basis = CostBasis.FULL_COST)
        assertEquals(PricingMethod.TargetMargin(BigDecimal(30), CostBasis.FULL_COST), valid(full.check(PricingSettings(), CostingRules(), toman)).pricing.defaultMethod)
        val markup = full.withMethod(MethodChoice.MARKUP)
        assertEquals("42.86" to CostBasis.FULL_COST, markup.percent to markup.basis)
        assertEquals("30", markup.withMethod(MethodChoice.MARGIN).percent)
        assertEquals("یعنی مارک‌آپ ۴۲٫۹٪، روی همهٔ هزینه‌ها", full.equivalents())

        // A food cost target measures the food alone: from every cost it has no equivalent, so the number is kept and marked.
        val foodCost = full.withMethod(MethodChoice.FOOD_COST)
        assertEquals("30" to true, foodCost.percent to foodCost.carried)
        // Back to a margin from a food cost target, it starts on the food alone, where it puts the same price.
        val back = foodCost.copy(percent = "35", carried = false).withMethod(MethodChoice.MARGIN)
        assertEquals(Triple("65", CostBasis.FOOD_COST, false), Triple(back.percent, back.basis, back.carried))
    }

    @Test
    fun `menu labels are validated as names only and use the general pricing method`() {
        val input = SettingsInput.of(PricingSettings(), CostingRules(), toman)
            .plusCategory("نوشیدنی گرم")
            .plusCategory("")
        assertEquals(listOf("غذای اصلی", "پیش‌غذا و سالاد", "نوشیدنی سرد", "دسر و کیک", "صبحانه"), input.categoryPresets())
        val saved = valid(input.check(PricingSettings(), CostingRules(), toman)).pricing
        assertEquals(emptyMap(), saved.methodByCategory)
        assertEquals(listOf("نوشیدنی گرم"), saved.generalSections)
        assertEquals(listOf(MenuSectionInput("نوشیدنی گرم")), SettingsInput.of(saved, CostingRules(), toman).categories)
        fun error(form: SettingsInput) =
            assertIs<Checked.Invalid>(form.check(PricingSettings(), CostingRules(), toman)).errors[Field.CATEGORY]
        assertEquals("بخش «نوشيدني گرم» دو بار آمده است.", error(input.plusCategory("نوشيدني گرم")))
        assertEquals(emptyList(), valid(input.withoutCategory(0).check(PricingSettings(), CostingRules(), toman)).pricing.generalSections)
    }

    @Test
    fun `old section percentages disappear from editing but original names survive`() {
        val old = PricingSettings(
            methodByCategory = mapOf("نوشیدنی سرد" to PricingMethod.TargetMargin(BigDecimal(75), CostBasis.FULL_COST)),
            generalSections = listOf("پیش‌غذا"),
        )
        val form = SettingsInput.of(old, CostingRules(), toman)
        assertEquals(listOf(MenuSectionInput("نوشیدنی سرد"), MenuSectionInput("پیش‌غذا")), form.categories)
        val saved = valid(form.check(old, CostingRules(), toman)).pricing
        assertEquals(emptyMap(), saved.methodByCategory)
        assertEquals(listOf("نوشیدنی سرد", "پیش‌غذا"), saved.generalSections)
        assertEquals(PricingMethod.TargetFoodCost(BigDecimal(35)), saved.defaultMethod)
        assertEquals("بخش «نوشیدنی سرد» دو بار آمده است.",
            assertIs<Checked.Invalid>(form.plusCategory("نوشیدنی سرد").check(old, CostingRules(), toman)).errors[Field.CATEGORY])
    }
}
