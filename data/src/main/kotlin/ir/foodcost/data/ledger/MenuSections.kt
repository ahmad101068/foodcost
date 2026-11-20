package ir.foodcost.data.ledger

import ir.foodcost.core.pricing.PricingSettings
import ir.foodcost.data.ops.Opt
import java.time.LocalDate

/** A name change keeps each food attached to its section, including already scheduled changes.
 * Uses ordinary dated operations, so old backups, historical reports and closed periods retain
 * their format and figures. A failed step rolls the entire edit back.
 */
fun Ledger.saveMenuSections(pricing: PricingSettings, renames: Map<String, String>, from: LocalDate) = atomically { ledger ->
    val before = ledger.state
    require(renames.keys.all { it in before.pricing.sections }) {
        "بخش‌های منو تغییر کرده‌اند؛ تنظیمات را دوباره باز کنید و تغییر نام را انجام دهید."
    }
    require(renames.values.all { it.isNotBlank() && it in pricing.sections } && renames.values.toSet().size == renames.size) {
        "نام بخش‌های منو باید متفاوت و غیرخالی باشد."
    }
    fun renamed(policy: PricingSettings): PricingSettings {
        val names = policy.sections.map { renames[it] ?: it }
        require(names.distinct().size == names.size) {
            "این نام با یک بخش در تنظیمات تاریخ‌دار آینده تداخل دارد؛ نام دیگری انتخاب کنید."
        }
        return policy.copy(
            methodByCategory = policy.methodByCategory.mapKeys { (name, _) -> renames[name] ?: name },
            generalSections = policy.generalSections.map { renames[it] ?: it },
        )
    }
    // Capture the original snapshots first: chained or swapped names must be mapped once only.
    val futureRules = if (renames.isEmpty()) emptyList() else before.pricingHistory.mapNotNull { it.from }
        .filter { it > from }.distinct().sorted().map { day ->
            day to renamed(before.pricingHistory.inForce(day)!!.value)
        }
    val foodChanges = before.recipes.values.flatMap { recipe ->
        (listOf(from) + recipe.categoryHistory.mapNotNull { it.from }.filter { it > from })
            .distinct().sorted().mapNotNull { day ->
                renames[recipe.asOf(day).category]?.let { Triple(recipe.id, day, it) }
            }
    }
    ledger.saveSettings(pricing = pricing, effectiveFrom = from)
    futureRules.forEach { (day, policy) -> ledger.saveSettings(pricing = policy, effectiveFrom = day) }
    foodChanges.forEach { (id, day, name) -> ledger.updateRecipe(id, category = Opt.Value(name), effectiveFrom = day) }
}
