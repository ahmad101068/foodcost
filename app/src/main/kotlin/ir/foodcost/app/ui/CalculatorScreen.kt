package ir.foodcost.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ir.foodcost.app.AppContainer
import ir.foodcost.app.ui.theme.statusColors
import ir.foodcost.core.model.RecipeId
import ir.foodcost.core.pricing.PriceStatus
import ir.foodcost.core.units.Uom
import ir.foodcost.data.ledger.LedgerState
import ir.foodcost.presentation.Checked
import ir.foodcost.presentation.Field
import ir.foodcost.presentation.Labels
import ir.foodcost.presentation.MoneyFormat
import ir.foodcost.presentation.Persian
import ir.foodcost.presentation.QuickCalculator
import ir.foodcost.presentation.QuickChange
import ir.foodcost.presentation.QuickChannel
import ir.foodcost.presentation.QuickExtra
import ir.foodcost.presentation.QuickInput
import ir.foodcost.presentation.QuickPlan
import ir.foodcost.presentation.QuickResult
import ir.foodcost.presentation.QuickReview
import ir.foodcost.presentation.QuickRow
import ir.foodcost.presentation.QuickRowResult
import ir.foodcost.presentation.QuickSuggestion
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
    // A save that changes a dish on the menu or recorded prices waits for a look at what it changes.
    var reviewing by remember { mutableStateOf<Pair<QuickPlan, QuickReview>?>(null) }
    val context = LocalContext.current
    val firstName = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val quantities = remember { HashMap<Int, FocusRequester>() }
    fun quantity(index: Int): FocusRequester = quantities.getOrPut(index) { FocusRequester() }
    val extraAmounts = remember { HashMap<Int, FocusRequester>() }
    fun extraAmount(index: Int): FocusRequester = extraAmounts.getOrPut(index) { FocusRequester() }
    var focusExtra by remember { mutableStateOf<Int?>(null) }

    fun commit(plan: QuickPlan, current: QuickInput) {
        actions.run({ ledger -> QuickCalculator.save(ledger, plan, today) }) { id ->
            val saved = container.state.value?.let { QuickCalculator.open(it, id, today, money) }
            update(saved ?: current.copy(recipeId = id))
            actions.say("«${plan.name}» در غذاها ذخیره شد.")
        }
    }

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
            is Checked.Valid -> {
                val review = QuickCalculator.review(current, checked.value, state, money, today)
                if (review.isEmpty) commit(checked.value, current) else reviewing = checked.value to review
            }
        }
    }

    val topActions: @Composable RowScope.() -> Unit = {
        if (result.hasLines) {
            IconButton(onClick = { share(context, input.dishName, QuickCalculator.shareText(input, result, money)) }) {
                Icon(Icons.Filled.Share, contentDescription = "فرستادن نتیجه")
            }
        }
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
            // While the keyboard is up, the display keeps its figures and drops the line under them.
            Display(result, money, compact = WindowInsets.isImeVisible, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
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
                    val sections = QuickCalculator.categories(state, input)
                    if (sections.isNotEmpty()) {
                        SectionChips(sections, input.category, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                            update(input.copy(category = it))
                        }
                    }
                }
                Text(
                    QuickCalculator.portionsLabel(input),
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
                ExtrasCard(
                    input = input,
                    result = result,
                    money = money,
                    amountFocus = ::extraAmount,
                    onChange = { i, extra -> update(input.withExtra(i, extra)) },
                    onRemove = { i -> update(input.withoutExtra(i)) },
                    onAdd = { preset ->
                        focusExtra = input.extras.size
                        update(input.plusExtra(preset))
                    },
                )
                // A new other cost takes the keyboard on its amount once it is on the screen.
                LaunchedEffect(focusExtra, input.extras.size) {
                    val i = focusExtra
                    if (i != null && i < input.extras.size) {
                        runCatching { extraAmount(i).requestFocus() }
                        focusExtra = null
                    }
                }
                if (result.netSales != null) PriceDetails(result, money)
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

    reviewing?.let { (plan, review) ->
        ReviewDialog(
            review = review,
            money = money,
            onDismiss = { reviewing = null },
            onSave = {
                reviewing = null
                commit(plan, input)
            },
        )
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

/**
 * The calculator's display, always on top so the result stays in sight while typing below. Without
 * a menu price it shows the cost and the suggested price; with one, the four figures that decide a
 * price: the cost, the net sale, the food cost and what is left.
 */
@Composable
private fun Display(result: QuickResult, money: MoneyFormat, compact: Boolean, modifier: Modifier = Modifier) {
    DisplayPanel(modifier) {
        val netSales = result.netSales
        val remaining = result.remaining
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Figure("هزینهٔ هر پرس", result.totalCost, money, Modifier.weight(1.1f).testTag("cost"), isResult = true)
            if (netSales != null) {
                Figure("فروش خالص", netSales, money, Modifier.weight(1f).testTag("net-sales"))
            } else {
                Figure("قیمت پیشنهادی", result.suggested ?: "—", money, Modifier.weight(1f).testTag("suggested"))
            }
        }
        if (netSales != null && remaining != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Figure("فودکاست", result.foodCostPercent ?: "—", money, Modifier.weight(1.1f).testTag("food-cost"))
                // Not profit: only the costs entered are taken off. The label and sign say which way it went.
                Figure(
                    if (result.remainingNegative) "کسری هر پرس" else "مانده هر پرس",
                    remaining,
                    money,
                    Modifier.weight(1f).testTag("remaining"),
                )
            }
        }
        if (!compact) {
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

/** Under the figures: the suggested price beside the menu price, or how it is worked out, or how to start. */
private fun note(result: QuickResult): String {
    if (!result.hasLines) return "قیمت هر کیلو و مقدار هر ماده را بنویسید."
    if (result.netSales == null) return "${result.method}، ${result.miscNote}"
    return listOfNotNull(
        result.suggested?.let { "قیمت پیشنهادی $it" },
        result.raise?.let { "باید $it گران‌تر شود" },
    ).joinToString("، ")
}

/**
 * Costs of a portion that are not food: a box, a cup, a share of a wage. Each is counted once; the
 * box only for portions that leave the restaurant.
 */
@Composable
private fun ExtrasCard(
    input: QuickInput,
    result: QuickResult,
    money: MoneyFormat,
    amountFocus: (Int) -> FocusRequester,
    onChange: (Int, QuickExtra) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: (QuickExtra) -> Unit,
) {
    Card(colors = whiteCardColors(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("هزینه‌های دیگر هر پرس", style = MaterialTheme.typography.titleSmall)
            input.extras.forEachIndexed { i, extra ->
                val problem = result.extras.getOrNull(i)?.problem
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextInput(
                        value = extra.name,
                        onValueChange = { onChange(i, extra.copy(name = it)) },
                        label = "عنوان",
                        modifier = Modifier.weight(1.2f).testTag("extra-$i-name"),
                        imeAction = ImeAction.Next,
                    )
                    TextInput(
                        value = extra.amount,
                        onValueChange = { onChange(i, extra.copy(amount = it)) },
                        label = "هر پرس",
                        modifier = Modifier.weight(1f).focusRequester(amountFocus(i)).testTag("extra-$i-amount"),
                        number = true,
                        suffix = money.unit,
                        imeAction = ImeAction.Done,
                    )
                    IconButton(onClick = { onRemove(i) }, modifier = Modifier.focusProperties { canFocus = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "حذف هزینه")
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    // Out of the keyboard's Next walk; focusProperties must come before the clickable it governs.
                    modifier = Modifier
                        .focusProperties { canFocus = false }
                        .clickable { onChange(i, extra.copy(takeawayOnly = !extra.takeawayOnly)) },
                ) {
                    Checkbox(checked = extra.takeawayOnly, onCheckedChange = null)
                    Text("فقط برای بیرون‌بر و آنلاین", style = MaterialTheme.typography.bodyMedium)
                }
                if (problem != null && extra.amount.isNotBlank()) {
                    Text(problem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickCalculator.EXTRA_PRESETS.forEach { preset ->
                    AssistChip(
                        onClick = { onAdd(preset) },
                        label = { Text(preset.name) },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        modifier = Modifier.focusProperties { canFocus = false },
                    )
                }
            }
            result.overheadShare?.let {
                Text("سهم سربار ماهانه از تنظیمات: $it", style = MaterialTheme.typography.bodyMedium)
            }
            if (input.extras.isNotEmpty() || result.overheadShare != null) {
                Text(
                    if (result.pricesFoodOnly) {
                        "قیمت پیشنهادی از بهای مواد حساب می‌شود؛ این هزینه‌ها از «مانده» کم می‌شوند تا دوبار حساب نشوند."
                    } else {
                        "این هزینه‌ها در قیمت پیشنهادی هم حساب می‌شوند."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** At the menu price typed: margin and markup, pricing backwards, and the other channels beside the restaurant. */
@Composable
private fun PriceDetails(result: QuickResult, money: MoneyFormat) {
    Card(colors = whiteCardColors(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("جزئیات قیمت منو", style = MaterialTheme.typography.titleSmall)
            val margin = result.margin
            val markup = result.markup
            if (margin != null && markup != null) {
                Text("مارجین $margin و مارک‌آپ $markup، روی همهٔ هزینه‌های ثبت‌شده", style = MaterialTheme.typography.bodyMedium)
            }
            val maxCost = result.maxCost
            val maxCostLabel = result.maxCostLabel
            if (maxCost != null && maxCostLabel != null) {
                Text("$maxCostLabel: $maxCost", style = MaterialTheme.typography.bodyMedium)
            }
            if (result.channels.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ChannelTable(result.channels, money)
            }
        }
    }
}

/** The channels side by side, amounts without their unit so the columns fit. */
@Composable
private fun ChannelTable(channels: List<QuickChannel>, money: MoneyFormat) {
    fun bare(text: String?) = text?.removeSuffix(" ${money.unit}") ?: "—"
    @Composable
    fun line(label: String, values: List<String>, tag: String? = null, strong: Boolean = false) {
        Row(Modifier.fillMaxWidth().then(if (tag != null) Modifier.testTag(tag) else Modifier)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.3f))
            values.forEach {
                Text(
                    it,
                    style = if (strong) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        line("به ${money.unit}", channels.map { it.label }, strong = true)
        line("قیمت", channels.map { bare(it.price) })
        line("هزینه", channels.map { bare(it.cost) })
        line("مانده", channels.map { bare(it.remaining) }, tag = "channel-remaining", strong = true)
        line("فودکاست", channels.map { it.foodCostPercent ?: "—" })
        line("برای همان مانده", channels.map { bare(it.priceForSameRemaining) })
    }
    Text(
        "«برای همان مانده» یعنی قیمتی که در آن کانال همان مانده‌ای را می‌دهد که فروش حضوری.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
            // A slip of a zero or the same ingredient twice: counted, but worth a second look.
            result.warning?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("row-$index-warning"),
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

/** The dish's menu section; one with a target of its own prices the dish by it. Tapped again, it is cleared. */
@Composable
private fun SectionChips(sections: List<String>, selected: String?, modifier: Modifier = Modifier, onSelect: (String?) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("بخش منو", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sections.forEach { name ->
                FilterChip(
                    selected = name == selected,
                    onClick = { onSelect(if (name == selected) null else name) },
                    label = { Text(name) },
                    modifier = Modifier.focusProperties { canFocus = false },
                )
            }
        }
    }
}

/**
 * Before a save that changes what is already saved: the dish's figures before and after, the
 * ingredient prices it records for every dish, and anything that looks like a slip.
 */
@Composable
private fun ReviewDialog(review: QuickReview, money: MoneyFormat, onDismiss: () -> Unit, onSave: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ذخیرهٔ «${review.name}»") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (review.changes.isNotEmpty()) ChangeTable(review.changes, money)
                if (review.prices.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("قیمت‌های تازه", style = MaterialTheme.typography.titleSmall)
                        review.prices.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                if (review.warnings.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("پیش از ذخیره ببینید", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                        review.warnings.forEach {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave, modifier = Modifier.testTag("review-save")) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("برگشت") } },
    )
}

/** The figures that change, before and after, with how far the amounts move. */
@Composable
private fun ChangeTable(changes: List<QuickChange>, money: MoneyFormat) {
    val better = statusColors(PriceStatus.OK).content
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text("به ${money.unit}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.25f))
            Text("قبل", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("بعد", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        }
        changes.forEach { change ->
            Row(Modifier.fillMaxWidth().testTag("change-${change.label}")) {
                Text(change.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.25f))
                Text(change.before, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
                Column(Modifier.weight(1f)) {
                    Text(change.after, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    change.change?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = if (change.worse) MaterialTheme.colorScheme.error else better)
                    }
                }
            }
        }
    }
}

/** Hands the calculation as text to whichever app the user picks: a messenger, notes, email. */
private fun share(context: Context, title: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        if (title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, title.trim())
    }
    context.startActivity(Intent.createChooser(send, "فرستادن نتیجه"))
}
