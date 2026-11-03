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
import ir.foodcost.data.ops.Opt
import ir.foodcost.data.ops.Role
import ir.foodcost.data.ops.valueOr
import java.time.LocalDate
import java.util.TreeMap

/**
 * A value one change set, counting from [from] on; null counts it on every day. Of two that count
 * from the same day, the one recorded later ([at]) wins, as with prices.
 */
data class Dated<T>(val from: LocalDate?, val at: Hlc, val value: T) {
    internal val day: LocalDate get() = from ?: LocalDate.MIN
}

private fun <T> latestFirst(): Comparator<Dated<T>> = compareBy<Dated<T>>({ it.day }, { it.at })

/** The value in force on [date]: the one that counts from the latest day on or before it. */
fun <T> List<Dated<T>>.inForce(date: LocalDate): Dated<T>? = filter { !it.day.isAfter(date) }.maxWithOrNull(latestFirst())

/** The value in force from the last day any of them counts from. */
fun <T> List<Dated<T>>.latest(): Dated<T>? = maxWithOrNull(latestFirst())

/**
 * Whether [value] counting from [from] changes the value of any day: it does when it differs from
 * the one in force on that day, which stays in force until the next day a change counts from.
 */
internal fun <T> List<Dated<T>>.changedBy(value: T, from: LocalDate?, default: T): Boolean =
    (inForce(from ?: LocalDate.MIN)?.value ?: default) != value

data class IngredientRecord(
    val id: IngredientId,
    val name: String?,
    val baseDimension: Dimension?,
    val category: String?,
    /** The yield in force now; [yieldsHistory] has the days each one counted from. */
    val yields: List<YieldStage>,
    val conversions: Conversions,
    /** Hidden from pickers but still costed in the recipes that use it. */
    val archived: Boolean,
    val createdAt: Hlc,
    val updatedAt: Hlc,
    val yieldsHistory: List<Dated<List<YieldStage>>> = emptyList(),
    val conversionsHistory: List<Dated<Conversions>> = emptyList(),
) {
    /** The core model, once the name and the base dimension are known. */
    fun toIngredient(): Ingredient? {
        val n = name ?: return null
        val dimension = baseDimension ?: return null
        return Ingredient(id, n, dimension, yields, conversions, category)
    }

    /** As it was on [date]: the yield and conversions in force that day. */
    fun asOf(date: LocalDate): IngredientRecord = copy(
        yields = yieldsHistory.inForce(date)?.value ?: emptyList(),
        conversions = conversionsHistory.inForce(date)?.value ?: Conversions(),
    )
}

data class RecipeRecord(
    val id: RecipeId,
    val name: String?,
    val kind: RecipeKind?,
    /** The menu section in force now; [categoryHistory] has the days each one counted from. */
    val category: String?,
    val pricing: PricingMethod?,
    val archived: Boolean,
    val createdAt: Hlc,
    val updatedAt: Hlc,
    val categoryHistory: List<Dated<String?>> = emptyList(),
    val pricingHistory: List<Dated<PricingMethod?>> = emptyList(),
) {
    fun toRecipe(versions: Collection<RecipeVersion>): Recipe? {
        val n = name ?: return null
        val k = kind ?: return null
        return Recipe(id, n, k, versions.toList(), category, pricing)
    }

    /** As it was on [date]: the section and pricing method in force that day. */
    fun asOf(date: LocalDate): RecipeRecord = copy(
        category = categoryHistory.inForce(date)?.value,
        pricing = pricingHistory.inForce(date)?.value,
    )
}

data class PriceRecord(val priceId: String, val price: PurchasePrice, val recordedAt: Hlc, val voided: Boolean)

