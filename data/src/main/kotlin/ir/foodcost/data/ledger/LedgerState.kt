package ir.foodcost.data.ledger

import ir.foodcost.core.FoodCostCalculator
import ir.foodcost.core.costing.CostingRules
import ir.foodcost.core.model.Catalog
import ir.foodcost.core.model.Ingredient
import ir.foodcost.core.model.IngredientId
import ir.foodcost.core.model.MenuPrice
import ir.foodcost.core.model.PurchasePrice
import ir.foodcost.core.model.Recipe
import ir.foodcost.core.model.RecipeId
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.model.RecipeVersion
import ir.foodcost.core.model.YieldStage
import ir.foodcost.core.period.PeriodLock
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.core.pricing.PricingSettings
import ir.foodcost.core.reports.MenuSnapshot
import ir.foodcost.core.units.Conversions
import ir.foodcost.core.units.Dimension
import ir.foodcost.data.clock.Hlc
import ir.foodcost.data.ops.Actor
import ir.foodcost.data.ops.Change
import ir.foodcost.data.ops.OpId
import ir.foodcost.data.ops.Operation
import ir.foodcost.data.ops.Role
import ir.foodcost.data.ops.valueOr
import java.time.LocalDate
import java.util.TreeMap

data class IngredientRecord(
    val id: IngredientId,
    val name: String?,
    val baseDimension: Dimension?,
    val category: String?,
    val yields: List<YieldStage>,
    val conversions: Conversions,
    /** Hidden from pickers but still costed in the recipes that use it. */
    val archived: Boolean,
    val createdAt: Hlc,
    val updatedAt: Hlc,
) {
    /** The core model, once the name and the base dimension are known. */
    fun toIngredient(): Ingredient? {
        val n = name ?: return null
        val dimension = baseDimension ?: return null
        return Ingredient(id, n, dimension, yields, conversions, category)
    }
}

data class RecipeRecord(
    val id: RecipeId,
    val name: String?,
    val kind: RecipeKind?,
    val category: String?,
    val pricing: PricingMethod?,
    val archived: Boolean,
    val createdAt: Hlc,
    val updatedAt: Hlc,
) {
    fun toRecipe(versions: Collection<RecipeVersion>): Recipe? {
        val n = name ?: return null
        val k = kind ?: return null
        return Recipe(id, n, k, versions.toList(), category, pricing)
    }
}

data class PriceRecord(val priceId: String, val price: PurchasePrice, val recordedAt: Hlc, val voided: Boolean)

data class ClosedPeriod(val through: LocalDate, val snapshot: MenuSnapshot?, val closedAt: Hlc, val closedBy: Actor)

enum class RejectReason {
    /** The change would alter a day inside a closed period. */
    PERIOD_CLOSED,

    /** The person's role may not make this change. */
    NOT_ALLOWED,

    /** What the change points to does not exist or is already in that state. */
    NO_TARGET,

    /** Something with the same id was already recorded. */
    DUPLICATE,

    /**
     * The result would break a rule of the core, such as a blank name or a menu price of zero.
     * The app checks this before recording, so it comes only from a damaged or hand-made file.
     */
    INVALID,
}

data class Rejection(val operation: Operation, val reason: RejectReason)

/**
 * Everything the operations add up to, at one moment. It depends only on the set of operations,
 * never on the order they arrived in, so two devices with the same operations hold the same state.
 */
data class LedgerState(
    val ingredients: Map<IngredientId, IngredientRecord>,
    /** In the order recorded; on one day the price recorded later wins. */
    val prices: Map<String, PriceRecord>,
    val recipes: Map<RecipeId, RecipeRecord>,
    val versions: Map<RecipeId, Map<LocalDate, RecipeVersion>>,
    val menuPrices: Map<RecipeId, Map<LocalDate, Long>>,
    val costing: CostingRules,
    val pricing: PricingSettings,
    val lockedThrough: LocalDate?,
    /** Periods closed now, each with its official figures. */
    val closedPeriods: List<ClosedPeriod>,
    /** Operations that were recorded but not applied, with the reason. */
    val rejections: List<Rejection>,
    val appliedIds: Set<OpId>,
    val lastAt: Hlc?,
) {
    val periodLock: PeriodLock get() = PeriodLock(lockedThrough)

    /** The core's view of the data, ready for costing. */
    val catalog: Catalog by lazy {
        Catalog(
            ingredients.values.mapNotNull { it.toIngredient() },
            prices.values.filter { !it.voided }.map { it.price },
            recipes.values.mapNotNull { it.toRecipe(versions[it.id].orEmpty().values) },
            menuPrices.flatMap { (id, byDate) -> byDate.map { (date, rials) -> MenuPrice(id, date, rials) } },
        )
    }

    fun calculator(): FoodCostCalculator = FoodCostCalculator(catalog, costing, pricing)

    companion object {
        val EMPTY = LedgerState(
            emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(),
            CostingRules(), PricingSettings(), null, emptyList(), emptyList(), emptySet(), null,
        )

        /** Applies [operations] in [Operation.ORDER]; duplicates count once. */
        fun fold(operations: Collection<Operation>): LedgerState {
            val builder = StateBuilder(EMPTY)
            operations.sortedWith(Operation.ORDER).forEach { builder.add(it) }
            return builder.build()
        }
    }
}

