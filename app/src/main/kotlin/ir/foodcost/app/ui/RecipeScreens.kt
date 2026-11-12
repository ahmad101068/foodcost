package ir.foodcost.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ir.foodcost.app.ui.theme.FoodSectionColors
import ir.foodcost.app.ui.theme.foodSectionColors
import ir.foodcost.app.ui.theme.statusColors
import ir.foodcost.core.model.ComponentRef
import ir.foodcost.core.model.QtyBasis
import ir.foodcost.core.model.RecipeId
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.model.RecipeLine
import ir.foodcost.core.pricing.PriceStatus
import ir.foodcost.core.units.Uom
import ir.foodcost.data.ledger.LedgerState
import ir.foodcost.data.ops.Opt
import ir.foodcost.presentation.BackupTexts
import ir.foodcost.presentation.Batch
import ir.foodcost.presentation.Checked
import ir.foodcost.presentation.ComponentChoice
import ir.foodcost.presentation.DishesScreen
import ir.foodcost.presentation.Field
import ir.foodcost.presentation.Labels
import ir.foodcost.presentation.LineInput
import ir.foodcost.presentation.LineRow
import ir.foodcost.presentation.MenuRow
import ir.foodcost.presentation.MoneyFormat
import ir.foodcost.presentation.Persian
import ir.foodcost.presentation.PricingInfo
import ir.foodcost.presentation.RecipeDetail
import ir.foodcost.presentation.RecipeEdits
import ir.foodcost.presentation.RecipeInput
import ir.foodcost.presentation.RecipeRow
import ir.foodcost.presentation.RecipeScreens
import ir.foodcost.presentation.Sections
import ir.foodcost.presentation.checkMenuPrice
import java.time.LocalDate

/**
 * The dishes tab: every plate with what it costs, its menu price, the suggested price and whether
 * the price still covers it, the ones that need a new price first; then the preparations.
 */
private enum class FoodFilter { ALL, NEEDS_PRICE, SUITABLE }

