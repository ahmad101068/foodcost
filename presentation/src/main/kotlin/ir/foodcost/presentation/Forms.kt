package ir.foodcost.presentation

import ir.foodcost.core.Decimals
import ir.foodcost.core.asFraction
import ir.foodcost.core.costing.CostingRules
import ir.foodcost.core.model.ComponentRef
import ir.foodcost.core.model.Ingredient
import ir.foodcost.core.model.IngredientId
import ir.foodcost.core.model.PurchasePrice
import ir.foodcost.core.model.QtyBasis
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.model.RecipeLine
import ir.foodcost.core.model.RecipeVersion
import ir.foodcost.core.model.YieldStage
import ir.foodcost.core.money.PriceRounding
import ir.foodcost.core.over
import ir.foodcost.core.pricing.CostBasis
import ir.foodcost.core.pricing.OnlineTerms
import ir.foodcost.core.pricing.Overhead
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.core.pricing.PricingSettings
import ir.foodcost.core.units.Conversions
import ir.foodcost.core.units.Dimension
import ir.foodcost.core.units.UnitConverter
import ir.foodcost.core.units.Uom
import ir.foodcost.data.ledger.IngredientRecord
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** The fields a form can report a problem on. */
enum class Field {
    NAME, DATE, QUANTITY, AMOUNT, DISCOUNT, TAX, DELIVERY, YIELD, PIECE_WEIGHT, DENSITY, PORTIONS, MISC, OUTPUT, PERCENT, VAT, ROW,
    EXTRA, FIXED_COSTS, MONTHLY_PORTIONS, COMMISSION, UPLIFT,
}

/** A checked form: either a value ready to save or, field by field, what to fix. */
sealed interface Checked<out T> {
    data class Valid<out T>(val value: T) : Checked<T>

    data class Invalid(val errors: Map<Field, String>) : Checked<Nothing>
}

/** Collects the problems found while a form is read. */
internal class Problems {
    private val errors = LinkedHashMap<Field, String>()

    fun add(field: Field, message: String) {
        if (field !in errors) errors[field] = message
    }

    fun has(field: Field): Boolean = field in errors

    fun decimal(text: String, field: Field, message: String, valid: (BigDecimal) -> Boolean): BigDecimal? {
        val value = Numbers.parse(text)
        if (value == null || !valid(value)) {
            add(field, message)
            return null
        }
        return value
    }

    fun optionalDecimal(text: String, field: Field, message: String, valid: (BigDecimal) -> Boolean): BigDecimal? =
        if (text.isBlank()) null else decimal(text, field, message, valid)

    fun money(text: String, money: MoneyFormat, field: Field, message: String): Long? {
        if (text.isBlank()) return null
        return money.parse(text) ?: run {
            add(field, message)
            null
        }
    }

    fun <T> result(build: () -> T): Checked<T> = if (errors.isEmpty()) Checked.Valid(build()) else Checked.Invalid(errors.toMap())
}

private val HUNDRED = Decimals.HUNDRED

/** An ingredient ready to be created or saved. */
data class IngredientDraft(
    val name: String,
    val dimension: Dimension,
    val category: String?,
    val yields: List<YieldStage>,
    val conversions: Conversions,
)

/** The ingredient form, as typed. */
data class IngredientInput(
    val name: String = "",
    val dimension: Dimension = Dimension.MASS,
    val yieldPercent: String = "100",
    val category: String = "",
    val gramsPerPiece: String = "",
    val gramsPerMilliliter: String = "",
) {
    fun check(): Checked<IngredientDraft> {
        val p = Problems()
        val cleanName = name.trim()
        if (cleanName.isEmpty()) p.add(Field.NAME, "نام ماده را بنویسید.")
        val yieldValue = p.decimal(yieldPercent.ifBlank { "100" }, Field.YIELD, "بازدهی باید بیشتر از صفر و حداکثر ۱۰۰ باشد.") {
            it.signum() > 0 && it <= HUNDRED
        }
        val piece = p.optionalDecimal(gramsPerPiece, Field.PIECE_WEIGHT, "وزن هر عدد باید بیشتر از صفر باشد.") { it.signum() > 0 }
        val density = p.optionalDecimal(gramsPerMilliliter, Field.DENSITY, "وزن هر میلی‌لیتر باید بیشتر از صفر باشد.") { it.signum() > 0 }
        return p.result {
            val fraction = yieldValue!!.asFraction()
            IngredientDraft(
                name = cleanName,
                dimension = dimension,
                category = category.trim().ifEmpty { null },
                yields = if (fraction.compareTo(BigDecimal.ONE) == 0) emptyList() else listOf(YieldStage(YIELD_STAGE, fraction)),
                conversions = Conversions(piece, density),
            )
        }
    }

    companion object {
        const val YIELD_STAGE = "پاک کردن"

        fun of(record: IngredientRecord): IngredientInput = IngredientInput(
            name = record.name.orEmpty(),
            dimension = record.baseDimension ?: Dimension.MASS,
            yieldPercent = Numbers.editable(record.yields.fold(BigDecimal.ONE) { acc, s -> acc.multiply(s.ratio) }.multiply(HUNDRED)),
            category = record.category.orEmpty(),
            gramsPerPiece = record.conversions.gramsPerPiece?.let(Numbers::editable).orEmpty(),
            gramsPerMilliliter = record.conversions.gramsPerMilliliter?.let(Numbers::editable).orEmpty(),
        )
    }
}

