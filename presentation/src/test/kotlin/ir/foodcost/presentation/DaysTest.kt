package ir.foodcost.presentation

import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals

class DaysTest {
    private val tehran = ZoneId.of("Asia/Tehran")

    @Test
    fun `the date is read again a second after midnight, however late the app was opened`() {
        assertEquals(31_000, Days.millisToTomorrow(ZonedDateTime.of(2026, 11, 3, 23, 59, 30, 0, tehran)))
        assertEquals(86_401_000, Days.millisToTomorrow(ZonedDateTime.of(2026, 11, 3, 0, 0, 0, 0, tehran)))
        assertEquals(43_201_000, Days.millisToTomorrow(ZonedDateTime.of(2026, 3, 20, 12, 0, 0, 0, tehran)))
    }
}
