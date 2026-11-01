package ir.foodcost.presentation

import ir.foodcost.core.FoodCostCalculator
import ir.foodcost.core.costing.LatestPurchasePrice
import ir.foodcost.core.costing.RecipeCost
import ir.foodcost.core.costing.Section
import ir.foodcost.core.costing.UnitCostResult
import ir.foodcost.core.model.Catalog
import ir.foodcost.core.model.ComponentRef
import ir.foodcost.core.model.ExtraCost
import ir.foodcost.core.model.Ingredient
import ir.foodcost.core.model.IngredientId
import ir.foodcost.core.model.MenuPrice
import ir.foodcost.core.model.PurchasePrice
import ir.foodcost.core.model.QtyBasis
import ir.foodcost.core.model.Recipe
import ir.foodcost.core.model.RecipeId
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.model.RecipeLine
import ir.foodcost.core.model.RecipeVersion
import ir.foodcost.core.model.SalesChannel
import ir.foodcost.core.money.DisplayCurrency
import ir.foodcost.core.pricing.CostBasis
import ir.foodcost.core.pricing.PriceStatus
import ir.foodcost.core.pricing.Pricer
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.core.pricing.Sale
import ir.foodcost.core.roundToRials
import ir.foodcost.core.units.Conversions
import ir.foodcost.core.units.Dimension
import ir.foodcost.core.units.UnitConverter
import ir.foodcost.core.units.Uom
import ir.foodcost.data.ledger.Ledger
import ir.foodcost.data.ledger.LedgerState
import ir.foodcost.data.ledger.RejectReason
import ir.foodcost.data.ops.Opt
import java.math.BigDecimal
import java.time.LocalDate

/** One line of the calculator, as typed: an ingredient, its price per kilo (liter, piece) and how much goes in. */
data class QuickRow(
    val name: String = "",
    val price: String = "",
    val qty: String = "",
    val uom: Uom = Uom.GRAM,
    /** The saved ingredient this row stands for, once picked from the suggestions or saved. */
    val ingredientId: IngredientId? = null,
) {
    val isBlank: Boolean get() = name.isBlank() && price.isBlank() && qty.isBlank()
}

/** A cost of a portion that is not food, as typed: a box, a cup and lid, a share of a wage. */
data class QuickExtra(val name: String = "", val amount: String = "", val takeawayOnly: Boolean = false) {
    val isBlank: Boolean get() = name.isBlank() && amount.isBlank()
}

/** The calculator as typed. [recipeId] is the saved dish being edited, if any. */
data class QuickInput(
    val dishName: String = "",
    val rows: List<QuickRow> = listOf(QuickRow()),
    val portions: String = "1",
    val menuPrice: String = "",
    val recipeId: RecipeId? = null,
    /** The saved dish's own share for salt and spices; null takes the default. */
    val miscPercent: BigDecimal? = null,
    /** Costs of a portion that are not food, each counted once. */
    val extras: List<QuickExtra> = emptyList(),
    /** The menu section, such as drinks, which can have a food cost target of its own. */
    val category: String? = null,
) {
    fun plusExtra(extra: QuickExtra): QuickInput = copy(extras = extras + extra)

    fun withExtra(index: Int, extra: QuickExtra): QuickInput = copy(extras = extras.toMutableList().also { it[index] = extra })

    fun withoutExtra(index: Int): QuickInput = copy(extras = extras.filterIndexed { i, _ -> i != index })

    /** Replaces row [index]. Typing into the last row brings a fresh empty row below it. */
    fun withRow(index: Int, row: QuickRow): QuickInput {
        val updated = rows.toMutableList()
        updated[index] = row
        if (!updated.last().isBlank) updated += QuickRow()
        return copy(rows = updated)
    }

    /** Removes row [index], keeping one empty row at the end to type into. */
    fun withoutRow(index: Int): QuickInput {
        val updated = rows.filterIndexed { i, _ -> i != index }.toMutableList()
        if (updated.isEmpty() || !updated.last().isBlank) updated += QuickRow()
        return copy(rows = updated)
    }
}

data class QuickRowResult(
    /** قیمت هر کیلو, قیمت هر لیتر or قیمت هر عدد: what the price field asks for. */
    val priceLabel: String,
    val cost: String?,
    /** The yield of a saved ingredient, so the waste in the cost is no surprise. */
    val note: String?,
    val problem: String?,
    /** The row only lacks a price or a quantity: it is still being typed, so it is not an error yet. */
    val waiting: Boolean,
    val units: List<Uom>,
    /**
     * Worth a second look, though the row is counted: a price about ten times the last one (a zero too
     * many, or rials typed as toman), or an ingredient already in another row.
     */
    val warning: String? = null,
)

data class QuickExtraResult(val problem: String?, val waiting: Boolean)

/**
 * The same portion sold in another channel, beside the restaurant's own figures. [priceForSameRemaining]
 * is what to charge there for a portion to leave as much as one eaten in.
 */
data class QuickChannel(
    val label: String,
    val price: String,
    val cost: String,
    val remaining: String,
    val remainingNegative: Boolean,
    val foodCostPercent: String?,
    val priceForSameRemaining: String?,
)

