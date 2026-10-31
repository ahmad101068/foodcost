package ir.foodcost.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.foodcost.app.AppContainer
import ir.foodcost.core.money.DisplayCurrency
import ir.foodcost.core.money.PriceRounding
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
    var input by remember(state.pricing, state.costing) { mutableStateOf(SettingsInput.of(state.pricing, state.costing)) }
    var errors by remember { mutableStateOf(emptyMap<Field, String>()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
                Segmented(MethodChoice.entries, input.method, { methodName(it) }) { input = input.copy(method = it) }
                TextInput(
                    value = input.percent,
                    onValueChange = { input = input.copy(percent = it) },
                    label = methodName(input.method),
                    error = errors[Field.PERCENT],
                    supporting = methodHint(input.method),
                    number = true,
                    suffix = "٪",
                )
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
                Button(
                    onClick = {
                        when (val checked = input.check(state.pricing, state.costing)) {
                            is Checked.Invalid -> errors = checked.errors
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
            }

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
