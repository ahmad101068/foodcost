package ir.foodcost.data

import ir.foodcost.core.bd
import ir.foodcost.core.costing.CostingRules
import ir.foodcost.core.model.IngredientId
import ir.foodcost.core.model.PurchasePrice
import ir.foodcost.core.model.RecipeId
import ir.foodcost.core.model.RecipeKind
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
import ir.foodcost.data.ledger.ChangesClosedPeriod
import ir.foodcost.data.ledger.ConflictingChanges
import ir.foodcost.data.ledger.Ledger
import ir.foodcost.data.ledger.LedgerState
import ir.foodcost.data.ledger.RejectReason
import ir.foodcost.data.ops.Actor
import ir.foodcost.data.ops.Change
import ir.foodcost.data.ops.OpId
import ir.foodcost.data.ops.Operation
import ir.foodcost.data.ops.Opt
import ir.foodcost.data.ops.Role
import ir.foodcost.data.ops.Seen
import ir.foodcost.data.store.InMemoryOperationStore
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
        k.ledger.updateIngredient(k.rice, yields = Opt.Value(listOf(YieldStage("شستن", "0.9".bd))), effectiveFrom = N2).applied()

        assertEquals(official, k.ledger.state.closedPeriods.single().snapshot)
        assertEquals(4_224_082L, assertNotNull(official).items.single().foodCostRials)
    }

    @Test
    fun `a yield, a section or a setting changed after a close counts from its day, and the closed days are worked out as before`() {
        val k = Kitchen(T.ledger("A"))
        // A plate that takes the default share for spices, 3%: 100 g of meat at 16,000,000 a kilo.
        val salad = k.ledger.createRecipe("سالاد", RecipeKind.MENU_ITEM)
        k.ledger.saveRecipeVersion(salad, RecipeVersion(D1, batchLines = listOf(T.line(k.meat, "100")))).applied()
        k.ledger.closePeriod(D31).applied()
        val official = assertNotNull(k.ledger.state.closedPeriods.single().snapshot)
        fun costOn(day: java.time.LocalDate) = k.ledger.calculator().cost(k.kebab, day).foodCostPerPortion
        fun saladOn(day: java.time.LocalDate) = k.ledger.calculator().cost(salad, day).foodCostPerPortion

        // As the independent audit's B01 and B02: half the rice lost in washing, and 9% for spices, from N2 on.
        k.ledger.updateIngredient(k.rice, yields = Opt.Value(listOf(YieldStage("شستن", "0.5".bd))), effectiveFrom = N2).applied()
        k.ledger.saveSettings(costing = CostingRules("9".bd, mapOf("main" to "9".bd)), effectiveFrom = N2).applied()
        k.ledger.updateRecipe(k.kebab, category = Opt.Value("special"), effectiveFrom = N2).applied()

        assertEquals(official, MenuSnapshot.of(k.ledger.calculator().menu(D31)))
        assertAmount("1648000", saladOn(D31))
        assertAmount("1744000", saladOn(N2))
        assertAmount("4224081.6327", costOn(D31))
        assertAmount("4224081.6327", costOn(D15))
        assertTrue(costOn(N2) > costOn(D31))
        assertEquals(listOf(YieldStage("شستن", "0.5".bd)), k.ledger.state.ingredients.getValue(k.rice).yields)
        assertEquals("special", k.ledger.state.recipes.getValue(k.kebab).category)
        assertEquals(CostingRules("9".bd, mapOf("main" to "9".bd)), k.ledger.state.costing)

        // Without a day, or on a closed day, the same changes would rewrite the closed days, so they are refused.
        val half = Opt.Value(listOf(YieldStage("شستن", "0.6".bd)))
        assertEquals(RejectReason.PERIOD_CLOSED, k.ledger.updateIngredient(k.rice, yields = half).rejection())
        assertEquals(RejectReason.PERIOD_CLOSED, k.ledger.updateIngredient(k.rice, yields = half, effectiveFrom = D15).rejection())
        assertEquals(RejectReason.PERIOD_CLOSED, k.ledger.saveSettings(costing = CostingRules("5".bd)).rejection())
        assertEquals(RejectReason.PERIOD_CLOSED, k.ledger.updateRecipe(k.kebab, category = Opt.Value("drinks"), effectiveFrom = D31).rejection())
        // A name changes no figure, nor a value a day already has, so neither is refused.
        k.ledger.updateIngredient(k.rice, name = Opt.Value("برنج هاشمی")).applied()
        k.ledger.updateIngredient(k.rice, yields = Opt.Value(listOf(YieldStage("شستن", "0.5".bd))), effectiveFrom = N2).applied()
        k.ledger.updateIngredient(k.rice, yields = Opt.Value(listOf(YieldStage("شستن", "0.98".bd)))).applied()
        k.ledger.updateRecipe(k.kebab, archived = Opt.Value(false)).applied()
        k.ledger.saveSettings(costing = CostingRules("9".bd, mapOf("main" to "9".bd)), effectiveFrom = N2).applied()
        assertEquals(1, k.ledger.state.ingredients.getValue(k.rice).yieldsHistory.count { it.from == N2 })

        // Read again from the stored operations, the same days get the same figures.
        val again = ir.foodcost.data.ledger.LedgerState.fold(k.ledger.operations())
        assertEquals(k.ledger.state, again)
        assertEquals(official, MenuSnapshot.of(again.calculator().menu(D31)))
    }

    @Test
    fun `of changes entered out of order, each day takes the one that counts from the latest day before it`() {
        val k = Kitchen(T.ledger("A"))
        val late = listOf(YieldStage("شستن", "0.8".bd))
        val early = listOf(YieldStage("شستن", "0.9".bd))
        k.ledger.updateIngredient(k.rice, yields = Opt.Value(late), effectiveFrom = N2).applied()
        k.ledger.updateIngredient(k.rice, yields = Opt.Value(early), effectiveFrom = D15).applied()
        val rice = k.ledger.state.ingredients.getValue(k.rice)
        assertEquals(listOf(YieldStage("شستن", "0.98".bd)), rice.asOf(D1).yields)
        assertEquals(early, rice.asOf(D31).yields)
        assertEquals(late, rice.asOf(N2).yields)
        assertEquals(late, rice.yields) // in force now
        val calc = k.ledger.calculator()
        assertTrue(calc.cost(k.kebab, D1).foodCostPerPortion < calc.cost(k.kebab, D31).foodCostPerPortion)
        assertTrue(calc.cost(k.kebab, D31).foodCostPerPortion < calc.cost(k.kebab, N2).foodCostPerPortion)
    }

    @Test
    fun `a change of several steps is stored whole, or not at all when a step fails`() {
        val store = InMemoryOperationStore()
        val ledger = T.ledger("A", store = store)
        val before = ledger.state
        assertFailsWith<IllegalStateException> {
            ledger.atomically {
                it.createIngredient("گوشت", Dimension.MASS)
                assertEquals(1, it.state.ingredients.size) // the next step already sees it
                error("refused")
            }
        }
        assertEquals(before, ledger.state)
        assertTrue(store.all().isEmpty())

        val made = ledger.atomically {
            val meat = it.createIngredient("گوشت", Dimension.MASS)
            it.recordPrice(T.price(meat, D1, 16_000_000)).applied()
            ledger.atomically { inner -> inner.createIngredient("پیاز", Dimension.MASS) } // one change still
            meat
        }
        assertEquals(3, store.all().size)
        assertTrue(ledger.state.ingredients.containsKey(made))
        assertFailsWith<IllegalStateException> { ledger.atomically { it.receive(emptyList()) } }
    }

    @Test
    fun `a rejected atomic step rolls back earlier accepted changes without caller checking outcome`() {
        val store = InMemoryOperationStore()
        val ledger = T.ledger("A", store = store)
        val before = ledger.state
        assertFailsWith<ir.foodcost.data.ledger.AtomicChangeRejected> {
            ledger.atomically { l ->
                l.createIngredient("گوشت", Dimension.MASS)
                // The price targets an ingredient that does not exist; ignore the outcome.
                l.recordPrice(T.price(ir.foodcost.core.model.IngredientId("missing"), D1, 16_000_000))
            }
        }
        assertEquals(before, ledger.state)
        assertTrue(store.all().isEmpty())
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
    fun `a price for a closed day that the close had not seen stays out on every phone, whenever it was recorded`() {
        val wallA = FakeWall(1_000_000)
        val wallB = FakeWall(1_000_000)
        val a = T.ledger("A", wall = wallA)
        val k = Kitchen(a)
        val b = T.ledger("B", Role.COOK, wallB)
        b.receive(a.operations())

        wallB.now = 1_500_000
        val early = b.recordPrice(PurchasePrice(k.rice, D15, "10".bd, Uom.KILOGRAM, 47_000_000)).applied() // before the close, by B's clock
        wallA.now = 2_000_000
        a.closePeriod(D31).applied() // A has not seen B's price
        wallB.now = 3_000_000
        val late = b.recordPrice(PurchasePrice(k.rice, D15, "10".bd, Uom.KILOGRAM, 50_000_000)).applied() // B has not heard of the close

        val fromA = a.operations()
        val fromB = b.operations()
        a.receive(fromB)
        b.receive(fromA)

        assertEquals(a.state, b.state)
        assertEquals(setOf(early.id, late.id), a.state.rejections.map { it.operation.id }.toSet())
        assertTrue(a.state.rejections.all { it.reason == RejectReason.PERIOD_CLOSED })
        assertEquals(46_000_000L, assertNotNull(a.state.catalog.priceAt(k.rice, D15)).grossRials) // as the close found it
        // The official figures and the figures worked out again agree.
        assertEquals(a.state.closedPeriods.single().snapshot, MenuSnapshot.of(a.calculator().menu(D31)))
    }

    @Test
    fun `a change recorded before a close but arriving after it stays out of the closed days, and the look before an import says so`() {
        // The case the audit of 0.7.0 found: a dearer meat price from another phone, stamped before the close.
        val wall = FakeWall(1_000)
        val a = T.ledger("A", wall = wall)
        val k = Kitchen(a)
        val closed = a.calculator().cost(k.kebab, D15).foodCostPerPortion
        wall.now = 3_000
        a.closePeriod(D31).applied()
        val remote = Operation(
            OpId("remote"), Hlc(2_000, 0, "B"), Actor("B-user", Role.OWNER),
            Change.PriceRecorded("remote", T.price(k.meat, D1, 32_000_000)),
        )

        assertEquals(1, a.preview(listOf(remote)).refused)
        assertEquals(1, a.receive(listOf(remote)))
        assertEquals(RejectReason.PERIOD_CLOSED, a.state.rejections.single { it.operation.id == remote.id }.reason)
        assertEquals(0, closed.compareTo(a.calculator().cost(k.kebab, D15).foodCostPerPortion))
        assertEquals(a.state.closedPeriods.single().snapshot, MenuSnapshot.of(a.calculator().menu(D31)))
        assertEquals(a.state, LedgerState.fold(a.operations().shuffled(Random(5)))) // the same whatever the order
    }

    @Test
    fun `reopening a period takes in the changes its close had not seen, and closing it again keeps them`() {
        val wallA = FakeWall(1_000)
        val a = T.ledger("A", wall = wallA)
        val k = Kitchen(a)
        val wallB = FakeWall(1_000)
        val b = T.ledger("B", wall = wallB)
        b.receive(a.operations())
        wallB.now = 2_000
        val dearer = b.recordPrice(T.price(k.meat, D1, 32_000_000), priceId = "dearer").applied()
        wallA.now = 3_000
        a.closePeriod(D31).applied()
        a.receive(b.operations())
        val closed = a.calculator().cost(k.kebab, D15).foodCostPerPortion
        assertTrue(a.state.rejections.any { it.operation.id == dearer.id })

        wallA.now = 4_000
        a.reopenPeriod(D1, "فاکتور گوشت دیر رسید").applied()
        assertTrue(a.state.rejections.none { it.operation.id == dearer.id })
        val open = a.calculator().cost(k.kebab, D15).foodCostPerPortion
        assertTrue(open > closed)

        wallA.now = 5_000
        a.closePeriod(D31).applied()
        assertEquals(a.state.closedPeriods.single().snapshot, MenuSnapshot.of(a.calculator().menu(D31)))
        assertEquals(0, open.compareTo(a.calculator().cost(k.kebab, D15).foodCostPerPortion))

        // A phone that gets everything, in any order, comes to the same.
        val c = T.ledger("C")
        c.receive(a.operations().shuffled(Random(3)))
        assertEquals(a.state, c.state)
        b.receive(a.operations())
        assertEquals(a.state, b.state)
    }

    @Test
    fun `a close knows which changes of each phone it had, so one left out of a file that passed on later ones stays out`() {
        // The audit of 0.7.1 (FCA-017): A had B's later price but not its earlier one when it closed.
        val wallA = FakeWall(1_000)
        val a = T.ledger("A", wall = wallA)
        val k = Kitchen(a)
        val wallB = FakeWall(2_000)
        val b = T.ledger("B", wall = wallB)
        b.receive(a.operations())
        val earlier = b.recordPrice(T.price(k.meat, D15, 24_000_000), priceId = "earlier").applied()
        wallB.now = 2_500
        val later = b.recordPrice(T.price(k.meat, D15.plusDays(10), 32_000_000), priceId = "later").applied()
        a.receive(listOf(later)) // a file with B's later change but not its earlier one
        wallA.now = 3_000
        a.closePeriod(D31).applied()
        val closed = a.calculator().cost(k.kebab, D15).foodCostPerPortion
        assertEquals(Seen(later.at, seq = 2, missing = listOf(1L..1L), unnumbered = emptySet()), a.state.closedPeriods.single().seen?.get("B"))

        assertEquals(1, a.preview(listOf(earlier)).refused)
        a.receive(listOf(earlier))
        assertEquals(RejectReason.PERIOD_CLOSED, a.state.rejections.single { it.operation.id == earlier.id }.reason)
        assertEquals(0, closed.compareTo(a.calculator().cost(k.kebab, D15).foodCostPerPortion))
        assertEquals(a.state.closedPeriods.single().snapshot, MenuSnapshot.of(a.calculator().menu(D31)))
        // B, which had both, comes to the same once it has A's close.
        b.receive(a.operations())
        assertEquals(a.state, b.state)
    }

    @Test
    fun `a close that did not keep what it had seen is trusted only for its own phone's changes`() {
        // The audit of 0.7.1 (FCA-018): a close as 0.7.0 made it, then a dearer price from another phone stamped before it.
        val wall = FakeWall(1_000)
        val author = T.ledger("A", wall = wall)
        val k = Kitchen(author)
        wall.now = 3_000
        author.closePeriod(D31).applied()
        val legacy = author.operations().map { op -> (op.change as? Change.PeriodClosed)?.let { op.copy(change = it.copy(seen = null)) } ?: op }
        val a = T.ledger("A", wall = FakeWall(4_000), store = InMemoryOperationStore(legacy))
        assertFalse(a.state.closedPeriods.single().verified)
        val closed = a.calculator().cost(k.kebab, D15).foodCostPerPortion
        assertEquals(a.state.closedPeriods.single().snapshot, MenuSnapshot.of(a.calculator().menu(D31))) // its own changes all count

        val late = Operation(OpId("late"), Hlc(2_000, 0, "B"), Actor("B-user", Role.OWNER), Change.PriceRecorded("late", T.price(k.meat, D1, 32_000_000)))
        assertEquals(1, a.preview(listOf(late)).refused)
        a.receive(listOf(late))
        assertEquals(RejectReason.PERIOD_CLOSED, a.state.rejections.single().reason)
        assertEquals(0, closed.compareTo(a.calculator().cost(k.kebab, D15).foodCostPerPortion))
    }

    @Test
    fun `a close lists the unnumbered changes it had, so an older one left out of a file stays out`() {
        // The audit of 0.7.2 (FCA-020): changes of a version before numbering, one of them left out.
        val a = T.ledger("A", wall = FakeWall(1_000))
        val k = Kitchen(a)
        val b = T.ledger("B", wall = FakeWall(2_000))
        b.receive(a.operations())
        val earlier = b.recordPrice(T.price(k.meat, D15, 24_000_000), priceId = "old-earlier").applied().copy(seq = null)
        val later = b.recordPrice(T.price(k.meat, D15.plusDays(10), 32_000_000), priceId = "old-later").applied().copy(seq = null)
        a.receive(listOf(later))
        a.closePeriod(D31).applied()
        assertEquals(setOf(later.id.value), a.state.closedPeriods.single().seen?.get("B")?.unnumbered)
        val closed = a.calculator().cost(k.kebab, D15).foodCostPerPortion

        assertEquals(1, a.preview(listOf(earlier)).refused)
        a.receive(listOf(earlier))
        assertEquals(RejectReason.PERIOD_CLOSED, a.state.rejections.single { it.operation.id == earlier.id }.reason)
        assertEquals(0, closed.compareTo(a.calculator().cost(k.kebab, D15).foodCostPerPortion))
        assertEquals(a.state.closedPeriods.single().snapshot, MenuSnapshot.of(a.calculator().menu(D31)))
    }

    @Test
    fun `a change numbered like another of its phone is refused at the door, and inside both are refused alike`() {
        // The audits of 0.7.2 and 0.7.3 (FCA-021, FCA-026): a change already counted in a closed
        // period is never refused afterwards because another came with its number.
        val a = T.ledger("A", wall = FakeWall(1_000))
        val k = Kitchen(a)
        val b = T.ledger("B", wall = FakeWall(2_000))
        b.receive(a.operations())
        val accepted = b.recordPrice(T.price(k.meat, D15, 24_000_000), priceId = "included").applied()
        a.receive(listOf(accepted))
        a.closePeriod(D31).applied()
        val stored = a.operations().size
        val closed = a.calculator().cost(k.kebab, D31).foodCostPerPortion
        val clash = accepted.copy(
            id = OpId("other"),
            at = Hlc(accepted.at.wallMillis, accepted.at.counter + 1, "B"),
            change = Change.PriceRecorded("other", T.price(k.meat, D15, 35_000_000)),
        )

        assertEquals(1, a.preview(listOf(clash)).conflicts)
        assertEquals(1, assertFailsWith<ConflictingChanges> { a.receive(listOf(clash)) }.count)
        assertEquals(stored, a.operations().size)
        assertEquals(0, closed.compareTo(a.calculator().cost(k.kebab, D31).foodCostPerPortion))
        assertEquals(a.state.closedPeriods.single().snapshot, MenuSnapshot.of(a.calculator().menu(D31)))

        // Two such changes already inside, from a version before the door was shut, are both
        // refused, the same on every phone whatever the order.
        val inside = LedgerState.fold(a.operations() + clash)
        assertEquals(setOf(accepted.id, clash.id), inside.rejections.filter { it.reason == RejectReason.DUPLICATE }.map { it.operation.id }.toSet())
        assertEquals(inside, LedgerState.fold((a.operations() + clash).shuffled(Random(11))))
    }

    @Test
    fun `a backup that would move a closed period's figures is not imported at all`() {
        // The audit of 0.7.3 (FCA-027): a close as 0.7.2 made it trusts its own phone's unnumbered
        // changes, and a late one of them moved the closed day.
        val live = T.ledger("A")
        val k = Kitchen(live)
        val closing = live.closePeriod(D31).applied()
        val close = closing.change as Change.PeriodClosed
        val as072 = closing.copy(change = close.copy(seen = close.seen!!.mapValues { (_, s) -> s.copy(unnumbered = null) }))
        val a = T.ledger("A", store = InMemoryOperationStore(live.operations().dropLast(1) + as072))
        val s = a.state.closedPeriods.single().seen!!.getValue("A")
        val late = Operation(
            OpId("late-own"), Hlc(s.at.wallMillis, s.at.counter - 1, "A"), Actor("A-user", Role.OWNER),
            Change.PriceRecorded("late-own", T.price(k.meat, D15, 24_000_000)),
        )
        val stored = a.operations().size
        val closed = a.calculator().cost(k.kebab, D31).foodCostPerPortion

        assertEquals(1, a.preview(listOf(late)).changesClosed)
        assertEquals(1, assertFailsWith<ChangesClosedPeriod> { a.receive(listOf(late)) }.count)
        assertEquals(stored, a.operations().size)
        assertEquals(0, closed.compareTo(a.calculator().cost(k.kebab, D31).foodCostPerPortion))

        // A dish made after the close, with nothing on the closed days, or a dish renamed, moves nothing.
        val other = T.ledger("B")
        other.receive(a.operations())
        other.createRecipe("دوغ", RecipeKind.MENU_ITEM)
        other.updateRecipe(k.kebab, name = Opt.Value("کباب کوبیده"))
        assertEquals(0, a.preview(other.operations()).changesClosed)
        assertEquals(2, a.receive(other.operations()))
    }

    @Test
    fun `a gap in a phone's numbers is one range in a close, however many numbers it holds`() {
        // The audit of 0.7.2 (FCA-022): one change numbered 50,000 had made a list of 49,999 numbers.
        val a = T.ledger("A", wall = FakeWall(1_000))
        val k = Kitchen(a)
        val far = Operation(OpId("far"), Hlc(2_000, 0, "FAR"), Actor("far-user", Role.OWNER), Change.PriceRecorded("far", T.price(k.meat, N2, 20_000_000)), seq = 50_000)
        a.receive(listOf(far))
        a.closePeriod(D31).applied()
        assertEquals(listOf(1L..49_999L), a.state.closedPeriods.single().seen?.get("FAR")?.missing)
    }

    @Test
    fun `a backup with a change that has the id of one here but other content is not imported at all`() {
        // The audit of 0.7.2 (FCA-024): it had been skipped without a word.
        val a = T.ledger("A")
        val k = Kitchen(a)
        val stored = a.operations().size
        val changed = a.operations().first().copy(actor = Actor("other", Role.MANAGER))
        val fresh = Operation(OpId("new"), Hlc(9_000_000, 0, "B"), Actor("B-user", Role.OWNER), Change.PriceRecorded("new", T.price(k.rice, N2, 47_000_000)))
        assertEquals(1, a.preview(listOf(changed, fresh)).conflicts)
        assertEquals(1, assertFailsWith<ConflictingChanges> { a.receive(listOf(changed, fresh)) }.count)
        assertEquals(stored, a.operations().size) // nothing of it is taken
        // Two changes in one file with one id and different content clash too; the same change twice does not.
        assertEquals(1, a.preview(listOf(fresh, fresh.copy(reason = "دیگر"))).conflicts)
        assertEquals(0, a.preview(listOf(fresh, fresh)).conflicts)
        assertEquals(1, a.receive(listOf(fresh, fresh)))
    }

    @Test
    fun `each phone numbers its changes from 1 with none left out, and changes refused or undone take no number`() {
        val store = InMemoryOperationStore()
        val a = T.ledger("A", store = store)
        val k = Kitchen(a)
        a.closePeriod(D31).applied()
        assertTrue(a.recordPrice(T.price(k.meat, D15, 1)) is Ledger.Outcome.Rejected)
        assertFailsWith<IllegalStateException> {
            a.atomically { l ->
                l.createIngredient("نمک", Dimension.MASS)
                error("undone")
            }
        }
        a.recordPrice(T.price(k.meat, N2, 17_000_000)).applied()
        val numbers = a.operations().map { it.seq }
        assertEquals((1L..numbers.size).toList(), numbers)

        // Opened again, the phone goes on from its last number, and its own changes coming back
        // from a backup are not numbered again.
        val again = T.ledger("A", store = store)
        assertEquals(numbers.size + 1L, again.recordPrice(T.price(k.rice, N2, 47_000_000)).applied().seq)
        val restored = T.ledger("A")
        restored.receive(store.all())
        assertEquals(numbers.size + 2L, restored.recordPrice(T.price(k.meat, N2.plusDays(1), 17_500_000)).applied().seq)
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