data class ClosedPeriod(
    val through: LocalDate,
    val snapshot: MenuSnapshot?,
    val closedAt: Hlc,
    val closedBy: Actor,
    /** For each device, the latest of its changes the close had; null for closes that did not keep it. */
    val seen: Map<String, Hlc>? = null,
) {
    /**
     * Whether the close had [op] when it was made. A change it had not seen, recorded after it or
     * on a phone that had not heard of it, stays out of the days it closed.
     */
    fun saw(op: Operation): Boolean {
        if (op.at > closedAt) return false
        val known = seen ?: return true // a close that did not keep what it had seen counts everything before it
        return known[op.at.device]?.let { op.at <= it } == true
    }
}

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
    /** The costing rules with the days each counted from; [costing] is the one in force now. */
    val costingHistory: List<Dated<CostingRules>> = emptyList(),
    val pricingHistory: List<Dated<PricingSettings>> = emptyList(),
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

    /** Works out each day on the yields, sections and settings in force that day. */
    fun calculator(): FoodCostCalculator = FoodCostCalculator(catalog, costing, pricing, earlier = ::termsAt)

    /** The last day a change to a yield, a conversion, a section, a pricing method or a setting counts from. */
    private val lastTermsDay: LocalDate? by lazy {
        val days = ArrayList<LocalDate>()
        ingredients.values.forEach { r ->
            r.yieldsHistory.mapNotNullTo(days) { it.from }
            r.conversionsHistory.mapNotNullTo(days) { it.from }
        }
        recipes.values.forEach { r ->
            r.categoryHistory.mapNotNullTo(days) { it.from }
            r.pricingHistory.mapNotNullTo(days) { it.from }
        }
        costingHistory.mapNotNullTo(days) { it.from }
        pricingHistory.mapNotNullTo(days) { it.from }
        days.maxOrNull()
    }

    /** The catalog and settings in force on [date], or null when they are the ones in force now. */
    private fun termsAt(date: LocalDate): FoodCostCalculator.Terms? {
        val last = lastTermsDay ?: return null
        if (!date.isBefore(last)) return null
        val then = Catalog(
            ingredients.values.mapNotNull { it.asOf(date).toIngredient() },
            prices.values.filter { !it.voided }.map { it.price },
            recipes.values.mapNotNull { it.asOf(date).toRecipe(versions[it.id].orEmpty().values) },
            menuPrices.flatMap { (id, byDate) -> byDate.map { (day, rials) -> MenuPrice(id, day, rials) } },
        )
        return FoodCostCalculator.Terms(
            then,
            costingHistory.inForce(date)?.value ?: CostingRules(),
            pricingHistory.inForce(date)?.value ?: PricingSettings(),
        )
    }

    companion object {
        val EMPTY = LedgerState(
            emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(),
            CostingRules(), PricingSettings(), null, emptyList(), emptyList(), emptySet(), null,
        )

        /**
         * Applies [operations] in [Operation.ORDER]; duplicates count once. The periods still closed
         * at the end are worked out first, from the closes and reopenings alone, so that a change
         * the close of its day had not seen stays out of that day wherever it sits in the order.
         */
        fun fold(operations: Collection<Operation>): LedgerState {
            val sorted = operations.sortedWith(Operation.ORDER)
            val closes = StateBuilder(EMPTY).apply { sorted.filter { it.change.isPeriodChange }.forEach { add(it) } }.build().closedPeriods
            val builder = StateBuilder(EMPTY, closes)
            sorted.forEach { builder.add(it) }
            return builder.build()
        }
    }
}

/** Whether the change closes or reopens a period. */
internal val Change.isPeriodChange: Boolean get() = this is Change.PeriodClosed || this is Change.PeriodReopened

/**
 * Applies operations one at a time. They must come in [Operation.ORDER]. [finalCloses] are the
 * periods still closed once every operation is applied, when they are known ahead; without them
 * the periods closed so far count, which is the same when no close or reopening comes later.
 */
