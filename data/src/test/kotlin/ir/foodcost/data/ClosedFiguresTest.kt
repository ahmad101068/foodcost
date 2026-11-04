package ir.foodcost.data

import ir.foodcost.core.bd
import ir.foodcost.core.costing.CostingRules
import ir.foodcost.core.model.IngredientId
import ir.foodcost.core.model.RecipeId
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.model.RecipeVersion
import ir.foodcost.core.model.YieldStage
import ir.foodcost.core.pricing.PriceStatus
import ir.foodcost.core.reports.MenuSnapshot
import ir.foodcost.core.units.Dimension
import ir.foodcost.data.clock.Hlc
import ir.foodcost.data.ledger.Ledger
import ir.foodcost.data.ledger.LedgerState
import ir.foodcost.data.ledger.RejectReason
import ir.foodcost.data.ops.Actor
import ir.foodcost.data.ops.Change
import ir.foodcost.data.ops.OpId
import ir.foodcost.data.ops.Operation
import ir.foodcost.data.ops.Opt
import ir.foodcost.data.ops.Role
import ir.foodcost.data.store.InMemoryOperationStore
import java.time.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The audit of 0.7.4 (FCA-030, FCA-031) and what follows from it: no change, made here or brought
 * by a backup, moves the figures of a closed period, and a backup of such a ledger restores.
 * These tests use only what 0.7.4 already had, so they ran against it before it was fixed.
 */
class ClosedFiguresTest {
    /** A day's menu figures as they are worked out now, in the form a close keeps them. */
    private fun live(ledger: Ledger, day: LocalDate = T.D31) = figures(MenuSnapshot.of(ledger.calculator().menu(day)))

    /** The official figures the only close kept. */
    private fun official(ledger: Ledger) = figures(checkNotNull(ledger.state.closedPeriods.single().snapshot))

    private fun figures(s: MenuSnapshot) = s.items.filter { it.status != PriceStatus.EMPTY }.associate {
        it.recipeId to listOf(it.foodCostRials, it.menuPriceRials, it.suggestedPriceRials, it.foodCostPercent?.stripTrailingZeros()?.toPlainString(), it.status)
    }

    /** A kitchen whose October is closed. */
    private fun closed(device: String = "A"): Kitchen = Kitchen(T.ledger(device)).also { it.ledger.closePeriod(T.D31).applied() }

    @Test
    fun `FCA-030 a dish made a preparation from a day after the close stays on the closed menu`() {
        val k = closed()
        // The way the dish's screen saves it: the new kind counts from today.
        k.ledger.updateRecipe(k.kebab, kind = Opt.Value(RecipeKind.PREP), effectiveFrom = T.N2).applied()
        assertEquals(official(k.ledger), live(k.ledger))
        assertEquals(1, live(k.ledger).size)
        // From its day on it is a preparation, off the menu.
        assertTrue(k.ledger.calculator().menu(T.N2).items.none { it.recipe.id == k.kebab })
        assertTrue(k.ledger.calculator().menu(T.D31).items.any { it.recipe.id == k.kebab })
    }

    @Test
    fun `FCA-030 a kind changed on every day, or on a closed day, is refused after the close`() {
        val k = closed()
        assertEquals(RejectReason.PERIOD_CLOSED, k.ledger.updateRecipe(k.kebab, kind = Opt.Value(RecipeKind.PREP)).rejection())
        assertEquals(RejectReason.PERIOD_CLOSED, k.ledger.updateRecipe(k.kebab, kind = Opt.Value(RecipeKind.PREP), effectiveFrom = T.D15).rejection())
        assertEquals(official(k.ledger), live(k.ledger))
        assertEquals(RecipeKind.MENU_ITEM, k.ledger.state.recipes.getValue(k.kebab).kind)
    }

    @Test
    fun `FCA-030 a preparation made a menu item after the close does not join the closed menu`() {
        val k = closed()
        k.ledger.updateRecipe(k.chelow, kind = Opt.Value(RecipeKind.MENU_ITEM), effectiveFrom = T.N2).applied()
        assertEquals(official(k.ledger), live(k.ledger))
        assertTrue(k.ledger.calculator().menu(T.N2).items.any { it.recipe.id == k.chelow })
    }

