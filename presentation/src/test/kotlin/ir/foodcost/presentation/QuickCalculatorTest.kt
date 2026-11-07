package ir.foodcost.presentation

import ir.foodcost.core.model.ComponentRef
import ir.foodcost.core.model.ExtraCost
import ir.foodcost.core.model.PurchasePrice
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.model.RecipeLine
import ir.foodcost.core.model.RecipeVersion
import ir.foodcost.core.model.YieldStage
import ir.foodcost.core.money.DisplayCurrency
import ir.foodcost.core.pricing.CostBasis
import ir.foodcost.core.pricing.OnlineTerms
import ir.foodcost.core.pricing.Overhead
import ir.foodcost.core.pricing.PriceStatus
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.core.units.Dimension
import ir.foodcost.core.units.Uom
import ir.foodcost.data.clock.HybridClock
import ir.foodcost.data.ledger.Ledger
import ir.foodcost.data.ledger.RejectReason
import ir.foodcost.data.ops.Actor
import ir.foodcost.data.ops.Opt
import ir.foodcost.data.ops.Role
import ir.foodcost.data.store.InMemoryOperationStore
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuickCalculatorTest {
    private val toman = MoneyFormat(DisplayCurrency.TOMAN)
    private val today: LocalDate = LocalDate.of(2026, 11, 1)

    private fun ledger(): Ledger {
        var wall = 1_000_000L
        return Ledger(InMemoryOperationStore(), HybridClock("A") { wall++ }, Actor("owner", Role.OWNER))
    }

    /** The owner's own koobideh: prices per kilo in toman and grams per portion. */
    private fun koobideh(menuPrice: String = ""): QuickInput {
        var input = QuickInput(dishName = "چلوکباب کوبیده", menuPrice = menuPrice)
        listOf(
            QuickRow("گوشت", "1450000", "170"),
            QuickRow("دمبه", "1100000", "30"),
            QuickRow("پیاز", "80000", "50"),
            QuickRow("برنج", "350000", "200"),
        ).forEach { input = input.withRow(input.rows.lastIndex, it) }
        return input
    }

    private fun <T> valid(checked: Checked<T>): T = assertIs<Checked.Valid<T>>(checked).value

    @Test
    fun `percentage and fixed discounts produce identical sale metrics`() {
        val ledger = ledger()
        val baseline = koobideh("630000")
        val percent = QuickCalculator.compute(baseline.copy(discountMode = "percent", discountValue = "10"), ledger.state, toman, today)
        val fixed = QuickCalculator.compute(baseline.copy(discountMode = "fixed", discountValue = "63000"), ledger.state, toman, today)
        assertEquals(percent.netSales, fixed.netSales)
        assertEquals(percent.foodCostPercent, fixed.foodCostPercent)
        assertEquals(percent.remaining, fixed.remaining)
        assertEquals(percent.margin, fixed.margin)
        assertEquals(percent.markup, fixed.markup)
        assertEquals(percent.maxCost, fixed.maxCost)
        assertEquals(5670000L, percent.menuRials)
        assertEquals("۵۱۵٬۴۵۵ تومان", percent.netSales)
        assertEquals("۷۰٫۶٪", percent.foodCostPercent)
    }

    @Test
    fun `invalid discounts cannot be saved or produce a sale`() {
        val ledger = ledger()
        val base = koobideh("630000")
        for ((mode, amount) in listOf(
            "percent" to "101", "percent" to "-1", "percent" to "100",
            "percent" to "abc", "fixed" to "630001", "fixed" to "-10",
            "fixed" to "630000", "fixed" to "abc",
        )) {
            val input = base.copy(discountMode = mode, discountValue = amount)
            assertNull(QuickCalculator.compute(input, ledger.state, toman, today).netSales, "$mode $amount")
            assertIs<Checked.Invalid>(QuickCalculator.check(input, ledger.state, toman, today), "$mode $amount")
        }
    }

    @Test
    fun `the owner's koobideh costs 364,105 toman a portion and should sell for 1,150,000`() {
        val result = QuickCalculator.compute(koobideh(), ledger().state, toman, today)
        assertEquals(
            listOf("۲۴۶٬۵۰۰ تومان", "۳۳٬۰۰۰ تومان", "۴٬۰۰۰ تومان", "۷۰٬۰۰۰ تومان", null),
            result.rows.map { it.cost },
        )
        assertEquals("قیمت هر کیلو", result.rows.first().priceLabel)
        assertEquals("۳۶۴٬۱۰۵ تومان", result.costPerPortion)
        assertEquals("با ۳٪ برای نمک، ادویه و روغن", result.miscNote)
        assertEquals("۱٬۱۵۰٬۰۰۰ تومان", result.suggested)
        assertEquals("فودکاست هدف ۳۵٪", result.method)
        assertEquals(PriceStatus.NO_PRICE, result.status)
        assertNull(result.foodCostPercent)
    }

    @Test
    fun `at 630,000 toman the plate is under-priced and the screen says by how much`() {
        val result = QuickCalculator.compute(koobideh(menuPrice = "630000"), ledger().state, toman, today)
        assertEquals(PriceStatus.UNDER, result.status)
        assertEquals("۶۳٫۶٪", result.foodCostPercent) // 630,000 includes 10% VAT, so the net sale is 572,727
        assertEquals("۵۷۲٬۷۲۷ تومان", result.netSales)
        assertEquals("\u200E+۲۰۸٬۶۲۲ تومان", result.remaining) // nothing but food entered, so it is the gross profit
        assertFalse(result.remainingNegative)
        assertEquals("۸۳٪", result.raise)
        // Backwards: for 630,000 to meet a 35% food cost, the food may cost at most 200,455.
        assertEquals("۲۰۰٬۴۵۵ تومان", result.maxCost)
        assertEquals("حداکثر بهای مواد برای این قیمت", result.maxCostLabel)
        assertEquals(emptyList(), result.channels)
    }

    @Test
    fun `other costs lower what is left without raising a food cost price, and takeaway shows beside the restaurant`() {
        val input = koobideh(menuPrice = "630000")
            .plusExtra(QuickExtra("ظرف و بسته‌بندی", "15000", takeawayOnly = true))
            .plusExtra(QuickExtra("دستمزد", "20000"))
        val result = QuickCalculator.compute(input, ledger().state, toman, today)
        assertEquals("۳۶۴٬۱۰۵ تومان", result.costPerPortion)
        assertEquals("۳۸۴٬۱۰۵ تومان", result.totalCost) // the box is not used at the table
        assertEquals("۲۰٬۰۰۰ تومان", result.otherCosts)
        assertEquals("۱٬۱۵۰٬۰۰۰ تومان", result.suggested) // a food cost target prices the food alone
        assertTrue(result.pricesFoodOnly)
        assertEquals("\u200E+۱۸۸٬۶۲۲ تومان", result.remaining)
        assertEquals("۶۳٫۶٪", result.foodCostPercent)

        assertEquals(listOf("حضوری", "بیرون‌بر"), result.channels.map { it.label })
        val takeaway = result.channels[1]
        assertEquals("\u200E+۱۷۳٬۶۲۲ تومان", takeaway.remaining)
        assertEquals("۶۵۰٬۰۰۰ تومان", takeaway.priceForSameRemaining) // 646,500 rounded up to 10,000

        val blank = QuickCalculator.compute(input.plusExtra(QuickExtra("سایر")), ledger().state, toman, today)
        assertEquals(QuickExtraResult("مبلغ را بنویسید.", waiting = true), blank.extras[2])
    }

    @Test
    fun `selling online takes the platform's discount and commission, and says what to charge there`() {
        val ledger = ledger()
        ledger.saveSettings(ledger.state.costing, ledger.state.pricing.copy(online = OnlineTerms(BigDecimal(20), BigDecimal(10), BigDecimal(15))))
        val result = QuickCalculator.compute(koobideh(menuPrice = "630000"), ledger.state, toman, today)
        assertEquals(listOf("حضوری", "آنلاین"), result.channels.map { it.label })
        val online = result.channels[1]
        assertEquals("۷۳۰٬۰۰۰ تومان", online.price) // 15% dearer, rounded up
        assertEquals("\u200E+۱۱۳٬۷۱۳ تومان", online.remaining)
        assertEquals("۸۸۰٬۰۰۰ تومان", online.priceForSameRemaining) // 875,000 rounded up
    }

    @Test
    fun `a dish saves its other costs and opens with them`() {
        val ledger = ledger()
        val input = koobideh().plusExtra(QuickExtra("ظرف و بسته‌بندی", "15000", takeawayOnly = true))
        val id = QuickCalculator.save(ledger, valid(QuickCalculator.check(input, ledger.state, toman, today)), today)
        assertEquals(listOf(ExtraCost("ظرف و بسته‌بندی", 150_000, takeawayOnly = true)), ledger.state.catalog.recipe(id)!!.versionAt(today)!!.extras)
        val opened = assertNotNull(QuickCalculator.open(ledger.state, id, today, toman))
        assertEquals(listOf(QuickExtra("ظرف و بسته‌بندی", "15000", takeawayOnly = true)), opened.extras)

        val unpriced = input.plusExtra(QuickExtra("دستمزد", ""))
        assertEquals("مبلغ «دستمزد» را بنویسید.", assertIs<Checked.Invalid>(QuickCalculator.check(unpriced, ledger.state, toman, today)).errors[Field.EXTRA])
    }

    @Test
    fun `typing into the last row brings a new one and removing rows keeps one to type into`() {
        val input = koobideh()
        assertEquals(5, input.rows.size)
        assertTrue(input.rows.last().isBlank)
        val fewer = input.withoutRow(0).withoutRow(0).withoutRow(0).withoutRow(0)
        assertEquals(listOf(QuickRow()), fewer.rows)
        assertEquals(listOf(QuickRow()), QuickInput().withoutRow(0).rows)
    }

    @Test
    fun `a row without a price is left out of the total and marked`() {
        val input = QuickInput()
            .withRow(0, QuickRow("گوشت", "1450000", "170"))
            .withRow(1, QuickRow("زعفران", "", "0.5"))
        val result = QuickCalculator.compute(input, ledger().state, toman, today)
        assertEquals("قیمت را بنویسید.", result.rows[1].problem)
        assertTrue(result.rows[1].waiting)
        assertFalse(result.rows[0].waiting)
        assertNull(result.rows[1].cost)
        assertEquals("۲۵۳٬۸۹۵ تومان", result.costPerPortion) // 246,500 plus 3%
        assertEquals(PriceStatus.INCOMPLETE, result.status)
        assertEquals(1, result.incompleteRows)
    }

    @Test
    fun `a unit that does not fit a saved ingredient is an error, not a row still being typed`() {
        val ledger = ledger()
        val id = QuickCalculator.save(ledger, valid(QuickCalculator.check(koobideh(), ledger.state, toman, today)), today)
        val opened = assertNotNull(QuickCalculator.open(ledger.state, id, today, toman))
        val pieces = opened.withRow(0, opened.rows[0].copy(uom = Uom.PIECE))
        val row = QuickCalculator.compute(pieces, ledger.state, toman, today).rows[0]
        assertEquals("این واحد به این ماده نمی‌خورد.", row.problem)
        assertFalse(row.waiting)
    }

    @Test
    fun `saving creates the ingredients, their prices and the dish, and opening it shows the same`() {
        val ledger = ledger()
        val id = QuickCalculator.save(ledger, valid(QuickCalculator.check(koobideh("630000"), ledger.state, toman, today)), today)

        val state = ledger.state
        assertEquals(setOf("گوشت", "دمبه", "پیاز", "برنج"), state.ingredients.values.map { it.name }.toSet())
        assertEquals(4, state.catalog.prices.size)
        assertEquals(RecipeKind.MENU_ITEM, state.recipes.getValue(id).kind)
        assertEquals(6_300_000L, state.catalog.menuPriceAt(id, today)?.rials)

        val opened = assertNotNull(QuickCalculator.open(state, id, today, toman))
        assertEquals("چلوکباب کوبیده", opened.dishName)
        assertEquals(listOf("1450000", "1100000", "80000", "350000", ""), opened.rows.map { it.price })
        assertEquals(listOf("170", "30", "50", "200", ""), opened.rows.map { it.qty })
        assertEquals("630000", opened.menuPrice)
        assertEquals("۳۶۴٬۱۰۵ تومان", QuickCalculator.compute(opened, state, toman, today).costPerPortion)
        assertEquals("۳۶۴٬۱۰۵ تومان", MenuStatusScreen.build(state, today, toman).rows.single().foodCost)
    }

    @Test
    fun `saving again reuses the ingredients and records only the prices that changed`() {
        val ledger = ledger()
        val id = QuickCalculator.save(ledger, valid(QuickCalculator.check(koobideh(), ledger.state, toman, today)), today)
        val opened = assertNotNull(QuickCalculator.open(ledger.state, id, today, toman))
        val dearer = opened.withRow(0, opened.rows[0].copy(price = "1600000"))
        assertEquals(id, QuickCalculator.save(ledger, valid(QuickCalculator.check(dearer, ledger.state, toman, today)), today))

        assertEquals(4, ledger.state.ingredients.size)
        assertEquals(5, ledger.state.catalog.prices.size)
        assertEquals(1, ledger.state.recipes.size)
        assertEquals("۳۹۰٬۳۷۰ تومان", MenuStatusScreen.build(ledger.state, today, toman).rows.single().foodCost)
    }

    @Test
    fun `a name typed again matches the saved ingredient instead of making a second one`() {
        val ledger = ledger()
        QuickCalculator.save(ledger, valid(QuickCalculator.check(koobideh(), ledger.state, toman, today)), today)
        val salad = QuickInput(dishName = "سالاد").withRow(0, QuickRow("پیاز", "80000", "20"))
        QuickCalculator.save(ledger, valid(QuickCalculator.check(salad, ledger.state, toman, today)), today)
        assertEquals(4, ledger.state.ingredients.size)
        assertEquals(4, ledger.state.catalog.prices.size)
    }

    @Test
    fun `saved ingredients are suggested with today's price and picking one fills the row`() {
        val ledger = ledger()
        QuickCalculator.save(ledger, valid(QuickCalculator.check(koobideh(), ledger.state, toman, today)), today)
        val typed = QuickRow(name = "گوش")
        val suggestion = QuickCalculator.suggestions(ledger.state, typed, today, toman).single()
        assertEquals("گوشت", suggestion.name)
        assertEquals("هر کیلو ۱٬۴۵۰٬۰۰۰ تومان", suggestion.detail)
        val picked = QuickCalculator.pick(ledger.state, typed, suggestion.id, today, toman)
        assertEquals(QuickRow("گوشت", "1450000", "", Uom.GRAM, suggestion.id), picked)
        assertEquals(emptyList(), QuickCalculator.suggestions(ledger.state, picked, today, toman))
    }

    @Test
    fun `a saved ingredient keeps its yield in the calculator`() {
        val ledger = ledger()
        val rice = ledger.createIngredient("برنج طارم", Dimension.MASS, yields = listOf(YieldStage("شستن", BigDecimal("0.98"))))
        ledger.recordPrice(PurchasePrice(rice, today, BigDecimal.ONE, Uom.KILOGRAM, 3_500_000))
        val row = QuickCalculator.pick(ledger.state, QuickRow(name = "برنج", qty = "200"), rice, today, toman)
        val result = QuickCalculator.compute(QuickInput().withRow(0, row), ledger.state, toman, today)
        assertEquals("۷۱٬۴۲۹ تومان", result.rows[0].cost) // 70,000 for 200 g, 2% lost in washing
        assertEquals("بازدهی ۹۸٪", result.rows[0].note)
    }

    @Test
    fun `nothing is saved without a dish name or with an unfinished row`() {
        val state = ledger().state
        val noName = assertIs<Checked.Invalid>(QuickCalculator.check(koobideh().copy(dishName = " "), state, toman, today))
        assertEquals(setOf(Field.NAME), noName.errors.keys)
        val unfinished = koobideh().withRow(4, QuickRow("زعفران", "", "0.5"))
        assertEquals("قیمت «زعفران» را بنویسید.", assertIs<Checked.Invalid>(QuickCalculator.check(unfinished, state, toman, today)).errors[Field.ROW])
        val empty = assertIs<Checked.Invalid>(QuickCalculator.check(QuickInput(dishName = "خالی"), state, toman, today))
        assertEquals("دست‌کم یک ماده با قیمت و مقدار لازم است.", empty.errors[Field.ROW])
    }

    @Test
    fun `a new calculation under the name of a dish on the menu updates it instead of making a second one`() {
        val ledger = ledger()
        val id = QuickCalculator.save(ledger, valid(QuickCalculator.check(koobideh("630000"), ledger.state, toman, today)), today)
        val again = koobideh().let { it.withRow(0, it.rows[0].copy(price = "1600000")) }.copy(dishName = "چلوکباب کوبیده ")
        val plan = valid(QuickCalculator.check(again, ledger.state, toman, today))
        assertEquals(id, plan.recipeId)
        assertEquals(id, QuickCalculator.save(ledger, plan, today))
        assertEquals(1, ledger.state.recipes.size)
        val row = MenuStatusScreen.build(ledger.state, today, toman).rows.single()
        assertEquals("۳۹۰٬۳۷۰ تومان", row.foodCost)
        assertEquals("۶۳۰٬۰۰۰ تومان", row.menuPrice)
    }

    @Test
    fun `a dish that needs the full recipe screen is not overwritten by a calculation with its name`() {
        val ledger = ledger()
        val rice = ledger.createIngredient("برنج", Dimension.MASS)
        val chelow = ledger.createRecipe("چلو", RecipeKind.PREP)
        ledger.saveRecipeVersion(chelow, RecipeVersion(today, batchLines = listOf(RecipeLine(ComponentRef.IngredientRef(rice), BigDecimal("150"), Uom.GRAM))))
        val plate = ledger.createRecipe("چلوکباب کوبیده", RecipeKind.MENU_ITEM)
        ledger.saveRecipeVersion(plate, RecipeVersion(today, batchLines = listOf(RecipeLine(ComponentRef.RecipeRef(chelow), BigDecimal.ONE, Uom.PORTION))))
        val refused = assertIs<Checked.Invalid>(QuickCalculator.check(koobideh(), ledger.state, toman, today))
        assertEquals(setOf(Field.NAME), refused.errors.keys)

        // A preparation is not a plate on the menu: a plate with its name is a dish of its own.
        assertNull(valid(QuickCalculator.check(koobideh().copy(dishName = "چلو"), ledger.state, toman, today)).recipeId)
    }

    @Test
    fun `dishes with sub-recipes and preparations open in the full recipe screen`() {
        val ledger = ledger()
        val rice = ledger.createIngredient("برنج", Dimension.MASS)
        val chelow = ledger.createRecipe("چلو", RecipeKind.PREP)
        ledger.saveRecipeVersion(chelow, RecipeVersion(today, batchLines = listOf(RecipeLine(ComponentRef.IngredientRef(rice), BigDecimal("150"), Uom.GRAM))))
        val plate = ledger.createRecipe("چلوخورش", RecipeKind.MENU_ITEM)
        ledger.saveRecipeVersion(plate, RecipeVersion(today, batchLines = listOf(RecipeLine(ComponentRef.RecipeRef(chelow), BigDecimal.ONE, Uom.PORTION))))
        assertNull(QuickCalculator.open(ledger.state, chelow, today, toman))
        assertNull(QuickCalculator.open(ledger.state, plate, today, toman))
    }

    @Test
    fun `the koobideh's suggested price is the same whichever pricing method puts it`() {
        val suggested = MethodChoice.entries.map { choice ->
            val ledger = ledger()
            val form = SettingsInput.of(ledger.state.pricing, ledger.state.costing, toman).withMethod(choice)
            val draft = valid(form.check(ledger.state.pricing, ledger.state.costing, toman))
            ledger.saveSettings(draft.costing, draft.pricing)
            QuickCalculator.compute(koobideh(), ledger.state, toman, today).suggested
        }
        assertEquals(List(3) { "۱٬۱۵۰٬۰۰۰ تومان" }, suggested)
    }

    @Test
    fun `a dish in a section with its own target is priced by it, and keeps its section`() {
        val ledger = ledger()
        ledger.saveSettings(ledger.state.costing, ledger.state.pricing.copy(methodByCategory = mapOf("غذای اصلی" to PricingMethod.TargetFoodCost(BigDecimal(30)))))
        assertEquals(listOf("غذای اصلی"), QuickCalculator.categories(ledger.state, QuickInput()))

        val main = koobideh("630000").copy(category = "غذای اصلی")
        val result = QuickCalculator.compute(main, ledger.state, toman, today)
        assertEquals("۱٬۳۴۰٬۰۰۰ تومان", result.suggested) // 364,105 at 30%, with VAT, rounded up
        assertEquals("فودکاست هدف ۳۰٪ برای غذای اصلی", result.method)
        assertEquals("۱٬۱۵۰٬۰۰۰ تومان", QuickCalculator.compute(koobideh(), ledger.state, toman, today).suggested)

        val id = QuickCalculator.save(ledger, valid(QuickCalculator.check(main, ledger.state, toman, today)), today)
        assertEquals("غذای اصلی", ledger.state.recipes.getValue(id).category)
        assertEquals("غذای اصلی", QuickCalculator.open(ledger.state, id, today, toman)?.category)
        val row = MenuStatusScreen.build(ledger.state, today, toman).rows.single()
        assertEquals("۱٬۳۴۰٬۰۰۰ تومان", row.suggestedPrice)
        assertEquals("غذای اصلی، هدف ۳۰٪", row.category)

        // Saved again without a section, the dish leaves it; a section not in the settings is still offered.
        QuickCalculator.save(ledger, valid(QuickCalculator.check(main.copy(recipeId = id, category = null), ledger.state, toman, today)), today)
        assertNull(ledger.state.recipes.getValue(id).category)
        assertEquals(listOf("غذای اصلی", "کباب"), QuickCalculator.categories(ledger.state, QuickInput(category = "کباب")))
    }

    @Test
    fun `a price ten times the last one is flagged as a slip, and a real rise is not`() {
        val ledger = ledger()
        val id = QuickCalculator.save(ledger, valid(QuickCalculator.check(koobideh(), ledger.state, toman, today)), today)
        val opened = assertNotNull(QuickCalculator.open(ledger.state, id, today, toman))
        fun warning(price: String, money: MoneyFormat = toman) =
            QuickCalculator.compute(opened.withRow(0, opened.rows[0].copy(price = price)), ledger.state, money, today).rows[0].warning

        assertEquals("قیمت «گوشت» ۱۰ برابر قیمت قبلی (۱٬۴۵۰٬۰۰۰ تومان) است؛ شاید به ریال نوشته شده.", warning("14500000"))
        assertEquals("قیمت «گوشت» یک‌دهم قیمت قبلی (۱٬۴۵۰٬۰۰۰ تومان) است؛ صفرهایش را دوباره ببینید.", warning("145000"))
        assertNull(warning("1600000"))
        assertNull(warning("2900000")) // twice the price is a rise, not a slip
        val rial = MoneyFormat(DisplayCurrency.RIAL)
        assertEquals("قیمت «گوشت» یک‌دهم قیمت قبلی (۱۴٬۵۰۰٬۰۰۰ ریال) است؛ شاید به تومان نوشته شده.", warning("1450000", rial))

        // A name typed again stands for the saved ingredient, so its price is checked too.
        val typed = QuickInput().withRow(0, QuickRow("گوشت", "14500000", "170"))
        assertEquals(warning("14500000"), QuickCalculator.compute(typed, ledger.state, toman, today).rows[0].warning)
        val meat = assertNotNull(ledger.state.catalog.ingredient(opened.rows[0].ingredientId!!))
        assertEquals(warning("14500000"), QuickCalculator.quickPriceWarning(ledger.state, meat, "14500000", toman, today))
        assertNull(QuickCalculator.quickPriceWarning(ledger.state, meat, "1500000", toman, today))

        // A purchase of 10 kilos for 145,000,000 toman is 14,500,000 a kilo: the same slip.
        val bag = PriceInput.new(today, Dimension.MASS).copy(packageQty = "10", amount = "145000000")
        assertEquals(warning("14500000"), QuickCalculator.purchaseWarning(ledger.state, meat, bag, toman, today))
        assertNull(QuickCalculator.purchaseWarning(ledger.state, meat, bag.copy(amount = "15000000"), toman, today))
    }

    @Test
    fun `the same ingredient twice is flagged, and twice at two prices is not saved`() {
        val state = ledger().state
        val twice = koobideh().withRow(4, QuickRow("پياز", "80000", "20")) // typed with an Arabic yeh
        assertEquals("«پیاز» در ردیف ۳ هم آمده است.", QuickCalculator.compute(twice, state, toman, today).rows[4].warning)
        valid(QuickCalculator.check(twice, state, toman, today))

        val twoPrices = koobideh().withRow(4, QuickRow("پیاز", "90000", "20"))
        assertEquals(
            "«پیاز» دو بار با دو قیمت آمده است؛ یکی را بردارید.",
            assertIs<Checked.Invalid>(QuickCalculator.check(twoPrices, state, toman, today)).errors[Field.ROW],
        )
    }

    @Test
    fun `before saving over a dish, the review shows what changes and which prices reach other dishes`() {
        val ledger = ledger()
        val id = QuickCalculator.save(ledger, valid(QuickCalculator.check(koobideh("630000"), ledger.state, toman, today)), today)
        val loqmeh = QuickInput(dishName = "کباب لقمه").withRow(0, QuickRow("گوشت", "1450000", "150"))
        QuickCalculator.save(ledger, valid(QuickCalculator.check(loqmeh, ledger.state, toman, today)), today)

        val opened = assertNotNull(QuickCalculator.open(ledger.state, id, today, toman))
        val dearer = opened.withRow(0, opened.rows[0].copy(price = "1600000"))
        val review = QuickCalculator.review(dearer, valid(QuickCalculator.check(dearer, ledger.state, toman, today)), ledger.state, toman, today)
        assertEquals("چلوکباب کوبیده", review.name)
        assertEquals(
            listOf(
                QuickChange("هزینهٔ هر پرس", "۳۶۴٬۱۰۵", "۳۹۰٬۳۷۰", "‎+۷٫۲٪", worse = true),
                QuickChange("قیمت پیشنهادی", "۱٬۱۵۰٬۰۰۰", "۱٬۲۳۰٬۰۰۰", "‎+۷٪", worse = true),
                QuickChange("فودکاست", "۶۳٫۶٪", "۶۸٫۲٪", null, worse = true),
                QuickChange("مانده هر پرس", "‎+۲۰۸٬۶۲۲", "‎+۱۸۲٬۳۵۷", null, worse = true),
            ),
            review.changes,
        )
        assertEquals(listOf("گوشت: هر کیلو از ۱٬۴۵۰٬۰۰۰ به ۱٬۶۰۰٬۰۰۰ تومان؛ ۱ غذای دیگر هم با آن حساب می‌شود."), review.prices)
        assertEquals(emptyList(), review.warnings)

        // A raise of the menu price alone is good news, and says so.
        val raised = opened.copy(menuPrice = "1200000")
        val raise = QuickCalculator.review(raised, valid(QuickCalculator.check(raised, ledger.state, toman, today)), ledger.state, toman, today)
        assertEquals(listOf("قیمت منو", "فودکاست", "مانده هر پرس"), raise.changes.map { it.label })
        assertEquals(listOf(false, false, false), raise.changes.map { it.worse })

        // A new dish of new ingredients changes nothing already saved.
        val fresh = QuickInput(dishName = "املت").withRow(0, QuickRow("تخم‌مرغ", "10000", "2", Uom.PIECE))
        assertTrue(QuickCalculator.review(fresh, valid(QuickCalculator.check(fresh, ledger.state, toman, today)), ledger.state, toman, today).isEmpty)
    }

    @Test
    fun `the calculation goes out as text`() {
        val input = koobideh("630000").plusExtra(QuickExtra("ظرف و بسته‌بندی", "15000", takeawayOnly = true))
        val result = QuickCalculator.compute(input, ledger().state, toman, today)
        assertEquals(
            """
            چلوکباب کوبیده

            مواد یک پرس:
            • گوشت، ۱۷۰ گرم، هر کیلو ۱٬۴۵۰٬۰۰۰ تومان: ۲۴۶٬۵۰۰ تومان
            • دمبه، ۳۰ گرم، هر کیلو ۱٬۱۰۰٬۰۰۰ تومان: ۳۳٬۰۰۰ تومان
            • پیاز، ۵۰ گرم، هر کیلو ۸۰٬۰۰۰ تومان: ۴٬۰۰۰ تومان
            • برنج، ۲۰۰ گرم، هر کیلو ۳۵۰٬۰۰۰ تومان: ۷۰٬۰۰۰ تومان
            هزینه‌های دیگر هر پرس:
            • ظرف و بسته‌بندی (فقط بیرون‌بر و آنلاین): ۱۵٬۰۰۰ تومان

            بهای مواد هر پرس: ۳۶۴٬۱۰۵ تومان، با ۳٪ برای نمک، ادویه و روغن
            قیمت پیشنهادی: ۱٬۱۵۰٬۰۰۰ تومان، فودکاست هدف ۳۵٪
            قیمت منو: ۶۳۰٬۰۰۰ تومان، زیر قیمت
            فروش خالص: ۵۷۲٬۷۲۷ تومان
            فودکاست: ۶۳٫۶٪
            مانده هر پرس: ‎+۲۰۸٬۶۲۲ تومان
            """.trimIndent(),
            QuickCalculator.shareText(input, result, toman),
        )
    }

    @Test
    fun `a draft comes back as it was typed, and an unreadable one is dropped`() {
        val ledger = ledger()
        val id = QuickCalculator.save(ledger, valid(QuickCalculator.check(koobideh(), ledger.state, toman, today)), today)
        val draft = assertNotNull(QuickCalculator.open(ledger.state, id, today, toman))
            .copy(menuPrice = "630000", miscPercent = BigDecimal("2.5"), category = "غذای اصلی")
            .plusExtra(QuickExtra("ظرف و بسته‌بندی", "15000", takeawayOnly = true))
            .withRow(4, QuickRow("زعفران", "", "0.5", Uom.MESGHAL))
        assertEquals(draft, QuickDrafts.decode(QuickDrafts.encode(draft, DisplayCurrency.TOMAN), DisplayCurrency.TOMAN))
        assertEquals(QuickInput(), QuickDrafts.decode(QuickDrafts.encode(QuickInput(), DisplayCurrency.TOMAN), DisplayCurrency.TOMAN))
        assertNull(QuickDrafts.decode("{", DisplayCurrency.TOMAN))
        assertNull(QuickDrafts.decode("""{"v":99}""", DisplayCurrency.TOMAN))
    }

    @Test
    fun `a change of currency turns the calculator's amounts into the new currency, so every figure stays`() {
        val rial = MoneyFormat(DisplayCurrency.RIAL)
        val draft = koobideh("630000").plusExtra(QuickExtra("ظرف و بسته‌بندی", "15000", takeawayOnly = true))
        val inRials = QuickDrafts.convert(draft, DisplayCurrency.TOMAN, DisplayCurrency.RIAL)
        assertEquals(listOf("14500000", "11000000", "800000", "3500000", ""), inRials.rows.map { it.price })
        assertEquals("6300000", inRials.menuPrice)
        assertEquals("150000", inRials.extras.single().amount)
        assertEquals(draft.rows.map { it.qty }, inRials.rows.map { it.qty }) // grams are not money

        // The same plate: 364,105 toman is 3,641,050 rials, at the same food cost.
        val before = QuickCalculator.compute(draft, ledger().state, toman, today)
        val after = QuickCalculator.compute(inRials, ledger().state, rial, today)
        assertEquals("۳۶۴٬۱۰۵ تومان", before.totalCost)
        assertEquals("۳٬۶۴۱٬۰۵۰ ریال", after.totalCost)
        assertEquals("۶۳٫۶٪", after.foodCostPercent)
        assertEquals(before.foodCostPercent, after.foodCostPercent)
        assertEquals(before.totalCostRials, after.totalCostRials)
        assertEquals(before.suggestedRials, after.suggestedRials)

        // Back to toman as it was typed; 1,450,000 toman is 1,450 new rials.
        assertEquals(draft, QuickDrafts.convert(inRials, DisplayCurrency.RIAL, DisplayCurrency.TOMAN))
        assertEquals("1450", QuickDrafts.convert(draft, DisplayCurrency.TOMAN, DisplayCurrency.NEW_RIAL).rows[0].price)

        // Whatever is not an amount stays as typed, to be corrected on the screen.
        val odd = QuickInput(menuPrice = "۶۳۰ هزار").withRow(0, QuickRow("گوشت", "", "170"))
        assertEquals(odd, QuickDrafts.convert(odd, DisplayCurrency.TOMAN, DisplayCurrency.RIAL))

        // A draft kept in toman and read after the switch to rials comes back in rials; one kept
        // before drafts recorded their currency is read as typed.
        assertEquals(inRials, QuickDrafts.decode(QuickDrafts.encode(draft, DisplayCurrency.TOMAN), DisplayCurrency.RIAL))
        val older = QuickDrafts.encode(draft, DisplayCurrency.TOMAN).replace("\"currency\":\"TOMAN\",", "")
        assertTrue("currency" !in older)
        assertEquals(draft, QuickDrafts.decode(older, DisplayCurrency.RIAL))
    }

    @Test
    fun `a calculation with a row not complete says its suggested price is not final`() {
        // The independent audit's B05: one row without a price, and the suggestion looked final.
        val partial = koobideh().withRow(1, QuickRow("دمبه", "", "30"))
        val result = QuickCalculator.compute(partial, ledger().state, toman, today)
        assertEquals(PriceStatus.INCOMPLETE, result.status)
        assertTrue(result.provisional)
        assertEquals("قیمت پیشنهادی (ناقص)", result.suggestedLabel)
        val text = QuickCalculator.shareText(partial, result, toman)
        assertEquals("این حساب ناقص است؛ عددهای آن قطعی نیستند.", text.lines()[1])
        assertTrue("قیمت پیشنهادی (ناقص): " in text)
        // What is left out is named, on the screen and in the text.
        assertEquals(listOf("دمبه"), result.leftOut)
        assertEquals("«دمبه» کامل نیست و در جمع نیامده است.", result.leftOutNote)
        assertTrue("«دمبه» کامل نیست و در جمع نیامده است." in text)
        val two = partial.withRow(2, QuickRow("", "50000", ""))
        assertEquals("«دمبه» و «ردیف ۳» کامل نیستند و در جمع نیامده‌اند.", QuickCalculator.compute(two, ledger().state, toman, today).leftOutNote)
        // Stored changes that could not be read are said in the text as well.
        assertEquals(BackupTexts.unreadableShort(2), QuickCalculator.shareText(koobideh(), QuickCalculator.compute(koobideh(), ledger().state, toman, today), toman, unreadable = 2).lines()[1])
        // Saving it is refused until the price is written.
        assertIs<Checked.Invalid>(QuickCalculator.check(partial.copy(dishName = "کوبیده"), ledger().state, toman, today))

        val complete = QuickCalculator.compute(koobideh(), ledger().state, toman, today)
        assertFalse(complete.provisional)
        assertEquals("قیمت پیشنهادی", complete.suggestedLabel)
        assertEquals(null, complete.leftOutNote)
    }

    @Test
    fun `an amount past any real price is said to be too large, and the largest ones still work out`() {
        val slip = koobideh().withRow(0, QuickRow("گوشت", "922337203685477580", "170"))
        val result = QuickCalculator.compute(slip, ledger().state, toman, today)
        assertEquals("این مبلغ بیش از حد بزرگ است.", result.rows[0].problem)
        assertFalse(result.rows[0].waiting)
        assertEquals(PriceStatus.INCOMPLETE, result.status)
        val refused = assertIs<Checked.Invalid>(QuickCalculator.check(slip, ledger().state, toman, today))
        assertTrue(refused.errors.values.any { "«گوشت» بیش از حد بزرگ است" in it })

        // The largest amount taken, for ten tonnes: the suggestion is past the largest amount, and is
        // said to be too large rather than shown as a number that looks like a price (audit of 0.7.0, FCA-013).
        val largest = QuickInput(dishName = "مجلس").withRow(0, QuickRow("زعفران", "100000000000000", "10000000"))
        val big = QuickCalculator.compute(largest, ledger().state, toman, today)
        assertEquals(null, big.suggestedRials)
        assertEquals("بیش از حد بزرگ", big.suggested)
        assertEquals(PriceStatus.INCOMPLETE, big.status)
        assertTrue("بیش از حد بزرگ" in big.explanations.getValue(Glossary.SUGGESTED))
    }

    @Test
    fun `a save refused part way, as on a closed day, leaves nothing of it behind`() {
        // The independent audit's B04: before, the new ingredient stayed although the save failed.
        val ledger = ledger()
        ledger.closePeriod(today)
        val stored = ledger.operations().size
        val plan = valid(QuickCalculator.check(koobideh("630000"), ledger.state, toman, today))
        val refused = assertFailsWith<QuickSaveRefused> { QuickCalculator.save(ledger, plan, today) }
        assertEquals(RejectReason.PERIOD_CLOSED, refused.reason)
        assertTrue(ledger.state.ingredients.isEmpty())
        assertTrue(ledger.state.recipes.isEmpty())
        assertEquals(stored, ledger.operations().size)
    }

    @Test
    fun `a calculation whose dish was archived since is saved as a new dish, not into the hidden one`() {
        val ledger = ledger()
        val first = QuickCalculator.save(ledger, valid(QuickCalculator.check(koobideh("630000"), ledger.state, toman, today)), today)
        val draft = assertNotNull(QuickCalculator.open(ledger.state, first, today, toman))
        assertEquals(draft, QuickCalculator.current(draft, ledger.state))

        // The dish is archived from the dishes list while the calculator still holds it.
        assertIs<Ledger.Outcome.Applied>(ledger.updateRecipe(first, archived = Opt.Value(true)))
        assertNull(QuickCalculator.current(draft, ledger.state).recipeId)
        val plan = valid(QuickCalculator.check(draft, ledger.state, toman, today))
        assertNull(plan.recipeId)
        assertTrue(QuickCalculator.review(draft, plan, ledger.state, toman, today).changes.isEmpty())

        val second = QuickCalculator.save(ledger, plan, today)
        assertTrue(second != first)
        assertEquals(true, ledger.state.recipes[first]?.archived)
        assertEquals(listOf(second), MenuStatusScreen.build(ledger.state, today, toman).rows.map { it.recipeId })

        // Even a plan made before the dish was archived does not change it.
        val other = ledger()
        val kept = QuickCalculator.save(other, valid(QuickCalculator.check(koobideh("630000"), other.state, toman, today)), today)
        val dearer = assertNotNull(QuickCalculator.open(other.state, kept, today, toman)).copy(menuPrice = "700000")
        val stale = valid(QuickCalculator.check(dearer, other.state, toman, today))
        assertEquals(kept, stale.recipeId)
        assertIs<Ledger.Outcome.Applied>(other.updateRecipe(kept, archived = Opt.Value(true)))
        val made = QuickCalculator.save(other, stale, today)
        assertTrue(made != kept)
        assertEquals(6_300_000L, other.state.catalog.menuPriceAt(kept, today)?.rials)
        assertEquals(7_000_000L, other.state.catalog.menuPriceAt(made, today)?.rials)
    }

    @Test
    fun `the dishes list and recipe screen show the calculator's cost per portion, other costs and overhead included`() {
        val ledger = ledger()
        // 120 million toman of fixed costs over 1,000 portions: 120,000 toman a portion.
        ledger.saveSettings(ledger.state.costing, ledger.state.pricing.copy(overhead = Overhead(1_200_000_000, BigDecimal(1000))))
        val input = koobideh("630000").plusExtra(QuickExtra("دستمزد", "20000"))
        val id = QuickCalculator.save(ledger, valid(QuickCalculator.check(input, ledger.state, toman, today)), today)
        val calculator = QuickCalculator.compute(assertNotNull(QuickCalculator.open(ledger.state, id, today, toman)), ledger.state, toman, today)
        assertEquals("۵۰۴٬۱۰۵ تومان", calculator.totalCost)

        val row = MenuStatusScreen.build(ledger.state, today, toman).rows.single()
        assertEquals(calculator.totalCost, row.cost)
        assertEquals("۳۶۴٬۱۰۵ تومان", row.foodCost)
        assertEquals("۱۴۰٬۰۰۰ تومان", row.otherCosts)
        assertEquals(calculator.totalCost, RecipeScreens.detail(ledger.state, id, today, toman)?.pricing?.totalCost)

        // With nothing but food, the cost is the food alone and nothing else is shown.
        val plain = ledger()
        QuickCalculator.save(plain, valid(QuickCalculator.check(koobideh("630000"), plain.state, toman, today)), today)
        val plainRow = MenuStatusScreen.build(plain.state, today, toman).rows.single()
        assertEquals("۳۶۴٬۱۰۵ تومان", plainRow.cost)
        assertNull(plainRow.otherCosts)
    }

    @Test
    fun `on every cost, a margin and the markup it turns into put the same price`() {
        val suggested = listOf(MethodChoice.MARGIN, MethodChoice.MARKUP).map { choice ->
            val ledger = ledger()
            val form = SettingsInput.of(ledger.state.pricing, ledger.state.costing, toman)
                .copy(method = MethodChoice.MARGIN, percent = "20", basis = CostBasis.FULL_COST)
                .withMethod(choice)
            val draft = valid(form.check(ledger.state.pricing, ledger.state.costing, toman))
            ledger.saveSettings(draft.costing, draft.pricing)
            QuickCalculator.compute(koobideh().plusExtra(QuickExtra("دستمزد", "20000")), ledger.state, toman, today).suggested
        }
        // 384,105 of food and wage at a 20% margin, or a 25% markup: 480,131 net, 530,000 on the menu.
        assertEquals(List(2) { "۵۳۰٬۰۰۰ تومان" }, suggested)
    }

    @Test
    fun `a quick price update is a price per kilo from today`() {
        val ledger = ledger()
        val meat = ledger.createIngredient("گوشت", Dimension.MASS)
        val ingredient = assertNotNull(ledger.state.catalog.ingredient(meat))
        val price = valid(QuickCalculator.quickPrice(ingredient, "۱٬۵۰۰٬۰۰۰", toman, today))
        assertEquals(PurchasePrice(meat, today, BigDecimal.ONE, Uom.KILOGRAM, 15_000_000), price)
        assertIs<Checked.Invalid>(QuickCalculator.quickPrice(ingredient, "0", toman, today))
    }
}
