package ir.foodcost.core.pricing

import ir.foodcost.core.Decimals
import ir.foodcost.core.asFraction
import ir.foodcost.core.costing.RecipeCost
import ir.foodcost.core.over
import java.math.BigDecimal

/**
 * A stateless, instant what-if calculator. No purchases, inventory movements or ledger writes.
 * All amounts are in rials; UI is responsible for showing the selected currency.
 * "Remaining" means revenue after the costs entered here, NOT net restaurant profit.
 */
data class InstantQuoteInput(
    val foodCostPerPortion: BigDecimal,
    val extraCostPerPortion: BigDecimal = BigDecimal.ZERO,
    val menuPrice: BigDecimal? = null,
    val targetFoodCostPercent: BigDecimal = BigDecimal(35),
    val vatPercent: BigDecimal = BigDecimal.ZERO,
    val menuPriceIncludesVat: Boolean = false,
    val portions: BigDecimal = BigDecimal.ONE,
) {
    init {
        require(foodCostPerPortion.signum() >= 0) { "Food cost cannot be negative" }
        require(extraCostPerPortion.signum() >= 0) { "Extra costs cannot be negative" }
        require(menuPrice == null || menuPrice.signum() > 0) { "Menu price must be positive" }
        require(targetFoodCostPercent.signum() > 0 && targetFoodCostPercent < Decimals.HUNDRED) {
            "Target food cost must be between 0 and 100 percent"
        }
        require(vatPercent.signum() >= 0 && vatPercent < Decimals.HUNDRED) { "Invalid VAT percentage" }
        require(portions.signum() > 0) { "Portions must be positive" }
    }
}

data class InstantQuote(
    val foodCostPerPortion: BigDecimal,
    val enteredCostPerPortion: BigDecimal,
    val netPricePerPortion: BigDecimal?,
    val foodCostPercent: BigDecimal?,
    val enteredCostMarginPercent: BigDecimal?,
    val enteredCostMarkupPercent: BigDecimal?,
    val remainderPerPortion: BigDecimal?,
    val totalEnteredCost: BigDecimal,
    val totalNetRevenue: BigDecimal?,
    /** Unrounded menu price at the requested food-cost target; VAT included only when configured. */
    val targetMenuPrice: BigDecimal,
)

object InstantCalculator {
    fun calculate(input: InstantQuoteInput): InstantQuote {
        val vatFactor = if (input.menuPriceIncludesVat) {
            BigDecimal.ONE + input.vatPercent.asFraction()
        } else {
            BigDecimal.ONE
        }
        val net = input.menuPrice?.over(vatFactor)
        val entered = input.foodCostPerPortion + input.extraCostPerPortion
        return InstantQuote(
            foodCostPerPortion = input.foodCostPerPortion,
            enteredCostPerPortion = entered,
            netPricePerPortion = net,
            foodCostPercent = net?.let { Metrics.foodCostPercent(input.foodCostPerPortion, it) },
            enteredCostMarginPercent = net?.let { Metrics.grossMarginPercent(it, entered) },
            enteredCostMarkupPercent = net?.let { Metrics.markupPercent(it, entered) },
            remainderPerPortion = net?.minus(entered),
            totalEnteredCost = entered.multiply(input.portions),
            totalNetRevenue = net?.multiply(input.portions),
            targetMenuPrice = input.foodCostPerPortion.over(input.targetFoodCostPercent.asFraction()).multiply(vatFactor),
        )
    }

    /** Refuse to give a deceptively precise result when any recipe line is missing or invalid. */
    fun calculate(cost: RecipeCost, input: InstantQuoteInput): InstantQuote {
        require(cost.hasLines && cost.isComplete) { "Recipe needs complete, valid lines" }
        require(input.foodCostPerPortion.compareTo(cost.foodCostPerPortion) == 0) {
            "Food cost must match the validated recipe"
        }
        return calculate(input)
    }
}
