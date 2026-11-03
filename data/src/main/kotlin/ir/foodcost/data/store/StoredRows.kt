package ir.foodcost.data.store

import ir.foodcost.data.json.Json
import ir.foodcost.data.json.asObject
import ir.foodcost.data.ops.OpId
import ir.foodcost.data.ops.Operation
import ir.foodcost.data.ops.OperationCodec

/** Operations read back from the JSON text of stored rows, and the rows that could not be read. */
class StoredRows private constructor(val operations: List<Operation>, val unreadable: List<Long>) {
    companion object {
        /**
         * Reads each row, given by its number and text. A row that cannot be read, damaged or of a
         * kind this version does not know, is listed by its number and left out; it never stops the
         * rest from being read, and nothing is removed from where it is stored.
         */
        fun read(rows: Sequence<Pair<Long, String>>): StoredRows {
            val operations = LinkedHashMap<OpId, Operation>()
            val unreadable = ArrayList<Long>()
            for ((number, body) in rows) {
                try {
                    val op = OperationCodec.decode(Json.parse(body).asObject("operation"))
                    if (op.id !in operations) operations[op.id] = op
                } catch (e: RuntimeException) {
                    unreadable += number
                }
            }
            return StoredRows(operations.values.toList(), unreadable)
        }
    }
}
