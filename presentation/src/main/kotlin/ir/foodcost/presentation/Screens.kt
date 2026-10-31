package ir.foodcost.presentation

import ir.foodcost.core.costing.IssueType
import ir.foodcost.core.costing.LatestPurchasePrice
import ir.foodcost.core.costing.LineCost
import ir.foodcost.core.costing.Section
import ir.foodcost.core.costing.UnitCostResult
import ir.foodcost.core.model.Catalog
import ir.foodcost.core.model.ComponentRef
import ir.foodcost.core.model.Ingredient
import ir.foodcost.core.model.IngredientId
import ir.foodcost.core.model.QtyBasis
import ir.foodcost.core.model.Recipe
import ir.foodcost.core.model.RecipeId
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.model.RecipeLine
import ir.foodcost.core.model.RecipeVersion
import ir.foodcost.core.over
import ir.foodcost.core.pricing.PriceStatus
import ir.foodcost.core.reports.MenuItemReport
import ir.foodcost.core.units.Dimension
import ir.foodcost.core.units.UnitConverter
import ir.foodcost.core.units.Uom
import ir.foodcost.data.ledger.LedgerState
import ir.foodcost.data.ledger.PriceRecord
import java.math.BigDecimal
import java.time.LocalDate

// ---------------------------------------------------------------- menu status

data class MenuRow(
    val recipeId: RecipeId,
    val name: String,
    /** Set for event packages, so they stand out from plates. */
    val kindLabel: String?,
    val status: PriceStatus,
    val statusLabel: String,
    val foodCost: String,
    val menuPrice: String?,
    val suggestedPrice: String,
    val foodCostPercent: String?,
    /** How much the price should go up, such as ۸٪, when it is under or near the line. */
    val raise: String?,
)

data class MenuStatusModel(val date: String, val rows: List<MenuRow>, val counts: Map<PriceStatus, Int>) {
    fun count(status: PriceStatus): Int = counts[status] ?: 0

    val needsRepricing: Int get() = count(PriceStatus.UNDER) + count(PriceStatus.NEAR)
}

object MenuStatusScreen {
    private val ORDER = listOf(
        PriceStatus.UNDER, PriceStatus.NEAR, PriceStatus.INCOMPLETE,
        PriceStatus.NO_PRICE, PriceStatus.EMPTY, PriceStatus.OK,
    )

    /** Every menu item and event package on [date], the ones that need a new price first. */
    fun build(state: LedgerState, date: LocalDate, money: MoneyFormat): MenuStatusModel {
        val report = state.calculator().menu(date)
        val items = report.items.filter { state.recipes[it.recipe.id]?.archived != true }
        val names = Persian.nameOrder()
        val rows = items.sortedWith(
            compareBy<MenuItemReport> { ORDER.indexOf(it.status) }
                .thenByDescending { if (it.needsRaise()) it.evaluation.changePercent ?: BigDecimal.ZERO else BigDecimal.ZERO }
                .thenBy(names) { it.recipe.name },
        ).map { row(it, money) }
        return MenuStatusModel(JalaliDate.of(date).formatLong(), rows, items.groupingBy { it.status }.eachCount())
    }

    private fun MenuItemReport.needsRaise(): Boolean = status == PriceStatus.UNDER || status == PriceStatus.NEAR

    private fun row(item: MenuItemReport, money: MoneyFormat): MenuRow {
        val e = item.evaluation
        return MenuRow(
            recipeId = item.recipe.id,
            name = item.recipe.name,
            kindLabel = if (item.recipe.kind == RecipeKind.EVENT_PACKAGE) Labels.kind(item.recipe.kind) else null,
            status = item.status,
            statusLabel = Labels.status(item.status),
            foodCost = money.full(item.cost.foodCostPerPortion),
            menuPrice = e.currentPrice?.let { money.full(it) },
            suggestedPrice = money.full(e.suggestion.menuPrice),
            foodCostPercent = e.foodCostPercent?.let { Numbers.percent(it) },
            raise = e.changePercent?.takeIf { item.needsRaise() && it.signum() > 0 }?.let { Numbers.percent(it, 0) },
        )
    }
}