/** Applies operations one at a time. They must come in [Operation.ORDER]. */
internal class StateBuilder(from: LedgerState) {
    private val ingredients = LinkedHashMap(from.ingredients)
    private val prices = LinkedHashMap(from.prices)
    private val recipes = LinkedHashMap(from.recipes)
    private val versions = HashMap<RecipeId, TreeMap<LocalDate, RecipeVersion>>()
        .apply { from.versions.forEach { (id, byDate) -> put(id, TreeMap(byDate)) } }
    private val menuPrices = HashMap<RecipeId, TreeMap<LocalDate, Long>>()
        .apply { from.menuPrices.forEach { (id, byDate) -> put(id, TreeMap(byDate)) } }
    private var costing = from.costing
    private var pricing = from.pricing
    private var lockedThrough = from.lockedThrough
    private val closedPeriods = ArrayList(from.closedPeriods)
    private val rejections = ArrayList(from.rejections)
    private val appliedIds = HashSet(from.appliedIds)
    private var lastAt = from.lastAt

    /** Applies [op]; returns why it was rejected, or null. An operation already applied changes nothing. */
    fun add(op: Operation): RejectReason? {
        if (!appliedIds.add(op.id)) return null
        if (lastAt == null || op.at > lastAt!!) lastAt = op.at
        val reason = rejectionFor(op) ?: applyChange(op)
        if (reason != null) rejections += Rejection(op, reason)
        return reason
    }

    /**
     * Applies the change of an allowed operation. If the result would break a rule of the core,
     * nothing changes and the answer is [RejectReason.INVALID], so the catalog can always be built.
     */
    private fun applyChange(op: Operation): RejectReason? {
        when (val c = op.change) {
            is Change.IngredientSaved -> {
                val old = ingredients[c.id]
                    ?: IngredientRecord(c.id, null, null, null, emptyList(), Conversions(), false, op.at, op.at)
                val new = old.copy(
                    name = c.name.valueOr(old.name),
                    baseDimension = c.baseDimension.valueOr(old.baseDimension),
                    category = c.category.valueOr(old.category),
                    yields = c.yields.valueOr(old.yields),
                    conversions = c.conversions.valueOr(old.conversions),
                    archived = c.archived.valueOr(old.archived),
                    updatedAt = op.at,
                )
                if (new.name?.isBlank() == true || !followsCoreRules { new.toIngredient() }) return RejectReason.INVALID
                ingredients[c.id] = new
            }
            is Change.PriceRecorded -> prices[c.priceId] = PriceRecord(c.priceId, c.price, op.at, voided = false)
            is Change.PriceVoided -> prices[c.priceId] = prices.getValue(c.priceId).copy(voided = true)
            is Change.RecipeSaved -> {
                val old = recipes[c.id] ?: RecipeRecord(c.id, null, null, null, null, false, op.at, op.at)
                val new = old.copy(
                    name = c.name.valueOr(old.name),
                    kind = c.kind.valueOr(old.kind),
                    category = c.category.valueOr(old.category),
                    pricing = c.pricing.valueOr(old.pricing),
                    archived = c.archived.valueOr(old.archived),
                    updatedAt = op.at,
                )
                if (new.name?.isBlank() == true) return RejectReason.INVALID
                recipes[c.id] = new
            }
            is Change.RecipeVersionSaved -> versions.getOrPut(c.recipeId) { TreeMap() }[c.version.validFrom] = c.version
            is Change.RecipeVersionRemoved -> versions.getValue(c.recipeId).remove(c.validFrom)
            is Change.MenuPriceSet -> {
                if (!followsCoreRules { MenuPrice(c.recipeId, c.effectiveFrom, c.rials) }) return RejectReason.INVALID
                menuPrices.getOrPut(c.recipeId) { TreeMap() }[c.effectiveFrom] = c.rials
            }
            is Change.MenuPriceRemoved -> menuPrices.getValue(c.recipeId).remove(c.effectiveFrom)
            is Change.SettingsSaved -> {
                c.costing?.let { costing = it }
                c.pricing?.let { pricing = it }
            }
            is Change.PeriodClosed -> {
                lockedThrough = c.through
                closedPeriods += ClosedPeriod(c.through, c.snapshot, op.at, op.actor)
            }
            is Change.PeriodReopened -> {
                closedPeriods.removeAll { !it.through.isBefore(c.from) }
                lockedThrough = closedPeriods.maxOfOrNull { it.through }
            }
        }
        return null
    }

