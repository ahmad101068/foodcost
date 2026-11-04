package ir.foodcost.data

import ir.foodcost.core.bd
import ir.foodcost.core.costing.CostingRules
import ir.foodcost.core.model.ComponentRef
import ir.foodcost.core.model.IngredientId
import ir.foodcost.core.model.PurchasePrice
import ir.foodcost.core.model.RecipeId
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.model.RecipeLine
import ir.foodcost.core.model.RecipeVersion
import ir.foodcost.core.model.YieldStage
import ir.foodcost.core.pricing.Overhead
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.core.pricing.PricingSettings
import ir.foodcost.core.units.Conversions
import ir.foodcost.core.units.Dimension
import ir.foodcost.core.units.UnitConverter
import ir.foodcost.core.units.Uom
import ir.foodcost.data.ledger.ChangesClosedPeriod
import ir.foodcost.data.ledger.ConflictingChanges
import ir.foodcost.data.ledger.Ledger
import ir.foodcost.data.ledger.LedgerState
import ir.foodcost.data.ledger.RejectReason
import ir.foodcost.data.ops.Backup
import ir.foodcost.data.ops.Change
import ir.foodcost.data.ops.Operation
import ir.foodcost.data.ops.Opt
import ir.foodcost.data.store.InMemoryOperationStore
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Random
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Random sessions on fixed seeds: changes of every kind, closes and reopenings in any order, made
 * here, sent to another phone in pieces and in any order, written to a backup and read back. After
 * every step and at the end the rules of closed periods must hold, and a failure names its seed
 * and step, so it can be run again:
 * - a closed period's figures, worked out again, are always its official ones;
 * - no change the rules about days let through ever needs the last check (no gap in the rules);
 * - a step that fails half way stores nothing;
 * - the same changes give the same state in any order, after a restart and on another phone;
 * - receiving the same changes again changes nothing, and a backup reads back whole.
 */
class ClosedPeriodPropertyTest {
    private val days: List<LocalDate> = (0L..70L).map { LocalDate.of(2026, 9, 20).plusDays(it) }

    private class Abort : RuntimeException()