// ---------------------------------------------------------------- ingredients

data class IngredientRow(
    val id: IngredientId,
    val name: String,
    val category: String?,
    /** هر کیلو ۴۶۰٬۰۰۰ تومان, as bought. */
    val unitPrice: String?,
    /** Why there is no price, when there is none. */
    val priceProblem: String?,
    val yieldLabel: String?,
    val priceDate: String?,
)

data class PriceRow(
    val priceId: String,
    val date: String,
    val packageLabel: String,
    val total: String,
    val unitPrice: String?,
    val details: String?,
    val voided: Boolean,
    /** The price used today. */
    val inForce: Boolean,
)

data class IngredientDetail(
    val id: IngredientId,
    val name: String,
    val dimension: Dimension,
    val dimensionLabel: String,
    val category: String?,
    val yieldLabel: String,
    val conversionsLabel: String?,
    val unitPrice: String?,
    /** The cost of a usable kilo once the waste is taken out, when there is waste. */
    val usableUnitPrice: String?,
    val priceProblem: String?,
    val prices: List<PriceRow>,
    val usedIn: List<String>,
    val purchaseUnits: List<Uom>,
    val input: IngredientInput,
    val archived: Boolean,
)

object IngredientScreens {
    fun rows(state: LedgerState, date: LocalDate, money: MoneyFormat, query: String = ""): List<IngredientRow> {
        val catalog = state.catalog
        val costs = LatestPurchasePrice(catalog)
        val names = Persian.nameOrder()
        return state.ingredients.values
            .filter { !it.archived && Persian.matches(it.name.orEmpty(), query) }
            .mapNotNull { record -> catalog.ingredient(record.id) }
            .map { ingredient ->
                val unit = costs.unitCost(ingredient, date)
                IngredientRow(
                    id = ingredient.id,
                    name = ingredient.name,
                    category = ingredient.category,
                    unitPrice = (unit as? UnitCostResult.Found)?.let { Prices.perUnit(it.rialsPerBaseUnit, ingredient.baseDimension, money) },
                    priceProblem = priceProblem(unit),
                    yieldLabel = yieldPercent(ingredient)?.let { "بازدهی ${Numbers.percent(it)}" },
                    priceDate = (unit as? UnitCostResult.Found)?.price?.effectiveFrom?.let { JalaliDate.of(it).format() },
                )
            }
            .sortedWith(compareBy(names) { it.name })
    }

    fun detail(state: LedgerState, id: IngredientId, date: LocalDate, money: MoneyFormat): IngredientDetail? {
        val record = state.ingredients[id] ?: return null
        val catalog = state.catalog
        val ingredient = catalog.ingredient(id) ?: return null
        val unit = LatestPurchasePrice(catalog).unitCost(ingredient, date)
        val found = unit as? UnitCostResult.Found
        val records = state.prices.values.filter { it.price.ingredientId == id }
        val inForce = records
            .filter { !it.voided && !it.price.effectiveFrom.isAfter(date) }
            .maxWithOrNull(compareBy<PriceRecord> { it.price.effectiveFrom }.thenBy { it.recordedAt })
            ?.priceId
        val usable = ingredient.usableFraction
        val names = Persian.nameOrder()
        return IngredientDetail(
            id = id,
            name = ingredient.name,
            dimension = ingredient.baseDimension,
            dimensionLabel = Labels.dimension(ingredient.baseDimension),
            category = ingredient.category,
            yieldLabel = Numbers.percent(usable.multiply(BigDecimal(100))),
            conversionsLabel = conversionsLabel(ingredient),
            unitPrice = found?.let { Prices.perUnit(it.rialsPerBaseUnit, ingredient.baseDimension, money) },
            usableUnitPrice = found?.takeIf { usable < BigDecimal.ONE }
                ?.let { Prices.perUnit(it.rialsPerBaseUnit.over(usable), ingredient.baseDimension, money) },
            priceProblem = priceProblem(unit),
            prices = records
                .sortedWith(compareByDescending<PriceRecord> { it.price.effectiveFrom }.thenByDescending { it.recordedAt })
                .map { priceRow(it, ingredient, money, it.priceId == inForce) },
            usedIn = catalog.usersOf(ComponentRef.IngredientRef(id), date)
                .mapNotNull { catalog.recipe(it)?.name }
                .sortedWith(names),
            purchaseUnits = UnitOptions.forIngredient(ingredient.baseDimension, ingredient.conversions),
            input = IngredientInput.of(record),
            archived = record.archived,
        )
    }

