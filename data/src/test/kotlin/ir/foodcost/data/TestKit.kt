package ir.foodcost.data

import ir.foodcost.core.bd
import ir.foodcost.core.model.ComponentRef
import ir.foodcost.core.model.IngredientId
import ir.foodcost.core.model.PurchasePrice
import ir.foodcost.core.model.RecipeId
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.model.RecipeLine
import ir.foodcost.core.model.RecipeVersion
import ir.foodcost.core.model.YieldStage
import ir.foodcost.core.units.Dimension
import ir.foodcost.core.units.Uom
import ir.foodcost.data.clock.HybridClock
import ir.foodcost.data.ledger.Ledger
import ir.foodcost.data.ops.Actor
import ir.foodcost.data.ops.Operation
import ir.foodcost.data.ops.Role
import ir.foodcost.data.store.InMemoryOperationStore
import ir.foodcost.data.store.OperationStore
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

/** A wall clock the test moves by hand. */
class FakeWall(var now: Long = 1_000_000L) : () -> Long {
    override fun invoke(): Long = now
}

object T {
    val D1: LocalDate = LocalDate.of(2026, 10, 1)
    val D15: LocalDate = LocalDate.of(2026, 10, 15)
    val D31: LocalDate = LocalDate.of(2026, 10, 31)
    val N2: LocalDate = LocalDate.of(2026, 11, 2)

    fun ledger(
        device: String,
        role: Role = Role.OWNER,
        wall: FakeWall = FakeWall(),
        store: OperationStore = InMemoryOperationStore(),
    ) = Ledger(store, HybridClock(device, wall), Actor("$device-user", role))

    fun line(id: IngredientId, qty: String) = RecipeLine(ComponentRef.IngredientRef(id), qty.bd, Uom.GRAM)

    fun sub(id: RecipeId, portions: String) = RecipeLine(ComponentRef.RecipeRef(id), portions.bd, Uom.PORTION)

    fun price(id: IngredientId, from: LocalDate, rialsPerKilo: Long) = PurchasePrice(id, from, "1".bd, Uom.KILOGRAM, rialsPerKilo)
}

/**
 * A small kitchen entered through the ledger: rice and minced meat, plain rice as a preparation,
 * and a koobideh plate on the menu at 13,500,000 rials.
 */
class Kitchen(val ledger: Ledger) {
    val rice: IngredientId = ledger.createIngredient("برنج طارم", Dimension.MASS, yields = listOf(YieldStage("شستن", "0.98".bd)))
    val meat: IngredientId = ledger.createIngredient("گوشت چرخ‌کرده", Dimension.MASS)
    val chelow: RecipeId = ledger.createRecipe("چلو", RecipeKind.PREP)
    val kebab: RecipeId = ledger.createRecipe("چلوکباب کوبیده", RecipeKind.MENU_ITEM, category = "main")

    init {
        ledger.recordPrice(PurchasePrice(rice, T.D1, "10".bd, Uom.KILOGRAM, 46_000_000)).applied()
        ledger.recordPrice(T.price(meat, T.D1, 16_000_000)).applied()
        ledger.saveRecipeVersion(chelow, RecipeVersion(T.D1, batchLines = listOf(T.line(rice, "150")), miscPercent = "0".bd)).applied()
        ledger.saveRecipeVersion(
            kebab,
            RecipeVersion(T.D1, batchLines = listOf(T.line(meat, "220"), T.sub(chelow, "1")), miscPercent = "0".bd),
        ).applied()
        ledger.setMenuPrice(kebab, T.D1, 13_500_000).applied()
    }
}

fun Ledger.Outcome.applied(): Operation =
    (this as? Ledger.Outcome.Applied ?: fail("Expected the change to be applied, but: $this")).operation

fun Ledger.Outcome.rejection() =
    (this as? Ledger.Outcome.Rejected ?: fail("Expected the change to be rejected, but it was applied")).reason

fun assertAmount(expected: String, actual: BigDecimal?, scale: Int = 4) {
    val value = assertNotNull(actual, "expected $expected, got null")
    assertEquals(expected.bd.setScale(scale, RoundingMode.HALF_UP), value.setScale(scale, RoundingMode.HALF_UP))
}
