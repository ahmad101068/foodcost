package ir.foodcost.core.model

import ir.foodcost.core.Decimals
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.core.units.Dimension
import ir.foodcost.core.units.Uom
import java.math.BigDecimal
import java.time.LocalDate

@JvmInline
value class RecipeId(val value: String)

enum class RecipeKind {
    /** Sold on the menu. */
    MENU_ITEM,

    /** Made in the kitchen and used inside other recipes, such as plain rice or one skewer of kebab. */
    PREP,

    /** An event menu, priced per guest. */
    EVENT_PACKAGE,
}

/** Where a portion is sold. Takeaway and online portions carry their packaging; online ones a commission too. */
enum class SalesChannel { DINE_IN, TAKEAWAY, ONLINE }

/**
 * A cost of a portion that is not food, such as its takeaway box, a cup and lid, or a share of the
 * cook's wage. Entered per portion; [takeawayOnly] costs are left out of portions eaten in.
 */
data class ExtraCost(val name: String, val rialsPerPortion: Long, val takeawayOnly: Boolean = false) {
    init {
        require(name.isNotBlank()) { "An extra cost needs a name" }
        require(rialsPerPortion >= 0) { "An extra cost cannot be negative" }
    }

    fun appliesTo(channel: SalesChannel): Boolean = !takeawayOnly || channel != SalesChannel.DINE_IN
}

/** Whether a line's quantity is what goes into the food or what is bought. */
enum class QtyBasis {
    /** After cleaning and trimming; the ingredient's yield is applied to find what is bought. */
    USABLE,

    /** As bought, waste included; no yield is applied. */
    AS_PURCHASED,
}

sealed interface ComponentRef {
    data class IngredientRef(val id: IngredientId) : ComponentRef
    data class RecipeRef(val id: RecipeId) : ComponentRef
}

data class RecipeLine(
    val component: ComponentRef,
    /** Null until the quantity is entered; the recipe is incomplete meanwhile. */
    val qty: BigDecimal?,
    val uom: Uom,
    /** Ignored for sub-recipes, whose cost already includes their own yields. */
    val basis: QtyBasis = QtyBasis.USABLE,
) {
    init {
        require(qty == null || qty.signum() >= 0) { "A quantity cannot be negative" }
    }
}

/**
 * The recipe as it stands from [validFrom] until the next version. Old versions are kept,
 * so a closed period is always recalculated with the recipe it really used.
 */
data class RecipeVersion(
    val validFrom: LocalDate,
    /** Portions (guests, for an event package) that the batch lines produce. */
    val portions: BigDecimal = BigDecimal.ONE,
    /** Quantities for the whole batch. */
    val batchLines: List<RecipeLine> = emptyList(),
    /** Quantities added to every portion when it is served, such as rice and butter beside a stew. */
    val portionLines: List<RecipeLine> = emptyList(),
    /** Total output of the batch, such as 4 kg of cooked rice; needed to use the recipe by weight or volume. */
    val outputQty: BigDecimal? = null,
    val outputUom: Uom? = null,
    /** Share added for unweighed items such as salt and spices; null takes the default. */
    val miscPercent: BigDecimal? = null,
    val note: String = "",
    /** Costs of a portion that are not food, counted once, outside the food cost and its misc share. */
    val extras: List<ExtraCost> = emptyList(),
) {
    init {
        require(portions.signum() > 0) { "Portions must be positive" }
        require((outputQty == null) == (outputUom == null)) { "Output quantity and unit go together" }
        require(outputQty == null || outputQty.signum() > 0) { "The output quantity must be positive" }
        require(outputUom == null || outputUom.dimension != Dimension.PORTION) { "Output is a weight, volume or count" }
        require(miscPercent == null || (miscPercent.signum() >= 0 && miscPercent < Decimals.HUNDRED)) {
            "The misc percent must be in [0, 100)"
        }
    }

    val lines: List<RecipeLine> get() = batchLines + portionLines
}

data class Recipe(
    val id: RecipeId,
    val name: String,
    val kind: RecipeKind,
    val versions: List<RecipeVersion>,
    /** Menu section, such as "main" or "drinks"; pricing and misc defaults can depend on it. */
    val category: String? = null,
    /** A pricing rule for this item only; null takes the category or the general rule. */
    val pricing: PricingMethod? = null,
) {
    init {
        require(versions.map { it.validFrom }.toSet().size == versions.size) {
            "Two versions of '$name' start on the same day"
        }
    }

    /** The version in force on [date], or null if the recipe did not exist yet. */
    fun versionAt(date: LocalDate): RecipeVersion? =
        versions.filter { !it.validFrom.isAfter(date) }.maxByOrNull { it.validFrom }
}

/** The price on the menu from [effectiveFrom]; it includes VAT when the menu prices do. */
data class MenuPrice(val recipeId: RecipeId, val effectiveFrom: LocalDate, val rials: Long) {
    init {
        require(rials > 0) { "A menu price must be positive" }
    }
}
