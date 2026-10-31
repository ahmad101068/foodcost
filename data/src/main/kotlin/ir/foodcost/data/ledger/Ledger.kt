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
import ir.foodcost.data.store.OperationStore
import java.time.LocalDate
import java.util.UUID

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

    /** Checks [change] against the current state, then stores and applies it, or rejects it without storing. */
    @Synchronized
    fun execute(change: Change, reason: String? = null): Outcome {
        val op = Operation(OpId.random(), clock.now(), actor, change, reason?.takeIf { it.isNotBlank() })
        val builder = StateBuilder(state)
        builder.add(op)?.let { return Outcome.Rejected(op, it) }
        store.append(op)
        state = builder.build()
        return Outcome.Applied(op)
    }

    /**
     * Takes operations from another device or a backup. Operations already here are ignored, so
     * receiving the same batch twice changes nothing. Returns how many were new.
     */
    @Synchronized
    fun receive(operations: Collection<Operation>): Int {
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

    fun updateIngredient(
        id: IngredientId,
        name: Opt<String> = Opt.Keep,
        category: Opt<String?> = Opt.Keep,
        yields: Opt<List<YieldStage>> = Opt.Keep,
        conversions: Opt<Conversions> = Opt.Keep,
        archived: Opt<Boolean> = Opt.Keep,
    ): Outcome {
        require(state.ingredients.containsKey(id)) { "Unknown ingredient $id" }
        require(name !is Opt.Value || name.value.isNotBlank()) { "An ingredient needs a name" }
        return execute(Change.IngredientSaved(id, name, Opt.Keep, category, yields, conversions, archived))
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

    fun updateRecipe(
        id: RecipeId,
        name: Opt<String> = Opt.Keep,
        kind: Opt<RecipeKind> = Opt.Keep,
        category: Opt<String?> = Opt.Keep,
        pricing: Opt<PricingMethod?> = Opt.Keep,
        archived: Opt<Boolean> = Opt.Keep,
    ): Outcome {
        require(state.recipes.containsKey(id)) { "Unknown recipe $id" }
        require(name !is Opt.Value || name.value.isNotBlank()) { "A recipe needs a name" }
        return execute(Change.RecipeSaved(id, name, kind, category, pricing, archived))
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

    fun saveSettings(costing: CostingRules? = null, pricing: PricingSettings? = null): Outcome =
        execute(Change.SettingsSaved(costing, pricing))

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
