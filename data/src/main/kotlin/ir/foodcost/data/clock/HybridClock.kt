package ir.foodcost.data.clock

/**
 * A hybrid logical clock timestamp: wall time, a counter for events in the same millisecond,
 * and the device as the final tiebreaker. Timestamps from all devices form one total order.
 */
data class Hlc(val wallMillis: Long, val counter: Int, val device: String) : Comparable<Hlc> {
    override fun compareTo(other: Hlc): Int =
        compareValuesBy(this, other, { it.wallMillis }, { it.counter }, { it.device })
}

/**
 * Hands out timestamps that always increase on this device, even if the phone's clock is set back,
 * and that sort after every timestamp received from other devices.
 */
class HybridClock(private val device: String, private val wallClock: () -> Long = System::currentTimeMillis) {
    private var last = Hlc(0, 0, device)

    @Synchronized
    fun now(): Hlc {
        val wall = wallClock()
        last = if (wall > last.wallMillis) Hlc(wall, 0, device) else Hlc(last.wallMillis, last.counter + 1, device)
        return last
    }

    /** Moves past [remote], so the next local event sorts after it. */
    @Synchronized
    fun receive(remote: Hlc) {
        val wall = maxOf(wallClock(), last.wallMillis, remote.wallMillis)
        val counter = when (wall) {
            last.wallMillis -> if (wall == remote.wallMillis) maxOf(last.counter, remote.counter) + 1 else last.counter + 1
            remote.wallMillis -> remote.counter + 1
            else -> 0
        }
        last = Hlc(wall, counter, device)
    }
}
