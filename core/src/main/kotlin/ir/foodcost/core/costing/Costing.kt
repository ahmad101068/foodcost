package ir.foodcost.core.costing

import ir.foodcost.core.Decimals
import ir.foodcost.core.model.Catalog
import ir.foodcost.core.model.ComponentRef
import ir.foodcost.core.model.Ingredient
import ir.foodcost.core.model.PurchasePrice
import ir.foodcost.core.model.Recipe
import ir.foodcost.core.model.RecipeId
import ir.foodcost.core.model.RecipeLine
import ir.foodcost.core.model.RecipeVersion
import ir.foodcost.core.over
import ir.foodcost.core.units.UnitConverter
import java.math.BigDecimal
import java.time.LocalDate

/** Cost of one base unit (g, ml or piece) of an ingredient as purchased. */
sealed interface UnitCostResult {
    data class Found(val rialsPerBaseUnit: BigDecimal, val price: PurchasePrice?) : UnitCostResult

    data object Missing : UnitCostResult

    /** A price exists, but its package unit cannot be converted to the ingredient's base unit. */
    data class UnitMismatch(val price: PurchasePrice) : UnitCostResult
}

/**
 * Where ingredient costs come from. Menu pricing uses the latest purchase price (what it costs to
 * buy again); inventory valuation can plug in a weighted average later without touching the engine.
 */
fun interface IngredientCostSource {
    fun unitCost(ingredient: Ingredient, date: LocalDate): UnitCostResult
}

class LatestPurchasePrice(private val catalog: Catalog) : IngredientCostSource {
    override fun unitCost(ingredient: Ingredient, date: LocalDate): UnitCostResult {
        val price = catalog.priceAt(ingredient.id, date) ?: return UnitCostResult.Missing
        val packageBase = UnitConverter.toBase(price.packageQty, price.packageUom, ingredient.baseDimension, ingredient.conversions)
            ?: return UnitCostResult.UnitMismatch(price)
        return UnitCostResult.Found(BigDecimal(price.netCostRials).over(packageBase), price)
    }
}

/** Defaults for the share added for unweighed items (salt, spices, frying oil). */
data class CostingRules(
    val defaultMiscPercent: BigDecimal = BigDecimal(3),
    val miscPercentByCategory: Map<String, BigDecimal> = emptyMap(),
) {
    init {
        (miscPercentByCategory.values + defaultMiscPercent).forEach {
            require(it.signum() >= 0 && it < Decimals.HUNDRED) { "The misc percent must be in [0, 100)" }
        }
    }

    fun miscPercentFor(recipe: Recipe, version: RecipeVersion): BigDecimal =
        version.miscPercent ?: recipe.category?.let { miscPercentByCategory[it] } ?: defaultMiscPercent
}

enum class IssueType {
    /** The recipe has no version on the date. */
    NO_VERSION,

    /** The version has no lines. */
    NO_LINES,

    /** The line points to an ingredient or recipe that does not exist. */
    MISSING_COMPONENT,

    /** The line has no quantity yet. */
    MISSING_QUANTITY,

    /** The line's unit cannot be converted, e.g. ml of an ingredient bought by weight with no density. */
    UNIT_MISMATCH,

    /** The ingredient has no purchase price on the date. */
    NO_PRICE,

    /** The purchase price's package unit cannot be converted to the ingredient's base unit. */
    PRICE_UNIT_MISMATCH,

    /** A sub-recipe is used by weight or volume, but its total output is not entered. */
    NO_OUTPUT_QUANTITY,

    /** The line uses a recipe that, directly or through others, uses itself. */
    CYCLE,

    /** The sub-recipe has issues of its own. */
    SUB_RECIPE_INCOMPLETE,
}

enum class Section { BATCH, PORTION }

data class CostIssue(
    val type: IssueType,
    val recipeId: RecipeId,
    val section: Section? = null,
    val lineIndex: Int? = null,
    val component: ComponentRef? = null,
)

data class LineCost(
    val section: Section,
    val index: Int,
    val line: RecipeLine,
    /** The cost of the line as written: the whole batch for batch lines. */
    val cost: BigDecimal,
    val costPerPortion: BigDecimal,
    /** Ingredient lines: base units bought for the line as written, waste included. */
    val purchaseQty: BigDecimal?,
    /** Sub-recipe lines: portions of the sub-recipe the line takes. */
    val subRecipePortions: BigDecimal?,
    val issue: CostIssue?,
)

/**
 * The food cost of one portion of a recipe on [date], line by line.
 * Lines with an issue count as zero and are listed in [issues]; check [isComplete] before trusting the total.
 */
data class RecipeCost(
    val recipe: Recipe,
    val date: LocalDate,
    val version: RecipeVersion?,
    val lines: List<LineCost>,
    /** Ingredient lines per portion. */
    val ingredientsPerPortion: BigDecimal,
    /** Sub-recipe lines per portion; each already includes its own misc share. */
    val subRecipesPerPortion: BigDecimal,
    val miscPercent: BigDecimal,
    /** The misc share, taken on this recipe's own ingredients only, never again on sub-recipes. */
    val miscPerPortion: BigDecimal,
    val issues: List<CostIssue>,
) {
    val foodCostPerPortion: BigDecimal = ingredientsPerPortion + subRecipesPerPortion + miscPerPortion

    val portions: BigDecimal get() = version?.portions ?: BigDecimal.ONE

    val batchCost: BigDecimal get() = foodCostPerPortion.multiply(portions)

    val isComplete: Boolean get() = issues.isEmpty()

    val hasLines: Boolean get() = lines.isNotEmpty()

    /** The cost of one g, ml or piece of the batch output, when the version states its output. */
    val costPerOutputUnit: BigDecimal?
        get() {
            val v = version ?: return null
            val qty = v.outputQty ?: return null
            val uom = v.outputUom ?: return null
            return batchCost.over(qty.multiply(uom.toBase))
        }
}

/** One ingredient's total in a shopping list. Quantities are in the ingredient's base unit. */
data class Requirement(
    val ingredient: Ingredient,
    /** What goes into the food, after preparation. */
    val usableQty: BigDecimal,
    /** What to buy, waste included. */
    val purchaseQty: BigDecimal,
    /** Rials per purchased base unit; null when there is no usable price. */
    val unitCost: BigDecimal?,
    val price: PurchasePrice?,
) {
    val cost: BigDecimal? get() = unitCost?.multiply(purchaseQty)

    /** How many packages of the current price to buy, such as 1.6 cans of 16 kg. */
    val packages: BigDecimal?
        get() {
            val p = price ?: return null
            val size = UnitConverter.toBase(p.packageQty, p.packageUom, ingredient.baseDimension, ingredient.conversions)
                ?: return null
            return purchaseQty.over(size)
        }
}

data class RequirementList(val items: List<Requirement>, val issues: List<CostIssue>) {
    /** Cost of the ingredients that have a price. Misc shares are not part of a shopping list. */
    val ingredientCost: BigDecimal get() = items.mapNotNull { it.cost }.fold(BigDecimal.ZERO, BigDecimal::add)

    val isComplete: Boolean get() = issues.isEmpty()
}
