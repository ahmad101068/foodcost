package ir.foodcost.presentation

import ir.foodcost.core.money.DisplayCurrency
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.core.pricing.PricingScope
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalculatorScopeTest {
    private val money = MoneyFormat(DisplayCurrency.TOMAN)
    private val day = LocalDate.of(2026, 11, 10)

    private fun ledger(): Ledger {
        var wall = 1_000_000L
        return Ledger(InMemoryOperationStore(), HybridClock("scope") { wall++ }, Actor("owner", Role.OWNER))
    }

    private fun input() = QuickInput(dishName = "چلو", menuPrice = "250000")
        .withRow(0, QuickRow("برنج", "350000", "200"))

    private fun plan(input: QuickInput, ledger: Ledger): QuickPlan =
        assertIs<Checked.Valid<QuickPlan>>(QuickCalculator.check(input, ledger.state, money, day)).value

    @Test
    fun `general pricing keeps the section label without advertising its inactive target`() {
        val ledger = ledger()
        val targets = mapOf("غذای اصلی" to PricingMethod.TargetFoodCost(BigDecimal(30)))
        ledger.saveSettings(ledger.state.costing, ledger.state.pricing.copy(methodByCategory = targets, scope = PricingScope.GENERAL))
        val food = input().withCategory("غذای اصلی")
        val general = QuickCalculator.compute(food, ledger.state, money, day)
        assertEquals("فودکاست هدف ۳۵٪", general.method)
        val id = QuickCalculator.save(ledger, plan(food, ledger), day)
        assertEquals("غذای اصلی", MenuStatusScreen.build(ledger.state, day, money).rows.single().category)
        assertEquals("غذای اصلی", ledger.state.recipes.getValue(id).category)

        ledger.saveSettings(ledger.state.costing, ledger.state.pricing.copy(scope = PricingScope.MENU_SECTIONS))
        val section = QuickCalculator.compute(food, ledger.state, money, day)
        assertEquals("فودکاست هدف ۳۰٪ برای غذای اصلی", section.method)
        assertTrue(section.suggestedRials!! > general.suggestedRials!!)
        assertEquals("غذای اصلی، هدف ۳۰٪", MenuStatusScreen.build(ledger.state, day, money).rows.single().category)

        ledger.saveSettings(ledger.state.costing, ledger.state.pricing.copy(scope = PricingScope.GENERAL))
        assertEquals(targets, ledger.state.pricing.methodByCategory)
        assertEquals(general.suggestedRials, QuickCalculator.compute(food, ledger.state, money, day).suggestedRials)
    }

    @Test
    fun `explicit no section survives draft restore and clears a namesake section when saved`() {
        val ledger = ledger()
        val original = input().withCategory("غذای اصلی")
        val id = QuickCalculator.save(ledger, plan(original, ledger), day)
        // An untouched fresh calculation still preserves metadata when saved over a namesake.
        assertEquals("غذای اصلی", plan(input(), ledger).category)
        val cleared = input().withCategory(null)
        val restored = QuickDrafts.decode(QuickDrafts.encode(cleared, DisplayCurrency.TOMAN), DisplayCurrency.TOMAN)!!
        assertTrue(restored.categoryChosen)
        assertNull(plan(restored, ledger).category)
        assertEquals(id, QuickCalculator.save(ledger, plan(restored, ledger), day))
        assertNull(ledger.state.recipes.getValue(id).category)
    }

    @Test
    fun `historical menu labels use the pricing scope of that day after a later scope switch`() {
        val ledger = ledger()
        ledger.saveSettings(
            ledger.state.costing,
            ledger.state.pricing.copy(
                methodByCategory = mapOf("غذای اصلی" to PricingMethod.TargetFoodCost(BigDecimal(30))),
                scope = PricingScope.MENU_SECTIONS,
            ),
            effectiveFrom = day,
        )
        val food = input().withCategory("غذای اصلی")
        QuickCalculator.save(ledger, plan(food, ledger), day)
        val before = MenuStatusScreen.build(ledger.state, day, money).rows.single()
        ledger.saveSettings(ledger.state.costing, ledger.state.pricing.copy(scope = PricingScope.GENERAL), effectiveFrom = day.plusDays(1))
        val historical = MenuStatusScreen.build(ledger.state, day, money).rows.single()
        assertEquals(before.category, historical.category)
        assertEquals(before.suggestedPrice, historical.suggestedPrice)
        assertEquals("غذای اصلی، هدف ۳۰٪", historical.category)
        assertEquals("غذای اصلی", MenuStatusScreen.build(ledger.state, day.plusDays(1), money).rows.single().category)
    }

    @Test
    fun `old drafts without explicit category state keep their earlier interpretation`() {
        val draft = input().copy(category = "غذای اصلی")
        val older = QuickDrafts.encode(draft, DisplayCurrency.TOMAN).replace(",\"categoryChosen\":false", "")
        val restored = QuickDrafts.decode(older, DisplayCurrency.TOMAN)!!
        assertEquals(draft, restored)
        assertFalse(restored.categoryChosen)
    }

    @Test
    fun `a legacy new rial draft still restores the same rial amounts in toman`() {
        val legacy = input().copy(rows = listOf(QuickRow("برنج", "350", "200"), QuickRow()), menuPrice = "250")
        val restored = QuickDrafts.decode(QuickDrafts.encode(legacy, DisplayCurrency.NEW_RIAL), DisplayCurrency.TOMAN)!!
        assertEquals("350000", restored.rows.first().price)
        assertEquals("250000", restored.menuPrice)
        assertEquals("200", restored.rows.first().qty)
    }
}
