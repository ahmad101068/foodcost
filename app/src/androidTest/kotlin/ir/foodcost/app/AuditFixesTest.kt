package ir.foodcost.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.foodcost.app.data.SqliteOperationStore
import ir.foodcost.core.costing.CostingRules
import ir.foodcost.core.model.IngredientId
import ir.foodcost.core.model.PurchasePrice
import ir.foodcost.core.model.YieldStage
import ir.foodcost.core.money.PriceRounding
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.core.pricing.PricingScope
import ir.foodcost.core.pricing.PricingSettings
import ir.foodcost.core.units.Dimension
import ir.foodcost.core.units.Uom
import ir.foodcost.presentation.*
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real native input, save and SQLite-backed state for the owner's four audited regressions. */
@RunWith(AndroidJUnit4::class)
class AuditFixesTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val container get() = (context.applicationContext as FoodCostApplication).container
    private val shots get() = File(context.filesDir, "audit-fix-screenshots").apply { mkdirs() }

    @Test
    fun auditedCalculatorFlowsKeepFiguresAndHistoryConsistent() {
        context.deleteDatabase(SqliteOperationStore.DATABASE)
        context.getSharedPreferences("foodcost", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "calculator-draft.json").delete()
        shots.deleteRecursively()
        val today = LocalDate.now()
        var meat: IngredientId? = null
        runBlocking {
            container.change { l ->
                l.saveSettings(costing = CostingRules(BigDecimal.ZERO), pricing = PricingSettings(vatPercent = BigDecimal.ZERO, rounding = PriceRounding(1), scope = PricingScope.GENERAL))
                val created = l.createIngredient("گوشت ممیزی", Dimension.MASS, yields = listOf(YieldStage("پاک کردن", BigDecimal("0.8"))))
                meat = created
                l.recordPrice(PurchasePrice(created, today.minusDays(1), BigDecimal.ONE, Uom.KILOGRAM, 10_000_000))
            }
        }
        val meatId = checkNotNull(meat)
        container.snoozeBackupReminder()
        container.quickDraft.value = QuickInput(dishName = "آزمون ممیزی", menuPrice = "2000000", rows = listOf(QuickRow("گوشت ممیزی", "1000000", "1000"), QuickRow()))
        container.saveDraft()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            figure("cost", "۱٬۲۵۰٬۰۰۰")
            shot("01-typed-name-yield")
            edit("row-0-name", "گوشت ممیزی")
            click("row-0-suggestion-${meatId.value}")
            edit("row-0-name", " گوشت ممیزی ")
            check(container.quickDraft.value.rows.first().ingredientId == meatId)
            figure("cost", "۱٬۲۵۰٬۰۰۰")
            click("calculator-save")
            rule.waitUntil(15_000) { container.quickDraft.value.recipeId != null }
            val id = checkNotNull(container.quickDraft.value.recipeId)
            check(QuickCalculator.compute(checkNotNull(QuickCalculator.open(container.state.value!!, id, today, MoneyFormat(container.currency.value))), container.state.value!!, MoneyFormat(container.currency.value), today).costPerPortion == "۱٬۲۵۰٬۰۰۰ تومان")
            figure("cost", "۱٬۲۵۰٬۰۰۰")
            shot("02-yield-after-save")

            for (bad in listOf("0", "-2", "abc")) {
                edit("calculator-portions", bad)
                scroll("portions-error")
                rule.onNodeWithTag("portions-error").assertIsDisplayed()
                rule.onNodeWithTag("calculator-share").assertIsNotEnabled()
                val before = container.state.value!!.lastAt
                click("calculator-save")
                check(container.state.value!!.lastAt == before)
                val result = QuickCalculator.compute(container.quickDraft.value, container.state.value!!, MoneyFormat(container.currency.value), today)
                check(result.provisional && result.foodCostRials == null && result.suggestedRials == null)
                check(QuickCalculator.shareText(container.quickDraft.value, result, MoneyFormat(container.currency.value)).contains("این حساب ناقص است"))
            }
            shot("03-invalid-portions")
            edit("calculator-portions", "2")
            rule.onNodeWithTag("portions-error").assertDoesNotExist()
            rule.onNodeWithTag("calculator-share").assertIsEnabled()
            figure("cost", "۶۲۵٬۰۰۰")
            edit("calculator-portions", "1")

            runBlocking {
                container.change { l ->
                    l.saveSettings(costing = CostingRules(BigDecimal("20")), pricing = l.state.pricing.copy(defaultMethod = PricingMethod.TargetFoodCost(BigDecimal("50")), vatPercent = BigDecimal("20")), effectiveFrom = today.plusDays(1))
                }
            }
            figure("cost", "۱٬۲۵۰٬۰۰۰")
            figure("suggested", "۳٬۵۷۱٬۴۲۹")
            scroll("calculator-dashboard")
            rule.onNodeWithText("قیمت منو با مالیات ۰٪").assertIsDisplayed()
            shot("04-future-policy-excluded")

            runBlocking {
                container.change { l ->
                    l.setMenuPrice(id, today.minusDays(1), 20_000_000)
                    l.setMenuPrice(id, today, 25_000_000)
                }
            }
            val history = container.state.value!!.menuPrices.getValue(id)
            instrumentation.runOnMainSync {
                container.quickDraft.value = checkNotNull(QuickCalculator.open(container.state.value!!, id, today, MoneyFormat(container.currency.value)))
            }
            rule.waitForIdle()
            edit("menu-price", "")
            scroll("menu-price-kept")
            rule.onNodeWithTag("menu-price-kept").assertTextContains("۲٬۵۰۰٬۰۰۰ تومان", substring = true)
            shot("05-empty-price-explicit-keep")
            click("calculator-save")
            rule.waitUntil(15_000) { container.quickDraft.value.menuPrice == "2500000" }
            check(container.state.value!!.menuPrices.getValue(id) == history)
            scenario.recreate()
            rule.waitForIdle()
            rule.onNodeWithTag("menu-price-kept").assertDoesNotExist()
            figure("cost", "۱٬۲۵۰٬۰۰۰")
            scroll("menu-price")
            val displayedPrice = rule.onNodeWithTag("menu-price").fetchSemanticsNode().config[SemanticsProperties.EditableText].text
            check(MoneyFormat(container.currency.value).parse(displayedPrice) == 25_000_000L)
            shot("06-reopen-retains-price-history")
        }
    }

    private fun scroll(tag: String) {
        rule.waitUntil(15_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        try { rule.onNodeWithTag(tag).performScrollTo() } catch (_: AssertionError) { /* Fixed actions sit outside the scroll. */ }
        rule.waitForIdle()
    }

    private fun click(tag: String) { scroll(tag); rule.onNodeWithTag(tag).performClick(); rule.waitForIdle() }

    private fun edit(tag: String, value: String) {
        scroll(tag)
        rule.onNodeWithTag(tag).performClick().performTextReplacement(value)
        instrumentation.uiAutomation.executeShellCommand("input keyevent 111").close()
        rule.waitForIdle()
    }

    private fun figure(tag: String, amount: String) {
        rule.waitUntil(15_000) { rule.onAllNodes(hasTestTag(tag) and hasText(amount, substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        dismissEmulatorLauncherAnr(instrumentation)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(shots, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        File(shots, "device.txt").writeText("widthDp=${context.resources.configuration.screenWidthDp}, density=${context.resources.displayMetrics.density}, fontScale=${context.resources.configuration.fontScale}")
    }
}
