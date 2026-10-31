package ir.foodcost.data.store

import ir.foodcost.data.ops.OpId
import ir.foodcost.data.ops.Operation

/**
 * Where operations are kept: Room on the phone, PostgreSQL on the server. Operations are only
 * ever added; the store keeps the first copy of an id and ignores the rest.
 */
interface OperationStore {
    /** Every operation, in the order this store received them. */
    fun all(): List<Operation>

    fun contains(id: OpId): Boolean

    fun append(operation: Operation)

    fun appendAll(operations: Collection<Operation>)
}

class InMemoryOperationStore(initial: Collection<Operation> = emptyList()) : OperationStore {
    private val operations = LinkedHashMap<OpId, Operation>()

    init {
        appendAll(initial)
    }

    @Synchronized
    override fun all(): List<Operation> = operations.values.toList()

    @Synchronized
    override fun contains(id: OpId): Boolean = operations.containsKey(id)

    @Synchronized
    override fun append(operation: Operation) {
        operations.putIfAbsent(operation.id, operation)
    }

    @Synchronized
    override fun appendAll(operations: Collection<Operation>) {
        for (op in operations) this.operations.putIfAbsent(op.id, op)
    }
}
