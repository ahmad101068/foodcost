package ir.foodcost.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ir.foodcost.core.model.IngredientId
import ir.foodcost.core.pricing.PriceStatus
import ir.foodcost.core.units.Dimension
import ir.foodcost.data.ledger.LedgerState
import ir.foodcost.data.ops.Opt
import ir.foodcost.presentation.Checked
import ir.foodcost.presentation.Field
import ir.foodcost.presentation.IngredientDetail
import ir.foodcost.presentation.IngredientInput
import ir.foodcost.presentation.IngredientRow
import ir.foodcost.presentation.IngredientScreens
import ir.foodcost.presentation.JalaliDate
import ir.foodcost.presentation.Labels
import ir.foodcost.presentation.MoneyFormat
import ir.foodcost.presentation.PriceInput
import ir.foodcost.presentation.PriceRow
import ir.foodcost.presentation.QuickCalculator
import ir.foodcost.presentation.UnitOptions
import java.time.LocalDate

/** The ingredients tab: everything bought, with today's price per kilo. */
@Composable
fun IngredientsScreen(state: LedgerState, money: MoneyFormat, today: LocalDate, nav: Navigator, actions: AppActions) {
    var query by rememberSaveable { mutableStateOf("") }
    var repricing by remember { mutableStateOf<IngredientRow?>(null) }
    val rows = remember(state, money, today, query) { IngredientScreens.rows(state, today, money, query) }
    TabScaffold(
        title = "مواد اولیه",
        nav = nav,
        actions = actions,
        floatingActionButton = {
            AddButton("ماده تازه", onClick = { nav.open(Screen.EditIngredient(null)) })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        ) {
            item { SearchField(query, { query = it }, "جستجوی ماده") }
            if (rows.isEmpty()) {
                item {
                    if (query.isBlank()) {
                        EmptyState(
                            title = "هنوز ماده‌ای ثبت نشده",
                            body = "هر چیزی که می‌خرید و در غذا به کار می‌رود را اینجا ثبت کنید: برنج، گوشت، روغن، زعفران و... بعد قیمت خرید هر کدام را وارد کنید.",
                        )
                    } else {
                        EmptyState(title = "ماده‌ای با این نام پیدا نشد", body = "نام دیگری را جستجو کنید یا ماده تازه بسازید.")
                    }
                }
            }
            items(rows, key = { it.id.value }) { row ->
                IngredientListItem(
                    row = row,
                    onClick = { nav.open(Screen.Ingredient(row.id.value)) },
                    onReprice = { repricing = row },
                )
            }
        }
    }
    repricing?.let { row ->
        QuickPriceDialog(
            row = row,
            money = money,
            warningFor = { text -> state.catalog.ingredient(row.id)?.let { QuickCalculator.quickPriceWarning(state, it, text, money, today) } },
            onDismiss = { repricing = null },
            onSave = { text ->
                val ingredient = state.catalog.ingredient(row.id)
                when (val checked = ingredient?.let { QuickCalculator.quickPrice(it, text, money, today) }) {
                    null -> repricing = null
                    is Checked.Invalid -> actions.say(checked.errors.values.first())
                    is Checked.Valid -> actions.run({ it.recordPrice(checked.value) }) {
                        repricing = null
                        actions.say("قیمت «${row.name}» به‌روز شد.")
                    }
                }
            },
        )
    }
}

/**
 * One field, ready for typing: the new price per kilo (liter, piece), from today. A price about ten
 * times the last one (a zero too many, or rials for toman) is said at once and saved only on a
 * second press, since every dish made with the ingredient would take it.
 */
