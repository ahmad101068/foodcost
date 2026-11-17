package ir.foodcost.app.ui

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.foodcost.app.ui.theme.premiumWarningColors
import ir.foodcost.core.money.DisplayCurrency
import ir.foodcost.core.money.PriceRounding
import ir.foodcost.core.pricing.CostBasis
import ir.foodcost.core.pricing.PricingScope
import ir.foodcost.presentation.*
import java.math.BigDecimal

@Composable
fun PremiumSettingsOverview(
    currency: DisplayCurrency,
    input: SettingsInput,
    onInputChange: (SettingsInput) -> Unit,
    savePricing: (SettingsInput) -> Unit,
    navigate: (SettingsPage) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    FoodCostCard(Modifier.testTag("settings-general-group")) {
        Column(Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp)) {
            FoodCostSectionHeader("عمومی", ReferenceIcons.sliders)
            FoodCostSettingsRow(
                "قیمت‌گذاری عمومی",
                "تنظیم درصد هدف و روش محاسبه",
                ReferenceIcons.tag,
                { navigate(SettingsPage.PRICING) },
                Modifier.testTag("settings-section-pricing"),
                verticalPadding = 6.5.dp,
            )
            HorizontalDivider(
                Modifier.padding(horizontal = 4.dp),
                color = scheme.outlineVariant,
                thickness = .6.dp,
            )
            FoodCostSettingsRow(
                "هزینه‌های جانبی",
                "تعریف و مدیریت هزینه‌های ثابت و متغیر",
                ReferenceIcons.coins,
                { navigate(SettingsPage.COSTS) },
                Modifier.testTag("settings-section-costs"),
                verticalPadding = 6.5.dp,
            )
            HorizontalDivider(
                Modifier.padding(horizontal = 4.dp),
                color = scheme.outlineVariant,
                thickness = .6.dp,
            )
            FoodCostSettingsRow(
                "واحد پول",
                "انتخاب واحد نمایش قیمت‌ها",
                ReferenceIcons.money,
                { navigate(SettingsPage.CURRENCY) },
                Modifier.testTag("settings-section-currency"),
                verticalPadding = 6.5.dp,
            ) {
                Surface(color = scheme.primaryContainer, shape = RoundedCornerShape(50)) {
                    Text(
                        Labels.currency(currency),
                        Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            HorizontalDivider(
                Modifier.padding(horizontal = 4.dp),
                color = scheme.outlineVariant,
                thickness = .6.dp,
            )
            // The real VAT control lives in the pricing draft and uses its existing save
            // validation.
            FoodCostSettingsRow(
                "نمایش مالیات در قیمت نهایی",
                "اعمال درصد مالیات در محاسبه قیمت فروش",
                ReferenceIcons.receipt,
                { navigate(SettingsPage.PRICING) },
                verticalPadding = 6.5.dp,
            ) {
                FoodCostSwitch(
                    checked = input.menuPriceIncludesVat,
                    onCheckedChange = {
                        val next = input.copy(menuPriceIncludesVat = it)
                        onInputChange(next)
                        savePricing(next)
                    },
                    modifier = Modifier.size(width = 42.dp, height = 30.dp),
                )
            }
        }
    }
    FoodCostCard(Modifier.testTag("settings-data-group")) {
        Column(Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp)) {
            FoodCostSectionHeader("داده‌ها")
            FoodCostSettingsRow(
                "پشتیبان‌گیری و بازیابی",
                "مدیریت نسخه پشتیبان",
                ReferenceIcons.backup,
                { navigate(SettingsPage.BACKUP) },
                Modifier.testTag("settings-section-backup"),
            )
            HorizontalDivider(
                Modifier.padding(horizontal = 4.dp),
                color = scheme.outlineVariant,
                thickness = .6.dp,
            )
            FoodCostSettingsRow(
                "سلامت اطلاعات و یادآوری پشتیبان",
                "بررسی وضعیت داده‌ها و نسخه پشتیبان",
                ReferenceIcons.check,
                { navigate(SettingsPage.BACKUP) },
            )
        }
    }
    FoodCostCard(Modifier.testTag("settings-help-group")) {
        FoodCostSettingsRow(
            "راهنما",
            "سؤالات متداول و راهنمای استفاده",
            ReferenceIcons.info,
            { navigate(SettingsPage.HELP) },
            Modifier.testTag("settings-section-help"),
            verticalPadding = 7.75.dp,
        )
        HorizontalDivider(
            Modifier.padding(horizontal = 12.dp),
            color = scheme.outlineVariant,
            thickness = .6.dp,
        )
        FoodCostSettingsRow(
            "درباره برنامه",
            "نسخه، قلم و مجوزهای برنامه",
            ReferenceIcons.info,
            { navigate(SettingsPage.HELP) },
            Modifier.testTag("settings-section-about"),
            verticalPadding = 7.75.dp,
        )
    }
}

@Composable
fun PremiumGeneralPricing(
    input: SettingsInput,
    onChange: (SettingsInput) -> Unit,
    money: MoneyFormat,
    errors: Map<Field, String>,
    save: () -> Unit,
    openSections: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val enabled = input.scope == PricingScope.GENERAL
    val large = LocalDensity.current.fontScale > 1.25f
    val context = LocalContext.current
    val titles =
        mapOf(
            MethodChoice.FOOD_COST to "فودکاست",
            MethodChoice.MARGIN to "حاشیه سود",
            MethodChoice.MARKUP to "مارک‌آپ",
        )
    val icons =
        mapOf(
            MethodChoice.FOOD_COST to ReferenceIcons.tag,
            MethodChoice.MARGIN to PremiumIcons.trend,
            MethodChoice.MARKUP to ReferenceIcons.layers,
        )
    FoodCostCard(Modifier.testTag("pricing-methods")) {
        FlowRow(
            Modifier.padding(6.dp)
                .fillMaxWidth()
                .border(.7.dp, colors.outline, RoundedCornerShape(10.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            maxItemsInEachRow = if (large) 1 else 3,
        ) {
            MethodChoice.entries.forEach { method ->
                val selected = input.method == method
                Surface(
                    onClick = { if (enabled) onChange(input.withMethod(method)) },
                    color = Color.Transparent,
                    shape = RoundedCornerShape(9.dp),
                    modifier =
                        Modifier.weight(1f)
                            .testTag("general-method-$method")
                            .then(if (!enabled) Modifier.semantics { disabled() } else Modifier),
                ) {
                    Row(
                        Modifier.then(
                                if (selected) Modifier.background(premiumBrush()) else Modifier
                            )
                            .padding(horizontal = 5.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement =
                            Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    ) {
                        Icon(
                            icons.getValue(method),
                            null,
                            modifier = Modifier.size(19.dp),
                            tint = if (selected) Color.White else colors.primary,
                        )
                        Text(
                            titles.getValue(method),
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                            color = if (selected) Color.White else colors.onSurface,
                        )
                    }
                }
            }
        }
    }
    FoodCostCard(Modifier.testTag("pricing-target-card")) {
        @Composable
        fun target(modifier: Modifier) {
            Column(modifier, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Icon(
                        icons.getValue(input.method),
                        null,
                        Modifier.size(22.dp),
                        tint = colors.primary,
                    )
                    Text(
                        "مقدار ${titles.getValue(input.method)}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                fun step(increase: Boolean) {
                    val value = Numbers.parse(input.percent) ?: return
                    val next = if (increase) value + BigDecimal.ONE else value - BigDecimal.ONE
                    if (
                        next.signum() >= 0 &&
                            (input.method == MethodChoice.MARKUP || next < BigDecimal(100))
                    )
                        onChange(input.withPercent(next.toPlainString()))
                }
                Spacer(Modifier.height(9.dp))
                ReferenceField(
                    input.percent,
                    { onChange(input.withPercent(it)) },
                    "${titles.getValue(input.method)} هدف",
                    Modifier.testTag("general-pricing-percent"),
                    number = true,
                    centered = true,
                    height = 30.dp,
                    fontSize = 20.sp,
                    verticalPadding = 0.dp,
                    suffix = "٪",
                    enabled = enabled,
                    leading = {
                        IconButton(
                            { step(true) },
                            enabled = enabled,
                            modifier = Modifier.size(24.dp).background(colors.surfaceContainerLow, RoundedCornerShape(6.dp)),
                        ) {
                            Icon(
                                PremiumIcons.plus,
                                "افزایش درصد",
                                Modifier.size(22.dp),
                                tint = colors.primary,
                            )
                        }
                    },
                    trailing = {
                        IconButton(
                            { step(false) },
                            enabled = enabled,
                            modifier = Modifier.size(24.dp).background(colors.surfaceContainerLow, RoundedCornerShape(6.dp)),
                        ) {
                            Icon(PremiumIcons.minus, "کاهش درصد", Modifier.size(22.dp))
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    when (input.method) {
                        MethodChoice.FOOD_COST -> "سهم هزینه مواد اولیه از قیمت فروش"
                        MethodChoice.MARGIN -> "سهم حاشیه سود از فروش خالص"
                        MethodChoice.MARKUP -> "درصد افزوده بر مبنای هزینه"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        val equivalentFood = input.withMethod(MethodChoice.FOOD_COST)
        val gaugeValue = if (equivalentFood.carried) null else Numbers.parse(equivalentFood.percent)
        if (large)
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                target(Modifier.fillMaxWidth())
                FoodCostPercentageGauge(
                    gaugeValue?.toFloat(),
                    gaugeValue?.let { Numbers.percent(it) } ?: "—",
                    "فودکاست هدف",
                    Modifier.fillMaxWidth().padding(horizontal = 30.dp),
                    valueFontSize = 38.sp,
                )
            }
        else
            Row(
                Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                target(Modifier.weight(1.5f))
                VerticalDivider(Modifier.height(80.dp), color = colors.outlineVariant)
                FoodCostPercentageGauge(
                    gaugeValue?.toFloat(),
                    gaugeValue?.let { Numbers.percent(it) } ?: "—",
                    "فودکاست هدف",
                    Modifier.weight(1f),
                    valueFontSize = 38.sp,
                )
            }
        errors[Field.PERCENT]?.let {
            Text(
                it,
                Modifier.padding(8.dp),
                color = colors.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (input.carried)
            Text(
                SettingsInput.CARRIED_MESSAGE,
                Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
            )
    }
    FoodCostCard(Modifier.testTag("pricing-equivalents")) {
        FlowRow(
            Modifier.fillMaxWidth().padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            maxItemsInEachRow = if (large) 1 else 3,
        ) {
            MethodChoice.entries.forEach { method ->
                val mapped = input.withMethod(method)
                val value = if (mapped.carried) null else Numbers.parse(mapped.percent)
                Column(
                    Modifier.weight(1f)
                        .background(
                            when (method) {
                                MethodChoice.FOOD_COST -> colors.primaryContainer
                                MethodChoice.MARGIN -> premiumWarningColors().container
                                else -> colors.surfaceContainerLow
                            },
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 5.dp, vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            icons.getValue(method),
                            null,
                            Modifier.size(18.dp),
                            tint = colors.primary,
                        )
                        Text(
                            if (method == MethodChoice.FOOD_COST) "فودکاست هدف"
                            else titles.getValue(method),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            textAlign = TextAlign.Center,
                        )
                    }
                    HorizontalDivider(
                        Modifier.padding(vertical = 2.dp),
                        color = colors.surfaceContainerLowest,
                    )
                    Text(
                        value?.let { Numbers.percent(it) } ?: "—",
                        style =
                            MaterialTheme.typography.titleLarge.copy(
                                fontSize = 18.sp,
                                lineHeight = 28.sp,
                            ),
                        color = colors.tertiary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
    FoodCostCard(Modifier.testTag("pricing-settings")) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 5.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(ReferenceIcons.settings, null, Modifier.size(23.dp), tint = colors.primary)
                Text("تنظیمات قیمت‌گذاری", style = MaterialTheme.typography.titleMedium)
            }
            var vat by remember { mutableStateOf(false) }
            PricingSettingRow(
                "مالیات",
                "اعمال مالیات بر قیمت فروش",
                ReferenceIcons.coins,
                Persian.digits(input.vatPercent) + "٪",
                { vat = true },
            )
            if (vat)
                AlertDialog(
                    onDismissRequest = { vat = false },
                    title = { Text("مالیات") },
                    text = {
                        Column {
                            TextInput(
                                input.vatPercent,
                                { onChange(input.copy(vatPercent = it)) },
                                "مالیات بر ارزش افزوده",
                                number = true,
                                suffix = "٪",
                                error = errors[Field.VAT],
                            )
                            SwitchRow("قیمت‌های منو با مالیات است", input.menuPriceIncludesVat) {
                                onChange(input.copy(menuPriceIncludesVat = it))
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { vat = false }) { Text("تأیید") } },
                )
            HorizontalDivider(color = colors.outlineVariant, thickness = .6.dp)
            var rounding by remember { mutableStateOf(false) }
            Box {
                PricingSettingRow(
                    "گرد کردن قیمت",
                    "روش گرد کردن نتیجه محاسبه",
                    ReferenceIcons.sliders,
                    "${Labels.direction(input.roundingDirection)} ${money.full(input.roundingStepRials)}",
                    { rounding = true },
                )
                DropdownMenu(rounding, { rounding = false }) {
                    SettingsInput.ROUNDING_STEPS.forEach { step ->
                        DropdownMenuItem(
                            text = { Text(money.full(step)) },
                            onClick = {
                                onChange(input.copy(roundingStepRials = step))
                                rounding = false
                            },
                        )
                    }
                    PriceRounding.Direction.entries.forEach { direction ->
                        DropdownMenuItem(
                            text = { Text(Labels.direction(direction)) },
                            onClick = {
                                onChange(input.copy(roundingDirection = direction))
                                rounding = false
                            },
                        )
                    }
                }
            }
            HorizontalDivider(color = colors.outlineVariant, thickness = .6.dp)
            var basis by remember { mutableStateOf(false) }
            Box {
                PricingSettingRow(
                    "مبنای محاسبه",
                    "مبنای اعمال درصدهای قیمت‌گذاری",
                    ReferenceIcons.calculator,
                    if (input.basis == CostBasis.FOOD_COST) "فقط مواد اولیه" else "همهٔ هزینه‌ها",
                    { basis = true },
                )
                DropdownMenu(basis, { basis = false }) {
                    if (input.method == MethodChoice.FOOD_COST)
                        DropdownMenuItem(
                            text = { Text("فودکاست همواره روی مواد اولیه است") },
                            onClick = { basis = false },
                        )
                    else
                        CostBasis.entries.forEach { b ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (b == CostBasis.FOOD_COST) "فقط مواد اولیه"
                                        else "همهٔ هزینه‌ها"
                                    )
                                },
                                onClick = {
                                    onChange(input.copy(basis = b, conversionOrigin = null))
                                    basis = false
                                },
                            )
                        }
                }
            }
        }
    }
    FoodCostCard(Modifier.testTag("pricing-scope")) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(ReferenceIcons.layers, null, Modifier.size(23.dp), tint = colors.primary)
            Column(
                Modifier.weight(1f)
                    .clickable(onClick = openSections)
                    .testTag("settings-section-sections")
            ) {
                Text("قیمت‌گذاری بر اساس بخش‌های منو", style = MaterialTheme.typography.titleSmall)
                Text(
                    "اعمال قواعد متفاوت برای گروه‌های منو (اختیاری)",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            FoodCostSwitch(
                checked = !enabled,
                onCheckedChange = {
                    onChange(
                        input.copy(
                            scope = if (it) PricingScope.MENU_SECTIONS else PricingScope.GENERAL
                        )
                    )
                },
                modifier =
                    Modifier.size(width = 42.dp, height = 30.dp)
                        .testTag(if (enabled) "pricing-scope-sections" else "pricing-scope-general"),
            )
        }
        // The existing sections feature is preserved; it is already part of the approved 0.8.3.
    }
    Surface(
        color = colors.primaryContainer,
        shape = RoundedCornerShape(9.dp),
        modifier = Modifier.testTag("pricing-notice"),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(ReferenceIcons.info, null, Modifier.size(18.dp), tint = colors.primary)
            Text(
                input.equivalents() ?: "مقادیر قبلی حفظ می‌شوند؛ فقط حالت انتخاب‌شده اعمال می‌شود.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = if (large) 1 else 2,
    ) {
        FoodCostPrimaryButton(
            "ذخیره تغییرات",
            save,
            Modifier.weight(1.05f).testTag("settings-save-pricing"),
            PremiumIcons.save,
        )
        FoodCostOutlinedButton(
            "اشتراک‌گذاری",
            {
                val text =
                    "قیمت‌گذاری FoodCost\n${titles.getValue(input.method)}: ${Persian.digits(input.percent)}٪\n${input.equivalents().orEmpty()}"
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        },
                        "اشتراک‌گذاری تنظیمات",
                    )
                )
            },
            Modifier.weight(1f),
            PremiumIcons.share,
        )
    }
}

@Composable
private fun PricingSettingRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    onClick: () -> Unit,
) {
    val large = LocalDensity.current.fontScale > 1.25f
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style =
                    MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 15.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(7.dp),
            border = BorderStroke(.7.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier = Modifier.weight(if (large) 1f else 1.1f),
        ) {
            Row(
                Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(value, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Icon(ReferenceIcons.chevron, null, Modifier.size(13.dp))
            }
        }
    }
}
