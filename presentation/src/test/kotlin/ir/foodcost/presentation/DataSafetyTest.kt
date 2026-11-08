package ir.foodcost.presentation

import ir.foodcost.data.ledger.AtomicChangeRejected
import ir.foodcost.data.ledger.BackupPreview
import ir.foodcost.data.ledger.RejectReason
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The audit of 0.7.8: what the app says when data was not read whole, a change is refused, or no backup is recent. */
class DataSafetyTest {
    private val tehran = ZoneId.of("Asia/Tehran")
    private val now = Instant.parse("2026-11-08T12:00:00Z")

    @Test
    fun `a refused change says which rule refused it, not that the value was wrong`() {
        // Every change in the app runs as one atomic change, where a refusal is an exception.
        assertEquals("این تاریخ در دورهٔ بسته است و تغییر نمی‌کند.", Labels.failure(AtomicChangeRejected(RejectReason.PERIOD_CLOSED)))
        assertEquals("این تغییر رقم‌های یک دورهٔ بسته را عوض می‌کرد و ثبت نشد.", Labels.failure(AtomicChangeRejected(RejectReason.CLOSED_FIGURES)))
        assertEquals("این تاریخ در دورهٔ بسته است و تغییر نمی‌کند.", Labels.failure(QuickSaveRefused(RejectReason.PERIOD_CLOSED)))
        assertEquals(Labels.DATA_NOT_VERIFIED, Labels.failure(DataNotVerified()))
        assertEquals("این مقدار پذیرفته نشد.", Labels.failure(IllegalArgumentException("A recipe needs a name")))
        assertEquals("این کار انجام نشد.", Labels.failure(IllegalStateException("disk full")))
    }

    @Test
    fun `data is verified only when every row was read and no damaged file waits for a choice`() {
        assertTrue(DataIntegrity().verified)
        assertFalse(DataIntegrity(unreadable = 1).verified)
        assertFalse(DataIntegrity(setAside = true).verified)
    }

    @Test
    fun `the ways on are said for a damaged file and for unreadable rows`() {
        val rows = BackupTexts.locked(DataIntegrity(unreadable = 3))
        assertTrue("۳ تغییر" in rows && "پشتیبان اضطراری" in rows && "بدون آن‌ها ادامه دهید" in rows, rows)
        val file = BackupTexts.locked(DataIntegrity(setAside = true))
        assertTrue("پروندهٔ خراب را برای بررسی ذخیره کنید" in file && "داده‌های خالی شروع کنید" in file, file)
        assertTrue("دوباره خوانده می‌شوند" in BackupTexts.continueWithout(DataIntegrity(unreadable = 3)))
        assertTrue("پروندهٔ خراب روی گوشی می‌ماند" in BackupTexts.continueWithout(DataIntegrity(setAside = true)))
        // The warning the app opens with no longer says a backup can be made as usual.
        val opening = BackupTexts.unreadable(2)
        assertTrue("پشتیبان اضطراری" in opening && "بدون آن‌ها ادامه دهید" in opening, opening)
    }

    @Test
    fun `an emergency backup says before importing what it lacks`() {
        val preview = BackupPreview(4, 1, 2, 1, 0, 0, 0, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1), 0, 0, 0)
        val text = BackupTexts.preview(preview, sealed = false, unreadable = 2)
        assertTrue(text.startsWith("این پشتیبان اضطراری است: ۲ تغییر"), text)
        assertFalse("اضطراری" in BackupTexts.preview(preview, sealed = false))
    }

    @Test
    fun `a backup is asked for once there is enough to lose, a week after the last one, and not while put off`() {
        // A first look at the app is not nagged; ten changes without a backup are.
        assertNull(BackupReminder.due(stored = 9, atBackup = 0, lastBackup = null, snoozedUntil = null, now = now, zone = tehran))
        val first = BackupReminder.due(stored = 10, atBackup = 0, lastBackup = null, snoozedUntil = null, now = now, zone = tehran)
        assertTrue(first != null && "هنوز از داده‌ها پشتیبان نساخته‌اید" in first, first)
        // Six days after a backup, nothing; seven, with changes since, a reminder that counts them.
        val backup = Instant.parse("2026-11-01T12:00:00Z")
        assertNull(BackupReminder.due(stored = 50, atBackup = 40, lastBackup = backup, snoozedUntil = null, now = now.minusSeconds(86_400), zone = tehran))
        assertEquals(
            "آخرین پشتیبان ۷ روز پیش ساخته شد و از آن به بعد ۱۰ تغییر ثبت شده است.",
            BackupReminder.due(stored = 50, atBackup = 40, lastBackup = backup, snoozedUntil = null, now = now, zone = tehran),
        )
        // Nothing new since the backup, however old: nothing to say.
        assertNull(BackupReminder.due(stored = 40, atBackup = 40, lastBackup = backup, snoozedUntil = null, now = now, zone = tehran))
        // Put off: not before the day it was put off to.
        val snoozed = BackupReminder.snooze(now)
        assertNull(BackupReminder.due(stored = 50, atBackup = 40, lastBackup = backup, snoozedUntil = snoozed, now = now, zone = tehran))
        assertTrue(BackupReminder.due(stored = 50, atBackup = 40, lastBackup = backup, snoozedUntil = snoozed, now = snoozed, zone = tehran) != null)
        // The backup card says when the last one was made.
        assertEquals("آخرین پشتیبان: ۱۰ آبان ۱۴۰۵", BackupReminder.last(backup, tehran))
        assertEquals("هنوز پشتیبانی ساخته نشده است.", BackupReminder.last(null, tehran))
    }
}