data class QuickResult(
    val rows: List<QuickRowResult>,
    val hasLines: Boolean,
    /** The food alone. */
    val costPerPortion: String,
    /** Food and the other costs of a portion eaten in: the cost on the display. */
    val totalCost: String,
    /** The other costs of a portion eaten in, its overhead share included; null when there are none. */
    val otherCosts: String?,
    /** The portion's share of the month's fixed costs from the settings; null when none is set. */
    val overheadShare: String?,
    val miscNote: String,
    val method: String,
    /** True when the suggested price counts the food alone, so the other costs only lower what is left. */
    val pricesFoodOnly: Boolean,
    val suggested: String?,
    val suggestedRials: Long?,
    /** At the menu price typed: what a portion sells for without VAT. */
    val netSales: String?,
    val foodCostPercent: String?,
    /** At the menu price typed: what a portion leaves after every cost entered, signed. Not profit. */
    val remaining: String?,
    val remainingNegative: Boolean,
    /** On every cost entered. */
    val margin: String?,
    val markup: String?,
    /** Pricing backwards: the most a portion may cost for the menu price typed, and what that covers. */
    val maxCost: String?,
    val maxCostLabel: String?,
    val raise: String?,
    val status: PriceStatus,
    val statusLabel: String,
    val incompleteRows: Int,
    val extras: List<QuickExtraResult>,
    /** The restaurant, takeaway and online side by side; empty when they would all show the same. */
    val channels: List<QuickChannel>,
    /** The figures as numbers, for comparing a dish before and after a change; null when not shown. */
    val totalCostRials: BigDecimal? = null,
    val menuRials: Long? = null,
    val foodCostValue: BigDecimal? = null,
    val remainingRials: BigDecimal? = null,
)

data class QuickSuggestion(val id: IngredientId, val name: String, val detail: String)

/** One figure of a dish before and after a save: amounts without their unit, so the columns fit. */
data class QuickChange(
    val label: String,
    val before: String,
    val after: String,
    /** How much it moved, such as ‎+۷٫۲٪, for amounts. */
    val change: String?,
    /** True when the move is bad for the dish: a higher cost or food cost, less left. */
    val worse: Boolean,
)

/**
 * What a save is about to change, shown before it is done: the dish's figures before and after,
 * the ingredient prices it records (which reach every dish made with them), and anything that
 * looks like a slip. Empty for a new dish whose ingredients are new too.
 */
data class QuickReview(
    val name: String,
    val changes: List<QuickChange>,
    val prices: List<String>,
    val warnings: List<String>,
) {
    val isEmpty: Boolean get() = changes.isEmpty() && prices.isEmpty() && warnings.isEmpty()
}

data class QuickPlanRow(
    val ingredientId: IngredientId?,
    val name: String,
    val dimension: Dimension,
    val priceRials: Long,
    val qty: BigDecimal,
    val uom: Uom,
)

/** A checked calculator, ready to be saved as a dish. */
data class QuickPlan(
    val name: String,
    val rows: List<QuickPlanRow>,
    val portions: BigDecimal,
    val menuPriceRials: Long?,
    val recipeId: RecipeId?,
    val miscPercent: BigDecimal?,
    val extras: List<ExtraCost> = emptyList(),
    val category: String? = null,
)

/** The ledger refused part of a save, such as a price for a closed period. */
class QuickSaveRefused(val reason: RejectReason) : IllegalStateException("Refused: $reason")

/**
 * The calculator: ingredients typed with their prices, costed as you type. It prices a dish with the
 * same engine and settings as saved dishes, so both always show the same numbers, and writes nothing
 * until the dish is saved.
 */
object QuickCalculator {
    /** Other costs offered with one tap; the box is for takeaway and online orders only. */
    val EXTRA_PRESETS: List<QuickExtra> = listOf(
        QuickExtra("ظرف و بسته‌بندی", takeawayOnly = true),
        QuickExtra("دستمزد"),
        QuickExtra("گاز و برق"),
        QuickExtra("سایر"),
    )

    private val QUICK = RecipeId("quick")
    private val LINE_UNITS = listOf(
        Uom.GRAM, Uom.KILOGRAM, Uom.PIECE, Uom.MILLILITER, Uom.LITER,
        Uom.MESGHAL, Uom.TABLESPOON, Uom.TEASPOON,
    )

