package ir.foodcost.presentation

import ir.foodcost.core.FoodCostCalculator
import ir.foodcost.core.costing.LatestPurchasePrice
import ir.foodcost.core.costing.Section
import ir.foodcost.core.costing.UnitCostResult
import ir.foodcost.core.model.Catalog
import ir.foodcost.core.model.ComponentRef
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
import ir.foodcost.core.pricing.PriceStatus
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

/** The calculator as typed. [recipeId] is the saved dish being edited, if any. */
data class QuickInput(
    val dishName: String = "",
    val rows: List<QuickRow> = listOf(QuickRow()),
    val portions: String = "1",
    val menuPrice: String = "",
    val recipeId: RecipeId? = null,
    /** The saved dish's own share for salt and spices; null takes the default. */
    val miscPercent: BigDecimal? = null,
) {
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
)

data class QuickResult(
    val rows: List<QuickRowResult>,
    val hasLines: Boolean,
    val costPerPortion: String,
    val miscNote: String,
    val method: String,
    val suggested: String?,
    val suggestedRials: Long?,
    val foodCostPercent: String?,
    /** What a portion leaves after its food cost, at the menu price typed. */
    val profit: String?,
    val raise: String?,
    val status: PriceStatus,
    val statusLabel: String,
    val incompleteRows: Int,
)

data class QuickSuggestion(val id: IngredientId, val name: String, val detail: String)

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
)

/** The ledger refused part of a save, such as a price for a closed period. */
class QuickSaveRefused(val reason: RejectReason) : IllegalStateException("Refused: $reason")

/**
 * The calculator: ingredients typed with their prices, costed as you type. It prices a dish with the
 * same engine and settings as saved dishes, so both always show the same numbers, and writes nothing
 * until the dish is saved.
 */
object QuickCalculator {
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

        val savedDish = input.recipeId?.let { catalog.recipe(it) }
        val portions = Numbers.parse(input.portions)?.takeIf { it.signum() > 0 } ?: BigDecimal.ONE
        val recipe = Recipe(
            id = QUICK,
            name = input.dishName.ifBlank { "محاسبه" },
            kind = savedDish?.kind ?: RecipeKind.MENU_ITEM,
            versions = listOf(RecipeVersion(today, portions, lines, miscPercent = input.miscPercent)),
            category = savedDish?.category,
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
            )
        }
        val status = when {
            !hasLines -> PriceStatus.EMPTY
            problems.isNotEmpty() -> PriceStatus.INCOMPLETE
            else -> evaluation.status
        }
        val s = evaluation.suggestion
        return QuickResult(
            rows = rows,
            hasLines = hasLines,
            costPerPortion = if (hasLines) money.full(cost.foodCostPerPortion) else "—",
            miscNote = "با ${Numbers.percent(cost.miscPercent)} برای نمک، ادویه و روغن",
            method = Labels.method(s.method),
            suggested = if (hasLines) money.full(s.menuPrice) else null,
            suggestedRials = if (hasLines) s.menuPrice else null,
            foodCostPercent = if (hasLines) evaluation.foodCostPercent?.let { Numbers.percent(it) } else null,
            profit = if (hasLines) evaluation.grossProfit?.let { money.full(it) } else null,
            raise = evaluation.changePercent
                ?.takeIf { hasLines && it.signum() > 0 && (status == PriceStatus.UNDER || status == PriceStatus.NEAR) }
                ?.let { Numbers.percent(it, 0) },
            status = status,
            statusLabel = Labels.status(status),
            incompleteRows = problems.size,
        )
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
        val version = recipe.versionAt(today) ?: return QuickInput(dishName = recipe.name, menuPrice = menu, recipeId = recipeId)
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
        )
    }

    /** Checks the calculator before saving it as a dish, so nothing is left half saved. */
    fun check(input: QuickInput, state: LedgerState, money: MoneyFormat): Checked<QuickPlan> {
        val p = Problems()
        val name = input.dishName.trim()
        if (name.isEmpty()) p.add(Field.NAME, "نام غذا را بنویسید.")
        val rows = ArrayList<QuickPlanRow>()
        input.rows.forEachIndexed { i, row ->
            if (row.isBlank) return@forEachIndexed
            val saved = row.ingredientId?.let { state.catalog.ingredient(it) }
            val dimension = priceDimension(row, saved)
            val rials = money.parse(row.price)?.takeIf { it > 0 }
            val qty = Numbers.parse(row.qty)?.takeIf { it.signum() > 0 }
            val rowName = saved?.name ?: row.name.trim()
            val where = "ردیف ${Persian.digits(i + 1)}"
            when {
                rowName.isEmpty() -> p.add(Field.ROW, "نام مادهٔ $where را بنویسید.")
                rials == null -> p.add(Field.ROW, "قیمت «$rowName» را بنویسید.")
                qty == null -> p.add(Field.ROW, "مقدار «$rowName» را بنویسید.")
                !fits(row.uom, dimension, saved?.conversions ?: Conversions()) -> p.add(Field.ROW, "واحد «$rowName» به آن نمی‌خورد.")
                else -> rows += QuickPlanRow(saved?.id, rowName, dimension, rials, qty, row.uom)
            }
        }
        if (rows.isEmpty() && !p.has(Field.ROW)) p.add(Field.ROW, "دست‌کم یک ماده با قیمت و مقدار لازم است.")
        val portions = p.decimal(input.portions.ifBlank { "1" }, Field.PORTIONS, "تعداد پرس باید بیشتر از صفر باشد.") { it.signum() > 0 }
        val menu = if (input.menuPrice.isBlank()) null else money.parse(input.menuPrice)
        if (input.menuPrice.isNotBlank() && (menu == null || menu <= 0)) p.add(Field.AMOUNT, "قیمت منو درست نیست.")
        return p.result { QuickPlan(name, rows, portions!!, menu, input.recipeId, input.miscPercent) }
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
            if (ledger.state.recipes[existing]?.name != plan.name) ledger.updateRecipe(existing, name = Opt.Value(plan.name)).orRefuse()
            existing
        } else {
            ledger.createRecipe(plan.name, RecipeKind.MENU_ITEM)
        }
        val base = RecipeEdits.base(ledger.state.catalog.recipe(recipeId), today)
        ledger.saveRecipeVersion(
            recipeId,
            base.copy(portions = plan.portions, batchLines = lines, portionLines = emptyList(), miscPercent = plan.miscPercent),
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
