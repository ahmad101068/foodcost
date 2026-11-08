package ir.foodcost.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ir.foodcost.app.AppContainer
import ir.foodcost.data.json.JsonException
import ir.foodcost.data.ledger.BackupPreview
import ir.foodcost.data.ledger.ChangesClosedPeriod
import ir.foodcost.data.ledger.ConflictingChanges
import ir.foodcost.data.ops.Backup
import ir.foodcost.data.ops.BackupSeal
import ir.foodcost.data.ops.Operation
import ir.foodcost.presentation.BackupReminder
import ir.foodcost.presentation.BackupTexts
import ir.foodcost.presentation.JalaliDate
import ir.foodcost.presentation.Labels
import ir.foodcost.presentation.Persian
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Where an import has got to: waiting for the password of a locked backup, or for a look at what it adds. */
private sealed interface Importing {
    data class Locked(val text: String, val wrong: Boolean) : Importing

    class Review(val operations: List<Operation>, val preview: BackupPreview, val sealed: Boolean, val unreadable: Int) : Importing
}

/**
 * The settings' backup card. A backup is made with a password or without one; one brought back is
 * opened with its password if it has one, and imported only after a look at what it adds. While the
 * stored data was not read whole it also offers the ways on: an emergency backup of everything on
 * the phone, the damaged file saved for a closer look, and going on without what was not read.
 */