/** The purchase price form, as typed. Amounts are in the display currency. */
data class PriceInput(
    val date: String,
    val packageQty: String = "1",
    val packageUom: Uom,
    val amount: String = "",
    val discount: String = "",
    val tax: String = "",
    val taxRecoverable: Boolean = false,
    val delivery: String = "",
    val supplier: String = "",
) {
    fun check(ingredientId: IngredientId, money: MoneyFormat): Checked<PurchasePrice> {
        val p = Problems()
        val day = JalaliDate.parse(date)
        if (day == null) p.add(Field.DATE, "تاریخ را به شکل ۱۴۰۵/۰۸/۰۹ بنویسید.")
        val qty = p.decimal(packageQty, Field.QUANTITY, "مقدار باید بیشتر از صفر باشد.") { it.signum() > 0 }
        val gross = p.money(amount, money, Field.AMOUNT, "مبلغ درست نیست.")
        if (amount.isBlank()) p.add(Field.AMOUNT, "مبلغ را وارد کنید.")
        if (gross == 0L) p.add(Field.AMOUNT, "مبلغ باید بیشتر از صفر باشد.")
        val off = p.money(discount, money, Field.DISCOUNT, "تخفیف درست نیست.") ?: 0L
        val taxRials = p.money(tax, money, Field.TAX, "مالیات درست نیست.") ?: 0L
        val deliveryRials = p.money(delivery, money, Field.DELIVERY, "هزینهٔ حمل درست نیست.") ?: 0L
        if (gross != null && off > gross) p.add(Field.DISCOUNT, "تخفیف از مبلغ بیشتر است.")
        return p.result {
            PurchasePrice(
                ingredientId = ingredientId,
                effectiveFrom = day!!.toLocalDate(),
                packageQty = qty!!,
                packageUom = packageUom,
                grossRials = gross!!,
                discountRials = off,
                taxRials = taxRials,
                taxRecoverable = taxRecoverable,
                deliveryRials = deliveryRials,
                supplier = supplier.trim().ifEmpty { null },
            )
        }
    }

    /** What one kilo, liter or piece costs at what has been typed so far; null until it can be worked out. */
    fun unitPrice(ingredient: Ingredient, money: MoneyFormat): String? {
        val qty = Numbers.parse(packageQty)?.takeIf { it.signum() > 0 } ?: return null
        val gross = money.parse(amount) ?: return null
        fun extra(text: String): Long? = if (text.isBlank()) 0L else money.parse(text)
        val off = extra(discount) ?: return null
        val taxRials = extra(tax) ?: return null
        val deliveryRials = extra(delivery) ?: return null
        val net = gross - off + (if (taxRecoverable) 0L else taxRials) + deliveryRials
        if (net < 0) return null
        val base = UnitConverter.toBase(qty, packageUom, ingredient.baseDimension, ingredient.conversions) ?: return null
        return Prices.perUnit(BigDecimal.valueOf(net).over(base), ingredient.baseDimension, money)
    }

    companion object {
        fun new(today: LocalDate, dimension: Dimension): PriceInput =
            PriceInput(date = JalaliDate.of(today).format(), packageUom = UnitOptions.defaultForPurchase(dimension))
    }
}

