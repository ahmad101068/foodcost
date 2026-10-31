package ir.foodcost.core.reports

import ir.foodcost.core.Decimals
import ir.foodcost.core.costing.RecipeCost
import ir.foodcost.core.costing.RequirementList
import ir.foodcost.core.model.Ingredient
import ir.foodcost.core.model.Recipe
import ir.foodcost.core.model.RecipeId
import ir.foodcost.core.over
import ir.foodcost.core.pricing.PriceEvaluation
import ir.foodcost.core.pricing.PriceStatus
import ir.foodcost.core.roundTo
import ir.foodcost.core.roundToRials
import java.math.BigDecimal
import java.time.LocalDate

data class MenuItemReport(val evaluation: PriceEvaluation) {
    val recipe: Recipe get() = evaluation.cost.recipe
    val cost: RecipeCost get() = evaluation.cost
    val status: PriceStatus get() = evaluation.status
}

/** Every menu item and event package on one date, with its cost, suggestion and status. */
data class MenuReport(val date: LocalDate, val items: List<MenuItemReport>) {
    fun count(status: PriceStatus): Int = items.count { it.status == status }

    /** Items under or near the line, the largest needed increase first. */
    val needsRepricing: List<MenuItemReport>
        get() = items
            .filter { it.status == PriceStatus.UNDER || it.status == PriceStatus.NEAR }
            .sortedByDescending { it.evaluation.changePercent ?: BigDecimal.ZERO }
}

/** A quote for an event: the per-guest price and what to buy for all guests. */
data class EventQuote(val evaluation: PriceEvaluation, val guests: Int, val requirements: RequirementList) {
    val pricePerGuest: Long get() = evaluation.suggestion.menuPrice
    val total: Long get() = Math.multiplyExact(pricePerGuest, guests.toLong())
    val foodCostTotal: BigDecimal get() = evaluation.cost.foodCostPerPortion.multiply(BigDecimal(guests))
}

/**
 * How one ingredient moved a portion's cost between two dates, split into what its price did and
 * what the change in quantity did. Quantities are per portion, as bought, in base units.
 */
data class IngredientEffect(
    val ingredient: Ingredient,
    val qtyBefore: BigDecimal,
    val qtyAfter: BigDecimal,
    val unitCostBefore: BigDecimal?,
    val unitCostAfter: BigDecimal?,
) {
    /** The new quantity times the change in unit cost. */
    val priceEffect: BigDecimal get() = qtyAfter.multiply((unitCostAfter ?: BigDecimal.ZERO) - (unitCostBefore ?: BigDecimal.ZERO))

    /** The change in quantity times the old unit cost. */
    val quantityEffect: BigDecimal get() = (qtyAfter - qtyBefore).multiply(unitCostBefore ?: BigDecimal.ZERO)

    val total: BigDecimal get() = priceEffect + quantityEffect
}

/** Why a portion's food cost changed between two dates. */
data class CostChange(val before: RecipeCost, val after: RecipeCost, val effects: List<IngredientEffect>) {
    val recipe: Recipe get() = after.recipe
    val delta: BigDecimal get() = after.foodCostPerPortion - before.foodCostPerPortion

    val deltaPercent: BigDecimal?
        get() = if (before.foodCostPerPortion.signum() <= 0) {
            null
        } else {
            delta.multiply(Decimals.HUNDRED).over(before.foodCostPerPortion)
        }

    /** The part of [delta] not tied to one ingredient: the misc shares on top of the changes. */
    val otherEffect: BigDecimal get() = delta - effects.fold(BigDecimal.ZERO) { sum, e -> sum + e.total }
}

/** Frozen figures of a menu report, for closing a period. Plain values only, so later edits cannot reach them. */
data class MenuSnapshot(val date: LocalDate, val items: List<Item>) {
    data class Item(
        val recipeId: RecipeId,
        val name: String,
        val foodCostRials: Long,
        val menuPriceRials: Long?,
        val suggestedPriceRials: Long,
        val foodCostPercent: BigDecimal?,
        val status: PriceStatus,
    )

    companion object {
        fun of(report: MenuReport): MenuSnapshot = MenuSnapshot(
            report.date,
            report.items.map {
                Item(
                    it.recipe.id,
                    it.recipe.name,
                    it.cost.foodCostPerPortion.roundToRials(),
                    it.evaluation.currentPrice,
                    it.evaluation.suggestion.menuPrice,
                    it.evaluation.foodCostPercent?.roundTo(2),
                    it.status,
                )
            },
        )
    }
}
