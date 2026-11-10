package ir.foodcost.app.ui

import android.content.Context
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ArrowForward
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ir.foodcost.app.AppContainer
import ir.foodcost.core.money.DisplayCurrency
import ir.foodcost.core.money.PriceRounding
import ir.foodcost.core.pricing.CostBasis
import ir.foodcost.data.ledger.LedgerState
import ir.foodcost.presentation.Checked
import ir.foodcost.presentation.Field
import ir.foodcost.presentation.Labels
import ir.foodcost.presentation.MethodChoice
import ir.foodcost.presentation.MoneyFormat
import ir.foodcost.presentation.Numbers
import ir.foodcost.presentation.Persian
import ir.foodcost.presentation.SettingsInput
import ir.foodcost.presentation.SettingsArea
import java.time.LocalDate

/** The settings tab: currency, pricing rules and backup. */
@Composable
fun SettingsScreen(
    container: AppContainer,
    state: LedgerState,
    money: MoneyFormat,
    today: LocalDate,
    nav: Navigator,
    actions: AppActions,
) {
    val currency by container.currency.collectAsState()
    var input by remember(state.pricing, state.costing, money) { mutableStateOf(SettingsInput.of(state.pricing, state.costing, money)) }
    val persisted = remember(state.pricing, state.costing, money) { SettingsInput.of(state.pricing, state.costing, money) }
    var errors by remember { mutableStateOf(emptyMap<Field, String>()) }
    var section by rememberSaveable { mutableStateOf("خانه") }
    // Intercept the system back button while inside settings, before the app-level handler
    // navigates to the calculator. The on-screen return button uses the same destination.
    BackHandler(enabled = section != "خانه") { section = "خانه" }
    val context = LocalContext.current
    val categoryNames = remember { HashMap<Int, FocusRequester>() }
    fun categoryName(i: Int): FocusRequester = categoryNames.getOrPut(i) { FocusRequester() }
    var focusCategory by remember { mutableStateOf<Int?>(null) }

    TabScaffold(title = "تنظیمات", nav = nav, actions = actions) { padding ->
        // While the data waits for the user's choice, or a backup is due, the backup card comes first:
        // that is what the line on top of the screens leads here for.
        val backupFirst = !actions.integrity.verified || actions.backupReminder != null
        Column(
            Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (backupFirst) BackupCard(container, today, actions)
            if (section == "خانه") {
                SettingsCard("وضعیت فعلی قیمت‌گذاری") {
                    Text("روش عمومی ذخیره‌شده: ${methodName(persisted.method)}", style = MaterialTheme.typography.titleMedium)
                    Text("درصد: ${persisted.percent}٪   |   مبنا: ${basisName(persisted.basis)}")
                    Text("مالیات: ${persisted.vatPercent}٪", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("بخش‌های منو: ${state.pricing.sections.size} (فقط دسته‌بندی)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (input != persisted) {
                        Text("ویرایش‌هایی در تنظیمات وجود دارد که هنوز ذخیره نشده‌اند.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedButton(onClick = { section = "قیمت‌گذاری" }, modifier = Modifier.fillMaxWidth()) {
                        Text("ویرایش قیمت‌گذاری")
                    }
                }
                listOf(
                    Triple("قیمت‌گذاری", "فودکاست، مارجین، مارک‌آپ، مالیات و گرد کردن", Icons.Filled.Settings),
                    Triple("دسته‌بندی منو", "بخش‌بندی غذاها بدون قیمت‌گذاری جداگانه", Icons.Filled.List),
                    Triple("فروش آنلاین", "کمیسیون، تخفیف و افزایش قیمت", Icons.Filled.ShoppingCart),
                    Triple("هزینه‌های ثابت", "سربار ماهانه و سهم هر پرس", Icons.Filled.ShoppingCart),
                    Triple("پشتیبان‌گیری", "ساخت و بازگرداندن فایل اطلاعات", Icons.Filled.Info),
                    Triple("عمومی و راهنما", "واحد پول، راهنما و اطلاعات برنامه", Icons.Filled.Info),
                ).forEach { (name, description, symbol) ->
                    Card(
                        onClick = { section = name },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(symbol, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.titleMedium)
                                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Filled.ArrowForward, contentDescription = null)
                        }
                    }
                }
            } else {
                OutlinedButton(onClick = { section = "خانه" }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.ArrowForward, contentDescription = null)
                    Text("بازگشت به تنظیمات")
                }
            }
            if (!backupFirst && section == "پشتیبان‌گیری") BackupCard(container, today, actions)

            if (section == "عمومی و راهنما") SettingsCard("راهنما") {
                Text(
                    "معنی واژه‌هایی مثل فودکاست، مارجین، مارک‌آپ و بهای تمام‌شده، و راهنمای کار با برنامه.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { nav.open(Screen.Help(Screen.Help.TERMS)) }) { Text("واژه‌ها") }
                    OutlinedButton(onClick = { nav.open(Screen.Help(Screen.Help.GUIDE)) }) { Text("کار با برنامه") }
                }
            }

            if (section == "عمومی و راهنما") SettingsCard("واحد پول") {
                Segmented(listOf(DisplayCurrency.RIAL, DisplayCurrency.TOMAN), currency, { Labels.currency(it) }) { container.setCurrency(it) }
                Text(
                    "مبلغ‌ها همیشه به ریال ذخیره می‌شوند؛ این فقط شکل نمایش و ورود مبلغ است.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (section == "قیمت‌گذاری") SettingsCard("قیمت‌گذاری") {
                // The three are ways to put the same price: switching turns the number into its equivalent.
                Segmented(MethodChoice.entries, input.method, { methodName(it) }) { input = input.withMethod(it) }
                TextInput(
                    value = input.percent,
                    onValueChange = { input = input.copy(percent = it, carried = false) },
                    label = methodName(input.method),
                    error = errors[Field.PERCENT],
                    supporting = listOfNotNull(
                        methodHint(input.method),
                        // From a margin or markup on every cost there is no food cost equivalent: the number is the old one.
                        if (input.carried) "این عدد از روش قبلی مانده است. فودکاست فقط بهای مواد را می‌سنجد و با «همهٔ هزینه‌ها» برابری ثابتی ندارد؛ عدد را دوباره بنویسید." else input.equivalents(),
                    ).joinToString("\n"),
                    number = true,
                    suffix = "٪",
                )
                val equivalents = input.equivalentPercentages()
                if (equivalents != null) {
                    Text("معادل‌های خودکار درصد", style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MethodChoice.entries.forEach { choice ->
                            Card(
                                modifier = Modifier.weight(1f).testTag("pricing-rate-${choice.name.lowercase()}"),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (choice == input.method) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    },
                                ),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(methodName(choice), style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        equivalents[choice]?.let { Numbers.percent(it) } ?: "—",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                    if (input.method != MethodChoice.FOOD_COST && input.basis == CostBasis.FULL_COST) {
                        Text(
                            "فودکاست واقعی از حاشیه سود روی همه هزینه‌ها به‌تنهایی به دست نمی‌آید؛ برای محاسبه آن سهم مواد اولیه نیز لازم است.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (input.percent.isNotBlank() && !input.carried) {
                    Text("برای مشاهده معادل‌ها، درصد معتبر وارد کنید.", style = MaterialTheme.typography.bodySmall)
                }

                if (input.method != MethodChoice.FOOD_COST) {
                    Text("روی چه هزینه‌ای؟", style = MaterialTheme.typography.labelLarge)
                    Segmented(CostBasis.entries, input.basis, { basisName(it) }) { input = input.copy(basis = it) }
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
                TextInput(
                    value = input.vatPercent,
                    onValueChange = { input = input.copy(vatPercent = it) },
                    label = "مالیات بر ارزش افزوده",
                    error = errors[Field.VAT],
                    number = true,
                    suffix = "٪",
                )
                SwitchRow("قیمت‌های منو با مالیات است", input.menuPriceIncludesVat) {
                    input = input.copy(menuPriceIncludesVat = it)
                }
                Text("گرد کردن قیمت پیشنهادی", style = MaterialTheme.typography.labelLarge)
                ChoiceChips(SettingsInput.ROUNDING_STEPS, input.roundingStepRials, { money.full(it) }) {
                    input = input.copy(roundingStepRials = it)
                }
                Segmented(PriceRounding.Direction.entries, input.roundingDirection, { Labels.direction(it) }) {
                    input = input.copy(roundingDirection = it)
                }
                TextInput(
                    value = input.miscPercent,
                    onValueChange = { input = input.copy(miscPercent = it) },
                    label = "هزینه‌های جزئی پیش‌فرض",
                    error = errors[Field.MISC],
                    supporting = "نمک، ادویه، روغن سرخ کردن و چیزهایی که وزن نمی‌شوند.",
                    number = true,
                    suffix = "٪",
                )
            }

            if (section == "دسته‌بندی منو") SettingsCard("دسته‌بندی منو") {
                Text(
                    "بخش‌های منو فقط برای مرتب‌کردن غذاها هستند. تمام غذاها، با هر نام بخشی، از روش و درصد قیمت‌گذاری عمومی استفاده می‌کنند.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                input.categories.forEachIndexed { i, category ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextInput(
                            value = category.name,
                            onValueChange = { input = input.withCategory(i, category.copy(name = it)) },
                            label = "نام بخش",
                            modifier = Modifier.weight(1f).focusRequester(categoryName(i)).testTag("category-$i-name"),
                            imeAction = ImeAction.Done,
                        )
                        IconButton(onClick = { input = input.withoutCategory(i) }) {
                            Icon(Icons.Filled.Close, contentDescription = "حذف بخش")
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
                                focusCategory = input.categories.size
                                input = input.plusCategory(name)
                            },
                            label = { Text(name) },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        )
                    }
                    AssistChip(
                        onClick = {
                            focusCategory = input.categories.size
                            input = input.plusCategory("")
                        },
                        label = { Text("بخش دیگر") },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    )
                }
                LaunchedEffect(focusCategory, input.categories.size) {
                    val i = focusCategory ?: return@LaunchedEffect
                    if (i < input.categories.size) {
                        runCatching { categoryName(i).requestFocus() }
                        focusCategory = null
                    }
                }
            }

            if (section == "هزینه‌های ثابت") SettingsCard("هزینه‌های ثابت ماهانه") {
                Text(
                    "حقوق، اجاره، گاز و برق و بقیهٔ هزینه‌های ثابت یک ماه، تقسیم بر پرس‌هایی که در ماه فروخته می‌شود، سهم هر پرس از سربار است. اختیاری است.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextInput(
                    value = input.monthlyFixed,
                    onValueChange = { input = input.copy(monthlyFixed = it) },
                    label = "جمع هزینه‌های ثابت ماه",
                    error = errors[Field.FIXED_COSTS],
                    number = true,
                    suffix = money.unit,
                )
                TextInput(
                    value = input.monthlyPortions,
                    onValueChange = { input = input.copy(monthlyPortions = it) },
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

            if (section == "فروش آنلاین") SettingsCard("فروش آنلاین") {
                Text(
                    "اگر غذا از راه سامانه‌های سفارش آنلاین هم فروخته می‌شود، ماشین‌حساب کنار فروش حضوری، مانده‌اش را هم نشان می‌دهد.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextInput(
                    value = input.onlineCommission,
                    onValueChange = { input = input.copy(onlineCommission = it) },
                    label = "کمیسیون سامانه",
                    error = errors[Field.COMMISSION],
                    supporting = "درصدی از فروش خالص پس از تخفیف",
                    number = true,
                    suffix = "٪",
                )
                TextInput(
                    value = input.onlineDiscount,
                    onValueChange = { input = input.copy(onlineDiscount = it) },
                    label = "تخفیف معمول آنلاین",
                    error = errors[Field.DISCOUNT],
                    number = true,
                    suffix = "٪",
                )
                TextInput(
                    value = input.onlineUplift,
                    onValueChange = { input = input.copy(onlineUplift = it) },
                    label = "قیمت آنلاین چند درصد بیشتر از منو",
                    error = errors[Field.UPLIFT],
                    number = true,
                    suffix = "٪",
                )
            }

            // Each section commits only its own fields. Pending edits elsewhere are excluded.
            if (section in listOf("قیمت‌گذاری", "دسته‌بندی منو", "هزینه‌های ثابت", "فروش آنلاین")) Button(
                onClick = {
                    val area = when (section) {
                        "قیمت‌گذاری" -> SettingsArea.GENERAL_PRICING
                        "دسته‌بندی منو" -> SettingsArea.MENU_SECTIONS
                        "هزینه‌های ثابت" -> SettingsArea.FIXED_COSTS
                        else -> SettingsArea.ONLINE
                    }
                    val scoped = input.forSaveArea(area, state.pricing, state.costing, money)
                    when (val checked = scoped.check(state.pricing, state.costing, money)) {
                        is Checked.Invalid -> {
                            errors = checked.errors
                            // The field may be scrolled out of sight above the button.
                            actions.say(checked.errors.values.first())
                        }
                        is Checked.Valid -> {
                            errors = emptyMap()
                            actions.run({ it.saveSettings(checked.value.costing, checked.value.pricing, effectiveFrom = today) }) {
                                actions.say("تنظیمات ذخیره شد.")
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (section == "دسته‌بندی منو") "ذخیرهٔ دسته‌بندی" else "ذخیرهٔ قیمت‌گذاری") }



            if (section == "عمومی و راهنما") SettingsCard("دربارهٔ برنامه") {
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