@Composable
fun RecipesScreen(state: LedgerState, money: MoneyFormat, today: LocalDate, nav: Navigator, actions: AppActions) {
    var query by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingBase by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(FoodFilter.ALL) }
    var sortByName by rememberSaveable { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Pair<RecipeId, String>?>(null) }
    val model = remember(state, money, today) { DishesScreen.build(state, today, money) }
    var expandedId by rememberSaveable { mutableStateOf(model.plates.firstOrNull()?.recipeId?.value) }
    val dataUnreliable = actions.unreadableRows > 0
    val categories = model.plates.mapNotNull { state.recipes[it.recipeId]?.category?.trim()?.takeIf(String::isNotEmpty) }
        .distinct().sortedWith(Persian.nameOrder())
    val activeCategory = categoryFilter?.takeIf { it in categories }
    LaunchedEffect(categories) { if (categoryFilter != null && activeCategory == null) categoryFilter = null }
    val activeFilter = if (dataUnreliable) FoodFilter.ALL else filter
    fun needsPrice(row: MenuRow) = row.status == PriceStatus.UNDER || row.status == PriceStatus.NEAR
    val matching = model.plates.filter { Persian.matches(it.name, query) &&
        (activeCategory == null || state.recipes[it.recipeId]?.category?.trim() == activeCategory) }
    val plates = matching.filter { when (activeFilter) {
        FoodFilter.ALL -> true; FoodFilter.NEEDS_PRICE -> needsPrice(it); FoodFilter.SUITABLE -> it.status == PriceStatus.OK
    } }.let { if (sortByName) it.sortedWith(compareBy(Persian.nameOrder()) { row -> row.name }) else it }
    val groups = plates.groupBy { state.recipes[it.recipeId]?.category?.trim()?.takeIf(String::isNotEmpty) ?: "بدون بخش" }
        .toSortedMap(Persian.nameOrder())
    val preparations = if (activeCategory == null && activeFilter == FoodFilter.ALL)
        model.preparations.filter { Persian.matches(it.name, query) } else emptyList()
    TabScaffold(title = "غذاها", nav = nav, actions = actions,
        topActions = { HeaderAddButton("افزودن غذا", onClick = { creating = true }) }) { padding ->
        LazyColumn(Modifier.padding(padding).testTag("food-list"),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp)) {
            item {
                Box {
                    SearchField(query, { query = it }, "جستجوی غذا", onOptions = { options = true })
                    DropdownMenu(options, onDismissRequest = { options = false }) {
                        DropdownMenuItem(text = { Text(if (sortByName) "مرتب‌سازی بر اساس وضعیت قیمت" else "مرتب‌سازی بر اساس نام") },
                            onClick = { sortByName = !sortByName; options = false }, modifier = Modifier.testTag("food-sort"))
                        DropdownMenuItem(text = { Text("همهٔ بخش‌ها") }, onClick = { categoryFilter = null; options = false }, modifier = Modifier.testTag("food-filter-all-sections"))
                        categories.forEach { category ->
                            DropdownMenuItem(text = { Text(category) }, onClick = { categoryFilter = category; options = false },
                                modifier = Modifier.testTag("food-filter-category-$category"))
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    CountChoice("همه", Persian.digits(matching.size), activeFilter == FoodFilter.ALL,
                        Modifier.weight(1f).testTag("food-filter-all")) { filter = FoodFilter.ALL }
                    CountChoice("نیازمند اصلاح", if (dataUnreliable) "—" else Persian.digits(matching.count(::needsPrice)), activeFilter == FoodFilter.NEEDS_PRICE,
                        Modifier.weight(1.45f).testTag("food-filter-needs"), enabled = !dataUnreliable, tone = PriceStatus.UNDER) { filter = FoodFilter.NEEDS_PRICE }
                    CountChoice("مناسب", if (dataUnreliable) "—" else Persian.digits(matching.count { it.status == PriceStatus.OK }), activeFilter == FoodFilter.SUITABLE,
                        Modifier.weight(1f).testTag("food-filter-suitable"), enabled = !dataUnreliable, tone = PriceStatus.OK) { filter = FoodFilter.SUITABLE }
                }
            }
            activeCategory?.let { category -> item {
                TextButton(onClick = { categoryFilter = null }) { Text("بخش: $category ×") }
            } }
            if (plates.isEmpty() && preparations.isEmpty()) item {
                if (model.isEmpty) EmptyState("هنوز غذایی ذخیره نشده", "غذا را در ماشین‌حساب حساب و ذخیره کنید تا بها و وضعیت قیمتش اینجا دیده شود.", action = "رفتن به ماشین‌حساب", onAction = { nav.tab(Tab.CALC) })
                else EmptyState("غذایی با این فیلتر پیدا نشد", "جستجو، وضعیت قیمت یا بخش منو را تغییر دهید.", action = "نمایش همهٔ غذاها", onAction = { filter = FoodFilter.ALL; categoryFilter = null; query = "" })
            }
            groups.forEach { (category, rows) ->
                item(key = "heading-$category") { GroupHeading(category, rows.size, "غذا") }
                itemsIndexed(rows, key = { _, row -> "food-${row.recipeId.value}" }) { index, row ->
                    GroupedRow(index == 0, index == rows.lastIndex) {
                        GroupedPlate(row, money, expandedId == row.recipeId.value, dataUnreliable,
                            onToggle = { expandedId = row.recipeId.value.takeUnless { expandedId == it } },
                            onOpen = { nav.open(dishScreen(state, row.recipeId, today, money)) },
                            onDetails = { nav.open(Screen.Recipe(row.recipeId.value)) },
                            onDelete = { deleteTarget = row.recipeId to row.name })
                    }
                }
            }
            if (preparations.isNotEmpty()) {
                item { GroupHeading("دستورهای پایه", preparations.size, "دستور") }
                items(preparations, key = { "prep-${it.id.value}" }) { row ->
                    PreparationCard(row, dataUnreliable) { nav.open(Screen.Recipe(row.id.value)) }
                }
            }
            item { TextButton(onClick = { creatingBase = true }, modifier = Modifier.padding(top = 12.dp)) { Text("دستور پایه (برای ترکیبات مشترک)") } }
        }
    }
    deleteTarget?.let { (recipeId, name) ->
        ConfirmDialog(
            title = "حذف «$name»؟",
            text = "این غذا از فهرست فعال حذف می‌شود. سوابق قبلی برای صحت اطلاعات مالی حفظ خواهند شد.",
            confirm = "حذف غذا",
            onConfirm = {
                actions.run({ it.updateRecipe(recipeId, archived = Opt.Value(true)) }) {
                    deleteTarget = null
                }
            },
            onDismiss = { deleteTarget = null },
        )
    }
    if (creating) {
        RecipeHeaderDialog(
            title = "افزودن غذا",
            initial = RecipeInput(kind = RecipeKind.MENU_ITEM),
            sections = Sections.of(state),
            onDismiss = { creating = false },
            onSave = { input ->
                actions.run({ it.createRecipe(input.name, input.kind, input.categoryOrNull) }) { id ->
                    creating = false
                    nav.open(Screen.Recipe(id.value))
                }
            },
        )
    }
    if (creatingBase) {
        RecipeHeaderDialog(
            title = "دستور پایه",
            initial = RecipeInput(kind = RecipeKind.PREP),
            sections = Sections.of(state),
            onDismiss = { creatingBase = false },
            onSave = { input ->
                actions.run({ it.createRecipe(input.name, RecipeKind.PREP, null) }) { id ->
                    creatingBase = false
                    nav.open(Screen.Recipe(id.value))
                }
            },
        )
    }
}

