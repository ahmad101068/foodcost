package ir.foodcost.core.model

import ir.foodcost.core.units.Conversions
import ir.foodcost.core.units.Dimension
import ir.foodcost.core.units.Uom
import java.math.BigDecimal
import java.time.LocalDate

@JvmInline
value class IngredientId(val value: String)

/** One preparation step and the share of weight that survives it, for example trimming at 0.80. */
data class YieldStage(val name: String, val ratio: BigDecimal) {
    init {
        require(ratio.signum() > 0 && ratio <= BigDecimal.ONE) { "Yield of '$name' must be in (0, 1], was $ratio" }
    }
}

data class Ingredient(
    val id: IngredientId,
    val name: String,
    /** The dimension the ingredient is costed in: per gram, per milliliter or per piece. */
    val baseDimension: Dimension,
    /** Preparation stages in order. The usable share is the product of their ratios. */
    val yields: List<YieldStage> = emptyList(),
    val conversions: Conversions = Conversions(),
    val category: String? = null,
) {
    init {
        require(baseDimension != Dimension.PORTION) { "An ingredient cannot be costed per portion" }
    }

    /** The share of the purchased quantity that is usable after every preparation stage. */
    val usableFraction: BigDecimal = yields.fold(BigDecimal.ONE) { acc, stage -> acc.multiply(stage.ratio) }
}

/**
 * One purchase price, from an invoice or a supplier's list, valid from [effectiveFrom] until the next one.
 * [netCostRials] is what the restaurant really pays for one package.
 */
data class PurchasePrice(
    val ingredientId: IngredientId,
    val effectiveFrom: LocalDate,
    val packageQty: BigDecimal,
    val packageUom: Uom,
    val grossRials: Long,
    val discountRials: Long = 0,
    val taxRials: Long = 0,
    /** VAT the restaurant claims back as input credit is not part of the cost. */
    val taxRecoverable: Boolean = false,
    val deliveryRials: Long = 0,
    val supplier: String? = null,
) {
    init {
        require(packageQty.signum() > 0) { "The package quantity must be positive" }
        require(packageUom.dimension != Dimension.PORTION) { "A purchase cannot be in portions" }
        require(grossRials >= 0 && discountRials >= 0 && taxRials >= 0 && deliveryRials >= 0) { "Amounts cannot be negative" }
        require(discountRials <= grossRials) { "The discount cannot exceed the gross price" }
    }

    /** Gross − discount + tax that is not claimed back + delivery. */
    val netCostRials: Long get() = grossRials - discountRials + (if (taxRecoverable) 0 else taxRials) + deliveryRials
}
