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
import ir.foodcost.core.pricing.PricingScope
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
    EXTRA, FIXED_COSTS, MONTHLY_PORTIONS, COMMISSION, UPLIFT, CATEGORY,
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
    fun unitPrice(ingredient: Ingredient, money: MoneyFormat): String? =
        perBaseUnit(ingredient, money)?.let { Prices.perUnit(it, ingredient.baseDimension, money) }

    /** The same in rials per kilo, liter or piece, for comparing with the last price. */
    fun unitRials(ingredient: Ingredient, money: MoneyFormat): Long? =
        perBaseUnit(ingredient, money)?.multiply(Prices.shownUnitSize(ingredient.baseDimension))?.setScale(0, RoundingMode.HALF_UP)
            ?.takeIf { it <= BigDecimal(MoneyFormat.MAX_RIALS) }?.toLong()

    private fun perBaseUnit(ingredient: Ingredient, money: MoneyFormat): BigDecimal? {
        val qty = Numbers.parse(packageQty)?.takeIf { it.signum() > 0 } ?: return null
        val gross = money.parse(amount) ?: return null
        fun extra(text: String): Long? = if (text.isBlank()) 0L else money.parse(text)
        val off = extra(discount) ?: return null
        val taxRials = extra(tax) ?: return null
        val deliveryRials = extra(delivery) ?: return null
        val net = gross - off + (if (taxRecoverable) 0L else taxRials) + deliveryRials
        if (net < 0) return null
        val base = UnitConverter.toBase(qty, packageUom, ingredient.baseDimension, ingredient.conversions) ?: return null
        return BigDecimal.valueOf(net).over(base)
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

/**
 * A menu section, as typed: one with a pricing method of its own, as drinks usually aim lower than
 * plates, or with [useGeneral] one priced like the rest, there to put dishes in.
 */
data class CategoryTargetInput(
    val name: String = "",
    val percent: String = "",
    val method: MethodChoice = MethodChoice.FOOD_COST,
    val basis: CostBasis = CostBasis.FOOD_COST,
    val useGeneral: Boolean = false,
) {
    val isBlank: Boolean get() = name.isBlank() && (useGeneral || percent.isBlank())
}

/** The pricing rules and misc share, ready to save. */
data class SettingsDraft(val costing: CostingRules, val pricing: PricingSettings)

/** The independently saved settings pages. Costs includes overhead and online sales terms. */
enum class SettingsPart { PRICING, SECTIONS, COSTS }

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
    /** The menu sections: priced with a method of their own, or with the general one. */
    val categories: List<CategoryTargetInput> = emptyList(),
    /**
     * True when a method switch could not convert [percent] to a valid equivalent.
     * The old number is only a reminder until the user types a percentage for the new method.
     */
    val carried: Boolean = false,
    /** The first value in an unchanged sequence of method switches, avoiding round-trip drift. */
    val conversionOrigin: PricingMethod? = null,
    /** Only the selected policy affects suggested prices; inactive settings stay stored. */
    val scope: PricingScope = PricingScope.MENU_SECTIONS,
) {
    fun plusCategory(name: String): SettingsInput = copy(categories = categories + CategoryTargetInput(name))

    fun withCategory(index: Int, category: CategoryTargetInput): SettingsInput =
        copy(categories = categories.toMutableList().also { it[index] = category })

    fun withoutCategory(index: Int): SettingsInput = copy(categories = categories.filterIndexed { i, _ -> i != index })

    /** The suggested sections not added yet. */
    fun categoryPresets(): List<String> {
        val taken = categories.map { Persian.searchKey(it.name) }.toSet()
        return CATEGORY_PRESETS.filter { Persian.searchKey(it) !in taken }
    }

    /** The portion's share of the month's fixed costs, as typed so far; null until both are readable. */
    fun overheadShare(money: MoneyFormat): String? {
        val fixed = money.parse(monthlyFixed)?.takeIf { it >= 0 } ?: return null
        val portions = Numbers.parse(monthlyPortions)?.takeIf { it.signum() > 0 } ?: return null
        return money.full(Overhead(fixed, portions).perPortion)
    }

    fun check(currentPricing: PricingSettings, currentCosting: CostingRules, money: MoneyFormat): Checked<SettingsDraft> {
        val p = Problems()
        if (carried) p.add(Field.PERCENT, CARRIED_MESSAGE)
        val value = p.decimal(percent, Field.PERCENT, percentMessage()) { validPercent(it, method) }
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
        val byCategory = LinkedHashMap<String, PricingMethod>()
        val general = ArrayList<String>()
        val seen = HashSet<String>()
        categories.forEach { category ->
            if (category.isBlank) return@forEach
            val name = category.name.trim()
            val target = Numbers.parse(category.percent)
            val validTarget = target != null && validPercent(target, category.method)
            when {
                name.isEmpty() -> p.add(Field.CATEGORY, "نام بخش را بنویسید.")
                !seen.add(Persian.searchKey(name)) -> p.add(Field.CATEGORY, "بخش «$name» دو بار آمده است.")
                category.useGeneral -> general += name
                !validTarget -> p.add(Field.CATEGORY, if (category.method == MethodChoice.FOOD_COST) "فودکاست هدف «$name» باید بیشتر از ۰ و کمتر از ۱۰۰ باشد." else "درصد روش قیمت‌گذاری «$name» معتبر نیست.")
                else -> byCategory[name] = when (category.method) {
                    MethodChoice.FOOD_COST -> PricingMethod.TargetFoodCost(target!!)
                    MethodChoice.MARGIN -> PricingMethod.TargetMargin(target!!, category.basis)
                    MethodChoice.MARKUP -> PricingMethod.Markup(target!!, category.basis)
                }
            }
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
                    scope = scope,
                    defaultMethod = newMethod,
                    vatPercent = vat!!,
                    menuPriceIncludesVat = menuPriceIncludesVat,
                    rounding = PriceRounding(roundingStepRials, roundingDirection),
                    overhead = overhead,
                    online = OnlineTerms(commission!!, discount!!, uplift!!),
                    methodByCategory = byCategory,
                    generalSections = general,
                ),
            )
        }
    }

    /**
     * Save only the visible page. Unfinished input on another page cannot block this save or
     * replace that page's last confirmed values. Inactive pricing values remain available for
     * a later scope switch; an unmapped section continues to use the saved general fallback.
     */
    fun checkPart(
        part: SettingsPart,
        currentPricing: PricingSettings,
        currentCosting: CostingRules,
        money: MoneyFormat,
    ): Checked<SettingsDraft> {
        val saved = of(currentPricing, currentCosting, money)
        val activeGeneral = scope == PricingScope.GENERAL
        val dormantSections = if (part == SettingsPart.SECTIONS && currentPricing.scope == PricingScope.GENERAL) {
            inactiveSections(saved.categories)
        } else null
        val page = when (part) {
            SettingsPart.PRICING -> saved.copy(
                scope = scope,
                method = if (activeGeneral) method else saved.method,
                percent = if (activeGeneral) percent else saved.percent,
                basis = if (activeGeneral) basis else saved.basis,
                carried = activeGeneral && carried,
                vatPercent = vatPercent,
                menuPriceIncludesVat = menuPriceIncludesVat,
                roundingStepRials = roundingStepRials,
                roundingDirection = roundingDirection,
            )
            SettingsPart.SECTIONS -> saved.copy(
                categories = dormantSections?.first ?: categories,
            )
            SettingsPart.COSTS -> saved.copy(
                miscPercent = miscPercent,
                monthlyFixed = monthlyFixed,
                monthlyPortions = monthlyPortions,
                onlineCommission = onlineCommission,
                onlineDiscount = onlineDiscount,
                onlineUplift = onlineUplift,
            )
        }
        return when (val checked = page.check(currentPricing, currentCosting, money)) {
            is Checked.Invalid -> checked
            is Checked.Valid -> {
                val draft = checked.value
                val pricing = when (part) {
                    SettingsPart.PRICING -> currentPricing.copy(
                        scope = scope,
                        defaultMethod = if (activeGeneral) draft.pricing.defaultMethod else currentPricing.defaultMethod,
                        vatPercent = draft.pricing.vatPercent,
                        menuPriceIncludesVat = menuPriceIncludesVat,
                        rounding = draft.pricing.rounding,
                    )
                    SettingsPart.SECTIONS -> currentPricing.copy(
                        methodByCategory = if (dormantSections == null) draft.pricing.methodByCategory else {
                            draft.pricing.methodByCategory.mapValues { (name, method) ->
                                dormantSections.second[name]?.let { currentPricing.methodByCategory[it] } ?: method
                            }
                        },
                        generalSections = draft.pricing.generalSections,
                    )
                    SettingsPart.COSTS -> currentPricing.copy(
                        overhead = draft.pricing.overhead,
                        online = draft.pricing.online,
                    )
                }
                Checked.Valid(SettingsDraft(if (part == SettingsPart.COSTS) draft.costing else currentCosting, pricing))
            }
        }
    }

    /** Section names stay editable in general mode, but their disabled pricing controls do not. */
    private fun inactiveSections(saved: List<CategoryTargetInput>): Pair<List<CategoryTargetInput>, Map<String, String>> {
        val requestedNames = categories.map { Persian.searchKey(it.name) }.toSet()
        val savedByName = saved.associateBy { Persian.searchKey(it.name) }
        val availableRenames = saved.filter { Persian.searchKey(it.name) !in requestedNames }.toMutableList()
        val sourceNames = LinkedHashMap<String, String>()
        val rows = categories.map { row ->
            val existing = savedByName[Persian.searchKey(row.name)]
            // A renamed row carries its old disabled fields. Match those before treating a new
            // name as a plain section, so renaming does not discard its dormant target.
            val renamed = if (existing == null) availableRenames.firstOrNull {
                it.percent == row.percent && it.method == row.method && it.basis == row.basis && it.useGeneral == row.useGeneral
            } else null
            if (renamed != null) availableRenames.remove(renamed)
            val prior = existing ?: renamed
            if (prior != null) sourceNames[row.name.trim()] = prior.name
            prior?.copy(name = row.name) ?: row.copy(percent = "", useGeneral = true)
        }
        return rows to sourceNames
    }

    /**
     * The form with [choice] as its method and the number turned into that method's equivalent,
     * so switching the method leaves the suggested price as it was: a 35% food cost is a 65% margin
     * and a 185.71% markup. A margin and a markup convert on whichever cost they are taken, every
     * cost included: a 40% margin is a 66.67% markup. A margin or markup coming from a food cost
     * target starts on the food alone, where it puts the same price. Only a margin or markup on
     * every cost turning into a food cost target, which measures the food alone, has no equivalent:
     * the number is kept and marked [carried]. Invalid or unrepresentable conversions are also
     * marked, and cannot be saved or converted again until a percentage is entered manually.
     */
    fun withMethod(choice: MethodChoice): SettingsInput {
        if (choice == method) return this
        if (carried || (choice == MethodChoice.FOOD_COST && basis == CostBasis.FULL_COST)) {
            return copy(method = choice, carried = true, conversionOrigin = null)
        }
        val value = Numbers.parse(percent)
            ?: return copy(method = choice, carried = true, conversionOrigin = null)
        val activeBasis = if (method == MethodChoice.FOOD_COST) CostBasis.FOOD_COST else basis
        // A direct copy changing the percentage or basis must not reuse an old conversion origin.
        val origin = conversionOrigin?.takeIf {
            val (sourceMethod, sourceValue) = it.choiceAndPercent()
            it.costBasis() == activeBasis && equivalent(sourceValue, sourceMethod, method)?.compareTo(value) == 0
        }
        val (sourceMethod, sourceValue) = origin?.choiceAndPercent() ?: (method to value)
        val converted = equivalent(sourceValue, sourceMethod, choice)
            ?: return copy(method = choice, carried = true, conversionOrigin = null)
        return copy(
            method = choice,
            percent = Numbers.editable(converted),
            basis = activeBasis,
            carried = false,
            conversionOrigin = origin ?: when (method) {
                MethodChoice.FOOD_COST -> PricingMethod.TargetFoodCost(value)
                MethodChoice.MARGIN -> PricingMethod.TargetMargin(value, activeBasis)
                MethodChoice.MARKUP -> PricingMethod.Markup(value, activeBasis)
            },
        )
    }

    /**
     * The number in the other methods: «یعنی حاشیهٔ سود ۶۵٪ و مارک‌آپ ۱۸۵٫۷٪», or on every cost,
     * where a food cost share has no equivalent, «یعنی مارک‌آپ ۶۶٫۷٪، روی همهٔ هزینه‌ها».
     */
    fun equivalents(): String? {
        if (carried) return null
        val value = Numbers.parse(percent)?.takeIf { validPercent(it, method) } ?: return null
        if (basis == CostBasis.FULL_COST && method != MethodChoice.FOOD_COST) {
            val other = if (method == MethodChoice.MARGIN) MethodChoice.MARKUP else MethodChoice.MARGIN
            val converted = equivalent(value, method, other) ?: return null
            return "یعنی ${shortName(other)} ${Numbers.percent(converted)}، روی همهٔ هزینه‌ها"
        }
        val others = MethodChoice.entries.filter { it != method }.mapNotNull { other ->
            val converted = equivalent(value, method, other) ?: return@mapNotNull null
            "${shortName(other)} ${Numbers.percent(converted)}"
        }
        return others.takeIf { it.isNotEmpty() }?.let { "یعنی ${it.joinToString(" و ")}" }
    }

    private fun percentMessage(): String = when (method) {
        MethodChoice.FOOD_COST -> "فودکاست هدف باید بیشتر از ۰ و کمتر از ۱۰۰ باشد."
        MethodChoice.MARGIN -> "حاشیهٔ سود باید از ۰ تا کمتر از ۱۰۰ باشد."
        MethodChoice.MARKUP -> "مارک‌آپ نمی‌تواند منفی باشد."
    }

    companion object {
        const val CARRIED_MESSAGE = "این عدد از روش قبلی مانده و معادل معتبری برای روش فعلی نیست؛ درصد این روش را دوباره وارد کنید."

        private fun PricingMethod.choiceAndPercent(): Pair<MethodChoice, BigDecimal> = when (this) {
            is PricingMethod.TargetFoodCost -> MethodChoice.FOOD_COST to percent
            is PricingMethod.TargetMargin -> MethodChoice.MARGIN to percent
            is PricingMethod.Markup -> MethodChoice.MARKUP to percent
        }

        private fun PricingMethod.costBasis(): CostBasis = when (this) {
            is PricingMethod.TargetFoodCost -> CostBasis.FOOD_COST
            is PricingMethod.TargetMargin -> basis
            is PricingMethod.Markup -> basis
        }

        private fun validPercent(value: BigDecimal, method: MethodChoice): Boolean = when (method) {
            MethodChoice.FOOD_COST -> value.signum() > 0 && value < HUNDRED
            MethodChoice.MARGIN -> value.signum() >= 0 && value < HUNDRED
            MethodChoice.MARKUP -> value.signum() >= 0
        }

        private fun shortName(choice: MethodChoice): String = when (choice) {
            MethodChoice.FOOD_COST -> "فودکاست"
            MethodChoice.MARGIN -> "حاشیهٔ سود"
            MethodChoice.MARKUP -> "مارک‌آپ"
        }

        /**
         * [value] of method [from] as method [to], on the food cost: the food cost is the cost's share
         * of the net price, the margin what is left of it, and the markup what is added to the cost.
         * Null when either percentage is outside its method's range. Keep calculation precision;
         * rounding is only for the explanatory text, never for the value used to price dishes.
         */
        internal fun equivalent(value: BigDecimal, from: MethodChoice, to: MethodChoice): BigDecimal? {
            if (!validPercent(value, from)) return null
            if (from == to) return value
            val result = when (to) {
                MethodChoice.FOOD_COST -> when (from) {
                    MethodChoice.MARKUP -> HUNDRED_SQUARED over (HUNDRED + value)
                    else -> HUNDRED - value
                }
                MethodChoice.MARGIN -> when (from) {
                    MethodChoice.MARKUP -> value.multiply(HUNDRED) over (HUNDRED + value)
                    else -> HUNDRED - value
                }
                MethodChoice.MARKUP -> when (from) {
                    MethodChoice.FOOD_COST -> (HUNDRED - value).multiply(HUNDRED) over value
                    else -> value.multiply(HUNDRED) over (HUNDRED - value)
                }
            }
            return result.takeIf { validPercent(it, to) }?.stripTrailingZeros()
        }

        private val HUNDRED_SQUARED = BigDecimal(10_000)

        /** Rounding steps offered, in rials: 1,000 to 100,000 toman. */
        val ROUNDING_STEPS: List<Long> = listOf(10_000L, 50_000L, 100_000L, 500_000L, 1_000_000L)

        /** Sections offered with one tap, for restaurants and cafes; any other name can be typed. */
        val CATEGORY_PRESETS: List<String> = listOf(
            "غذای اصلی", "پیش‌غذا و سالاد", "نوشیدنی گرم", "نوشیدنی سرد", "دسر و کیک", "صبحانه",
        )

        /** A section's rule as a food cost target, whichever way it was stored. */
        private fun foodCostTarget(method: PricingMethod): BigDecimal? = when (method) {
            is PricingMethod.TargetFoodCost -> method.percent
            is PricingMethod.TargetMargin -> equivalent(method.percent, MethodChoice.MARGIN, MethodChoice.FOOD_COST)
            is PricingMethod.Markup -> equivalent(method.percent, MethodChoice.MARKUP, MethodChoice.FOOD_COST)
        }

        fun of(pricing: PricingSettings, costing: CostingRules, money: MoneyFormat): SettingsInput {
            val m = pricing.defaultMethod
            val (choice, value) = m.choiceAndPercent()
            fun percentOrBlank(v: BigDecimal) = if (v.signum() == 0) "" else Numbers.editable(v)
            return SettingsInput(
                method = choice,
                scope = pricing.scope,
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
                categories = pricing.methodByCategory.map { (name, method) ->
                    CategoryTargetInput(
                        name = name,
                        percent = Numbers.editable(when (method) {
                            is PricingMethod.TargetFoodCost -> method.percent
                            is PricingMethod.TargetMargin -> method.percent
                            is PricingMethod.Markup -> method.percent
                        }),
                        method = when (method) {
                            is PricingMethod.TargetFoodCost -> MethodChoice.FOOD_COST
                            is PricingMethod.TargetMargin -> MethodChoice.MARGIN
                            is PricingMethod.Markup -> MethodChoice.MARKUP
                        },
                        basis = when (method) {
                            is PricingMethod.TargetFoodCost -> CostBasis.FOOD_COST
                            is PricingMethod.TargetMargin -> method.basis
                            is PricingMethod.Markup -> method.basis
                        },
                    )
                } + pricing.generalSections.filter { it !in pricing.methodByCategory }.map { CategoryTargetInput(name = it, useGeneral = true) },
            )
        }
    }
}