    fun compute(input: QuickInput, state: LedgerState, money: MoneyFormat, today: LocalDate): QuickResult {
        val catalog = state.catalog
        val ingredients = ArrayList<Ingredient>()
        val prices = ArrayList<PurchasePrice>()
        val lines = ArrayList<RecipeLine>()
        val lineOfRow = HashMap<Int, Int>()
        val problems = HashMap<Int, String>()
        val waiting = HashSet<Int>()
        input.rows.forEachIndexed { i, row ->
            if (row.isBlank) return@forEachIndexed
            val saved = row.ingredientId?.let { catalog.ingredient(it) }
            val dimension = priceDimension(row, saved)
            val rials = money.parse(row.price)?.takeIf { it > 0 }
            val qty = Numbers.parse(row.qty)?.takeIf { it.signum() > 0 }
            val problem = when {
                rials == null -> "قیمت را بنویسید."
                qty == null -> "مقدار را بنویسید."
                !fits(row.uom, dimension, saved?.conversions ?: Conversions()) -> "این واحد به این ماده نمی‌خورد."
                else -> null
            }
            if (problem != null) {
                problems[i] = problem
                if (rials == null || qty == null) waiting += i
                return@forEachIndexed
            }
            val id = IngredientId("quick-$i")
            ingredients += saved?.copy(id = id) ?: Ingredient(id, row.name.ifBlank { "ردیف ${i + 1}" }, dimension)
            prices += PurchasePrice(id, today, BigDecimal.ONE, shownUnit(dimension), rials!!)
            lineOfRow[i] = lines.size
            lines += RecipeLine(ComponentRef.IngredientRef(id), qty, row.uom)
        }

        val extras = ArrayList<ExtraCost>()
        val extraResults = input.extras.map { extra ->
            val rials = money.parse(extra.amount)?.takeIf { it >= 0 }
            when {
                extra.isBlank -> QuickExtraResult(null, waiting = true)
                rials == null -> QuickExtraResult("مبلغ را بنویسید.", waiting = true)
                else -> {
                    extras += ExtraCost(extra.name.ifBlank { "هزینه" }, rials, extra.takeawayOnly)
                    QuickExtraResult(null, waiting = false)
                }
            }
        }

        val savedDish = input.recipeId?.let { catalog.recipe(it) }
        val portions = Numbers.parse(input.portions)?.takeIf { it.signum() > 0 } ?: BigDecimal.ONE
        val recipe = Recipe(
            id = QUICK,
            name = input.dishName.ifBlank { "محاسبه" },
            kind = savedDish?.kind ?: RecipeKind.MENU_ITEM,
            versions = listOf(RecipeVersion(today, portions, lines, miscPercent = input.miscPercent, extras = extras)),
            category = input.category,
            pricing = savedDish?.pricing,
        )
        val menuRials = money.parse(input.menuPrice)?.takeIf { it > 0 }
        val calculator = FoodCostCalculator(
            Catalog(ingredients, prices, listOf(recipe), listOfNotNull(menuRials?.let { MenuPrice(QUICK, today, it) })),
            state.costing,
            state.pricing,
        )
        val evaluation = calculator.evaluate(QUICK, today)
        val cost = evaluation.cost
        val hasLines = lines.isNotEmpty()
        val warnings = rowWarnings(input, state, money, today)

        val rows = input.rows.mapIndexed { i, row ->
            val saved = row.ingredientId?.let { catalog.ingredient(it) }
            val dimension = priceDimension(row, saved)
            val lineCost = lineOfRow[i]?.let { index -> cost.lines.firstOrNull { it.section == Section.BATCH && it.index == index } }
            QuickRowResult(
                priceLabel = "قیمت هر ${Labels.priceUnit(dimension)}",
                cost = lineCost?.let { money.full(it.cost) },
                note = saved?.usableFraction?.takeIf { it < BigDecimal.ONE }
                    ?.let { "بازدهی ${Numbers.percent(it.multiply(BigDecimal(100)))}" },
                problem = problems[i],
                waiting = i in waiting,
                units = lineUnits(dimension, saved?.conversions ?: Conversions(), saved != null),
                warning = warnings[i],
            )
        }
        val status = when {
            !hasLines -> PriceStatus.EMPTY
            problems.isNotEmpty() -> PriceStatus.INCOMPLETE
            else -> evaluation.status
        }
        val s = evaluation.suggestion
        val pricer = calculator.pricer
        val other = pricer.otherCosts(cost, SalesChannel.DINE_IN)
        val sale = menuRials?.takeIf { hasLines }?.let { pricer.sale(cost, SalesChannel.DINE_IN, it) }
        val maxCost = menuRials?.takeIf { hasLines }?.let { pricer.maxCost(recipe, it) }
        val overhead = state.pricing.overhead?.perPortion?.takeIf { it.signum() > 0 && recipe.kind != RecipeKind.PREP }
        // «فودکاست هدف ۲۰٪ برای نوشیدنی گرم» when the dish's section has a target of its own.
        val section = recipe.category?.takeIf { recipe.pricing == null && it in state.pricing.methodByCategory }
        return QuickResult(
            rows = rows,
            hasLines = hasLines,
            costPerPortion = if (hasLines) money.full(cost.foodCostPerPortion) else "—",
            totalCost = if (hasLines) money.full(cost.foodCostPerPortion + other) else "—",
            otherCosts = other.takeIf { hasLines && it.signum() > 0 }?.let { money.full(it) },
            overheadShare = overhead?.let { money.full(it) },
            miscNote = "با ${Numbers.percent(cost.miscPercent)} برای نمک، ادویه و روغن",
            method = Labels.method(s.method) + (section?.let { " برای $it" } ?: ""),
            pricesFoodOnly = basisOf(s.method) == CostBasis.FOOD_COST,
            suggested = if (hasLines) money.full(s.menuPrice) else null,
            suggestedRials = if (hasLines) s.menuPrice else null,
            netSales = sale?.let { money.full(it.netSales) },
            foodCostPercent = sale?.foodCostPercent?.let { Numbers.percent(it) },
            remaining = sale?.let { signed(it.remaining, money) },
            remainingNegative = sale != null && sale.remaining.signum() < 0,
            margin = sale?.marginPercent?.let { Numbers.percent(it) },
            markup = sale?.markupPercent?.let { Numbers.percent(it) },
            maxCost = maxCost?.let { money.full(it.amount) },
            maxCostLabel = maxCost?.let { if (it.basis == CostBasis.FOOD_COST) "حداکثر بهای مواد برای این قیمت" else "حداکثر هزینهٔ هر پرس برای این قیمت" },
            raise = evaluation.changePercent
                ?.takeIf { hasLines && it.signum() > 0 && (status == PriceStatus.UNDER || status == PriceStatus.NEAR) }
                ?.let { Numbers.percent(it, 0) },
            status = status,
            statusLabel = Labels.status(status),
            incompleteRows = problems.size,
            extras = extraResults,
            channels = if (sale == null) emptyList() else channels(pricer, cost, sale, extras, state.pricing.online.isSet, money),
            totalCostRials = if (hasLines) cost.foodCostPerPortion + other else null,
            menuRials = menuRials,
            foodCostValue = sale?.foodCostPercent,
            remainingRials = sale?.remaining,
        )
    }