    private fun yieldPercent(ingredient: Ingredient): BigDecimal? =
        ingredient.usableFraction.takeIf { it < BigDecimal.ONE }?.multiply(BigDecimal(100))

    private fun priceProblem(unit: UnitCostResult): String? = when (unit) {
        is UnitCostResult.Found -> null
        UnitCostResult.Missing -> "قیمت خرید ثبت نشده"
        is UnitCostResult.UnitMismatch -> "واحد قیمت خرید به نوع ماده نمی‌خورد"
    }

    private fun conversionsLabel(ingredient: Ingredient): String? {
        val parts = buildList {
            ingredient.conversions.gramsPerPiece?.let { add("هر عدد ${Prices.quantity(it, Uom.GRAM)}") }
            ingredient.conversions.gramsPerMilliliter?.let { add("هر میلی‌لیتر ${Prices.quantity(it, Uom.GRAM)}") }
        }
        return parts.joinToString(" · ").ifEmpty { null }
    }

    private fun priceRow(record: PriceRecord, ingredient: Ingredient, money: MoneyFormat, inForce: Boolean): PriceRow {
        val p = record.price
        val base = UnitConverter.toBase(p.packageQty, p.packageUom, ingredient.baseDimension, ingredient.conversions)
        val details = buildList {
            if (p.discountRials > 0) add("تخفیف ${money.full(p.discountRials)}")
            if (p.taxRials > 0) add((if (p.taxRecoverable) "مالیات قابل کسر " else "مالیات ") + money.full(p.taxRials))
            if (p.deliveryRials > 0) add("حمل ${money.full(p.deliveryRials)}")
            p.supplier?.let { add(it) }
        }.joinToString(" · ").ifEmpty { null }
        return PriceRow(
            priceId = record.priceId,
            date = JalaliDate.of(p.effectiveFrom).format(),
            packageLabel = Prices.quantity(p.packageQty, p.packageUom),
            total = money.full(p.netCostRials),
            unitPrice = base?.let { Prices.perUnit(BigDecimal.valueOf(p.netCostRials).over(it), ingredient.baseDimension, money) },
            details = details,
            voided = record.voided,
            inForce = inForce,
        )
    }
}

// ---------------------------------------------------------------- recipes

data class RecipeRow(
    val id: RecipeId,
    val name: String,
    val kind: RecipeKind,
    val cost: String,
    /** Null for preparations, which have no menu price. */
    val status: PriceStatus?,
    val statusLabel: String?,
    val complete: Boolean,
)

data class RecipeGroup(val kind: RecipeKind, val title: String, val rows: List<RecipeRow>)

data class LineRow(
    val section: Section,
    val index: Int,
    val name: String,
    val isSubRecipe: Boolean,
    val quantity: String,
    val cost: String,
    val issue: String?,
)

data class PricingInfo(
    val method: String,
    val suggested: String,
    val suggestedRials: Long,
    val menuPrice: String?,
    val menuPriceRials: Long?,
    val foodCostPercent: String?,
    val suggestedFoodCostPercent: String?,
    val raise: String?,
    val grossProfit: String?,
)

