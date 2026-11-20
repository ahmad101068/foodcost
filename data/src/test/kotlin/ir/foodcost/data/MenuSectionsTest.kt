package ir.foodcost.data

import ir.foodcost.core.bd
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.data.ledger.AtomicChangeRejected
import ir.foodcost.data.ledger.saveMenuSections
import ir.foodcost.data.ops.Opt
import org.junit.jupiter.api.Test
import kotlin.test.*

class MenuSectionsTest {
    @Test
    fun `rename keeps closed figures, future rules, associations and backup restore`() {
        val k = Kitchen(T.ledger("sections"))
        val l = k.ledger
        val old = l.state.pricing.copy(methodByCategory = mapOf("main" to PricingMethod.TargetFoodCost("20".bd)))
        l.saveSettings(pricing = old).applied()
        l.closePeriod(T.D31).applied()
        val future = T.N2.plusDays(10)
        l.saveSettings(pricing = old.copy(methodByCategory = mapOf("main" to PricingMethod.Markup("150".bd))), effectiveFrom = future).applied()
        l.updateRecipe(k.kebab, category = Opt.Value("other"), effectiveFrom = T.N2.plusDays(2)).applied()
        l.updateRecipe(k.kebab, category = Opt.Value("main"), effectiveFrom = future).applied()
        val days = listOf(T.D31, T.N2, T.N2.plusDays(2), future)
        val prices = days.map { l.calculator().evaluate(k.kebab, it).suggestion.menuPrice }
        val closed = l.state.closedPeriods
        l.saveMenuSections(old.copy(methodByCategory = mapOf("renamed" to old.methodByCategory.getValue("main"))), mapOf("main" to "renamed"), T.N2)
        assertEquals(prices, days.map { l.calculator().evaluate(k.kebab, it).suggestion.menuPrice })
        assertEquals(listOf("main", "renamed", "other", "renamed"), days.map { l.state.recipes.getValue(k.kebab).asOf(it).category })
        assertEquals(closed, l.state.closedPeriods)
        val restored = T.ledger("restored")
        assertEquals(l.operations().size, restored.receive(l.operations()))
        assertEquals(l.state.recipes, restored.state.recipes)
        assertEquals(prices, days.map { restored.calculator().evaluate(k.kebab, it).suggestion.menuPrice })
    }

    @Test
    fun `closed day rejects the entire rename without storing a partial rule`() {
        val l = Kitchen(T.ledger("closed-sections")).ledger
        val old = l.state.pricing.copy(methodByCategory = mapOf("main" to PricingMethod.TargetFoodCost("20".bd)))
        l.saveSettings(pricing = old).applied()
        l.closePeriod(T.D31).applied()
        val ops = l.operations()
        val state = l.state
        assertFailsWith<AtomicChangeRejected> {
            l.saveMenuSections(old.copy(methodByCategory = mapOf("new" to old.methodByCategory.getValue("main"))), mapOf("main" to "new"), T.D31)
        }
        assertEquals(ops, l.operations())
        assertEquals(state, l.state)
    }

    @Test
    fun `swapped names map each food once including archived foods`() {
        val l = T.ledger("swaps")
        val a = l.createRecipe("A", RecipeKind.MENU_ITEM, category = "A")
        val b = l.createRecipe("B", RecipeKind.MENU_ITEM, category = "B")
        l.updateRecipe(b, archived = Opt.Value(true)).applied()
        val old = l.state.pricing.copy(generalSections = listOf("A", "B"))
        l.saveSettings(pricing = old).applied()
        l.saveMenuSections(old.copy(generalSections = listOf("B", "A")), mapOf("A" to "B", "B" to "A"), T.N2)
        assertEquals("B", l.state.recipes.getValue(a).asOf(T.N2).category)
        assertEquals("A", l.state.recipes.getValue(b).asOf(T.N2).category)
        assertEquals("A", l.state.recipes.getValue(a).asOf(T.D31).category)
    }

    @Test
    fun `collision with a future section rejects without losing its pricing`() {
        val l = T.ledger("collision")
        val old = l.state.pricing.copy(generalSections = listOf("A"))
        l.saveSettings(pricing = old).applied()
        l.saveSettings(pricing = old.copy(generalSections = listOf("A", "B")), effectiveFrom = T.N2.plusDays(1)).applied()
        val ops = l.operations()
        assertFailsWith<IllegalArgumentException> {
            l.saveMenuSections(old.copy(generalSections = listOf("B")), mapOf("A" to "B"), T.N2)
        }
        assertEquals(ops, l.operations())
    }
}