@Composable
fun BackupCard(container: AppContainer, today: LocalDate, actions: AppActions) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    // True when the backup being made is an emergency one, made whatever state the data is in.
    var emergency by remember { mutableStateOf(false) }
    var continuing by remember { mutableStateOf(false) }
    // The password of the backup being saved, empty for none; kept only until the file is written.
    var exportPassword by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf<Importing?>(null) }
    val integrity = actions.integrity
    val locked = !integrity.verified
    val unreadable = actions.unreadableRows
    // Rows not read stay on the phone even after the user went on without them; an emergency backup carries them.
    val keptUnreadable = container.hasUnreadableRows
    val damaged = remember(integrity) { container.damagedFiles() }
    // Closed periods whose figures no longer add up to their record; worked out only when some period is closed.
    val closedMismatches = container.state.collectAsState().value?.closedMismatches?.size ?: 0
    val lastBackup by container.lastBackup.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val password = exportPassword
        val isEmergency = emergency
        exportPassword = ""
        if (uri != null) {
            scope.launch {
                val saved = try {
                    withContext(Dispatchers.IO) {
                        val text = if (isEmergency) container.exportEmergencyBackup() else container.exportVerifiedBackup()
                        writeText(context, uri, if (password.isEmpty()) text else BackupSeal.seal(text, password.toCharArray()))
                    }
                } catch (e: Exception) {
                    false
                }
                if (saved) container.backupSaved()
                actions.say(
                    when {
                        !saved -> "پشتیبان ذخیره نشد."
                        isEmergency -> "پشتیبان اضطراری ذخیره شد."
                        password.isEmpty() -> "پشتیبان ذخیره شد."
                        else -> "پشتیبان رمزدار ذخیره شد. رمز را جایی یادداشت کنید."
                    },
                )
            }
        }
    }

    val damagedLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = try {
                    withContext(Dispatchers.IO) { writeZip(context, uri, container.damagedFiles()) }
                } catch (e: Exception) {
                    false
                }
                actions.say(if (saved) "پروندهٔ خراب ذخیره شد." else "پروندهٔ خراب ذخیره نشد.")
            }
        }
    }

    // A look at what a readable backup adds; nothing is imported until it is confirmed.
    suspend fun review(plain: String, sealed: Boolean) {
        val read = try {
            withContext(Dispatchers.IO) { Backup.read(plain) to Backup.unreadable(plain).size }
        } catch (e: JsonException) {
            actions.say("این فایل پشتیبان معتبر نیست.")
            return
        } catch (e: Exception) {
            actions.say("فایل خوانده نشد.")
            return
        }
        val (operations, unreadableIn) = read
        val preview = container.previewBackup(operations)
        when {
            preview.conflicts > 0 -> actions.say(BackupTexts.conflicts(preview.conflicts))
            preview.changesClosed > 0 -> actions.say(BackupTexts.changesClosed(preview.changesClosed))
            preview.fresh == 0 -> actions.say("این پشتیبان چیز تازه‌ای نداشت.")
            else -> importing = Importing.Review(operations, preview, sealed, unreadableIn)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val read = try {
                    withContext(Dispatchers.IO) { readText(context, uri)?.let { it to BackupSeal.isSealed(it) } }
                } catch (e: Exception) {
                    null
                }
                when {
                    read == null -> actions.say("فایل خوانده نشد.")
                    read.second -> importing = Importing.Locked(read.first, wrong = false)
                    else -> review(read.first, sealed = false)
                }
            }
        }
    }

    SettingsCard("پشتیبان") {
        Text(
            "از همهٔ داده‌ها یک فایل پشتیبان بسازید و جای امنی نگه دارید. با همین فایل داده‌ها روی گوشی دیگر هم برمی‌گردند. " +
                "پشتیبان خودکار اندروید داده‌های فودکاست را نمی‌برد؛ اگر برنامه حذف یا گوشی عوض شود، داده‌ها فقط از همین پشتیبان برمی‌گردند.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            BackupReminder.last(lastBackup),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("last-backup"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    emergency = false
                    exporting = true
                },
                enabled = !locked,
            ) { Text("ساخت پشتیبان") }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text("بازگرداندن") }
        }
        if (locked) {
            Text(
                BackupTexts.locked(integrity),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("locked-ways"),
            )
        } else if (keptUnreadable) {
            Text(
                "${Persian.digits(container.unreadableRowsKept)} ردیفِ خوانده‌نشده را به انتخاب خودتان کنار گذاشته‌اید؛ متنشان روی گوشی مانده و پشتیبان اضطراری آن را هم می‌برد.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (locked || keptUnreadable || damaged.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (keptUnreadable || (locked && !integrity.setAside)) {
                    OutlinedButton(
                        onClick = {
                            emergency = true
                            exporting = true
                        },
                        modifier = Modifier.testTag("emergency-backup"),
                    ) { Text("پشتیبان اضطراری") }
                }
                if (damaged.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { damagedLauncher.launch("foodcost-damaged-${JalaliDate.of(today).isoLike()}.zip") },
                        modifier = Modifier.testTag("save-damaged"),
                    ) { Text("ذخیرهٔ پروندهٔ خراب") }
                }
                if (locked) {
                    OutlinedButton(
                        onClick = { continuing = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("continue-without"),
                    ) { Text(if (integrity.setAside) "شروع با داده‌های خالی" else "ادامه بدون آن‌ها") }
                }
            }
        }
        if (closedMismatches > 0) {
            Text(BackupTexts.closedMismatches(closedMismatches), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }

    if (exporting) {
        ExportDialog(
            emergency = emergency,
            unreadable = if (emergency) 0 else unreadable,
            onDismiss = { exporting = false },
            onExport = { password ->
                exporting = false
                exportPassword = password
                val kind = if (emergency) "foodcost-emergency" else "foodcost"
                exportLauncher.launch("$kind-${JalaliDate.of(today).isoLike()}.json")
            },
        )
    }

    if (continuing) {
        ConfirmDialog(
            title = if (integrity.setAside) "شروع با داده‌های خالی؟" else "ادامه بدون تغییرهای خوانده‌نشده؟",
            text = BackupTexts.continueWithout(integrity),
            confirm = if (integrity.setAside) "شروع کن" else "ادامه بده",
            onConfirm = {
                continuing = false
                container.continueWithoutUnreadable()
                actions.say("ثبت و پشتیبان دوباره باز شد.")
            },
            onDismiss = { continuing = false },
        )
    }

    when (val stage = importing) {
        null -> Unit
        is Importing.Locked -> PasswordDialog(
            wrong = stage.wrong,
            onDismiss = { importing = null },
            onOpen = { password ->
                scope.launch {
                    val plain = try {
                        withContext(Dispatchers.IO) { BackupSeal.open(stage.text, password.toCharArray()) }
                    } catch (e: BackupSeal.WrongPassword) {
                        importing = stage.copy(wrong = true)
                        return@launch
                    } catch (e: Exception) {
                        importing = null
                        actions.say("این فایل پشتیبان معتبر نیست.")
                        return@launch
                    }
                    importing = null
                    review(plain, sealed = true)
                }
            },
        )
        is Importing.Review -> AlertDialog(
            onDismissRequest = { importing = null },
            title = { Text("وارد کردن پشتیبان") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(BackupTexts.preview(stage.preview, stage.sealed, stage.unreadable), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "چیزی از داده‌های این گوشی پاک نمی‌شود؛ تغییرهایی که از قبل هست دوباره اضافه نمی‌شوند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        importing = null
                        scope.launch {
                            val added = try {
                                container.receive(stage.operations)
                            } catch (e: ConflictingChanges) {
                                actions.say(BackupTexts.conflicts(e.count))
                                return@launch
                            } catch (e: ChangesClosedPeriod) {
                                actions.say(BackupTexts.changesClosed(e.count))
                                return@launch
                            } catch (e: Exception) {
                                actions.say("پشتیبان وارد نشد.")
                                return@launch
                            }
                            actions.say("${Persian.digits(added)} تغییر از پشتیبان اضافه شد.")
                        }
                    },
                    modifier = Modifier.testTag("import-confirm"),
                ) { Text("وارد کن") }
            },
            dismissButton = { TextButton(onClick = { importing = null }) { Text("برگشت") } },
        )
    }
}

