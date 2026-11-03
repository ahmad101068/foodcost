package ir.foodcost.data.ledger

import ir.foodcost.core.FoodCostCalculator
import ir.foodcost.core.costing.CostingRules
import ir.foodcost.core.model.ComponentRef
import ir.foodcost.core.model.Ingredient
import ir.foodcost.core.model.IngredientId
import ir.foodcost.core.model.MenuPrice
import ir.foodcost.core.model.PurchasePrice
import ir.foodcost.core.model.RecipeId
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.model.RecipeVersion
import ir.foodcost.core.model.YieldStage
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.core.pricing.PricingSettings
import ir.foodcost.core.reports.MenuSnapshot
import ir.foodcost.core.units.Conversions
import ir.foodcost.core.units.Dimension
import ir.foodcost.data.clock.HybridClock
import ir.foodcost.data.ops.Actor
import ir.foodcost.data.ops.Backup
import ir.foodcost.data.ops.Change
import ir.foodcost.data.ops.OpId
import ir.foodcost.data.ops.Operation
import ir.foodcost.data.ops.Opt
import ir.foodcost.data.ops.Role
import ir.foodcost.data.store.OperationStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** What a backup would add, for a look before importing it. */
data class BackupPreview(
    /** Changes not here yet; the rest are here already and change nothing. */
    val fresh: Int,
    val ingredients: Int,
    val prices: Int,
    val recipes: Int,
    val menuPrices: Int,
    val settings: Int,
    val periods: Int,
    /** The first and last day the new changes were made on. */
    val firstDay: LocalDate?,
    val lastDay: LocalDate?,
    /** Phones other than this one that made them. */
    val otherDevices: Int,
    /** New changes made by someone other than an owner. */
    val byOthers: Int,
    /** New changes the rules refuse, such as a price on a closed day; they are kept but change nothing. */
    val refused: Int,
)

/**
 * The app's way to read and change data. Every change becomes an [Operation] that is checked,
 * stored and applied; nothing is ever overwritten, so the log doubles as history and backup.
 * Works fully offline; syncing is just exchanging operations with [receive].
 */
