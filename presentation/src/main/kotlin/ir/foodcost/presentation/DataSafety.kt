package ir.foodcost.presentation

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Whether the stored data was read whole. While it was not, no figure is shown and nothing is
 * recorded, until the user chooses: bring the missing part back from a backup, or go on without it.
 */
data class DataIntegrity(
    /** Stored rows that could not be read and that the user has not chosen to go on without. */
    val unreadable: Int = 0,
    /** The data file was too damaged to open: it was set aside and the app opened empty. */
    val setAside: Boolean = false,
) {
    val verified: Boolean get() = unreadable == 0 && !setAside
}

/** A change refused while the stored data was not read whole and the user has not chosen what to do. */
class DataNotVerified : IllegalStateException("The stored data was not read whole; restore a backup or go on without the part that was not read")

/**
 * A reminder to make a backup. Android's own backup leaves this app's data out, so after the app is
 * removed or the phone changed, the data comes back only from a backup the user made.
 */
object BackupReminder {
    /** Days after a backup before changes made since are worth a reminder. */
    const val DAYS = 7L

    /** Changes before the first backup is asked for, so a first look at the app is not nagged. */
    const val FIRST = 10

    /** Days a reminder stays away after «بعداً». */
    const val SNOOZE_DAYS = 3L

    /**
     * What to say, or null when nothing: [stored] changes now, [atBackup] of them in the last backup,
     * made at [lastBackup] (null for never), and no reminder before [snoozedUntil].
     */
    fun due(stored: Int, atBackup: Int, lastBackup: Instant?, snoozedUntil: Instant?, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String? {
        val unsaved = stored - atBackup
        if (unsaved <= 0) return null
        if (snoozedUntil != null && now.isBefore(snoozedUntil)) return null
        if (lastBackup == null) {
            if (stored < FIRST) return null
            return "هنوز از داده‌ها پشتیبان نساخته‌اید. اگر برنامه حذف یا گوشی عوض شود، داده‌ها فقط از پشتیبانی که خودتان ساخته‌اید برمی‌گردند."
        }
        val days = ChronoUnit.DAYS.between(lastBackup.atZone(zone).toLocalDate(), now.atZone(zone).toLocalDate())
        if (days < DAYS) return null
        return "آخرین پشتیبان ${Persian.digits(days)} روز پیش ساخته شد و از آن به بعد ${Persian.digits(unsaved)} تغییر ثبت شده است."
    }

    /** When a reminder put off now may come back. */
    fun snooze(now: Instant): Instant = now.plus(Duration.ofDays(SNOOZE_DAYS))

    /** «آخرین پشتیبان: ۱۷ آبان ۱۴۰۵» for the backup card, or that there is none yet. */
    fun last(lastBackup: Instant?, zone: ZoneId = ZoneId.systemDefault()): String =
        lastBackup?.let { "آخرین پشتیبان: ${JalaliDate.of(it.atZone(zone).toLocalDate()).formatLong()}" }
            ?: "هنوز پشتیبانی ساخته نشده است."
}
