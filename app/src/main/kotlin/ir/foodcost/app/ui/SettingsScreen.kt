package ir.foodcost.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.rememberCoroutineScope
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
import ir.foodcost.data.json.JsonException
import ir.foodcost.data.ledger.LedgerState
import ir.foodcost.presentation.Checked
import ir.foodcost.presentation.Field
import ir.foodcost.presentation.JalaliDate
import ir.foodcost.presentation.Labels
import ir.foodcost.presentation.MethodChoice
import ir.foodcost.presentation.MoneyFormat
import ir.foodcost.presentation.Persian
import ir.foodcost.presentation.SettingsInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    var errors by remember { mutableStateOf(emptyMap<Field, String>()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val categoryNames = remember { HashMap<Int, FocusRequester>() }
    val categoryPercents = remember { HashMap<Int, FocusRequester>() }
    fun categoryName(i: Int): FocusRequester = categoryNames.getOrPut(i) { FocusRequester() }
    fun categoryPercent(i: Int): FocusRequester = categoryPercents.getOrPut(i) { FocusRequester() }
    var focusCategory by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = try {
                    withContext(Dispatchers.IO) { writeText(context, uri, container.open().exportBackup()) }
                } catch (e: Exception) {
                    false
                }
                actions.say(if (saved) "پشتیبان ذخیره شد." else "پشتیبان ذخیره نشد.")
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val message = try {
                    val text = withContext(Dispatchers.IO) { readText(context, uri) }
                    if (text == null) {
                        "فایل خوانده نشد."
                    } else {
                        val added = container.change { it.importBackup(text) }
                        if (added == 0) "این پشتیبان چیز تازه‌ای نداشت." else "${Persian.digits(added)} تغییر از پشتیبان اضافه شد."
                    }
                } catch (e: JsonException) {
                    "این فایل پشتیبان معتبر نیست."
                } catch (e: Exception) {
                    "فایل خوانده نشد."
                }
                actions.say(message)
            }
        }
    }

    TabScaffold(title = "تنظیمات", nav = nav, actions = actions) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsCard("واحد پول") {
                Segmented(DisplayCurrency.entries, currency, { Labels.currency(it) }) { container.setCurrency(it) }
                Text(
                    "مبلغ‌ها همیشه به ریال ذخیره می‌شوند؛ این فقط شکل نمایش و ورود مبلغ است.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsCard("قیمت‌گذاری") {
                // The three are ways to put the same price: switching turns the number into its equivalent.
                Segmented(MethodChoice.entries, input.method, { methodName(it) }) { input = input.withMethod(it) }
                TextInput(
                    value = input.percent,
                    onValueChange = { input = input.copy(percent = it) },
                    label = methodName(input.method),
                    error = errors[Field.PERCENT],
                    supporting = listOfNotNull(methodHint(input.method), input.equivalents()).joinToString("\n"),
                    number = true,
                    suffix = "٪",
                )
                if (input.method != MethodChoice.FOOD_COST) {
                    Text("روی چه هزینه‌ای؟", style = MaterialTheme.typography.labelLarge)
                    Segmented(CostBasis.entries, input.basis, { basisName(it) }) { input = input.copy(basis = it) }
                    Text(
                        if (input.basis == CostBasis.FOOD_COST) {
                            "فقط بهای مواد؛ هزینه‌های دیگر از مانده کم می‌شوند."
                        } else {
                            "مواد، هزینه‌های دیگر هر غذا و سهم سربار ماهانه."
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

            SettingsCard("فودکاست هدف هر بخش منو") {
                Text(
                    "برای بخش‌هایی که فودکاستشان با بقیه فرق دارد؛ نوشیدنی‌ها معمولاً فودکاست کمتری از غذا دارند. بخش هر غذا در ماشین‌حساب انتخاب می‌شود و غذای بی‌بخش با قیمت‌گذاری بالا حساب می‌شود.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                input.categories.forEachIndexed { i, category ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextInput(
                            value = category.name,
                            onValueChange = { input = input.withCategory(i, category.copy(name = it)) },
                            label = "نام بخش",
                            modifier = Modifier.weight(1.4f).focusRequester(categoryName(i)).testTag("category-$i-name"),
                            imeAction = ImeAction.Next,
                        )
                        TextInput(
                            value = category.percent,
                            onValueChange = { input = input.withCategory(i, category.copy(percent = it)) },
                            label = "فودکاست هدف",
                            modifier = Modifier.weight(1f).focusRequester(categoryPercent(i)).testTag("category-$i-percent"),
                            number = true,
                            suffix = "٪",
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
                                focusCategory = input.categories.size to true
                                input = input.plusCategory(name)
                            },
                            label = { Text(name) },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        )
                    }
                    AssistChip(
                        onClick = {
                            focusCategory = input.categories.size to false
                            input = input.plusCategory("")
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

            SettingsCard("هزینه‌های ثابت ماهانه") {
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

            SettingsCard("فروش آنلاین") {
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

            // One button for the three cards above: pricing, fixed costs and online sales.
            Button(
                onClick = {
                    when (val checked = input.check(state.pricing, state.costing, money)) {
                        is Checked.Invalid -> {
                            errors = checked.errors
                            // The field may be scrolled out of sight above the button.
                            actions.say(checked.errors.values.first())
                        }
                        is Checked.Valid -> {
                            errors = emptyMap()
                            actions.run({ it.saveSettings(checked.value.costing, checked.value.pricing) }) {
                                actions.say("تنظیمات ذخیره شد.")
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("ذخیرهٔ قیمت‌گذاری") }

            SettingsCard("پشتیبان") {
                Text(
                    "از همهٔ داده‌ها یک فایل پشتیبان بسازید و جای امنی نگه دارید. با همین فایل داده‌ها روی گوشی دیگر هم برمی‌گردند.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportLauncher.launch("foodcost-${JalaliDate.of(today).isoLike()}.json") }) {
                        Text("ساخت پشتیبان")
                    }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Text("بازگرداندن")
                    }
                }
                val unreadable = container.unreadableRows
                if (unreadable > 0) {
                    Text(
                        "${Persian.digits(unreadable)} ردیف ذخیره‌شده خوانده نشد.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            SettingsCard("دربارهٔ برنامه") {
                Text("فودکاست، نسخهٔ ${Persian.digits(versionLabel(context))}", style = MaterialTheme.typography.bodyMedium)
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

private fun writeText(context: Context, uri: Uri, text: String): Boolean =
    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null

private fun readText(context: Context, uri: Uri): String? =
    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }

@Suppress("DEPRECATION")
private fun versionLabel(context: Context): String {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    return "${info.versionName.orEmpty()} (${info.versionCode})"
}
