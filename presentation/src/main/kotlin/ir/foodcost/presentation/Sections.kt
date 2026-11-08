package ir.foodcost.presentation

import ir.foodcost.data.ledger.LedgerState

/** The menu sections a dish can be put in. */
object Sections {
    /**
     * Those of the settings, with a pricing method of their own or priced with the general one, then
     * any other section a dish on the menu is already in, such as one typed before sections were
     * chosen from the settings: each once, in that order.
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
