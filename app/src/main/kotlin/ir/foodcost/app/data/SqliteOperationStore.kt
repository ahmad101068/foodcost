package ir.foodcost.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import ir.foodcost.data.json.Json
import ir.foodcost.data.ops.OpId
import ir.foodcost.data.ops.Operation
import ir.foodcost.data.ops.OperationCodec
import ir.foodcost.data.store.OperationStore
import ir.foodcost.data.store.StoredRows

/**
 * Keeps operations in one append-only SQLite table on the phone. Each row holds the operation's
 * JSON, the same text a backup holds. Rows are read once when the app opens and kept in memory.
 * [name] is the database file; tests use one of their own.
 */
class SqliteOperationStore(context: Context, name: String = DATABASE) : OperationStore {
    private val helper = Helper(context, name)
    private val cache = LinkedHashMap<OpId, Operation>()

    // The ids of rows that could not be read. They stay in the table and are left out of the data,
    // until the same operation arrives again, from a backup, and its row is mended with it.
    private val unreadableIds = HashSet<String>()

    /** Rows that could not be read. They stay in the table and are left out of the data. */
    val unreadable: Int
        @Synchronized get() = unreadableIds.size

    init {
        val rows = ArrayList<Triple<Long, String, String>>()
        helper.readableDatabase.rawQuery("SELECT seq, id, body FROM operations ORDER BY seq", null).use { cursor ->
            while (cursor.moveToNext()) rows += Triple(cursor.getLong(0), cursor.getString(1), cursor.getString(2))
        }
        val read = StoredRows.read(rows.asSequence().map { (seq, _, body) -> seq to body })
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

    private class Helper(context: Context, name: String) : SQLiteOpenHelper(context, name, null, 1) {
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
    }
}