@Composable
private fun UnverifiedFinancialData() {
    Text(
        BackupTexts.UNVERIFIED_RESULT,
        modifier = Modifier.testTag("unverified-financial-result"),
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun GroupedPlate(row: MenuRow, money: MoneyFormat, expanded: Boolean, dataUnreliable: Boolean,
    onToggle: () -> Unit, onOpen: () -> Unit, onDetails: () -> Unit, onDelete: () -> Unit) {
    val large = LocalDensity.current.fontScale > 1.25f
    val palette = statusColors(row.status)
    @Composable fun status() {
        if (!dataUnreliable) FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            ListPriceStatus(row.status, row.statusLabel)
            row.foodCostPercent?.let { Text("فودکاست $it", style = MaterialTheme.typography.bodySmall, color = palette.content) }
        }
    }
    @Composable fun price(modifier: Modifier) {
        if (dataUnreliable) Text(BackupTexts.UNVERIFIED_PRICE, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        else GroupAmount(row.menuPrice ?: "ثبت نشده", money.unit, modifier.testTag("food-price-${row.recipeId.value}"))
    }
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "جزئیات ${row.name}", onClick = onToggle)
        .padding(horizontal = 9.dp, vertical = 10.dp).testTag("food-${row.recipeId.value}"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        if (!large) RowSymbol(ReferenceIcons.dishes)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(row.name, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp))
            row.kindLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            status()
            if (large) price(Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        if (!large) price(Modifier.width(105.dp))
        RowChevron(expanded, onToggle)
        RowMenu("food-menu-${row.recipeId.value}") { close ->
            DropdownMenuItem(text = { Text("اصلاح قیمت") }, onClick = { close(); onOpen() }, enabled = !dataUnreliable)
            DropdownMenuItem(text = { Text("جزئیات و ویرایش غذا") }, onClick = { close(); onDetails() })
            DropdownMenuItem(text = { Text("حذف غذا") }, onClick = { close(); onDelete() }, enabled = !dataUnreliable,
                modifier = Modifier.testTag("delete-dish-${row.recipeId.value}"))
        }
    }
    if (expanded) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (dataUnreliable) UnverifiedFinancialData() else {
                    if (large) {
                        SmallFact("هزینهٔ مواد هر پرس", row.foodCost)
                        SmallFact("قیمت پیشنهادی", row.suggestedPrice)
                    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SmallFact("هزینهٔ مواد هر پرس", row.foodCost, Modifier.weight(1f))
                        SmallFact("قیمت پیشنهادی", row.suggestedPrice, Modifier.weight(1f))
                    }
                    row.otherCosts?.let { Text("بهای تمام‌شده: ${row.cost}", style = MaterialTheme.typography.bodySmall) }
                    row.raise?.let { Text("برای رسیدن به هدف قیمت، $it افزایش لازم است.", style = MaterialTheme.typography.bodySmall, color = palette.content) }
                }
                Button(onClick = onOpen, shape = RoundedCornerShape(10.dp), modifier = Modifier.testTag("food-open-${row.recipeId.value}")) {
                    Text(if (dataUnreliable) "مشاهدهٔ محاسبه" else "اصلاح قیمت")
                }
            }
        }
    }
}

@Composable
private fun PreparationCard(row: RecipeRow, dataUnreliable: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = whiteCardColors()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(row.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (dataUnreliable) UnverifiedFinancialData() else LabeledValue("هزینهٔ مواد اولیه هر پرس", row.cost, amount = true)
        }
    }
}

