package ir.foodcost.core

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Number rules for the whole core.
 *
 * Stored money (purchase prices, menu prices) is a whole number of current rials in a [Long].
 * Everything derived from it, such as the cost of one gram or one portion, is a [BigDecimal]
 * kept at 34 significant digits and rounded only when it is shown or written on a document.
 * Floating point is never used for money.
 *
 * Kotlin's `/` operator on BigDecimal keeps the scale of the left side, so `1.bd / 3.bd` is 0.
 * Always divide with [over].
 */
object Decimals {
    val CONTEXT: MathContext = MathContext(34, RoundingMode.HALF_EVEN)
    val HUNDRED: BigDecimal = BigDecimal(100)
}

/** Division at [Decimals.CONTEXT] precision. */
infix fun BigDecimal.over(divisor: BigDecimal): BigDecimal = divide(divisor, Decimals.CONTEXT)

/** Whole rials, half up: the form shown to users and written on documents. */
fun BigDecimal.roundToRials(): Long = setScale(0, RoundingMode.HALF_UP).longValueExact()

/** Rounds half up to [scale] decimals, for display. */
fun BigDecimal.roundTo(scale: Int): BigDecimal = setScale(scale, RoundingMode.HALF_UP)

/** [this] percent of [amount]: `3.bd.percentOf(x)` is 3% of x. */
fun BigDecimal.percentOf(amount: BigDecimal): BigDecimal = amount.multiply(this).over(Decimals.HUNDRED)

/** The fraction a percentage stands for: 35 becomes 0.35. */
fun BigDecimal.asFraction(): BigDecimal = over(Decimals.HUNDRED)

val Int.bd: BigDecimal get() = BigDecimal(this)
val Long.bd: BigDecimal get() = BigDecimal(this)
val String.bd: BigDecimal get() = BigDecimal(this)
