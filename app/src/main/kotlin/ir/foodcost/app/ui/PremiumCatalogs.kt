package ir.foodcost.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.foodcost.app.ui.theme.premiumWarningColors
import ir.foodcost.data.ledger.LedgerState
import ir.foodcost.presentation.*
import java.time.LocalDate

@Composable
fun PremiumStatistic(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    warning: Boolean = false,
    severe: Boolean = false,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val background =
        if (!warning) scheme.primaryContainer
        else if (severe) scheme.errorContainer.copy(alpha = .55f)
        else premiumWarningColors().container
    val accent =
        if (!warning) scheme.primary
        else if (severe) scheme.error else premiumWarningColors().content
    Row(
        modifier
            .semantics(mergeDescendants = true) {}
            .heightIn(min = 32.dp)
            .background(background, RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(if (compact) 3.dp else 5.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(if (compact) 18.dp else 20.dp))
        Text(
            "$label:",
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize = if (compact) 10.sp else 11.sp,
                    lineHeight = 16.sp,
                ),
            color = scheme.onSurface,
        )
        Text(
            value,
            style =
                MaterialTheme.typography.titleSmall.copy(
                    fontSize = if (compact) 12.sp else 14.sp,
                    lineHeight = 18.sp,
                ),
            color = accent,
        )
    }
}

/** A purchase date is factual. We never invent the clock time printed in the concept. */
private fun priceDay(row: IngredientRow, today: LocalDate): String =
    when (row.priceEffectiveFrom) {
        null -> "قیمتی ثبت نشده"
        today -> "امروز"
        today.minusDays(1) -> "دیروز"
        else -> row.priceDate.orEmpty()
    }

@Composable
fun PremiumIngredientCard(
    row: IngredientRow,
    state: LedgerState,
    money: MoneyFormat,
    today: LocalDate,
    dataUnreliable: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onReprice: () -> Unit,
    onHistory: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val large = LocalDensity.current.fontScale > 1.25f
    val colors = MaterialTheme.colorScheme
    val unit =
        when (state.ingredients[row.id]?.baseDimension) {
            ir.foodcost.core.units.Dimension.MASS -> "کیلوگرم"
            ir.foodcost.core.units.Dimension.VOLUME -> "لیتر"
            else -> "عدد"
        }
    val price = row.unitPrice?.substringAfter("هر ")?.substringAfter(" ") ?: "نیاز به قیمت"
    FoodCostCard(Modifier.testTag("ingredient-${row.id.value}")) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 8.dp, vertical = 6.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (!large) RowSymbol(ReferenceIcons.layers)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.name, style = MaterialTheme.typography.titleMedium)
                Surface(color = colors.surfaceContainerLow, shape = RoundedCornerShape(50)) {
                    Text(
                        unit,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                    )
                }
                if (large)
                    IngredientPrice(
                        row,
                        price,
                        today,
                        dataUnreliable,
                        onReprice,
                        Modifier.fillMaxWidth(),
                    )
            }
            if (!large)
                IngredientPrice(row, price, today, dataUnreliable, onReprice, Modifier.weight(1f))
            VerticalDivider(Modifier.height(40.dp), color = colors.outlineVariant)
            if (large)
                Column {
                    CatalogAction(PremiumIcons.edit, "ویرایش ${row.name}", onEdit)
                    CatalogAction(ReferenceIcons.trash, "حذف ${row.name}", onDelete)
                }
            else
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    CatalogAction(PremiumIcons.edit, "ویرایش ${row.name}", onEdit)
                    CatalogAction(ReferenceIcons.trash, "حذف ${row.name}", onDelete)
                }
        }
        if (expanded) {
            HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = colors.outlineVariant)
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FoodCostPrimaryButton(
                    "ثبت قیمت تازه",
                    onReprice,
                    Modifier.weight(1f).testTag("ingredient-reprice-${row.id.value}"),
                )
                FoodCostOutlinedButton(
                    "تاریخچه",
                    onHistory,
                    Modifier.weight(1f).testTag("ingredient-history-${row.id.value}"),
                )
            }
        }
    }
}

@Composable
private fun IngredientPrice(
    row: IngredientRow,
    price: String,
    today: LocalDate,
    unreliable: Boolean,
    onReprice: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(
                    if (row.unitPrice == null) premiumWarningColors().container
                    else colors.primaryContainer,
                    RoundedCornerShape(8.dp),
                )
                .then(
                    if (row.unitPrice == null && !unreliable)
                        Modifier.clickable(onClick = onReprice)
                    else Modifier
                )
                .semantics(mergeDescendants = true) {}
                .testTag(
                    if (row.unitPrice == null) "ingredient-register-${row.id.value}"
                    else "ingredient-price-${row.id.value}"
                )
                .padding(horizontal = 5.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (row.unitPrice != null && !unreliable)
                Text(
                    "قیمت آخرین",
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                        ),
                )
            Text(
                if (unreliable) BackupTexts.UNVERIFIED_PRICE else price,
                style =
                    MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color =
                    if (row.unitPrice == null) premiumWarningColors().content else colors.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        if (!unreliable)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    PremiumIcons.clock,
                    null,
                    modifier = Modifier.size(12.dp),
                    tint = colors.onSurfaceVariant,
                )
                Text(
                    "به‌روزرسانی: ${priceDay(row,today)}",
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                        ),
                    color = colors.onSurfaceVariant,
                )
            }
    }
}

