package ir.foodcost.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.foodcost.app.ui.theme.statusColors
import ir.foodcost.core.pricing.PriceStatus
import ir.foodcost.data.ledger.LedgerState
import ir.foodcost.presentation.Labels
import ir.foodcost.presentation.MenuRow
import ir.foodcost.presentation.MenuStatusModel
import ir.foodcost.presentation.MenuStatusScreen
import ir.foodcost.presentation.MoneyFormat
import ir.foodcost.presentation.Persian
import java.time.LocalDate

/** The first tab: every plate with its cost, its price and whether the price still covers it. */
@Composable
fun MenuScreen(state: LedgerState, money: MoneyFormat, today: LocalDate, nav: Navigator, actions: AppActions) {
    val model = remember(state, money, today) { MenuStatusScreen.build(state, today, money) }
    TabScaffold(title = "وضعیت منو", nav = nav, actions = actions) { padding ->
        if (model.rows.isEmpty()) {
            EmptyState(
                title = "هنوز غذایی در منو نیست",
                body = "یک غذا را در «ماشین‌حساب» حساب و ذخیره کنید تا بها، قیمت پیشنهادی و وضعیت قیمتش اینجا دیده شود.",
                modifier = Modifier.padding(padding),
                action = "رفتن به ماشین‌حساب",
                onAction = { nav.tab(Tab.CALC) },
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { MenuSummary(model) }
                items(model.rows, key = { it.recipeId.value }) { row ->
                    MenuRowCard(row) { nav.open(dishScreen(state, row.recipeId, today, money)) }
                }
            }
        }
    }
}

@Composable
private fun MenuSummary(model: MenuStatusModel) {
    val attention = model.needsRepricing
    val colors = statusColors(if (attention > 0) PriceStatus.UNDER else PriceStatus.OK)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.container, contentColor = colors.content),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(model.date, style = MaterialTheme.typography.labelMedium)
            Text(
                if (attention > 0) "${Persian.digits(attention)} غذا باید گران‌تر شود" else "قیمت غذاها بهای آن‌ها را می‌پوشاند",
                style = MaterialTheme.typography.titleLarge,
            )
            val counts = listOf(
                PriceStatus.UNDER, PriceStatus.NEAR, PriceStatus.OK,
                PriceStatus.NO_PRICE, PriceStatus.INCOMPLETE, PriceStatus.EMPTY,
            ).filter { model.count(it) > 0 }.joinToString(" · ") { "${Labels.status(it)} ${Persian.digits(model.count(it))}" }
            Text(counts, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MenuRowCard(row: MenuRow, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(row.name, style = MaterialTheme.typography.titleMedium)
                    row.kindLabel?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                StatusChip(row.status, row.statusLabel)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledValue("بهای هر پرس", row.foodCost, Modifier.weight(1f))
                LabeledValue("قیمت منو", row.menuPrice ?: "ثبت نشده", Modifier.weight(1f))
                LabeledValue("پیشنهادی", row.suggestedPrice, Modifier.weight(1f))
            }
            val notes = listOfNotNull(
                row.foodCostPercent?.let { "فودکاست $it" },
                row.raise?.let { "باید $it گران‌تر شود" },
            )
            if (notes.isNotEmpty()) {
                Text(
                    notes.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
