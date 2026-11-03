package ir.foodcost.presentation

import ir.foodcost.data.ledger.BackupPreview

/** What the backup screens say: the look before an import, the password advice and stored rows not read. */
object BackupTexts {
    /** Why and how to lock a backup, said where it is made. */
    const val PASSWORD_ADVICE =
        "فایل پشتیبان همهٔ قیمت‌های خرید و دستورهای شماست. اگر آن را برای کسی می‌فرستید یا روی فضای ابری می‌گذارید، رمز بگذارید. " +
            "رمز را جایی یادداشت کنید: بدون آن فایل باز نمی‌شود و راهی برای بازیابی‌اش نیست."

    const val WRONG_PASSWORD = "رمز درست نیست، یا فایل بعد از رمزگذاری عوض شده است."

    /** The changes a backup would add, from when and where, for a look before importing it. */
    fun preview(p: BackupPreview): String = buildString {
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
    }

    /** Stored rows the app could not read, and what that means for the figures and for backups. */
    fun unreadable(count: Int): String =
        "${Persian.digits(count)} تغییر ذخیره‌شده خوانده نشد. عددهایی که می‌بینید این تغییرها را ندارند و پشتیبانی که بسازید هم آن‌ها را نخواهد داشت. " +
            "این ردیف‌ها روی گوشی می‌مانند و پاک نمی‌شوند؛ پیش از تصمیم مهم با پشتیبانی تماس بگیرید."

    private fun count(n: Int, what: String): String? = if (n > 0) "${Persian.digits(n)} $what" else null
}
