package ir.foodcost.presentation

import ir.foodcost.core.costing.Section
import ir.foodcost.core.model.ComponentRef
import ir.foodcost.core.model.IngredientId
import ir.foodcost.core.model.PurchasePrice
import ir.foodcost.core.model.RecipeId
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.model.RecipeLine
import ir.foodcost.core.model.RecipeVersion
import ir.foodcost.core.model.YieldStage
import ir.foodcost.core.money.DisplayCurrency
import ir.foodcost.core.pricing.PriceStatus
import ir.foodcost.core.units.Conversions
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val D1: LocalDate = LocalDate.of(2026, 10, 1)
private val D15: LocalDate = LocalDate.of(2026, 10, 15)
private val N2: LocalDate = LocalDate.of(2026, 11, 2)

/** The data tests' kitchen: rice and minced meat, plain rice, and a koobideh plate at 1,350,000 toman. */
private class Kitchen {
    private var wall = 1_000_000L
    val ledger = Ledger(InMemoryOperationStore(), HybridClock("A") { wall++ }, Actor("owner", Role.OWNER))
    val rice = ledger.createIngredient("برنج طارم", Dimension.MASS, yields = listOf(YieldStage("شستن", BigDecimal("0.98"))))
    val meat = ledger.createIngredient("گوشت چرخ‌کرده", Dimension.MASS)
    val chelow = ledger.createRecipe("چلو", RecipeKind.PREP)
    val kebab = ledger.createRecipe("چلوکباب کوبیده", RecipeKind.MENU_ITEM, category = "main")

    init {
        ledger.recordPrice(PurchasePrice(rice, D1, BigDecimal("10"), Uom.KILOGRAM, 46_000_000), priceId = "rice-1")
        ledger.recordPrice(perKilo(meat, D1, 16_000_000), priceId = "meat-1")
        ledger.saveRecipeVersion(chelow, RecipeVersion(D1, batchLines = listOf(grams(rice, "150")), miscPercent = BigDecimal.ZERO))
        ledger.saveRecipeVersion(
            kebab,
            RecipeVersion(D1, batchLines = listOf(grams(meat, "220"), portions(chelow, "1")), miscPercent = BigDecimal.ZERO),
        )
        ledger.setMenuPrice(kebab, D1, 13_500_000)
    }

    val state get() = ledger.state

    fun perKilo(id: IngredientId, from: LocalDate, rials: Long) = PurchasePrice(id, from, BigDecimal.ONE, Uom.KILOGRAM, rials)

    fun grams(id: IngredientId, qty: String) = RecipeLine(ComponentRef.IngredientRef(id), BigDecimal(qty), Uom.GRAM)

    fun portions(id: RecipeId, qty: String) = RecipeLine(ComponentRef.RecipeRef(id), BigDecimal(qty), Uom.PORTION)
}

class ScreensTest {
    private val toman = MoneyFormat(DisplayCurrency.TOMAN)

    @Test
    fun `the menu screen shows each plate's cost, prices and status in Persian`() {
        val k = Kitchen()
        val model = MenuStatusScreen.build(k.state, D1, toman)
        assertEquals("۹ مهر ۱۴۰۵", model.date)
        val row = model.rows.single()
        assertEquals("چلوکباب کوبیده", row.name)
        assertEquals(PriceStatus.OK, row.status)
        assertEquals("سالم", row.statusLabel)
        assertEquals("۴۲۲٬۴۰۸ تومان", row.foodCost)
        assertEquals("۱٬۳۵۰٬۰۰۰ تومان", row.menuPrice)
        assertEquals("۱٬۳۳۰٬۰۰۰ تومان", row.suggestedPrice)
        assertEquals("۳۴٫۴٪", row.foodCostPercent)
        assertNull(row.raise)
        assertEquals(0, model.needsRepricing)
    }

    @Test
    fun `plates that lost their margin come first, with the rise they need`() {
        val k = Kitchen()
        k.ledger.recordPrice(k.perKilo(k.meat, D15, 20_000_000))
        val salad = k.ledger.createRecipe("سالاد شیرازی", RecipeKind.MENU_ITEM)
        k.ledger.saveRecipeVersion(salad, RecipeVersion(D1, batchLines = listOf(k.grams(k.rice, "10"))))

        val model = MenuStatusScreen.build(k.state, D15, toman)
        assertEquals(listOf(PriceStatus.UNDER, PriceStatus.NO_PRICE), model.rows.map { it.status })
        val kebab = model.rows.first()
        assertEquals("۵۱۰٬۴۰۸ تومان", kebab.foodCost)
        assertEquals("۱٬۶۱۰٬۰۰۰ تومان", kebab.suggestedPrice)
        assertEquals("۱۹٪", kebab.raise)
        assertEquals(1, model.needsRepricing)
        assertNull(model.rows[1].menuPrice)
    }