data class RecipeDetail(
    val id: RecipeId,
    val name: String,
    val kind: RecipeKind,
    val kindLabel: String,
    val category: String?,
    /** What an edit starts from: the version in force, dated today, or an empty one. */
    val draft: RecipeVersion,
    val since: String?,
    val portionsLabel: String,
    val lines: List<LineRow>,
    val miscLabel: String,
    val miscCost: String,
    val costPerPortion: String,
    /** Prevent incomplete zero-priced recipes from being quoted as valid. */
    val validForCalculator: Boolean,
    val batchCost: String?,
    val outputLabel: String?,
    val pricing: PricingInfo?,
    val issues: List<String>,
    val status: PriceStatus,
    val statusLabel: String,
    val header: RecipeInput,
    val batch: BatchInput,
)

/** Something a recipe line can use: an ingredient or another recipe. */
data class ComponentChoice(
    val ref: ComponentRef,
    val name: String,
    val detail: String,
    val units: List<Uom>,
    val defaultUnit: Uom,
    /** Ingredients with waste can be measured clean or as bought. */
    val hasWaste: Boolean,
)

object RecipeScreens {
    private val KIND_ORDER = listOf(RecipeKind.MENU_ITEM, RecipeKind.EVENT_PACKAGE, RecipeKind.PREP)

    fun groups(state: LedgerState, date: LocalDate, money: MoneyFormat, query: String = ""): List<RecipeGroup> {
        val calculator = state.calculator()
        val evaluations = calculator.menu(date).items.associateBy { it.recipe.id }
        val sheet = calculator.sheet(date)
        val names = Persian.nameOrder()
        val rows = state.catalog.recipes.values
            .filter { state.recipes[it.id]?.archived != true && Persian.matches(it.name, query) }
            .map { recipe ->
                val evaluation = evaluations[recipe.id]?.evaluation
                val cost = evaluation?.cost ?: sheet.recipe(recipe.id)
                RecipeRow(
                    id = recipe.id,
                    name = recipe.name,
                    kind = recipe.kind,
                    cost = money.full(cost.foodCostPerPortion),
                    status = evaluation?.status,
                    statusLabel = evaluation?.status?.let(Labels::status),
                    complete = cost.isComplete && cost.hasLines,
                )
            }
        return KIND_ORDER.mapNotNull { kind ->
            rows.filter { it.kind == kind }
                .sortedWith(compareBy(names) { it.name })
                .takeIf { it.isNotEmpty() }
                ?.let { RecipeGroup(kind, Labels.kindGroup(kind), it) }
        }
    }

