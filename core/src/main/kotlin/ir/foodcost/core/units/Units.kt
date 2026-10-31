package ir.foodcost.core.units

import ir.foodcost.core.over
import java.math.BigDecimal

/** What a quantity measures. PORTION is used only to take portions of a sub-recipe. */
enum class Dimension { MASS, VOLUME, COUNT, PORTION }

/** Units of measure, each with its size in the base unit of its dimension: g, ml, piece or portion. */
enum class Uom(val dimension: Dimension, size: String) {
    GRAM(Dimension.MASS, "1"),
    KILOGRAM(Dimension.MASS, "1000"),

    /** The traditional unit saffron is sold in: 4.608 g. */
    MESGHAL(Dimension.MASS, "4.608"),
    MILLILITER(Dimension.VOLUME, "1"),
    LITER(Dimension.VOLUME, "1000"),
    TEASPOON(Dimension.VOLUME, "5"),
    TABLESPOON(Dimension.VOLUME, "15"),
    PIECE(Dimension.COUNT, "1"),
    PORTION(Dimension.PORTION, "1");

    /** Size of one unit in the base unit of [dimension]. */
    val toBase: BigDecimal = BigDecimal(size)
}

/** One ingredient's bridges between dimensions: the weight of one piece and its density. */
data class Conversions(
    val gramsPerPiece: BigDecimal? = null,
    val gramsPerMilliliter: BigDecimal? = null,
) {
    init {
        require(gramsPerPiece == null || gramsPerPiece.signum() > 0) { "The weight of a piece must be positive" }
        require(gramsPerMilliliter == null || gramsPerMilliliter.signum() > 0) { "Density must be positive" }
    }
}

object UnitConverter {
    /**
     * Converts [qty] [uom] to the base unit of [target]: g, ml, piece or portion.
     * Crossing dimensions goes through grams and needs the matching bridge in [conversions].
     * Returns null when the units cannot be converted.
     */
    fun toBase(qty: BigDecimal, uom: Uom, target: Dimension, conversions: Conversions = Conversions()): BigDecimal? {
        val base = qty.multiply(uom.toBase)
        if (uom.dimension == target) return base
        if (uom.dimension == Dimension.PORTION || target == Dimension.PORTION) return null
        val grams = toGrams(base, uom.dimension, conversions) ?: return null
        return fromGrams(grams, target, conversions)
    }

    private fun toGrams(base: BigDecimal, from: Dimension, c: Conversions): BigDecimal? = when (from) {
        Dimension.MASS -> base
        Dimension.VOLUME -> c.gramsPerMilliliter?.let { base.multiply(it) }
        Dimension.COUNT -> c.gramsPerPiece?.let { base.multiply(it) }
        Dimension.PORTION -> null
    }

    private fun fromGrams(grams: BigDecimal, to: Dimension, c: Conversions): BigDecimal? = when (to) {
        Dimension.MASS -> grams
        Dimension.VOLUME -> c.gramsPerMilliliter?.let { grams.over(it) }
        Dimension.COUNT -> c.gramsPerPiece?.let { grams.over(it) }
        Dimension.PORTION -> null
    }
}
