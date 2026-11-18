package ir.foodcost.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ir.foodcost.app.AppContainer
import ir.foodcost.data.ledger.LedgerState
import ir.foodcost.presentation.BackupReminder
import ir.foodcost.presentation.BackupTexts
import ir.foodcost.presentation.Persian

/** Actual read-integrity and backup-reminder status, separate from file export/import controls. */
@Composable
fun DataHealthCard(container: AppContainer, state: LedgerState, actions: AppActions, openBackup: () -> Unit) {
    val integrity = actions.integrity
    val lastBackup by container.lastBackup.collectAsState()
    val mismatches = state.closedMismatches.size
    Column(Modifier.fillMaxWidth().testTag("settings-health"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FoodCostCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("وضعیت خواندن اطلاعات", style = MaterialTheme.typography.titleMedium)
                val status = when {
                    !integrity.verified -> BackupTexts.locked(integrity)
                    container.hasUnreadableRows -> "داده‌های قابل خواندن فعال‌اند؛ ردیف‌های کنارگذاشته‌شده هنوز روی گوشی نگهداری می‌شوند."
                    mismatches > 0 -> BackupTexts.closedMismatches(mismatches)
                    else -> "اطلاعات فعلی بدون ردیف خوانده‌نشده باز شده‌اند."
                }
                Text(status, style = MaterialTheme.typography.bodyMedium, color = if (!integrity.verified || mismatches > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag("health-read-status"))
                Text("تغییرات اعمال‌شده: ${Persian.digits(state.appliedIds.size)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("health-applied"))
                Text("ردیف‌های خوانده‌نشده: ${Persian.digits(integrity.unreadable)}", style = MaterialTheme.typography.bodyMedium)
                Text("ردیف‌های کنارگذاشته‌شده: ${Persian.digits(container.unreadableRowsKept)}", style = MaterialTheme.typography.bodyMedium)
                Text("تغییرات ردشده: ${Persian.digits(state.rejections.size)}", style = MaterialTheme.typography.bodyMedium)
                Text("دوره‌های بستهٔ نیازمند بررسی: ${Persian.digits(mismatches)}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        FoodCostCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("یادآوری پشتیبان", style = MaterialTheme.typography.titleMedium)
                Text(BackupReminder.last(lastBackup), style = MaterialTheme.typography.bodyMedium)
                actions.backupReminder?.let { reminder ->
                    Text(reminder, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = actions::snoozeBackupReminder, modifier = Modifier.testTag("health-snooze-backup")) { Text("سه روز بعد یادآوری کن") }
                }
                Button(onClick = openBackup, modifier = Modifier.testTag("health-open-backup")) { Text("رفتن به پشتیبان و بازیابی") }
            }
        }
    }
}
