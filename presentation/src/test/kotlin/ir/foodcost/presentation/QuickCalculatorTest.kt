package ir.foodcost.presentation

import ir.foodcost.core.model.ComponentRef
import ir.foodcost.core.model.ExtraCost
import ir.foodcost.core.model.PurchasePrice
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.model.RecipeLine
import ir.foodcost.core.model.RecipeVersion
import ir.foodcost.core.model.YieldStage
import ir.foodcost.core.money.DisplayCurrency
import ir.foodcost.core.pricing.OnlineTerms
import ir.foodcost.core.pricing.PriceStatus
import ir.foodcost.core.units.Dimension
import ir.foodcost.core.units.Uom
import ir.foodcost.data.clock.HybridClock
import ir.foodcost.data.ledger.Ledger
import ir.foodcost.data.ops.Actor
import ir.foodcost.data.ops.Role
import ir.foodcost.data.store.InMemoryOperationStore
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
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
    fun `a quick price update is a price per kilo from today`() {
        val ledger = ledger()
        val meat = ledger.createIngredient("گوشت", Dimension.MASS)
        val ingredient = assertNotNull(ledger.state.catalog.ingredient(meat))
        val price = valid(QuickCalculator.quickPrice(ingredient, "۱٬۵۰۰٬۰۰۰", toman, today))
        assertEquals(PurchasePrice(meat, today, BigDecimal.ONE, Uom.KILOGRAM, 15_000_000), price)
        assertIs<Checked.Invalid>(QuickCalculator.quickPrice(ingredient, "0", toman, today))
    }
}
