package ir.foodcost.app

import android.content.Context
import android.util.AtomicFile
import ir.foodcost.app.data.SqliteOperationStore
import ir.foodcost.core.money.DisplayCurrency
import ir.foodcost.data.clock.HybridClock
import ir.foodcost.data.ledger.BackupPreview
import ir.foodcost.data.ledger.Ledger
import ir.foodcost.data.ledger.LedgerState
import ir.foodcost.data.ops.Actor
import ir.foodcost.data.ops.Backup
import ir.foodcost.data.ops.Operation
import ir.foodcost.data.ops.Role
import ir.foodcost.presentation.BackupReminder
import ir.foodcost.presentation.DataIntegrity
import ir.foodcost.presentation.DataNotVerified
import ir.foodcost.presentation.Days
import ir.foodcost.presentation.QuickDrafts
import ir.foodcost.presentation.QuickInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID

/** The app's single ledger and the user's display choices. Lives as long as the app. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("foodcost", Context.MODE_PRIVATE)
    private val openLock = Mutex()

    @Volatile
    private var ledger: Ledger? = null

    @Volatile
    private var store: SqliteOperationStore? = null

    private val _state = MutableStateFlow<LedgerState?>(null)

    /** Null until the stored operations have been read. */
    val state: StateFlow<LedgerState?> = _state.asStateFlow()

    private val _currency = MutableStateFlow(readCurrency())
    val currency: StateFlow<DisplayCurrency> = _currency.asStateFlow()

    private val _today = MutableStateFlow(LocalDate.now())

    /**
     * The date every price is recorded with. It is read again when the app comes back to the
     * screen and at midnight, so a phone left on overnight dates the next day's prices right.
     */
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    // Declared before the work started below, which reads them.
    private val _integrity = MutableStateFlow(DataIntegrity())

    /** Whether the stored data was read whole: while it was not, no figure is shown and nothing is recorded. */
    val integrity: StateFlow<DataIntegrity> = _integrity.asStateFlow()

    private val _backupReminder = MutableStateFlow<String?>(null)

    /** What to say about making a backup, or null: Android's own backup leaves this app's data out. */
    val backupReminder: StateFlow<String?> = _backupReminder.asStateFlow()

    private val _lastBackup = MutableStateFlow(prefs.getLong(KEY_LAST_BACKUP_AT, 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli))

    /** When the last backup was saved on this phone, or null for never. */
    val lastBackup: StateFlow<Instant?> = _lastBackup.asStateFlow()

    // Written whole or not at all: a write cut short, by a full phone or the app being closed,
    // leaves the last whole draft, never half of one.
    private val draftFile = AtomicFile(File(appContext.filesDir, DRAFT_FILE))
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The calculator tab's calculation. Switching tabs does not lose it, and it is written to the
     * phone's storage as it is typed, so it is still there after the phone closes the app.
     */
    val quickDraft = MutableStateFlow(readDraft())

    init {
        File(appContext.filesDir, "$DRAFT_FILE.tmp").delete() // left by versions before the draft was written whole
        background.launch {
            // The amounts and the currency they are in are written together. A currency change
            // sets both at once, and the pause lets only the pair after it be written.
            combine(quickDraft, _currency) { input, currency -> input to currency }.drop(1).collectLatest { (input, currency) ->
                delay(DRAFT_PAUSE_MILLIS) // one write when typing pauses, not one per key
                writeDraft(input, currency)
            }
        }
        background.launch {
            while (true) {
                delay(Days.millisToTomorrow(ZonedDateTime.now()))
                refreshToday()
            }
        }
    }

    /** Reads the date again, as the app comes back to the screen, perhaps on another day. */
    fun refreshToday() {
        _today.value = LocalDate.now()
        publishReminder()
    }

    /** Writes the calculation now, as the app leaves the screen and may be closed. */
    fun saveDraft() = writeDraft(quickDraft.value, _currency.value)

    private fun readDraft(): QuickInput = try {
        QuickDrafts.decode(draftFile.readFully().toString(Charsets.UTF_8), _currency.value) ?: QuickInput()
    } catch (e: IOException) {
        QuickInput() // none yet, or not readable
    }

    /** Replaces the stored draft in one step, so a write cut short never leaves half a draft. */
    @Synchronized
    private fun writeDraft(input: QuickInput, currency: DisplayCurrency) {
        if (input == QuickInput()) {
            draftFile.delete()
            return
        }
        val out = try {
            draftFile.startWrite()
        } catch (e: IOException) {
            return // the calculation is still on the screen; only its copy on the phone is older
        }
        try {
            out.write(QuickDrafts.encode(input, currency).toByteArray(Charsets.UTF_8))
            draftFile.finishWrite(out)
        } catch (e: IOException) {
            draftFile.failWrite(out)
        }
    }

    /** Stored rows that could not be read and that the user has not chosen to go on without. */
    val unreadableRows: Int get() = (store?.unreadableIds.orEmpty() - acceptedUnreadable()).size

    // Rows the user chose to go on without: left out of the figures, kept in the table as they are.
    private fun acceptedUnreadable(): Set<String> = prefs.getStringSet(KEY_UNREADABLE_ACCEPTED, null).orEmpty()

    // A damaged database was set aside and the app opened empty; it stays so until a backup is
    // brought back or the user chooses to start empty.
    private val recoveryRequired: Boolean get() = prefs.getBoolean(KEY_RECOVERY_REQUIRED, false)

    /** Unsafe while rows are not read or a damaged database was set aside, until the user chooses what to do. */
    val financialDataUnverified: Boolean get() = unreadableRows > 0 || recoveryRequired

    private fun publishIntegrity() {
        _integrity.value = DataIntegrity(unreadable = unreadableRows, setAside = recoveryRequired)
    }

    /** All export callers must use this guarded entry point rather than Ledger.exportBackup(). */
    suspend fun exportVerifiedBackup(): String {
        val l = open()
        return withContext(Dispatchers.IO) {
            if (financialDataUnverified) throw DataNotVerified()
            l.exportBackup()
        }
    }

    /**
     * An emergency backup, made whatever state the data is in: every operation that was read and,
     * as text, every row that was not, so nothing on the phone is lost. Bringing it back says what
     * it lacks, and versions before this one do not open it.
     */
    suspend fun exportEmergencyBackup(): String {
        val l = open()
        return withContext(Dispatchers.IO) { Backup.write(l.operations(), store?.unreadableText().orEmpty()) }
    }

    /** Every stored row that could not be read, the ones the user chose to go on without included. */
    val unreadableRowsKept: Int get() = store?.unreadable ?: 0

    /** True when some stored rows are not read, so the backup that keeps everything is an emergency one. */
    val hasUnreadableRows: Boolean get() = unreadableRowsKept > 0

    /** The damaged database files set aside on this phone, to save elsewhere for a closer look. */
    fun damagedFiles(): List<File> = SqliteOperationStore.damagedFiles(appContext)

    /**
     * The user's choice to go on without what could not be read: rows not read stay in the table as
     * they are and out of the figures, a damaged file set aside stays where it is, and changes and
     * backups open again. A backup that brings those rows back still mends them.
     */
    fun continueWithoutUnreadable() {
        prefs.edit()
            .putStringSet(KEY_UNREADABLE_ACCEPTED, HashSet(acceptedUnreadable() + store?.unreadableIds.orEmpty()))
            .putBoolean(KEY_RECOVERY_REQUIRED, false)
            .putInt(KEY_UNREADABLE_SEEN, 0)
            .commit()
        publishIntegrity()
        publishReminder()
    }

    /** Notes a backup just saved, of every change stored now, for the reminder and the backup card. */
    fun backupSaved() {
        val stored = ledger?.operations()?.size ?: return
        val now = Instant.now()
        prefs.edit().putLong(KEY_LAST_BACKUP_AT, now.toEpochMilli()).putInt(KEY_LAST_BACKUP_OPS, stored).apply()
        _lastBackup.value = now
        publishReminder()
    }

    /** Puts the reminder off for a few days. */
    fun snoozeBackupReminder() {
        prefs.edit().putLong(KEY_REMINDER_SNOOZED_UNTIL, BackupReminder.snooze(Instant.now()).toEpochMilli()).apply()
        publishReminder()
    }

    private fun publishReminder() {
        val stored = ledger?.operations()?.size ?: return
        val snoozed = prefs.getLong(KEY_REMINDER_SNOOZED_UNTIL, 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        _backupReminder.value = BackupReminder.due(stored, prefs.getInt(KEY_LAST_BACKUP_OPS, 0), _lastBackup.value, snoozed, Instant.now())
    }

    /** Unreadable rows not shown to the user yet: more than when they last said they had read the warning. */
    fun unreadableToWarn(): Int {
        val seen = prefs.getInt(KEY_UNREADABLE_SEEN, 0)
        val now = unreadableRows
        if (now < seen) prefs.edit().putInt(KEY_UNREADABLE_SEEN, now).apply() // some were mended since
        return now.takeIf { it > seen } ?: 0
    }

    fun acknowledgeUnreadable() {
        prefs.edit().putInt(KEY_UNREADABLE_SEEN, unreadableRows).apply()
    }

    /** The name a damaged database file was set aside under, until the user says they read it; or null. */
    fun setAsideToWarn(): String? = prefs.getString(KEY_SET_ASIDE, null)

    fun acknowledgeSetAside() {
        prefs.edit().remove(KEY_SET_ASIDE).apply()
    }

    /** Opens the ledger once, reading every stored operation off the main thread. */
    suspend fun open(): Ledger = ledger ?: openLock.withLock {
        ledger ?: withContext(Dispatchers.IO) {
            val s = SqliteOperationStore(appContext)
            store = s
            // A damaged file set aside is said until the user has read it, even if the app closes first.
            s.setAside?.let {
                prefs.edit().putString(KEY_SET_ASIDE, it.name).putBoolean(KEY_RECOVERY_REQUIRED, true).commit()
            }
            // Rows chosen to go without that a backup has mended since are no longer kept apart.
            val accepted = acceptedUnreadable()
            if (accepted.isNotEmpty()) prefs.edit().putStringSet(KEY_UNREADABLE_ACCEPTED, HashSet(accepted intersect s.unreadableIds)).commit()
            Ledger(s, HybridClock(deviceId()), Actor(OWNER_ID, Role.OWNER))
        }.also {
            ledger = it
            _state.value = it.state
            publishIntegrity()
            publishReminder()
        }
    }

    /**
     * Runs a change off the main thread as one change: what it stores is kept whole or, if it
     * fails part way, not at all. Then publishes the new state.
     */
    suspend fun <T> change(block: (Ledger) -> T): T {
        val l = open()
        val result = try {
            withContext(Dispatchers.IO) {
                // Never change a ledger that was not read whole, until the user chooses what to do.
                // A backup restore uses receive(), which remains available to mend unreadable rows.
                if (financialDataUnverified) throw DataNotVerified()
                l.atomically(block)
            }
        } finally {
            _state.value = l.state
            publishReminder()
        }
        return result
    }

    /** What the changes of a backup would add, worked out off the main thread; nothing is added. */
    suspend fun previewBackup(operations: List<Operation>): BackupPreview {
        val l = open()
        return withContext(Dispatchers.IO) { l.preview(operations) }
    }

    /** Adds the changes of a backup, off the main thread. Returns how many were new. */
    suspend fun receive(operations: List<Operation>): Int {
        val l = open()
        val added = withContext(Dispatchers.IO) { l.receive(operations) }
        _state.value = l.state
        // Only a nonempty imported backup can replace a database set aside as unreadable.
        // The user must still verify that this backup contains their expected historical data.
        if (added > 0 && unreadableRows == 0) prefs.edit().putBoolean(KEY_RECOVERY_REQUIRED, false).commit()
        publishIntegrity()
        publishReminder()
        return added
    }

    fun setCurrency(currency: DisplayCurrency) {
        val old = _currency.value
        if (currency == old) return
        prefs.edit().putString(KEY_CURRENCY, currency.name).apply()
        // The calculator's amounts were typed in the old currency: the same amounts, in the new one.
        quickDraft.value = QuickDrafts.convert(quickDraft.value, old, currency)
        _currency.value = currency
    }

    private fun readCurrency(): DisplayCurrency = when (prefs.getString(KEY_CURRENCY, null)) {
        DisplayCurrency.RIAL.name -> DisplayCurrency.RIAL
        DisplayCurrency.TOMAN.name, null -> DisplayCurrency.TOMAN
        else -> {
            // Older installations may have selected NEW_RIAL. Keep its conversion support in
            // QuickDrafts.decode, but migrate the display choice to an available unit without
            // changing stored rial amounts or the numeric meaning of a saved calculator draft.
            prefs.edit().putString(KEY_CURRENCY, DisplayCurrency.TOMAN.name).apply()
            DisplayCurrency.TOMAN
        }
    }

    /**
     * This phone's id, kept in a file of its own. Android's backup and the move to a new phone leave
     * all of the app's data out since 0.7.8 (res/xml/data_extraction_rules.xml), this file too, so a
     * phone that gets the data back from the app's own backup has an id of its own, and two phones
     * never number their changes alike. Versions before 0.7.3 kept the id with the other settings,
     * which an older Android backup could bring back to every phone restored from it, so it is not
     * taken over: without its own file, the phone makes a new id. Its earlier changes keep the id
     * they were made under.
     */
    private fun deviceId(): String {
        val ids = appContext.getSharedPreferences(DEVICE_FILE, Context.MODE_PRIVATE)
        ids.getString(KEY_DEVICE, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        ids.edit().putString(KEY_DEVICE, id).commit()
        prefs.edit().remove(KEY_DEVICE).apply()
        return id
    }

    private companion object {
        const val KEY_CURRENCY = "currency"
        const val KEY_UNREADABLE_SEEN = "unreadable-seen"
        const val KEY_SET_ASIDE = "damaged-database-set-aside"
        const val KEY_RECOVERY_REQUIRED = "financial-recovery-required"
        const val KEY_UNREADABLE_ACCEPTED = "unreadable-accepted"
        const val KEY_LAST_BACKUP_AT = "last-backup-at"
        const val KEY_LAST_BACKUP_OPS = "last-backup-operations"
        const val KEY_REMINDER_SNOOZED_UNTIL = "backup-reminder-snoozed-until"
        const val KEY_DEVICE = "device"
        const val DEVICE_FILE = "device"
        const val OWNER_ID = "owner"
        const val DRAFT_FILE = "calculator-draft.json"
        const val DRAFT_PAUSE_MILLIS = 400L
    }
}