internal class StateBuilder(from: LedgerState, private val finalCloses: List<ClosedPeriod>? = null) {
    private val ingredients = LinkedHashMap(from.ingredients)
    private val prices = LinkedHashMap(from.prices)
    private val recipes = LinkedHashMap(from.recipes)
    private val versions = HashMap<RecipeId, TreeMap<LocalDate, RecipeVersion>>()
        .apply { from.versions.forEach { (id, byDate) -> put(id, TreeMap(byDate)) } }
    private val menuPrices = HashMap<RecipeId, TreeMap<LocalDate, Long>>()
        .apply { from.menuPrices.forEach { (id, byDate) -> put(id, TreeMap(byDate)) } }
    private var costing = from.costing
    private var pricing = from.pricing
    private val costingHistory = ArrayList(from.costingHistory)
    private val pricingHistory = ArrayList(from.pricingHistory)
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
                val yieldsHistory = c.yields.dated(old.yieldsHistory, c.effectiveFrom, op, emptyList())
                val conversionsHistory = c.conversions.dated(old.conversionsHistory, c.effectiveFrom, op, Conversions())
                val new = old.copy(
                    name = c.name.valueOr(old.name),
                    baseDimension = c.baseDimension.valueOr(old.baseDimension),
                    category = c.category.valueOr(old.category),
                    yields = yieldsHistory.latest()?.value ?: emptyList(),
                    conversions = conversionsHistory.latest()?.value ?: Conversions(),
                    archived = c.archived.valueOr(old.archived),
                    updatedAt = op.at,
                    yieldsHistory = yieldsHistory,
                    conversionsHistory = conversionsHistory,
                )
                // The new yield and conversions must follow the core's rules even if a later day's are in force now.
                val asSet = new.copy(yields = c.yields.valueOr(new.yields), conversions = c.conversions.valueOr(new.conversions))
                if (new.name?.isBlank() == true || !followsCoreRules { new.toIngredient() } || !followsCoreRules { asSet.toIngredient() }) {
                    return RejectReason.INVALID
                }
                ingredients[c.id] = new
            }
            is Change.PriceRecorded -> prices[c.priceId] = PriceRecord(c.priceId, c.price, op.at, voided = false)
            is Change.PriceVoided -> prices[c.priceId] = prices.getValue(c.priceId).copy(voided = true)
            is Change.RecipeSaved -> {
                val old = recipes[c.id] ?: RecipeRecord(c.id, null, null, null, null, false, op.at, op.at)
                val categoryHistory = c.category.dated(old.categoryHistory, c.effectiveFrom, op, null)
                val pricingHistory = c.pricing.dated(old.pricingHistory, c.effectiveFrom, op, null)
                val new = old.copy(
                    name = c.name.valueOr(old.name),
                    kind = c.kind.valueOr(old.kind),
                    category = categoryHistory.latest()?.value,
                    pricing = pricingHistory.latest()?.value,
                    archived = c.archived.valueOr(old.archived),
                    updatedAt = op.at,
                    categoryHistory = categoryHistory,
                    pricingHistory = pricingHistory,
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
                c.costing?.takeIf { costingHistory.changedBy(it, c.effectiveFrom, CostingRules()) }
                    ?.let { costingHistory += Dated(c.effectiveFrom, op.at, it) }
                c.pricing?.takeIf { pricingHistory.changedBy(it, c.effectiveFrom, PricingSettings()) }
                    ?.let { pricingHistory += Dated(c.effectiveFrom, op.at, it) }
                costing = costingHistory.latest()?.value ?: CostingRules()
                pricing = pricingHistory.latest()?.value ?: PricingSettings()
            }
            is Change.PeriodClosed -> {
                lockedThrough = c.through
                closedPeriods += ClosedPeriod(c.through, c.snapshot, op.at, op.actor, c.seen)
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

    /** [history] with this value added as of [from], when the change sets it and it changes some day's value. */
    private fun <T> Opt<T>.dated(history: List<Dated<T>>, from: LocalDate?, op: Operation, default: T): List<Dated<T>> =
        if (this is Opt.Value && history.changedBy(value, from, default)) history + Dated(from, op.at, value) else history

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

        // A new yield, conversion, section, pricing method or setting counts from its day on, or on
        // every day without one: on a closed day it would change the official figures.
        val termsDay: LocalDate? = when (c) {
            is Change.IngredientSaved -> ingredients[c.id]?.let { old ->
                val from = c.effectiveFrom
                val changes = (c.yields is Opt.Value && old.yieldsHistory.changedBy(c.yields.value, from, emptyList())) ||
                    (c.conversions is Opt.Value && old.conversionsHistory.changedBy(c.conversions.value, from, Conversions()))
                if (changes) from ?: LocalDate.MIN else null
            }
            is Change.RecipeSaved -> recipes[c.id]?.let { old ->
                val from = c.effectiveFrom
                val changes = (c.category is Opt.Value && old.categoryHistory.changedBy(c.category.value, from, null)) ||
                    (c.pricing is Opt.Value && old.pricingHistory.changedBy(c.pricing.value, from, null))
                if (changes) from ?: LocalDate.MIN else null
            }
            is Change.SettingsSaved -> {
                val from = c.effectiveFrom
                val changes = c.costing?.let { costingHistory.changedBy(it, from, CostingRules()) } == true ||
                    c.pricing?.let { pricingHistory.changedBy(it, from, PricingSettings()) } == true
                if (changes) from ?: LocalDate.MIN else null
            }
            else -> null
        }
        val day: LocalDate? = termsDay ?: when (c) {
            is Change.PriceRecorded -> c.price.effectiveFrom
            is Change.PriceVoided -> prices.getValue(c.priceId).price.effectiveFrom
            is Change.RecipeVersionSaved -> c.version.validFrom
            is Change.RecipeVersionRemoved -> c.validFrom
            is Change.MenuPriceSet -> c.effectiveFrom
            is Change.MenuPriceRemoved -> c.effectiveFrom
            else -> null
        }
        // A closed day stays as its close found it: a change for it that the close had not seen,
        // recorded after it or arriving late from a phone that recorded it earlier, stays out
        // until the period is reopened, the same on every phone whatever order things arrive in.
        val closes = finalCloses ?: closedPeriods
        if (day != null && closes.any { !day.isAfter(it.through) && !it.saw(op) }) return RejectReason.PERIOD_CLOSED
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
        costingHistory = costingHistory.toList(),
        pricingHistory = pricingHistory.toList(),
    )
}
