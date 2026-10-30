package ir.foodcost.core.money

import ir.foodcost.core.over
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * How amounts are shown and typed. Storage is always whole current rials, so every currency
 * converts exactly and switching the display never changes a stored number.
 */
enum class DisplayCurrency(val rialsPerUnit: Long, val fractionDigits: Int) {
    /** The current rial. */
    RIAL(1, 0),

    /** Toman: 10 rials. */
    TOMAN(10, 0),

    /** The rial after removing four zeros: 1 new rial = 10,000 current rials; 1 qeran = 1/100 new rial. */
    NEW_RIAL(10_000, 2);

    private val factor: BigDecimal = BigDecimal(rialsPerUnit)

    /** The exact amount in this currency. Exact because [rialsPerUnit] is a power of ten. */
    fun exact(rials: BigDecimal): BigDecimal = rials.divide(factor)

    fun exact(rials: Long): BigDecimal = exact(BigDecimal(rials))

    /** The amount as shown: [fractionDigits] decimals, half up. */
    fun display(rials: BigDecimal): BigDecimal = exact(rials).setScale(fractionDigits, RoundingMode.HALF_UP)

    fun display(rials: Long): BigDecimal = display(BigDecimal(rials))

    /** Converts an amount typed in this currency to whole rials. Rejects anything finer than one rial. */
    fun toRials(amount: BigDecimal): Long {
        val rials = amount.multiply(factor)
        require(rials.signum() == 0 || rials.stripTrailingZeros().scale() <= 0) {
            "$amount ${name.lowercase()} is finer than one rial"
        }
        return rials.longValueExact()
    }
}

/** Rounds a suggested menu price to a step, for example up to the next 100,000 rials. */
data class PriceRounding(val stepRials: Long = 1, val direction: Direction = Direction.UP) {
    enum class Direction { UP, NEAREST, DOWN }

    init {
        require(stepRials >= 1) { "The rounding step must be at least one rial" }
    }

    /** Rounds to whole steps and never returns less than one step. Zero or less returns 0. */
    fun apply(amountRials: BigDecimal): Long {
        if (amountRials.signum() <= 0) return 0
        val step = BigDecimal(stepRials)
        // Cut the noise that repeating divisions leave in the last digits,
        // so an exact 10,000,000 never rounds up to the next step.
        val steps = amountRials.over(step).setScale(12, RoundingMode.HALF_UP)
        val whole = when (direction) {
            Direction.UP -> steps.setScale(0, RoundingMode.CEILING)
            Direction.NEAREST -> steps.setScale(0, RoundingMode.HALF_UP)
            Direction.DOWN -> steps.setScale(0, RoundingMode.FLOOR)
        }.max(BigDecimal.ONE)
        return whole.multiply(step).longValueExact()
    }
}
