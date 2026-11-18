package ir.foodcost.presentation

import ir.foodcost.core.costing.CostingRules
import ir.foodcost.core.model.PurchasePrice
import ir.foodcost.core.model.YieldStage
import ir.foodcost.core.money.DisplayCurrency
import ir.foodcost.core.money.PriceRounding
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.core.pricing.PricingSettings
import ir.foodcost.core.pricing.PricingScope
import ir.foodcost.core.units.Dimension
import ir.foodcost.core.units.Uom
import ir.foodcost.data.clock.HybridClock
import ir.foodcost.data.ledger.Ledger
import ir.foodcost.data.ops.Actor
import ir.foodcost.data.ops.Opt
import ir.foodcost.data.ops.Role
import ir.foodcost.data.store.InMemoryOperationStore
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.*

/** Owner's independently reproduced audit cases, asserting the corrected financial behavior. */
class QuickCalculatorAuditRegressionTest {
    private val today = LocalDate.of(2026, 11, 17)
    private val money = MoneyFormat(DisplayCurrency.TOMAN)

    private fun ledger(): Ledger {
        var wall = 1_000_000L
        return Ledger(InMemoryOperationStore(), HybridClock("audit-regression") { wall++ }, Actor("owner", Role.OWNER)).also {
            it.saveSettings(costing = CostingRules(BigDecimal.ZERO), pricing = PricingSettings(vatPercent = BigDecimal.ZERO, rounding = PriceRounding(1)))
        }
    }

    private fun input() = QuickInput(dishName = "غذای آزمون", menuPrice = "2000000", rows = listOf(QuickRow("گوشت", "1000000", "1000")))

    private fun save(ledger: Ledger, input: QuickInput, day: LocalDate = today) =
        QuickCalculator.save(ledger, assertIs<Checked.Valid<QuickPlan>>(QuickCalculator.check(input, ledger.state, money, day)).value, day)

    private fun equalFigures(a: QuickResult, b: QuickResult) {
        assertEquals(a.costPerPortion, b.costPerPortion)
        assertEquals(a.totalCost, b.totalCost)
        assertEquals(a.suggestedRials, b.suggestedRials)
        assertEquals(a.foodCostPercent, b.foodCostPercent)
        assertEquals(a.grossProfit, b.grossProfit)
        assertEquals(a.remaining, b.remaining)
    }

    @Test
    fun `typed names use the same ingredient yield before review and after save`() {
        val l = ledger()
        val meat = l.createIngredient("گوشت", Dimension.MASS, yields = listOf(YieldStage("پاک کردن", BigDecimal("0.8"))))
        l.recordPrice(PurchasePrice(meat, today, BigDecimal.ONE, Uom.KILOGRAM, 10_000_000))
        val typed = input()
        val before = QuickCalculator.compute(typed, l.state, money, today)
        assertEquals("۱٬۲۵۰٬۰۰۰ تومان", before.costPerPortion)
        assertEquals("بازدهی ۸۰٪", before.rows.single().note)
        val plan = assertIs<Checked.Valid<QuickPlan>>(QuickCalculator.check(typed, l.state, money, today)).value
        assertEquals(meat, plan.rows.single().ingredientId)
        assertTrue(QuickCalculator.review(typed, plan, l.state, money, today).isEmpty)
        val id = QuickCalculator.save(l, plan, today)
        val opened = assertNotNull(QuickCalculator.open(l.state, id, today, money))
        equalFigures(before, QuickCalculator.compute(opened, l.state, money, today))
    }

    @Test
    fun `a matching name of another dimension is never bound to the wrong ingredient`() {
        val l = ledger()
        val mass = l.createIngredient("روغن", Dimension.MASS, yields = listOf(YieldStage("افت", BigDecimal("0.8"))))
        val volume = l.createIngredient("روغن", Dimension.VOLUME, yields = listOf(YieldStage("افت", BigDecimal("0.5"))))
        l.recordPrice(PurchasePrice(mass, today, BigDecimal.ONE, Uom.KILOGRAM, 10_000_000))
        l.recordPrice(PurchasePrice(volume, today, BigDecimal.ONE, Uom.LITER, 10_000_000))
        val typed = input().copy(rows = listOf(QuickRow(" روغن ", "1000000", "1", Uom.LITER)))
        assertEquals("۲٬۰۰۰٬۰۰۰ تومان", QuickCalculator.compute(typed, l.state, money, today).costPerPortion)
        val plan = assertIs<Checked.Valid<QuickPlan>>(QuickCalculator.check(typed, l.state, money, today)).value
        assertEquals(volume, plan.rows.single().ingredientId)
        val id = QuickCalculator.save(l, plan, today)
        equalFigures(QuickCalculator.compute(typed, l.state, money, today), QuickCalculator.compute(assertNotNull(QuickCalculator.open(l.state, id, today, money)), l.state, money, today))
    }

