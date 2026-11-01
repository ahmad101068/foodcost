package ir.foodcost.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ir.foodcost.app.AppContainer
import ir.foodcost.core.model.RecipeId
import ir.foodcost.core.units.Uom
import ir.foodcost.data.ledger.LedgerState
import ir.foodcost.presentation.Checked
import ir.foodcost.presentation.Field
import ir.foodcost.presentation.Labels
import ir.foodcost.presentation.MoneyFormat
import ir.foodcost.presentation.Numbers
import ir.foodcost.presentation.Persian
import ir.foodcost.presentation.QuickCalculator
import ir.foodcost.presentation.QuickInput
import ir.foodcost.presentation.QuickResult
import ir.foodcost.presentation.QuickRow
import ir.foodcost.presentation.QuickRowResult
import ir.foodcost.presentation.QuickSuggestion
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The calculator: type each ingredient's price per kilo and how many grams go in, and the cost, the
 * suggested price and the food cost update with every key. With [recipeId] it edits a saved dish;
 * without it, it is the first tab, whose calculation survives switching tabs.
 *
 * Made for speed: an empty calculator opens with the keyboard on the first field, the keyboard's
 * Next key walks name, price and quantity row after row, and picking a saved ingredient fills its
 * price and jumps to the quantity.
 */
