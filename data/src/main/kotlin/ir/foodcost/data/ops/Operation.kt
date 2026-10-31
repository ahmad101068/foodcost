package ir.foodcost.data.ops

import ir.foodcost.core.costing.CostingRules
import ir.foodcost.core.model.IngredientId
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
import ir.foodcost.data.clock.Hlc
import java.time.LocalDate
import java.util.UUID

@JvmInline
value class OpId(val value: String) {
    companion object {
        fun random(): OpId = OpId(UUID.randomUUID().toString())
    }
}

/** Who may do what. Menu prices, settings and closing periods are for owners and managers. */
enum class Role { OWNER, MANAGER, COOK }

data class Actor(val id: String, val role: Role)

/**
 * One recorded change, and the only way data changes. Operations are never edited or deleted;
 * a mistake is fixed with a new operation, so the log is also the audit trail.
 */
data class Operation(
    val id: OpId,
    val at: Hlc,
    val actor: Actor,
    val change: Change,
    val reason: String? = null,
) {
    companion object {
        /** The order every device applies operations in, so every device reaches the same state. */
        val ORDER: Comparator<Operation> = compareBy<Operation> { it.at }.thenBy { it.id.value }
    }
}

/** One field of an update: [Keep] leaves it as it is; [Value] sets it, to null for fields that can be empty. */
sealed interface Opt<out T> {
    data object Keep : Opt<Nothing>

    data class Value<out T>(val value: T) : Opt<T>
}

fun <T> Opt<T>.valueOr(current: T): T = if (this is Opt.Value) value else current

/**
 * Every kind of change. Updates name only the fields they change, so edits made at the same time
 * on two devices to different fields are both kept.
 */
sealed interface Change {
    data class IngredientSaved(
        val id: IngredientId,
        val name: Opt<String> = Opt.Keep,
        val baseDimension: Opt<Dimension> = Opt.Keep,
        val category: Opt<String?> = Opt.Keep,
        val yields: Opt<List<YieldStage>> = Opt.Keep,
        val conversions: Opt<Conversions> = Opt.Keep,
        val archived: Opt<Boolean> = Opt.Keep,
    ) : Change

    /** A purchase price is only ever added; a wrong one is voided and entered again. */
    data class PriceRecorded(val priceId: String, val price: PurchasePrice) : Change

    data class PriceVoided(val priceId: String) : Change

    data class RecipeSaved(
        val id: RecipeId,
        val name: Opt<String> = Opt.Keep,
        val kind: Opt<RecipeKind> = Opt.Keep,
        val category: Opt<String?> = Opt.Keep,
        val pricing: Opt<PricingMethod?> = Opt.Keep,
        val archived: Opt<Boolean> = Opt.Keep,
    ) : Change

    /** Adds the version, or replaces the one that starts on the same day. */
    data class RecipeVersionSaved(val recipeId: RecipeId, val version: RecipeVersion) : Change

    data class RecipeVersionRemoved(val recipeId: RecipeId, val validFrom: LocalDate) : Change

    data class MenuPriceSet(val recipeId: RecipeId, val effectiveFrom: LocalDate, val rials: Long) : Change

    data class MenuPriceRemoved(val recipeId: RecipeId, val effectiveFrom: LocalDate) : Change

    /** Null leaves that part of the settings as it is. */
    data class SettingsSaved(val costing: CostingRules? = null, val pricing: PricingSettings? = null) : Change

    /** Closes every day up to [through]; [snapshot] keeps the official figures of the period. */
    data class PeriodClosed(val through: LocalDate, val snapshot: MenuSnapshot?) : Change

    /** Reopens the closed periods that include [from] or come after it. */
    data class PeriodReopened(val from: LocalDate) : Change
}