    fun detail(state: LedgerState, id: RecipeId, date: LocalDate, money: MoneyFormat): RecipeDetail? {
        val record = state.recipes[id] ?: return null
        val catalog = state.catalog
        val recipe = catalog.recipe(id) ?: return null
        val evaluation = state.calculator().evaluate(id, date)
        val cost = evaluation.cost
        val version = recipe.versionAt(date)
        val draft = RecipeEdits.base(recipe, date)
        val portions = draft.portions
        val issues = cost.issues
            .filter { it.type != IssueType.NO_VERSION && it.type != IssueType.NO_LINES }
            .map { Labels.issue(it.type, it.component?.let { c -> name(catalog, c) } ?: recipe.name) }
            .distinct()
        val output = version?.let { v ->
            val qty = v.outputQty ?: return@let null
            val uom = v.outputUom ?: return@let null
            val perUnit = cost.costPerOutputUnit?.let { Prices.perUnit(it, uom.dimension, money) }
            listOfNotNull("خروجی ${Prices.quantity(qty, uom)}", perUnit).joinToString(" · ")
        }
        val s = evaluation.suggestion
        return RecipeDetail(
            id = id,
            name = recipe.name,
            kind = recipe.kind,
            kindLabel = Labels.kind(recipe.kind),
            category = recipe.category,
            draft = draft,
            since = version?.validFrom?.let { JalaliDate.of(it).format() },
            portionsLabel = if (portions.compareTo(BigDecimal.ONE) == 0) "برای ۱ پرس" else "برای ${Numbers.format(portions, 2)} پرس",
            lines = cost.lines.map { lineRow(it, catalog, money) },
            miscLabel = "هزینه‌های جزئی ${Numbers.percent(cost.miscPercent)}" + if (draft.miscPercent == null) " (پیش‌فرض)" else "",
            miscCost = money.full(cost.miscPerPortion),
            costPerPortion = money.full(cost.foodCostPerPortion),
            validForCalculator = cost.hasLines && cost.isComplete,
            batchCost = if (portions > BigDecimal.ONE) money.full(cost.batchCost) else null,
            outputLabel = output,
            pricing = if (recipe.kind == RecipeKind.PREP) {
                null
            } else {
                PricingInfo(
                    method = Labels.method(s.method),
                    suggested = money.full(s.menuPrice),
                    suggestedRials = s.menuPrice,
                    menuPrice = evaluation.currentPrice?.let { money.full(it) },
                    menuPriceRials = evaluation.currentPrice,
                    foodCostPercent = evaluation.foodCostPercent?.let { Numbers.percent(it) },
                    suggestedFoodCostPercent = s.foodCostPercent?.let { Numbers.percent(it) },
                    raise = evaluation.changePercent?.takeIf { it.signum() > 0 }?.let { Numbers.percent(it, 0) },
                    grossProfit = evaluation.grossProfit?.let { money.full(it) },
                )
            },
            issues = issues,
            status = evaluation.status,
            statusLabel = Labels.status(evaluation.status),
            header = RecipeInput(record.name.orEmpty(), record.kind ?: recipe.kind, record.category.orEmpty()),
            batch = BatchInput.of(draft),
        )
    }

    /** What a line of recipe [recipeId] can use. Recipes that use this one are left out, so no loop can be made. */
    fun choices(state: LedgerState, recipeId: RecipeId, date: LocalDate, money: MoneyFormat): List<ComponentChoice> {
        val catalog = state.catalog
        val names = Persian.nameOrder()
        val ingredients = state.ingredients.values
            .filter { !it.archived }
            .mapNotNull { catalog.ingredient(it.id) }
            .map { ingredientChoice(it, catalog, date, money) }
            .sortedWith(compareBy(names) { it.name })
        val users = catalog.usersOf(ComponentRef.RecipeRef(recipeId), date)
        val sheet = state.calculator().sheet(date)
        val recipes = catalog.recipes.values
            .filter { it.id != recipeId && it.id !in users && state.recipes[it.id]?.archived != true }
            .sortedWith(compareBy<Recipe> { if (it.kind == RecipeKind.PREP) 0 else 1 }.thenBy(names) { it.name })
            .map { recipeChoice(it, sheet.recipe(it.id).foodCostPerPortion, date, money) }
        return ingredients + recipes
    }

    /** The choice for a component already on a line, even if it was archived since. */
    fun choiceFor(state: LedgerState, ref: ComponentRef, date: LocalDate, money: MoneyFormat): ComponentChoice? {
        val catalog = state.catalog
        return when (ref) {
            is ComponentRef.IngredientRef -> catalog.ingredient(ref.id)?.let { ingredientChoice(it, catalog, date, money) }
            is ComponentRef.RecipeRef -> catalog.recipe(ref.id)?.let {
                recipeChoice(it, state.calculator().cost(it.id, date).foodCostPerPortion, date, money)
            }
        }
    }