/** A recipe line form, as typed. */
data class LineInput(
    val qty: String = "",
    val uom: Uom,
    val basis: QtyBasis = QtyBasis.USABLE,
) {
    fun check(component: ComponentRef): Checked<RecipeLine> {
        val p = Problems()
        val q = p.decimal(qty, Field.QUANTITY, "مقدار را وارد کنید.") { it.signum() > 0 }
        val lineBasis = if (component is ComponentRef.IngredientRef) basis else QtyBasis.USABLE
        return p.result { RecipeLine(component, q, uom, lineBasis) }
    }

    companion object {
        fun of(line: RecipeLine): LineInput = LineInput(line.qty?.let(Numbers::editable).orEmpty(), line.uom, line.basis)
    }
}

/** A recipe's name, kind and menu section, as typed. */
data class RecipeInput(
    val name: String = "",
    val kind: RecipeKind = RecipeKind.MENU_ITEM,
    val category: String = "",
) {
    fun check(): Checked<RecipeInput> {
        val p = Problems()
        if (name.isBlank()) p.add(Field.NAME, "نام دستور را بنویسید.")
        return p.result { copy(name = name.trim(), category = category.trim()) }
    }

    val categoryOrNull: String? get() = category.trim().ifEmpty { null }
}

/** How many portions a recipe makes, its misc share and its total output. */
data class Batch(val portions: BigDecimal, val miscPercent: BigDecimal?, val outputQty: BigDecimal?, val outputUom: Uom?)

/** The batch form, as typed. A blank misc share means the default; a blank output means none. */
data class BatchInput(
    val portions: String = "1",
    val miscPercent: String = "",
    val outputQty: String = "",
    val outputUom: Uom = Uom.KILOGRAM,
) {
    fun check(): Checked<Batch> {
        val p = Problems()
        val n = p.decimal(portions, Field.PORTIONS, "تعداد پرس باید بیشتر از صفر باشد.") { it.signum() > 0 }
        val misc = p.optionalDecimal(miscPercent, Field.MISC, "درصد هزینه‌های جزئی باید از ۰ تا کمتر از ۱۰۰ باشد.") {
            it.signum() >= 0 && it < HUNDRED
        }
        val output = p.optionalDecimal(outputQty, Field.OUTPUT, "خروجی باید بیشتر از صفر باشد.") { it.signum() > 0 }
        return p.result { Batch(n!!, misc, output, if (output == null) null else outputUom) }
    }

    companion object {
        fun of(version: RecipeVersion): BatchInput = BatchInput(
            portions = Numbers.editable(version.portions),
            miscPercent = version.miscPercent?.let(Numbers::editable).orEmpty(),
            outputQty = version.outputQty?.let(Numbers::editable).orEmpty(),
            outputUom = version.outputUom ?: Uom.KILOGRAM,
        )
    }
}

/** Reads a menu price typed in the display currency. */
fun checkMenuPrice(text: String, money: MoneyFormat): Checked<Long> {
    val p = Problems()
    val rials = p.money(text, money, Field.AMOUNT, "مبلغ درست نیست.")
    if (text.isBlank()) p.add(Field.AMOUNT, "قیمت را وارد کنید.")
    if (rials == 0L) p.add(Field.AMOUNT, "قیمت باید بیشتر از صفر باشد.")
    return p.result { rials!! }
}

enum class MethodChoice { FOOD_COST, MARGIN, MARKUP }

/** The pricing rules and misc share, ready to save. */
data class SettingsDraft(val costing: CostingRules, val pricing: PricingSettings)

