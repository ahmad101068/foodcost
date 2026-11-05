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
import ir.foodcost.data.ops.Operation
import ir.foodcost.data.ops.Role
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

    /** Stored rows that could not be read, if any. */
    val unreadableRows: Int get() = store?.unreadable ?: 0

    /** All export callers must use this guarded entry point rather than Ledger.exportBackup(). */
    suspend fun exportVerifiedBackup(): String {
        val l = open()
        return withContext(Dispatchers.IO) {
            check(unreadableRows == 0) { "Incomplete ledger cannot be exported as a valid backup" }
            l.exportBackup()
        }
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
            s.setAside?.let { prefs.edit().putString(KEY_SET_ASIDE, it.name).commit() }
            Ledger(s, HybridClock(deviceId()), Actor(OWNER_ID, Role.OWNER))
        }.also {
            ledger = it
            _state.value = it.state
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
                // Never mutate a partial financial ledger. A backup restore uses receive(),
                // which remains available to mend unreadable stored operations.
                check(unreadableRows == 0) {
                    "Financial data is incomplete; restore a valid backup before making changes"
                }
                l.atomically(block)
            }
        } finally {
            _state.value = l.state
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

    private fun readCurrency(): DisplayCurrency =
        prefs.getString(KEY_CURRENCY, null)?.let { name -> DisplayCurrency.entries.firstOrNull { it.name == name } }
            ?: DisplayCurrency.TOMAN

    /**
     * This phone's id, kept in a file of its own that Android's backup and the move to a new phone
     * leave out (res/xml/data_extraction_rules.xml): a phone restored from a backup gets an id of
     * its own, so two phones never number their changes alike. Versions before 0.7.3 kept the id
     * with the other settings, which an older backup brings back to every phone restored from it,
     * so it is not taken over: without its own file, the phone makes a new id. Its earlier changes
     * keep the id they were made under.
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
        const val KEY_DEVICE = "device"
        const val DEVICE_FILE = "device"
        const val OWNER_ID = "owner"
        const val DRAFT_FILE = "calculator-draft.json"
        const val DRAFT_PAUSE_MILLIS = 400L
    }
}