    @Test
    fun `FCA-030 the backup of a ledger whose dish changed kind after the close restores on another phone`() {
        val k = closed()
        k.ledger.updateRecipe(k.kebab, kind = Opt.Value(RecipeKind.PREP), effectiveFrom = T.N2).applied()
        val other = T.ledger("B")
        val preview = other.preview(k.ledger.operations())
        assertEquals(0, preview.changesClosed)
        assertEquals(0, preview.refused)
        assertEquals(k.ledger.operations().size, other.receive(k.ledger.operations()))
        assertEquals(official(other), live(other))
        assertEquals(k.ledger.state.recipes, other.state.recipes)
    }

    @Test
    fun `FCA-031 a base unit changed after the close is refused and the closed cost stays`() {
        val k = closed()
        val before = k.ledger.calculator().cost(k.kebab, T.D31).foodCostPerPortion
        for (from in listOf(null, T.D15, T.N2)) {
            val out = k.ledger.execute(Change.IngredientSaved(k.meat, baseDimension = Opt.Value(Dimension.COUNT), effectiveFrom = from))
            assertTrue(out is Ledger.Outcome.Rejected, "a base unit change from $from was applied")
        }
        assertEquals(before, k.ledger.calculator().cost(k.kebab, T.D31).foodCostPerPortion)
        assertEquals(official(k.ledger), live(k.ledger))
        assertEquals(Dimension.MASS, k.ledger.state.ingredients.getValue(k.meat).baseDimension)
    }

    @Test
    fun `every change the audit of 0 7 4 tried after a close leaves the closed figures as they were`() {
        // tests/AuditMutations.kt of the audit, as a test: accepted or refused, the closed day's
        // figures must stay the official ones.
        val changes: List<Pair<String, (Kitchen) -> Ledger.Outcome>> = listOf(
            "recipe_name" to { k -> k.ledger.updateRecipe(k.kebab, name = Opt.Value("کباب جدید"), effectiveFrom = T.N2) },
            "recipe_kind_to_prep_ui" to { k -> k.ledger.updateRecipe(k.kebab, kind = Opt.Value(RecipeKind.PREP), effectiveFrom = T.N2) },
            "recipe_kind_to_prep_undated" to { k -> k.ledger.updateRecipe(k.kebab, kind = Opt.Value(RecipeKind.PREP)) },
            "recipe_category_future" to { k -> k.ledger.updateRecipe(k.kebab, category = Opt.Value("new"), effectiveFrom = T.N2) },
            "recipe_category_undated" to { k -> k.ledger.updateRecipe(k.kebab, category = Opt.Value("new")) },
            "recipe_archived" to { k -> k.ledger.updateRecipe(k.kebab, archived = Opt.Value(true)) },
            "ingredient_name" to { k -> k.ledger.updateIngredient(k.meat, name = Opt.Value("چرخ‌کردهٔ تازه")) },
            "ingredient_archived" to { k -> k.ledger.updateIngredient(k.meat, archived = Opt.Value(true)) },
            "ingredient_yield_future" to { k -> k.ledger.updateIngredient(k.meat, yields = Opt.Value(listOf(YieldStage("پاک کردن", "0.8".bd))), effectiveFrom = T.N2) },
            "ingredient_yield_undated" to { k -> k.ledger.updateIngredient(k.meat, yields = Opt.Value(listOf(YieldStage("پاک کردن", "0.8".bd)))) },
            "ingredient_base_dimension_direct" to { k -> k.ledger.execute(Change.IngredientSaved(k.meat, baseDimension = Opt.Value(Dimension.COUNT))) },
            "purchase_price_future" to { k -> k.ledger.recordPrice(T.price(k.meat, T.N2, 30_000_000)) },
            "purchase_price_closed" to { k -> k.ledger.recordPrice(T.price(k.meat, T.D15, 30_000_000)) },
            "menu_price_future" to { k -> k.ledger.setMenuPrice(k.kebab, T.N2, 15_000_000) },
            "menu_price_closed" to { k -> k.ledger.setMenuPrice(k.kebab, T.D15, 15_000_000) },
            "recipe_version_future" to { k -> k.ledger.saveRecipeVersion(k.kebab, RecipeVersion(T.N2, batchLines = listOf(T.line(k.meat, "300")))) },
            "recipe_version_closed" to { k -> k.ledger.saveRecipeVersion(k.kebab, RecipeVersion(T.D15, batchLines = listOf(T.line(k.meat, "300")))) },
            "void_old_price" to { k -> k.ledger.voidPrice(k.ledger.state.prices.values.first { it.price.ingredientId == k.meat }.priceId) },
            "settings_future" to { k -> k.ledger.saveSettings(costing = CostingRules("9".bd), effectiveFrom = T.N2) },
            "settings_undated" to { k -> k.ledger.saveSettings(costing = CostingRules("9".bd), effectiveFrom = null) },
        )
        val broken = changes.mapNotNull { (name, change) ->
            val k = closed()
            val out = change(k)
            name.takeIf { official(k.ledger) != live(k.ledger) }?.let { "$name (${if (out is Ledger.Outcome.Applied) "applied" else "refused"})" }
        }
        assertEquals(emptyList(), broken)
    }

