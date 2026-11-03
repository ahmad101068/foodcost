package ir.foodcost.presentation

import ir.foodcost.data.ledger.BackupPreview
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class BackupTextsTest {
    private fun preview(fresh: Int, refused: Int = 0, byOthers: Int = 0, devices: Int = 1, last: LocalDate = LocalDate.of(2026, 10, 13)) =
        BackupPreview(
            fresh = fresh, ingredients = 2, prices = 3, recipes = 4, menuPrices = 1, settings = 0, periods = 0,
            firstDay = LocalDate.of(2026, 10, 3), lastDay = last, otherDevices = devices, byOthers = byOthers, refused = refused,
        )

    @Test
    fun `the look before an import says what comes, from when and where, and what the rules refuse`() {
        assertEquals(
            "این پشتیبان ۱۰ تغییر تازه دارد: ۲ ماده، ۳ قیمت خرید، ۴ دستور یا نسخهٔ دستور، ۱ قیمت منو.\n" +
                "از ۱۱ مهر ۱۴۰۵ تا ۲۱ مهر ۱۴۰۵ ثبت شده‌اند، روی یک گوشی دیگر.",
            BackupTexts.preview(preview(10)),
        )
        assertEquals(
            "این پشتیبان ۱۰ تغییر تازه دارد: ۲ ماده، ۳ قیمت خرید، ۴ دستور یا نسخهٔ دستور، ۱ قیمت منو.\n" +
                "همه در ۱۱ مهر ۱۴۰۵ ثبت شده‌اند، روی ۲ گوشی دیگر.\n" +
                "۳ تغییر را کسی جز مالک ثبت کرده است.\n" +
                "۶ تغییر با قاعده‌ها جور نیست، مثل قیمتی برای روزی در دورهٔ بسته؛ نگه داشته می‌شود ولی چیزی را عوض نمی‌کند.",
            BackupTexts.preview(preview(10, refused = 6, byOthers = 3, devices = 2, last = LocalDate.of(2026, 10, 3))),
        )
    }
}
