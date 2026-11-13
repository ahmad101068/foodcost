package ir.foodcost.presentation

import ir.foodcost.core.Decimals
import ir.foodcost.core.over
import ir.foodcost.core.pricing.CostBasis
import ir.foodcost.core.pricing.PricingMethod
import java.math.BigDecimal

/** Shared, transient percentage state for general pricing and individual menu sections. */
internal data class PricingPercent(
    val method: MethodChoice,
    val percent: String,
    val basis: CostBasis,
    val carried: Boolean,
    val conversionOrigin: PricingMethod?,
) {
    fun withMethod(choice: MethodChoice): PricingPercent {
        if (choice == method) return this
        if (carried || (choice == MethodChoice.FOOD_COST && basis == CostBasis.FULL_COST)) {
            return copy(method = choice, carried = true, conversionOrigin = null)
        }
        val value = Numbers.parse(percent)
            ?: return copy(method = choice, carried = true, conversionOrigin = null)
        val activeBasis = if (method == MethodChoice.FOOD_COST) CostBasis.FOOD_COST else basis
        // Manual changes cannot reuse a previous conversion's number or cost basis.
        val origin = conversionOrigin?.takeIf {
            val (sourceMethod, sourceValue) = it.choiceAndPercent()
            it.costBasis() == activeBasis && equivalent(sourceValue, sourceMethod, method)?.compareTo(value) == 0
        }
        val (sourceMethod, sourceValue) = origin?.choiceAndPercent() ?: (method to value)
        val converted = equivalent(sourceValue, sourceMethod, choice)
            ?: return copy(method = choice, carried = true, conversionOrigin = null)
        return copy(
            method = choice,
            percent = Numbers.editable(converted),
            basis = activeBasis,
            carried = false,
            conversionOrigin = origin ?: when (method) {
                MethodChoice.FOOD_COST -> PricingMethod.TargetFoodCost(value)
                MethodChoice.MARGIN -> PricingMethod.TargetMargin(value, activeBasis)
                MethodChoice.MARKUP -> PricingMethod.Markup(value, activeBasis)
            },
        )
    }

    fun equivalents(): String? {
        if (carried) return null
        val value = Numbers.parse(percent)?.takeIf { validPercent(it, method) } ?: return null
        if (basis == CostBasis.FULL_COST && method != MethodChoice.FOOD_COST) {
            val other = if (method == MethodChoice.MARGIN) MethodChoice.MARKUP else MethodChoice.MARGIN
            val converted = equivalent(value, method, other) ?: return null
            return "یعنی ${shortName(other)} ${Numbers.percent(converted)}، روی همهٔ هزینه‌ها"
        }
        val others = MethodChoice.entries.filter { it != method }.mapNotNull { other ->
            equivalent(value, method, other)?.let { "${shortName(other)} ${Numbers.percent(it)}" }
        }
        return others.takeIf { it.isNotEmpty() }?.let { "یعنی ${it.joinToString(" و ")}، روی مواد اولیه" }
    }

    companion object {
        private val HUNDRED = Decimals.HUNDRED
        private val HUNDRED_SQUARED = BigDecimal(10_000)

        private fun PricingMethod.choiceAndPercent(): Pair<MethodChoice, BigDecimal> = when (this) {
            is PricingMethod.TargetFoodCost -> MethodChoice.FOOD_COST to percent
            is PricingMethod.TargetMargin -> MethodChoice.MARGIN to percent
            is PricingMethod.Markup -> MethodChoice.MARKUP to percent
        }

        private fun PricingMethod.costBasis(): CostBasis = when (this) {
            is PricingMethod.TargetFoodCost -> CostBasis.FOOD_COST
            is PricingMethod.TargetMargin -> basis
            is PricingMethod.Markup -> basis
        }

        fun validPercent(value: BigDecimal, method: MethodChoice): Boolean = when (method) {
            MethodChoice.FOOD_COST -> value.signum() > 0 && value < HUNDRED
            MethodChoice.MARGIN -> value.signum() >= 0 && value < HUNDRED
            MethodChoice.MARKUP -> value.signum() >= 0
        }

        private fun shortName(choice: MethodChoice): String = when (choice) {
            MethodChoice.FOOD_COST -> "فودکاست"
            MethodChoice.MARGIN -> "حاشیهٔ سود"
            MethodChoice.MARKUP -> "مارک‌آپ"
        }

        /** All equivalents use the same cost basis; never round an input to its display precision. */
        fun equivalent(value: BigDecimal, from: MethodChoice, to: MethodChoice): BigDecimal? {
            if (!validPercent(value, from)) return null
            if (from == to) return value
            val result = when (to) {
                MethodChoice.FOOD_COST -> when (from) {
                    MethodChoice.MARKUP -> HUNDRED_SQUARED over (HUNDRED + value)
                    else -> HUNDRED - value
                }
                MethodChoice.MARGIN -> when (from) {
                    MethodChoice.MARKUP -> value.multiply(HUNDRED) over (HUNDRED + value)
                    else -> HUNDRED - value
                }
                MethodChoice.MARKUP -> when (from) {
                    MethodChoice.FOOD_COST -> (HUNDRED - value).multiply(HUNDRED) over value
                    else -> value.multiply(HUNDRED) over (HUNDRED - value)
                }
            }
            return result.takeIf { validPercent(it, to) }?.stripTrailingZeros()
        }
    }
}
