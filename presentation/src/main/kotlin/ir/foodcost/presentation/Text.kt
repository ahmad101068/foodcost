package ir.foodcost.presentation

import ir.foodcost.core.money.DisplayCurrency
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Collator
import java.util.Locale

/** Persian digits and a search-friendly form of names. */
object Persian {
    private const val PERSIAN_ZERO = '۰'
    private const val ARABIC_ZERO = '٠'

    /** Latin digits become Persian digits; everything else stays as it is. */
    fun digits(text: String): String = buildString(text.length) {
        for (c in text) append(if (c in '0'..'9') PERSIAN_ZERO + (c - '0') else c)
    }

    fun digits(value: Int): String = digits(value.toString())

    fun digits(value: Long): String = digits(value.toString())

    /** Persian and Arabic-Indic digits become Latin digits, so the text can be parsed. */
    fun latinDigits(text: String): String = buildString(text.length) {
        for (c in text) {
            append(
                when (c) {
                    in '۰'..'۹' -> '0' + (c - PERSIAN_ZERO)
                    in '٠'..'٩' -> '0' + (c - ARABIC_ZERO)
                    else -> c
                },
            )
        }
    }

    /**
     * The form used to match what people type: Arabic yeh and kaf become the Persian letters,
     * spaces, the zero-width non-joiner and diacritics are dropped, and Latin letters are lower-cased.
     * So «چرخ کرده» finds «چرخ‌کرده» and «علي» finds «علی».
     */
    fun searchKey(text: String): String = buildString(text.length) {
        for (c in latinDigits(text)) {
            when (c) {
                'ي', 'ى' -> append('ی') // ي ى → ی
                'ك' -> append('ک') // ك → ک
                'ة' -> append('ه') // ة → ه
                'أ', 'إ' -> append('ا') // أ إ → ا
                ' ', ' ', '‌', '‍', '‎', '‏', 'ـ' -> Unit
                in 'ً'..'ْ' -> Unit // diacritics
                else -> append(c.lowercaseChar())
            }
        }
    }

    fun matches(name: String, query: String): Boolean {
        val q = searchKey(query)
        return q.isEmpty() || searchKey(name).contains(q)
    }

    /** Alphabetical order for Persian names. */
    fun nameOrder(): Comparator<String> {
        val collator = Collator.getInstance(Locale.forLanguageTag("fa-IR"))
        return Comparator { a, b -> collator.compare(a, b) }
    }
}

/** Numbers as Persian readers expect them: Persian digits, ٬ between thousands and ٫ before decimals. */
object Numbers {
    const val GROUP_SEPARATOR = '٬'
    const val DECIMAL_SEPARATOR = '٫'
    private const val LEFT_TO_RIGHT_MARK = '‎'
    private val NUMBER = Regex("-?(\\d+(\\.\\d*)?|\\.\\d+)")

    /** 1234567.891 with two decimals is ۱٬۲۳۴٬۵۶۷٫۸۹. Zeros at the end of the decimals are dropped. */
    fun format(value: BigDecimal, maxFractionDigits: Int = 0): String {
        val rounded = value.setScale(maxFractionDigits, RoundingMode.HALF_UP)
        val plain = rounded.abs().toPlainString()
        val whole = plain.substringBefore('.')
        val fraction = plain.substringAfter('.', "").trimEnd('0')
        val grouped = whole.reversed().chunked(3).joinToString(GROUP_SEPARATOR.toString()).reversed()
        val text = if (fraction.isEmpty()) grouped else "$grouped$DECIMAL_SEPARATOR$fraction"
        // The mark keeps the minus sign on the left of the number inside right-to-left text.
        val sign = if (rounded.signum() < 0) "$LEFT_TO_RIGHT_MARK-" else ""
        return sign + Persian.digits(text)
    }

    fun format(value: Long): String = format(BigDecimal.valueOf(value))

    fun format(value: Int): String = format(value.toLong())

    /** A percentage such as ۳۴٫۸٪. */
    fun percent(value: BigDecimal, maxFractionDigits: Int = 1): String = format(value, maxFractionDigits) + "٪"