@Composable
private fun CatalogAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier =
            Modifier.size(28.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp)),
    ) {
        Icon(
            icon,
            label,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PremiumRecipeCard(
    row: MenuRow,
    state: LedgerState,
    money: MoneyFormat,
    today: LocalDate,
    expanded: Boolean,
    unreliable: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val result = if (!unreliable) state.calculator().evaluate(row.recipeId, today) else null
    val detail = RecipeScreens.detail(state, row.recipeId, today, money)
    val large = LocalDensity.current.fontScale > 1.25f
    FoodCostCard(
        Modifier.testTag("food-${row.recipeId.value}")
            .combinedClickable(
                onClick = onToggle,
                onLongClick = if (unreliable) null else onDelete,
                onLongClickLabel = "حذف غذا",
            )
    ) {
        Column(
            Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(
                    ReferenceIcons.dishes,
                    null,
                    tint = scheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(row.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (!unreliable) StatusChip(row.status, row.statusLabel)
                RowChevron(expanded, onToggle)
            }
            HorizontalDivider(color = scheme.outlineVariant, thickness = .6.dp)
            if (unreliable) Text(BackupTexts.UNVERIFIED_RESULT, color = scheme.error)
            else {
                @Composable
                fun metrics(first: Boolean, modifier: Modifier) {
                    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (first) {
                            FoodCostFinancialMetric(
                                "هزینه مواد اولیه هر پرس",
                                row.foodCost,
                                money,
                                ReferenceIcons.tag,
                                Modifier.weight(1.25f),
                                accent = scheme.primary,
                            )
                            VerticalDivider(Modifier.height(43.dp), color = scheme.outlineVariant)
                            FoodCostFinancialMetric(
                                "قیمت منو",
                                row.menuPrice ?: "—",
                                money,
                                ReferenceIcons.coins,
                                Modifier.weight(1f).testTag("food-price-${row.recipeId.value}"),
                            )
                        } else {
                            FoodCostFinancialMetric(
                                "فودکاست",
                                row.foodCostPercent ?: "—",
                                money,
                                null,
                                Modifier.weight(1f),
                                accent = ir.foodcost.app.ui.theme.statusColors(row.status).content,
                            )
                            VerticalDivider(Modifier.height(43.dp), color = scheme.outlineVariant)
                            FoodCostFinancialMetric(
                                "سود ناخالص",
                                result
                                    ?.grossProfit
                                    ?.takeIf { result.cost.isComplete }
                                    ?.let(money::full) ?: "—",
                                money,
                                PremiumIcons.trend,
                                Modifier.weight(1f),
                                accent = scheme.primary,
                            )
                        }
                    }
                }
                if (large) {
                    metrics(true, Modifier.fillMaxWidth())
                    metrics(false, Modifier.fillMaxWidth())
                } else
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        metrics(true, Modifier.weight(1.35f))
                        VerticalDivider(Modifier.height(43.dp), color = scheme.outlineVariant)
                        metrics(false, Modifier.weight(1f))
                    }
            }
            if (expanded) {
                Surface(color = scheme.surfaceContainerLow, shape = RoundedCornerShape(9.dp)) {
                    Column(Modifier.padding(5.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                PremiumIcons.chef,
                                null,
                                Modifier.size(19.dp),
                                tint = scheme.primary,
                            )
                            Text("دستور پخت", style = MaterialTheme.typography.titleSmall)
                            Text(
                                detail?.lines?.take(5)?.joinToString("، ") { it.name } ?: "",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        HorizontalDivider(
                            Modifier.padding(vertical = 3.dp),
                            color = scheme.surfaceContainerLowest,
                        )
                        Text(
                            "مواد اولیه (${Persian.digits(detail?.lines?.size?:0)} ماده)",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FoodCostPrimaryButton(
                        "اصلاح قیمت",
                        onOpen,
                        Modifier.weight(1f).testTag("food-open-${row.recipeId.value}"),
                        PremiumIcons.edit,
                        compact = true,
                    )
                    FoodCostOutlinedButton(
                        "جزئیات",
                        onDetails,
                        Modifier.weight(1f),
                        ReferenceIcons.info,
                        compact = true,
                    )
                }
            }
        }
    }
}

@Composable
fun FoodCostFinancialMetric(
    label: String,
    value: String,
    money: MoneyFormat,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    modifier: Modifier = Modifier,
    accent: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, lineHeight = 14.sp),
            textAlign = TextAlign.Center,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        ) {
            if (icon != null)
                Icon(
                    icon,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            Text(
                value.removeSuffix(" ${money.unit}"),
                style =
                    MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
                color = accent,
                textAlign = TextAlign.Center,
            )
        }
        if (value.endsWith(money.unit))
            Text(
                money.unit,
                style =
                    MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 13.sp),
            )
    }
}
