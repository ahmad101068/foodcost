package ir.foodcost.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.foodcost.app.AppContainer
import ir.foodcost.app.ui.theme.statusColors
import ir.foodcost.core.model.RecipeId
import ir.foodcost.core.pricing.PriceStatus
import ir.foodcost.core.pricing.PricingScope
import ir.foodcost.core.units.Uom
import ir.foodcost.data.ledger.LedgerState
import ir.foodcost.presentation.BackupTexts
import ir.foodcost.presentation.Checked
import ir.foodcost.presentation.Field
import ir.foodcost.presentation.Glossary
import ir.foodcost.presentation.Labels
import ir.foodcost.presentation.MoneyFormat
import ir.foodcost.presentation.Persian
import ir.foodcost.presentation.QuickCalculator
import ir.foodcost.presentation.QuickChange
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
    var dish by
        remember(recipeId) {
            mutableStateOf(
                recipeId?.let { QuickCalculator.open(state, it, today, money) } ?: QuickInput()
            )
        }
    // A dish archived since it was opened here is no longer the one edited: saving makes a new one.
    val input = QuickCalculator.current(if (recipeId == null) draft else dish, state)
    val update: (QuickInput) -> Unit = {
        if (recipeId == null) container.quickDraft.value = it else dish = it
    }
    val result =
        remember(input, state, money, today) { QuickCalculator.compute(input, state, money, today) }
    var askName by remember { mutableStateOf(false) }
    // A save that changes a dish on the menu or recorded prices waits for a look at what it
    // changes.
    var reviewing by remember { mutableStateOf<Pair<QuickPlan, QuickReview>?>(null) }
    // A word on the screen being explained: its meaning and the calculation behind its number.
    var explaining by remember { mutableStateOf<List<String>?>(null) }
    val context = LocalContext.current
    val firstName = remember { FocusRequester() }
    val rowNames = remember { HashMap<Int, FocusRequester>() }
    fun rowName(index: Int): FocusRequester =
        if (index == 0) firstName else rowNames.getOrPut(index) { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val quantities = remember { HashMap<Int, FocusRequester>() }
    fun quantity(index: Int): FocusRequester = quantities.getOrPut(index) { FocusRequester() }
    val extraAmounts = remember { HashMap<Int, FocusRequester>() }
    fun extraAmount(index: Int): FocusRequester = extraAmounts.getOrPut(index) { FocusRequester() }
    var focusExtra by remember { mutableStateOf<Int?>(null) }
    var focusRow by remember { mutableStateOf<Int?>(null) }
    var revealBlank by remember { mutableStateOf(false) }
    val keyboardVisible = WindowInsets.isImeVisible
    LaunchedEffect(keyboardVisible) { if (keyboardVisible) revealBlank = false }
    var discountExpanded by
        rememberSaveable(recipeId) { mutableStateOf(input.discountValue.isNotBlank()) }
    var detailsExpanded by
        rememberSaveable(recipeId) {
            mutableStateOf(input.extras.size > 1 || input.extras.any { it.takeawayOnly })
        }

    fun commit(plan: QuickPlan, current: QuickInput) {
        if (actions.unreadableRows > 0) {
            actions.say(Labels.DATA_NOT_VERIFIED)
            return
        }
        actions.run({ ledger -> QuickCalculator.save(ledger, plan, today) }) { id ->
            val saved = container.state.value?.let { QuickCalculator.open(it, id, today, money) }
            update(saved ?: current.copy(recipeId = id))
            actions.say("«${plan.name}» در غذاها ذخیره شد.")
        }
    }

    fun save(current: QuickInput) {
        if (actions.unreadableRows > 0) {
            actions.say(Labels.DATA_NOT_VERIFIED)
            return
        }
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
                if (review.isEmpty) commit(checked.value, current)
                else reviewing = checked.value to review
            }
        }
    }

    val topActions: @Composable RowScope.() -> Unit = {
        if (recipeId != null)
            TextButton(
                onClick = {
                    focusManager.clearFocus()
                    save(input)
                }
            ) {
                Text("ذخیره")
            }

        if (recipeId == null && input != QuickInput()) {
            IconButton(
                onClick = {
                    update(QuickInput())
                    runCatching { firstName.requestFocus() }
                }
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "محاسبهٔ تازه")
            }
        }
        if (result.hasLines && actions.unreadableRows == 0) {
            IconButton(
                onClick = {
                    share(
                        context,
                        input.dishName,
                        QuickCalculator.shareText(input, result, money, actions.unreadableRows),
                    )
                }
            ) {
                Icon(Icons.Filled.Share, contentDescription = "فرستادن نتیجه")
            }
        }
    }
    val body: @Composable (PaddingValues) -> Unit = { padding ->
        // One scroll surface keeps every field reachable with the keyboard or larger text.
        // The graphical dashboard remains the first section instead of consuming a fixed viewport.
        Column(
            Modifier.padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .testTag("calculator-scroll")
        ) {
            // While the keyboard is up, the display keeps its figures and drops the line under
            // them.
            Display(
                result,
                money,
                compact = WindowInsets.isImeVisible,
                dataUnreliable = actions.unreadableRows > 0,
                vatNote =
                    if (state.pricing.menuPriceIncludesVat)
                        "قیمت منو با مالیات ${Persian.digits(state.pricing.vatPercent.stripTrailingZeros().toPlainString())}٪"
                    else "قیمت منو بدون مالیات",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.5.dp),
                onExplain = { explaining = listOf(it) },
            )
            Column(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Card(
                    colors = whiteCardColors(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().testTag("calculator-form"),
                ) {
                    Column(
                        Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val largeText =
                            LocalDensity.current.fontScale > 1.25f ||
                                LocalConfiguration.current.screenWidthDp < 380
                        @Composable
                        fun fieldRow(
                            label: String,
                            icon: androidx.compose.ui.graphics.vector.ImageVector,
                            fieldWidth: Dp = 218.dp,
                            field: @Composable () -> Unit,
                        ) {
                            if (largeText)
                                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            icon,
                                            null,
                                            Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(label, style = MaterialTheme.typography.titleSmall)
                                    }
                                    Box(Modifier.fillMaxWidth()) { field() }
                                }
                            else
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        icon,
                                        null,
                                        Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        label,
                                        style =
                                            MaterialTheme.typography.titleSmall.copy(
                                                fontSize = 11.5.sp
                                            ),
                                        modifier = Modifier.weight(1f),
                                    )
                                    Box(Modifier.width(fieldWidth)) { field() }
                                }
                        }
                        fieldRow("نام غذا", PremiumIcons.chef, 237.dp) {
                            ReferenceField(
                                input.dishName,
                                { update(input.copy(dishName = it)) },
                                "نام غذا",
                                Modifier.testTag("dish-name"),
                                imeAction = ImeAction.Next,
                                height = 27.dp,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                horizontalPadding = 8.dp,
                            )
                        }
                        fieldRow("تعداد پرس", PremiumIcons.people, 140.dp) {
                            ReferenceField(
                                input.portions,
                                { update(input.copy(portions = it)) },
                                "تعداد پرس",
                                Modifier.testTag("calculator-portions"),
                                number = true,
                                centered = true,
                                height = 27.dp,
                                fontSize = 14.sp,
                                verticalPadding = 1.5.dp,
                                leading = {
                                    Icon(
                                        PremiumIcons.plus,
                                        "افزایش تعداد پرس",
                                        Modifier.size(24.dp)
                                            .background(
                                                MaterialTheme.colorScheme.surfaceContainerLow,
                                                RoundedCornerShape(6.dp),
                                            )
                                            .clickable {
                                                val n =
                                                    ir.foodcost.presentation.Numbers.parse(
                                                        input.portions
                                                    ) ?: java.math.BigDecimal.ONE
                                                update(
                                                    input.copy(
                                                        portions =
                                                            n.add(java.math.BigDecimal.ONE)
                                                                .toPlainString()
                                                    )
                                                )
                                            }
                                            .padding(3.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                trailing = {
                                    Icon(
                                        PremiumIcons.minus,
                                        "کاهش تعداد پرس",
                                        Modifier.size(24.dp)
                                            .background(
                                                MaterialTheme.colorScheme.surfaceContainerLow,
                                                RoundedCornerShape(6.dp),
                                            )
                                            .clickable {
                                                val n =
                                                    ir.foodcost.presentation.Numbers.parse(
                                                        input.portions
                                                    ) ?: java.math.BigDecimal.ONE
                                                if (n > java.math.BigDecimal.ONE)
                                                    update(
                                                        input.copy(
                                                            portions =
                                                                n.subtract(java.math.BigDecimal.ONE)
                                                                    .toPlainString()
                                                        )
                                                    )
                                            }
                                            .padding(3.dp),
                                    )
                                },
                            )
                        }
                        fieldRow("قیمت فروش (هر پرس)", ReferenceIcons.tag) {
                            ReferenceField(
                                input.menuPrice,
                                { update(input.copy(menuPrice = it)) },
                                "قیمت فروش هر پرس",
                                Modifier.testTag("menu-price"),
                                number = true,
                                suffix = money.unit,
                                imeAction = ImeAction.Done,
                                textAlign = TextAlign.Left,
                                textInsetEnd = 14.dp,
                                horizontalPadding = 5.dp,
                                height = 27.dp,
                                fontSize = 13.sp,
                            )
                        }
                        val mainExtraIndex = input.extras.indexOfFirst { !it.takeawayOnly }
                        fieldRow(
                            if (input.extras.count { !it.takeawayOnly } > 1)
                                input.extras.first { !it.takeawayOnly }.name
                            else "هزینه جانبی (هر پرس)",
                            ReferenceIcons.coins,
                        ) {
                            ReferenceField(
                                input.extras.getOrNull(mainExtraIndex)?.amount.orEmpty(),
                                { value ->
                                    update(
                                        if (mainExtraIndex < 0)
                                            input.copy(
                                                extras =
                                                    input.extras + QuickExtra("هزینه جانبی", value)
                                            )
                                        else
                                            input.withExtra(
                                                mainExtraIndex,
                                                input.extras[mainExtraIndex].copy(amount = value),
                                            )
                                    )
                                },
                                "هزینه جانبی هر پرس",
                                Modifier.testTag("main-extra-amount"),
                                number = true,
                                suffix = money.unit,
                                imeAction = ImeAction.Done,
                                textAlign = TextAlign.Left,
                                textInsetEnd = 14.dp,
                                horizontalPadding = 5.dp,
                                height = 27.dp,
                                fontSize = 13.sp,
                            )
                        }
                        val discountProblem = QuickCalculator.discountProblem(input, money)
                        if (discountExpanded || discountProblem != null)
                            DisclosureRow(
                                title = "تخفیف فروش (اختیاری)",
                                expanded = discountExpanded,
                                tag = "discount-toggle",
                                onClick = { discountExpanded = !discountExpanded },
                                summary =
                                    input.discountValue
                                        .takeIf { it.isNotBlank() }
                                        ?.let {
                                            if (input.discountMode == QuickInput.PERCENT)
                                                "${Persian.digits(it)}٪"
                                            else "$it ${money.unit}"
                                        },
                            )
                        if (discountExpanded || discountProblem != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FilterChip(
                                    selected = input.discountMode == QuickInput.PERCENT,
                                    onClick = {
                                        update(
                                            input.copy(
                                                discountMode = QuickInput.PERCENT,
                                                discountValue = "",
                                            )
                                        )
                                    },
                                    label = { Text("درصدی") },
                                    modifier = Modifier.testTag("discount-percent-mode"),
                                )
                                FilterChip(
                                    selected = input.discountMode == QuickInput.FIXED,
                                    onClick = {
                                        update(
                                            input.copy(
                                                discountMode = QuickInput.FIXED,
                                                discountValue = "",
                                            )
                                        )
                                    },
                                    label = { Text("مبلغ ثابت") },
                                    modifier = Modifier.testTag("discount-fixed-mode"),
                                )
                            }
                            TextInput(
                                value = input.discountValue,
                                onValueChange = { update(input.copy(discountValue = it)) },
                                label =
                                    if (input.discountMode == QuickInput.PERCENT)
                                        "درصد تخفیف (کمتر از ۱۰۰)"
                                    else "تخفیف هر پرس",
                                modifier = Modifier.fillMaxWidth().testTag("discount-value"),
                                number = true,
                                suffix =
                                    if (input.discountMode == QuickInput.PERCENT) "٪"
                                    else money.unit,
                                imeAction = ImeAction.Done,
                            )
                        }
                        if (discountProblem != null) {
                            Text(
                                discountProblem,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.testTag("discount-error"),
                            )
                        } else if (input.discountValue.isNotBlank()) {
                            Text(
                                "تخفیف فقط برای دیدن اثرش است و با غذا ذخیره نمی‌شود. روی فروش حضوری و بیرون‌بر حساب می‌شود" +
                                    if (state.pricing.online.isSet)
                                        "؛ فروش آنلاین تخفیف خودش را از تنظیمات دارد."
                                    else ".",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("discount-note"),
                            )
                        }
                    }
                }
                Card(
                    colors = whiteCardColors(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().testTag("calculator-ingredients"),
                ) {
                    Column(
                        Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                ReferenceIcons.layers,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                QuickCalculator.portionsLabel(input),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(10.dp),
                                modifier =
                                    Modifier.focusProperties { canFocus = false }
                                        .clickable {
                                            revealBlank = true
                                            val blank = input.rows.indexOfLast { it.isBlank }
                                            if (blank >= 0) focusRow = blank
                                            else {
                                                focusRow = input.rows.size
                                                update(input.copy(rows = input.rows + QuickRow()))
                                            }
                                        }
                                        .testTag("ingredient-add-row"),
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text("افزودن ماده", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        if (LocalDensity.current.fontScale <= 1.25f) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        RoundedCornerShape(9.dp),
                                    )
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    "نام ماده",
                                    Modifier.weight(1.2f),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Text(
                                    "مقدار",
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Text(
                                    if (input.rows.all { it.isBlank || it.uom == Uom.GRAM })
                                        "قیمت / کیلو"
                                    else "قیمت / واحد خرید",
                                    Modifier.weight(1.2f),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Spacer(Modifier.width(22.dp))
                            }
                        }
                        input.rows.forEachIndexed { i, row ->
                            val show =
                                !row.isBlank ||
                                    input.rows.none { !it.isBlank } ||
                                    keyboardVisible ||
                                    revealBlank ||
                                    focusRow == i
                            if (show)
                                RowCard(
                                    index = i,
                                    row = row,
                                    result = result.rows[i],
                                    suggestions =
                                        QuickCalculator.suggestions(state, row, today, money),
                                    money = money,
                                    showPriceUnitLabel =
                                        input.rows.any { !it.isBlank && it.uom != Uom.GRAM },
                                    nameFocus = rowName(i),
                                    quantityFocus = quantity(i),
                                    onChange = { update(input.withRow(i, it)) },
                                    onRemove = { update(input.withoutRow(i)) },
                                    onNextRow = { focusRow = i + 1 },
                                    onPick = {
                                        update(
                                            input.withRow(
                                                i,
                                                QuickCalculator.pick(
                                                    state,
                                                    row,
                                                    it.id,
                                                    today,
                                                    money,
                                                ),
                                            )
                                        )
                                        runCatching { quantity(i).requestFocus() }
                                    },
                                )
                        }
                        if (detailsExpanded)
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainer,
                                        RoundedCornerShape(9.dp),
                                    )
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (actions.unreadableRows == 0)
                                    Text(
                                        "جمع مواد: ${result.costPerPortion} · ${result.miscNote}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.testTag("ingredient-total"),
                                    )
                                else
                                    Text(
                                        BackupTexts.UNVERIFIED_RESULT,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    ReferenceIcons.info,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                    }
                }
                LaunchedEffect(focusRow, input.rows.size) {
                    focusRow
                        ?.takeIf { it in input.rows.indices }
                        ?.let {
                            runCatching { rowName(it).requestFocus() }
                            focusRow = null
                        }
                }
                if (recipeId == null && input == QuickInput()) {
                    TextButton(
                        onClick = { nav.open(Screen.Help(Screen.Help.GUIDE)) },
                        modifier = Modifier.focusProperties { canFocus = false },
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("اولین بار است؟ راهنما و یک نمونه")
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DisclosureRow(
                        "هزینه‌ها و کانال‌های فروش",
                        detailsExpanded,
                        "calculator-details-toggle",
                        { detailsExpanded = !detailsExpanded },
                    )
                    if (detailsExpanded) {
                        SectionSelector(
                            QuickCalculator.categories(state, input),
                            input.category,
                            Modifier.fillMaxWidth(),
                        ) {
                            update(input.withCategory(it))
                        }
                        if (!discountExpanded)
                            TextButton(
                                onClick = { discountExpanded = true },
                                modifier = Modifier.testTag("discount-toggle"),
                            ) {
                                Text("تخفیف فروش (اختیاری)")
                            }
                        ExtrasCard(
                            input,
                            result,
                            money,
                            ::extraAmount,
                            onChange = { i, extra -> update(input.withExtra(i, extra)) },
                            onRemove = { i -> update(input.withoutExtra(i)) },
                            onAdd = { preset ->
                                focusExtra = input.extras.size
                                update(input.plusExtra(preset))
                            },
                            policy =
                                if (state.pricing.scope == PricingScope.GENERAL)
                                    "قانون قیمت‌گذاری عمومی · ${result.method}"
                                else "قانون بخش منو · ${result.method}",
                        )
                        LaunchedEffect(focusExtra, input.extras.size) {
                            focusExtra
                                ?.takeIf { it < input.extras.size }
                                ?.let { i ->
                                    runCatching { extraAmount(i).requestFocus() }
                                    focusExtra = null
                                }
                        }
                        if (actions.unreadableRows == 0 && result.netSales != null)
                            PriceDetails(result, money) { explaining = it }
                    }
                }
                input.recipeId?.let { id ->
                    TextButton(onClick = { nav.open(Screen.Recipe(id.value)) }) {
                        Text("تعداد پرس، درصد جزئی و جزئیات بیشتر")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        // Here, next to the fields, so the first one is in place when it is asked to take the
        // keyboard.
        LaunchedEffect(recipeId) {
            if (recipeId == null && input == QuickInput()) runCatching { firstName.requestFocus() }
        }
    }
    val title = if (recipeId == null) "ماشین‌حساب" else input.dishName.ifBlank { "غذا" }
    if (recipeId == null) {
        TabScaffold(
            title = title,
            nav = nav,
            actions = actions,
            topActions = topActions,
            bottomActions = {
                FlowRow(
                    Modifier.fillMaxWidth()
                        .testTag("calculator-actions")
                        .padding(horizontal = 12.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow =
                        if (
                            LocalDensity.current.fontScale > 1.25f ||
                                LocalConfiguration.current.screenWidthDp < 380
                        )
                            1
                        else 2,
                ) {
                    FoodCostPrimaryButton(
                        "ذخیره غذا",
                        {
                            focusManager.clearFocus()
                            save(input)
                        },
                        Modifier.weight(1.05f).testTag("calculator-save"),
                        PremiumIcons.save,
                    )
                    FoodCostOutlinedButton(
                        "اشتراک‌گذاری",
                        {
                            share(
                                context,
                                input.dishName,
                                QuickCalculator.shareText(
                                    input,
                                    result,
                                    money,
                                    actions.unreadableRows,
                                ),
                            )
                        },
                        Modifier.weight(1f),
                        PremiumIcons.share,
                    )
                }
            },
            content = body,
        )
    } else {
        DetailScaffold(
            title = title,
            nav = nav,
            actions = actions,
            topActions = topActions,
            content = body,
        )
    }

    explaining?.let { ids ->
        TermDialog(
            ids = ids,
            live = result.explanations,
            onDismiss = { explaining = null },
            more = {
                explaining = null
                nav.open(Screen.Help(Screen.Help.TERMS, ids.first()))
            },
        )
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
 * The calculator's graphical display at the top of the scrollable form. Without a menu price it
 * shows the food's cost and the suggested price; with one, the four figures that decide a price:
 * the food's cost, the net sale, the food cost and the gross profit. When the dish has other costs,
 * the cost with them and what is left after them come under those.
 */
@Composable
private fun Display(
    result: QuickResult,
    money: MoneyFormat,
    compact: Boolean,
    dataUnreliable: Boolean,
    vatNote: String,
    modifier: Modifier = Modifier,
    onExplain: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val accents = ir.foodcost.app.ui.theme.dashboardColors()
    val large = LocalDensity.current.fontScale > 1.25f
    val referenceViewport = !large && LocalConfiguration.current.screenWidthDp >= 380
    FoodCostCard(modifier.testTag("calculator-dashboard")) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (dataUnreliable) {
                Text(
                    BackupTexts.UNVERIFIED_RESULT,
                    color = scheme.error,
                    modifier = Modifier.testTag("unverified-financial-result"),
                )
                return@Column
            }
            @Composable
            fun metrics(modifier: Modifier) {
                Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    @Composable
                    fun metric(
                        label: String,
                        value: String,
                        icon: androidx.compose.ui.graphics.vector.ImageVector,
                        background: Color,
                        accent: Color,
                        tag: String,
                        term: String,
                    ) {
                        Row(
                            Modifier.fillMaxWidth()
                                .heightIn(min = 33.dp)
                                .background(background, RoundedCornerShape(9.dp))
                                .clickable { onExplain(term) }
                                .testTag(tag)
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(17.dp))
                            Text(
                                label,
                                Modifier.weight(1f),
                                style =
                                    MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 10.sp,
                                        lineHeight = 15.sp,
                                    ),
                            )
                            Text(
                                buildAnnotatedString {
                                    append(value)
                                    val unitStart = value.lastIndexOf(money.unit)
                                    if (unitStart >= 0) {
                                        addStyle(
                                            SpanStyle(
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Normal,
                                            ),
                                            unitStart,
                                            unitStart + money.unit.length,
                                        )
                                    }
                                },
                                style =
                                    MaterialTheme.typography.titleSmall.copy(
                                        fontSize = 16.sp,
                                        lineHeight = 24.sp,
                                    ),
                                color = accent,
                            )
                        }
                    }
                    metric(
                        "قیمت فروش پیشنهادی",
                        result.suggested ?: "—",
                        ReferenceIcons.tag,
                        accents.sales.container,
                        scheme.primary,
                        "suggested",
                        Glossary.SUGGESTED,
                    )
                    metric(
                        "هزینه مواد اولیه هر پرس",
                        result.costPerPortion,
                        ReferenceIcons.layers,
                        accents.food.container,
                        accents.food.content,
                        "cost",
                        Glossary.FOOD,
                    )
                    metric(
                        if (result.grossProfitNegative) "زیان ناخالص" else "سود ناخالص",
                        result.grossProfit?.replace("+", "") ?: "—",
                        PremiumIcons.trend,
                        if (result.grossProfitNegative) scheme.errorContainer
                        else scheme.tertiaryContainer,
                        if (result.grossProfitNegative) scheme.error else scheme.tertiary,
                        "gross-profit",
                        Glossary.GROSS_PROFIT,
                    )
                }
            }
            @Composable
            fun gauge(modifier: Modifier) {
                FoodCostPercentageGauge(
                    result.foodCostValue?.toFloat(),
                    result.foodCostPercent ?: "—",
                    "فودکاست",
                    modifier
                        .absoluteOffset(x = if (referenceViewport) 5.dp else 0.dp)
                        .testTag("food-cost-gauge"),
                    status = if (result.hasLines) result.status to result.statusLabel else null,
                )
            }
            if (large) {
                metrics(Modifier.fillMaxWidth())
                gauge(Modifier.fillMaxWidth().padding(horizontal = 35.dp))
            } else
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    metrics(Modifier.weight(if (referenceViewport) 1.85f else 1.9f))
                    androidx.compose.material3.VerticalDivider(
                        Modifier.height(98.dp),
                        color = scheme.outlineVariant,
                    )
                    gauge(Modifier.weight(1f))
                }
            if (result.discountApplied)
                Text(
                    "فروش پس از تخفیف: ${result.menuRials?.let(money::full)?:"—"}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("discount-summary"),
                )
            HorizontalDivider(color = scheme.outlineVariant, thickness = .6.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Row(
                    Modifier.weight(if (referenceViewport) .93f else 1f)
                        .absoluteOffset(x = if (referenceViewport) (-3).dp else 0.dp)
                        .semantics(mergeDescendants = true) {}
                        .clickable { onExplain(Glossary.FOOD_COST) }
                        .testTag("food-cost"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            if (referenceViewport) 9.dp else 5.dp,
                            Alignment.CenterHorizontally,
                        ),
                ) {
                    Text(
                        "فودکاست:",
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontSize = if (referenceViewport) 10.5.sp else 11.sp
                            ),
                    )
                    Text(
                        result.foodCostPercent ?: "—",
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontSize = if (referenceViewport) 17.sp else 14.5.sp
                            ),
                        color = scheme.tertiary,
                    )
                }
                androidx.compose.material3.VerticalDivider(
                    Modifier.height(28.dp),
                    color = scheme.outlineVariant,
                )
                Column(
                    Modifier.weight(1f).padding(start = if (referenceViewport) 30.dp else 0.dp),
                    horizontalAlignment =
                        if (referenceViewport) Alignment.Start else Alignment.CenterHorizontally,
                ) {
                    Text(
                        vatNote,
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontSize = if (referenceViewport) 9.5.sp else 10.sp,
                                lineHeight = 14.sp,
                            ),
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        result.menuRials?.let(money::full) ?: "—",
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            if (result.leftOutNote != null)
                Text(
                    result.leftOutNote!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error,
                )
        }
    }
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
    policy: String,
) {
    var addOpen by remember { mutableStateOf(false) }
    GroupedRow(true, true, Modifier.testTag("calculator-extras")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "هزینه‌های دیگر هر پرس",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    OutlinedButton(
                        onClick = { addOpen = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                        modifier =
                            Modifier.focusProperties { canFocus = false }.testTag("extra-add-menu"),
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text("افزودن هزینه", style = MaterialTheme.typography.labelLarge)
                    }
                    DropdownMenu(addOpen, onDismissRequest = { addOpen = false }) {
                        QuickCalculator.EXTRA_PRESETS.forEachIndexed { index, preset ->
                            DropdownMenuItem(
                                text = { Text(preset.name) },
                                onClick = {
                                    addOpen = false
                                    onAdd(preset)
                                },
                                modifier = Modifier.testTag("extra-preset-$index"),
                            )
                        }
                    }
                }
            }
            Text(
                policy,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("calculator-pricing-scope"),
            )
            input.extras.forEachIndexed { index, extra ->
                var scopeOpen by remember { mutableStateOf(false) }
                val large = LocalDensity.current.fontScale > 1.25f
                val problem = result.extras.getOrNull(index)?.problem
                @Composable
                fun name(modifier: Modifier) {
                    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ReferenceField(
                            extra.name,
                            { onChange(index, extra.copy(name = it)) },
                            "عنوان",
                            Modifier.testTag("extra-$index-name"),
                            imeAction = ImeAction.Next,
                            fontSize = 15.sp,
                        )
                        Box {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                shape = RoundedCornerShape(8.dp),
                                border =
                                    BorderStroke(.7.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier =
                                    Modifier.focusProperties { canFocus = false }
                                        .clickable { scopeOpen = true }
                                        .testTag("extra-$index-scope"),
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        if (extra.takeawayOnly) "بیرون‌بر و آنلاین"
                                        else "همهٔ فروش‌ها",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    Icon(
                                        Icons.Filled.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            DropdownMenu(scopeOpen, onDismissRequest = { scopeOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("همهٔ فروش‌ها") },
                                    onClick = {
                                        scopeOpen = false
                                        onChange(index, extra.copy(takeawayOnly = false))
                                    },
                                    modifier = Modifier.testTag("extra-$index-scope-all"),
                                )
                                DropdownMenuItem(
                                    text = { Text("بیرون‌بر و آنلاین") },
                                    onClick = {
                                        scopeOpen = false
                                        onChange(index, extra.copy(takeawayOnly = true))
                                    },
                                    modifier = Modifier.testTag("extra-$index-scope-takeaway"),
                                )
                            }
                        }
                    }
                }
                @Composable
                fun amount(modifier: Modifier) {
                    ReferenceField(
                        extra.amount,
                        { onChange(index, extra.copy(amount = it)) },
                        "هر پرس",
                        modifier.focusRequester(amountFocus(index)).testTag("extra-$index-amount"),
                        number = true,
                        suffix = money.unit,
                        imeAction = ImeAction.Done,
                        fontSize = 15.sp,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        if (!large)
                            RowSymbol(
                                if (extra.takeawayOnly) ReferenceIcons.box
                                else ReferenceIcons.receipt
                            )
                        name(Modifier.weight(1.3f))
                        if (!large) amount(Modifier.weight(1f))
                        RowMenu("extra-$index-menu") { close ->
                            DropdownMenuItem(
                                text = { Text("حذف هزینه") },
                                onClick = {
                                    close()
                                    onRemove(index)
                                },
                                modifier = Modifier.testTag("extra-$index-remove"),
                            )
                        }
                    }
                    if (large) amount(Modifier.fillMaxWidth())
                    if (problem != null && extra.amount.isNotBlank())
                        Text(
                            problem,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("extra-$index-error"),
                        )
                }
                if (index < input.extras.lastIndex)
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = .5.dp,
                    )
            }
            if (input.extras.isEmpty())
                Text(
                    "ظرف، دستمزد یا سهم هزینهٔ هر پرس را از «افزودن هزینه» وارد کنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            result.overheadShare?.let {
                Text("سهم سربار ماهانه از تنظیمات: $it", style = MaterialTheme.typography.bodySmall)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    ReferenceIcons.info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (result.pricesFoodOnly)
                        "هزینه‌ها یک‌بار، برحسب کانال فروش از مانده کم می‌شوند."
                    else "هزینه‌ها برحسب کانال فروش، در قیمت پیشنهادی هم حساب می‌شوند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Channel tabs keep every amount full width at large font sizes and retain each channel's own math.
 */
@Composable
private fun PriceDetails(
    result: QuickResult,
    money: MoneyFormat,
    onExplain: (List<String>) -> Unit,
) {
    if (result.channels.isEmpty()) return
    var selectedCode by rememberSaveable {
        mutableStateOf(ir.foodcost.core.model.SalesChannel.DINE_IN.name)
    }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selected =
        result.channels.indexOfFirst { it.channel.name == selectedCode }.takeIf { it >= 0 } ?: 0
    val channel = result.channels[selected]
    val large = LocalDensity.current.fontScale > 1.25f
    GroupedRow(true, true, Modifier.testTag("pricing-dashboard")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "کانال‌های فروش",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    ReferenceIcons.info,
                    contentDescription = "راهنمای کانال‌های فروش",
                    modifier =
                        Modifier.size(18.dp).clickable { onExplain(listOf(Glossary.CHANNELS)) },
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                result.channels.forEachIndexed { index, item ->
                    Surface(
                        color =
                            if (index == selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerLowest,
                        contentColor =
                            if (index == selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(.7.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier =
                            Modifier.focusProperties { canFocus = false }
                                .semantics {
                                    this.selected = index == selected
                                    role = Role.Tab
                                }
                                .clickable { selectedCode = item.channel.name }
                                .testTag("channel-tab-$index"),
                    ) {
                        Text(
                            item.label,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            val negative = channel.remainingNegative
            Surface(
                color =
                    if (negative) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("channel-card-$selected"),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("ماندهٔ هر پرس", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "قیمت منو: ${channel.price}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().testTag("channel-$selected-price"),
                    )
                    FitText(
                        channel.remaining.replace("+", ""),
                        MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 25.sp,
                            textAlign = TextAlign.Center,
                        ),
                        Modifier.fillMaxWidth()
                            .testTag(
                                if (selected == 0) "channel-remaining"
                                else "channel-$selected-remaining"
                            ),
                        color =
                            if (negative) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.tertiary,
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = .5.dp,
                    )
                    @Composable
                    fun net(modifier: Modifier) {
                        Column(modifier) {
                            Text("فروش خالص", style = MaterialTheme.typography.bodySmall)
                            Text(
                                channel.netSales,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.fillMaxWidth().testTag("channel-$selected-net"),
                            )
                        }
                    }
                    @Composable
                    fun cost(modifier: Modifier) {
                        Column(modifier) {
                            Text(
                                if (channel.channel == ir.foodcost.core.model.SalesChannel.ONLINE)
                                    "جمع هزینه‌ها با کمیسیون"
                                else "جمع هزینه‌ها",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                channel.cost,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.fillMaxWidth().testTag("channel-$selected-cost"),
                            )
                        }
                    }
                    if (large) {
                        net(Modifier.fillMaxWidth())
                        cost(Modifier.fillMaxWidth())
                    } else
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            net(Modifier.weight(1f))
                            cost(Modifier.weight(1f))
                        }
                    result.leftOutNote?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            DisclosureRow(
                "درصد سود و جزئیات بیشتر",
                expanded,
                "channel-details-toggle",
                { expanded = !expanded },
            )
            if (expanded) {
                Text("جزئیات ${channel.label}", style = MaterialTheme.typography.titleSmall)
                Text(
                    "فروش خالص: ${result.netSales?:"—"}",
                    modifier = Modifier.testTag("net-sales"),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "مبنای حاشیه و مارک‌آپ: همهٔ هزینه‌های ثبت‌شدهٔ این کانال",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("channel-percent-basis"),
                )
                channel.foodCostPercent?.let {
                    Explained("فودکاست: $it") { onExplain(listOf(Glossary.FOOD_COST)) }
                }
                channel.margin?.let {
                    Text(
                        "حاشیه پس از هزینه‌ها: $it",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag("channel-$selected-margin"),
                    )
                }
                channel.markup?.let {
                    Text(
                        "مارک‌آپ روی هزینه‌ها: $it",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag("channel-$selected-markup"),
                    )
                }
                channel.priceForSameRemaining?.let {
                    Text(
                        "قیمت برای حفظ ماندهٔ حضوری: $it",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                if (
                    channel.channel != ir.foodcost.core.model.SalesChannel.DINE_IN &&
                        channel.priceForSameRemaining == null
                ) {
                    Text(
                        "قیمت لازم برای حفظ ماندهٔ حضوری بیش از حد بزرگ است.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("channel-$selected-same-remaining-unavailable"),
                    )
                }
                Explained("«برای همان مانده» یعنی قیمت لازم برای حفظ ماندهٔ حضوری در کانال دیگر.") {
                    onExplain(listOf(Glossary.CHANNELS))
                }
                result.maxCost?.let {
                    Text(
                        "راهنمای قیمت حضوری · ${result.maxCostLabel ?: "حداکثر هزینه"}: $it",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                result.suggested?.let {
                    Text(
                        "${result.suggestedLabel}: $it",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/** A line whose words a tap explains, marked so. */
@Composable
private fun Explained(text: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.fillMaxWidth()
                .focusProperties { canFocus = false }
                .clickable(onClickLabel = "معنی", onClick = onClick),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp).size(16.dp),
        )
    }
}

/** The row's number, which the messages about unfinished rows refer to. */
@Composable
private fun RowNumber(number: Int, blank: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.size(28.dp)
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
    showPriceUnitLabel: Boolean,
    nameFocus: FocusRequester?,
    quantityFocus: FocusRequester,
    onChange: (QuickRow) -> Unit,
    onRemove: () -> Unit,
    onPick: (QuickSuggestion) -> Unit,
    onNextRow: () -> Unit,
) {
    val priceFocus = remember { FocusRequester() }
    val fontScale = LocalDensity.current.fontScale
    var nameFocused by remember { mutableStateOf(false) }
    @Composable
    fun nameField(modifier: Modifier) {
        ReferenceField(
            value = row.name,
            onValueChange = { onChange(row.copy(name = it, ingredientId = null)) },
            label = "نام ماده",
            modifier =
                modifier
                    .then(if (nameFocus != null) Modifier.focusRequester(nameFocus) else Modifier)
                    .onFocusChanged { nameFocused = it.isFocused }
                    .testTag("row-$index-name"),
            imeAction = ImeAction.Next,
            onImeAction = { priceFocus.requestFocus() },
            outlined = false,
            height = 26.dp,
            fontSize = 12.5.sp,
            verticalPadding = 2.dp,
        )
    }
    @Composable
    fun priceField(modifier: Modifier) {
        ReferenceField(
            value = row.price,
            onValueChange = { onChange(row.copy(price = it)) },
            label = result.priceLabel,
            modifier = modifier.focusRequester(priceFocus).testTag("row-$index-price"),
            number = true,
            suffix = money.unit,
            plainSuffix = true,
            imeAction = ImeAction.Next,
            onImeAction = { quantityFocus.requestFocus() },
            outlined = false,
            height = 26.dp,
            fontSize = 12.5.sp,
            verticalPadding = 2.dp,
        )
    }
    @Composable
    fun quantityField(modifier: Modifier) {
        ReferenceField(
            value = row.qty,
            onValueChange = { onChange(row.copy(qty = it)) },
            label = "مقدار",
            modifier = modifier.focusRequester(quantityFocus).testTag("row-$index-qty"),
            number = true,
            suffix = null,
            trailing = {
                UnitMenu(
                    row.uom,
                    result.units,
                    Modifier.testTag("row-$index-unit"),
                    compact = true,
                    textOnly = true,
                ) {
                    onChange(row.copy(uom = it))
                }
            },
            imeAction = ImeAction.Next,
            onImeAction = onNextRow,
            outlined = false,
            height = 26.dp,
            fontSize = 12.5.sp,
            verticalPadding = 2.dp,
            centered = true,
        )
    }
    @Composable
    fun removeButton() {
        if (!row.isBlank) {
            Box(
                Modifier.size(22.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        RoundedCornerShape(6.dp),
                    )
                    .focusProperties { canFocus = false }
                    .clickable(role = Role.Button, onClickLabel = "حذف ردیف", onClick = onRemove)
                    .testTag("row-$index-remove"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    ReferenceIcons.trash,
                    contentDescription = "حذف ردیف ${Persian.digits(index + 1)}",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(Modifier.width(22.dp))
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Narrow screens and larger accessibility fonts retain full-width readable ingredient
        // names.
        val stackName = maxWidth < 300.dp || fontScale > 1.25f
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (stackName) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    nameField(Modifier.weight(1f))
                    removeButton()
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    priceField(Modifier.weight(1.3f))
                    quantityField(Modifier.weight(0.8f))
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    removeButton()
                    Row(
                        Modifier.weight(1f)
                            .border(
                                .7.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(9.dp),
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        nameField(Modifier.weight(1.05f))
                        androidx.compose.material3.VerticalDivider(
                            Modifier.height(18.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        quantityField(Modifier.weight(1f))
                        androidx.compose.material3.VerticalDivider(
                            Modifier.height(18.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        priceField(Modifier.weight(1.2f))
                    }
                }
            }
            if (stackName || row.uom != Uom.GRAM || showPriceUnitLabel) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "ردیف ${Persian.digits(index + 1)} · ${result.priceLabel} (${money.unit})",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                    )
                    UnitMenu(row.uom, result.units, Modifier.testTag("row-$index-unit")) {
                        onChange(row.copy(uom = it))
                    }
                }
            }
            if (nameFocused && suggestions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { suggestion ->
                        AssistChip(
                            onClick = { onPick(suggestion) },
                            label = { Text("${suggestion.name}، ${suggestion.detail}") },
                            modifier = Modifier.focusProperties { canFocus = false },
                        )
                    }
                }
            }
            val problem = result.problem
            val info = problem ?: result.note
            if (info != null) {
                Text(
                    info,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (problem != null && !result.waiting) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
private fun UnitMenu(
    selected: Uom,
    options: List<Uom>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    textOnly: Boolean = false,
    onSelect: (Uom) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.focusProperties { canFocus = false }
                .then(
                    if (textOnly)
                        Modifier.semantics {
                            contentDescription = "واحد مقدار: ${Labels.uom(selected)}"
                        }
                    else Modifier
                )
                .clickable(onClickLabel = "واحد مقدار: ${Labels.uom(selected)}") { open = true }
                .padding(if (compact) 0.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!compact || textOnly)
                Text(
                    Labels.uom(selected),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                )
            if (!textOnly)
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "واحد مقدار: ${Labels.uom(selected)}",
                    modifier = Modifier.size(if (compact) 16.dp else 20.dp),
                )
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
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim()) }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

/** A food's category is selected explicitly; it is never a switch enabling a pricing module. */
@Composable
private fun SectionSelector(
    sections: List<String>,
    selected: String?,
    modifier: Modifier = Modifier,
    onSelect: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            shape = RoundedCornerShape(9.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier =
                Modifier.fillMaxWidth()
                    .focusProperties { canFocus = false }
                    .clickable { open = true }
                    .testTag("menu-section-selector"),
        ) {
            Row(
                Modifier.heightIn(min = 32.dp).padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("دسته:", style = MaterialTheme.typography.labelMedium)
                FitText(
                    selected ?: "بدون بخش",
                    MaterialTheme.typography.labelLarge,
                    Modifier.weight(1f).padding(horizontal = 6.dp),
                )
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("بدون بخش") },
                onClick = {
                    open = false
                    onSelect(null)
                },
                modifier = Modifier.testTag("menu-section-none"),
            )
            sections.forEachIndexed { index, name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        open = false
                        onSelect(name)
                    },
                    modifier = Modifier.testTag("menu-section-$index"),
                )
            }
            if (sections.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("بخش‌ها را در تنظیمات بسازید") },
                    onClick = {},
                    enabled = false,
                )
            }
        }
    }
}

@Composable
private fun DisclosureRow(
    title: String,
    expanded: Boolean,
    tag: String,
    onClick: () -> Unit,
    summary: String? = null,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier =
            Modifier.fillMaxWidth()
                .focusProperties { canFocus = false }
                .clickable(onClick = onClick)
                .testTag(tag),
    ) {
        Row(
            Modifier.heightIn(min = 32.dp).padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            summary?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "بستن جزئیات" else "نمایش جزئیات",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Before a save that changes what is already saved: the dish's figures before and after, the
 * ingredient prices it records for every dish, and anything that looks like a slip.
 */
@Composable
private fun ReviewDialog(
    review: QuickReview,
    money: MoneyFormat,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ذخیرهٔ «${review.name}»") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (review.changes.isNotEmpty()) ChangeTable(review.changes, money)
                if (review.prices.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("قیمت‌های تازه", style = MaterialTheme.typography.titleSmall)
                        review.prices.forEach {
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (review.warnings.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "پیش از ذخیره ببینید",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        review.warnings.forEach {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, modifier = Modifier.testTag("review-save")) {
                Text("ذخیره")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("برگشت") } },
    )
}

/** The figures that change, before and after, with how far the amounts move. */
@Composable
private fun ChangeTable(changes: List<QuickChange>, money: MoneyFormat) {
    val better = statusColors(PriceStatus.OK).content
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                "به ${money.unit}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1.25f),
            )
            Text(
                "قبل",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            Text(
                "بعد",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
        }
        changes.forEach { change ->
            Row(Modifier.fillMaxWidth().testTag("change-${change.label}")) {
                Text(
                    change.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1.25f),
                )
                FitText(
                    change.before,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    FitText(change.after, style = MaterialTheme.typography.labelLarge)
                    change.change?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (change.worse) MaterialTheme.colorScheme.error else better,
                        )
                    }
                }
            }
        }
    }
}

/** Hands the calculation as text to whichever app the user picks: a messenger, notes, email. */
private fun share(context: Context, title: String, text: String) {
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, title.trim())
        }
    context.startActivity(Intent.createChooser(send, "فرستادن نتیجه"))
}