    @Test
    fun `the ingredient list shows the price per kilo and the yield`() {
        val k = Kitchen()
        val egg = k.ledger.createIngredient("تخم‌مرغ", Dimension.COUNT)
        val rows = IngredientScreens.rows(k.state, D1, toman).associateBy { it.id }
        assertEquals("هر کیلو ۴۶۰٬۰۰۰ تومان", rows.getValue(k.rice).unitPrice)
        assertEquals("بازدهی ۹۸٪", rows.getValue(k.rice).yieldLabel)
        assertEquals("۱۴۰۵/۰۷/۰۹", rows.getValue(k.rice).priceDate)
        assertEquals("هر کیلو ۱٬۶۰۰٬۰۰۰ تومان", rows.getValue(k.meat).unitPrice)
        assertNull(rows.getValue(k.meat).yieldLabel)
        assertEquals("قیمت خرید ثبت نشده", rows.getValue(egg).priceProblem)
        assertEquals(listOf(k.meat), IngredientScreens.rows(k.state, D1, toman, query = "گوشت چرخ کرده").map { it.id })
    }

    @Test
    fun `the ingredient screen lists its prices, newest first, and where it is used`() {
        val k = Kitchen()
        k.ledger.recordPrice(PurchasePrice(k.rice, N2, BigDecimal("10"), Uom.KILOGRAM, 99_000_000), priceId = "typo")
        k.ledger.voidPrice("typo", "اشتباه تایپی")

        val detail = assertNotNull(IngredientScreens.detail(k.state, k.rice, N2, toman))
        assertEquals("۹۸٪", detail.yieldLabel)
        assertEquals("هر کیلو ۴۶۰٬۰۰۰ تومان", detail.unitPrice)
        assertEquals("هر کیلو ۴۶۹٬۳۸۸ تومان", detail.usableUnitPrice)
        assertEquals(listOf("typo", "rice-1"), detail.prices.map { it.priceId })
        assertTrue(detail.prices[0].voided)
        assertFalse(detail.prices[0].inForce)
        assertTrue(detail.prices[1].inForce)
        assertEquals("۱۰ کیلوگرم", detail.prices[1].packageLabel)
        assertEquals("۴٬۶۰۰٬۰۰۰ تومان", detail.prices[1].total)
        assertEquals(listOf("چلو", "چلوکباب کوبیده"), detail.usedIn)
        assertEquals(listOf(Uom.KILOGRAM, Uom.GRAM, Uom.MESGHAL), detail.purchaseUnits)
        assertEquals("98", detail.input.yieldPercent)
    }

    @Test
    fun `recipes are grouped by kind`() {
        val k = Kitchen()
        val groups = RecipeScreens.groups(k.state, D1, toman)
        assertEquals(listOf(RecipeKind.MENU_ITEM, RecipeKind.PREP), groups.map { it.kind })
        assertEquals("۷۰٬۴۰۸ تومان", groups[1].rows.single().cost)
        assertNull(groups[1].rows.single().status)
        assertEquals(PriceStatus.OK, groups[0].rows.single().status)
    }

    @Test
    fun `the recipe screen shows each line's cost and the suggested price`() {
        val k = Kitchen()
        val detail = assertNotNull(RecipeScreens.detail(k.state, k.kebab, D1, toman))
        assertEquals(listOf("گوشت چرخ‌کرده", "چلو"), detail.lines.map { it.name })
        assertEquals(listOf("۲۲۰ گرم", "۱ پرس"), detail.lines.map { it.quantity })
        assertEquals(listOf("۳۵۲٬۰۰۰ تومان", "۷۰٬۴۰۸ تومان"), detail.lines.map { it.cost })
        assertEquals("۴۲۲٬۴۰۸ تومان", detail.costPerPortion)
        assertEquals("هزینه‌های جزئی ۰٪", detail.miscLabel)
        assertEquals("برای ۱ پرس", detail.portionsLabel)
        val pricing = assertNotNull(detail.pricing)
        assertEquals("فودکاست هدف ۳۵٪", pricing.method)
        assertEquals("۱٬۳۳۰٬۰۰۰ تومان", pricing.suggested)
        assertEquals(13_500_000L, pricing.menuPriceRials)
        assertEquals("۳۴٫۴٪", pricing.foodCostPercent)
        assertTrue(detail.issues.isEmpty())
        assertNull(RecipeScreens.detail(k.state, k.chelow, D1, toman)?.pricing)
    }