    @Test
    fun `a backup that brings back a change the close had, which this phone could no longer read, is taken`() {
        // A price for a closed day, recorded before the close: the close had it.
        val k = Kitchen(T.ledger("A"))
        val lost = k.ledger.recordPrice(T.price(k.meat, T.D15, 20_000_000)).applied()
        k.ledger.closePeriod(T.D31).applied()
        val all = k.ledger.operations()

        // The same phone after its row of that price became unreadable: the store reads without it.
        val damaged = T.ledger("A", store = InMemoryOperationStore().apply { appendAll(all - lost) })
        assertNotEquals(official(damaged), live(damaged))

        // Its backup brings the price back, which puts the closed figures back as the close kept them.
        assertEquals(0, damaged.preview(all).changesClosed)
        assertEquals(1, damaged.receive(all))
        assertEquals(official(damaged), live(damaged))
        assertEquals(k.ledger.state, damaged.state)
    }

    @Test
    fun `a kind changed on every day by a phone still on 0 7 4, after the close, is refused on arrival`() {
        val k = closed()
        // 0.7.4 recorded the dish screen's change with its day, but counted the kind on every day.
        val close = k.ledger.operations().last()
        val fromOld = Operation(
            OpId("from-0.7.4"), Hlc(close.at.wallMillis + 1, 0, "C"), Actor("C-user", Role.OWNER),
            Change.RecipeSaved(k.kebab, kind = Opt.Value(RecipeKind.PREP), effectiveFrom = T.N2, kindDated = false),
            seq = 1,
        )
        val batch = k.ledger.operations() + fromOld
        val preview = k.ledger.preview(batch)
        assertEquals(1, preview.fresh)
        assertEquals(1, preview.refused)
        assertEquals(0, preview.changesClosed)
        assertEquals(1, k.ledger.receive(batch))
        assertEquals(RejectReason.PERIOD_CLOSED, k.ledger.state.rejections.single().reason)
        assertEquals(official(k.ledger), live(k.ledger))
        assertEquals(RecipeKind.MENU_ITEM, k.ledger.state.recipes.getValue(k.kebab).kind)
    }

    @Test
    fun `a kind changed before 0 7 5 counts on every day, as it did, so the closes of then still add up`() {
        // A dish made a preparation on the 2nd of November, then October closed: 0.7.4 counted the
        // kind on every day, the 31st of October too, and the close kept that.
        val k = Kitchen(T.ledger("A"))
        k.ledger.execute(Change.RecipeSaved(k.kebab, kind = Opt.Value(RecipeKind.PREP), effectiveFrom = T.N2, kindDated = false)).applied()
        assertTrue(k.ledger.calculator().menu(T.D1).items.none { it.recipe.id == k.kebab })
        k.ledger.closePeriod(T.D31).applied()
        assertEquals(official(k.ledger), live(k.ledger))
        assertEquals(emptyList(), k.ledger.state.closedMismatches)
        val other = T.ledger("B")
        assertEquals(k.ledger.operations().size, other.receive(k.ledger.operations()))
        assertEquals(official(other), live(other))
    }

    @Test
    fun `data of 0 7 4 with a kind changed after a close reads with the closed figures put back`() {
        // What FCA-030 left in a phone's table: the close, then the dish's kind changed on every day.
        val k = closed()
        val ops = k.ledger.operations()
        val close = ops.last()
        val late = Operation(
            OpId("late-kind"), Hlc(close.at.wallMillis, close.at.counter + 1, "A"), Actor("A-user", Role.OWNER),
            Change.RecipeSaved(k.kebab, kind = Opt.Value(RecipeKind.PREP), effectiveFrom = T.N2, kindDated = false),
            seq = checkNotNull(close.seq) + 1,
        )
        val read = T.ledger("A", store = InMemoryOperationStore(ops + late))
        assertEquals(official(read), live(read))
        assertEquals(emptyList(), read.state.closedMismatches)
        assertEquals(late, read.state.rejections.single().operation)
        assertEquals(RejectReason.PERIOD_CLOSED, read.state.rejections.single().reason)
        // Its backup restores on another phone, the refused change kept and still refused.
        val other = T.ledger("B")
        assertEquals(ops.size + 1, other.receive(read.operations()))
        assertEquals(read.state, other.state)
        // The next change here takes the next number.
        assertEquals(checkNotNull(late.seq) + 1, read.recordPrice(T.price(k.meat, T.N2, 17_000_000)).applied().seq)
    }

