package ir.foodcost.app

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import ir.foodcost.app.data.SqliteOperationStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The app root asks before finishing; child settings and Help still consume Back normally. */
@RunWith(AndroidJUnit4::class)
class ExitConfirmationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before fun cleanStart() {
        UiDevice.getInstance(instrumentation).wakeUp()
        dismissEmulatorLauncherAnr(instrumentation)
        val context = instrumentation.targetContext
        context.deleteDatabase(SqliteOperationStore.DATABASE)
        context.getSharedPreferences("foodcost", Context.MODE_PRIVATE).edit().clear().commit()
        listOf("", ".bak", ".new", ".tmp").forEach { File(context.filesDir, "calculator-draft.json$it").delete() }
    }

    @Test fun cancelExitKeepsTheCalculatorOpen() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForTag("row-0-name")
            requestRootBack(scenario)
            waitForTag("exit-confirmation")
            compose.onNodeWithTag("exit-stay").performClick()
            compose.onNodeWithTag("exit-confirmation").assertDoesNotExist()
            compose.onNodeWithTag("row-0-name").assertIsDisplayed()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test fun confirmingExitFinishesTheActivity() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForTag("row-0-name")
            requestRootBack(scenario)
            waitForTag("exit-confirmation")
            compose.onNodeWithTag("exit-confirm").performClick()
            // finish() is asynchronous: the first idle frame can still be STARTED while
            // Android moves the activity through onStop/onDestroy on the main thread.
            compose.waitUntil(timeoutMillis = 15_000) {
                scenario.state == Lifecycle.State.DESTROYED
            }
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        }
    }

    @Test fun systemBackFromASettingsSectionReturnsToItsOverview() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openSettingsOverview()
            compose.onNodeWithTag("settings-section-pricing").performScrollTo().performClick()
            waitForTag("general-pricing-percent")
            // Dispatch app Back without a focus-dependent IME interception on the emulator.
            // The physical Back still closes the keyboard first when it is showing.
            requestRootBack(scenario)
            waitForTag("settings-overview")
            compose.onNodeWithTag("exit-confirmation").assertDoesNotExist()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test fun helpRoundTripKeepsTheSettingsPageAndUnconfirmedDraft() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openSettingsOverview()
            compose.onNodeWithTag("settings-section-pricing").performScrollTo().performClick()
            compose.onNodeWithTag("pricing-scope-general").performScrollTo().performClick()
            compose.onNodeWithTag("general-pricing-percent").performScrollTo().performTextReplacement("41.25")
            // The fixed toolbar Back target must work with the keyboard still open.
            compose.onNodeWithTag("settings-back").performClick()
            waitForTag("settings-overview")
            compose.onNodeWithTag("settings-section-help").performScrollTo().performClick()
            compose.onNodeWithText("کار با برنامه").performScrollTo().performClick()
            compose.onNodeWithText("حساب یک غذا").assertIsDisplayed()
            requestRootBack(scenario)
            waitForTag("settings-back")
            compose.onNodeWithText("کار با برنامه").assertIsDisplayed()
            compose.onNodeWithTag("settings-back").performClick()
            compose.onNodeWithTag("settings-section-pricing").performScrollTo().performClick()
            compose.onNodeWithTag("general-pricing-percent").performScrollTo().assertTextContains("41.25")
        }
    }

    private fun requestRootBack(scenario: ActivityScenario<MainActivity>) {
        // Separate hiding the IME from the real system Back whose navigation we are testing.
        hideKeyboard(scenario)
        physicalBack(scenario)
    }

    private fun focusedWindow(scenario: ActivityScenario<MainActivity>) {
        val device = UiDevice.getInstance(instrumentation)
        device.wakeUp()
        compose.waitUntil(timeoutMillis = 15_000) {
            dismissEmulatorLauncherAnr(instrumentation)
            var focused = false
            scenario.onActivity { focused = it.hasWindowFocus() }
            focused
        }
    }

    private fun physicalBack(scenario: ActivityScenario<MainActivity>) {
        focusedWindow(scenario)
        // UiDevice's injection can return false on the CI image. Shell input still routes an
        // actual system key through the focused window/IME; it does not call the app's handler.
        val output = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK"),
        ).use { it.readBytes().toString(Charsets.UTF_8) }
        check(!output.contains("Error", ignoreCase = true) && !output.contains("Exception")) {
            "System Back command failed: $output"
        }
        compose.waitForIdle()
    }

    private fun hideKeyboard(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity {
            val keyboard = it.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            keyboard.hideSoftInputFromWindow(it.window.decorView.windowToken, 0)
            it.currentFocus?.clearFocus()
        }
        waitForIme(scenario, visible = false)
        instrumentation.waitForIdleSync()
        UiDevice.getInstance(instrumentation).waitForIdle()
        compose.waitForIdle()
    }

    @Test fun physicalBackClosesTheKeyboardBeforeRequestingExit() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForTag("row-0-name")
            focusedWindow(scenario)
            compose.onNodeWithTag("row-0-name").performScrollTo().performClick()
            waitForIme(scenario, visible = true)
            physicalBack(scenario)
            waitForIme(scenario, visible = false)
            compose.onNodeWithTag("exit-confirmation").assertDoesNotExist()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            physicalBack(scenario)
            waitForTag("exit-confirmation")
        }
    }

    private fun waitForIme(scenario: ActivityScenario<MainActivity>, visible: Boolean) {
        compose.waitUntil(timeoutMillis = 15_000) {
            var showing = false
            scenario.onActivity {
                showing = androidx.core.view.ViewCompat.getRootWindowInsets(it.window.decorView)
                    ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
            }
            showing == visible
        }
    }

    private fun openSettingsOverview() {
        waitForTag("row-0-name")
        compose.onNodeWithText("تنظیمات").performClick()
        compose.waitForIdle()
        if (compose.onAllNodesWithTag("settings-overview").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithTag("settings-back").performClick()
        }
        waitForTag("settings-overview")
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