@Composable
private fun QuickPriceDialog(
    row: IngredientRow,
    money: MoneyFormat,
    warningFor: (String) -> String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember(row.id) { mutableStateOf("") }
    var confirmed by remember(row.id) { mutableStateOf<String?>(null) }
    val warning = warningFor(text)
    val asks = warning != null && confirmed == text
    fun submit() {
        if (warning != null && confirmed != text) confirmed = text else onSave(text)
    }
    val field = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(row.name) },
        text = {
            TextInput(
                value = text,
                onValueChange = { text = it },
                label = "${row.priceLabel} از امروز",
                error = warning,
                supporting = money.parse(text)?.let { money.full(it) } ?: row.unitPrice?.let { "قیمت فعلی: $it" },
                number = true,
                suffix = money.unit,
                modifier = Modifier.focusRequester(field).testTag("quick-price"),
                imeAction = ImeAction.Done,
                onImeAction = { submit() },
            )
            LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
        },
        confirmButton = { TextButton(onClick = { submit() }) { Text(if (asks) "ذخیره با همین قیمت" else "ذخیره") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun IngredientListItem(row: IngredientRow, onClick: () -> Unit, onReprice: () -> Unit) {
    val yieldLabel = row.yieldLabel
    val price = row.unitPrice
    ListItem(
        headlineContent = { Text(row.name) },
        supportingContent = {
            Text(
                price ?: row.priceProblem.orEmpty(),
                color = if (price == null) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (yieldLabel != null) Text(yieldLabel, style = MaterialTheme.typography.labelMedium)
                TextButton(
                    onClick = onReprice,
                    modifier = Modifier.semantics { contentDescription = "قیمت تازهٔ ${row.name}" },
                ) { Text("قیمت تازه") }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider()
}

/** One ingredient: its price today, the price history and where it is used. */
@Composable
fun IngredientScreen(id: IngredientId, state: LedgerState, money: MoneyFormat, today: LocalDate, nav: Navigator, actions: AppActions) {
    val detail = remember(state, money, today, id) { IngredientScreens.detail(state, id, today, money) }
    if (detail == null) {
        LaunchedEffect(id) { nav.back() }
        return
    }
    var voiding by remember { mutableStateOf<PriceRow?>(null) }
    DetailScaffold(
        title = detail.name,
        nav = nav,
        actions = actions,
        topActions = {
            IconButton(onClick = { nav.open(Screen.EditIngredient(id.value)) }) {
                Icon(Icons.Filled.Edit, contentDescription = "ویرایش ماده")
            }
        },
        floatingActionButton = {
            AddButton("قیمت تازه", onClick = { nav.open(Screen.NewPrice(id.value)) })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { IngredientCard(detail) }
            item { SectionTitle("سابقهٔ قیمت خرید", Modifier.padding(top = 8.dp)) }
            if (detail.prices.isEmpty()) {
                item {
                    Text(
                        "هنوز قیمتی ثبت نشده. با دکمهٔ «قیمت تازه» اولین قیمت خرید را وارد کنید.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(detail.prices, key = { it.priceId }) { price ->
                PriceItem(price, onVoid = { voiding = price })
            }
            if (detail.usedIn.isNotEmpty()) {
                item { SectionTitle("در این دستورها به کار رفته", Modifier.padding(top = 8.dp)) }
                item { Text(detail.usedIn.joinToString("، "), style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
    voiding?.let { price ->
        VoidPriceDialog(
            price = price,
            onDismiss = { voiding = null },
            onVoid = { reason -> actions.run({ it.voidPrice(price.priceId, reason) }) { voiding = null } },
        )
    }
}

@Composable
private fun IngredientCard(detail: IngredientDetail) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                listOfNotNull(detail.dimensionLabel, detail.category).joinToString("، "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                detail.unitPrice ?: detail.priceProblem.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                color = if (detail.unitPrice == null) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
            detail.usableUnitPrice?.let { Text("بعد از پاک کردن: $it", style = MaterialTheme.typography.bodyMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LabeledValue("بازدهی", detail.yieldLabel, Modifier.weight(1f))
                detail.conversionsLabel?.let { LabeledValue("تبدیل‌ها", it, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PriceItem(price: PriceRow, onVoid: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), colors = whiteCardColors()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(price.date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (price.inForce) {
                        Spacer(Modifier.width(8.dp))
                        StatusChip(PriceStatus.OK, "قیمت امروز")
                    }
                    if (price.voided) {
                        Spacer(Modifier.width(8.dp))
                        StatusChip(PriceStatus.UNDER, "باطل شده")
                    }
                }
                Text(
                    "${price.packageLabel} به ${price.total}",
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (price.voided) TextDecoration.LineThrough else null,
                )
                price.unitPrice?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                price.details?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!price.voided) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "گزینه‌ها")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("باطل کردن این قیمت") },
                            onClick = {
                                menuOpen = false
                                onVoid()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoidPriceDialog(price: PriceRow, onDismiss: () -> Unit, onVoid: (String?) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("باطل کردن قیمت") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("قیمت ${price.date} (${price.packageLabel} به ${price.total}) دیگر در حساب‌ها نمی‌آید، ولی در سابقه می‌ماند.")
                TextInput(reason, { reason = it }, "دلیل (اختیاری)")
            }
        },
        confirmButton = { TextButton(onClick = { onVoid(reason.trim().ifEmpty { null }) }) { Text("باطل کن") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

/** The ingredient form, for a new ingredient when [id] is null. */
@Composable
fun IngredientEditScreen(id: IngredientId?, state: LedgerState, nav: Navigator, actions: AppActions) {
    val record = id?.let { state.ingredients[it] }
    val editing = record != null
    var input by remember(id) { mutableStateOf(record?.let { IngredientInput.of(it) } ?: IngredientInput()) }
    var errors by remember { mutableStateOf(emptyMap<Field, String>()) }
    var showConversions by rememberSaveable {
        mutableStateOf(input.gramsPerPiece.isNotEmpty() || input.gramsPerMilliliter.isNotEmpty())
    }
    var confirmArchive by remember { mutableStateOf(false) }
    DetailScaffold(
        title = if (editing) "ویرایش ماده" else "ماده تازه",
        nav = nav,
        actions = actions,
        topActions = {
            if (editing) {
                IconButton(onClick = { confirmArchive = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "حذف ماده")
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextInput(input.name, { input = input.copy(name = it) }, "نام ماده", error = errors[Field.NAME])
            Text("با چه واحدی خریده و مصرف می‌شود؟", style = MaterialTheme.typography.labelLarge)
            Segmented(
                listOf(Dimension.MASS, Dimension.VOLUME, Dimension.COUNT),
                input.dimension,
                { Labels.dimension(it) },
                enabled = !editing,
            ) { input = input.copy(dimension = it) }
            Text(
                dimensionHint(input.dimension, editing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextInput(
                value = input.yieldPercent,
                onValueChange = { input = input.copy(yieldPercent = it) },
                label = "بازدهی",
                error = errors[Field.YIELD],
                supporting = "چند درصد از آنچه می‌خرید بعد از پاک کردن در غذا می‌رود؟ مثلاً مرغ کامل حدود ۷۰.",
                number = true,
                suffix = "٪",
            )
            TextInput(
                value = input.category,
                onValueChange = { input = input.copy(category = it) },
                label = "دسته (اختیاری)",
                supporting = "مثلاً پروتئین، لبنیات، خشکبار",
            )
            TextButton(onClick = { showConversions = !showConversions }) {
                Text(if (showConversions) "بستن تبدیل‌ها" else "تبدیل‌ها (اختیاری)")
            }
            if (showConversions) {
                TextInput(
                    value = input.gramsPerPiece,
                    onValueChange = { input = input.copy(gramsPerPiece = it) },
                    label = "وزن هر عدد",
                    error = errors[Field.PIECE_WEIGHT],
                    supporting = "برای چیزهایی مثل تخم‌مرغ که هم عددی و هم وزنی به کار می‌روند.",
                    number = true,
                    suffix = "گرم",
                )
                TextInput(
                    value = input.gramsPerMilliliter,
                    onValueChange = { input = input.copy(gramsPerMilliliter = it) },
                    label = "وزن هر میلی‌لیتر",
                    error = errors[Field.DENSITY],
                    supporting = "برای چیزهایی مثل روغن که وزنی خریده و حجمی مصرف می‌شوند؛ روغن حدود ۰٫۹۲.",
                    number = true,
                    suffix = "گرم",
                )
            }
            Button(
                onClick = {
                    when (val checked = input.check()) {
                        is Checked.Invalid -> errors = checked.errors
                        is Checked.Valid -> {
                            val draft = checked.value
                            if (IngredientScreens.nameTaken(state, draft.name, except = id)) {
                                errors = mapOf(Field.NAME to "«${draft.name}» از قبل در فهرست مواد هست.")
                            } else if (id == null) {
                                actions.run({
                                    it.createIngredient(draft.name, draft.dimension, draft.category, draft.yields, draft.conversions)
                                }) { newId ->
                                    nav.replace(Screen.Ingredient(newId.value))
                                    nav.open(Screen.NewPrice(newId.value))
                                }
                            } else {
                                actions.run({
                                    it.updateIngredient(
                                        id,
                                        name = Opt.Value(draft.name),
                                        category = Opt.Value(draft.category),
                                        yields = Opt.Value(draft.yields),
                                        conversions = Opt.Value(draft.conversions),
                                    )
                                }) { nav.back() }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("ذخیره") }
        }
    }
    if (confirmArchive && id != null) {
        ConfirmDialog(
            title = "حذف «${input.name}»؟",
            text = "این ماده از فهرست‌ها برداشته می‌شود. دستورهایی که آن را دارند همچنان با آخرین قیمتش حساب می‌شوند.",
            confirm = "حذف",
            onConfirm = {
                confirmArchive = false
                actions.run({ it.updateIngredient(id, archived = Opt.Value(true)) }) { nav.tab(Tab.INGREDIENTS) }
            },
            onDismiss = { confirmArchive = false },
        )
    }
}

private fun dimensionHint(dimension: Dimension, editing: Boolean): String {
    val hint = when (dimension) {
        Dimension.MASS -> "قیمت هر کیلو حساب می‌شود؛ مثل برنج، گوشت و سبزی."
        Dimension.VOLUME -> "قیمت هر لیتر حساب می‌شود؛ مثل شیر و آب‌لیمو."
        Dimension.COUNT -> "قیمت هر عدد حساب می‌شود؛ مثل تخم‌مرغ و نان."
        Dimension.PORTION -> ""
    }
    return if (editing) "$hint نوع ماده بعد از ثبت عوض نمی‌شود." else hint
}

/** Records a purchase: when, how much, and for how much. */
@Composable
fun PriceEntryScreen(id: IngredientId, state: LedgerState, money: MoneyFormat, today: LocalDate, nav: Navigator, actions: AppActions) {
    val ingredient = remember(state, id) { state.catalog.ingredient(id) }
    if (ingredient == null) {
        LaunchedEffect(id) { nav.back() }
        return
    }
    val units = remember(ingredient) { UnitOptions.forIngredient(ingredient.baseDimension, ingredient.conversions) }
    var input by remember(id) { mutableStateOf(PriceInput.new(today, ingredient.baseDimension)) }
    var errors by remember { mutableStateOf(emptyMap<Field, String>()) }
    var showMore by rememberSaveable { mutableStateOf(false) }
    val preview = input.unitPrice(ingredient, money)
    // A price about ten times the last one is recorded only on a second press, once it has been seen.
    val warning = QuickCalculator.purchaseWarning(state, ingredient, input, money, today)
    var confirmed by remember(id) { mutableStateOf<PriceInput?>(null) }
    val asks = warning != null && confirmed == input
    DetailScaffold(title = "قیمت خرید ${ingredient.name}", nav = nav, actions = actions) { padding ->
        Column(
            Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextInput(
                value = input.date,
                onValueChange = { input = input.copy(date = it) },
                label = "تاریخ خرید",
                error = errors[Field.DATE],
                supporting = JalaliDate.parse(input.date)?.formatLong(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { input = input.copy(date = JalaliDate.of(today).format()) }, label = { Text("امروز") })
                AssistChip(onClick = { input = input.copy(date = JalaliDate.of(today.minusDays(1)).format()) }, label = { Text("دیروز") })
            }
            TextInput(
                value = input.packageQty,
                onValueChange = { input = input.copy(packageQty = it) },
                label = "مقدار خرید",
                error = errors[Field.QUANTITY],
                number = true,
                suffix = Labels.uom(input.packageUom),
            )
            ChoiceChips(units, input.packageUom, { Labels.uom(it) }) { input = input.copy(packageUom = it) }
            TextInput(
                value = input.amount,
                onValueChange = { input = input.copy(amount = it) },
                label = "مبلغ کل",
                error = errors[Field.AMOUNT],
                supporting = money.parse(input.amount)?.let { money.full(it) },
                number = true,
                suffix = money.unit,
            )
            preview?.let { text ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Text(text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
            warning?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = { showMore = !showMore }) {
                Text(if (showMore) "بستن جزئیات" else "تخفیف، مالیات، حمل و فروشنده")
            }
            if (showMore) {
                TextInput(
                    value = input.discount,
                    onValueChange = { input = input.copy(discount = it) },
                    label = "تخفیف",
                    error = errors[Field.DISCOUNT],
                    number = true,
                    suffix = money.unit,
                )
                TextInput(
                    value = input.tax,
                    onValueChange = { input = input.copy(tax = it) },
                    label = "مالیات",
                    error = errors[Field.TAX],
                    number = true,
                    suffix = money.unit,
                )
                SwitchRow("مالیات را پس می‌گیرم و جزو هزینه نیست", input.taxRecoverable) {
                    input = input.copy(taxRecoverable = it)
                }
                TextInput(
                    value = input.delivery,
                    onValueChange = { input = input.copy(delivery = it) },
                    label = "هزینهٔ حمل",
                    error = errors[Field.DELIVERY],
                    number = true,
                    suffix = money.unit,
                )
                TextInput(
                    value = input.supplier,
                    onValueChange = { input = input.copy(supplier = it) },
                    label = "فروشنده (اختیاری)",
                )
            }
            Button(
                onClick = {
                    when (val checked = input.check(id, money)) {
                        is Checked.Invalid -> errors = checked.errors
                        is Checked.Valid -> if (warning != null && confirmed != input) {
                            errors = emptyMap()
                            confirmed = input
                        } else {
                            actions.run({ it.recordPrice(checked.value) }) { nav.back() }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (asks) "ثبت با همین قیمت" else "ثبت قیمت") }
        }
    }
}