class Ledger(
    private val store: OperationStore,
    private val clock: HybridClock,
    val actor: Actor,
) {
    /** The current state; replaced, never mutated, after each change. */
    @Volatile
    var state: LedgerState = LedgerState.fold(store.all())
        private set

    init {
        // New local operations must sort after everything already stored.
        state.lastAt?.let(clock::receive)
    }

    sealed interface Outcome {
        val operation: Operation

        data class Applied(override val operation: Operation) : Outcome

        /** Not stored: the change would break a rule, such as editing a closed period. */
        data class Rejected(override val operation: Operation, val reason: RejectReason) : Outcome
    }

    /** Operations made inside [atomically] and not stored yet. */
    private var pending: MutableList<Operation>? = null

    /**
     * Checks [change] against the current state, then stores and applies it, or rejects it without
     * storing. Inside [atomically] it is stored with the others when the block ends.
     */
    @Synchronized
    fun execute(change: Change, reason: String? = null): Outcome {
        val op = Operation(OpId.random(), clock.now(), actor, change, reason?.takeIf { it.isNotBlank() })
        val builder = StateBuilder(state)
        builder.add(op)?.let { return Outcome.Rejected(op, it) }
        val batch = pending
        if (batch != null) batch += op else store.append(op)
        state = builder.build()
        return Outcome.Applied(op)
    }

    /**
     * Runs [block] as one change of several steps, such as a dish saved with its new ingredients and
     * prices: what it makes is stored in one write when it returns, and nothing of it is stored if it
     * throws, so a step refused half way leaves no part behind. While it runs, [state] shows each step.
     * A step that comes back [Outcome.Rejected] throws nothing by itself; throw to undo the rest.
     */
    @Synchronized
    fun <T> atomically(block: (Ledger) -> T): T {
        if (pending != null) return block(this) // already one change
        val before = state
        val batch = ArrayList<Operation>()
        pending = batch
        try {
            val result = block(this)
            if (batch.isNotEmpty()) store.appendAll(batch)
            return result
        } catch (e: Throwable) {
            state = before
            throw e
        } finally {
            pending = null
        }
    }

    /**
     * Takes operations from another device or a backup. Operations already here are ignored, so
     * receiving the same batch twice changes nothing. Returns how many were new.
     */
    @Synchronized
    fun receive(operations: Collection<Operation>): Int {
        check(pending == null) { "Operations received are stored at once, not as part of another change" }
        val fresh = operations.distinctBy { it.id }.filter { !store.contains(it.id) }
        if (fresh.isEmpty()) return 0
        store.appendAll(fresh)
        clock.receive(fresh.maxOf { it.at })
        val last = state.lastAt
        state = if (last == null || fresh.all { it.at > last }) {
            StateBuilder(state).also { b -> fresh.sortedWith(Operation.ORDER).forEach { b.add(it) } }.build()
        } else {
            // Something older arrived late: apply everything again in order.
            LedgerState.fold(store.all())
        }
        return fresh.size
    }

    /** Every stored operation, for syncing or backup. */
    fun operations(): List<Operation> = store.all()

    /**
     * What receiving [operations] would add, without adding anything: a backup is a file someone
     * chose, and its changes, their days, phones and people are worth a look before they join the data.
     */
    @Synchronized
    fun preview(operations: Collection<Operation>, zone: ZoneId = ZoneId.systemDefault()): BackupPreview {
        val fresh = operations.distinctBy { it.id }.filter { !store.contains(it.id) }
        val after = if (fresh.isEmpty()) state else LedgerState.fold(store.all() + fresh)
        val days = fresh.map { Instant.ofEpochMilli(it.at.wallMillis).atZone(zone).toLocalDate() }
        fun count(kind: (Change) -> Boolean) = fresh.count { kind(it.change) }
        return BackupPreview(
            fresh = fresh.size,
            ingredients = count { it is Change.IngredientSaved },
            prices = count { it is Change.PriceRecorded || it is Change.PriceVoided },
            recipes = count { it is Change.RecipeSaved || it is Change.RecipeVersionSaved || it is Change.RecipeVersionRemoved },
            menuPrices = count { it is Change.MenuPriceSet || it is Change.MenuPriceRemoved },
            settings = count { it is Change.SettingsSaved },
            periods = count { it is Change.PeriodClosed || it is Change.PeriodReopened },
            firstDay = days.minOrNull(),
            lastDay = days.maxOrNull(),
            otherDevices = fresh.map { it.at.device }.distinct().count { it != clock.device },
            byOthers = fresh.count { it.actor.role != Role.OWNER },
            refused = (after.rejections.size - state.rejections.size).coerceAtLeast(0),
        )
    }

    fun calculator(): FoodCostCalculator = state.calculator()

    /** The whole log as one JSON text; importing it on any device rebuilds exactly this data. */
    fun exportBackup(): String = Backup.write(store.all())

    /** Adds the operations of a backup. Importing the same backup twice changes nothing. Returns how many were new. */
    fun importBackup(text: String): Int = receive(Backup.read(text))

    /** Every operation about an ingredient, its prices included, oldest first. */
    fun history(id: IngredientId): List<Operation> = sortedOperations().filter {
        when (val c = it.change) {
            is Change.IngredientSaved -> c.id == id
            is Change.PriceRecorded -> c.price.ingredientId == id
            is Change.PriceVoided -> state.prices[c.priceId]?.price?.ingredientId == id
            else -> false
        }
    }

    /** Every operation about a recipe, its versions and menu prices included, oldest first. */
    fun history(id: RecipeId): List<Operation> = sortedOperations().filter {
        when (val c = it.change) {
            is Change.RecipeSaved -> c.id == id
            is Change.RecipeVersionSaved -> c.recipeId == id
            is Change.RecipeVersionRemoved -> c.recipeId == id
            is Change.MenuPriceSet -> c.recipeId == id
            is Change.MenuPriceRemoved -> c.recipeId == id
            else -> false
        }
    }

    private fun sortedOperations(): List<Operation> = store.all().sortedWith(Operation.ORDER)

    fun createIngredient(
        name: String,
        baseDimension: Dimension,
        category: String? = null,
        yields: List<YieldStage> = emptyList(),
        conversions: Conversions = Conversions(),
    ): IngredientId {
        require(name.isNotBlank()) { "An ingredient needs a name" }
        val id = IngredientId(newId())
        Ingredient(id, name, baseDimension, yields, conversions, category) // the core's own checks
        execute(
            Change.IngredientSaved(
                id, Opt.Value(name.trim()), Opt.Value(baseDimension), Opt.Value(category), Opt.Value(yields),
                Opt.Value(conversions), Opt.Value(false),
            ),
        ).requireApplied()
        return id
    }

    /**
     * Changes an ingredient. A new yield or conversions count from [effectiveFrom] on, and the days
     * before keep the old ones; without a day they count on every day, which a closed period refuses.
     */
    fun updateIngredient(
        id: IngredientId,
        name: Opt<String> = Opt.Keep,
        category: Opt<String?> = Opt.Keep,
        yields: Opt<List<YieldStage>> = Opt.Keep,
        conversions: Opt<Conversions> = Opt.Keep,
        archived: Opt<Boolean> = Opt.Keep,
        effectiveFrom: LocalDate? = null,
    ): Outcome {
        require(state.ingredients.containsKey(id)) { "Unknown ingredient $id" }
        require(name !is Opt.Value || name.value.isNotBlank()) { "An ingredient needs a name" }
        return execute(Change.IngredientSaved(id, name, Opt.Keep, category, yields, conversions, archived, effectiveFrom))
    }

    /** Adds a purchase price. Pass your own [priceId] to refer to it later, for example to void it. */
    fun recordPrice(price: PurchasePrice, priceId: String = newId()): Outcome {
        require(state.ingredients.containsKey(price.ingredientId)) { "Unknown ingredient ${price.ingredientId}" }
        return execute(Change.PriceRecorded(priceId, price))
    }

    fun voidPrice(priceId: String, reason: String? = null): Outcome = execute(Change.PriceVoided(priceId), reason)

    fun createRecipe(name: String, kind: RecipeKind, category: String? = null, pricing: PricingMethod? = null): RecipeId {
        require(name.isNotBlank()) { "A recipe needs a name" }
        val id = RecipeId(newId())
        execute(
            Change.RecipeSaved(id, Opt.Value(name.trim()), Opt.Value(kind), Opt.Value(category), Opt.Value(pricing), Opt.Value(false)),
        ).requireApplied()
        return id
    }

    /** Changes a recipe. A new section or pricing method counts from [effectiveFrom] on, as for [updateIngredient]. */
    fun updateRecipe(
        id: RecipeId,
        name: Opt<String> = Opt.Keep,
        kind: Opt<RecipeKind> = Opt.Keep,
        category: Opt<String?> = Opt.Keep,
        pricing: Opt<PricingMethod?> = Opt.Keep,
        archived: Opt<Boolean> = Opt.Keep,
        effectiveFrom: LocalDate? = null,
    ): Outcome {
        require(state.recipes.containsKey(id)) { "Unknown recipe $id" }
        require(name !is Opt.Value || name.value.isNotBlank()) { "A recipe needs a name" }
        return execute(Change.RecipeSaved(id, name, kind, category, pricing, archived, effectiveFrom))
    }

    fun saveRecipeVersion(id: RecipeId, version: RecipeVersion): Outcome {
        require(state.recipes.containsKey(id)) { "Unknown recipe $id" }
        version.lines.forEach { line ->
            when (val c = line.component) {
                is ComponentRef.IngredientRef -> require(state.ingredients.containsKey(c.id)) { "Unknown ingredient ${c.id}" }
                is ComponentRef.RecipeRef -> require(state.recipes.containsKey(c.id)) { "Unknown recipe ${c.id}" }
            }
        }
        return execute(Change.RecipeVersionSaved(id, version))
    }

    fun removeRecipeVersion(id: RecipeId, validFrom: LocalDate): Outcome = execute(Change.RecipeVersionRemoved(id, validFrom))

    fun setMenuPrice(id: RecipeId, effectiveFrom: LocalDate, rials: Long): Outcome {
        require(state.recipes.containsKey(id)) { "Unknown recipe $id" }
        MenuPrice(id, effectiveFrom, rials) // the core's own checks
        return execute(Change.MenuPriceSet(id, effectiveFrom, rials))
    }

    fun removeMenuPrice(id: RecipeId, effectiveFrom: LocalDate): Outcome = execute(Change.MenuPriceRemoved(id, effectiveFrom))

    /** Saves settings that count from [effectiveFrom] on; without a day, on every day, which a closed period refuses. */
    fun saveSettings(costing: CostingRules? = null, pricing: PricingSettings? = null, effectiveFrom: LocalDate? = null): Outcome =
        execute(Change.SettingsSaved(costing, pricing, effectiveFrom))

    /** Closes every day up to [through] and keeps that day's menu figures as the period's official record. */
    fun closePeriod(through: LocalDate): Outcome =
        execute(Change.PeriodClosed(through, MenuSnapshot.of(calculator().menu(through))))

    /** Reopens closed periods from [from] on. Managers only, and the reason is kept in the history. */
    fun reopenPeriod(from: LocalDate, reason: String): Outcome {
        require(reason.isNotBlank()) { "Reopening a period needs a reason" }
        return execute(Change.PeriodReopened(from), reason)
    }

    private fun Outcome.requireApplied() {
        check(this is Outcome.Applied) { "Unexpected rejection: $this" }
    }

    private fun newId(): String = UUID.randomUUID().toString()
}
