package ir.foodcost.app.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.foodcost.core.units.Dimension
import ir.foodcost.data.clock.HybridClock
import ir.foodcost.data.ledger.Ledger
import ir.foodcost.data.ops.Actor
import ir.foodcost.data.ops.Operation
import ir.foodcost.data.ops.Role
import ir.foodcost.data.store.InMemoryOperationStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The phone's own SQLite table, which the tests of the data module cannot reach: what is stored is
 * there after a restart, a row that cannot be read is counted and mended, and a batch that fails
 * part way stores nothing. It uses a database file of its own, not the app's.
 */
@RunWith(AndroidJUnit4::class)
class SqliteOperationStoreTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "store-test.db"

    @Before
    @After
    fun clean() {
        context.deleteDatabase(name)
    }

    private fun store() = SqliteOperationStore(context, name)

    /** Three ingredients entered through a ledger on [store]. */
    private fun ledger(store: SqliteOperationStore): Ledger {
        val ledger = Ledger(store, HybridClock("phone"), Actor("owner", Role.OWNER))
        listOf("برنج", "گوشت", "پیاز").forEach { ledger.createIngredient(it, Dimension.MASS) }
        return ledger
    }

    /** One change made elsewhere, on another phone. */
    private fun elsewhere(): Operation =
        Ledger(InMemoryOperationStore(), HybridClock("other"), Actor("owner", Role.OWNER))
            .also { it.createIngredient("نمک", Dimension.MASS) }
            .operations().single()

    private fun damage(id: String) {
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("UPDATE operations SET body = ? WHERE id = ?", arrayOf("{\"id\": \"cut", id))
        }
    }

    @Test
    fun whatIsStoredIsReadBackAfterARestart() {
        val first = store()
        val stored = ledger(first).operations()
        first.close()

        val again = store()
        assertEquals(stored, again.all())
        assertEquals(0, again.unreadable)
        again.close()
    }

    @Test
    fun aRowThatCannotBeReadIsCountedAndMendedWhenItsChangeArrivesAgain() {
        val first = store()
        val stored = ledger(first).operations()
        first.close()
        damage(stored[1].id.value)

        val damaged = store()
        assertEquals(1, damaged.unreadable)
        assertEquals(listOf(stored[0], stored[2]), damaged.all())
        damaged.appendAll(stored) // the same changes again, as from a backup
        assertEquals(0, damaged.unreadable)
        assertEquals(stored.toSet(), damaged.all().toSet())
        damaged.close()

        // Mended in the table too, not only in memory: after a restart it reads.
        val mended = store()
        assertEquals(0, mended.unreadable)
        assertEquals(stored.toSet(), mended.all().toSet())
        mended.close()
    }

    @Test
    fun aBatchThatFailsPartWayStoresNothing() {
        val first = store()
        val ledger = ledger(first)
        val stale = store() // reads the table now, before the next change
        ledger.createIngredient("روغن", Dimension.VOLUME)
        val newest = ledger.operations().last()

        // The stale store does not know the newest row: storing it again beside a new change fails
        // on that row, and the new change, which came first, is not kept either.
        val other = elsewhere()
        try {
            stale.appendAll(listOf(other, newest))
            fail("A row stored but not read was taken for a new one")
        } catch (e: IllegalStateException) {
            // expected
        }
        assertFalse(stale.contains(other.id))
        stale.close()
        first.close()

        val after = store()
        assertFalse(after.contains(other.id))
        assertTrue(after.contains(newest.id))
        assertEquals(4, after.all().size)
        after.close()
    }
}