/** The settings form, as typed. Amounts are in [MoneyFormat]'s currency. */
data class SettingsInput(
    val method: MethodChoice,
    val percent: String,
    val vatPercent: String,
    val menuPriceIncludesVat: Boolean,
    val roundingStepRials: Long,
    val roundingDirection: PriceRounding.Direction,
    val miscPercent: String,
    /** What a margin or markup is taken on: the food alone, or every cost entered. */
    val basis: CostBasis = CostBasis.FOOD_COST,
    /** The month's fixed costs (wages, rent, energy), spread over [monthlyPortions]; both blank for none. */
    val monthlyFixed: String = "",
    val monthlyPortions: String = "",
    val onlineCommission: String = "",
    val onlineDiscount: String = "",
    val onlineUplift: String = "",
) {
    /** The portion's share of the month's fixed costs, as typed so far; null until both are readable. */
    fun overheadShare(money: MoneyFormat): String? {
        val fixed = money.parse(monthlyFixed)?.takeIf { it >= 0 } ?: return null
        val portions = Numbers.parse(monthlyPortions)?.takeIf { it.signum() > 0 } ?: return null
        return money.full(Overhead(fixed, portions).perPortion)
    }

    fun check(currentPricing: PricingSettings, currentCosting: CostingRules, money: MoneyFormat): Checked<SettingsDraft> {
        val p = Problems()
        val value = p.decimal(percent, Field.PERCENT, percentMessage()) {
            when (method) {
                MethodChoice.FOOD_COST -> it.signum() > 0 && it < HUNDRED
                MethodChoice.MARGIN -> it.signum() >= 0 && it < HUNDRED
                MethodChoice.MARKUP -> it.signum() >= 0
            }
        }
        val vat = p.decimal(vatPercent.ifBlank { "0" }, Field.VAT, "مالیات باید از ۰ تا کمتر از ۱۰۰ باشد.") {
            it.signum() >= 0 && it < HUNDRED
        }
        val misc = p.decimal(miscPercent.ifBlank { "0" }, Field.MISC, "درصد هزینه‌های جزئی باید از ۰ تا کمتر از ۱۰۰ باشد.") {
            it.signum() >= 0 && it < HUNDRED
        }
        var overhead: Overhead? = null
        if (monthlyFixed.isNotBlank() || monthlyPortions.isNotBlank()) {
            val fixed = money.parse(monthlyFixed)?.takeIf { it >= 0 }
            val portions = Numbers.parse(monthlyPortions)?.takeIf { it.signum() > 0 }
            if (fixed == null) p.add(Field.FIXED_COSTS, "هزینهٔ ثابت ماهانه را بنویسید، یا هر دو خانه را خالی بگذارید.")
            if (portions == null) p.add(Field.MONTHLY_PORTIONS, "تعداد پرس‌های ماه باید بیشتر از صفر باشد.")
            if (fixed != null && portions != null) overhead = Overhead(fixed, portions)
        }
        val commission = p.decimal(onlineCommission.ifBlank { "0" }, Field.COMMISSION, "کمیسیون باید از ۰ تا کمتر از ۱۰۰ باشد.") {
            it.signum() >= 0 && it < HUNDRED
        }
        val discount = p.decimal(onlineDiscount.ifBlank { "0" }, Field.DISCOUNT, "تخفیف باید از ۰ تا کمتر از ۱۰۰ باشد.") {
            it.signum() >= 0 && it < HUNDRED
        }
        val uplift = p.decimal(onlineUplift.ifBlank { "0" }, Field.UPLIFT, "درصد گران‌تر بودن آنلاین نمی‌تواند منفی باشد.") {
            it.signum() >= 0
        }
        return p.result {
            val newMethod = when (method) {
                MethodChoice.FOOD_COST -> PricingMethod.TargetFoodCost(value!!)
                MethodChoice.MARGIN -> PricingMethod.TargetMargin(value!!, basis)
                MethodChoice.MARKUP -> PricingMethod.Markup(value!!, basis)
            }
            SettingsDraft(
                costing = currentCosting.copy(defaultMiscPercent = misc!!),
                pricing = currentPricing.copy(
                    defaultMethod = newMethod,
                    vatPercent = vat!!,
                    menuPriceIncludesVat = menuPriceIncludesVat,
                    rounding = PriceRounding(roundingStepRials, roundingDirection),
                    overhead = overhead,
                    online = OnlineTerms(commission!!, discount!!, uplift!!),
                ),
            )
        }
    }

    /**
     * The form with [choice] as its method and the number turned into that method's equivalent,
     * so switching the method leaves the suggested price as it was: a 35% food cost is a 65% margin
     * and a 185.71% markup. A number that cannot be read is left as typed.
     */
    fun withMethod(choice: MethodChoice): SettingsInput {
        if (choice == method) return this
        // On every cost entered, a margin is no longer the other side of a food cost share.
        if (basis == CostBasis.FULL_COST) return copy(method = choice)
        val converted = Numbers.parse(percent)?.let { equivalent(it, method, choice) }
        return copy(method = choice, percent = converted?.let { Numbers.editable(it) } ?: percent)
    }

    /** The number in the other two methods: «یعنی حاشیهٔ سود ۶۵٪ و مارک‌آپ ۱۸۵٫۷٪». */
    fun equivalents(): String? {
        if (basis == CostBasis.FULL_COST && method != MethodChoice.FOOD_COST) return null
        val value = Numbers.parse(percent) ?: return null
        val others = MethodChoice.entries.filter { it != method }.map { other ->
            val converted = equivalent(value, method, other) ?: return null
            "${shortName(other)} ${Numbers.percent(converted)}"
        }
        return "یعنی ${others.joinToString(" و ")}"
    }

    private fun percentMessage(): String = when (method) {
        MethodChoice.FOOD_COST -> "فودکاست هدف باید بیشتر از ۰ و کمتر از ۱۰۰ باشد."
        MethodChoice.MARGIN -> "حاشیهٔ سود باید از ۰ تا کمتر از ۱۰۰ باشد."
        MethodChoice.MARKUP -> "مارک‌آپ نمی‌تواند منفی باشد."
    }

    companion object {
        private fun shortName(choice: MethodChoice): String = when (choice) {
            MethodChoice.FOOD_COST -> "فودکاست"
            MethodChoice.MARGIN -> "حاشیهٔ سود"
            MethodChoice.MARKUP -> "مارک‌آپ"
        }

        /**
         * [value] of method [from] as method [to], on the food cost: the food cost is the cost's share
         * of the net price, the margin what is left of it, and the markup what is added to the cost.
         * Null when [value] is out of range for [from].
         */
        internal fun equivalent(value: BigDecimal, from: MethodChoice, to: MethodChoice): BigDecimal? {
            val foodCost = when (from) {
                MethodChoice.FOOD_COST -> value
                MethodChoice.MARGIN -> HUNDRED - value
                MethodChoice.MARKUP -> if (value.signum() < 0) return null else HUNDRED_SQUARED.divide(HUNDRED + value, 10, RoundingMode.HALF_UP)
            }
            if (foodCost.signum() <= 0 || foodCost > HUNDRED) return null
            val result = when (to) {
                MethodChoice.FOOD_COST -> foodCost
                MethodChoice.MARGIN -> HUNDRED - foodCost
                MethodChoice.MARKUP -> HUNDRED_SQUARED.divide(foodCost, 10, RoundingMode.HALF_UP) - HUNDRED
            }
            return result.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
        }

        private val HUNDRED_SQUARED = BigDecimal(10_000)

        /** Rounding steps offered, in rials: 1,000 to 100,000 toman. */
        val ROUNDING_STEPS: List<Long> = listOf(10_000L, 50_000L, 100_000L, 500_000L, 1_000_000L)

        fun of(pricing: PricingSettings, costing: CostingRules, money: MoneyFormat): SettingsInput {
            val m = pricing.defaultMethod
            val (choice, value) = when (m) {
                is PricingMethod.TargetFoodCost -> MethodChoice.FOOD_COST to m.percent
                is PricingMethod.TargetMargin -> MethodChoice.MARGIN to m.percent
                is PricingMethod.Markup -> MethodChoice.MARKUP to m.percent
            }
            fun percentOrBlank(v: BigDecimal) = if (v.signum() == 0) "" else Numbers.editable(v)
            return SettingsInput(
                method = choice,
                percent = Numbers.editable(value),
                vatPercent = Numbers.editable(pricing.vatPercent),
                menuPriceIncludesVat = pricing.menuPriceIncludesVat,
                roundingStepRials = pricing.rounding.stepRials,
                roundingDirection = pricing.rounding.direction,
                miscPercent = Numbers.editable(costing.defaultMiscPercent),
                basis = when (m) {
                    is PricingMethod.TargetMargin -> m.basis
                    is PricingMethod.Markup -> m.basis
                    is PricingMethod.TargetFoodCost -> CostBasis.FOOD_COST
                },
                monthlyFixed = pricing.overhead?.monthlyFixedRials?.let { money.editable(it) }.orEmpty(),
                monthlyPortions = pricing.overhead?.monthlyPortions?.let { Numbers.editable(it) }.orEmpty(),
                onlineCommission = percentOrBlank(pricing.online.commissionPercent),
                onlineDiscount = percentOrBlank(pricing.online.discountPercent),
                onlineUplift = percentOrBlank(pricing.online.priceUpliftPercent),
            )
        }
    }
}
