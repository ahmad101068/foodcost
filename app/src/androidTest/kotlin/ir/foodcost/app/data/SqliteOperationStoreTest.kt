package ir.foodcost.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.foodcost.core.model.ComponentRef
import ir.foodcost.core.model.PurchasePrice
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.model.RecipeLine
import ir.foodcost.core.model.RecipeVersion
import ir.foodcost.core.reports.MenuSnapshot
import ir.foodcost.core.units.Dimension
import ir.foodcost.core.units.Uom
import ir.foodcost.data.clock.HybridClock
import ir.foodcost.data.json.Json
import ir.foodcost.data.json.JsonObject
import ir.foodcost.data.ledger.Ledger
import ir.foodcost.data.ops.Actor
import ir.foodcost.data.ops.Operation
import ir.foodcost.data.ops.Role
import ir.foodcost.data.store.InMemoryOperationStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The phone's own SQLite table, which the tests of the data module cannot reach: what is stored is
 * there after a restart, a row that cannot be read is counted and mended, a row stored under an id
 * other than its own is not read, a batch that fails part way stores nothing, a write cut short by
 * the phone dying leaves the last whole state, a damaged file is never deleted, and a file of a
 * newer version is neither opened nor changed. It uses database files of its own, not the app's.
 */
@RunWith(AndroidJUnit4::class)
class SqliteOperationStoreTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "store-test.db"

    private val copy = "store-test-crash.db"

    @Before
    @After
    fun clean() {
        context.deleteDatabase(name)
        context.deleteDatabase(copy)
        // Files a damaged database was set aside as.
        context.getDatabasePath(name).parentFile?.listFiles()?.filter { it.name.startsWith("$name.") }?.forEach { it.delete() }
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
    fun aRowStoredUnderAnIdOtherThanItsOwnIsNotRead() {
        // The audit of 0.7.1 (FCA-019): the id a row is stored under and the id in its text differ.
        val first = store()
        val stored = ledger(first).operations()
        first.close()
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("UPDATE operations SET id = ? WHERE id = ?", arrayOf("another", stored[0].id.value))
        }

        val odd = store()
        assertEquals(1, odd.unreadable)
        assertFalse(odd.contains(stored[0].id))
        odd.appendAll(stored) // the change itself comes back, under its own id
        assertTrue(odd.contains(stored[0].id))
        assertEquals(1, odd.unreadable) // the odd row stays, and is still said
        odd.close()
    }

    @Test
    fun conflictingIdsWithinOneBatchRejectEverythingAndSurviveRestart() {
        val first = store()
        val original = elsewhere()
        val conflict = original.copy(reason = "different content under the same id")
        try {
            first.appendAll(listOf(original, conflict))
            fail("Conflicting ids were silently accepted")
        } catch (e: IllegalStateException) {
            // expected: neither operation was stored
        }
        assertFalse(first.contains(original.id))
        assertTrue(first.all().isEmpty())
        first.close()
        val again = store()
        assertTrue(again.all().isEmpty())
        again.close()
    }

    @Test
    fun conflictingIdAlreadyStoredIsRejectedWithoutChangingTheOriginal() {
        val first = store()
        val original = elsewhere()
        first.append(original)
        try {
            first.append(original.copy(reason = "tampered duplicate"))
            fail("A conflicting stored id was accepted")
        } catch (e: IllegalStateException) {
            // expected
        }
        first.append(original) // identical replay is idempotent
        assertEquals(listOf(original), first.all())
        first.close()
        val again = store()
        assertEquals(listOf(original), again.all())
        again.close()
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

    @Test
    fun aDamagedDatabaseFileIsNotDeleted() {
        // The audit of 0.7.4 (FCA-010). A file whose header a failing write or flash chip spoiled:
        // Android's default handling of a damaged database deletes it and starts an empty one, so
        // everything stored would be gone without a word. The file must be kept, whatever else.
        val first = store()
        ledger(first)
        first.close()
        val file = context.getDatabasePath(name)
        RandomAccessFile(file, "rw").use { it.seek(0); it.write(ByteArray(100) { 0x5A }) }
        val damaged = file.readBytes()

        val opened = try {
            store()
        } catch (e: SQLiteException) {
            null // refusing to open is fine too
        }
        opened?.close()
        val kept = file.parentFile!!.listFiles()!!.filter { it.isFile }.any { it.readBytes().contentEquals(damaged) }
        assertTrue("The damaged database was deleted", kept)
    }

    @Test
    fun aDatabaseFileThatCannotBeReadIsSetAsideWholeAndAnEmptyOneStarted() {
        val first = store()
        val stored = ledger(first).operations()
        first.close()
        val file = context.getDatabasePath(name)
        RandomAccessFile(file, "rw").use { it.seek(0); it.write(ByteArray(100) { 0x5A }) }
        val damaged = file.readBytes()

        val opened = store()
        val aside = opened.setAside
        assertNotNull("The store did not say it set the damaged file aside", aside)
        assertTrue(aside!!.readBytes().contentEquals(damaged))
        assertTrue(aside.name.startsWith("$name.damaged-"))
        assertEquals(emptyList<Operation>(), opened.all())
        // The data comes back from a backup, into the new file.
        opened.appendAll(stored)
        opened.close()
        val again = store()
        assertNull(again.setAside)
        assertEquals(stored, again.all())
        again.close()
        assertTrue("The damaged file is still kept", aside.exists())
    }

    @Test
    fun aWriteCutShortByThePhoneDyingLeavesTheLastWholeState() {
        val first = store()
        val stored = ledger(first).operations()
        first.close()

        // A large write under way, enough to reach the disk before it ends, and the files as they
        // are on the disk at that moment: what a phone that dies then leaves behind.
        val dir = context.getDatabasePath(name).parentFile!!
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            db.beginTransaction()
            try {
                val body = "x".repeat(2_000)
                repeat(3_000) { i -> db.execSQL("INSERT INTO operations (id, body) VALUES (?, ?)", arrayOf("half-$i", body)) }
                listOf("", "-journal", "-wal", "-shm").forEach { suffix ->
                    File(dir, name + suffix).takeIf { it.exists() }?.copyTo(File(dir, copy + suffix), overwrite = true)
                }
            } finally {
                db.endTransaction() // never marked successful: undone
            }
        }

        val after = SqliteOperationStore(context, copy)
        assertEquals(stored, after.all())
        assertEquals(0, after.unreadable)
        after.close()
        // The table itself has nothing of the write either.
        val again = store()
        assertEquals(stored, again.all())
        again.close()
    }

    @Test
    fun aDatabaseOfANewerVersionIsNeitherOpenedNorChanged() {
        // An older app put over a newer one: it must not read a table it does not know, nor touch it.
        val first = store()
        ledger(first)
        first.close()
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { it.version = 2 }
        val before = rows()
        try {
            store()
            fail("A database of a newer version was opened")
        } catch (e: SQLiteException) {
            // expected
        }
        assertEquals(before, rows())
        assertEquals(3, before.size - 1) // the three ingredients, and the version
    }

    /** Every row of the table as it is in the file, and the file's version. */
    private fun rows(): List<String> =
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val rows = ArrayList<String>()
            db.rawQuery("SELECT seq, id, body FROM operations ORDER BY seq", null).use { c ->
                while (c.moveToNext()) rows += "${c.getLong(0)}|${c.getString(1)}|${c.getString(2)}"
            }
            rows + "version ${db.version}"
        }

    @Test
    fun theLatestPriceUnreadableLeavesTheOlderOneUntilABackupBringsItBack() {
        // The audit of 0.7.5 (R-075-01), on the phone's own table: with the row of the latest price
        // unreadable, the cost is worked out on the price before it, a figure that looks right. The
        // store counts the row, which every screen says, and a backup mends it and the figures.
        val first = store()
        val ledger = Ledger(first, HybridClock("phone"), Actor("owner", Role.OWNER))
        val meat = ledger.createIngredient("گوشت", Dimension.MASS)
        val day1 = LocalDate.of(2026, 10, 1)
        val day2 = day1.plusDays(10)
        ledger.recordPrice(PurchasePrice(meat, day1, BigDecimal.ONE, Uom.KILOGRAM, 16_000_000))
        val latest = (ledger.recordPrice(PurchasePrice(meat, day2, BigDecimal.ONE, Uom.KILOGRAM, 32_000_000)) as Ledger.Outcome.Applied).operation
        val dish = ledger.createRecipe("کباب", RecipeKind.MENU_ITEM)
        val line = RecipeLine(ComponentRef.IngredientRef(meat), BigDecimal(200), Uom.GRAM)
        ledger.saveRecipeVersion(dish, RecipeVersion(day1, batchLines = listOf(line), miscPercent = BigDecimal.ZERO))
        val all = ledger.operations()
        assertCost(6_400_000, ledger, dish, day2) // 200 g at 32,000 rials a gram
        first.close()
        damage(latest.id.value)

        val damaged = store()
        assertEquals(1, damaged.unreadable)
        val partial = Ledger(damaged, HybridClock("phone"), Actor("owner", Role.OWNER))
        assertCost(3_200_000, partial, dish, day2) // the price before it, 16,000 rials a gram
        assertEquals(1, partial.receive(all))
        assertEquals(0, damaged.unreadable)
        assertCost(6_400_000, partial, dish, day2)
        damaged.close()

        val again = store()
        assertEquals(0, again.unreadable)
        assertCost(6_400_000, Ledger(again, HybridClock("phone"), Actor("owner", Role.OWNER)), dish, day2)
        again.close()
    }

    private fun assertCost(rials: Long, ledger: Ledger, dish: ir.foodcost.core.model.RecipeId, day: LocalDate) {
        val cost = ledger.calculator().cost(dish, day).foodCostPerPortion
        assertEquals("cost on $day: $cost", 0, BigDecimal(rials).compareTo(cost))
    }

    @Test
    fun rowsAsVersion074StoredThemReadWithTheFiguresItWorkedOut() {
        // The table of 0.7.4 is this one, version 1; only the text of its rows was written by older
        // code. Legacy074Rows has the rows 0.7.4 itself wrote and the figures it worked out.
        store().close()
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            for (body in Legacy074Rows.operations) {
                val id = (Json.parse(body) as JsonObject).string("id")
                db.execSQL("INSERT INTO operations (id, body) VALUES (?, ?)", arrayOf(id, body))
            }
        }
        val read = store()
        assertEquals(0, read.unreadable)
        assertEquals(Legacy074Rows.operations.size, read.all().size)
        val ledger = Ledger(read, HybridClock("phone"), Actor("owner", Role.OWNER))
        for ((day, expected) in Legacy074Rows.figures) {
            val actual = MenuSnapshot.of(ledger.calculator().menu(LocalDate.parse(day))).items.sortedBy { it.recipeId.value }.map {
                "${it.recipeId.value}|${it.name}|${it.foodCostRials}|${it.menuPriceRials}|${it.suggestedPriceRials}|${it.foodCostPercent?.stripTrailingZeros()?.toPlainString()}|${it.status}"
            }
            assertEquals(day, expected, actual)
        }
        assertEquals(emptyList<Any>(), ledger.state.rejections)
        assertEquals(emptyList<Any>(), ledger.state.closedMismatches)
        read.close()
    }
}