    @Test
    fun `equivalent spelling retains selected identity and a genuinely different name clears it`() {
        val l = ledger()
        val id = l.createIngredient("شیر", Dimension.MASS, yields = listOf(YieldStage("افت", BigDecimal("0.8"))))
        l.recordPrice(PurchasePrice(id, today, BigDecimal.ONE, Uom.KILOGRAM, 10_000_000))
        val row = QuickCalculator.pick(l.state, QuickRow("شیر", "1000000", "1000"), id, today, money)
        val spaced = row.withName(" شير ")
        assertEquals(id, spaced.ingredientId)
        assertNull(row.withName("برنج").ingredientId)
        val typed = input().copy(rows = listOf(spaced))
        val before = QuickCalculator.compute(typed, l.state, money, today)
        assertEquals("۱٬۲۵۰٬۰۰۰ تومان", before.costPerPortion)
        val dish = save(l, typed)
        equalFigures(before, QuickCalculator.compute(assertNotNull(QuickCalculator.open(l.state, dish, today, money)), l.state, money, today))
    }

    @Test
    fun `invalid portions cannot produce definitive per portion amounts or share them`() {
        val l = ledger()
        for (bad in listOf("0", "-2", "abc")) {
            val typed = input().copy(portions = bad)
            val result = QuickCalculator.compute(typed, l.state, money, today)
            assertTrue(result.provisional, bad)
            assertNull(result.foodCostRials, bad)
            assertNull(result.totalCostRials, bad)
            assertNull(result.suggestedRials, bad)
            assertNull(result.grossProfitRials, bad)
            assertIs<Checked.Invalid>(QuickCalculator.check(typed, l.state, money, today))
            val share = QuickCalculator.shareText(typed, result, money)
            assertTrue(share.contains("این حساب ناقص است"), bad)
            assertFalse(share.contains("۱٬۰۰۰٬۰۰۰"), bad)
        }
    }

    @Test
    fun `blank portions deliberately default to one and valid reentry restores results`() {
        val l = ledger()
        val one = QuickCalculator.compute(input(), l.state, money, today)
        for (blank in listOf("", " ")) {
            val typed = input().copy(portions = blank)
            equalFigures(one, QuickCalculator.compute(typed, l.state, money, today))
            assertIs<Checked.Valid<QuickPlan>>(QuickCalculator.check(typed, l.state, money, today))
        }
        val corrected = QuickCalculator.compute(input().copy(portions = "2"), l.state, money, today)
        assertFalse(corrected.provisional)
        assertEquals("۵۰۰٬۰۰۰ تومان", corrected.costPerPortion)
    }

    @Test
    fun `future rules do not change todays calculator but apply on their effective date`() {
        val l = ledger()
        val id = save(l, input())
        l.saveSettings(costing = CostingRules(BigDecimal("20")), pricing = l.state.pricing.copy(defaultMethod = PricingMethod.TargetFoodCost(BigDecimal("50")), vatPercent = BigDecimal("10")), effectiveFrom = today.plusDays(1))
        for (day in listOf(today, today.plusDays(1))) {
            val quick = QuickCalculator.compute(assertNotNull(QuickCalculator.open(l.state, id, day, money)), l.state, money, day)
            val saved = l.calculator().evaluate(id, day)
            assertEquals(saved.suggestion.menuPrice, quick.suggestedRials, day.toString())
            assertEquals(money.full(saved.cost.foodCostPerPortion), quick.costPerPortion, day.toString())
        }
        assertEquals(28_571_429L, QuickCalculator.compute(assertNotNull(QuickCalculator.open(l.state, id, today, money)), l.state, money, today).suggestedRials)
    }

