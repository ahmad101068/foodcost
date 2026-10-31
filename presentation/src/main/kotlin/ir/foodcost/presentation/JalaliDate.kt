package ir.foodcost.presentation

import java.time.LocalDate

/** A date in the Iranian (Jalali, Solar Hijri) calendar, the way dates are read and written in Iran. */
data class JalaliDate(val year: Int, val month: Int, val day: Int) : Comparable<JalaliDate> {
    init {
        require(year in MIN_YEAR..MAX_YEAR) { "Year $year is out of range" }
        require(month in 1..12) { "Month $month is out of range" }
        require(day in 1..monthLength(year, month)) { "Day $day is out of range for $year/$month" }
    }

    val monthName: String get() = MONTHS[month - 1]

    fun toLocalDate(): LocalDate {
        val dayOfYear = if (month <= 6) (month - 1) * 31 else 186 + (month - 7) * 30
        return farvardinFirst(year).plusDays((dayOfYear + day - 1).toLong())
    }

    override fun compareTo(other: JalaliDate): Int = compareValuesBy(this, other, { it.year }, { it.month }, { it.day })

    /** ۱۴۰۵/۰۸/۰۹ */
    fun format(): String = Persian.digits(toString())

    /** ۹ آبان ۱۴۰۵ */
    fun formatLong(): String = "${Persian.digits(day)} $monthName ${Persian.digits(year)}"

    /** 1405-08-09, for file names. */
    fun isoLike(): String = "$year-${pad(month)}-${pad(day)}"

    override fun toString(): String = "$year/${pad(month)}/${pad(day)}"

    companion object {
        const val MIN_YEAR = 1
        const val MAX_YEAR = 3177

        val MONTHS = listOf(
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
        )

        fun of(date: LocalDate): JalaliDate {
            var year = date.year - 621
            val cal = cal(year)
            var k = (date.toEpochDay() - LocalDate.of(cal.gregorianYear, 3, cal.march).toEpochDay()).toInt()
            if (k >= 0) {
                if (k <= 185) return JalaliDate(year, 1 + k / 31, k % 31 + 1)
                k -= 186
            } else {
                // Before 1 Farvardin: the second half of the year before.
                year -= 1
                k += 179
                if (cal.leap == 1) k += 1
            }
            return JalaliDate(year, 7 + k / 30, k % 30 + 1)
        }

        fun isLeap(year: Int): Boolean = cal(year).leap == 0

        fun monthLength(year: Int, month: Int): Int = when {
            month <= 6 -> 31
            month <= 11 -> 30
            isLeap(year) -> 30
            else -> 29
        }

        /**
         * Reads ۱۴۰۵/۸/۹, 1405-08-09 and similar. A two-digit year means 13xx from 80 up and 14xx below.
         * Null when the text is not a real date.
         */
        fun parse(text: String): JalaliDate? {
            val parts = Persian.latinDigits(text.trim()).split('/', '-', '.', '٫').map { it.trim() }
            if (parts.size != 3) return null
            val (y, m, d) = parts.map { it.toIntOrNull() ?: return null }
            val year = when {
                y in 0..79 -> 1400 + y
                y in 80..99 -> 1300 + y
                else -> y
            }
            return try {
                JalaliDate(year, m, d)
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        private fun pad(n: Int): String = n.toString().padStart(2, '0')

        private fun farvardinFirst(year: Int): LocalDate {
            val c = cal(year)
            return LocalDate.of(c.gregorianYear, 3, c.march)
        }

        private class Cal(val leap: Int, val gregorianYear: Int, val march: Int)

        private val BREAKS = intArrayOf(
            -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
            1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178,
        )

        /**
         * The calendar rules of the jalaali-js library (33-year cycles with known breaks).
         * [Cal.leap] counts the years since the last leap year (0 means a leap year), and
         * 1 Farvardin falls on March [Cal.march] of [Cal.gregorianYear].
         */
        private fun cal(jy: Int): Cal {
            require(jy >= BREAKS.first() && jy < BREAKS.last()) { "Year $jy is out of range" }
            val gy = jy + 621
            var leapJ = -14
            var jp = BREAKS[0]
            var jump = 0
            for (i in 1 until BREAKS.size) {
                val jm = BREAKS[i]
                jump = jm - jp
                if (jy < jm) break
                leapJ += jump / 33 * 8 + jump % 33 / 4
                jp = jm
            }
            var n = jy - jp
            leapJ += n / 33 * 8 + (n % 33 + 3) / 4
            if (jump % 33 == 4 && jump - n == 4) leapJ += 1
            val leapG = gy / 4 - (gy / 100 + 1) * 3 / 4 - 150
            val march = 20 + leapJ - leapG
            if (jump - n < 6) n = n - jump + (jump + 4) / 33 * 33
            var leap = ((n + 1) % 33 - 1) % 4
            if (leap == -1) leap = 4
            return Cal(leap, gy, march)
        }
    }
}