    @Test
    fun `a line whose ingredient has no price says so`() {
        val k = Kitchen()
        val saffron = k.ledger.createIngredient("زعفران", Dimension.MASS)
        val draft = RecipeEdits.addLine(RecipeEdits.base(k.state.catalog.recipe(k.kebab), N2), k.grams(saffron, "0.2"))
        k.ledger.saveRecipeVersion(k.kebab, draft)

        val detail = assertNotNull(RecipeScreens.detail(k.state, k.kebab, N2, toman))
        assertEquals(PriceStatus.INCOMPLETE, detail.status)
        assertEquals(listOf("قیمت خرید «زعفران» ثبت نشده."), detail.issues)
        assertEquals("قیمت خرید «زعفران» ثبت نشده.", detail.lines.last().issue)
        assertEquals("۰٫۲ گرم", detail.lines.last().quantity)
        assertEquals("۱۴۰۵/۰۸/۱۱", detail.since)
    }

    @Test
    fun `edits start from the version in force and are dated the day they are made`() {
        val k = Kitchen()
        val base = RecipeEdits.base(k.state.catalog.recipe(k.kebab), N2)
        assertEquals(N2, base.validFrom)
        val changed = RecipeEdits.replaceLine(base, Section.BATCH, 0, k.grams(k.meat, "200"))
        assertEquals(BigDecimal("200"), RecipeEdits.lineAt(changed, Section.BATCH, 0)?.qty)
        val removed = RecipeEdits.removeLine(changed, Section.BATCH, 1)
        assertEquals(1, removed.batchLines.size)
        val batch = RecipeEdits.withBatch(removed, Batch(BigDecimal("4"), BigDecimal("2"), null, null))
        assertEquals(BigDecimal("4"), batch.portions)
        assertEquals(RecipeVersion(N2), RecipeEdits.base(null, N2))
    }

    @Test
    fun `a line can use ingredients and recipes, but never one that would make a loop`() {
        val k = Kitchen()
        val forChelow = RecipeScreens.choices(k.state, k.chelow, D1, toman)
        assertEquals(setOf("برنج طارم", "گوشت چرخ‌کرده"), forChelow.map { it.name }.toSet())
        val forKebab = RecipeScreens.choices(k.state, k.kebab, D1, toman)
        val chelow = forKebab.single { it.ref == ComponentRef.RecipeRef(k.chelow) }
        assertEquals("نیمه‌آماده · هر پرس ۷۰٬۴۰۸ تومان", chelow.detail)
        assertEquals(listOf(Uom.PORTION), chelow.units)
        val rice = forKebab.single { it.ref == ComponentRef.IngredientRef(k.rice) }
        assertTrue(rice.hasWaste)
        assertEquals(Uom.GRAM, rice.defaultUnit)
    }

    @Test
    fun `units follow what the ingredient can be converted to`() {
        assertEquals(listOf(Uom.KILOGRAM, Uom.GRAM, Uom.MESGHAL), UnitOptions.forIngredient(Dimension.MASS, Conversions()))
        assertEquals(
            listOf(Uom.PIECE, Uom.KILOGRAM, Uom.GRAM, Uom.MESGHAL),
            UnitOptions.forIngredient(Dimension.COUNT, Conversions(gramsPerPiece = BigDecimal("60"))),
        )
        assertEquals(listOf(Uom.PORTION), UnitOptions.forSubRecipe(RecipeVersion(D1)))
        assertEquals(
            listOf(Uom.PORTION, Uom.KILOGRAM, Uom.GRAM, Uom.MESGHAL),
            UnitOptions.forSubRecipe(RecipeVersion(D1, outputQty = BigDecimal("4"), outputUom = Uom.KILOGRAM)),
        )
    }
}
