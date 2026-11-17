package ir.foodcost.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import ir.foodcost.app.ui.theme.PremiumTokens as T
import ir.foodcost.core.pricing.PriceStatus

/** Additional vectors share the reference icons' 1.7/24 outline grid. No raster art. */
object PremiumIcons {
    private fun line(name: String, draw: PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .apply {
                path(
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.7f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    pathBuilder = draw,
                )
            }
            .build()

    val trend =
        line("Trend") {
            moveTo(3f, 18f)
            lineTo(10f, 11f)
            lineTo(14f, 15f)
            lineTo(22f, 6f)
            moveTo(16f, 6f)
            lineTo(22f, 6f)
            lineTo(22f, 12f)
        }
    val edit =
        line("Edit") {
            moveTo(4f, 20f)
            lineTo(5f, 14f)
            lineTo(17f, 2f)
            lineTo(22f, 7f)
            lineTo(10f, 19f)
            close()
            moveTo(14f, 5f)
            lineTo(19f, 10f)
        }
    val plus =
        line("Add") {
            moveTo(12f, 4f)
            lineTo(12f, 20f)
            moveTo(4f, 12f)
            lineTo(20f, 12f)
        }
    val minus =
        line("Subtract") {
            moveTo(5f, 12f)
            lineTo(19f, 12f)
        }
    val save =
        line("Save") {
            moveTo(3f, 3f)
            lineTo(18f, 3f)
            lineTo(22f, 7f)
            lineTo(22f, 21f)
            lineTo(3f, 21f)
            close()
            moveTo(7f, 3f)
            lineTo(7f, 9f)
            lineTo(17f, 9f)
            lineTo(17f, 3f)
            moveTo(7f, 21f)
            lineTo(7f, 14f)
            lineTo(18f, 14f)
            lineTo(18f, 21f)
        }
    val share =
        line("Share") {
            moveTo(7f, 11f)
            lineTo(17f, 5f)
            moveTo(7f, 13f)
            lineTo(17f, 19f)
            moveTo(7f, 12f)
            arcTo(2f, 2f, 0f, true, true, 3f, 12f)
            arcTo(2f, 2f, 0f, true, true, 7f, 12f)
            moveTo(21f, 4f)
            arcTo(2f, 2f, 0f, true, true, 17f, 4f)
            arcTo(2f, 2f, 0f, true, true, 21f, 4f)
            moveTo(21f, 20f)
            arcTo(2f, 2f, 0f, true, true, 17f, 20f)
            arcTo(2f, 2f, 0f, true, true, 21f, 20f)
        }
    val filter =
        line("Filter") {
            moveTo(3f, 3f)
            lineTo(21f, 3f)
            lineTo(14f, 12f)
            lineTo(14f, 21f)
            lineTo(10f, 18f)
            lineTo(10f, 12f)
            close()
        }
    val calendar =
        line("Calendar") {
            moveTo(3f, 5f)
            lineTo(21f, 5f)
            lineTo(21f, 22f)
            lineTo(3f, 22f)
            close()
            moveTo(3f, 10f)
            lineTo(21f, 10f)
            moveTo(7f, 2f)
            lineTo(7f, 7f)
            moveTo(17f, 2f)
            lineTo(17f, 7f)
        }
    val clock =
        line("Clock") {
            moveTo(22f, 12f)
            arcTo(10f, 10f, 0f, true, true, 2f, 12f)
            arcTo(10f, 10f, 0f, true, true, 22f, 12f)
            moveTo(12f, 5f)
            lineTo(12f, 12f)
            lineTo(17f, 15f)
        }
    val chef =
        line("Chef") {
            moveTo(7f, 10f)
            curveTo(0f, 11f, 0f, 2f, 7f, 3f)
            curveTo(10f, -1f, 15f, 0f, 17f, 3f)
            curveTo(24f, 2f, 25f, 11f, 18f, 10f)
            lineTo(18f, 22f)
            lineTo(6f, 22f)
            lineTo(6f, 10f)
            moveTo(10f, 14f)
            lineTo(10f, 18f)
            moveTo(14f, 14f)
            lineTo(14f, 18f)
        }
    val people =
        line("Portions") {
            moveTo(15f, 6f)
            arcTo(4f, 4f, 0f, true, true, 7f, 6f)
            arcTo(4f, 4f, 0f, true, true, 15f, 6f)
            moveTo(3f, 22f)
            curveTo(2f, 11f, 21f, 11f, 20f, 22f)
            close()
            moveTo(18f, 3f)
            curveTo(23f, 5f, 22f, 10f, 18f, 11f)
            moveTo(21f, 15f)
            curveTo(24f, 16f, 24f, 19f, 24f, 21f)
        }
}

@Composable
fun premiumBrush(): Brush = Brush.linearGradient(listOf(T.primaryBright, T.primary, T.primaryDeep))

@Composable
fun FoodCostCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(T.cardRadius),
        colors = whiteCardColors(),
        border = BorderStroke(.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f)),
        elevation = CardDefaults.cardElevation(1.5.dp),
    ) {
        Column(content = content)
    }
}