    /** The sections dishes can be put in: those with a target in the settings, and the dish's own. */
    fun categories(state: LedgerState, input: QuickInput): List<String> =
        (state.pricing.methodByCategory.keys + listOfNotNull(input.category)).distinct()

    /** Prices about a power of ten away from the last one, and ingredients already in an earlier row. */
    private fun rowWarnings(input: QuickInput, state: LedgerState, money: MoneyFormat, today: LocalDate): Map<Int, String> {
        val warnings = HashMap<Int, String>()
        val firstRow = HashMap<String, Pair<Int, String>>()
        input.rows.forEachIndexed { i, row ->
            if (row.isBlank) return@forEachIndexed
            val matched = matchedIngredient(state, row)
            val name = matched?.name ?: row.name.trim()
            if (name.isEmpty()) return@forEachIndexed
            val key = matched?.id?.value ?: "typed|${Persian.searchKey(name)}|${priceDimension(row, null)}"
            val first = firstRow[key]
            if (first != null) {
                warnings[i] = "«${first.second}» در ردیف ${Persian.digits(first.first + 1)} هم آمده است."
                return@forEachIndexed
            }
            firstRow[key] = i to name
            val typed = money.parse(row.price)?.takeIf { it > 0 } ?: return@forEachIndexed
            val last = matched?.let { currentPriceRials(state, it, today) } ?: return@forEachIndexed
            priceSlip(name, typed, last, money)?.let { warnings[i] = it }
        }
        return warnings
    }

    /** The saved ingredient a row stands for: the one picked, or one with the same name and kind. */
    private fun matchedIngredient(state: LedgerState, row: QuickRow): Ingredient? {
        val catalog = state.catalog
        val saved = row.ingredientId?.let { catalog.ingredient(it) }
        if (saved != null || row.ingredientId != null) return saved
        return findByName(state, row.name, priceDimension(row, null))?.let { catalog.ingredient(it) }
    }

    /**
     * A warning when [typedRials] is about a power of ten away from [lastRials], the way a zero too
     * many or too few, or rials typed for toman, look. Real rises between two purchases are far smaller.
     */
    fun priceSlip(name: String, typedRials: Long, lastRials: Long, money: MoneyFormat): String? {
        val power = powerOfTen(typedRials, lastRials) ?: return null
        val size = when (power) {
            1 -> "۱۰ برابر"
            2 -> "۱۰۰ برابر"
            3 -> "۱٬۰۰۰ برابر"
            4 -> "۱۰٬۰۰۰ برابر"
            -1 -> "یک‌دهم"
            -2 -> "یک‌صدم"
            -3 -> "یک‌هزارم"
            -4 -> "یک‌ده‌هزارم"
            else -> if (power > 0) "بسیار بیشتر از" else "بسیار کمتر از"
        }
        val hint = when {
            money.currency == DisplayCurrency.TOMAN && power == 1 -> "شاید به ریال نوشته شده."
            money.currency == DisplayCurrency.RIAL && power == -1 -> "شاید به تومان نوشته شده."
            money.currency == DisplayCurrency.NEW_RIAL && power == 3 -> "شاید به تومان نوشته شده."
            money.currency == DisplayCurrency.NEW_RIAL && power == 4 -> "شاید به ریال قدیم نوشته شده."
            else -> "صفرهایش را دوباره ببینید."
        }
        return "قیمت «$name» $size قیمت قبلی (${money.full(lastRials)}) است؛ $hint"
    }

    /** 1 when [typed] is about ten times [last], -1 when about a tenth, and so on; null when neither. */
    internal fun powerOfTen(typed: Long, last: Long): Int? {
        if (typed <= 0 || last <= 0) return null
        val ratio = typed.toDouble() / last.toDouble()
        val power = Math.round(Math.log10(ratio)).toInt()
        if (power == 0) return null
        val rest = ratio / Math.pow(10.0, power.toDouble())
        return power.takeIf { rest in 0.75..1.35 }
    }