    private inline fun followsCoreRules(build: () -> Any?): Boolean = try {
        build()
        true
    } catch (e: IllegalArgumentException) {
        false
    }

    private fun rejectionFor(op: Operation): RejectReason? {
        val c = op.change
        val managersOnly = c is Change.MenuPriceSet || c is Change.MenuPriceRemoved || c is Change.SettingsSaved ||
            c is Change.PeriodClosed || c is Change.PeriodReopened
        if (managersOnly && op.actor.role == Role.COOK) return RejectReason.NOT_ALLOWED

        // What a change points to was always created earlier: a device can only refer to an ingredient
        // or a recipe it already has, and its clock is then past the operation that created it.
        when (c) {
            is Change.PriceRecorded -> when {
                !ingredients.containsKey(c.price.ingredientId) -> return RejectReason.NO_TARGET
                prices.containsKey(c.priceId) -> return RejectReason.DUPLICATE
            }
            is Change.RecipeVersionSaved -> if (!recipes.containsKey(c.recipeId)) return RejectReason.NO_TARGET
            is Change.MenuPriceSet -> if (!recipes.containsKey(c.recipeId)) return RejectReason.NO_TARGET
            is Change.PriceVoided -> if (prices[c.priceId]?.voided != false) return RejectReason.NO_TARGET
            is Change.RecipeVersionRemoved ->
                if (versions[c.recipeId]?.containsKey(c.validFrom) != true) return RejectReason.NO_TARGET
            is Change.MenuPriceRemoved ->
                if (menuPrices[c.recipeId]?.containsKey(c.effectiveFrom) != true) return RejectReason.NO_TARGET
            is Change.PeriodClosed -> {
                val locked = lockedThrough
                if (locked != null && !c.through.isAfter(locked)) return RejectReason.NO_TARGET
            }
            is Change.PeriodReopened -> {
                val locked = lockedThrough
                if (locked == null || c.from.isAfter(locked)) return RejectReason.NO_TARGET
            }
            else -> {}
        }

        val day: LocalDate? = when (c) {
            is Change.PriceRecorded -> c.price.effectiveFrom
            is Change.PriceVoided -> prices.getValue(c.priceId).price.effectiveFrom
            is Change.RecipeVersionSaved -> c.version.validFrom
            is Change.RecipeVersionRemoved -> c.validFrom
            is Change.MenuPriceSet -> c.effectiveFrom
            is Change.MenuPriceRemoved -> c.effectiveFrom
            else -> null
        }
        val locked = lockedThrough
        if (day != null && locked != null && !day.isAfter(locked)) return RejectReason.PERIOD_CLOSED
        return null
    }

    fun build(): LedgerState = LedgerState(
        ingredients = LinkedHashMap(ingredients),
        prices = LinkedHashMap(prices),
        recipes = LinkedHashMap(recipes),
        versions = versions.mapValues { (_, byDate) -> TreeMap(byDate) },
        menuPrices = menuPrices.mapValues { (_, byDate) -> TreeMap(byDate) },
        costing = costing,
        pricing = pricing,
        lockedThrough = lockedThrough,
        closedPeriods = closedPeriods.toList(),
        rejections = rejections.toList(),
        appliedIds = HashSet(appliedIds),
        lastAt = lastAt,
    )
}