@Composable
fun FoodCostPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    compact: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = if (compact) 26.dp else 36.dp),
        shape = RoundedCornerShape(T.controlRadius),
        color = Color.Transparent,
    ) {
        Row(
            Modifier.background(premiumBrush())
                .padding(horizontal = 12.dp, vertical = if (compact) 2.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(if (compact) 18.dp else 22.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                color = Color.White,
                style =
                    MaterialTheme.typography.labelLarge.copy(
                        fontSize = if (compact) 13.sp else 15.sp,
                        lineHeight = if (compact) 20.sp else 22.sp,
                    ),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun FoodCostOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    compact: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = if (compact) 26.dp else 36.dp),
        shape = RoundedCornerShape(T.controlRadius),
        color = Color.Transparent,
        border = BorderStroke(.8.dp, MaterialTheme.colorScheme.primary),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = if (compact) 2.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (compact) 18.dp else 20.dp),
                )
                Spacer(Modifier.width(7.dp))
            }
            Text(
                text,
                color = MaterialTheme.colorScheme.primary,
                style =
                    MaterialTheme.typography.labelLarge.copy(
                        fontSize = if (compact) 13.sp else 15.sp,
                        lineHeight = if (compact) 20.sp else 22.sp,
                    ),
            )
        }
    }
}

@Composable
fun FoodCostAppBar(
    title: String,
    nav: Navigator,
    actions: @Composable RowScope.() -> Unit,
    navigationIcon: @Composable () -> Unit,
) {
    val module =
        when {
            title.contains("قیمت‌گذاری") || nav.tab == Tab.CALC -> ReferenceIcons.calculator
            nav.tab == Tab.INGREDIENTS -> ReferenceIcons.layers
            nav.tab == Tab.RECIPES -> ReferenceIcons.dishes
            else -> ReferenceIcons.settings
        }
    val shownTitle =
        if (title == "ماشین‌حساب") "فودکاست"
        else if (title == "قیمت‌گذاری") "قیمت‌گذاری عمومی" else title
    val subtitle =
        when {
            title == "ماشین‌حساب" -> "ماشین‌حساب حرفه‌ای هزینه غذا"
            title == "مواد اولیه" -> "مدیریت مواد و قیمت‌ها"
            title == "غذاها" -> "غذاها و دستور پخت‌ها"
            title == "تنظیمات" -> "تنظیمات برنامه و قیمت‌گذاری"
            title == "قیمت‌گذاری" -> "تنظیم قواعد قیمت‌گذاری"
            else -> null
        }
    var more by remember { mutableStateOf(false) }
    Column(Modifier.statusBarsPadding().fillMaxWidth().testTag("app-bar")) {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = T.appBarHeight)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(premiumBrush()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(module, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    shownTitle,
                    style =
                        MaterialTheme.typography.titleLarge.copy(
                            fontSize = 18.sp,
                            lineHeight = 26.sp,
                        ),
                    fontWeight = FontWeight.Bold,
                )
                if (subtitle != null && LocalDensity.current.fontScale <= 1.25f)
                    Text(
                        subtitle,
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                navigationIcon()
                actions()
            }
            Box {
                IconButton(
                    onClick = { more = true },
                    modifier =
                        Modifier.size(32.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerLow,
                                RoundedCornerShape(9.dp),
                            ),
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        "گزینه‌های برنامه",
                        modifier = Modifier.size(22.dp),
                    )
                }
                DropdownMenu(more, { more = false }) {
                    DropdownMenuItem(
                        text = { Text("راهنمای برنامه") },
                        onClick = {
                            more = false
                            nav.open(Screen.Help(Screen.Help.GUIDE))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("واژه‌های مالی") },
                        onClick = {
                            more = false
                            nav.open(Screen.Help(Screen.Help.TERMS))
                        },
                    )
                }
            }
        }
        HorizontalDivider(
            Modifier.padding(horizontal = 12.dp),
            thickness = 2.dp,
            color = T.topDivider,
        )
    }
}

