package ir.foodcost.core.costing

import ir.foodcost.core.model.Catalog
import ir.foodcost.core.model.ComponentRef
import ir.foodcost.core.model.Ingredient
import ir.foodcost.core.model.IngredientId
import ir.foodcost.core.model.QtyBasis
import ir.foodcost.core.model.Recipe
import ir.foodcost.core.model.RecipeId
import ir.foodcost.core.model.RecipeLine
import ir.foodcost.core.model.RecipeVersion
import ir.foodcost.core.over
import ir.foodcost.core.percentOf
import ir.foodcost.core.units.Dimension
import ir.foodcost.core.units.UnitConverter
import ir.foodcost.core.units.Uom
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Works out food costs from a [Catalog]. Every calculation is fixed at one date: the recipe
 * versions and prices in force that day. Nothing is mutated, so the same date always gives the
 * same answer, which is what keeps closed periods stable.
 */
class CostEngine(
    private val catalog: Catalog,
    private val rules: CostingRules = CostingRules(),
    private val costSource: IngredientCostSource = LatestPurchasePrice(catalog),
) {
    /**
     * A calculation fixed at [date]. It keeps what it computes, so price a whole menu with one sheet.
     * A sheet is cheap and not thread-safe: use one per thread and drop it after the job.
     */
    fun at(date: LocalDate): CostSheet = CostSheet(date)

    inner class CostSheet internal constructor(val date: LocalDate) {
        private val recipeCosts = HashMap<RecipeId, RecipeCost>()
        private val unitCosts = HashMap<IngredientId, UnitCostResult>()

        /**
         * Recipes that lie on a cycle on [date]. They are never expanded, so every calculation ends,
         * and lines that use them cost zero whatever order recipes are priced in.
         */
        val cyclic: Set<RecipeId> by lazy { findCyclic() }

        fun unitCost(ingredient: Ingredient): UnitCostResult =
            unitCosts.getOrPut(ingredient.id) { costSource.unitCost(ingredient, date) }

        fun recipe(id: RecipeId): RecipeCost {
            recipeCosts[id]?.let { return it }
            val recipe = requireNotNull(catalog.recipe(id)) { "Unknown recipe $id" }
            return compute(recipe).also { recipeCosts[id] = it }
        }

        /** The ingredients needed for [portions] portions, waste included, through every sub-recipe. */
        fun requirements(id: RecipeId, portions: BigDecimal): RequirementList {
            val recipe = requireNotNull(catalog.recipe(id)) { "Unknown recipe $id" }
            val usable = LinkedHashMap<IngredientId, BigDecimal>()
            val purchase = LinkedHashMap<IngredientId, BigDecimal>()
            val issues = LinkedHashSet<CostIssue>()

            fun walk(current: Recipe, count: BigDecimal) {
                val version = current.versionAt(date)
                if (version == null) {
                    issues += CostIssue(IssueType.NO_VERSION, current.id)
                    return
                }

                fun visit(section: Section, index: Int, line: RecipeLine, multiplier: BigDecimal) {
                    fun issue(type: IssueType) {
                        issues += CostIssue(type, current.id, section, index, line.component)
                    }
                    val qty = line.qty
                    when (val c = line.component) {
                        is ComponentRef.IngredientRef -> {
                            val ingredient = catalog.ingredient(c.id) ?: return issue(IssueType.MISSING_COMPONENT)
                            if (qty == null) return issue(IssueType.MISSING_QUANTITY)
                            val base = UnitConverter.toBase(qty, line.uom, ingredient.baseDimension, ingredient.conversions)
                                ?: return issue(IssueType.UNIT_MISMATCH)
                            val scaled = base.multiply(multiplier)
                            val usableQty: BigDecimal
                            val purchaseQty: BigDecimal
                            if (line.basis == QtyBasis.USABLE) {
                                usableQty = scaled
                                purchaseQty = scaled.over(ingredient.usableFraction)
                            } else {
                                usableQty = scaled.multiply(ingredient.usableFraction)
                                purchaseQty = scaled
                            }
                            usable.merge(c.id, usableQty, BigDecimal::add)
                            purchase.merge(c.id, purchaseQty, BigDecimal::add)
                        }
                        is ComponentRef.RecipeRef -> {
                            val sub = catalog.recipe(c.id) ?: return issue(IssueType.MISSING_COMPONENT)
                            if (c.id in cyclic) return issue(IssueType.CYCLE)
                            if (qty == null) return issue(IssueType.MISSING_QUANTITY)
                            val taken = portionsTaken(sub.versionAt(date), qty, line.uom)
                            val subPortions = taken.portions ?: return issue(taken.issue ?: IssueType.SUB_RECIPE_INCOMPLETE)
                            walk(sub, subPortions.multiply(multiplier))
                        }
                    }
                }

                val perBatch = count.over(version.portions)
                version.batchLines.forEachIndexed { i, line -> visit(Section.BATCH, i, line, perBatch) }
                version.portionLines.forEachIndexed { i, line -> visit(Section.PORTION, i, line, count) }
            }

            walk(recipe, portions)

            val items = purchase.keys.map { iid ->
                val ingredient = catalog.ingredient(iid)!!
                val found = when (val unit = unitCost(ingredient)) {
                    is UnitCostResult.Found -> unit
                    UnitCostResult.Missing -> {
                        issues += CostIssue(IssueType.NO_PRICE, id, component = ComponentRef.IngredientRef(iid))
                        null
                    }
                    is UnitCostResult.UnitMismatch -> {
                        issues += CostIssue(IssueType.PRICE_UNIT_MISMATCH, id, component = ComponentRef.IngredientRef(iid))
                        null
                    }
                }
                Requirement(ingredient, usable.getValue(iid), purchase.getValue(iid), found?.rialsPerBaseUnit, found?.price)
            }.sortedWith(compareBy<Requirement> { it.cost == null }.thenByDescending { it.cost })

            return RequirementList(items, issues.toList())
        }

        private fun compute(recipe: Recipe): RecipeCost {
            val zero = BigDecimal.ZERO
            val version = recipe.versionAt(date)
                ?: return RecipeCost(
                    recipe, date, null, emptyList(), zero, zero, zero, zero,
                    listOf(CostIssue(IssueType.NO_VERSION, recipe.id)),
                )
            val lines = version.batchLines.mapIndexed { i, line -> lineCost(recipe, version, Section.BATCH, i, line) } +
                version.portionLines.mapIndexed { i, line -> lineCost(recipe, version, Section.PORTION, i, line) }
            val ingredients = lines.filter { it.line.component is ComponentRef.IngredientRef }.sumOf { it.costPerPortion }
            val subRecipes = lines.filter { it.line.component is ComponentRef.RecipeRef }.sumOf { it.costPerPortion }
            val miscPercent = rules.miscPercentFor(recipe, version)
            val issues = buildList {
                if (lines.isEmpty()) add(CostIssue(IssueType.NO_LINES, recipe.id))
                lines.mapNotNullTo(this) { it.issue }
            }
            return RecipeCost(
                recipe, date, version, lines, ingredients, subRecipes,
                miscPercent, miscPercent.percentOf(ingredients), issues,
            )
        }

        private fun lineCost(recipe: Recipe, version: RecipeVersion, section: Section, index: Int, line: RecipeLine): LineCost {
            fun result(
                cost: BigDecimal,
                purchaseQty: BigDecimal? = null,
                subPortions: BigDecimal? = null,
                issue: IssueType? = null,
            ) = LineCost(
                section, index, line, cost,
                if (section == Section.BATCH) cost.over(version.portions) else cost,
                purchaseQty, subPortions,
                issue?.let { CostIssue(it, recipe.id, section, index, line.component) },
            )

            val zero = BigDecimal.ZERO
            val qty = line.qty
            return when (val c = line.component) {
                is ComponentRef.IngredientRef -> {
                    val ingredient = catalog.ingredient(c.id) ?: return result(zero, issue = IssueType.MISSING_COMPONENT)
                    if (qty == null) return result(zero, issue = IssueType.MISSING_QUANTITY)
                    val base = UnitConverter.toBase(qty, line.uom, ingredient.baseDimension, ingredient.conversions)
                        ?: return result(zero, issue = IssueType.UNIT_MISMATCH)
                    // A usable quantity is turned into what was bought exactly once, here.
                    val purchaseQty = if (line.basis == QtyBasis.USABLE) base.over(ingredient.usableFraction) else base
                    when (val unit = unitCost(ingredient)) {
                        is UnitCostResult.Found -> result(purchaseQty.multiply(unit.rialsPerBaseUnit), purchaseQty)
                        UnitCostResult.Missing -> result(zero, purchaseQty, issue = IssueType.NO_PRICE)
                        is UnitCostResult.UnitMismatch -> result(zero, purchaseQty, issue = IssueType.PRICE_UNIT_MISMATCH)
                    }
                }
                is ComponentRef.RecipeRef -> {
                    if (catalog.recipe(c.id) == null) return result(zero, issue = IssueType.MISSING_COMPONENT)
                    if (c.id in cyclic) return result(zero, issue = IssueType.CYCLE)
                    if (qty == null) return result(zero, issue = IssueType.MISSING_QUANTITY)
                    val sub = recipe(c.id)
                    val taken = portionsTaken(sub.version, qty, line.uom)
                    val portions = taken.portions
                        ?: return result(zero, issue = taken.issue ?: IssueType.SUB_RECIPE_INCOMPLETE)
                    result(
                        portions.multiply(sub.foodCostPerPortion),
                        subPortions = portions,
                        issue = if (sub.isComplete) null else IssueType.SUB_RECIPE_INCOMPLETE,
                    )
                }
            }
        }

        private fun findCyclic(): Set<RecipeId> {
            val edges: Map<RecipeId, List<RecipeId>> = catalog.recipes.values.associate { recipe ->
                recipe.id to recipe.versionAt(date)?.lines.orEmpty()
                    .mapNotNull { (it.component as? ComponentRef.RecipeRef)?.id }
                    .filter { catalog.recipe(it) != null }
            }
            // Tarjan's strongly connected components.
            var counter = 0
            val index = HashMap<RecipeId, Int>()
            val low = HashMap<RecipeId, Int>()
            val stack = ArrayDeque<RecipeId>()
            val onStack = HashSet<RecipeId>()
            val result = HashSet<RecipeId>()

            fun visit(v: RecipeId) {
                index[v] = counter
                low[v] = counter
                counter++
                stack.addLast(v)
                onStack += v
                for (w in edges[v].orEmpty()) {
                    if (w !in index) {
                        visit(w)
                        low[v] = minOf(low.getValue(v), low.getValue(w))
                    } else if (w in onStack) {
                        low[v] = minOf(low.getValue(v), index.getValue(w))
                    }
                }
                if (low[v] == index[v]) {
                    val component = mutableListOf<RecipeId>()
                    do {
                        val w = stack.removeLast()
                        onStack -= w
                        component += w
                    } while (w != v)
                    if (component.size > 1 || v in edges[v].orEmpty()) result += component
                }
            }

            for (v in edges.keys) if (v !in index) visit(v)
            return result
        }
    }

    private class Taken(val portions: BigDecimal?, val issue: IssueType?)

    /** How many portions of a sub-recipe a line takes, whether it is written in portions or by weight. */
    private fun portionsTaken(subVersion: RecipeVersion?, qty: BigDecimal, uom: Uom): Taken {
        if (uom.dimension == Dimension.PORTION) return Taken(qty, null)
        val version = subVersion ?: return Taken(null, IssueType.SUB_RECIPE_INCOMPLETE)
        val outQty = version.outputQty
        val outUom = version.outputUom
        if (outQty == null || outUom == null) return Taken(null, IssueType.NO_OUTPUT_QUANTITY)
        val needed = UnitConverter.toBase(qty, uom, outUom.dimension) ?: return Taken(null, IssueType.UNIT_MISMATCH)
        return Taken(needed.over(outQty.multiply(outUom.toBase)).multiply(version.portions), null)
    }
}