    @Test
    fun `future ingredient yield and dish pricing overrides stay out of todays preview`() {
        val l = ledger()
        val meat = l.createIngredient("گوشت", Dimension.MASS, yields = listOf(YieldStage("افت", BigDecimal("0.8"))))
        l.recordPrice(PurchasePrice(meat, today, BigDecimal.ONE, Uom.KILOGRAM, 10_000_000))
        val id = save(l, input())
        l.updateIngredient(meat, yields = Opt.Value(listOf(YieldStage("افت فردا", BigDecimal("0.5")))), effectiveFrom = today.plusDays(1))
        l.updateRecipe(id, pricing = Opt.Value(PricingMethod.TargetFoodCost(BigDecimal("50"))), effectiveFrom = today.plusDays(1))
        for (day in listOf(today, today.plusDays(1))) {
            val quick = QuickCalculator.compute(assertNotNull(QuickCalculator.open(l.state, id, day, money)), l.state, money, day)
            val saved = l.calculator().evaluate(id, day)
            assertEquals(saved.suggestion.menuPrice, quick.suggestedRials, day.toString())
            assertEquals(money.full(saved.cost.foodCostPerPortion), quick.costPerPortion, day.toString())
        }
    }

    @Test
    fun `blank saved menu price explicitly keeps its existing price and complete history`() {
        val l = ledger()
        val id = save(l, input(), today.minusDays(1))
        l.setMenuPrice(id, today, 25_000_000)
        l.closePeriod(today.minusDays(1))
        val opened = assertNotNull(QuickCalculator.open(l.state, id, today, money))
        val history = l.state.menuPrices.getValue(id)
        val cleared = opened.copy(menuPrice = "")
        val before = QuickCalculator.compute(cleared, l.state, money, today)
        assertEquals(25_000_000L, before.menuRials)
        assertEquals("۲٬۵۰۰٬۰۰۰ تومان", before.keptMenuPrice)
        save(l, cleared)
        val after = QuickCalculator.compute(assertNotNull(QuickCalculator.open(l.state, id, today, money)), l.state, money, today)
        equalFigures(before, after)
        assertEquals(history, l.state.menuPrices.getValue(id))
        assertTrue(l.state.closedMismatches.isEmpty())
        val restored = Ledger(InMemoryOperationStore(), HybridClock("restored") { 2_000_000L }, Actor("owner", Role.OWNER))
        restored.importBackup(l.exportBackup())
        assertEquals(history, restored.state.menuPrices.getValue(id))
    }

    @Test
    fun `blank price of a new dish still means no price and namesake preview keeps saved price`() {
        val l = ledger()
        val blank = input().copy(menuPrice = "")
        assertNull(QuickCalculator.compute(blank, l.state, money, today).menuRials)
        save(l, input())
        assertEquals(20_000_000L, QuickCalculator.compute(blank, l.state, money, today).menuRials)
    }

    @Test
    fun `fixed discount validates against an explicitly retained saved price`() {
        val l = ledger()
        val id = save(l, input())
        val typed = assertNotNull(QuickCalculator.open(l.state, id, today, money)).copy(menuPrice = "", discountMode = QuickInput.FIXED, discountValue = "100000")
        val before = QuickCalculator.compute(typed, l.state, money, today)
        assertEquals(19_000_000L, before.menuRials)
        assertNull(QuickCalculator.discountProblem(typed, money, before.originalMenuRials))
        assertIs<Checked.Valid<QuickPlan>>(QuickCalculator.check(typed, l.state, money, today))
        save(l, typed)
        assertEquals(20_000_000L, l.state.catalog.menuPriceAt(id, today)?.rials)
    }

    @Test
    fun `namesake preview preserves its section and own pricing before save`() {
        val l = ledger()
        l.saveSettings(pricing = l.state.pricing.copy(scope = PricingScope.MENU_SECTIONS, methodByCategory = mapOf("غذای اصلی" to PricingMethod.TargetFoodCost(BigDecimal("20")))))
        val id = save(l, input().withCategory("غذای اصلی"))
        for (method in listOf(null, PricingMethod.TargetFoodCost(BigDecimal("50")))) {
            l.updateRecipe(id, pricing = Opt.Value(method), effectiveFrom = today)
            val typed = input().copy(menuPrice = "")
            val before = QuickCalculator.compute(typed, l.state, money, today)
            val expected = l.calculator().evaluate(id, today)
            assertEquals(expected.suggestion.menuPrice, before.suggestedRials)
            save(l, typed)
            val opened = assertNotNull(QuickCalculator.open(l.state, id, today, money))
            assertEquals("غذای اصلی", opened.category)
            equalFigures(before, QuickCalculator.compute(opened, l.state, money, today))
        }
    }
}
