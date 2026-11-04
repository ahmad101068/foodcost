package ir.foodcost.presentation

import ir.foodcost.data.ledger.BackupPreview
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackupTextsTest {
    private fun preview(fresh: Int, refused: Int = 0, byOthers: Int = 0, devices: Int = 1, last: LocalDate = LocalDate.of(2026, 10, 13)) =
        BackupPreview(
            fresh = fresh, ingredients = 2, prices = 3, recipes = 4, menuPrices = 1, settings = 0, periods = 0,
            firstDay = LocalDate.of(2026, 10, 3), lastDay = last, otherDevices = devices, byOthers = byOthers, refused = refused,
        )

    @Test
    fun `a backup that clashes with the changes here says why it is not imported`() {
        assertEquals(
            "این پشتیبان ۲ تغییر دارد که شناسه یا شماره‌اش با تغییر دیگری یکی است ولی خودش فرق دارد. " +
                "فایل خراب یا دست‌کاری شده است و چیزی از آن وارد نمی‌شود.",
            BackupTexts.conflicts(2),
        )
        assertEquals(
            "این پشتیبان رقم یک دورهٔ بسته را عوض می‌کند و رقم دورهٔ بسته نباید عوض شود، " +
                "پس چیزی از آن وارد نمی‌شود. اگر این تغییرها درست‌اند، اول دوره را باز کنید.",
            BackupTexts.changesClosed(1),
        )
        assertEquals(true, "۳ دورهٔ بسته" in BackupTexts.changesClosed(3))
    }

    @Test
    fun `the look before an import says what comes, from when and where, and what the rules refuse`() {
        assertEquals(
            "این پشتیبان ۱۰ تغییر تازه دارد: ۲ ماده، ۳ قیمت خرید، ۴ دستور یا نسخهٔ دستور، ۱ قیمت منو.\n" +
                "از ۱۱ مهر ۱۴۰۵ تا ۲۱ مهر ۱۴۰۵ ثبت شده‌اند، روی یک گوشی دیگر.\n" +
                BackupTexts.SEALED,
            BackupTexts.preview(preview(10), sealed = true),
        )
        assertEquals(
            "این پشتیبان ۱۰ تغییر تازه دارد: ۲ ماده، ۳ قیمت خرید، ۴ دستور یا نسخهٔ دستور، ۱ قیمت منو.\n" +
                "همه در ۱۱ مهر ۱۴۰۵ ثبت شده‌اند، روی ۲ گوشی دیگر.\n" +
                "۳ تغییر را کسی جز مالک ثبت کرده است.\n" +
                "۶ تغییر با قاعده‌ها جور نیست، مثل قیمتی برای روزی در دورهٔ بسته؛ نگه داشته می‌شود ولی چیزی را عوض نمی‌کند.\n" +
                BackupTexts.NOT_SEALED,
            BackupTexts.preview(preview(10, refused = 6, byOthers = 3, devices = 2, last = LocalDate.of(2026, 10, 3)), sealed = false),
        )
    }

    @Test
    fun `a damaged data file set aside is named, and the way back from a backup is said`() {
        val text = BackupTexts.setAside("foodcost.db.damaged-1730000000000")
        assertTrue("«foodcost.db.damaged-1730000000000»" in text)
        assertTrue("چیزی پاک نشد" in text)
        assertTrue("«بازگرداندن»" in text)
    }

    @Test
    fun `closed periods that no longer add up are counted in Persian digits`() {
        assertTrue(BackupTexts.closedMismatches(1).startsWith("رقم یک دورهٔ بسته "))
        assertTrue(BackupTexts.closedMismatches(3).startsWith("رقم ۳ دورهٔ بسته "))
    }
}
