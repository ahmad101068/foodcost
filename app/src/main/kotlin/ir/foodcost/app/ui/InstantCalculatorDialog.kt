package ir.foodcost.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.foodcost.core.costing.RecipeCost
import ir.foodcost.core.pricing.InstantCalculator
import ir.foodcost.core.pricing.InstantQuoteInput
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.core.pricing.PricingSettings
import ir.foodcost.presentation.MoneyFormat
import ir.foodcost.presentation.Numbers
import java.math.BigDecimal

/**
 * The calculator never writes to a ledger. Price is persisted only if the user presses Save.
 * Calculations re-run in composition on every field edit, so there is no "Calculate" button.
 */
@Composable
fun InstantCalculatorDialog(
    cost: RecipeCost,
    pricing: PricingSettings,
    money: MoneyFormat,
    initialPriceRials: Long?,
    onDismiss: () -> Unit,
    onSavePrice: (Long) -> Unit,
) {
    val defaultTarget = (pricing.methodFor(cost.recipe) as? PricingMethod.TargetFoodCost)
        ?.percent?.let(Numbers::editable) ?: "35"
    var priceText by remember(cost.recipe.id, money.currency) {
        mutableStateOf(initialPriceRials?.let(money::editable).orEmpty())
    }
    var extrasText by remember(cost.recipe.id, money.currency) { mutableStateOf("") }
    var targetText by remember(cost.recipe.id) { mutableStateOf(defaultTarget) }
    var portionsText by remember(cost.recipe.id) { mutableStateOf("1") }

    val menuPrice = if (priceText.isBlank()) null else money.parse(priceText)
    val extraRials = if (extrasText.isBlank()) 0L else money.parse(extrasText)
    val target = Numbers.parse(targetText)
    val portions = Numbers.parse(portionsText)
    val valid = (priceText.isBlank() || (menuPrice != null && menuPrice > 0)) &&
        extraRials != null && target != null && target > BigDecimal.ZERO &&
        target < BigDecimal(100) && portions != null && portions > BigDecimal.ZERO

    val quote = if (valid && cost.hasLines && cost.isComplete) {
        runCatching {
            InstantCalculator.calculate(
                cost,
                InstantQuoteInput(
                    foodCostPerPortion = cost.foodCostPerPortion,
                    extraCostPerPortion = BigDecimal.valueOf(extraRials!!),
                    menuPrice = menuPrice?.let(BigDecimal::valueOf),
                    targetFoodCostPercent = target!!,
                    vatPercent = pricing.vatPercent,
                    menuPriceIncludesVat = pricing.menuPriceIncludesVat,
                    portions = portions!!,
                ),
            )
        }.getOrNull()
    } else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ماشین‌حساب لحظه‌ای فودکاست") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("قیمت و مقدار را تغییر دهید؛ نتیجه فوراً به‌روز می‌شود.",
                    style = MaterialTheme.typography.bodySmall)
                TextInput(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = "قیمت فروش منو (${money.unit})",
                    number = true,
                    supporting = if (pricing.menuPriceIncludesVat) "شامل مالیات طبق تنظیمات اپ" else "قیمت خالص",
                )
                TextInput(
                    value = extrasText,
                    onValueChange = { extrasText = it },
                    label = "هزینه جانبی هر پرس (${money.unit})",
                    supporting = "اختیاری: ظرف، ارسال یا سهم هزینه‌ای که خودتان تعیین می‌کنید",
                    number = true,
                )
                TextInput(
                    value = targetText,
                    onValueChange = { targetText = it },
                    label = "فودکاست هدف (٪)",
                    number = true,
                )
                TextInput(
                    value = portionsText,
                    onValueChange = { portionsText = it },
                    label = "تعداد پرس برای جمع کل",
                    number = true,
                )
                HorizontalDivider()
                if (quote == null) {
                    Text(
                        "برای محاسبه، مقدارهای معتبر وارد کنید و دستور پخت را کامل نگه دارید.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LabeledValue("هزینه مواد هر پرس", money.full(quote.foodCostPerPortion), emphasized = true)
                    LabeledValue("هزینه مواد + هزینه جانبی هر پرس", money.full(quote.enteredCostPerPortion))
                    LabeledValue("هزینه واردشده برای تمام پرس‌ها", money.full(quote.totalEnteredCost))
                    LabeledValue("قیمت هدف بر اساس فودکاست (پیش از گردکردن)", money.full(quote.targetMenuPrice))
                    quote.netPricePerPortion?.let {
                        LabeledValue("فروش خالص هر پرس", money.full(it))
                    }
                    quote.foodCostPercent?.let {
                        LabeledValue("درصد فودکاست", Numbers.percent(it, 2))
                    }
                    quote.remainderPerPortion?.let {
                        LabeledValue("مانده پس از هزینه‌های واردشده", money.full(it), emphasized = true)
                    }
                    quote.enteredCostMarginPercent?.let {
                        LabeledValue("حاشیه مانده بر فروش (Margin)", Numbers.percent(it, 2))
                    }
                    quote.enteredCostMarkupPercent?.let {
                        LabeledValue("مانده نسبت به هزینه (Markup)", Numbers.percent(it, 2))
                    }
                    Text(
                        "این مانده سود خالص قطعی رستوران نیست. هزینه‌های واردنشده در آن محاسبه نمی‌شوند. " +
                            "قیمت هدف بر اساس هزینه مواد است و هزینه جانبی را جداگانه نشان می‌دهیم.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (quote != null && menuPrice != null && menuPrice > 0) onSavePrice(menuPrice) },
                enabled = quote != null && menuPrice != null && menuPrice > 0,
            ) { Text("ثبت قیمت منو") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
    )
}