    /** The restaurant, takeaway and online side by side, leaving out a channel that would repeat another. */
    private fun channels(
        pricer: Pricer,
        cost: RecipeCost,
        dineIn: Sale,
        extras: List<ExtraCost>,
        online: Boolean,
        money: MoneyFormat,
    ): List<QuickChannel> {
        fun row(sale: Sale, same: Long?) = QuickChannel(
            label = Labels.channel(sale.channel),
            price = money.full(sale.price),
            cost = money.full(sale.registeredCost),
            remaining = signed(sale.remaining, money),
            remainingNegative = sale.remaining.signum() < 0,
            foodCostPercent = sale.foodCostPercent?.let { Numbers.percent(it) },
            priceForSameRemaining = same?.let { money.full(it) },
        )
        val list = mutableListOf(row(dineIn, null))
        if (extras.any { it.takeawayOnly }) {
            val takeaway = pricer.sale(cost, SalesChannel.TAKEAWAY, dineIn.price)
            list += row(takeaway, pricer.priceLeaving(dineIn.remaining, cost, SalesChannel.TAKEAWAY))
        }
        if (online) {
            val price = pricer.channelPrice(dineIn.price, SalesChannel.ONLINE)
            list += row(pricer.sale(cost, SalesChannel.ONLINE, price), pricer.priceLeaving(dineIn.remaining, cost, SalesChannel.ONLINE))
        }
        return if (list.size > 1) list else emptyList()
    }

    /** ‎+۱۲٬۰۰۰ تومان or ‎-۱۲٬۰۰۰ تومان: what is left shows its sign, not only its color. */
    private fun signed(rials: BigDecimal, money: MoneyFormat): String =
        if (rials.signum() < 0) money.full(rials) else "\u200E+" + money.full(rials)

    private fun basisOf(method: PricingMethod): CostBasis = when (method) {
        is PricingMethod.TargetFoodCost -> CostBasis.FOOD_COST
        is PricingMethod.TargetMargin -> method.basis
        is PricingMethod.Markup -> method.basis
    }

    /** Saved ingredients whose names match what is typed in [row], with today's price. */
    fun suggestions(state: LedgerState, row: QuickRow, today: LocalDate, money: MoneyFormat, limit: Int = 4): List<QuickSuggestion> {
        if (row.ingredientId != null || row.name.isBlank()) return emptyList()
        val key = Persian.searchKey(row.name)
        val names = Persian.nameOrder()
        return state.ingredients.values
            .filter { !it.archived }
            .mapNotNull { state.catalog.ingredient(it.id) }
            .filter { Persian.matches(it.name, row.name) }
            .sortedWith(compareBy<Ingredient> { if (Persian.searchKey(it.name).startsWith(key)) 0 else 1 }.thenBy(names) { it.name })
            .take(limit)
            .map { ingredient ->
                val price = currentPriceRials(state, ingredient, today)
                QuickSuggestion(
                    ingredient.id,
                    ingredient.name,
                    price?.let { "هر ${Labels.priceUnit(ingredient.baseDimension)} ${money.full(it)}" } ?: "بدون قیمت",
                )
            }
    }

    /** [row] after picking a saved ingredient: its name, today's price and a unit that fits it. */
    fun pick(state: LedgerState, row: QuickRow, id: IngredientId, today: LocalDate, money: MoneyFormat): QuickRow {
        val ingredient = state.catalog.ingredient(id) ?: return row
        val units = lineUnits(ingredient.baseDimension, ingredient.conversions, saved = true)
        return row.copy(
            name = ingredient.name,
            price = currentPrice(state, ingredient, today, money) ?: row.price,
            uom = if (row.uom in units) row.uom else units.first(),
            ingredientId = id,
        )
    }

    /**
     * A saved dish in the calculator, or null when it needs the full recipe screen: preparations,
     * and dishes with sub-recipes, per-portion lines or quantities written as bought.
     */
    fun open(state: LedgerState, recipeId: RecipeId, today: LocalDate, money: MoneyFormat): QuickInput? {
        val catalog = state.catalog
        val recipe = catalog.recipe(recipeId) ?: return null
        if (recipe.kind == RecipeKind.PREP) return null
        val menu = catalog.menuPriceAt(recipeId, today)?.rials?.let { money.editable(it) }.orEmpty()
        val version = recipe.versionAt(today)
            ?: return QuickInput(dishName = recipe.name, menuPrice = menu, recipeId = recipeId, category = recipe.category)
        if (version.portionLines.isNotEmpty()) return null
        val rows = version.batchLines.map { line ->
            val ref = line.component as? ComponentRef.IngredientRef ?: return null
            val qty = line.qty ?: return null
            if (line.basis != QtyBasis.USABLE) return null
            val ingredient = catalog.ingredient(ref.id) ?: return null
            QuickRow(
                name = ingredient.name,
                price = currentPrice(state, ingredient, today, money).orEmpty(),
                qty = Numbers.editable(qty),
                uom = line.uom,
                ingredientId = ingredient.id,
            )
        }
        return QuickInput(
            dishName = recipe.name,
            rows = rows + QuickRow(),
            portions = Numbers.editable(version.portions),
            menuPrice = menu,
            recipeId = recipeId,
            miscPercent = version.miscPercent,
            extras = version.extras.map { QuickExtra(it.name, money.editable(it.rialsPerPortion), it.takeawayOnly) },
            category = recipe.category,
        )
    }