    /**
     * Reads a number the way people type it: Persian, Arabic or Latin digits; ٫ . or / as the
     * decimal point; ٬ , ، and spaces between thousands. Null when the text is not a plain number.
     */
    fun parse(text: String): BigDecimal? {
        val cleaned = buildString {
            for (c in Persian.latinDigits(text.trim())) {
                when (c) {
                    GROUP_SEPARATOR, ',', '،', ' ', ' ', ' ', '‌', '‎', '‏' -> Unit
                    DECIMAL_SEPARATOR, '/', '.' -> append('.')
                    '−' -> append('-')
                    else -> append(c)
                }
            }
        }
        if (!NUMBER.matches(cleaned)) return null
        return BigDecimal(cleaned)
    }

    /** A value as plain Latin digits, for putting back into a text field. */
    fun editable(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()
}

/**
 * A number being typed, shown with ٬ between thousands (1450000 as 1٬450٬000) so its zeros can be
 * counted at a glance. Only what is shown changes: the typed text stays as it is, and the offsets
 * carry the cursor between the two.
 */
class DigitGroups private constructor(
    val shown: String,
    private val shownOffsets: IntArray,
    private val typedOffsets: IntArray,
) {
    /** Where the cursor at [typed] in the typed text is in the shown text. */
    fun shownOffset(typed: Int): Int = shownOffsets[typed.coerceIn(0, shownOffsets.lastIndex)]

    /** Where the cursor at [shown] in the shown text is in the typed text. */
    fun typedOffset(shown: Int): Int = typedOffsets[shown.coerceIn(0, typedOffsets.lastIndex)]

    companion object {
        private val DECIMAL_MARKS = setOf('.', '/', Numbers.DECIMAL_SEPARATOR)

        /** Null when there is nothing to group: fewer than four digits before the point, or not a plain number. */
        fun of(typed: String): DigitGroups? {
            val point = typed.indexOfFirst { it in DECIMAL_MARKS }
            val end = if (point < 0) typed.length else point
            if (end < 4 || (0 until end).any { !typed[it].isDigit() }) return null
            if (point >= 0 && (point + 1 until typed.length).any { !typed[it].isDigit() }) return null
            val shown = StringBuilder()
            val shownOffsets = IntArray(typed.length + 1)
            val typedOffsets = IntArray(typed.length + (end - 1) / 3 + 1)
            for (i in typed.indices) {
                if (i in 1 until end && (end - i) % 3 == 0) {
                    typedOffsets[shown.length] = i
                    shown.append(Numbers.GROUP_SEPARATOR)
                }
                shownOffsets[i] = shown.length
                typedOffsets[shown.length] = i
                shown.append(typed[i])
            }
            shownOffsets[typed.length] = shown.length
            typedOffsets[shown.length] = typed.length
            return DigitGroups(shown.toString(), shownOffsets, typedOffsets)
        }
    }
}

/** Shows and reads money in the currency the user chose. Amounts are always stored in rials. */
class MoneyFormat(val currency: DisplayCurrency) {
    val unit: String = Labels.currency(currency)

    /** The number alone: ۴۲۲٬۴۰۸ */
    fun amount(rials: BigDecimal): String = Numbers.format(currency.display(rials), currency.fractionDigits)

    fun amount(rials: Long): String = amount(BigDecimal.valueOf(rials))

    /** With the unit: ۴۲۲٬۴۰۸ تومان */
    fun full(rials: BigDecimal): String = "${amount(rials)} $unit"

    fun full(rials: Long): String = full(BigDecimal.valueOf(rials))

    /** An amount as plain Latin digits in this currency, for putting back into a text field. */
    fun editable(rials: Long): String = Numbers.editable(currency.exact(rials))

    /** Reads an amount typed in this currency. Null when it is not a number, is negative, or is finer than a rial. */
    fun parse(text: String): Long? {
        val value = Numbers.parse(text) ?: return null
        if (value.signum() < 0) return null
        return try {
            currency.toRials(value)
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: ArithmeticException) {
            null
        }
    }
}
