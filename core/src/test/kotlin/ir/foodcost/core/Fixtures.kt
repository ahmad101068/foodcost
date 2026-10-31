package ir.foodcost.core

import ir.foodcost.core.model.ComponentRef
import ir.foodcost.core.model.Ingredient
import ir.foodcost.core.model.IngredientId
import ir.foodcost.core.model.MenuPrice
import ir.foodcost.core.model.PurchasePrice
import ir.foodcost.core.model.QtyBasis
import ir.foodcost.core.model.Recipe
import ir.foodcost.core.model.RecipeId
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.model.RecipeLine
import ir.foodcost.core.model.RecipeVersion
import ir.foodcost.core.model.YieldStage
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.core.units.Conversions
import ir.foodcost.core.units.Dimension
import ir.foodcost.core.units.Uom
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Short builders so each test reads like the kitchen example it checks. Amounts are rials. */
object F {
    val D1: LocalDate = LocalDate.of(2026, 10, 1)
    val D2: LocalDate = LocalDate.of(2026, 11, 1)

    fun rid(id: String) = RecipeId(id)
    fun iid(id: String) = IngredientId(id)

    fun ingredient(
        id: String,
        dimension: Dimension = Dimension.MASS,
        yields: List<String> = emptyList(),
        gramsPerPiece: String? = null,
        density: String? = null,
    ) = Ingredient(
        IngredientId(id), id, dimension,
        yields.mapIndexed { i, ratio -> YieldStage("stage ${i + 1}", ratio.bd) },
        Conversions(gramsPerPiece?.bd, density?.bd),
    )

    fun price(id: String, qty: String, uom: Uom, rials: Long, from: LocalDate = D1) =
        PurchasePrice(IngredientId(id), from, qty.bd, uom, rials)

    fun item(id: String, qty: String?, uom: Uom = Uom.GRAM, basis: QtyBasis = QtyBasis.USABLE) =
        RecipeLine(ComponentRef.IngredientRef(IngredientId(id)), qty?.bd, uom, basis)

    fun sub(id: String, qty: String, uom: Uom = Uom.PORTION) =
        RecipeLine(ComponentRef.RecipeRef(RecipeId(id)), qty.bd, uom)

    fun version(
        batch: List<RecipeLine> = emptyList(),
        perPortion: List<RecipeLine> = emptyList(),
        portions: String = "1",
        misc: String? = "0",
        from: LocalDate = D1,
        output: Pair<String, Uom>? = null,
    ) = RecipeVersion(from, portions.bd, batch, perPortion, output?.first?.bd, output?.second, misc?.bd)

    fun recipe(
        id: String,
        vararg versions: RecipeVersion,
        kind: RecipeKind = RecipeKind.MENU_ITEM,
        category: String? = null,
        pricing: PricingMethod? = null,
    ) = Recipe(RecipeId(id), id, kind, versions.toList(), category, pricing)

    fun menuPrice(id: String, rials: Long, from: LocalDate = D1) = MenuPrice(RecipeId(id), from, rials)
}

/** Compares amounts after rounding both to [scale] decimals, so 4000 and 4000.000 are equal. */
fun assertAmount(expected: String, actual: BigDecimal?, scale: Int = 4) {
    val value = assertNotNull(actual, "expected $expected, got null")
    assertEquals(expected.bd.setScale(scale, RoundingMode.HALF_UP), value.setScale(scale, RoundingMode.HALF_UP))
}