    /**
     * Checks the calculator before saving it as a dish, so nothing is left half saved. A new
     * calculation saved under the name of a dish on the menu updates that dish instead of making a
     * second one; a dish that needs the full recipe screen is not overwritten from here.
     */
    fun check(input: QuickInput, state: LedgerState, money: MoneyFormat, today: LocalDate): Checked<QuickPlan> {
        val p = Problems()
        val name = input.dishName.trim()
        if (name.isEmpty()) p.add(Field.NAME, "نام غذا را بنویسید.")
        val namesake = if (input.recipeId == null && name.isNotEmpty()) dishNamed(state, name) else null
        if (namesake != null && open(state, namesake, today, money) == null) {
            p.add(Field.NAME, "«$name» با جزئیات بیشتر در دستورها هست؛ از منو بازش کنید یا نام دیگری بنویسید.")
        }
        val rows = ArrayList<QuickPlanRow>()
        // One ingredient can come twice, but not at two prices: only one of them could be recorded.
        val priceOf = HashMap<String, Long>()
        input.rows.forEachIndexed { i, row ->
            if (row.isBlank) return@forEachIndexed
            val saved = row.ingredientId?.let { state.catalog.ingredient(it) }
            val dimension = priceDimension(row, saved)
            val rials = money.parse(row.price)?.takeIf { it > 0 }
            val qty = Numbers.parse(row.qty)?.takeIf { it.signum() > 0 }
            val rowName = saved?.name ?: row.name.trim()
            val where = "ردیف ${Persian.digits(i + 1)}"
            val key = matchedIngredient(state, row)?.id?.value ?: "typed|${Persian.searchKey(rowName)}|$dimension"
            when {
                rowName.isEmpty() -> p.add(Field.ROW, "نام مادهٔ $where را بنویسید.")
                rials == null -> p.add(Field.ROW, "قیمت «$rowName» را بنویسید.")
                qty == null -> p.add(Field.ROW, "مقدار «$rowName» را بنویسید.")
                !fits(row.uom, dimension, saved?.conversions ?: Conversions()) -> p.add(Field.ROW, "واحد «$rowName» به آن نمی‌خورد.")
                priceOf[key]?.let { it != rials } == true -> p.add(Field.ROW, "«$rowName» دو بار با دو قیمت آمده است؛ یکی را بردارید.")
                else -> {
                    priceOf[key] = rials
                    rows += QuickPlanRow(saved?.id, rowName, dimension, rials, qty, row.uom)
                }
            }
        }
        if (rows.isEmpty() && !p.has(Field.ROW)) p.add(Field.ROW, "دست‌کم یک ماده با قیمت و مقدار لازم است.")
        val extras = ArrayList<ExtraCost>()
        input.extras.forEach { extra ->
            if (extra.isBlank) return@forEach
            val extraName = extra.name.trim().ifEmpty { "هزینه" }
            val rials = money.parse(extra.amount)?.takeIf { it >= 0 }
            if (rials == null) p.add(Field.EXTRA, "مبلغ «$extraName» را بنویسید.") else extras += ExtraCost(extraName, rials, extra.takeawayOnly)
        }
        val portions = p.decimal(input.portions.ifBlank { "1" }, Field.PORTIONS, "تعداد پرس باید بیشتر از صفر باشد.") { it.signum() > 0 }
        val menu = if (input.menuPrice.isBlank()) null else money.parse(input.menuPrice)
        if (input.menuPrice.isNotBlank() && (menu == null || menu <= 0)) p.add(Field.AMOUNT, "قیمت منو درست نیست.")
        // A new calculation saved over a dish on the menu keeps that dish's section unless one is chosen.
        val category = input.category?.trim()?.ifEmpty { null } ?: namesake?.let { state.recipes[it]?.category }
        return p.result { QuickPlan(name, rows, portions!!, menu, input.recipeId ?: namesake, input.miscPercent, extras, category) }
    }

    /**
     * What saving [plan] will change, for a look before it is done: the dish's figures before and
     * after when it is already on the menu, the ingredient prices that change for every dish made
     * with them, and the rows that look like a slip.
     */
    fun review(input: QuickInput, plan: QuickPlan, state: LedgerState, money: MoneyFormat, today: LocalDate): QuickReview {
        val after = compute(input.copy(recipeId = plan.recipeId, category = plan.category), state, money, today)
        val changes = plan.recipeId
            ?.let { open(state, it, today, money) }
            ?.let { compareFigures(compute(it, state, money, today), after, money) }
            .orEmpty()
        val catalog = state.catalog
        val prices = plan.rows.mapNotNull { row ->
            val id = row.ingredientId ?: findByName(state, row.name, row.dimension) ?: return@mapNotNull null
            val ingredient = catalog.ingredient(id) ?: return@mapNotNull null
            val last = currentPriceRials(state, ingredient, today)
            if (last == null || last == row.priceRials) return@mapNotNull null
            val others = catalog.usersOf(ComponentRef.IngredientRef(id), today).count {
                it != plan.recipeId && state.recipes[it]?.archived != true && catalog.recipe(it)?.kind != RecipeKind.PREP
            }
            val unit = Labels.priceUnit(ingredient.baseDimension)
            val change = "${ingredient.name}: هر $unit از ${money.amount(last)} به ${money.full(row.priceRials)}"
            if (others == 0) change else "$change؛ ${Persian.digits(others)} غذای دیگر هم با آن حساب می‌شود."
        }.distinct()
        return QuickReview(plan.name, changes, prices, after.rows.mapNotNull { it.warning }.distinct())
    }

