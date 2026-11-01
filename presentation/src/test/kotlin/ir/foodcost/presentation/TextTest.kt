package ir.foodcost.presentation

import ir.foodcost.core.money.DisplayCurrency
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextTest {

    @Test
    fun `digits turn Persian for showing and Latin for reading`() {
        assertEquals("۱۴۰۵/۰۸/۰۹", Persian.digits("1405/08/09"))
        assertEquals("12345", Persian.latinDigits("۱۲۳٤٥"))
        assertEquals("برنج 10", Persian.latinDigits("برنج ۱۰"))
    }

    @Test
    fun `search ignores spaces, the half-space and Arabic letter forms`() {
        assertTrue(Persian.matches("گوشت چرخ‌کرده", "چرخ کرده"))
        assertTrue(Persian.matches("علی", "علي"))
        assertTrue(Persian.matches("کره", "كره"))
        assertTrue(Persian.matches("Rice", "ri"))
        assertTrue(Persian.matches("هر چیزی", ""))
        assertFalse(Persian.matches("برنج", "گوشت"))
    }

    @Test
    fun `numbers are grouped and shown with Persian digits`() {
        assertEquals("۱٬۲۳۴٬۵۶۷٫۸۹", Numbers.format(BigDecimal("1234567.891"), 2))
        assertEquals("۱٬۰۰۰", Numbers.format(1000L))
        assertEquals("۱۲٫۵", Numbers.format(BigDecimal("12.50"), 2))
        assertEquals("۱٬۰۰۰", Numbers.format(BigDecimal("999.995"), 2))
        assertEquals("۰", Numbers.format(0))
        assertEquals("‎-۱٬۵۰۰", Numbers.format(BigDecimal("-1500")))
        assertEquals("۳۴٫۴٪", Numbers.percent(BigDecimal("34.4184")))
        assertEquals("۱۹٪", Numbers.percent(BigDecimal("19.259"), 0))
    }

    @Test
    fun `typed numbers are read whatever digits and separators are used`() {
        assertEquals(BigDecimal("1234.5"), Numbers.parse("۱٬۲۳۴٫۵"))
        assertEquals(BigDecimal("1234.5"), Numbers.parse("1,234.5"))
        assertEquals(BigDecimal("2.5"), Numbers.parse("۲/۵"))
        assertEquals(BigDecimal("12"), Numbers.parse("١٢"))
        assertEquals(BigDecimal("7"), Numbers.parse(" 7 "))
        assertEquals(BigDecimal("0.5"), Numbers.parse(".5"))
        assertEquals(BigDecimal("-5"), Numbers.parse("-5"))
        for (bad in listOf("", "12a", "1.2.3", "--1", "٫")) assertNull(Numbers.parse(bad), bad)
    }

    @Test
    fun `money shows and reads in the chosen currency and is kept in rials`() {
        val toman = MoneyFormat(DisplayCurrency.TOMAN)
        assertEquals("۴۲۲٬۴۰۸", toman.amount(BigDecimal("4224081.6327")))
        assertEquals("۱٬۳۵۰٬۰۰۰ تومان", toman.full(13_500_000))
        assertEquals(13_500_000L, toman.parse("۱٬۳۵۰٬۰۰۰"))
        assertEquals(5L, toman.parse("0.5"))
        assertNull(toman.parse("0.05"))
        assertNull(toman.parse("-1"))
        assertNull(toman.parse("بیست"))
        assertEquals("1350000", toman.editable(13_500_000))

        val newRial = MoneyFormat(DisplayCurrency.NEW_RIAL)
        assertEquals("۱٬۳۵۰ ریال جدید", newRial.full(13_500_000))
        assertEquals("۴۲۲٫۴۱", newRial.amount(BigDecimal("4224081.6327")))
        assertEquals(13_505_000L, newRial.parse("1350.5"))

        assertEquals("۵ ریال", MoneyFormat(DisplayCurrency.RIAL).full(5))
    }

    @Test
    fun `long numbers are shown in groups of three while typed, and the cursor stays in place`() {
        val price = assertNotNull(DigitGroups.of("1450000"))
        assertEquals("1٬450٬000", price.shown)
        assertEquals(listOf(0, 2, 3, 4, 6, 7, 8, 9), (0..7).map { price.shownOffset(it) })
        assertEquals(listOf(0, 1, 1, 2, 3, 4, 4, 5, 6, 7), (0..9).map { price.typedOffset(it) })
        assertEquals(9, price.shownOffset(42))

        assertEquals("1٬234.5", DigitGroups.of("1234.5")?.shown)
        assertEquals("12٬000/25", DigitGroups.of("12000/25")?.shown)
        assertEquals("۱۲٬۳۴۵", DigitGroups.of("۱۲۳۴۵")?.shown)
        assertEquals("1٬000.", DigitGroups.of("1000.")?.shown)

        assertNull(DigitGroups.of("170"))
        assertNull(DigitGroups.of("0.5"))
        assertNull(DigitGroups.of(""))
        assertNull(DigitGroups.of("-1000"))
        assertNull(DigitGroups.of("1,450,000"))
        assertNull(DigitGroups.of("12a45"))
        assertNull(DigitGroups.of("1000.5x"))
    }
}
