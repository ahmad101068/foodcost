package ir.foodcost.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.foodcost.app.data.SqliteOperationStore
import ir.foodcost.core.costing.CostingRules
import ir.foodcost.core.money.PriceRounding
import ir.foodcost.core.pricing.*
import ir.foodcost.presentation.*
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A fresh instrumentation process verifies actual UI conversions, basis labels and extreme input. */
@RunWith(AndroidJUnit4::class)
class PricingMethodsTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val container get() = (context.applicationContext as FoodCostApplication).container

    @Test fun sectionsConvertSafelyAndAcceptedOnlinePercentagesRemainUsable() {
        context.deleteDatabase(SqliteOperationStore.DATABASE)
        context.getSharedPreferences("foodcost", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "calculator-draft.json").delete()
        File(context.filesDir, "pricing-screenshots").deleteRecursively()
        val today = LocalDate.now()
        runBlocking {
            container.change { ledger -> ledger.saveSettings(
                costing = CostingRules(defaultMiscPercent = BigDecimal.ZERO),
                pricing = ledger.state.pricing.copy(scope = PricingScope.MENU_SECTIONS,
                    defaultMethod = PricingMethod.TargetFoodCost(BigDecimal(40)),
                    methodByCategory = linkedMapOf("غذای اصلی" to PricingMethod.TargetFoodCost(BigDecimal(40))),
                    vatPercent = BigDecimal.ZERO, rounding = PriceRounding(100_000, PriceRounding.Direction.DOWN)),
                effectiveFrom = today,
            ) }
        }
        container.snoozeBackupReminder()
        container.quickDraft.value = QuickInput(dishName = "آزمون قیمت", menuPrice = "250000", miscPercent = BigDecimal.ZERO,
            rows = listOf(QuickRow("مواد", "100000", "1000")),
            extras = listOf(QuickExtra("دستمزد", "10000"), QuickExtra("بسته‌بندی", "20000", true)))
        container.saveDraft()

        ActivityScenario.launch(MainActivity::class.java).use {
            showChannelDetails()
            rule.onNodeWithTag("channel-percent-basis").performScrollTo().assertTextContains("همهٔ هزینه‌های ثبت‌شده", substring = true)
            rule.onNodeWithTag("channel-0-margin").performScrollTo().assertTextContains("۵۶٪", substring = true)
            rule.onNodeWithTag("channel-0-markup").performScrollTo().assertTextContains("۱۲۷٫۳٪", substring = true)
            shot("31-pricing-channel-bases")

            tab("تنظیمات")
            clickTag("settings-section-pricing")
            clickTag("settings-section-sections")
            rule.onNodeWithTag("category-0-percent").assertTextContains("40")
            clickTag("category-0-method-MARGIN")
            rule.onNodeWithTag("category-0-percent").assertTextContains("60")
            clickTag("category-0-method-MARKUP")
            rule.onNodeWithTag("category-0-percent").assertTextContains("150")
            clickTag("settings-save-sections")
            waitMethod { it is PricingMethod.Markup && it.percent.compareTo(BigDecimal(150)) == 0 && it.basis == CostBasis.FOOD_COST }

            // Saved conversion history must not pin a stale section after another committed change.
            for (percent in listOf(200, 150)) {
                runBlocking { container.change { ledger -> ledger.saveSettings(
                    pricing = ledger.state.pricing.copy(methodByCategory = linkedMapOf("غذای اصلی" to PricingMethod.Markup(BigDecimal(percent)))),
                    effectiveFrom = today,
                ) } }
                rule.waitUntil(15_000) {
                    rule.onAllNodes(hasTestTag("category-0-percent") and hasText(percent.toString(), substring = true)).fetchSemanticsNodes().isNotEmpty()
                }
            }

            clickTag("category-0-basis-FULL_COST")
            clickTag("category-0-method-MARGIN")
            rule.onNodeWithTag("category-0-percent").assertTextContains("60")
            clickTag("category-0-method-MARKUP")
            rule.onNodeWithTag("category-0-percent").assertTextContains("150")
            clickTag("settings-save-sections")
            waitMethod { it is PricingMethod.Markup && it.percent.compareTo(BigDecimal(150)) == 0 && it.basis == CostBasis.FULL_COST }
            val committed = container.state.value!!.pricing.methodByCategory
            clickTag("category-0-method-FOOD_COST")
            rule.onNodeWithTag("category-0-conversion-warning").performScrollTo().assertIsDisplayed()
            clickTag("settings-save-sections")
            rule.waitForIdle()
            assertEquals(committed, container.state.value!!.pricing.methodByCategory)
            rule.onNodeWithTag("category-0-percent").performScrollTo().performTextReplacement("40")
            clickTag("settings-save-sections")
            waitMethod { it is PricingMethod.TargetFoodCost && it.percent.compareTo(BigDecimal(40)) == 0 }
            rule.onNodeWithTag("category-0-percent").performScrollTo()
            rule.onNodeWithTag("category-0-equivalents").assertTextContains("روی مواد اولیه", substring = true)
            shot("32-pricing-section-conversions")

            clickTag("settings-back")
            clickTag("settings-back")
            clickTag("settings-section-costs")
            replace("کمیسیون سامانه", "99." + "9".repeat(40))
            clickTag("settings-save-costs")
            rule.waitUntil(15_000) { container.state.value!!.pricing.online.commissionPercent > BigDecimal(99) }
            tab("ماشین‌حساب")
            clickTag("channel-tab-2")
            rule.onNodeWithTag("channel-2-remaining").performScrollTo().assertIsDisplayed()
            showChannelDetails()
            rule.onNodeWithTag("channel-2-same-remaining-unavailable").performScrollTo().assertIsDisplayed()
            shot("33-pricing-near100-commission")

            tab("تنظیمات")
            replace("کمیسیون سامانه", "20")
            replace("تخفیف معمول آنلاین", "99." + "9".repeat(40))
            clickTag("settings-save-costs")
            rule.waitUntil(15_000) { container.state.value!!.pricing.online.discountPercent > BigDecimal(99) }
            tab("ماشین‌حساب")
            clickTag("channel-tab-2")
            rule.onNodeWithTag("channel-2-remaining").performScrollTo().assertIsDisplayed()
            showChannelDetails()
            rule.onNodeWithTag("channel-2-same-remaining-unavailable").performScrollTo().assertIsDisplayed()
            shot("34-pricing-near100-discount")
        }
    }

    private fun waitMethod(matches: (PricingMethod?) -> Boolean) {
        rule.waitUntil(15_000) { matches(container.state.value?.pricing?.methodByCategory?.get("غذای اصلی")) }
    }
    private fun showChannelDetails() {
        if (rule.onAllNodesWithTag("channel-percent-basis").fetchSemanticsNodes().isEmpty()) clickTag("channel-details-toggle")
    }
    private fun clickTag(tag: String) {
        rule.waitUntil(15_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        val node = rule.onNodeWithTag(tag)
        try { node.performScrollTo() } catch (_: AssertionError) { /* Fixed bars do not scroll. */ }
        node.performClick(); rule.waitForIdle()
    }
    private fun tab(label: String) { rule.onAllNodesWithText(label).onFirst().performClick(); rule.waitForIdle() }
    private fun replace(label: String, value: String) {
        rule.onAllNodesWithText(label).onFirst().performScrollTo().performTextReplacement(value)
    }
    private fun shot(name: String) {
        rule.waitForIdle(); dismissEmulatorLauncherAnr(instrumentation)
        val root = File(context.filesDir, "pricing-screenshots").apply { mkdirs() }
        File(root, "$name.png").outputStream().use { check(checkNotNull(instrumentation.uiAutomation.takeScreenshot()).compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