    @Test
    fun `reopening lets a kind change count on the reopened days, and closing again keeps the new figures`() {
        val k = closed()
        assertEquals(RejectReason.PERIOD_CLOSED, k.ledger.updateRecipe(k.kebab, kind = Opt.Value(RecipeKind.PREP), effectiveFrom = T.D15).rejection())
        k.ledger.reopenPeriod(T.D1, "از نیمهٔ ماه این غذا فقط داخل غذای دیگر رفت").applied()
        k.ledger.updateRecipe(k.kebab, kind = Opt.Value(RecipeKind.PREP), effectiveFrom = T.D15).applied()
        k.ledger.closePeriod(T.D31).applied()
        assertEquals(emptyMap(), live(k.ledger))
        assertEquals(official(k.ledger), live(k.ledger))
        assertTrue(k.ledger.calculator().menu(T.D1).items.any { it.recipe.id == k.kebab })
    }

    @Test
    fun `a preparation's kind changing leaves the cost of the dish that uses it`() {
        val k = Kitchen(T.ledger("A"))
        val before = k.ledger.calculator().cost(k.kebab, T.N2).foodCostPerPortion
        k.ledger.updateRecipe(k.chelow, kind = Opt.Value(RecipeKind.MENU_ITEM), effectiveFrom = T.N2).applied()
        assertEquals(before, k.ledger.calculator().cost(k.kebab, T.N2).foodCostPerPortion)
        assertEquals(before, k.ledger.calculator().cost(k.kebab, T.D31).foodCostPerPortion)
    }

    @Test
    fun `dated kinds read back the same after a restart`() {
        val store = InMemoryOperationStore()
        val k = Kitchen(T.ledger("A", store = store))
        k.ledger.closePeriod(T.D31).applied()
        k.ledger.updateRecipe(k.kebab, kind = Opt.Value(RecipeKind.PREP), effectiveFrom = T.N2).applied()
        k.ledger.updateRecipe(k.chelow, kind = Opt.Value(RecipeKind.MENU_ITEM), effectiveFrom = T.N2).applied()
        k.ledger.updateRecipe(k.chelow, kind = Opt.Value(RecipeKind.PREP), effectiveFrom = T.N2.plusDays(3)).applied()
        val again = T.ledger("A", store = store)
        assertEquals(k.ledger.state, again.state)
        assertEquals(official(again), live(again))
    }

    @Test
    fun `an ingredient's base dimension is set once, closed period or not`() {
        val k = Kitchen(T.ledger("A"))
        assertEquals(RejectReason.INVALID, k.ledger.execute(Change.IngredientSaved(k.meat, baseDimension = Opt.Value(Dimension.COUNT))).rejection())
        assertEquals(RejectReason.INVALID, k.ledger.execute(Change.IngredientSaved(k.meat, baseDimension = Opt.Value(Dimension.VOLUME), effectiveFrom = T.N2)).rejection())
        // The one it has changes nothing and is taken.
        k.ledger.execute(Change.IngredientSaved(k.meat, baseDimension = Opt.Value(Dimension.MASS))).applied()
        assertEquals(Dimension.MASS, k.ledger.state.ingredients.getValue(k.meat).baseDimension)
    }

    @Test
    fun `a base dimension given to an ingredient that had none counts on every day, so a close refuses it`() {
        // Only a file written by hand has an ingredient without one.
        val l = T.ledger("A")
        val salt = IngredientId("salt")
        l.execute(Change.IngredientSaved(salt, name = Opt.Value("نمک"))).applied()
        l.closePeriod(T.D31).applied()
        assertEquals(RejectReason.PERIOD_CLOSED, l.execute(Change.IngredientSaved(salt, baseDimension = Opt.Value(Dimension.MASS))).rejection())
        l.reopenPeriod(T.D1, "نمک بی‌واحد ثبت شده بود").applied()
        l.execute(Change.IngredientSaved(salt, baseDimension = Opt.Value(Dimension.MASS))).applied()
    }

