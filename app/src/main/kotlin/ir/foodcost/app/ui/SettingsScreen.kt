package ir.foodcost.app.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ir.foodcost.app.AppContainer
import ir.foodcost.core.money.DisplayCurrency
import ir.foodcost.core.money.PriceRounding
import ir.foodcost.core.pricing.CostBasis
import ir.foodcost.core.pricing.PricingScope
import ir.foodcost.data.ledger.LedgerState
import ir.foodcost.presentation.Checked
import ir.foodcost.presentation.Field
import ir.foodcost.presentation.Labels
import ir.foodcost.presentation.MethodChoice
import ir.foodcost.presentation.MoneyFormat
import ir.foodcost.presentation.Persian
import ir.foodcost.presentation.SettingsInput
import ir.foodcost.presentation.SettingsPart
import java.time.LocalDate

enum class SettingsPage { OVERVIEW, PRICING, COSTS, SECTIONS, CURRENCY, BACKUP, HELP }

/** Focused settings screens share a draft owned above navigation, so Help does not discard edits. */
@Composable
fun SettingsScreen(
    container: AppContainer,
    state: LedgerState,
    money: MoneyFormat,
    today: LocalDate,
    nav: Navigator,
    actions: AppActions,
    input: SettingsInput,
    onInputChange: (SettingsInput) -> Unit,
    page: SettingsPage,
    onPageChange: (SettingsPage) -> Unit,
) {
    val currency by container.currency.collectAsState()
    var pricingDetails by remember { mutableStateOf(false) }
    var errors by remember(page) { mutableStateOf(emptyMap<Field, String>()) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val categoryNames = remember { HashMap<Int, FocusRequester>() }
    val categoryPercents = remember { HashMap<Int, FocusRequester>() }
    fun categoryName(i: Int): FocusRequester = categoryNames.getOrPut(i) { FocusRequester() }
    fun categoryPercent(i: Int): FocusRequester = categoryPercents.getOrPut(i) { FocusRequester() }
    var focusCategory by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    val generalPricingEnabled = input.scope == PricingScope.GENERAL
    // The sections page edits the policy that is actually saved, not an unsaved scope preview.
    val sectionPricingEnabled = state.pricing.scope == PricingScope.MENU_SECTIONS
    val backupFirst = !actions.integrity.verified || actions.backupReminder != null

    // Material Snackbar is anchored to the bottom and may cover the Save action while the user
    // quickly switches pages or pricing modes. Dismiss transient feedback on a new user choice.
    fun navigateTo(destination: SettingsPage) {
        focusManager.clearFocus()
        keyboard?.hide()
        actions.snackbar.currentSnackbarData?.dismiss()
        onPageChange(destination)
    }

    BackHandler(enabled = page != SettingsPage.OVERVIEW) { navigateTo(SettingsPage.OVERVIEW) }
    LaunchedEffect(backupFirst, actions.integrity.verified) {
        if (!actions.integrity.verified || (backupFirst && page == SettingsPage.OVERVIEW)) {
            onPageChange(SettingsPage.BACKUP)
        }
    }
    fun save(part: SettingsPart) {
        // Read the latest committed rules when saving. Compose's frame can still be showing a
        // previous snapshot while a background ledger change has already been published.
        val committed = container.state.value ?: state
        when (val checked = input.checkPart(part, committed.pricing, committed.costing, money)) {
            is Checked.Invalid -> {
                errors = checked.errors
                actions.say(checked.errors.values.first())
            }
            is Checked.Valid -> {
                errors = emptyMap()
                actions.run({ it.saveSettings(checked.value.costing, checked.value.pricing, effectiveFrom = today) }) {
                    actions.say("تنظیمات ذخیره شد.")
                }
            }
        }
    }

    val pageTitle = when (page) {
        SettingsPage.OVERVIEW -> "تنظیمات"
        SettingsPage.PRICING -> "قیمت‌گذاری"
        SettingsPage.COSTS -> "هزینه‌ها و فروش آنلاین"
        SettingsPage.SECTIONS -> "بخش‌های منو"
        SettingsPage.CURRENCY -> "واحد پول"
        SettingsPage.BACKUP -> "پشتیبان و بازیابی"
        SettingsPage.HELP -> "راهنما و درباره برنامه"
    }
    TabScaffold(title = pageTitle, nav = nav, actions = actions, navigationIcon = {
        if (page != SettingsPage.OVERVIEW) {
            // This target stays still while the keyboard resizes the scrollable form below it.
            IconButton(onClick = { navigateTo(SettingsPage.OVERVIEW) }, modifier = Modifier.testTag("settings-back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت به تنظیمات")
            }
        }
    }) { padding ->
        Column(
            Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize()
                .verticalScroll(rememberScrollState())
                // Let the last Save control scroll clear of the previous save snackbar and system bar.
                .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (page == SettingsPage.OVERVIEW) {
                Column(Modifier.testTag("settings-overview"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("مدیریت ساده و یکپارچه برنامه", style = MaterialTheme.typography.bodyLarge)
                    if (backupFirst) {
                        OutlinedButton(onClick = { navigateTo(SettingsPage.BACKUP) }) { Text("دیدن راه‌ها در پشتیبان") }
                    }
                    SettingsDestination("قیمت‌گذاری", "روش و دامنه اعمال قیمت", "pricing") { navigateTo(SettingsPage.PRICING) }
                    SettingsDestination("هزینه‌ها و فروش آنلاین", "سربار، کمیسیون و تخفیف", "costs") { navigateTo(SettingsPage.COSTS) }
                    SettingsDestination("بخش‌های منو", "ساخت و دسته‌بندی بخش‌ها", "sections") { navigateTo(SettingsPage.SECTIONS) }
                    SettingsDestination("واحد پول", Labels.currency(currency), "currency") { navigateTo(SettingsPage.CURRENCY) }
                    SettingsDestination("پشتیبان و بازیابی", "حفاظت از اطلاعات", "backup") { navigateTo(SettingsPage.BACKUP) }
                    SettingsDestination("راهنما و درباره برنامه", "معنی واژه‌ها و روش کار با برنامه", "help") { navigateTo(SettingsPage.HELP) }
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(ReferenceIcons.coins, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            Text("واحد پول: ${Labels.currency(currency)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (page == SettingsPage.PRICING) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("دامنه قیمت‌گذاری", style = MaterialTheme.typography.titleMedium)
                    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PricingScopeCard("عمومی", "یک قانون برای همه غذاها", input.scope == PricingScope.GENERAL, "pricing-scope-general") {
                            actions.snackbar.currentSnackbarData?.dismiss()
                            onInputChange(input.copy(scope = PricingScope.GENERAL))
                        }
                        PricingScopeCard("بخش‌های منو", "قانون جداگانه برای هر بخش", input.scope == PricingScope.MENU_SECTIONS, "pricing-scope-sections") {
                            actions.snackbar.currentSnackbarData?.dismiss()
                            onInputChange(input.copy(scope = PricingScope.MENU_SECTIONS))
                        }
                    }
                    if (!generalPricingEnabled) Text("برای غذاها و بخش‌های بدون قانون اختصاصی، آخرین روش عمومی ذخیره‌شده به کار می‌رود.", style = MaterialTheme.typography.bodySmall)
                }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("روش قیمت‌گذاری (حالت عمومی)", style = MaterialTheme.typography.titleSmall)
                // The three are ways to put the same price: switching turns the number into its equivalent.
                SettingsChoices(MethodChoice.entries, input.method, { methodName(it) }, enabled = generalPricingEnabled) { onInputChange(input.withMethod(it)) }
                SettingsTextInput(
                    value = input.percent, compact = true,
                    enabled = generalPricingEnabled,
                    modifier = Modifier.testTag("general-pricing-percent"),
                    onValueChange = { onInputChange(input.copy(percent = it, carried = false, conversionOrigin = null)) },
                    label = methodName(input.method),
                    error = errors[Field.PERCENT],
                    supporting = if (input.carried) SettingsInput.CARRIED_MESSAGE else input.equivalents(),
                    number = true,
                    suffix = "٪",
                )
                if (input.method != MethodChoice.FOOD_COST) {
                    Text("روی چه هزینه‌ای؟", style = MaterialTheme.typography.labelLarge)
                    SettingsChoices(CostBasis.entries, input.basis, { basisName(it) }, enabled = generalPricingEnabled) { onInputChange(input.copy(basis = it, conversionOrigin = null)) }
                    Text(
                        if (input.basis == CostBasis.FOOD_COST) {
                            "فقط بهای مواد؛ هزینه‌های دیگر از مانده کم می‌شوند."
                        } else {
                            "مواد، هزینه‌های دیگر هر غذا و سهم سربار ماهانه. عدد با عوض کردن مبنا همان می‌ماند ولی قیمت‌ها عوض می‌شوند؛ روی همهٔ هزینه‌ها، عدد به سود نزدیک‌تر است و معمولاً خیلی کوچک‌تر از «فقط مواد»."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (input.categories.isNotEmpty()) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("قیمت‌گذاری بخش‌های منو", style = MaterialTheme.typography.titleSmall)
                            Text(if (generalPricingEnabled) "با انتخاب حالت بخش‌های منو فعال می‌شود" else "قانون هر بخش در تنظیمات بخش‌های منو", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            input.categories.filter { it.name.isNotBlank() }.forEach { category ->
                                Row(Modifier.fillMaxWidth()) {
                                    Text(category.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${Persian.digits(category.percent)}٪", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                SettingsNotice("مقادیر قبلی حفظ می‌شوند؛ فقط حالت انتخاب‌شده اعمال می‌شود.")
                TextButton(onClick = { pricingDetails = !pricingDetails }, modifier = Modifier.testTag("pricing-tax-details")) {
                    Text(if (pricingDetails) "بستن مالیات و گرد کردن" else "مالیات و گرد کردن قیمت")
                }
                if (pricingDetails) {
                SettingsTextInput(
                    value = input.vatPercent,
                    onValueChange = { onInputChange(input.copy(vatPercent = it)) },
                    label = "مالیات بر ارزش افزوده",
                    error = errors[Field.VAT],
                    number = true,
                    suffix = "٪",
                )
                SwitchRow("قیمت‌های منو با مالیات است", input.menuPriceIncludesVat) {
                    onInputChange(input.copy(menuPriceIncludesVat = it))
                }
                Text("گرد کردن قیمت پیشنهادی", style = MaterialTheme.typography.labelLarge)
                ChoiceChips(SettingsInput.ROUNDING_STEPS, input.roundingStepRials, { money.full(it) }) {
                    onInputChange(input.copy(roundingStepRials = it))
                }
                Segmented(PriceRounding.Direction.entries, input.roundingDirection, { Labels.direction(it) }) {
                    onInputChange(input.copy(roundingDirection = it))
                }

                }

            }


                Button(onClick = { save(SettingsPart.PRICING) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("settings-save-pricing"), shape = RoundedCornerShape(12.dp)) { Text("ذخیرهٔ تغییرات", style = MaterialTheme.typography.titleMedium) }
            }
            if (page == SettingsPage.SECTIONS) {
            SettingsCard("بخش‌های منو") {
                if (input.scope != state.pricing.scope) {
                    SettingsNotice("تغییر دامنه هنوز ذخیره نشده است؛ ابتدا آن را در قیمت‌گذاری ذخیره کنید. این صفحه از حالت ذخیره‌شده پیروی می‌کند.")
                }
                Text(
                    "بخش‌های منو را اینجا بسازید تا غذاها را در آن‌ها بگذارید. هر بخش می‌تواند فودکاست هدف، حاشیهٔ سود یا مارک‌آپ مخصوص داشته باشد، " +
                        "یا مثل بقیه با روش عمومی بالا قیمت بخورد. برای غذای بدون بخش هم روش عمومی بالا اعمال می‌شود.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!sectionPricingEnabled) {
                    SettingsNotice("قیمت‌گذاری بخش‌ها در حالت عمومی غیرفعال است؛ نام بخش‌ها قابل ویرایش است و درصدهای قبلی حفظ می‌شوند.")
                }
                input.categories.forEachIndexed { i, category ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsTextInput(
                            value = category.name,
                            onValueChange = { onInputChange(input.withCategory(i, category.copy(name = it))) },
                            label = "نام بخش",
                            modifier = Modifier.weight(1.4f).focusRequester(categoryName(i)).testTag("category-$i-name"),
                            imeAction = ImeAction.Next,
                        )
                        if (!category.useGeneral) {
                            SettingsTextInput(
                                value = category.percent,
                                enabled = sectionPricingEnabled,
                                onValueChange = { onInputChange(input.withCategory(i, category.copy(percent = it))) },
                                label = methodName(category.method),
                                modifier = Modifier.weight(1f).focusRequester(categoryPercent(i)).testTag("category-$i-percent"),
                                number = true,
                                suffix = "٪",
                                imeAction = ImeAction.Done,
                            )
                        }
                        IconButton(onClick = { onInputChange(input.withoutCategory(i)) }) {
                            Icon(Icons.Filled.Close, contentDescription = "حذف بخش")
                        }
                    }
                    Text("روش قیمت‌گذاری «${category.name.ifBlank { "بخش جدید" }}»", style = MaterialTheme.typography.labelMedium)
                    // null: priced like the rest, with the general method above.
                    val methods: List<MethodChoice?> = listOf(null) + MethodChoice.entries
                    SettingsChoices(methods, category.method.takeIf { !category.useGeneral }, { it?.let(::methodName) ?: "روش عمومی" }, enabled = sectionPricingEnabled) { chosen ->
                        val next = when {
                            chosen == null -> category.copy(useGeneral = true)
                            category.useGeneral || chosen != category.method ->
                                category.copy(useGeneral = false, method = chosen, percent = "", basis = CostBasis.FOOD_COST)
                            else -> category
                        }
                        if (next != category) onInputChange(input.withCategory(i, next))
                    }
                    when {
                        category.useGeneral -> Text(
                            "این بخش مثل بقیهٔ غذاها با روش عمومی بالا قیمت می‌خورد و برای دسته‌بندی غذاهاست.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        category.method == MethodChoice.FOOD_COST ->
                            Text("مبنای فودکاست هدف: فقط هزینهٔ مواد اولیه", style = MaterialTheme.typography.bodySmall)
                        else -> {
                            Text("مبنای محاسبه درصد", style = MaterialTheme.typography.labelMedium)
                            SettingsChoices(CostBasis.entries, category.basis, { basisName(it) }, enabled = sectionPricingEnabled) { chosen ->
                                onInputChange(input.withCategory(i, category.copy(basis = chosen)))
                            }
                        }
                    }
                }
                errors[Field.CATEGORY]?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    input.categoryPresets().forEach { name ->
                        AssistChip(
                            onClick = {
                                focusCategory = input.categories.size to sectionPricingEnabled
                                onInputChange(input.plusCategory(name).let { added ->
                                    if (sectionPricingEnabled) added else added.withCategory(added.categories.lastIndex, added.categories.last().copy(useGeneral = true))
                                })
                            },
                            label = { Text(name) },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        )
                    }
                    AssistChip(
                        onClick = {
                            focusCategory = input.categories.size to false
                            onInputChange(input.plusCategory("").let { added -> if (sectionPricingEnabled) added else added.withCategory(added.categories.lastIndex, added.categories.last().copy(useGeneral = true)) })
                        },
                        label = { Text("بخش دیگر") },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    )
                }
                // A new section takes the keyboard: on its target when it came with a name, else on its name.
                LaunchedEffect(focusCategory, input.categories.size) {
                    val (i, named) = focusCategory ?: return@LaunchedEffect
                    if (i < input.categories.size) {
                        runCatching { (if (named) categoryPercent(i) else categoryName(i)).requestFocus() }
                        focusCategory = null
                    }
                }
            }


                Button(onClick = { save(SettingsPart.SECTIONS) }, modifier = Modifier.fillMaxWidth().testTag("settings-save-sections")) { Text("ذخیرهٔ بخش‌های منو") }
            }
            if (page == SettingsPage.COSTS) {
            SettingsCard("هزینه‌های جزئی") {
                SettingsTextInput(
                    value = input.miscPercent,
                    onValueChange = { onInputChange(input.copy(miscPercent = it)) },
                    label = "هزینه‌های جزئی پیش‌فرض",
                    error = errors[Field.MISC],
                    supporting = "نمک، ادویه، روغن سرخ کردن و چیزهایی که وزن نمی‌شوند.",
                    number = true,
                    suffix = "٪",
                )
            }

            SettingsCard("هزینه‌های ثابت ماهانه") {
                Text(
                    "حقوق، اجاره، گاز و برق و بقیهٔ هزینه‌های ثابت یک ماه، تقسیم بر پرس‌هایی که در ماه فروخته می‌شود، سهم هر پرس از سربار است. اختیاری است.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                SettingsTextInput(
                    value = input.monthlyFixed,
                    onValueChange = { onInputChange(input.copy(monthlyFixed = it)) },
                    label = "جمع هزینه‌های ثابت ماه",
                    error = errors[Field.FIXED_COSTS],
                    number = true,
                    suffix = money.unit,
                )
                SettingsTextInput(
                    value = input.monthlyPortions,
                    onValueChange = { onInputChange(input.copy(monthlyPortions = it)) },
                    label = "پرس‌های فروخته‌شده در ماه",
                    error = errors[Field.MONTHLY_PORTIONS],
                    supporting = input.overheadShare(money)?.let { "سهم هر پرس: $it" },
                    number = true,
                )
                Text(
                    "این سهم در «مانده» هر غذا کم می‌شود. قیمت پیشنهادی فقط وقتی آن را حساب می‌کند که حاشیه یا مارک‌آپ روی همهٔ هزینه‌ها باشد.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsCard("فروش آنلاین") {
                Text(
                    "اگر غذا از راه سامانه‌های سفارش آنلاین هم فروخته می‌شود، ماشین‌حساب کنار فروش حضوری، مانده‌اش را هم نشان می‌دهد.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                SettingsTextInput(
                    value = input.onlineCommission,
                    onValueChange = { onInputChange(input.copy(onlineCommission = it)) },
                    label = "کمیسیون سامانه",
                    error = errors[Field.COMMISSION],
                    supporting = "درصدی از فروش خالص پس از تخفیف",
                    number = true,
                    suffix = "٪",
                )
                SettingsTextInput(
                    value = input.onlineDiscount,
                    onValueChange = { onInputChange(input.copy(onlineDiscount = it)) },
                    label = "تخفیف معمول آنلاین",
                    error = errors[Field.DISCOUNT],
                    number = true,
                    suffix = "٪",
                )
                SettingsTextInput(
                    value = input.onlineUplift,
                    onValueChange = { onInputChange(input.copy(onlineUplift = it)) },
                    label = "قیمت آنلاین چند درصد بیشتر از منو",
                    error = errors[Field.UPLIFT],
                    number = true,
                    suffix = "٪",
                )
            }


                Button(onClick = { save(SettingsPart.COSTS) }, modifier = Modifier.fillMaxWidth().testTag("settings-save-costs")) { Text("ذخیرهٔ هزینه‌ها و فروش آنلاین") }
            }
            if (page == SettingsPage.CURRENCY) {
            SettingsCard("واحد پول") {
                Segmented(listOf(DisplayCurrency.TOMAN, DisplayCurrency.RIAL), currency, { Labels.currency(it) }) { container.setCurrency(it) }
                Text(
                    "مبلغ‌ها همیشه به ریال ذخیره می‌شوند؛ این فقط شکل نمایش و ورود مبلغ است.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }


            }
            if (page == SettingsPage.BACKUP) BackupCard(container, today, actions)
            if (page == SettingsPage.HELP) {
            SettingsCard("راهنما") {
                Text(
                    "معنی واژه‌هایی مثل فودکاست، مارجین، مارک‌آپ و بهای تمام‌شده، و راهنمای کار با برنامه.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { nav.open(Screen.Help(Screen.Help.TERMS)) }) { Text("واژه‌ها") }
                    OutlinedButton(onClick = { nav.open(Screen.Help(Screen.Help.GUIDE)) }) { Text("کار با برنامه") }
                }
            }

            SettingsCard("دربارهٔ برنامه") {
                Text("فودکاست، نسخهٔ ${Persian.digits(versionLabel(context))}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "فودکاست برای یک نفر روی یک گوشی است: هر تغییری به نام مالک ثبت می‌شود و کاربر یا نقش جدا ندارد. " +
                        "اگر چند نفر با یک گوشی کار می‌کنند، برنامه نمی‌گوید کدام تغییر را چه کسی داده است.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "قلم وزیرمتن، با مجوز SIL Open Font License",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            }
        }
    }
}

@Composable
private fun SettingsDestination(title: String, subtitle: String, tag: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("settings-section-$tag"),
        shape = RoundedCornerShape(14.dp), colors = whiteCardColors(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(when (tag) {
                "costs" -> ReferenceIcons.coins; "sections" -> ReferenceIcons.grid
                "currency" -> ReferenceIcons.money; "backup" -> ReferenceIcons.backup
                "help" -> ReferenceIcons.info; else -> ReferenceIcons.tag
            }, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("‹", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun PricingScopeCard(title: String, subtitle: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tag), shape = RoundedCornerShape(14.dp),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RadioButton(selected = selected, onClick = null)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SettingsNotice(text: String) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun <T> SettingsChoices(values: List<T>, selected: T, label: (T) -> String, enabled: Boolean, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value -> FilterChip(selected = value == selected, onClick = { onSelect(value) }, enabled = enabled, label = { Text(label(value)) }) }
    }
}

@Composable
private fun SettingsTextInput(
    value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    error: String? = null, supporting: String? = null, number: Boolean = false, suffix: String? = null,
    imeAction: ImeAction = ImeAction.Default, enabled: Boolean = true, compact: Boolean = false,
) {
    if (enabled && compact) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            ReferenceField(value, onValueChange, label, modifier, number = number, suffix = suffix, imeAction = imeAction, height = 40.dp)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            supporting?.let {
                val palette = ir.foodcost.app.ui.theme.statusColors(ir.foodcost.core.pricing.PriceStatus.NEAR)
                Surface(color = palette.container, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = palette.content, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                }
            }
        }
    } else if (enabled) {
        TextInput(value, onValueChange, label, modifier, error, supporting, number, suffix, imeAction)
    } else {
        OutlinedTextField(value = value, onValueChange = {}, enabled = false, singleLine = true,
            label = { Text(label) }, modifier = modifier.fillMaxWidth(),
            suffix = if (suffix != null) { { Text(suffix) } } else null,
            supportingText = { Text("برای ویرایش، حالت قیمت‌گذاری مربوط را فعال کنید؛ مقدار قبلی حفظ شده است.") })
    }
}

private fun methodName(method: MethodChoice): String = when (method) {
    MethodChoice.FOOD_COST -> "فودکاست هدف"
    MethodChoice.MARGIN -> "حاشیهٔ سود"
    MethodChoice.MARKUP -> "مارک‌آپ"
}

private fun basisName(basis: CostBasis): String = when (basis) {
    CostBasis.FOOD_COST -> "فقط مواد"
    CostBasis.FULL_COST -> "همهٔ هزینه‌ها"
}

private fun methodHint(method: MethodChoice): String = when (method) {
    MethodChoice.FOOD_COST -> "بهای مواد چند درصد قیمت فروش باشد؟ رستوران‌ها معمولاً ۲۸ تا ۳۵."
    MethodChoice.MARGIN -> "چند درصد قیمت فروش (بدون مالیات) سود ناخالص بماند؟"
    MethodChoice.MARKUP -> "چند درصد روی بها کشیده شود؟ ۲۰۰ یعنی سه برابر بها."
}

@Suppress("DEPRECATION")
private fun versionLabel(context: Context): String {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    return "${info.versionName.orEmpty()} (${info.versionCode})"
}