    private fun ingredientChoice(ingredient: Ingredient, catalog: Catalog, date: LocalDate, money: MoneyFormat): ComponentChoice {
        val detail = when (val unit = LatestPurchasePrice(catalog).unitCost(ingredient, date)) {
            is UnitCostResult.Found -> Prices.perUnit(unit.rialsPerBaseUnit, ingredient.baseDimension, money)
            UnitCostResult.Missing -> "قیمت خرید ثبت نشده"
            is UnitCostResult.UnitMismatch -> "واحد قیمت خرید به نوع ماده نمی‌خورد"
        }
        return ComponentChoice(
            ref = ComponentRef.IngredientRef(ingredient.id),
            name = ingredient.name,
            detail = detail,
            units = UnitOptions.forIngredient(ingredient.baseDimension, ingredient.conversions),
            defaultUnit = UnitOptions.defaultForLine(ingredient.baseDimension),
            hasWaste = ingredient.usableFraction < BigDecimal.ONE,
        )
    }

    private fun recipeChoice(recipe: Recipe, costPerPortion: BigDecimal, date: LocalDate, money: MoneyFormat): ComponentChoice =
        ComponentChoice(
            ref = ComponentRef.RecipeRef(recipe.id),
            name = recipe.name,
            detail = "${Labels.kind(recipe.kind)} · هر پرس ${money.full(costPerPortion)}",
            units = UnitOptions.forSubRecipe(recipe.versionAt(date)),
            defaultUnit = Uom.PORTION,
            hasWaste = false,
        )

    private fun name(catalog: Catalog, ref: ComponentRef): String = when (ref) {
        is ComponentRef.IngredientRef -> catalog.ingredient(ref.id)?.name
        is ComponentRef.RecipeRef -> catalog.recipe(ref.id)?.name
    } ?: "حذف‌شده"

    private fun lineRow(line: LineCost, catalog: Catalog, money: MoneyFormat): LineRow {
        val l = line.line
        val name = name(catalog, l.component)
        val quantity = l.qty?.let { Prices.quantity(it, l.uom) } ?: "بدون مقدار"
        val raw = l.component is ComponentRef.IngredientRef && l.basis == QtyBasis.AS_PURCHASED
        return LineRow(
            section = line.section,
            index = line.index,
            name = name,
            isSubRecipe = l.component is ComponentRef.RecipeRef,
            quantity = if (raw) "$quantity · ${Labels.basis(QtyBasis.AS_PURCHASED)}" else quantity,
            cost = money.full(line.cost),
            issue = line.issue?.let { Labels.issue(it.type, name) },
        )
    }
}

/** Edits to a recipe. Each returns the new version, dated [RecipeVersion.validFrom] of the version it starts from. */
object RecipeEdits {
    /** The version to edit on [date]: the one in force moved to [date], or an empty one. */
    fun base(recipe: Recipe?, date: LocalDate): RecipeVersion =
        recipe?.versionAt(date)?.copy(validFrom = date) ?: RecipeVersion(date)

    fun lineAt(version: RecipeVersion, section: Section, index: Int): RecipeLine? =
        lines(version, section).getOrNull(index)

    fun addLine(version: RecipeVersion, line: RecipeLine): RecipeVersion = version.copy(batchLines = version.batchLines + line)

    fun replaceLine(version: RecipeVersion, section: Section, index: Int, line: RecipeLine): RecipeVersion =
        withLines(version, section, lines(version, section).mapIndexed { i, old -> if (i == index) line else old })

    fun removeLine(version: RecipeVersion, section: Section, index: Int): RecipeVersion =
        withLines(version, section, lines(version, section).filterIndexed { i, _ -> i != index })

    fun withBatch(version: RecipeVersion, batch: Batch): RecipeVersion = version.copy(
        portions = batch.portions,
        miscPercent = batch.miscPercent,
        outputQty = batch.outputQty,
        outputUom = batch.outputUom,
    )

    private fun lines(version: RecipeVersion, section: Section): List<RecipeLine> = when (section) {
        Section.BATCH -> version.batchLines
        Section.PORTION -> version.portionLines
    }

    private fun withLines(version: RecipeVersion, section: Section, lines: List<RecipeLine>): RecipeVersion = when (section) {
        Section.BATCH -> version.copy(batchLines = lines)
        Section.PORTION -> version.copy(portionLines = lines)
    }
}
