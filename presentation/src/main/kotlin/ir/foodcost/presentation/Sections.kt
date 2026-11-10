package ir.foodcost.presentation

import ir.foodcost.data.ledger.LedgerState

/** The menu sections a dish can be put in. */
object Sections {
    /**
     * Names from settings (including legacy category names), then any other section used by
     * existing dishes. These labels have no pricing effect: all dishes use the general method.
     */
    fun of(state: LedgerState): List<String> {
        val settings = state.pricing.sections
        val inUse = state.recipes.values
            .filter { !it.archived }
            .mapNotNull { it.category?.trim()?.takeIf(String::isNotEmpty) }
            .filter { it !in settings }
            .distinct()
            .sortedWith(compareBy { Persian.searchKey(it) })
        return (settings + inUse).distinct()
    }
}
