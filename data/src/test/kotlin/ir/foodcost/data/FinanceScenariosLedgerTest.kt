package ir.foodcost.data

import ir.foodcost.core.bd
import ir.foodcost.data.ledger.Ledger
import ir.foodcost.data.ledger.RejectReason
import java.math.BigDecimal
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The financial scenarios F12 to F16 of the 0.7.4 audit's own probe (FinanceProbe.kt), through the
 * ledger: the koobideh plate's cost worked out by hand, a closed period's cost kept, a purchase for
 * a closed day refused, and a backup restored once and only once. F01 to F11 are in the core
 * module's FinanceScenariosTest.
 */
class FinanceScenariosLedgerTest {
    private fun assertClose(expected: String, actual: BigDecimal?) {
        assertTrue(actual != null && (actual - expected.bd).abs() < "0.00005".bd, "expected $expected, got $actual")
    }

    // 220 g of meat at 16,000,000 a kilo is 3,520,000; plain rice, 150 g of rice at 4,600,000 a kilo
    // washed to 98%, is 150 / 0.98 × 4,600 = 704,081.63…: 4,224,081.63… rials a plate.
    @Test
    fun `F12 the koobideh plate costs 4,224,081 point 63 rials`() {
        val k = Kitchen(T.ledger("K"))
        assertClose("4224081.632653061224", k.ledger.calculator().cost(k.kebab, T.D31).foodCostPerPortion)
    }

    @Test
    fun `F13 and F14 a later price leaves the closed cost, and a purchase for a closed day is refused`() {
        val k = Kitchen(T.ledger("K"))
        k.ledger.closePeriod(T.D31).applied()
        val closed = k.ledger.calculator().cost(k.kebab, T.D31).foodCostPerPortion
        val later = k.ledger.recordPrice(T.price(k.meat, T.N2, 40_000_000))
        assertTrue(later is Ledger.Outcome.Applied)
        assertEquals(closed, k.ledger.calculator().cost(k.kebab, T.D31).foodCostPerPortion)
        assertEquals(RejectReason.PERIOD_CLOSED, k.ledger.recordPrice(T.price(k.meat, T.D15, 40_000_000)).rejection())
    }

    @Test
    fun `F15 and F16 a backup restores the same data on another phone, and a second import adds nothing`() {
        val k = Kitchen(T.ledger("K"))
        k.ledger.closePeriod(T.D31).applied()
        k.ledger.recordPrice(T.price(k.meat, T.N2, 40_000_000)).applied()
        val backup = k.ledger.exportBackup()
        val other = T.ledger("R")
        assertEquals(k.ledger.operations().size, other.importBackup(backup))
        assertEquals(k.ledger.state, other.state)
        assertEquals(0, other.importBackup(backup))
        assertEquals(k.ledger.operations().size, other.operations().size)
    }
}