    /** The figures of a dish that differ between [before] and [after]. */
    private fun compareFigures(before: QuickResult, after: QuickResult, money: MoneyFormat): List<QuickChange> {
        fun differ(a: BigDecimal?, b: BigDecimal?) = if (a == null || b == null) a != b else a.compareTo(b) != 0
        fun amount(value: BigDecimal?) = value?.let { money.amount(it) } ?: "—"
        fun moved(a: BigDecimal?, b: BigDecimal?): String? {
            if (a == null || b == null || a.signum() <= 0) return null
            val percent = (b - a).multiply(BigDecimal(100)).divide(a, 4, java.math.RoundingMode.HALF_UP)
            return (if (percent.signum() > 0) "‎+" else "") + Numbers.percent(percent)
        }
        val list = ArrayList<QuickChange>()
        fun add(label: String, a: BigDecimal?, b: BigDecimal?, text: (BigDecimal?) -> String, change: String?, worseIfUp: Boolean) {
            if (!differ(a, b)) return
            val up = a != null && b != null && b > a
            val down = a != null && b != null && b < a
            list += QuickChange(label, text(a), text(b), change, worse = if (worseIfUp) up else down)
        }
        val costBefore = before.totalCostRials
        val costAfter = after.totalCostRials
        add("هزینهٔ هر پرس", costBefore, costAfter, ::amount, moved(costBefore, costAfter), worseIfUp = true)
        val suggestedBefore = before.suggestedRials?.let { BigDecimal(it) }
        val suggestedAfter = after.suggestedRials?.let { BigDecimal(it) }
        add("قیمت پیشنهادی", suggestedBefore, suggestedAfter, ::amount, moved(suggestedBefore, suggestedAfter), worseIfUp = true)
        val menuBefore = before.menuRials?.let { BigDecimal(it) }
        val menuAfter = after.menuRials?.let { BigDecimal(it) }
        add("قیمت منو", menuBefore, menuAfter, ::amount, moved(menuBefore, menuAfter), worseIfUp = false)
        add("فودکاست", before.foodCostValue, after.foodCostValue, { v -> v?.let { Numbers.percent(it) } ?: "—" }, null, worseIfUp = true)
        add("مانده هر پرس", before.remainingRials, after.remainingRials, { v -> v?.let { signedAmount(it, money) } ?: "—" }, null, worseIfUp = false)
        return list
    }

    private fun signedAmount(rials: BigDecimal, money: MoneyFormat): String =
        if (rials.signum() < 0) money.amount(rials) else "‎+" + money.amount(rials)

    /**
     * The calculation as plain text, to send to a partner or the kitchen: each ingredient with its
     * quantity, price and cost, the other costs, then the figures on the display.
     */
    fun shareText(input: QuickInput, result: QuickResult, money: MoneyFormat): String = buildString {
        appendLine(input.dishName.trim().ifEmpty { "محاسبهٔ فودکاست" })
        input.category?.let { appendLine("بخش منو: $it") }
        appendLine()
        appendLine("${portionsLabel(input)}:")
        input.rows.forEachIndexed { i, row ->
            val cost = result.rows.getOrNull(i)?.cost ?: return@forEachIndexed
            val qty = Numbers.parse(row.qty) ?: return@forEachIndexed
            val rials = money.parse(row.price) ?: return@forEachIndexed
            val unit = result.rows[i].priceLabel.removePrefix("قیمت ")
            appendLine("• ${row.name.trim()}، ${Prices.quantity(qty, row.uom)}، $unit ${money.full(rials)}: $cost")
        }
        val extras = input.extras.filterIndexed { i, extra -> !extra.isBlank && result.extras.getOrNull(i)?.problem == null }
        if (extras.isNotEmpty()) {
            appendLine("هزینه‌های دیگر هر پرس:")
            extras.forEach { extra ->
                val rials = money.parse(extra.amount) ?: 0L
                val note = if (extra.takeawayOnly) " (فقط بیرون‌بر و آنلاین)" else ""
                appendLine("• ${extra.name.trim().ifEmpty { "هزینه" }}$note: ${money.full(rials)}")
            }
        }
        if (result.incompleteRows > 0) appendLine("${Persian.digits(result.incompleteRows)} ردیف کامل نیست و در جمع نیامده است.")
        appendLine()
        appendLine("بهای مواد هر پرس: ${result.costPerPortion}، ${result.miscNote}")
        result.otherCosts?.let { appendLine("هزینهٔ هر پرس با هزینه‌های دیگر: ${result.totalCost}") }
        result.suggested?.let { appendLine("قیمت پیشنهادی: $it، ${result.method}") }
        result.menuRials?.let { appendLine("قیمت منو: ${money.full(it)}، ${result.statusLabel}") }
        result.netSales?.let { appendLine("فروش خالص: $it") }
        result.foodCostPercent?.let { appendLine("فودکاست: $it") }
        result.remaining?.let { appendLine("${if (result.remainingNegative) "کسری هر پرس" else "مانده هر پرس"}: $it") }
    }.trimEnd()

    /** مواد یک پرس, or مواد ۴ پرس for a batch. */
    fun portionsLabel(input: QuickInput): String {
        val portions = Numbers.parse(input.portions)
        return if (portions == null || portions.compareTo(BigDecimal.ONE) == 0) "مواد یک پرس" else "مواد ${Numbers.format(portions, 2)} پرس"
    }

