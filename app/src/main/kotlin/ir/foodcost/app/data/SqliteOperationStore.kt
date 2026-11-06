package ir.foodcost.app.data

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteOpenHelper
import ir.foodcost.data.json.Json
import ir.foodcost.data.ops.OpId
import ir.foodcost.data.ops.Operation
import ir.foodcost.data.ops.OperationCodec
import ir.foodcost.data.store.OperationStore
import ir.foodcost.data.store.StoredRows
import java.io.File
import java.io.IOException

/**
 * Keeps operations in one append-only SQLite table on the phone. Each row holds the operation's
 * JSON, the same text a backup holds. Rows are read once when the app opens and kept in memory.
 * [name] is the database file; tests use one of their own.
 *
 * A damaged file is never deleted, which is what Android does by default. One that cannot be
 * read at all is set aside whole, under a name of its own beside it, and an empty one is started
 * in its place: [setAside] says so, for the app to tell, and a backup brings the data back.
 */
class SqliteOperationStore(context: Context, name: String = DATABASE) : OperationStore {
    private val file: File = context.getDatabasePath(name)
    private var helper = Helper(context, name)
    private val cache = LinkedHashMap<OpId, Operation>()

    /** The damaged database file this store found when it opened and set aside, or null. */
    val setAside: File?

    // The ids of rows that could not be read. They stay in the table and are left out of the data,
    // until the same operation arrives again, from a backup, and its row is mended with it.
    private val unreadableIds = HashSet<String>()

    /** Rows that could not be read. They stay in the table and are left out of the data. */
    val unreadable: Int
        @Synchronized get() = unreadableIds.size

    init {
        var aside: File? = null
        val rows = try {
            readTable()
        } catch (e: SQLiteDatabaseCorruptException) {
            // The file cannot be read at all: kept whole under another name, and an empty one started.
            helper.close()
            aside = setAside(file)
            helper = Helper(context, name)
            readTable()
        }
        setAside = aside
        // The id a row is stored under must be the one its text has; a row whose two differ is not read.
        val read = StoredRows.readRows(rows.asSequence().map { (seq, id, body) -> StoredRows.Row(seq, id, body) })
        read.operations.forEach { cache[it.id] = it }
        val unreadableSeqs = read.unreadable.toHashSet()
        rows.filter { it.first in unreadableSeqs }.mapTo(unreadableIds) { it.second }
    }

    @Synchronized
    override fun all(): List<Operation> = cache.values.toList()

    @Synchronized
    override fun contains(id: OpId): Boolean = id in cache

    @Synchronized
    override fun append(operation: Operation) {
        appendAll(listOf(operation))
    }

    /**
     * Stores [operations] in one transaction: all of them or, if one fails, none, and the memory
     * copy changes only after the table has. An operation whose row could not be read mends that
     * row; one the table has although this store did not read it is an error, and stores nothing.
     */
    @Synchronized
    override fun appendAll(operations: Collection<Operation>) {
        val fresh = operations.filter { it.id !in cache }.distinctBy { it.id }
        if (fresh.isEmpty()) return
        val mended = ArrayList<String>()
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            for (op in fresh) {
                val id = op.id.value
                val values = ContentValues()
                values.put("id", id)
                values.put("body", Json.write(OperationCodec.encode(op)))
                if (db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L) continue
                // The id is in the table already.
                check(id in unreadableIds) { "Operation $id is stored but was not read" }
                values.remove("id")
                check(db.update(TABLE, values, "id = ?", arrayOf(id)) == 1) { "The unreadable row of $id was not mended" }
                mended += id
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        for (op in fresh) cache[op.id] = op
        unreadableIds.removeAll(mended.toSet())
    }

    /** Closes the database, for tests that open the same file again. */
    fun close() = helper.close()

    private fun readTable(): List<Triple<Long, String, String>> {
        val rows = ArrayList<Triple<Long, String, String>>()
        helper.readableDatabase.rawQuery("SELECT seq, id, body FROM operations ORDER BY seq", null).use { cursor ->
            while (cursor.moveToNext()) rows += Triple(cursor.getLong(0), cursor.getString(1), cursor.getString(2))
        }
        return rows
    }

    /**
     * Android's default answer to a damaged database is to delete it: here nothing is deleted. The
     * error reaches the code that hit it, and a file that cannot be read when the app opens is set
     * aside whole.
     */
    private class KeepDamaged : DatabaseErrorHandler {
        override fun onCorruption(dbObj: SQLiteDatabase) {
            // Nothing: the file stays as it is.
        }
    }

    private class Helper(context: Context, name: String) : SQLiteOpenHelper(context, name, null, 1, KeepDamaged()) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE $TABLE (" +
                    "seq INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "id TEXT NOT NULL UNIQUE, " +
                    "body TEXT NOT NULL)",
            )
        }

        /**
         * Each change to the table needs its own step here, written and tested against a copy of a
         * real database before the version above goes up. A step missing stops the app from opening
         * the database, rather than leaving it half changed.
         */
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            for (version in oldVersion until newVersion) {
                when (version) {
                    else -> throw IllegalStateException("No step from database version $version to ${version + 1}")
                }
            }
        }
    }

    companion object {
        /** The app's database file. */
        const val DATABASE = "foodcost.db"
        private const val TABLE = "operations"

        /**
         * Moves [file] and the journal files beside it to a name of their own, which nothing in the
         * app opens or deletes, so its data can still be got at. Throws if the file cannot be moved:
         * an empty database is never started over data that was not kept.
         */
        private fun setAside(file: File): File {
            var aside = File(file.parentFile, "${file.name}.damaged-${System.currentTimeMillis()}")
            while (aside.exists()) aside = File(aside.path + "-1")
            // A failed move must never leave the main DB at its aside path while the
            // next launch sees an absent original and silently creates an empty DB.
            // Move sidecars first, then the main file; roll back every completed move
            // on failure. If rollback itself fails, keep failing closed and preserve
            // the original exception with the rollback failure attached.
            val moved = ArrayList<Pair<File, File>>()
            try {
                for (suffix in listOf("-journal", "-wal", "-shm", "")) {
                    val source = File(file.path + suffix)
                    if (!source.exists()) continue
                    val target = File(aside.path + suffix)
                    if (!source.renameTo(target)) {
                        throw IOException("The damaged database's ${source.name} could not be set aside")
                    }
                    moved += source to target
                }
            } catch (failure: IOException) {
                for ((source, target) in moved.asReversed()) {
                    if (!target.renameTo(source)) {
                        failure.addSuppressed(IOException("Could not restore ${source.name} after failed quarantine"))
                    }
                }
                throw failure
            }
            return aside
        }
    }
}