    /** One phone's random session. */
    private inner class Session(val seed: Long, val device: String, val rnd: Random, store: InMemoryOperationStore = InMemoryOperationStore()) {
        val wall = FakeWall()
        val ledger: Ledger = T.ledger(device, wall = wall, store = store)
        val tally = HashMap<String, Int>()
        var step = 0
        var last = ""

        fun <T> pick(list: List<T>): T = list[rnd.nextInt(list.size)]
        fun day(): LocalDate = pick(days)
        fun dayOrNull(): LocalDate? = if (rnd.nextInt(4) == 0) null else day()
        fun amount(): Long = (5L + rnd.nextInt(400)) * 100_000L

        val ingredients: List<IngredientId> get() = ledger.state.ingredients.keys.toList()
        val recipes: List<RecipeId> get() = ledger.state.recipes.values.sortedBy { it.createdAt }.map { it.id }

        /** Ids are random: the session picks by when things were made, so a seed always makes the same session. */
        fun byAge(id: RecipeId) = ledger.state.recipes.getValue(id).createdAt

        fun units(id: IngredientId): List<Uom> {
            val ingredient = ledger.state.catalog.ingredient(id) ?: return listOf(Uom.GRAM)
            val convertible = Uom.entries.filter { it != Uom.PORTION && UnitConverter.toBase(BigDecimal.ONE, it, ingredient.baseDimension, ingredient.conversions) != null }
            // Now and then a unit that does not convert, which the costing flags.
            return if (rnd.nextInt(10) == 0) Uom.entries.filter { it != Uom.PORTION } else convertible
        }

        fun count(what: String) {
            tally[what] = (tally[what] ?: 0) + 1
        }

        /** Counts an outcome; one refused by the last check means the rules about days missed it. */
        fun outcome(name: String, out: Ledger.Outcome) {
            last = name
            when (out) {
                is Ledger.Outcome.Applied -> count("applied")
                is Ledger.Outcome.Rejected -> {
                    count("refused ${out.reason}")
                    if (out.reason == RejectReason.CLOSED_FIGURES) fail("seed $seed step $step ($name): only the last check refused it, a gap in the rules")
                }
            }
        }

        fun line(recipeIndex: Int): RecipeLine? {
            val r = rnd.nextInt(3)
            if (r == 0 && recipeIndex > 0) {
                // A recipe made before this one, so recipes never use each other in a circle.
                val sub = recipes[rnd.nextInt(recipeIndex)]
                return RecipeLine(ComponentRef.RecipeRef(sub), (1 + rnd.nextInt(3)).toBigDecimal(), Uom.PORTION)
            }
            if (ingredients.isEmpty()) return null
            val id = pick(ingredients)
            return RecipeLine(ComponentRef.IngredientRef(id), (10 + rnd.nextInt(300)).toBigDecimal(), pick(units(id)))
        }

        /** One random change, through the same calls the app makes. */
        fun act() {
            step++
            val l = ledger
            wall.now += 1 + rnd.nextInt(5_000)
            try {
                when (rnd.nextInt(100)) {
                    in 0..6 -> {
                        val d = pick(listOf(Dimension.MASS, Dimension.VOLUME, Dimension.COUNT))
                        val conversions = Conversions(
                            gramsPerPiece = if (rnd.nextBoolean()) (40 + rnd.nextInt(200)).toBigDecimal() else null,
                            gramsPerMilliliter = if (rnd.nextBoolean()) "1.0${rnd.nextInt(9)}".bd else null,
                        )
                        val yields = if (rnd.nextBoolean()) listOf(YieldStage("پاک کردن", "0.${60 + rnd.nextInt(40)}".bd)) else emptyList()
                        l.createIngredient("ماده ${ingredients.size}", d, yields = yields, conversions = conversions)
                        last = "ingredient"
                        count("applied")
                    }
                    in 7..22 -> if (ingredients.isNotEmpty()) {
                        val id = pick(ingredients)
                        outcome("price", l.recordPrice(PurchasePrice(id, day(), (1 + rnd.nextInt(5)).toBigDecimal(), pick(units(id)), amount())))
                    }
                    in 23..25 -> l.state.prices.values.filter { !it.voided }.takeIf { it.isNotEmpty() }?.let { outcome("void", l.voidPrice(pick(it).priceId)) }
                    in 26..31 -> {
                        l.createRecipe("دستور ${recipes.size}", pick(RecipeKind.entries), category = pick(listOf(null, "main", "drinks")))
                        last = "recipe"
                        count("applied")
                    }
                    in 32..46 -> if (recipes.isNotEmpty()) {
                        val index = rnd.nextInt(recipes.size)
                        val lines = (0..rnd.nextInt(3)).mapNotNull { line(index) }
                        val version = RecipeVersion(day(), portions = (1 + rnd.nextInt(4)).toBigDecimal(), batchLines = lines, miscPercent = if (rnd.nextBoolean()) "0".bd else null)
                        outcome("version", l.saveRecipeVersion(recipes[index], version))
                    }
                    in 47..49 -> l.state.versions.entries.filter { it.value.isNotEmpty() }.sortedBy { byAge(it.key) }.takeIf { it.isNotEmpty() }?.let {
                        val (id, byDay) = pick(it)
                        outcome("remove version", l.removeRecipeVersion(id, pick(byDay.keys.toList())))
                    }
                    in 50..56 -> if (recipes.isNotEmpty()) outcome("menu price", l.setMenuPrice(pick(recipes), day(), amount()))
                    in 57..58 -> l.state.menuPrices.entries.filter { it.value.isNotEmpty() }.sortedBy { byAge(it.key) }.takeIf { it.isNotEmpty() }?.let {
                        val (id, byDay) = pick(it)
                        outcome("remove menu price", l.removeMenuPrice(id, pick(byDay.keys.toList())))
                    }
                    in 59..68 -> if (recipes.isNotEmpty()) {
                        // The dish screen: name, kind and section, from today; or a call with any day or none.
                        val kind = if (rnd.nextBoolean()) Opt.Value(pick(RecipeKind.entries)) else Opt.Keep
                        val category = if (rnd.nextBoolean()) Opt.Value(pick(listOf(null, "main", "drinks", "new"))) else Opt.Keep
                        val pricing = if (rnd.nextInt(4) == 0) Opt.Value(pick(listOf(null, PricingMethod.Markup("150".bd)))) else Opt.Keep
                        outcome("recipe header", l.updateRecipe(pick(recipes), kind = kind, category = category, pricing = pricing, effectiveFrom = dayOrNull()))
                    }
                    in 69..75 -> if (ingredients.isNotEmpty()) {
                        val yields = if (rnd.nextBoolean()) Opt.Value(listOf(YieldStage("پاک کردن", "0.${50 + rnd.nextInt(50)}".bd))) else Opt.Keep
                        val conversions = if (rnd.nextBoolean()) Opt.Value(Conversions(gramsPerPiece = (30 + rnd.nextInt(300)).toBigDecimal())) else Opt.Keep
                        val name = if (rnd.nextInt(3) == 0) Opt.Value("نام تازه ${rnd.nextInt(99)}") else Opt.Keep
                        val archived = if (rnd.nextInt(6) == 0) Opt.Value(rnd.nextBoolean()) else Opt.Keep
                        outcome("ingredient edit", l.updateIngredient(pick(ingredients), name = name, yields = yields, conversions = conversions, archived = archived, effectiveFrom = dayOrNull()))
                    }
                    in 76..77 -> if (ingredients.isNotEmpty()) {
                        outcome("base dimension", l.execute(Change.IngredientSaved(pick(ingredients), baseDimension = Opt.Value(pick(listOf(Dimension.MASS, Dimension.VOLUME, Dimension.COUNT))), effectiveFrom = dayOrNull())))
                    }
                    in 78..80 -> {
                        val costing = if (rnd.nextBoolean()) CostingRules(rnd.nextInt(10).toBigDecimal(), mapOf("drinks" to "0".bd)) else null
                        val pricing = if (rnd.nextBoolean()) PricingSettings(defaultMethod = PricingMethod.TargetFoodCost((25 + rnd.nextInt(15)).toBigDecimal()), overhead = if (rnd.nextBoolean()) Overhead(300_000_000, "3000".bd) else null) else null
                        if (costing != null || pricing != null) outcome("settings", l.saveSettings(costing, pricing, dayOrNull()))
                    }
                    in 81..86 -> outcome("close", l.closePeriod(day()))
                    in 87..88 -> outcome("reopen", l.reopenPeriod(day(), "دلیل آزمون"))
                    in 89..91 -> if (recipes.isNotEmpty()) {
                        // As a phone on 0.7.4 recorded it: the kind on every day, whatever the day.
                        outcome("kind of 0.7.4", l.execute(Change.RecipeSaved(pick(recipes), kind = Opt.Value(pick(RecipeKind.entries)), effectiveFrom = dayOrNull(), kindDated = false)))
                    }
                    in 92..93 -> if (recipes.isNotEmpty()) outcome("archive", l.updateRecipe(pick(recipes), archived = Opt.Value(rnd.nextBoolean())))
                    else -> if (ingredients.isNotEmpty() && recipes.isNotEmpty()) {
                        // Several steps as one change that fails at the end: nothing of it stays.
                        val before = l.state
                        val stored = l.operations()
                        try {
                            l.atomically {
                                it.recordPrice(PurchasePrice(pick(ingredients), day(), BigDecimal.ONE, Uom.KILOGRAM, amount()))
                                it.setMenuPrice(pick(recipes), day(), amount())
                                if (rnd.nextBoolean()) it.closePeriod(day())
                                throw Abort()
                            }
                        } catch (e: Abort) {
                            // expected
                        }
                        if (l.state != before || l.operations() != stored) fail("seed $seed step $step: a change that failed half way left something behind")
                        last = "failed batch"
                        count("rolled back")
                    }
                }
            } catch (e: IllegalArgumentException) {
                count("not accepted by the call") // the app's own checks, such as a recipe line of an unknown component
            }
            checkClosed("after ${this.last}")
        }

        fun checkClosed(what: String) {
            val bad = ledger.state.closedMismatches
            if (bad.isNotEmpty()) fail("seed $seed step $step $what: closed through ${bad.map { it.through }} no longer adds up to the record")
        }
    }

