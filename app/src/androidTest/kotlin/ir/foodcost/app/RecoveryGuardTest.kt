package ir.foodcost.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.foodcost.app.data.SqliteOperationStore
import ir.foodcost.core.model.PurchasePrice
import ir.foodcost.core.units.Dimension
import ir.foodcost.core.units.Uom
import ir.foodcost.data.clock.HybridClock
import ir.foodcost.data.ledger.Ledger
import ir.foodcost.data.ops.Actor
import ir.foodcost.data.ops.Backup
import ir.foodcost.data.ops.Role
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** P0 regression: a quarantined database must not silently become a valid empty ledger. */
@RunWith(AndroidJUnit4::class)
class RecoveryGuardTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("foodcost", Context.MODE_PRIVATE)

    @Before fun cleanBefore() = clean()
    @After fun cleanAfter() = clean()

    private fun clean() {
        context.deleteDatabase(SqliteOperationStore.DATABASE)
        context.deleteDatabase("recovery-test-source.db")
        SqliteOperationStore.damagedFiles(context).forEach { it.delete() }
        prefs.edit().clear().commit()
    }

    /**
     * Regression for corrupt SQLite quarantine: a database and all of its sidecars
     * must be retained under the same unique suffix, never partly discarded.
     */
    @Test fun damagedDatabaseQuarantinePreservesMainFileAndSidecars() {
        val dir = context.cacheDir
        val source = java.io.File(dir, "quarantine-test-${System.nanoTime()}.db")
        val suffixes = listOf("", "-journal", "-wal", "-shm")
        try {
            suffixes.forEachIndexed { index, suffix ->
                java.io.File(source.path + suffix).writeText("test-payload-$index")
            }
            val method = SqliteOperationStore.Companion::class.java.getDeclaredMethod(
                "setAside", java.io.File::class.java,
            )
            method.isAccessible = true
            val aside = method.invoke(SqliteOperationStore.Companion, source) as java.io.File
            assertTrue(aside.name.startsWith(source.name + ".damaged-"))
            suffixes.forEachIndexed { index, suffix ->
                assertFalse(java.io.File(source.path + suffix).exists())
                val preserved = java.io.File(aside.path + suffix)
                assertTrue("Missing quarantined sidecar: $suffix", preserved.exists())
                assertEquals("test-payload-$index", preserved.readText())
                preserved.delete()
            }
        } finally {
            suffixes.forEach { java.io.File(source.path + it).delete() }
            dir.listFiles()?.filter { it.name.startsWith(source.name + ".damaged-") }?.forEach { it.delete() }
        }
    }

    @Test fun quarantinedDatabaseRejectsChangesAndExportsUntilNonemptyRestore() = runBlocking {
        prefs.edit().putBoolean("financial-recovery-required", true).commit()
        val container = AppContainer(context)
        container.open()
        assertTrue(container.financialDataUnverified)

        try {
            container.change { it.createIngredient("نباید ثبت شود", Dimension.MASS) }
            fail("A quarantined database accepted a financial mutation")
        } catch (_: IllegalStateException) { }

        try {
            container.exportVerifiedBackup()
            fail("A quarantined database exported an apparently complete backup")
        } catch (_: IllegalStateException) { }

        assertTrue(container.receive(emptyList()) == 0)
        assertTrue(container.financialDataUnverified)

        val source = SqliteOperationStore(context, "recovery-test-source.db")
        val sourceLedger = Ledger(source, HybridClock("source"), Actor("owner", Role.OWNER))
        sourceLedger.createIngredient("گوشت", Dimension.MASS)
        val backupOperations = source.all()
        source.close()
        assertTrue(backupOperations.isNotEmpty())

        assertTrue(container.receive(backupOperations) > 0)
        assertFalse(container.financialDataUnverified)
        assertTrue(container.exportVerifiedBackup().isNotEmpty())
    }

    /**
     * The audit of 0.7.8: a database set aside with no backup to bring back was a dead end. The user
     * can save the damaged file elsewhere and start empty; the file stays where it was.
     */
    @Test fun withoutABackupTheUserCanStartEmptyAndTheDamagedFileStays() = runBlocking {
        val dir = checkNotNull(context.getDatabasePath(SqliteOperationStore.DATABASE).parentFile)
        dir.mkdirs()
        val damaged = File(dir, "${SqliteOperationStore.DATABASE}.damaged-1")
        damaged.writeText("not a database")
        prefs.edit().putBoolean("financial-recovery-required", true).commit()
        val container = AppContainer(context)
        container.open()
        assertTrue(container.financialDataUnverified)
        assertEquals(listOf(damaged.name), container.damagedFiles().map { it.name })

        container.continueWithoutUnreadable()
        assertFalse(container.financialDataUnverified)
        assertTrue(container.integrity.value.verified)
        container.change { it.createIngredient("گوشت", Dimension.MASS) }
        assertTrue(container.exportVerifiedBackup().isNotEmpty())
        assertTrue(damaged.exists())
    }

    /**
     * The audit of 0.7.8: a row that could not be read locked the app for good when no backup had
     * it. An emergency backup keeps everything, the row as text included; going on without the row
     * opens changes again; and a backup that has the change still mends the row later.
     */
    @Test fun anEmergencyBackupKeepsTheRowNotReadAndTheUserCanGoOnWithoutIt() = runBlocking {
        val store = SqliteOperationStore(context)
        val ledger = Ledger(store, HybridClock("guard"), Actor("owner", Role.OWNER))
        val meat = ledger.createIngredient("گوشت", Dimension.MASS)
        val price = (ledger.recordPrice(PurchasePrice(meat, LocalDate.now(), BigDecimal.ONE, Uom.KILOGRAM, 16_000_000)) as Ledger.Outcome.Applied).operation
        store.close()
        context.openOrCreateDatabase(SqliteOperationStore.DATABASE, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("UPDATE operations SET body = ? WHERE id = ?", arrayOf("{\"id\": \"cut", price.id.value))
        }
        val container = AppContainer(context)
        container.open()
        assertEquals(1, container.unreadableRows)
        try {
            container.exportVerifiedBackup()
            fail("A ledger not read whole was exported as a whole backup")
        } catch (_: IllegalStateException) { }

        val emergency = container.exportEmergencyBackup()
        assertEquals(listOf(Backup.Unreadable(price.id.value, "{\"id\": \"cut")), Backup.unreadable(emergency))
        assertEquals(1, Backup.read(emergency).size) // the meat itself

        container.continueWithoutUnreadable()
        assertFalse(container.financialDataUnverified)
        assertEquals(1, container.unreadableRowsKept) // still on the phone, as it was stored
        container.change { it.createIngredient("پیاز", Dimension.MASS) }
        assertTrue(container.exportVerifiedBackup().isNotEmpty())

        // A backup that has the price mends its row.
        assertEquals(1, container.receive(listOf(price)))
        assertEquals(0, container.unreadableRowsKept)
        assertFalse(container.financialDataUnverified)
    }
}
