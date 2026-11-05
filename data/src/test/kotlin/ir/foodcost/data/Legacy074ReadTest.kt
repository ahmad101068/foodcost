package ir.foodcost.data

import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.reports.MenuSnapshot
import ir.foodcost.data.clock.HybridClock
import ir.foodcost.data.json.Json
import ir.foodcost.data.json.JsonObject
import ir.foodcost.data.ledger.Ledger
import ir.foodcost.data.ops.Actor
import ir.foodcost.data.ops.Backup
import ir.foodcost.data.ops.Change
import ir.foodcost.data.ops.Operation
import ir.foodcost.data.ops.OperationCodec
import ir.foodcost.data.ops.Role
import ir.foodcost.data.store.InMemoryOperationStore
import java.time.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Data that 0.7.4 itself stored ([Legacy074], made with its own code) reads in this version with
 * the figures 0.7.4 worked out, on every day, its closed October included; a phone carries on from
 * it, and its backup stays one that 0.7.4 reads.
 */
class Legacy074ReadTest {
    private val operations: List<Operation> = Legacy074.operations.map { OperationCodec.decode(Json.parse(it) as JsonObject) }

    private fun ledger(store: InMemoryOperationStore) = Ledger(store, HybridClock("phone", FakeWall(1_800_000_000_000L)), Actor("owner", Role.OWNER))

    private fun figures(ledger: Ledger, day: String): List<String> =
        MenuSnapshot.of(ledger.calculator().menu(LocalDate.parse(day))).items.sortedBy { it.recipeId.value }.map {
            "${it.recipeId.value}|${it.name}|${it.foodCostRials}|${it.menuPriceRials}|${it.suggestedPriceRials}|${it.foodCostPercent?.stripTrailingZeros()?.toPlainString()}|${it.status}"
        }

    @Test
    fun `what 0 7 4 stored reads with the figures 0 7 4 worked out, on every day`() {
        val l = ledger(InMemoryOperationStore(operations))
        for ((day, expected) in Legacy074.figures) assertEquals(expected, figures(l, day), day)
        assertEquals(emptyList(), l.state.rejections)
        assertEquals(LocalDate.parse(Legacy074.CLOSED_THROUGH), l.state.closedPeriods.single().through)
        assertEquals(emptyList(), l.state.closedMismatches)
        // Its kind changes keep the meaning they had: on every day, so the salad made a preparation
        // is off the menu of the days before too, as 0.7.4 counted it and its close kept it.
        val saved = operations.mapNotNull { it.change as? Change.RecipeSaved }
        assertTrue(saved.none { it.kindDated })
        assertTrue(saved.any { it.kind == ir.foodcost.data.ops.Opt.Value(RecipeKind.PREP) && it.effectiveFrom != null })
    }

    @Test
    fun `a phone carries on from data of 0 7 4, and its backup stays one 0 7 4 reads`() {
        val store = InMemoryOperationStore(operations)
        val l = ledger(store)
        val last = operations.maxOf { checkNotNull(it.seq) }
        val next = l.recordPrice(T.price(l.state.ingredients.keys.first(), LocalDate.of(2026, 11, 20), 21_000_000)).applied()
        assertEquals(last + 1, next.seq)
        // The days before the new price keep the figures of 0.7.4.
        for ((day, expected) in Legacy074.figures) assertEquals(expected, figures(l, day), day)
        // The same after the app opens again.
        val again = ledger(store)
        assertEquals(l.state, again.state)
        // Nothing in it needs version 6, so the file is one 0.7.4 reads, with the same meaning.
        val version = (Json.parse(again.exportBackup()) as JsonObject).int("version")
        assertTrue(version <= 5, "version $version")
        assertEquals(again.operations(), Backup.read(again.exportBackup()))
    }
}
