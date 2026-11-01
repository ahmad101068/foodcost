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
        val input = SettingsInput.of(PricingSettings(), CostingRules())
        assertEquals(
            SettingsInput(MethodChoice.FOOD_COST, "35", "10", true, 100_000, PriceRounding.Direction.UP, "3"),
            input,
        )
        val draft = valid(input.copy(method = MethodChoice.MARGIN, percent = "۳۰", miscPercent = "2", roundingStepRials = 50_000).check(PricingSettings(), CostingRules()))
        assertEquals(PricingMethod.TargetMargin(BigDecimal("30"), CostBasis.FOOD_COST), draft.pricing.defaultMethod)
        assertEquals(PriceRounding(50_000, PriceRounding.Direction.UP), draft.pricing.rounding)
        assertEquals(BigDecimal("2"), draft.costing.defaultMiscPercent)
        assertEquals(setOf(Field.PERCENT, Field.VAT), errors(input.copy(percent = "100", vatPercent = "-1").check(PricingSettings(), CostingRules())))
    }

    @Test
    fun `switching the pricing method turns the number into its equivalent, so the price stays`() {
        val foodCost = SettingsInput.of(PricingSettings(), CostingRules())
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
}