    /**
     * Sends [ops] to [to] in random pieces in a random order, sending again what was refused, as a
     * phone that gets them late would. When no piece goes in alone, as when two closes each need
     * a change the other piece has, the rest is sent as one file, which must go in.
     */
    private fun sendInPieces(ops: List<Operation>, to: Ledger, rnd: Random, seed: Long): Int {
        val pending = ArrayDeque(ops.shuffled(rnd).chunked(1 + rnd.nextInt(8)))
        var refusedInARow = 0
        var refused = 0
        while (pending.isNotEmpty()) {
            if (refusedInARow > pending.size) {
                val rest = pending.flatten()
                pending.clear()
                pending.addLast(rest)
                refusedInARow = 0
            }
            val piece = pending.removeFirst()
            val before = to.state
            val stored = to.operations()
            try {
                to.receive(piece)
                refusedInARow = 0
                if (to.state.closedMismatches.isNotEmpty()) fail("seed $seed: a piece made a closed period stop adding up")
            } catch (e: ChangesClosedPeriod) {
                // A close came before what it had seen: refused whole, and sent again later.
                if (to.state != before || to.operations() != stored) fail("seed $seed: a refused piece left something behind")
                if (pending.isEmpty()) fail("seed $seed: the whole rest of a ledger's changes was refused: ${e.message}")
                pending.addLast(piece)
                refusedInARow++
                refused++
            }
        }
        return refused
    }

