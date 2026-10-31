package ir.foodcost.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import ir.foodcost.data.json.Json
import ir.foodcost.data.json.asObject
import ir.foodcost.data.ops.OpId
import ir.foodcost.data.ops.Operation
import ir.foodcost.data.ops.OperationCodec
import ir.foodcost.data.store.OperationStore

/**
 * Keeps operations in one append-only SQLite table on the phone. Each row holds the operation's
 * JSON, the same text a backup holds. Rows are read once when the app opens and kept in memory.
 */
class SqliteOperationStore(context: Context) : OperationStore {
    private val helper = Helper(context)
    private val cache = LinkedHashMap<OpId, Operation>()

    /** Rows that could not be read. They stay in the table and are left out of the data. */
    var unreadable: Int = 0
        private set

    init {
        helper.readableDatabase.rawQuery("SELECT body FROM operations ORDER BY seq", null).use { cursor ->
            while (cursor.moveToNext()) {
                try {
                    val op = OperationCodec.decode(Json.parse(cursor.getString(0)).asObject("operation"))
                    if (op.id !in cache) cache[op.id] = op
                } catch (e: IllegalArgumentException) {
                    unreadable++
                }
            }
        }
    }

    @Synchronized
    override fun all(): List<Operation> = cache.values.toList()

    @Synchronized
    override fun contains(id: OpId): Boolean = id in cache

    @Synchronized
    override fun append(operation: Operation) {
        appendAll(listOf(operation))
    }

    @Synchronized
    override fun appendAll(operations: Collection<Operation>) {
        val fresh = operations.filter { it.id !in cache }.distinctBy { it.id }
        if (fresh.isEmpty()) return
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            for (op in fresh) {
                val values = ContentValues()
                values.put("id", op.id.value)
                values.put("body", Json.write(OperationCodec.encode(op)))
                db.insertWithOnConflict("operations", null, values, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        for (op in fresh) cache[op.id] = op
    }

    private class Helper(context: Context) : SQLiteOpenHelper(context, "foodcost.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE operations (" +
                    "seq INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "id TEXT NOT NULL UNIQUE, " +
                    "body TEXT NOT NULL)",
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}
