package ir.foodcost.data.store

import ir.foodcost.data.json.Json
import ir.foodcost.data.json.asObject
import ir.foodcost.data.ops.OpId
import ir.foodcost.data.ops.Operation
import ir.foodcost.data.ops.OperationCodec

/** Operations read back from the JSON text of stored rows, and the rows that could not be read. */
class StoredRows private constructor(val operations: List<Operation>, val unreadable: List<Long>) {
    /** One stored row: its number, the id it is stored under when the store keeps one apart, and its text. */
    data class Row(val number: Long, val id: String?, val body: String)

    companion object {
        /** [readRows] for rows given by their number and text alone. */
        fun read(rows: Sequence<Pair<Long, String>>): StoredRows = readRows(rows.map { (number, body) -> Row(number, null, body) })

        /**
         * Reads each row. A row that cannot be read, damaged or of a kind this version does not know,
         * is listed by its number and left out; so is one stored under an id other than the one its
         * text has, since which of the two is right cannot be told. None of them stops the rest from
         * being read, and nothing is removed from where it is stored.
         */
        fun readRows(rows: Sequence<Row>): StoredRows {
            val operations = LinkedHashMap<OpId, Operation>()
            val unreadable = ArrayList<Long>()
            for (row in rows) {
                val op = try {
                    OperationCodec.decode(Json.parse(row.body).asObject("operation"))
                } catch (e: RuntimeException) {
                    unreadable += row.number
                    continue
                }
                if (row.id != null && row.id != op.id.value) {
                    unreadable += row.number
                } else if (op.id !in operations) {
                    operations[op.id] = op
                }
            }
            return StoredRows(operations.values.toList(), unreadable)
        }
    }
}