/** The number is rendered from the domain result. Float conversion is only for drawing. */
@Composable
fun FoodCostPercentageGauge(
    value: Float?,
    text: String,
    label: String,
    modifier: Modifier = Modifier,
    color: Color = T.foodCostAccent,
    status: Pair<PriceStatus, String>? = null,
    valueFontSize: androidx.compose.ui.unit.TextUnit = 28.sp,
) {
    // REF-05 places the target title lower inside the arc than the calculator title.
    // Keep this calibration out of the reflowed large-font layout.
    val targetReferenceLayout =
        status == null && valueFontSize.value > 28f && LocalDensity.current.fontScale <= 1.25f
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth()
                .height(if (LocalDensity.current.fontScale > 1.25f) 160.dp else 84.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 8.dp.toPx()
                val w = size.width - stroke
                val d = minOf(w, (size.height - stroke) * 2)
                val origin = Offset((size.width - d) / 2, stroke / 2)
                drawArc(
                    T.gaugeTrack,
                    180f,
                    180f,
                    false,
                    origin,
                    Size(d, d),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    if (status?.first == PriceStatus.OK) T.success else T.gaugeDanger,
                    180f,
                    180f * ((value ?: 0f) / 100).coerceIn(0f, 1f),
                    false,
                    origin,
                    Size(d, d),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier.offset(y = if (targetReferenceLayout) 10.dp else 0.dp).padding(
                        top = if (valueFontSize.value > 28f) 4.dp else 18.dp,
                        bottom = 0.dp,
                    ),
            ) {
                Text(
                    label,
                    modifier = Modifier.offset(y = if (targetReferenceLayout) 7.dp else 0.dp),
                    style =
                        MaterialTheme.typography.titleSmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        ),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text,
                    style =
                        MaterialTheme.typography.headlineLarge.copy(
                            fontSize = valueFontSize,
                            lineHeight = valueFontSize * 1.28f,
                            fontWeight = FontWeight.Bold,
                        ),
                    color = color,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("gauge-value"),
                )
            }
        }
        if (status != null)
            StatusChip(
                status.first,
                status.second,
                prominent = true,
                modifier = Modifier.padding(top = 0.dp),
            )
    }
}

@Composable
fun FoodCostSectionHeader(text: String, icon: ImageVector = ReferenceIcons.layers) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(9.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(
            text,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
fun FoodCostSettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    verticalPadding: Dp = 7.dp,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = 12.dp, vertical = verticalPadding)
            .heightIn(min = 34.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style =
                    MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing?.invoke()
        if (trailing == null)
            Icon(
                ReferenceIcons.chevron,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
    }
}

/** Reference-sized switch; semantics and minimum touch expansion belong to the outer control. */
@Composable
fun FoodCostSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val colors = MaterialTheme.colorScheme
    val track = if (checked) colors.primary else if (dark) colors.surfaceVariant else T.switchTrack
    val thumb =
        if (!dark) Color.White else if (checked) colors.onPrimary else colors.onSurfaceVariant
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        modifier
            .size(42.dp, 30.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(34.dp, 19.dp)) {
            drawRoundRect(
                track,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
            )
            val radius = size.height * .39f
            // The approved light PNG uses a left thumb for off; keep boolean semantics intact.
            val atRight = if (dark) checked != rtl else checked
            drawCircle(
                thumb,
                radius,
                Offset(
                    if (atRight) size.width - size.height / 2 else size.height / 2,
                    size.height / 2,
                ),
            )
        }
    }
}