    @Test
    fun `random sessions on one phone keep every closed period as its close kept it`() {
        val total = HashMap<String, Int>()
        for (seed in 1L..120L) {
            val rnd = Random(seed)
            val s = Session(seed, "A", rnd)
            repeat(70) { s.act() }
            val ops = s.ledger.operations()

            // The same changes in any order, after a restart, read from a backup.
            assertEquals(s.ledger.state, LedgerState.fold(ops.shuffled(rnd)), "seed $seed: another order")
            assertEquals(s.ledger.state, T.ledger("A", store = InMemoryOperationStore(ops)).state, "seed $seed: restart")
            assertEquals(ops, Backup.read(Backup.write(ops)), "seed $seed: backup")
            // Numbered from 1 with none left out, refused and failed changes included.
            assertEquals((1L..ops.size).toList(), ops.map { it.seq }, "seed $seed: numbers")

            // Another phone that gets them in pieces, in any order, ends with the same data.
            val other = T.ledger("B")
            assertEquals(0, other.preview(ops).changesClosed, "seed $seed: preview")
            s.tally["pieces refused and sent again"] = sendInPieces(ops, other, rnd, seed)
            assertEquals(s.ledger.state, other.state, "seed $seed: other phone")
            assertEquals(0, other.receive(ops), "seed $seed: again")
            s.tally.forEach { (k, v) -> total[k] = (total[k] ?: 0) + v }
        }
        println("    one phone, seeds 1..120, 70 steps each: ${total.toSortedMap()}")
    }

    @Test
    fun `two phones changing at once never move a closed period, and agree once each has the other's changes`() {
        val total = HashMap<String, Int>()
        var agreed = 0
        var refused = 0
        for (seed in 1001L..1060L) {
            val rnd = Random(seed)
            val a = Session(seed, "A", rnd)
            repeat(30) { a.act() }
            // The second phone starts from a backup of the first, then both go on alone.
            val b = Session(seed, "B", rnd, InMemoryOperationStore(a.ledger.operations()))
            b.wall.now = a.wall.now
            repeat(30) {
                a.act()
                b.act()
            }
            fun exchange(from: Session, to: Session): Boolean {
                val before = to.ledger.state
                val stored = to.ledger.operations()
                return try {
                    to.ledger.receive(from.ledger.operations())
                    to.checkClosed("after receiving from the other phone")
                    true
                } catch (e: ChangesClosedPeriod) {
                    // Both closed days the other had not seen all of: kept apart until one reopens.
                    if (to.ledger.state != before || to.ledger.operations() != stored) fail("seed $seed: a refused import left something behind")
                    val periodChanges = from.ledger.operations().count {
                        it.at.device == from.device && (it.change is Change.PeriodClosed || it.change is Change.PeriodReopened)
                    }
                    if (to.ledger.state.closedPeriods.isEmpty() || periodChanges == 0) {
                        fail("seed $seed: an import was refused though not both phones closed or reopened a period")
                    }
                    false
                } catch (e: ConflictingChanges) {
                    fail("seed $seed: two phones never number alike: $e")
                }
            }
            val toB = exchange(a, b)
            val toA = exchange(b, a)
            if (toA && toB) {
                assertEquals(a.ledger.state, b.ledger.state, "seed $seed: the phones agree")
                agreed++
            } else {
                refused++
            }
            listOf(a, b).forEach { s -> s.tally.forEach { (k, v) -> total[k] = (total[k] ?: 0) + v } }
        }
        println("    two phones, seeds 1001..1060: agreed $agreed, kept apart by their closes $refused; ${total.toSortedMap()}")
    }
}