    /**
     * Saves [plan]: creates the ingredients it does not know yet (reusing ones with the same name),
     * records the prices that changed, then saves the dish and its menu price. Returns the dish.
     */
    fun save(ledger: Ledger, plan: QuickPlan, today: LocalDate): RecipeId {
        val lines = plan.rows.map { row ->
            val id = row.ingredientId?.takeIf { ledger.state.ingredients.containsKey(it) }
                ?: findByName(ledger.state, row.name, row.dimension)
                ?: ledger.createIngredient(row.name, row.dimension)
            val ingredient = checkNotNull(ledger.state.catalog.ingredient(id))
            if (currentPriceRials(ledger.state, ingredient, today) != row.priceRials) {
                ledger.recordPrice(PurchasePrice(id, today, BigDecimal.ONE, shownUnit(ingredient.baseDimension), row.priceRials)).orRefuse()
            }
            RecipeLine(ComponentRef.IngredientRef(id), row.qty, row.uom)
        }
        val existing = plan.recipeId?.takeIf { ledger.state.recipes.containsKey(it) }
        val recipeId = if (existing != null) {
            val record = ledger.state.recipes.getValue(existing)
            if (record.name != plan.name || record.category != plan.category) {
                ledger.updateRecipe(
                    existing,
                    name = if (record.name != plan.name) Opt.Value(plan.name) else Opt.Keep,
                    category = if (record.category != plan.category) Opt.Value(plan.category) else Opt.Keep,
                ).orRefuse()
            }
            existing
        } else {
            ledger.createRecipe(plan.name, RecipeKind.MENU_ITEM, plan.category)
        }
        val base = RecipeEdits.base(ledger.state.catalog.recipe(recipeId), today)
        ledger.saveRecipeVersion(
            recipeId,
            base.copy(portions = plan.portions, batchLines = lines, portionLines = emptyList(), miscPercent = plan.miscPercent, extras = plan.extras),
        ).orRefuse()
        plan.menuPriceRials?.let { rials ->
            if (ledger.state.catalog.menuPriceAt(recipeId, today)?.rials != rials) ledger.setMenuPrice(recipeId, today, rials).orRefuse()
        }
        return recipeId
    }

    /** A new price per kilo (liter, piece) for [ingredient], from [today]. */
    fun quickPrice(ingredient: Ingredient, text: String, money: MoneyFormat, today: LocalDate): Checked<PurchasePrice> {
        val p = Problems()
        val rials = money.parse(text)?.takeIf { it > 0 }
        if (rials == null) p.add(Field.AMOUNT, "قیمت را درست بنویسید.")
        return p.result { PurchasePrice(ingredient.id, today, BigDecimal.ONE, shownUnit(ingredient.baseDimension), rials!!) }
    }

    /** For a new price typed in the ingredient list: a warning when it looks like a slip, else null. */
    fun quickPriceWarning(state: LedgerState, ingredient: Ingredient, text: String, money: MoneyFormat, today: LocalDate): String? {
        val typed = money.parse(text)?.takeIf { it > 0 } ?: return null
        val last = currentPriceRials(state, ingredient, today) ?: return null
        return priceSlip(ingredient.name, typed, last, money)
    }

    /** Today's price of [ingredient] per kilo (liter, piece), ready to edit. */
    fun currentPrice(state: LedgerState, ingredient: Ingredient, today: LocalDate, money: MoneyFormat): String? =
        currentPriceRials(state, ingredient, today)?.let { money.editable(it) }

    fun shownUnit(dimension: Dimension): Uom = when (dimension) {
        Dimension.MASS -> Uom.KILOGRAM
        Dimension.VOLUME -> Uom.LITER
        Dimension.COUNT, Dimension.PORTION -> Uom.PIECE
    }

    private fun currentPriceRials(state: LedgerState, ingredient: Ingredient, today: LocalDate): Long? {
        val found = LatestPurchasePrice(state.catalog).unitCost(ingredient, today) as? UnitCostResult.Found ?: return null
        return found.rialsPerBaseUnit.multiply(Prices.shownUnitSize(ingredient.baseDimension)).roundToRials()
    }

    /** A saved ingredient prices in its own kind; a typed one in the kind of the unit chosen. */
    private fun priceDimension(row: QuickRow, saved: Ingredient?): Dimension = saved?.baseDimension ?: row.uom.dimension

    private fun fits(uom: Uom, dimension: Dimension, conversions: Conversions): Boolean =
        UnitConverter.toBase(BigDecimal.ONE, uom, dimension, conversions) != null

    private fun lineUnits(dimension: Dimension, conversions: Conversions, saved: Boolean): List<Uom> =
        if (saved) LINE_UNITS.filter { fits(it, dimension, conversions) } else LINE_UNITS

    /** The plate on the menu called [name], however its spaces and letters were typed. */
    private fun dishNamed(state: LedgerState, name: String): RecipeId? {
        val key = Persian.searchKey(name)
        return state.recipes.values.firstOrNull {
            !it.archived && it.kind == RecipeKind.MENU_ITEM && Persian.searchKey(it.name.orEmpty()) == key
        }?.id
    }

    private fun findByName(state: LedgerState, name: String, dimension: Dimension): IngredientId? {
        val key = Persian.searchKey(name)
        return state.ingredients.values.firstOrNull {
            !it.archived && it.baseDimension == dimension && Persian.searchKey(it.name.orEmpty()) == key
        }?.id
    }

    private fun Ledger.Outcome.orRefuse() {
        if (this is Ledger.Outcome.Rejected) throw QuickSaveRefused(reason)
    }
}
