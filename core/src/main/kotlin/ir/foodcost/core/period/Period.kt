package ir.foodcost.core.period

import ir.foodcost.core.model.MenuPrice
import ir.foodcost.core.model.PurchasePrice
import ir.foodcost.core.model.RecipeVersion
import java.time.LocalDate

class PeriodLockedException(message: String) : IllegalStateException(message)

/**
 * Days up to [lockedThrough] belong to closed periods. No purchase price, recipe version or menu
 * price may start inside them, so their reports never change after closing.
 */
data class PeriodLock(val lockedThrough: LocalDate? = null) {
    fun isOpen(date: LocalDate): Boolean = lockedThrough == null || date.isAfter(lockedThrough)

    fun requireOpen(date: LocalDate, what: String) {
        if (!isOpen(date)) throw PeriodLockedException("$what starts on $date, inside the period closed through $lockedThrough")
    }

    fun check(price: PurchasePrice) = requireOpen(price.effectiveFrom, "A purchase price")

    fun check(version: RecipeVersion) = requireOpen(version.validFrom, "A recipe version")

    fun check(menuPrice: MenuPrice) = requireOpen(menuPrice.effectiveFrom, "A menu price")
}

/** Inventory formulas, used once purchases and stock counts arrive in version 2. */
object InventoryMath {
    /** Opening stock + net purchases + transfers in − transfers out − closing stock ± documented adjustments. */
    fun periodConsumption(
        openingRials: Long,
        netPurchasesRials: Long,
        closingRials: Long,
        transfersInRials: Long = 0,
        transfersOutRials: Long = 0,
        adjustmentsRials: Long = 0,
    ): Long = openingRials + netPurchasesRials + transfersInRials - transfersOutRials - closingRials + adjustmentsRials
}
