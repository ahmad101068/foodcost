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
    fun preview(p: BackupPreview, sealed: Boolean, unreadable: Int = 0): String = buildString {
        if (unreadable > 0) {
            appendLine(emergency(unreadable))
        }
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

    /** Stored rows the app could not read, said as soon as they are found: what is held back, and the ways on. */
    fun unreadable(count: Int): String =
        "${Persian.digits(count)} تغییر ذخیره‌شده خوانده نشد. عددهای بدون آن‌ها ممکن است درست نباشند، پس تا تصمیم شما هیچ عددی نشان داده نمی‌شود و چیزی ثبت نمی‌شود. " +
            "این ردیف‌ها پاک نمی‌شوند. در «تنظیمات»، بخش «پشتیبان» می‌توانید پشتیبانی را که آن‌ها را دارد برگردانید، " +
            "از همهٔ داده‌ها همراه متن این ردیف‌ها پشتیبان اضطراری بسازید، یا آگاهانه بدون آن‌ها ادامه دهید."

    /** In place of a figure while the stored data was not read whole. */
    const val UNVERIFIED_RESULT =
        "تا تصمیم شما دربارهٔ داده‌های خوانده‌نشده عددی نشان داده نمی‌شود؛ راه‌ها در «تنظیمات»، بخش «پشتیبان» است."

    /** In place of a price of an ingredient while the stored data was not read whole. */
    const val UNVERIFIED_PRICE = "قیمت تا تصمیم شما دربارهٔ داده‌های خوانده‌نشده نشان داده نمی‌شود."

    /** In place of an ingredient's price history while the stored data was not read whole. */
    const val UNVERIFIED_HISTORY = "سابقهٔ قیمت تا تصمیم شما دربارهٔ داده‌های خوانده‌نشده نشان داده نمی‌شود."

    /** The line on top of every screen while the stored data was not read whole. */
    const val LOCKED_BANNER =
        "داده‌های مالی ناقص یا غیرقابل تأییدند؛ عددها و ثبت تا تصمیم شما بسته است. برای دیدن راه‌ها اینجا را بزنید."

    /** In the backup card while the stored data was not read whole: the ways on. */
    fun locked(integrity: DataIntegrity): String = if (integrity.setAside) {
        "پروندهٔ داده‌ها خراب بود و کنار گذاشته شد؛ برنامه با داده‌های خالی باز شده و تا تصمیم شما چیزی ثبت نمی‌شود. " +
            "آخرین پشتیبان را برگردانید. اگر پشتیبانی ندارید، پروندهٔ خراب را برای بررسی ذخیره کنید و بعد با داده‌های خالی شروع کنید."
    } else {
        "${Persian.digits(integrity.unreadable)} تغییر ذخیره‌شده خوانده نشد و تا تصمیم شما عددی نشان داده نمی‌شود و چیزی ثبت نمی‌شود. " +
            "پشتیبانی را که آن تغییرها را دارد برگردانید. اگر پشتیبانی ندارید یا آن تغییرها بعد از آخرین پشتیبان ثبت شده‌اند، " +
            "اول یک پشتیبان اضطراری بسازید و بعد بدون آن‌ها ادامه دهید."
    }

    /** Asked before the app goes on without the part it could not read. */
    fun continueWithout(integrity: DataIntegrity): String = if (integrity.setAside) {
        "برنامه با داده‌های خالی ادامه می‌دهد و ثبت دوباره باز می‌شود. پروندهٔ خراب روی گوشی می‌ماند و پاک نمی‌شود، " +
            "ولی برنامه از آن چیزی نمی‌خواند؛ اگر پشتیبانی پیدا کردید، هر وقت بخواهید آن را برگردانید."
    } else {
        "عددها بدون ${Persian.digits(integrity.unreadable)} تغییرِ خوانده‌نشده حساب می‌شوند و ثبت و پشتیبان دوباره باز می‌شود. " +
            "متن این ردیف‌ها روی گوشی می‌ماند و پاک نمی‌شود؛ اگر بعداً پشتیبانی را برگردانید که آن‌ها را دارد، دوباره خوانده می‌شوند. " +
            "اگر هنوز پشتیبان اضطراری نساخته‌اید، اول آن را بسازید."
    }

    /** What an emergency backup is, said where it is made. */
    const val EMERGENCY_ADVICE =
        "پشتیبان اضطراری همهٔ تغییرهای خوانده‌شده را دارد و متن ردیف‌های خراب را هم جدا نگه می‌دارد تا چیزی از دست نرود. " +
            "کامل نیست: هنگام برگرداندن گفته می‌شود چه چیزی ندارد، و نسخه‌های قدیمی برنامه آن را باز نمی‌کنند."

    /** Said of a backup that carries rows its phone could not read. */
    fun emergency(unreadable: Int): String =
        "این پشتیبان اضطراری است: ${Persian.digits(unreadable)} تغییر روی گوشی‌ای که آن را ساخته خوانده نمی‌شد و در آن نیست. " +
            "عددها بدون آن‌ها حساب می‌شوند."

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
            "و برنامه با داده‌های خالی باز شد. داده‌ها را از آخرین پشتیبان برگردانید: «تنظیمات»، بخش «پشتیبان»، «بازگرداندن». " +
            "اگر پشتیبانی ندارید، همان‌جا پروندهٔ خراب را برای بررسی ذخیره کنید و با داده‌های خالی شروع کنید."

    /** Closed periods whose figures, worked out again, are not the official ones their close kept. */
    fun closedMismatches(count: Int): String =
        "رقم ${if (count == 1) "یک دورهٔ بسته" else "${Persian.digits(count)} دورهٔ بسته"} با حساب دوبارهٔ همان روزها جور نیست. " +
            "رقم رسمی همان است که موقع بستن ثبت شد؛ تا دوره باز و دوباره بسته نشده، به حساب دوبارهٔ آن روزها تکیه نکنید."

    /** The same in one line, for the top of the screens with figures and for a calculation sent as text. */
    fun unreadableShort(count: Int): String =
        "${Persian.digits(count)} تغییر ذخیره‌شده خوانده نشد؛ عددها ممکن است کامل نباشند."

    private fun count(n: Int, what: String): String? = if (n > 0) "${Persian.digits(n)} $what" else null
}