    @Test
    fun `a close whose record is not what its day adds up to is refused`() {
        val k = Kitchen(T.ledger("A"))
        val real = MenuSnapshot.of(k.ledger.calculator().menu(T.D31))
        val wrong = real.copy(items = real.items.map { it.copy(foodCostRials = it.foodCostRials + 1) })
        assertEquals(RejectReason.CLOSED_FIGURES, k.ledger.execute(Change.PeriodClosed(T.D31, wrong)).rejection())
        assertTrue(k.ledger.state.closedPeriods.isEmpty())
        // Refused, it took no number: the next change has the one it would have had.
        val next = k.ledger.execute(Change.PeriodClosed(T.D31, real)).applied()
        assertEquals(k.ledger.operations().size.toLong(), next.seq)
    }

    @Test
    fun `a closed period whose figures are not its record is reported, changes elsewhere go on, and it is put right by closing again`() {
        // A close kept with a record that does not add up: damaged, or written by hand.
        val k = Kitchen(T.ledger("A"))
        val real = MenuSnapshot.of(k.ledger.calculator().menu(T.D31))
        val wrong = real.copy(items = real.items.map { it.copy(foodCostRials = it.foodCostRials + 1) })
        val ops = k.ledger.operations()
        val last = ops.last()
        val close = Operation(
            OpId("bad-close"), Hlc(last.at.wallMillis, last.at.counter + 1, "A"), Actor("A-user", Role.OWNER),
            Change.PeriodClosed(T.D31, wrong), seq = checkNotNull(last.seq) + 1,
        )
        val l = T.ledger("A", store = InMemoryOperationStore(ops + close))
        assertEquals(listOf(T.D31), l.state.closedMismatches.map { it.through })
        // Its figures stay as they are: a change for a later day is taken.
        l.recordPrice(T.price(k.meat, T.N2, 20_000_000)).applied()
        // A backup of it cannot start another phone's data with a close that does not add up.
        val other = T.ledger("B")
        assertEquals(1, other.preview(l.operations()).changesClosed)
        // Reopened and closed again, the record is the figures.
        l.reopenPeriod(T.D1, "رقم رسمی با حساب جور نبود").applied()
        l.closePeriod(T.D31).applied()
        assertEquals(emptyList(), l.state.closedMismatches)
        assertEquals(l.operations().size, other.receive(l.operations()))
        assertEquals(emptyList(), other.state.closedMismatches)
    }

    @Test
    fun `a kind given to a recipe that had none counts on every day, so a close refuses it`() {
        // Only a file written by hand has a recipe without a kind; its first kind has always been.
        val k = Kitchen(T.ledger("A"))
        val dish = RecipeId("no-kind")
        k.ledger.execute(Change.RecipeSaved(dish, name = Opt.Value("بی‌نوع"))).applied()
        k.ledger.saveRecipeVersion(dish, RecipeVersion(T.D1, batchLines = listOf(T.line(k.meat, "100")))).applied()
        k.ledger.closePeriod(T.D31).applied()
        assertEquals(RejectReason.PERIOD_CLOSED, k.ledger.updateRecipe(dish, kind = Opt.Value(RecipeKind.MENU_ITEM), effectiveFrom = T.N2).rejection())
        assertEquals(official(k.ledger), live(k.ledger))
    }

    @Test
    fun `changes made at once from many threads are each stored once, numbered without gaps, and add up as one history`() {
        val k = Kitchen(T.ledger("A"))
        k.ledger.closePeriod(T.D31).applied()
        val threads = (1..8).map { t ->
            Thread {
                repeat(40) { i ->
                    // Half for open days, half for closed ones, which are refused.
                    val day = if (i % 2 == 0) T.N2.plusDays(i.toLong()) else T.D15
                    k.ledger.recordPrice(T.price(k.meat, day, 16_000_000L + t * 1_000 + i))
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        val ops = k.ledger.operations()
        assertEquals(ops.size, ops.map { it.id }.toSet().size)
        assertEquals((1L..ops.size).toList(), ops.map { it.seq })
        assertEquals(k.ledger.state, LedgerState.fold(ops))
        assertEquals(official(k.ledger), live(k.ledger))
        assertEquals(9 + 1 + 8 * 20, ops.size) // the kitchen's nine, the close, and the prices for open days
    }
}
