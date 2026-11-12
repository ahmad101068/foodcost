package ir.foodcost.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.foodcost.app.ui.theme.statusColors
import ir.foodcost.core.pricing.PriceStatus
import ir.foodcost.presentation.Persian

/** A group stays visually continuous while each row remains a separate, lazy list item. */
@Composable
internal fun GroupedRow(first: Boolean, last: Boolean, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(topStart = if (first) 16.dp else 0.dp, topEnd = if (first) 16.dp else 0.dp,
        bottomStart = if (last) 16.dp else 0.dp, bottomEnd = if (last) 16.dp else 0.dp)
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = shape, border = BorderStroke(.6.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.animateContentSize(), content = content)
    }
}

@Composable
internal fun GroupHeading(title: String, count: Int, unit: String) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), modifier = Modifier.weight(1f))
        Text("${Persian.digits(count)} $unit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun CountChoice(label: String, count: String, selected: Boolean, modifier: Modifier = Modifier,
    enabled: Boolean = true, tone: PriceStatus? = null, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val palette = tone?.let { statusColors(it) }
    Surface(
        modifier = modifier.semantics { this.selected = selected; role = Role.Tab }.clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) scheme.primary else palette?.container ?: scheme.secondaryContainer,
        contentColor = if (selected) scheme.onPrimary else palette?.content ?: scheme.onSecondaryContainer,
    ) {
        Text("$label $count", style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp))
    }
}

@Composable
internal fun RowSymbol(icon: ImageVector) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(23.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** A compact visible control with an Android touch target, without a 48dp layout box over neighbours. */
@Composable
internal fun RowChevron(expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(width = 26.dp, height = 36.dp).clickable(role = Role.Button,
        onClickLabel = if (expanded) "بستن جزئیات" else "نمایش جزئیات", onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun RowMenu(tag: String, content: @Composable (close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(Modifier.size(width = 24.dp, height = 36.dp).testTag(tag).clickable(role = Role.Button,
            onClickLabel = "گزینه‌های بیشتر", onClick = { open = true }), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MoreVert, contentDescription = "گزینه‌های بیشتر", modifier = Modifier.size(20.dp))
        }
        DropdownMenu(open, onDismissRequest = { open = false }) { content { open = false } }
    }
}

@Composable
internal fun GroupAmount(value: String, unit: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, unitCaption: String = unit) {
    // Keep the number and unit together for accessibility, even inside the expandable row.
    Column(modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        FitText(value.removeSuffix(" $unit"), MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold), color = color)
        Text(unitCaption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SmallFact(label: String, value: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, color = color)
    }
}

@Composable
internal fun ListPriceStatus(status: PriceStatus, label: String) {
    val palette = statusColors(status)
    Surface(color = palette.container, contentColor = palette.content, shape = RoundedCornerShape(50)) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp), modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
    }
}
