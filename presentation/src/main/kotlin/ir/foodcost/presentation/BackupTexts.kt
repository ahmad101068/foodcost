package ir.foodcost.presentation

import ir.foodcost.data.ledger.BackupPreview

/** What the backup screens say: the look before an import, the password advice and stored rows not read. */
object BackupTexts {
    /** Why and how to lock a backup, said where it is made. */
    const val PASSWORD_ADVICE =
        "فایل پشتیبان همهٔ قیمت‌های خرید و دستورهای شماست. اگر آن را برای کسی می‌فرستید یا روی فضای ابری می‌گذارید، رمز بگذارید. " +
            "رمز را جایی یادداشت کنید: بدون آن فایل باز نمی‌شود و راهی برای بازیابی‌اش نیست."

    const val WRONG_PASSWORD = "رمز درست نیست، یا فایل بعد از رمزگذاری عوض شده است."

    /**
     * The changes a backup would add, from when and where, for a look before importing it, and
     * whether the file was [sealed]: one locked with a password cannot have been changed since,
     * one without could have been by anyone who had it.
     */
    fun preview(p: BackupPreview, sealed: Boolean): String = buildString {
        val parts = listOfNotNull(
            count(p.ingredients, "ماده"),
            count(p.prices, "قیمت خرید"),
            count(p.recipes, "دستور یا نسخهٔ دستور"),
            count(p.menuPrices, "قیمت منو"),
            count(p.settings, "تنظیمات"),
            count(p.periods, "بستن یا بازکردن دوره"),
        )
        append("این پشتیبان ${Persian.digits(p.fresh)} تغییر تازه دارد")
        append(if (parts.isEmpty()) "." else ": ${parts.joinToString("، ")}.")
        val first = p.firstDay
        val last = p.lastDay
        if (first != null && last != null) {
            appendLine()
            if (first == last) {
                append("همه در ${JalaliDate.of(first).formatLong()} ثبت شده‌اند")
            } else {
                append("از ${JalaliDate.of(first).formatLong()} تا ${JalaliDate.of(last).formatLong()} ثبت شده‌اند")
            }
            append(
                when (p.otherDevices) {
                    0 -> "، روی همین گوشی."
                    1 -> "، روی یک گوشی دیگر."
                    else -> "، روی ${Persian.digits(p.otherDevices)} گوشی دیگر."
                },
            )
        }
        if (p.byOthers > 0) {
            appendLine()
            append("${Persian.digits(p.byOthers)} تغییر را کسی جز مالک ثبت کرده است.")
        }
        if (p.refused > 0) {
            appendLine()
            append("${Persian.digits(p.refused)} تغییر با قاعده‌ها جور نیست، مثل قیمتی برای روزی در دورهٔ بسته؛ نگه داشته می‌شود ولی چیزی را عوض نمی‌کند.")
        }
        appendLine()
        append(if (sealed) SEALED else NOT_SEALED)
    }

    /** Said of a backup locked with a password, which opens only unchanged. */
    const val SEALED = "این فایل رمز داشت و از وقتی ساخته شده عوض نشده است."

    /** Said of a backup without a password: nothing shows who made it or that it was not changed. */
    const val NOT_SEALED =
        "این فایل رمز ندارد: هر کس به آن دسترسی داشته می‌توانسته آن را عوض کند. فقط فایلی را وارد کنید که خودتان ساخته‌اید یا از منبعش مطمئنید."

    /** Stored rows the app could not read, and what that means for the figures and for backups. */
    fun unreadable(count: Int): String =
        "${Persian.digits(count)} تغییر ذخیره‌شده خوانده نشد. عددهایی که می‌بینید این تغییرها را ندارند و پشتیبانی که بسازید هم آن‌ها را نخواهد داشت. " +
            "این ردیف‌ها روی گوشی می‌مانند و پاک نمی‌شوند؛ پیش از تصمیم مهم با پشتیبانی تماس بگیرید."

    /** Why a backup with changes that clash with ones here, by id or by number, is not imported at all. */
    fun conflicts(count: Int): String =
        "این پشتیبان ${Persian.digits(count)} تغییر دارد که شناسه یا شماره‌اش با تغییر دیگری یکی است ولی خودش فرق دارد. " +
            "فایل خراب یا دست‌کاری شده است و چیزی از آن وارد نمی‌شود."

    /** Why a backup that would move the figures of a closed period is not imported at all. */
    fun changesClosed(count: Int): String =
        "این پشتیبان رقم ${if (count == 1) "یک دورهٔ بسته" else "${Persian.digits(count)} دورهٔ بسته"} را عوض می‌کند و رقم دورهٔ بسته نباید عوض شود، " +
            "پس چیزی از آن وارد نمی‌شود. اگر این تغییرها درست‌اند، اول دوره را باز کنید."

    /** Said once when the app found its data file damaged and could not read it at all. */
    fun setAside(file: String): String =
        "پروندهٔ داده‌های برنامه خراب شده بود و خوانده نمی‌شد. چیزی پاک نشد: همان پرونده با نام «$file» کنار گذاشته شد " +
            "و برنامه با داده‌های خالی باز شد. داده‌ها را از آخرین پشتیبان برگردانید: «تنظیمات»، بخش «پشتیبان»، «بازگرداندن»."

    /** Closed periods whose figures, worked out again, are not the official ones their close kept. */
    fun closedMismatches(count: Int): String =
        "رقم ${if (count == 1) "یک دورهٔ بسته" else "${Persian.digits(count)} دورهٔ بسته"} با حساب دوبارهٔ همان روزها جور نیست. " +
            "رقم رسمی همان است که موقع بستن ثبت شد؛ تا دوره باز و دوباره بسته نشده، به حساب دوبارهٔ آن روزها تکیه نکنید."

    /** The same in one line, for the top of the screens with figures and for a calculation sent as text. */
    fun unreadableShort(count: Int): String =
        "${Persian.digits(count)} تغییر ذخیره‌شده خوانده نشد؛ عددها ممکن است کامل نباشند."

    private fun count(n: Int, what: String): String? = if (n > 0) "${Persian.digits(n)} $what" else null
}
