package ir.foodcost.app

import android.content.Context
import ir.foodcost.app.data.SqliteOperationStore
import ir.foodcost.core.money.DisplayCurrency
import ir.foodcost.data.clock.HybridClock
import ir.foodcost.data.ledger.Ledger
import ir.foodcost.data.ledger.LedgerState
import ir.foodcost.data.ops.Actor
import ir.foodcost.data.ops.Role
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
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

    private val draftFile = File(appContext.filesDir, DRAFT_FILE)
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The calculator tab's calculation. Switching tabs does not lose it, and it is written to the
     * phone's storage as it is typed, so it is still there after the phone closes the app.
     */
    val quickDraft = MutableStateFlow(readDraft())

    init {
        background.launch {
            quickDraft.drop(1).collectLatest { input ->
                delay(DRAFT_PAUSE_MILLIS) // one write when typing pauses, not one per key
                writeDraft(input)
            }
        }
    }

    /** Writes the calculation now, as the app leaves the screen and may be closed. */
    fun saveDraft() = writeDraft(quickDraft.value)

    private fun readDraft(): QuickInput = try {
        if (draftFile.exists()) QuickDrafts.decode(draftFile.readText()) ?: QuickInput() else QuickInput()
    } catch (e: IOException) {
        QuickInput()
    }

    /** Replaces the stored draft in one step, so a write cut short never leaves half a draft. */
    @Synchronized
    private fun writeDraft(input: QuickInput) {
        try {
            if (input == QuickInput()) {
                draftFile.delete()
                return
            }
            val temp = File(draftFile.path + ".tmp")
            temp.writeText(QuickDrafts.encode(input))
            if (!temp.renameTo(draftFile)) draftFile.writeText(QuickDrafts.encode(input))
        } catch (e: IOException) {
            // The calculation is still on the screen; only its copy on the phone is older.
        }
    }

    /** Stored rows that could not be read, if any. */
    val unreadableRows: Int get() = store?.unreadable ?: 0

    /** Opens the ledger once, reading every stored operation off the main thread. */
    suspend fun open(): Ledger = ledger ?: openLock.withLock {
        ledger ?: withContext(Dispatchers.IO) {
            val s = SqliteOperationStore(appContext)
            store = s
            Ledger(s, HybridClock(deviceId()), Actor(OWNER_ID, Role.OWNER))
        }.also {
            ledger = it
            _state.value = it.state
        }
    }

    /** Runs a change off the main thread, then publishes the new state. */
    suspend fun <T> change(block: (Ledger) -> T): T {
        val l = open()
        val result = withContext(Dispatchers.IO) { block(l) }
        _state.value = l.state
        return result
    }

    fun setCurrency(currency: DisplayCurrency) {
        prefs.edit().putString(KEY_CURRENCY, currency.name).apply()
        _currency.value = currency
    }

    private fun readCurrency(): DisplayCurrency =
        prefs.getString(KEY_CURRENCY, null)?.let { name -> DisplayCurrency.entries.firstOrNull { it.name == name } }
            ?: DisplayCurrency.TOMAN

    private fun deviceId(): String = prefs.getString(KEY_DEVICE, null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_DEVICE, it).apply() }

    private companion object {
        const val KEY_CURRENCY = "currency"
        const val KEY_DEVICE = "device"
        const val OWNER_ID = "owner"
        const val DRAFT_FILE = "calculator-draft.json"
        const val DRAFT_PAUSE_MILLIS = 400L
    }
}
