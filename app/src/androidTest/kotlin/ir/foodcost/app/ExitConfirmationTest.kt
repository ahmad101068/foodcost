package ir.foodcost.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A separate instrumentation invocation checks Back at the root calculator.
 * Do not merge into WalkThroughTest: FoodCostApplication's container lives beyond
 * an ActivityScenario and the two tests otherwise share persisted in-memory state.
 */
@RunWith(AndroidJUnit4::class)
class ExitConfirmationTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    @Before
    fun clearStoredState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(ir.foodcost.app.data.SqliteOperationStore.DATABASE)
        context.getSharedPreferences("foodcost", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "calculator-draft.json").delete()
    }

    @Test
    fun calculatorBackAsksBeforeClosing() {
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.waitUntil(15_000) {
                rule.onAllNodesWithTag("row-0-name").fetchSemanticsNodes().isNotEmpty()
            }
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            // A first press may only dismiss the system's open software keyboard.
            device.pressBack()
            rule.waitForIdle()
            if (rule.onAllNodesWithText("آیا می‌خواهید از برنامه خارج شوید؟").fetchSemanticsNodes().isEmpty()) {
                device.pressBack()
            }
            rule.waitUntil(15_000) {
                rule.onAllNodesWithText("آیا می‌خواهید از برنامه خارج شوید؟").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onAllNodesWithText("انصراف").onFirst().performClick()
            rule.waitForIdle()
            rule.onAllNodesWithText("آیا می‌خواهید از برنامه خارج شوید؟").assertCountEquals(0)
            rule.onAllNodesWithTag("row-0-name").onFirst().assertExists()
        }
    }
}
