package ir.foodcost.app

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import ir.foodcost.app.data.SqliteOperationStore
import ir.foodcost.core.model.ComponentRef
import ir.foodcost.core.model.PurchasePrice
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.model.RecipeLine
import ir.foodcost.core.model.RecipeVersion
import ir.foodcost.core.units.Dimension
import ir.foodcost.core.units.Uom
import ir.foodcost.data.clock.HybridClock
import ir.foodcost.data.ledger.Ledger
import ir.foodcost.data.ops.Actor
import ir.foodcost.data.ops.Role
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The audit of 0.7.5 (R-075-01): while a stored change cannot be read, the figures are without it,
 * so every screen says they may be incomplete, and goes on saying it after the warning the app
 * opens with has been read. Here the latest price of the meat cannot be read: the dish is costed on
 * the price before it, and the line is there on the calculator, the dishes and the dish.
 *
 * The app reads its data once, when it first opens: this test runs in a process of its own
 * (scripts/walkthrough.sh), on data of its own in the app's file, which it removes after.
 */
@RunWith(AndroidJUnit4::class)
class UnreadableWarningTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val today = LocalDate.now()

    @Before
    fun dataWithAPriceThatCannotBeRead() {
        clean()
        val store = SqliteOperationStore(context)
        val ledger = Ledger(store, HybridClock("warning-test"), Actor("owner", Role.OWNER))
        val meat = ledger.createIngredient("گوشت", Dimension.MASS)
        ledger.recordPrice(PurchasePrice(meat, today.minusDays(10), BigDecimal.ONE, Uom.KILOGRAM, 16_000_000))
        val latest = (ledger.recordPrice(PurchasePrice(meat, today.minusDays(1), BigDecimal.ONE, Uom.KILOGRAM, 32_000_000)) as Ledger.Outcome.Applied).operation
        val dish = ledger.createRecipe("کباب آزمون", RecipeKind.MENU_ITEM)
        val line = RecipeLine(ComponentRef.IngredientRef(meat), BigDecimal(200), Uom.GRAM)
        ledger.saveRecipeVersion(dish, RecipeVersion(today.minusDays(10), batchLines = listOf(line), miscPercent = BigDecimal.ZERO))
        store.close()
        context.openOrCreateDatabase(SqliteOperationStore.DATABASE, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("UPDATE operations SET body = ? WHERE id = ?", arrayOf("{\"id\": \"cut", latest.id.value))
        }
    }

    @After
    fun clean() {
        context.deleteDatabase(SqliteOperationStore.DATABASE)
        context.getSharedPreferences("foodcost", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "calculator-draft.json").delete()
    }

    @Test
    fun everyScreenBlocksUnverifiedFinancialResultsAfterWarningAcknowledgement() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("بخشی از داده‌ها خوانده نشد")
            click("فهمیدم")
            assertWarning() // the calculator
            click("غذاها")
            assertWarning()
            click("کباب آزمون")
            assertWarning()
            // The latest unreadable price must never lead to a stale confirmed amount.
            compose.waitUntil(timeoutMillis = 15_000) {
                compose.onAllNodes(hasTestTag("unverified-financial-result")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onAllNodes(hasTestTag("cost")).assertCountEquals(0)
        }
    }

    private fun assertWarning() {
        dismissSystemDialogs()
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodes(hasTestTag("unreadable-warning")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("unreadable-warning").assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText("۱ تغییر ذخیره‌شده خوانده نشد", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitFor(text: String) {
        dismissSystemDialogs()
        compose.waitUntil(timeoutMillis = 15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun click(text: String) {
        waitFor(text)
        compose.onAllNodesWithText(text).onFirst().performClick()
        compose.waitForIdle()
    }

    /** As in the walk-through: "Wait" closes the system's dialog about a launcher not answering. */
    private fun dismissSystemDialogs() {
        val device = UiDevice.getInstance(instrumentation)
        repeat(3) {
            val wait = device.findObject(By.res("android", "aerr_wait")) ?: device.findObject(By.text("Wait")) ?: return
            try {
                wait.click()
            } catch (e: StaleObjectException) {
                // gone by itself
            }
            device.waitForIdle()
        }
    }
}
