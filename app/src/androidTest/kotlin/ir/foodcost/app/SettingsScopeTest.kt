package ir.foodcost.app

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.foodcost.app.data.SqliteOperationStore
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.core.pricing.PricingScope
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The scope is an exclusive choice, while the inactive rule remains available for later use. */
@RunWith(AndroidJUnit4::class)
class SettingsScopeTest {
    @get:Rule val rule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as FoodCostApplication).container

    @Before
    fun cleanState() {
        context.deleteDatabase(SqliteOperationStore.DATABASE)
        context.getSharedPreferences("foodcost", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "calculator-draft.json").delete()
    }

    @Test
    fun inactiveRulesRemainSavedAndUnconfirmedGeneralDraftCannotLeakIntoCostSave() {
        runBlocking {
            container.change { ledger ->
                ledger.saveSettings(
                    pricing = ledger.state.pricing.copy(
                        scope = PricingScope.MENU_SECTIONS,
                        defaultMethod = PricingMethod.TargetFoodCost(BigDecimal(35)),
                        methodByCategory = linkedMapOf("غذای اصلی" to PricingMethod.TargetFoodCost(BigDecimal(30))),
                    ),
                    effectiveFrom = LocalDate.now(),
                )
            }
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            clickText("تنظیمات")
            waitForSettings()
            if (!exists("settings-overview")) clickTag("settings-back")

            clickTag("settings-section-pricing")
            waitForTag("general-pricing-percent")
            rule.onNodeWithTag("general-pricing-percent").assertIsNotEnabled()

            clickTag("pricing-scope-general")
            rule.onNodeWithTag("general-pricing-percent").assertIsEnabled().assertTextContains("35")
            clickTag("settings-save-pricing")
            waitForScope(PricingScope.GENERAL)
            assertSavedRates()

            clickTag("settings-back")
            clickTag("settings-section-sections")
            waitForTag("category-0-percent")
            rule.onNodeWithTag("category-0-percent").assertIsNotEnabled().assertTextContains("30")
            rule.onNodeWithTag("category-0-name").assertIsEnabled()

            clickTag("settings-back")
            clickTag("settings-section-pricing")
            clickTag("pricing-scope-sections")
            rule.onNodeWithTag("general-pricing-percent").assertIsNotEnabled().assertTextContains("35")
            // The previous success snackbar must not intercept this real touchscreen tap.
            scrolled(rule.onNodeWithTag("settings-save-pricing")).performClick()
            waitForScope(PricingScope.MENU_SECTIONS)
            assertSavedRates()

            clickTag("settings-back")
            clickTag("settings-section-sections")
            waitForTag("category-0-percent")
            rule.onNodeWithTag("category-0-percent").assertIsEnabled().assertTextContains("30")

            // A draft in one section must not be applied by saving a different section.
            clickTag("settings-back")
            clickTag("settings-section-pricing")
            clickTag("pricing-scope-general")
            scrolled(rule.onNodeWithTag("general-pricing-percent")).performTextReplacement("invalid")
            clickTag("settings-back")
            clickTag("settings-section-costs")
            waitForText("کمیسیون سامانه")
            scrolled(rule.onAllNodesWithText("کمیسیون سامانه").onFirst()).performTextReplacement("20")
            clickTag("settings-save-costs")
            rule.waitUntil(timeoutMillis = 15_000) {
                container.state.value?.pricing?.online?.commissionPercent?.compareTo(BigDecimal(20)) == 0
            }
            assertEquals(PricingScope.MENU_SECTIONS, container.state.value!!.pricing.scope)
            assertSavedRates()

            // The unconfirmed draft can still be corrected later; preserving it must not
            // change the policy that the calculator actually reads from the ledger.
            clickTag("settings-back")
            clickTag("settings-section-pricing")
            rule.onNodeWithTag("general-pricing-percent").assertIsEnabled().assertTextContains("invalid")
            assertEquals(PricingScope.MENU_SECTIONS, container.state.value!!.pricing.scope)
        }
    }

    private fun assertSavedRates() {
        val pricing = container.state.value!!.pricing
        assertEquals(PricingMethod.TargetFoodCost(BigDecimal(35)), pricing.defaultMethod)
        assertEquals(PricingMethod.TargetFoodCost(BigDecimal(30)), pricing.methodByCategory["غذای اصلی"])
    }

    private fun waitForScope(scope: PricingScope) {
        try {
            rule.waitUntil(timeoutMillis = 15_000) { container.state.value?.pricing?.scope == scope }
        } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
            // Attach the ledger's committed policy and the actual rendered controls to CI.
            // A stale Compose draft and an operation rejected by the ledger look identical
            // without these diagnostics, so neither case should be guessed at.
            val committed = container.state.value?.pricing
            val tree = rule.onRoot(useUnmergedTree = true).printToString().take(16_000)
            throw AssertionError(
                "Expected committed scope=$scope, actual=${committed?.scope}, " +
                    "default=${committed?.defaultMethod}, categories=${committed?.methodByCategory}. " +
                    "Rendered semantics:\n$tree", failure,
            )
        }
    }

    private fun exists(tag: String): Boolean = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun waitForSettings() {
        rule.waitUntil(timeoutMillis = 15_000) { exists("settings-overview") || exists("settings-back") }
    }

    private fun waitForTag(tag: String) {
        rule.waitUntil(timeoutMillis = 15_000) { exists(tag) }
    }

    private fun waitForText(text: String) {
        rule.waitUntil(timeoutMillis = 15_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun clickTag(tag: String) {
        waitForTag(tag)
        // A recently shown Snackbar can cover the physical centre of the bottom Save button.
        // Invoke the same accessibility action rather than sending an obstructed coordinate.
        scrolled(rule.onNodeWithTag(tag)).performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
    }

    private fun clickText(text: String) {
        waitForText(text)
        scrolled(rule.onAllNodesWithText(text).onFirst()).performClick()
        rule.waitForIdle()
    }

    private fun scrolled(node: SemanticsNodeInteraction): SemanticsNodeInteraction {
        try {
            node.performScrollTo()
        } catch (_: AssertionError) {
            // Fixed app bars and bottom navigation are outside the scroll container.
        }
        return node
    }
}
