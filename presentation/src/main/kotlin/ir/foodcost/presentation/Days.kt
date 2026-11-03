package ir.foodcost.presentation

import java.time.Duration
import java.time.ZonedDateTime

/** When the date changes, for an app that dates every price it records with today. */
object Days {
    /** Until a moment after the next midnight, so the date read then is already tomorrow's. */
    fun millisToTomorrow(now: ZonedDateTime): Long =
        Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(now.zone)).toMillis() + AFTER_MIDNIGHT_MILLIS

    private const val AFTER_MIDNIGHT_MILLIS = 1_000L
}