private enum class RecipeDialog { HEADER, ARCHIVE, MENU_PRICE, BATCH }

/** One recipe: what goes in it, what a portion costs and what it should sell for. */
@Composable
fun RecipeScreen(id: RecipeId, state: LedgerState, money: MoneyFormat, today: LocalDate, nav: Navigator, actions: AppActions) {
    val detail = remember(state, money, today, id) { RecipeScreens.detail(state, id, today, money) }
    if (detail == null) {
        LaunchedEffect(id) { nav.back() }
        return
    }
    var dialog by rememberSaveable { mutableStateOf<RecipeDialog?>(null) }
    DetailScaffold(
        title = detail.name,
        nav = nav,
        actions = actions,
        topActions = {
            IconButton(onClick = { dialog = RecipeDialog.HEADER }) {
                Icon(Icons.Filled.Edit, contentDescription = "ویرایش دستور")
            }
            IconButton(
                onClick = { dialog = RecipeDialog.ARCHIVE },
                modifier = Modifier.testTag("delete-recipe"),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "حذف غذا", tint = MaterialTheme.colorScheme.error)
            }
        },
        floatingActionButton = {
            AddButton("افزودن ماده", onClick = { nav.open(Screen.Line(id.value, null, null)) })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { if (actions.unreadableRows > 0) UnverifiedFinancialData() else CostCard(detail, onEditPrice = { dialog = RecipeDialog.MENU_PRICE }) }
            if (detail.issues.isNotEmpty()) {
                item { IssuesCard(detail.issues) }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("مواد، ${detail.portionsLabel}", Modifier.weight(1f))
                    TextButton(onClick = { dialog = RecipeDialog.BATCH }) { Text("تعداد پرس و جزئیات") }
                }
            }
            if (detail.lines.isEmpty()) {
                item {
                    Text(
                        "هنوز ماده‌ای اضافه نشده. با دکمهٔ «افزودن ماده» شروع کنید.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            items(detail.lines, key = { "${it.section.name}-${it.index}" }) { line ->
                LineItem(line) { nav.open(Screen.Line(id.value, line.section, line.index)) }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        detail.miscLabel,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(detail.miscCost, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    when (dialog) {
        RecipeDialog.HEADER -> RecipeHeaderDialog(
            title = "ویرایش دستور",
            initial = detail.header,
            sections = Sections.of(state),
            note = "نوع و بخش منوی تازه از امروز حساب می‌شوند؛ عددهای روزهای قبل همان می‌مانند.",
            onDismiss = { dialog = null },
            onSave = { input ->
                actions.run({
                    it.updateRecipe(
                        id,
                        name = Opt.Value(input.name),
                        kind = Opt.Value(input.kind),
                        category = Opt.Value(input.categoryOrNull),
                        effectiveFrom = today,
                    )
                }) { dialog = null }
            },
            onArchive = { dialog = RecipeDialog.ARCHIVE },
        )
        RecipeDialog.ARCHIVE -> ConfirmDialog(
            title = "حذف «${detail.name}»؟",
            text = "این غذا از فهرست غذاها و منو حذف می‌شود. اطلاعات تاریخی برای حفظ صحت سوابق مالی باقی می‌ماند.",
            confirm = "حذف",
            onConfirm = {
                actions.run({ it.updateRecipe(id, archived = Opt.Value(true)) }) {
                    dialog = null
                    nav.back()
                }
            },
            onDismiss = { dialog = null },
        )
        RecipeDialog.MENU_PRICE -> detail.pricing?.let { pricing ->
            MenuPriceDialog(
                pricing = pricing,
                money = money,
                onDismiss = { dialog = null },
                onSave = { rials -> actions.run({ it.setMenuPrice(id, today, rials) }) { dialog = null } },
            )
        }
        RecipeDialog.BATCH -> BatchDialog(
            detail = detail,
            onDismiss = { dialog = null },
            onSave = { batch ->
                actions.run({ it.saveRecipeVersion(id, RecipeEdits.withBatch(detail.draft, batch)) }) { dialog = null }
            },
        )
        null -> Unit
    }
}

@Composable
private fun CostCard(detail: RecipeDetail, onEditPrice: () -> Unit) {
    // The same lapis window as the calculator's display, so a cost always looks the same.
    DisplayPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val about = listOfNotNull(detail.kindLabel, detail.category)
                if (about.isNotEmpty()) {
                    Text(
                        about.joinToString("، "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // As on top of the calculator: the food alone; with other costs, the whole cost below.
                Text("هزینهٔ مواد اولیه هر پرس", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AmountText(detail.costPerPortion, MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (detail.kind != RecipeKind.PREP) StatusChip(detail.status, detail.statusLabel)
        }
        detail.pricing?.totalCost?.let { LabeledValue("بهای تمام‌شده", it, amount = true) }
        detail.batchCost?.let { LabeledValue("کل دستور", it, amount = true) }
        detail.outputLabel?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        detail.pricing?.let { pricing ->
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LabeledValue("قیمت پیشنهادی", pricing.suggested, Modifier.weight(1f), emphasized = true, amount = true)
                LabeledValue("قیمت فعلی منو", pricing.menuPrice ?: "ثبت نشده", Modifier.weight(1f), emphasized = true, amount = true)
            }
            val notes = listOfNotNull(
                pricing.method,
                pricing.foodCostPercent?.let { "فودکاست با قیمت فعلی $it" },
                pricing.grossProfit?.let { "سود ناخالص با قیمت فعلی $it" },
                pricing.raise?.let { "باید $it گران‌تر شود" },
            )
            Text(
                notes.joinToString("، "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onEditPrice) {
                Text(if (pricing.menuPrice == null) "ثبت قیمت منو" else "تغییر قیمت منو")
            }
        }
    }
}

@Composable
private fun IssuesCard(issues: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("این دستور کامل نیست؛ بها را با احتیاط ببینید.", style = MaterialTheme.typography.titleSmall)
            }
            issues.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun LineItem(line: LineRow, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = whiteCardColors()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(line.name + if (line.isSubRecipe) " (دستور)" else "", style = MaterialTheme.typography.bodyLarge)
                Text(line.quantity, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                line.issue?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
            Text(line.cost, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun RecipeHeaderDialog(
    title: String,
    initial: RecipeInput,
    onDismiss: () -> Unit,
    onSave: (RecipeInput) -> Unit,
    onArchive: (() -> Unit)? = null,
    /** The menu sections of the settings, and any other a dish is already in, offered to choose from. */
    sections: List<String> = emptyList(),
    /** Said under the fields, such as from which day a change counts. */
    note: String? = null,
) {
    var input by remember { mutableStateOf(initial) }
    var errors by remember { mutableStateOf(emptyMap<Field, String>()) }
    var sectionMenuExpanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextInput(input.name, { input = input.copy(name = it) }, "نام", error = errors[Field.NAME])
                // A dish being edited can become a base recipe and back; the change counts from today,
                // as the note below says. A new one gets its kind from the button it was made with.
                if (onArchive != null) {
                    val kinds = listOf(RecipeKind.MENU_ITEM, RecipeKind.PREP) + listOfNotNull(RecipeKind.EVENT_PACKAGE.takeIf { initial.kind == it })
                    ChoiceChips(kinds, input.kind, { Labels.kind(it) }, Modifier.testTag("recipe-kind")) { input = input.copy(kind = it) }
                }
                if (input.kind == RecipeKind.EVENT_PACKAGE) {
                    Text("پکیج قدیمی؛ برای حفظ سازگاری اطلاعات قبلی قابل ویرایش است.")
                } else if (input.kind == RecipeKind.PREP) {
                    Text("دستور پایه برای ترکیباتی است که در چند غذا استفاده می‌شوند.")
                }
                if (input.kind == RecipeKind.MENU_ITEM) {
                    androidx.compose.foundation.layout.Box {
                        OutlinedButton(
                            onClick = { sectionMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth().testTag("recipe-menu-section"),
                        ) {
                            Text("بخش منو: " + input.category.ifBlank { "بدون بخش" })
                        }
                        DropdownMenu(
                            expanded = sectionMenuExpanded,
                            onDismissRequest = { sectionMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("بدون بخش") },
                                onClick = {
                                    input = input.copy(category = "")
                                    sectionMenuExpanded = false
                                },
                            )
                            // In the settings' order, then the sections only dishes are in.
                            sections.distinct().forEach { section ->
                                DropdownMenuItem(
                                    text = { Text(section) },
                                    onClick = {
                                        input = input.copy(category = section)
                                        sectionMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    if (sections.isEmpty()) {
                        Text(
                            "بخش‌های منو را در «تنظیمات»، بخش «قیمت‌گذاری اختصاصی بخش‌های منو» بسازید؛ بخشی که روش خودش را ندارد با روش عمومی قیمت می‌خورد.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (onArchive != null) {
                    TextButton(onClick = onArchive) {
                        Text("حذف این دستور", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when (val checked = input.check()) {
                    is Checked.Valid -> onSave(checked.value)
                    is Checked.Invalid -> errors = checked.errors
                }
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun MenuPriceDialog(pricing: PricingInfo, money: MoneyFormat, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by remember { mutableStateOf(pricing.menuPriceRials?.let { money.editable(it) } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("قیمت منو") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(
                    value = text,
                    onValueChange = {
                        text = it
                        error = null
                    },
                    label = "قیمت (${money.unit})",
                    error = error,
                    supporting = money.parse(text)?.let { money.full(it) },
                    number = true,
                )
                pricing.suggestedRials?.let { suggested ->
                    TextButton(onClick = { text = money.editable(suggested) }) {
                        Text("همان قیمت پیشنهادی: ${pricing.suggested}")
                    }
                }
                Text(
                    "قیمت تازه از امروز حساب می‌شود؛ قیمت روزهای قبل همان می‌ماند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when (val checked = checkMenuPrice(text, money)) {
                    is Checked.Valid -> onSave(checked.value)
                    is Checked.Invalid -> error = checked.errors.values.firstOrNull()
                }
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun BatchDialog(detail: RecipeDetail, onDismiss: () -> Unit, onSave: (Batch) -> Unit) {
    var input by remember { mutableStateOf(detail.batch) }
    var errors by remember { mutableStateOf(emptyMap<Field, String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعداد پرس و جزئیات") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(
                    value = input.portions,
                    onValueChange = { input = input.copy(portions = it) },
                    label = "مقدارها برای چند پرس است؟",
                    error = errors[Field.PORTIONS],
                    number = true,
                )
                TextInput(
                    value = input.miscPercent,
                    onValueChange = { input = input.copy(miscPercent = it) },
                    label = "هزینه‌های جزئی (٪)",
                    error = errors[Field.MISC],
                    supporting = "نمک، ادویه و روغن سرخ کردن. خالی بماند، پیش‌فرض حساب می‌شود.",
                    number = true,
                )
                if (detail.kind == RecipeKind.PREP) {
                    TextInput(
                        value = input.outputQty,
                        onValueChange = { input = input.copy(outputQty = it) },
                        label = "خروجی کل (اختیاری)",
                        error = errors[Field.OUTPUT],
                        supporting = "مثلاً ۴ کیلو چلو؛ برای برداشتن به وزن لازم است.",
                        number = true,
                    )
                    ChoiceChips(
                        listOf(Uom.KILOGRAM, Uom.GRAM, Uom.LITER, Uom.MILLILITER, Uom.PIECE),
                        input.outputUom,
                        { Labels.uom(it) },
                    ) { input = input.copy(outputUom = it) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when (val checked = input.check()) {
                    is Checked.Valid -> onSave(checked.value)
                    is Checked.Invalid -> errors = checked.errors
                }
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

/** Adds a line to a recipe or edits one: first what to use, then how much. */
@Composable
fun LineEditorScreen(screen: Screen.Line, state: LedgerState, money: MoneyFormat, today: LocalDate, nav: Navigator, actions: AppActions) {
    val recipeId = RecipeId(screen.recipeId)
    val detail = remember(state, money, today, recipeId) { RecipeScreens.detail(state, recipeId, today, money) }
    if (detail == null) {
        LaunchedEffect(recipeId) { nav.back() }
        return
    }
    val section = screen.section
    val index = screen.index
    val existing: RecipeLine? = if (section != null && index != null) RecipeEdits.lineAt(detail.draft, section, index) else null
    var chosen by remember { mutableStateOf(existing?.component) }
    val picked = chosen
    val choice = remember(picked, state, money, today) {
        if (picked == null) null else RecipeScreens.choiceFor(state, picked, today, money)
    }
    if (choice == null) {
        ComponentPicker(recipeId, state, money, today, nav, actions) { chosen = it.ref }
        return
    }
    val sameComponent = existing != null && existing.component == choice.ref
    LineForm(
        choice = choice,
        existing = if (sameComponent) existing else null,
        nav = nav,
        actions = actions,
        onSave = { line ->
            val version = if (section != null && index != null && existing != null) {
                RecipeEdits.replaceLine(detail.draft, section, index, line)
            } else {
                RecipeEdits.addLine(detail.draft, line)
            }
            actions.run({ it.saveRecipeVersion(recipeId, version) }) { nav.back() }
        },
        onDelete = if (section != null && index != null && existing != null) {
            {
                actions.run({ it.saveRecipeVersion(recipeId, RecipeEdits.removeLine(detail.draft, section, index)) }) { nav.back() }
            }
        } else {
            null
        },
    )
}

@Composable
private fun ComponentPicker(
    recipeId: RecipeId,
    state: LedgerState,
    money: MoneyFormat,
    today: LocalDate,
    nav: Navigator,
    actions: AppActions,
    onPick: (ComponentChoice) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val choices = remember(state, money, today, recipeId) { RecipeScreens.choices(state, recipeId, today, money) }
    val shown = remember(choices, query) { choices.filter { Persian.matches(it.name, query) } }
    val ingredients = shown.filter { it.ref is ComponentRef.IngredientRef }
    val recipes = shown.filter { it.ref is ComponentRef.RecipeRef }
    DetailScaffold(title = "چه چیزی اضافه شود؟", nav = nav, actions = actions) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            item { SearchField(query, { query = it }, "جستجوی ماده یا دستور") }
            if (choices.isEmpty()) {
                item {
                    EmptyState(
                        title = "هنوز ماده‌ای ثبت نشده",
                        body = "اول از بخش «مواد» مواد اولیه و قیمت خریدشان را ثبت کنید.",
                        action = "رفتن به مواد",
                        onAction = { nav.tab(Tab.INGREDIENTS) },
                    )
                }
            }
            if (ingredients.isNotEmpty()) {
                item(key = "ingredients") { SectionTitle("مواد اولیه") }
                items(ingredients, key = { keyOf(it.ref) }) { choice -> ChoiceItem(choice) { onPick(choice) } }
            }
            if (recipes.isNotEmpty()) {
                item(key = "recipes") { SectionTitle("دستورهای پایه و غذاها", Modifier.padding(top = 12.dp)) }
                items(recipes, key = { keyOf(it.ref) }) { choice -> ChoiceItem(choice) { onPick(choice) } }
            }
        }
    }
}

private fun keyOf(ref: ComponentRef): String = when (ref) {
    is ComponentRef.IngredientRef -> "i-${ref.id.value}"
    is ComponentRef.RecipeRef -> "r-${ref.id.value}"
}

@Composable
private fun ChoiceItem(choice: ComponentChoice, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(choice.name) },
        supportingContent = { Text(choice.detail) },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider()
}

@Composable
private fun LineForm(
    choice: ComponentChoice,
    existing: RecipeLine?,
    nav: Navigator,
    actions: AppActions,
    onSave: (RecipeLine) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var input by remember(choice.ref) {
        mutableStateOf(existing?.let { LineInput.of(it) } ?: LineInput(uom = choice.defaultUnit))
    }
    var errors by remember { mutableStateOf(emptyMap<Field, String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    DetailScaffold(
        title = choice.name,
        nav = nav,
        actions = actions,
        topActions = {
            if (onDelete != null) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "حذف از دستور")
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(choice.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextInput(
                value = input.qty,
                onValueChange = { input = input.copy(qty = it) },
                label = "مقدار",
                error = errors[Field.QUANTITY],
                number = true,
                suffix = Labels.uom(input.uom),
            )
            Text("واحد", style = MaterialTheme.typography.labelLarge)
            ChoiceChips(choice.units, input.uom, { Labels.uom(it) }) { input = input.copy(uom = it) }
            if (choice.hasWaste) {
                Text("مقدار نوشته‌شده", style = MaterialTheme.typography.labelLarge)
                Segmented(listOf(QtyBasis.USABLE, QtyBasis.AS_PURCHASED), input.basis, { Labels.basis(it) }) {
                    input = input.copy(basis = it)
                }
                Text(
                    Labels.basisHint(input.basis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = {
                    when (val checked = input.check(choice.ref)) {
                        is Checked.Valid -> onSave(checked.value)
                        is Checked.Invalid -> errors = checked.errors
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("ذخیره") }
        }
    }
    if (confirmDelete && onDelete != null) {
        ConfirmDialog(
            title = "حذف «${choice.name}» از دستور؟",
            text = "این ماده از دستور برداشته می‌شود. نسخه‌های قبلی دستور در سابقه می‌مانند.",
            confirm = "حذف",
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
