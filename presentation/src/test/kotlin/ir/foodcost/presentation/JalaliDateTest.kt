package ir.foodcost.presentation

import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JalaliDateTest {

    @Test
    fun `known dates convert both ways`() {
        val known = mapOf(
            LocalDate.of(1979, 2, 11) to JalaliDate(1357, 11, 22),
            LocalDate.of(2000, 1, 1) to JalaliDate(1378, 10, 11),
            LocalDate.of(2024, 3, 20) to JalaliDate(1403, 1, 1),
            LocalDate.of(2025, 3, 20) to JalaliDate(1403, 12, 30),
            LocalDate.of(2025, 3, 21) to JalaliDate(1404, 1, 1),
            LocalDate.of(2026, 3, 20) to JalaliDate(1404, 12, 29),
            LocalDate.of(2026, 3, 21) to JalaliDate(1405, 1, 1),
            LocalDate.of(2026, 10, 1) to JalaliDate(1405, 7, 9),
            LocalDate.of(2026, 10, 31) to JalaliDate(1405, 8, 9),
        )
        for ((gregorian, jalali) in known) {
            assertEquals(jalali, JalaliDate.of(gregorian), "$gregorian")
            assertEquals(gregorian, jalali.toLocalDate(), "$jalali")
        }
    }

    @Test
    fun `every day from 1950 to 2100 converts back and follows the day before`() {
        var day = LocalDate.of(1950, 1, 1)
        var previous = JalaliDate.of(day.minusDays(1))
        while (day.year <= 2100) {
            val jalali = JalaliDate.of(day)
            assertEquals(day, jalali.toLocalDate())
            val expected = when {
                previous.day < JalaliDate.monthLength(previous.year, previous.month) -> previous.copy(day = previous.day + 1)
                previous.month < 12 -> JalaliDate(previous.year, previous.month + 1, 1)
                else -> JalaliDate(previous.year + 1, 1, 1)
            }
            assertEquals(expected, jalali, "$day")
            previous = jalali
            day = day.plusDays(1)
        }
    }

    @Test
    fun `leap years have a thirtieth of Esfand`() {
        assertTrue(JalaliDate.isLeap(1399))
        assertTrue(JalaliDate.isLeap(1403))
        assertFalse(JalaliDate.isLeap(1400))
        assertFalse(JalaliDate.isLeap(1404))
        assertFalse(JalaliDate.isLeap(1405))
        assertEquals(30, JalaliDate.monthLength(1403, 12))
        assertEquals(29, JalaliDate.monthLength(1404, 12))
    }

    @Test
    fun `typed dates are read in the usual forms`() {
        val aban9 = JalaliDate(1405, 8, 9)
        assertEquals(aban9, JalaliDate.parse("۱۴۰۵/۰۸/۰۹"))
        assertEquals(aban9, JalaliDate.parse("1405-8-9"))
        assertEquals(aban9, JalaliDate.parse(" 05/8/9 "))
        assertEquals(JalaliDate(1399, 12, 30), JalaliDate.parse("99/12/30"))
        for (bad in listOf("1404/12/30", "1405/13/01", "1405/08", "abc", "")) assertNull(JalaliDate.parse(bad), bad)
    }

    @Test
    fun `dates are written the Persian way`() {
        val date = JalaliDate(1405, 8, 9)
        assertEquals("۱۴۰۵/۰۸/۰۹", date.format())
        assertEquals("۹ آبان ۱۴۰۵", date.formatLong())
        assertEquals("1405-08-09", date.isoLike())
    }
}
