package ir.foodcost.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import ir.foodcost.core.model.ComponentRef
import ir.foodcost.core.model.QtyBasis
import ir.foodcost.core.model.RecipeId
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.model.RecipeLine
import ir.foodcost.core.units.Uom
import ir.foodcost.data.ledger.LedgerState
import ir.foodcost.data.ops.Opt
import ir.foodcost.presentation.Batch
import ir.foodcost.presentation.Checked
import ir.foodcost.presentation.ComponentChoice
import ir.foodcost.presentation.Field
import ir.foodcost.presentation.Labels
import ir.foodcost.presentation.LineInput
import ir.foodcost.presentation.LineRow
import ir.foodcost.presentation.MoneyFormat
import ir.foodcost.presentation.Persian
import ir.foodcost.presentation.PricingInfo
import ir.foodcost.presentation.RecipeDetail
import ir.foodcost.presentation.RecipeEdits
import ir.foodcost.presentation.RecipeInput
import ir.foodcost.presentation.RecipeRow
import ir.foodcost.presentation.RecipeScreens
import ir.foodcost.presentation.checkMenuPrice
import java.time.LocalDate

/** The recipes tab: every recipe, grouped by kind. */
@Composable
fun RecipesScreen(state: LedgerState, money: MoneyFormat, today: LocalDate, nav: Navigator, actions: AppActions) {
    var query by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    val groups = remember(state, money, today, query) { RecipeScreens.groups(state, today, money, query) }
    TabScaffold(
        title = "دستورها",
        nav = nav,
        actions = actions,
        floatingActionButton = {
            AddButton("دستور تازه", onClick = { creating = true })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        ) {
            item { SearchField(query, { query = it }, "جستجوی دستور") }
            if (groups.isEmpty()) {
                item {
                    if (query.isBlank()) {
                        EmptyState(
                            title = "هنوز دستوری ساخته نشده",
                            body = "برای هر غذا یک دستور بسازید و مواد و مقدارشان را وارد کنید. چیزهایی مثل چلو یا سس را می‌شود «نیمه‌آماده» ساخت و در چند غذا به کار برد.",
                        )
                    } else {
                        EmptyState(title = "دستوری با این نام پیدا نشد", body = "نام دیگری را جستجو کنید.")
                    }
                }
            }
            groups.forEach { group ->
                item(key = "group-${group.kind.name}") { SectionTitle(group.title, Modifier.padding(top = 12.dp)) }
                items(group.rows, key = { it.id.value }) { row ->
                    RecipeListItem(row) { nav.open(Screen.Recipe(row.id.value)) }
                }
            }
        }
    }
    if (creating) {
        RecipeHeaderDialog(
            title = "دستور تازه",
            initial = RecipeInput(),
            onDismiss = { creating = false },
            onSave = { input ->
                actions.run({ it.createRecipe(input.name, input.kind, input.categoryOrNull) }) { id ->
                    creating = false
                    nav.open(Screen.Recipe(id.value))
                }
            },
        )
    }
}

@Composable
private fun RecipeListItem(row: RecipeRow, onClick: () -> Unit) {
    val status = row.status
    ListItem(
        headlineContent = { Text(row.name) },
        supportingContent = { Text("بهای هر پرس ${row.cost}" + if (row.complete) "" else " · ناقص") },
        trailingContent = if (status != null) {
            { StatusChip(status, row.statusLabel.orEmpty()) }
        } else {
            null
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider()
}

private enum class RecipeDialog { HEADER, ARCHIVE, MENU_PRICE, BATCH, INSTANT }

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
            item { CostCard(detail, onEditPrice = { dialog = RecipeDialog.MENU_PRICE }) }
            if (detail.kind != RecipeKind.PREP && detail.validForCalculator) {
                item {
                    Button(
                        onClick = { dialog = RecipeDialog.INSTANT },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("ماشین‌حساب لحظه‌ای قیمت و سود") }
                }
            }
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
        RecipeDialog.INSTANT -> InstantCalculatorDialog(
            cost = remember(state, id, today) { state.calculator().cost(id, today) },
            pricing = state.pricing,
            money = money,
            initialPriceRials = detail.pricing?.menuPriceRials,
            onDismiss = { dialog = null },
            onSavePrice = { amount ->
                actions.run({ it.setMenuPrice(id, today, amount) }) { dialog = null }
            },
        )
        RecipeDialog.HEADER -> RecipeHeaderDialog(
            title = "ویرایش دستور",
            initial = detail.header,
            onDismiss = { dialog = null },
            onSave = { input ->
                actions.run({
                    it.updateRecipe(
                        id,
                        name = Opt.Value(input.name),
                        kind = Opt.Value(input.kind),
                        category = Opt.Value(input.categoryOrNull),
                    )
                }) { dialog = null }
            },
            onArchive = { dialog = RecipeDialog.ARCHIVE },
        )
        RecipeDialog.ARCHIVE -> ConfirmDialog(
            title = "حذف «${detail.name}»؟",
            text = "این دستور از فهرست‌ها و منو برداشته می‌شود. سابقه‌اش در پشتیبان می‌ماند.",
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
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        listOfNotNull(detail.kindLabel, detail.category).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("بهای هر پرس", style = MaterialTheme.typography.labelLarge)
                    Text(if (detail.validForCalculator) detail.costPerPortion else "محاسبه ناقص", style = MaterialTheme.typography.headlineSmall)
                }
                if (detail.kind != RecipeKind.PREP) StatusChip(detail.status, detail.statusLabel)
            }
            detail.batchCost?.let { LabeledValue("کل دستور", it) }
            detail.outputLabel?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (detail.validForCalculator) detail.pricing?.let { pricing ->
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LabeledValue("قیمت پیشنهادی", pricing.suggested, Modifier.weight(1f), emphasized = true)
                    LabeledValue("قیمت فعلی منو", pricing.menuPrice ?: "ثبت نشده", Modifier.weight(1f), emphasized = true)
                }
                val notes = listOfNotNull(
                    pricing.method,
                    pricing.foodCostPercent?.let { "فودکاست با قیمت فعلی $it" },
                    pricing.raise?.let { "باید $it گران‌تر شود" },
                )
                Text(
                    notes.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onEditPrice) {
                    Text(if (pricing.menuPrice == null) "ثبت قیمت منو" else "تغییر قیمت منو")
                }
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
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(line.name + if (line.isSubRecipe) " · دستور" else "", style = MaterialTheme.typography.bodyLarge)
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
) {
    var input by remember { mutableStateOf(initial) }
    var errors by remember { mutableStateOf(emptyMap<Field, String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextInput(input.name, { input = input.copy(name = it) }, "نام", error = errors[Field.NAME])
                ChoiceChips(RecipeKind.entries, input.kind, { Labels.kind(it) }) { input = input.copy(kind = it) }
                Text(
                    Labels.kindHint(input.kind),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextInput(input.category, { input = input.copy(category = it) }, "بخش منو (اختیاری)")
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
                TextButton(onClick = { text = money.editable(pricing.suggestedRials) }) {
                    Text("همان قیمت پیشنهادی: ${pricing.suggested}")
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
                item(key = "recipes") { SectionTitle("دستورها و نیمه‌آماده‌ها", Modifier.padding(top = 12.dp)) }
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
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
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