@Composable
fun CalculatorScreen(
    container: AppContainer,
    recipeId: RecipeId?,
    state: LedgerState,
    money: MoneyFormat,
    today: LocalDate,
    nav: Navigator,
    actions: AppActions,
) {
    val draft by container.quickDraft.collectAsState()
    var dish by remember(recipeId) {
        mutableStateOf(recipeId?.let { QuickCalculator.open(state, it, today, money) } ?: QuickInput())
    }
    val input = if (recipeId == null) draft else dish
    val update: (QuickInput) -> Unit = { if (recipeId == null) container.quickDraft.value = it else dish = it }
    val result = remember(input, state, money, today) { QuickCalculator.compute(input, state, money, today) }
    var askName by remember { mutableStateOf(false) }
    val firstName = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val quantities = remember { HashMap<Int, FocusRequester>() }
    fun quantity(index: Int): FocusRequester = quantities.getOrPut(index) { FocusRequester() }

    fun save(current: QuickInput) {
        when (val checked = QuickCalculator.check(current, state, money, today)) {
            is Checked.Invalid -> {
                val other = checked.errors.filterKeys { it != Field.NAME }
                when {
                    other.isNotEmpty() -> actions.say(other.values.first())
                    current.dishName.isBlank() -> askName = true
                    else -> checked.errors[Field.NAME]?.let { actions.say(it) }
                }
            }
            is Checked.Valid -> actions.run({ ledger -> QuickCalculator.save(ledger, checked.value, today) }) { id ->
                val saved = container.state.value?.let { QuickCalculator.open(it, id, today, money) }
                update(saved ?: current.copy(recipeId = id))
                actions.say("«${checked.value.name}» در غذاها ذخیره شد.")
            }
        }
    }

    val topActions: @Composable RowScope.() -> Unit = {
        if (recipeId == null && input != QuickInput()) {
            IconButton(
                onClick = {
                    update(QuickInput())
                    runCatching { firstName.requestFocus() }
                },
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "محاسبهٔ تازه")
            }
        }
        TextButton(
            onClick = {
                // The keyboard goes away, so the word that the dish was saved is not hidden behind it.
                focusManager.clearFocus()
                save(input)
            },
        ) { Text("ذخیره") }
    }
    val body: @Composable (PaddingValues) -> Unit = { padding ->
        // The keyboard shortens the list instead of covering it, so the field being typed in stays in sight.
        Column(Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize()) {
            Display(result, money, Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Card(colors = whiteCardColors(), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextInput(
                            value = input.dishName,
                            onValueChange = { update(input.copy(dishName = it)) },
                            label = "نام غذا",
                            modifier = Modifier.weight(1f).testTag("dish-name"),
                            imeAction = ImeAction.Next,
                        )
                        TextInput(
                            value = input.menuPrice,
                            onValueChange = { update(input.copy(menuPrice = it)) },
                            label = "قیمت منو",
                            modifier = Modifier.weight(1f).testTag("menu-price"),
                            number = true,
                            suffix = money.unit,
                            imeAction = ImeAction.Done,
                        )
                    }
                }
                Text(
                    portionsTitle(input),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
                input.rows.forEachIndexed { i, row ->
                    RowCard(
                        index = i,
                        row = row,
                        result = result.rows[i],
                        suggestions = QuickCalculator.suggestions(state, row, today, money),
                        money = money,
                        nameFocus = if (i == 0) firstName else null,
                        quantityFocus = quantity(i),
                        onChange = { update(input.withRow(i, it)) },
                        onRemove = { update(input.withoutRow(i)) },
                        onPick = {
                            update(input.withRow(i, QuickCalculator.pick(state, row, it.id, today, money)))
                            runCatching { quantity(i).requestFocus() }
                        },
                    )
                }
                input.recipeId?.let { id ->
                    TextButton(onClick = { nav.open(Screen.Recipe(id.value)) }) {
                        Text("تعداد پرس، درصد جزئی و جزئیات بیشتر")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        // Here, next to the fields, so the first one is in place when it is asked to take the keyboard.
        LaunchedEffect(recipeId) {
            if (recipeId == null && input == QuickInput()) runCatching { firstName.requestFocus() }
        }
    }
    val title = if (recipeId == null) "ماشین‌حساب" else input.dishName.ifBlank { "غذا" }
    if (recipeId == null) {
        TabScaffold(title = title, nav = nav, actions = actions, topActions = topActions, content = body)
    } else {
        DetailScaffold(title = title, nav = nav, actions = actions, topActions = topActions, content = body)
    }

    if (askName) {
        NameDialog(
            onDismiss = { askName = false },
            onSave = { name ->
                askName = false
                val named = input.copy(dishName = name)
                update(named)
                save(named)
            },
        )
    }
}

/** The calculator's display: always on top, so the result stays in sight while typing below. */
@Composable
private fun Display(result: QuickResult, money: MoneyFormat, modifier: Modifier = Modifier) {
    DisplayPanel(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Figure("بهای هر پرس", result.costPerPortion, money, Modifier.weight(1.1f).testTag("cost"), isResult = true)
            Figure("قیمت پیشنهادی", result.suggested ?: "—", money, Modifier.weight(1f).testTag("suggested"))
        }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (result.hasLines) StatusChip(result.status, result.statusLabel)
            Text(
                note(result),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        if (result.incompleteRows > 0) {
            val wrong = result.rows.any { it.problem != null && !it.waiting }
            Text(
                "${Persian.digits(result.incompleteRows)} ردیف کامل نیست و در جمع نیامده است.",
                style = MaterialTheme.typography.bodySmall,
                color = if (wrong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A figure on the display, with its unit set smaller than the number. Read out as one item. */
@Composable
private fun Figure(label: String, text: String, money: MoneyFormat, modifier: Modifier, isResult: Boolean = false) {
    val number = text.removeSuffix(" ${money.unit}")
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row {
            Text(
                number,
                style = if (isResult) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                color = if (isResult) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            if (number != text) {
                Text(
                    money.unit,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp).alignByBaseline(),
                )
            }
        }
    }
}

/** Under the figures: how the menu price stands, or how the suggestion was made, or how to start. */
private fun note(result: QuickResult): String {
    if (!result.hasLines) return "قیمت هر کیلو و مقدار هر ماده را بنویسید."
    val withPrice = listOfNotNull(
        result.foodCostPercent?.let { "فودکاست $it" },
        result.profit?.let { "سود ناخالص هر پرس $it" },
        result.raise?.let { "باید $it گران‌تر شود" },
    )
    return if (withPrice.isEmpty()) "${result.method}، ${result.miscNote}" else withPrice.joinToString("، ")
}

/** The quantities are for one portion unless the saved dish says otherwise. */
private fun portionsTitle(input: QuickInput): String {
    val portions = Numbers.parse(input.portions)
    return if (portions == null || portions.compareTo(BigDecimal.ONE) == 0) "مواد یک پرس" else "مواد ${Numbers.format(portions, 2)} پرس"
}

/** The row's number, which the messages about unfinished rows refer to. */
@Composable
private fun RowNumber(number: Int, blank: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(28.dp)
            .background(if (blank) Color.Transparent else colors.primaryContainer, CircleShape)
            .border(1.dp, if (blank) colors.outlineVariant else Color.Transparent, CircleShape),
    ) {
        Text(
            Persian.digits(number),
            style = MaterialTheme.typography.labelLarge,
            color = if (blank) colors.outline else colors.onPrimaryContainer,
        )
    }
}

@Composable
private fun RowCard(
    index: Int,
    row: QuickRow,
    result: QuickRowResult,
    suggestions: List<QuickSuggestion>,
    money: MoneyFormat,
    nameFocus: FocusRequester?,
    quantityFocus: FocusRequester,
    onChange: (QuickRow) -> Unit,
    onRemove: () -> Unit,
    onPick: (QuickSuggestion) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = whiteCardColors()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RowNumber(index + 1, row.isBlank)
                TextInput(
                    value = row.name,
                    onValueChange = { onChange(row.copy(name = it, ingredientId = null)) },
                    label = if (row.isBlank) "مادهٔ تازه" else "ماده",
                    modifier = Modifier
                        .weight(1f)
                        .then(if (nameFocus != null) Modifier.focusRequester(nameFocus) else Modifier)
                        .testTag("row-$index-name"),
                    imeAction = ImeAction.Next,
                )
                if (!row.isBlank) {
                    IconButton(onClick = onRemove, modifier = Modifier.focusProperties { canFocus = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "حذف ردیف")
                    }
                }
            }
            if (suggestions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { s ->
                        AssistChip(
                            onClick = { onPick(s) },
                            label = { Text("${s.name}، ${s.detail}") },
                            modifier = Modifier.focusProperties { canFocus = false },
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(
                    value = row.price,
                    onValueChange = { onChange(row.copy(price = it)) },
                    label = result.priceLabel,
                    modifier = Modifier.weight(1.3f).testTag("row-$index-price"),
                    number = true,
                    suffix = money.unit,
                    imeAction = ImeAction.Next,
                )
                TextInput(
                    value = row.qty,
                    onValueChange = { onChange(row.copy(qty = it)) },
                    label = "مقدار",
                    modifier = Modifier.weight(0.8f).focusRequester(quantityFocus).testTag("row-$index-qty"),
                    number = true,
                    imeAction = ImeAction.Next,
                )
                UnitMenu(row.uom, result.units) { onChange(row.copy(uom = it)) }
            }
            val problem = result.problem
            val info = problem ?: listOfNotNull(result.cost?.let { "هزینه: $it" }, result.note).joinToString("، ").ifEmpty { null }
            if (info != null) {
                Text(
                    info,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (problem != null && !result.waiting) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun UnitMenu(selected: Uom, options: List<Uom>, onSelect: (Uom) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        // Left out of the keyboard's Next walk, which goes from the quantity straight to the next row.
        TextButton(onClick = { open = true }, modifier = Modifier.focusProperties { canFocus = false }) {
            Text(Labels.uom(selected))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { uom ->
                DropdownMenuItem(
                    text = { Text(Labels.uom(uom)) },
                    onClick = {
                        open = false
                        onSelect(uom)
                    },
                )
            }
        }
    }
}

@Composable
private fun NameDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val field = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("نام این غذا چیست؟") },
        text = {
            TextInput(
                value = name,
                onValueChange = { name = it },
                label = "نام غذا",
                modifier = Modifier.focusRequester(field).testTag("name-dialog"),
                imeAction = ImeAction.Done,
                onImeAction = { if (name.isNotBlank()) onSave(name.trim()) },
            )
            LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim()) }) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
