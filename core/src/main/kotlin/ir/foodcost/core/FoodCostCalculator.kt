package ir.foodcost.core

import ir.foodcost.core.costing.CostEngine
import ir.foodcost.core.costing.CostingRules
import ir.foodcost.core.costing.IngredientCostSource
import ir.foodcost.core.costing.LatestPurchasePrice
import ir.foodcost.core.costing.RecipeCost
import ir.foodcost.core.costing.RequirementList
import ir.foodcost.core.costing.UnitCostResult
import ir.foodcost.core.model.Catalog
import ir.foodcost.core.model.IngredientId
import ir.foodcost.core.model.RecipeId
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.pricing.PriceEvaluation
import ir.foodcost.core.pricing.Pricer
import ir.foodcost.core.pricing.PricingSettings
import ir.foodcost.core.reports.CostChange
import ir.foodcost.core.reports.EventQuote
import ir.foodcost.core.reports.IngredientEffect
import ir.foodcost.core.reports.MenuItemReport
import ir.foodcost.core.reports.MenuReport
import java.math.BigDecimal
import java.time.LocalDate

/** The single entry point the app uses: costs, suggested prices, menu reports, shopping lists and quotes. */
class FoodCostCalculator(
    val catalog: Catalog,
    val costing: CostingRules = CostingRules(),
    val pricing: PricingSettings = PricingSettings(),
    costSource: IngredientCostSource = LatestPurchasePrice(catalog),
    /**
     * The catalog and rules in force on a day when they were different then, or null when they were
     * these: a yield or a setting changed later counts from the day it changed, so the days before
     * it, a closed period above all, keep the figures they had.
     */
    private val earlier: (LocalDate) -> Terms? = { null },
) {
    /** What the figures of one day are worked out from. */
    class Terms(val catalog: Catalog, val costing: CostingRules, val pricing: PricingSettings)

    private val engine = CostEngine(catalog, costing, costSource)
    /** Suggested prices, sales by channel and pricing backwards, on [pricing]. */
    val pricer = Pricer(pricing)

    /** One day's calculation, on the catalog and rules in force that day. */
    private class Day(val sheet: CostEngine.CostSheet, val pricer: Pricer, val catalog: Catalog)

    private fun day(date: LocalDate): Day {
        val then = earlier(date) ?: return Day(engine.at(date), pricer, catalog)
        return Day(CostEngine(then.catalog, then.costing, LatestPurchasePrice(then.catalog)).at(date), Pricer(then.pricing), then.catalog)
    }

    /** A calculation fixed at [date]; reuse it for many lookups on the same day. */
    fun sheet(date: LocalDate): CostEngine.CostSheet = day(date).sheet

    fun cost(id: RecipeId, date: LocalDate): RecipeCost = day(date).sheet.recipe(id)

    /** Cost, suggested price and the status of the current menu price. */
    fun evaluate(id: RecipeId, date: LocalDate): PriceEvaluation = evaluate(day(date), id)

    /** Every menu item and event package on [date]. */
    fun menu(date: LocalDate): MenuReport {
        val day = day(date)
        val items = day.catalog.recipes.values
            .filter { it.kind != RecipeKind.PREP }
            .map { MenuItemReport(evaluate(day, it.id)) }
        return MenuReport(date, items)
    }

    /** What to buy for [portions] portions, waste included. */
    fun requirements(id: RecipeId, portions: BigDecimal, date: LocalDate): RequirementList =
        day(date).sheet.requirements(id, portions)

    /** A per-guest price and shopping list for an event menu. */
    fun quoteEvent(id: RecipeId, guests: Int, date: LocalDate): EventQuote {
        require(guests > 0) { "An event needs at least one guest" }
        val day = day(date)
        return EventQuote(evaluate(day, id), guests, day.sheet.requirements(id, BigDecimal(guests)))
    }

    /** Why one portion's cost changed between [from] and [to]. */
    fun costChange(id: RecipeId, from: LocalDate, to: LocalDate): CostChange =
        costChange(id, day(from), day(to))

    /** Every menu item whose cost changed between [from] and [to], the largest increase first. */
    fun costChanges(from: LocalDate, to: LocalDate): List<CostChange> {
        val before = day(from)
        val after = day(to)
        return catalog.recipes.values
            .filter { it.kind != RecipeKind.PREP }
            .map { costChange(it.id, before, after) }
            .filter { it.delta.signum() != 0 }
            .sortedWith(compareBy<CostChange> { it.deltaPercent == null }.thenByDescending { it.deltaPercent })
    }

    private fun evaluate(day: Day, id: RecipeId): PriceEvaluation =
        day.pricer.evaluate(day.sheet.recipe(id), day.catalog.menuPriceAt(id, day.sheet.date)?.rials)

    private fun costChange(id: RecipeId, beforeDay: Day, afterDay: Day): CostChange {
        val before = beforeDay.sheet
        val after = afterDay.sheet
        val one = BigDecimal.ONE
        val was = before.requirements(id, one).items.associateBy { it.ingredient.id }
        val now = after.requirements(id, one).items.associateBy { it.ingredient.id }
        val effects = (was.keys + now.keys).map { ingredientId ->
            val a = was[ingredientId]
            val b = now[ingredientId]
            IngredientEffect(
                ingredient = (b ?: a)!!.ingredient,
                qtyBefore = a?.purchaseQty ?: BigDecimal.ZERO,
                qtyAfter = b?.purchaseQty ?: BigDecimal.ZERO,
                unitCostBefore = a?.unitCost ?: before.unitCostOrNull(beforeDay.catalog, ingredientId),
                unitCostAfter = b?.unitCost,
            )
        }.filter { it.total.signum() != 0 }.sortedByDescending { it.total.abs() }
        return CostChange(before.recipe(id), after.recipe(id), effects)
    }

    /** The unit cost on this sheet's date, for an ingredient a recipe only started using later. */
    private fun CostEngine.CostSheet.unitCostOrNull(catalog: Catalog, id: IngredientId): BigDecimal? {
        val ingredient = catalog.ingredient(id) ?: return null
        return (unitCost(ingredient) as? UnitCostResult.Found)?.rialsPerBaseUnit
    }
}
