package ir.foodcost.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.foodcost.app.data.SqliteOperationStore
import ir.foodcost.core.units.Dimension
import ir.foodcost.data.clock.HybridClock
import ir.foodcost.data.ledger.Ledger
import ir.foodcost.data.ops.Actor
import ir.foodcost.data.ops.Role
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
}
