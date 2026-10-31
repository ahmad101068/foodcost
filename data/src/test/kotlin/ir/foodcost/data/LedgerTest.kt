package ir.foodcost.data

import ir.foodcost.core.bd
import ir.foodcost.core.costing.CostingRules
import ir.foodcost.core.model.IngredientId
import ir.foodcost.core.model.PurchasePrice
import ir.foodcost.core.model.RecipeId
import ir.foodcost.core.model.RecipeVersion
import ir.foodcost.core.model.YieldStage
import ir.foodcost.core.pricing.Overhead
import ir.foodcost.core.pricing.PriceStatus
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.core.pricing.PricingSettings
import ir.foodcost.core.reports.MenuSnapshot
import ir.foodcost.core.units.Dimension
import ir.foodcost.core.units.Uom
import ir.foodcost.data.T.D1
import ir.foodcost.data.T.D15
import ir.foodcost.data.T.D31
import ir.foodcost.data.T.N2
import ir.foodcost.data.clock.Hlc
import ir.foodcost.data.ledger.RejectReason
import ir.foodcost.data.ops.Actor
import ir.foodcost.data.ops.Change
import ir.foodcost.data.ops.OpId
import ir.foodcost.data.ops.Operation
import ir.foodcost.data.ops.Opt
import ir.foodcost.data.ops.Role
import ir.foodcost.data.store.InMemoryOperationStore
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LedgerTest {

    @Test
    fun `changes entered through the ledger are priced by the core`() {
        val k = Kitchen(T.ledger("A"))
        val e = k.ledger.calculator().evaluate(k.kebab, D1)
        assertTrue(e.cost.isComplete)
        assertAmount("4224081.6327", e.cost.foodCostPerPortion) // 220 g meat + 150 g rice after a 98% yield
        assertEquals(13_300_000L, e.suggestion.menuPrice)
        assertEquals(PriceStatus.OK, e.status)
    }

    @Test
    fun `T05 operations received twice, or a backup imported twice, are applied once`() {
        val a = Kitchen(T.ledger("A")).ledger
        val b = T.ledger("B")
        val ops = a.operations()
        assertEquals(ops.size, b.receive(ops))
        val once = b.state
        assertEquals(0, b.receive(ops))
        assertEquals(0, b.receive(ops.reversed() + ops))
        assertEquals(0, b.importBackup(a.exportBackup()))
        assertEquals(once, b.state)
        assertEquals(a.state, b.state)
        assertEquals(2, b.state.catalog.prices.size)
    }

    @Test
    fun `T09 a closed period changes only after a manager reopens it, and every step is kept`() {
        val wall = FakeWall()
        val owner = T.ledger("A", Role.OWNER, wall)
        val k = Kitchen(owner)
        owner.closePeriod(D31).applied()
        assertEquals(D31, owner.state.lockedThrough)

        // A price or a menu price for a closed day is refused and nothing is stored.
        val stored = owner.operations().size
        assertEquals(RejectReason.PERIOD_CLOSED, owner.recordPrice(T.price(k.meat, D15, 18_000_000)).rejection())
        assertEquals(RejectReason.PERIOD_CLOSED, owner.setMenuPrice(k.kebab, D15, 14_000_000).rejection())
        assertEquals(stored, owner.operations().size)

        // A cook can record prices, but cannot reopen periods or change menu prices.
        val cook = T.ledger("B", Role.COOK)
        cook.receive(owner.operations())
        assertEquals(RejectReason.NOT_ALLOWED, cook.reopenPeriod(D1, "فاکتور اشتباه").rejection())
        assertEquals(RejectReason.NOT_ALLOWED, cook.setMenuPrice(k.kebab, N2, 14_000_000).rejection())
        cook.recordPrice(T.price(k.meat, N2, 17_000_000)).applied()

        // The owner reopens with a reason, fixes the price and closes again.
        assertFailsWith<IllegalArgumentException> { owner.reopenPeriod(D1, " ") }
        owner.reopenPeriod(D1, "فاکتور گوشت اشتباه ثبت شده بود").applied()
        assertNull(owner.state.lockedThrough)
        owner.recordPrice(T.price(k.meat, D15, 18_000_000)).applied()
        owner.closePeriod(D31).applied()

        val periodSteps = owner.operations().map { it.change }.filter { it is Change.PeriodClosed || it is Change.PeriodReopened }
        assertEquals(listOf("PeriodClosed", "PeriodReopened", "PeriodClosed"), periodSteps.map { it::class.simpleName })
        val reopen = owner.operations().single { it.change is Change.PeriodReopened }
        assertEquals("فاکتور گوشت اشتباه ثبت شده بود", reopen.reason)
        assertEquals(Role.OWNER, reopen.actor.role)

        // The new official figures include the corrected meat price: 220 g × 18,000 + the rice.
        val official = assertNotNull(owner.state.closedPeriods.single().snapshot)
        assertEquals(4_664_082L, official.items.single().foodCostRials)
    }

    @Test
    fun `T04 the official figures of a closed period stay as they were`() {
        val k = Kitchen(T.ledger("A"))
        k.ledger.closePeriod(D31).applied()
        val official = k.ledger.state.closedPeriods.single().snapshot

        k.ledger.recordPrice(T.price(k.meat, N2, 20_000_000)).applied()
        k.ledger.updateIngredient(k.rice, yields = Opt.Value(listOf(YieldStage("شستن", "0.9".bd)))).applied()

        assertEquals(official, k.ledger.state.closedPeriods.single().snapshot)
        assertEquals(4_224_082L, assertNotNull(official).items.single().foodCostRials)
    }

    @Test
    fun `T10 a backup restored on another phone gives the same data and the same figures`() {
        val wall = FakeWall()
        val a = T.ledger("A", Role.OWNER, wall)
        val k = Kitchen(a)
        a.updateIngredient(k.rice, name = Opt.Value("برنج \"هاشمی\"\nدرجه یک"), archived = Opt.Value(true)).applied()
        a.recordPrice(T.price(k.meat, N2, 99_000_000), priceId = "typo").applied()
        a.voidPrice("typo", "اشتباه تایپی").applied()
        a.saveSettings(
            CostingRules("3".bd, mapOf("main" to "2".bd)),
            PricingSettings(methodByCategory = mapOf("main" to PricingMethod.TargetFoodCost("33".bd)), overhead = Overhead(600_000_000, "4000".bd)),
        ).applied()
        a.closePeriod(D31).applied()
        a.saveRecipeVersion(k.kebab, RecipeVersion(N2, batchLines = listOf(T.line(k.meat, "200"), T.sub(k.chelow, "1")))).applied()
        a.setMenuPrice(k.kebab, N2, 14_500_000).applied()

        val backup = a.exportBackup()
        val b = T.ledger("B")
        assertEquals(a.operations().size, b.importBackup(backup))

        assertEquals(a.state, b.state)
        for (day in listOf(D1, D31, N2)) {
            assertEquals(MenuSnapshot.of(a.calculator().menu(day)), MenuSnapshot.of(b.calculator().menu(day)))
        }
        assertEquals(backup, b.exportBackup())
    }

    @Test
    fun `C02 edits made at the same time on two phones are all kept and both phones agree`() {
        val wallA = FakeWall(1_000_000)
        val wallB = FakeWall(1_000_000)
        val a = T.ledger("A", wall = wallA)
        val k = Kitchen(a)
        val b = T.ledger("B", wall = wallB)
        b.receive(a.operations())

        // Both phones go offline and edit.
        wallA.now = 2_000_000
        a.updateIngredient(k.rice, name = Opt.Value("برنج هاشمی")).applied()
        wallB.now = 2_000_500
        b.updateIngredient(k.rice, yields = Opt.Value(listOf(YieldStage("شستن", "0.9".bd)))).applied()
        wallA.now = 2_001_000
        a.setMenuPrice(k.kebab, N2, 14_000_000).applied()
        wallB.now = 2_001_500
        b.setMenuPrice(k.kebab, N2, 15_000_000).applied()
        wallB.now = 2_002_000
        b.recordPrice(T.price(k.meat, N2, 17_000_000)).applied()

        // They meet again and swap operations.
        val fromA = a.operations()
        val fromB = b.operations()
        a.receive(fromB)
        b.receive(fromA)

        assertEquals(a.state, b.state)
        val rice = a.state.ingredients.getValue(k.rice)
        assertEquals("برنج هاشمی", rice.name) // A's rename
        assertAmount("0.9", rice.yields.single().ratio) // B's new yield
        assertEquals(15_000_000L, a.state.menuPrices.getValue(k.kebab).getValue(N2)) // the later edit of the same price
        assertEquals(3, a.state.catalog.prices.size)

        // A third phone that gets everything in a scrambled order agrees as well.
        val c = T.ledger("C")
        c.receive((fromA + fromB).shuffled(Random(7)))
        assertEquals(a.state, c.state)
    }

    @Test
    fun `a price recorded offline for a day that was closed meanwhile is refused on every phone`() {
        val wallA = FakeWall(1_000_000)
        val wallB = FakeWall(1_000_000)
        val a = T.ledger("A", wall = wallA)
        val k = Kitchen(a)
        val b = T.ledger("B", Role.COOK, wallB)
        b.receive(a.operations())

        wallB.now = 1_500_000
        b.recordPrice(PurchasePrice(k.rice, D15, "10".bd, Uom.KILOGRAM, 47_000_000)).applied() // before the close
        wallA.now = 2_000_000
        a.closePeriod(D31).applied()
        wallB.now = 3_000_000
        val late = b.recordPrice(PurchasePrice(k.rice, D15, "10".bd, Uom.KILOGRAM, 50_000_000)).applied() // B has not heard of it

        val fromA = a.operations()
        val fromB = b.operations()
        a.receive(fromB)
        b.receive(fromA)

        assertEquals(a.state, b.state)
        assertEquals(listOf(late.id), a.state.rejections.map { it.operation.id })
        assertEquals(RejectReason.PERIOD_CLOSED, a.state.rejections.single().reason)
        assertEquals(47_000_000L, assertNotNull(a.state.catalog.priceAt(k.rice, D15)).grossRials)
    }

    @Test
    fun `the history of an ingredient or a recipe lists every change in order`() {
        val k = Kitchen(T.ledger("A"))
        k.ledger.updateIngredient(k.meat, name = Opt.Value("گوشت مخلوط")).applied()
        k.ledger.recordPrice(T.price(k.meat, N2, 17_000_000), priceId = "second").applied()
        k.ledger.voidPrice("second", "تکراری").applied()

        assertEquals(
            listOf("IngredientSaved", "PriceRecorded", "IngredientSaved", "PriceRecorded", "PriceVoided"),
            k.ledger.history(k.meat).map { it.change::class.simpleName },
        )
        assertEquals(
            listOf("RecipeSaved", "RecipeVersionSaved", "MenuPriceSet"),
            k.ledger.history(k.kebab).map { it.change::class.simpleName },
        )
    }

    @Test
    fun `wrong or repeated targets are refused`() {
        val k = Kitchen(T.ledger("A"))
        k.ledger.recordPrice(T.price(k.meat, N2, 17_000_000), priceId = "x").applied()
        assertEquals(RejectReason.DUPLICATE, k.ledger.recordPrice(T.price(k.meat, N2, 18_000_000), priceId = "x").rejection())
        k.ledger.voidPrice("x").applied()
        assertEquals(RejectReason.NO_TARGET, k.ledger.voidPrice("x").rejection())
        assertEquals(RejectReason.NO_TARGET, k.ledger.voidPrice("missing").rejection())
        assertEquals(RejectReason.NO_TARGET, k.ledger.removeMenuPrice(k.kebab, N2).rejection())
        assertEquals(RejectReason.NO_TARGET, k.ledger.reopenPeriod(D1, "هیچ دوره‌ای بسته نیست").rejection())
    }

    @Test
    fun `operations from a damaged or hand-made file are refused and cannot break the data`() {
        val k = Kitchen(T.ledger("A"))
        val before = k.ledger.state
        val ghost = IngredientId("ghost")
        var n = 0
        fun foreign(change: Change) = Operation(OpId("foreign-${n++}"), Hlc(9_000_000, n, "Z"), Actor("z", Role.OWNER), change)
        val bad = listOf(
            foreign(Change.IngredientSaved(IngredientId("per-portion"), Opt.Value("پرسی"), Opt.Value(Dimension.PORTION))),
            foreign(Change.IngredientSaved(k.rice, name = Opt.Value(" "))),
            foreign(Change.RecipeSaved(k.kebab, name = Opt.Value(""))),
            foreign(Change.MenuPriceSet(k.kebab, N2, 0)),
            foreign(Change.PriceRecorded("p-ghost", T.price(ghost, N2, 1_000))),
            foreign(Change.MenuPriceSet(RecipeId("ghost"), N2, 1_000)),
            foreign(Change.RecipeVersionSaved(RecipeId("ghost"), RecipeVersion(N2))),
        )
        assertEquals(bad.size, k.ledger.receive(bad))

        assertEquals(
            listOf(
                RejectReason.INVALID, RejectReason.INVALID, RejectReason.INVALID, RejectReason.INVALID,
                RejectReason.NO_TARGET, RejectReason.NO_TARGET, RejectReason.NO_TARGET,
            ),
            k.ledger.state.rejections.map { it.reason },
        )
        assertEquals(before.ingredients, k.ledger.state.ingredients)
        assertEquals(before.recipes, k.ledger.state.recipes)
        assertEquals(before.menuPrices, k.ledger.state.menuPrices)
        assertEquals(before.versions, k.ledger.state.versions)
        assertEquals(before.catalog.prices, k.ledger.state.catalog.prices)
        assertAmount("4224081.6327", k.ledger.calculator().evaluate(k.kebab, N2).cost.foodCostPerPortion)
    }

    @Test
    fun `bad input is refused before anything is stored`() {
        val ledger = T.ledger("A")
        assertFailsWith<IllegalArgumentException> { ledger.createIngredient(" ", Dimension.MASS) }
        assertFailsWith<IllegalArgumentException> { ledger.createIngredient("برنج", Dimension.PORTION) }
        assertFailsWith<IllegalArgumentException> { ledger.recordPrice(T.price(IngredientId("ghost"), D1, 1)) }
        assertTrue(ledger.operations().isEmpty())
    }

    @Test
    fun `a ledger opened again on the same store carries on, even if the phone's clock went back`() {
        val store = InMemoryOperationStore()
        val wall = FakeWall(5_000_000)
        val first = T.ledger("A", wall = wall, store = store)
        Kitchen(first)

        wall.now = 1_000
        val second = T.ledger("A", wall = wall, store = store)
        assertEquals(first.state, second.state)
        second.createIngredient("نمک", Dimension.MASS)
        assertTrue(second.operations().last().at > assertNotNull(first.state.lastAt))
    }
}