/** Asks for an optional password for the backup about to be made, with why one is worth it. */
@Composable
private fun ExportDialog(emergency: Boolean, unreadable: Int, onDismiss: () -> Unit, onExport: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (emergency) "پشتیبان اضطراری" else "ساخت پشتیبان") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (emergency) Text(BackupTexts.EMERGENCY_ADVICE, style = MaterialTheme.typography.bodyMedium)
                Text(BackupTexts.PASSWORD_ADVICE, style = MaterialTheme.typography.bodyMedium)
                PasswordField(password, { password = it; error = null }, "رمز (اگر می‌خواهید)", ImeAction.Next)
                if (password.isNotEmpty()) PasswordField(again, { again = it; error = null }, "تکرار رمز", ImeAction.Done)
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    error = when {
                        password.isEmpty() -> null
                        password.length < MIN_PASSWORD -> "رمز دست‌کم ${Persian.digits(MIN_PASSWORD)} حرف باشد."
                        again != password -> "دو رمز یکی نیستند."
                        else -> null
                    }
                    // A whole backup is not made of data not read whole; the emergency one is.
                    if (unreadable > 0) error = Labels.DATA_NOT_VERIFIED
                    if (error == null) onExport(password)
                },
            ) { Text(if (password.isEmpty()) "ساخت بدون رمز" else "ساخت با رمز") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("برگشت") } },
    )
}

/** Asks for the password of a locked backup, again if the last one did not open it. */
@Composable
private fun PasswordDialog(wrong: Boolean, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("پشتیبان رمزدار") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("این پشتیبان رمز دارد. رمزی را که هنگام ساختنش گذاشتید بنویسید.", style = MaterialTheme.typography.bodyMedium)
                PasswordField(password, { password = it }, "رمز", ImeAction.Done)
                if (wrong) Text(BackupTexts.WRONG_PASSWORD, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { onOpen(password) }, enabled = password.isNotEmpty()) { Text("باز کن") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("برگشت") } },
    )
}

@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String, imeAction: ImeAction) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val MIN_PASSWORD = 6

private fun writeText(context: Context, uri: Uri, text: String): Boolean =
    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null

private fun readText(context: Context, uri: Uri): String? =
    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }

/** The damaged database files, as they are, in one zip file. */
private fun writeZip(context: Context, uri: Uri, files: List<File>): Boolean =
    context.contentResolver.openOutputStream(uri)?.use { out ->
        val zip = ZipOutputStream(out)
        for (file in files) {
            zip.putNextEntry(ZipEntry(file.name))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        zip.finish()
    } != null
