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
import ir.foodcost.core.money.PriceRounding
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.core.pricing.PricingScope
import ir.foodcost.presentation.*
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Disposable-emulator regression for the owner's six navigation and control reports. */
@RunWith(AndroidJUnit4::class)
class UxControlsTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val container get() = (context.applicationContext as FoodCostApplication).container
    private val shots get() = File(context.filesDir, "ux-control-screenshots").apply { mkdirs() }

    @Test
    fun settingsRoutesPricingFiltersAndNewCalculationWorkIndependently() {
        context.deleteDatabase(SqliteOperationStore.DATABASE)
        context.getSharedPreferences("foodcost", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "calculator-draft.json").delete()
        shots.deleteRecursively()
        val today = LocalDate.now()
        val categoryRules = linkedMapOf("غذای اصلی" to PricingMethod.TargetFoodCost(BigDecimal("30")))
        runBlocking {
            container.change { ledger ->
                ledger.saveSettings(
                    costing = CostingRules(BigDecimal.ZERO),
                    pricing = ledger.state.pricing.copy(
                        scope = PricingScope.MENU_SECTIONS,
                        defaultMethod = PricingMethod.TargetFoodCost(BigDecimal("35")),
                        methodByCategory = categoryRules,
                        vatPercent = BigDecimal.ZERO,
                        rounding = PriceRounding(1),
                    ),
                    effectiveFrom = today,
                )
                listOf(
                    QuickInput(dishName = "غذای آزمایش", category = "غذای اصلی", categoryChosen = true,
                        menuPrice = "500000", rows = listOf(QuickRow("برنج آزمایش", "250000", "200"))),
                    QuickInput(dishName = "چای آزمایش", category = "نوشیدنی", categoryChosen = true,
                        menuPrice = "50000", rows = listOf(QuickRow("چای خشک آزمایش", "1000000", "10"))),
                ).forEach { input ->
                    val checked = QuickCalculator.check(input, ledger.state, MoneyFormat(container.currency.value), today)
                    check(checked is Checked.Valid)
                    QuickCalculator.save(ledger, checked.value, today)
                }
            }
        }
        container.snoozeBackupReminder()
        container.quickDraft.value = QuickInput()
        container.saveDraft()
        val foodId = container.state.value!!.recipes.values.single { it.name == "غذای آزمایش" }.id
        val teaId = container.state.value!!.recipes.values.single { it.name == "چای آزمایش" }.id
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            tab("تنظیمات")
            click("settings-section-pricing")
            edit("general-pricing-percent", "32")
            for (destination in listOf("ماشین‌حساب", "غذاها", "مواد اولیه")) {
                tab(destination)
                tab("تنظیمات")
                rule.onNodeWithTag("settings-overview").assertExists()
                rule.onNodeWithTag("settings-page-pricing").assertDoesNotExist()
                click("settings-section-pricing")
                rule.onNodeWithTag("general-pricing-percent").assertTextContains("32")
                assertEquals(PricingMethod.TargetFoodCost(BigDecimal("35")), container.state.value!!.pricing.defaultMethod)
            }
            for (method in MethodChoice.entries) {
                click("general-method-$method")
                rule.onNodeWithTag("general-method-$method").assertIsEnabled().assertIsSelected()
            }
            edit("general-pricing-percent", "150")
            click("settings-save-pricing")
            rule.waitUntil(15_000) { container.state.value!!.pricing.defaultMethod == PricingMethod.Markup(BigDecimal("150")) }
            assertEquals(PricingScope.MENU_SECTIONS, container.state.value!!.pricing.scope)
            assertEquals(categoryRules, container.state.value!!.pricing.methodByCategory)
            shot("01-section-mode-active-methods")
            click("settings-section-sections")
            for (method in MethodChoice.entries) {
                click("category-0-method-$method")
                rule.onNodeWithTag("category-0-method-$method").assertIsEnabled()
                rule.onNodeWithTag("category-0-percent").assertIsEnabled()
            }
            shot("02-section-specific-active-methods")
            click("settings-back")
            click("pricing-scope-general")
            click("settings-save-pricing")
            rule.waitUntil(15_000) { container.state.value!!.pricing.scope == PricingScope.GENERAL }
            click("settings-section-sections")
            rule.onNodeWithTag("category-0-percent").assertIsNotEnabled()
            click("activate-section-pricing")
            rule.waitUntil(15_000) { container.state.value!!.pricing.scope == PricingScope.MENU_SECTIONS }
            rule.onNodeWithTag("category-0-percent").assertIsEnabled()
            assertEquals(categoryRules, container.state.value!!.pricing.methodByCategory)
            assertEquals(PricingMethod.Markup(BigDecimal("150")), container.state.value!!.pricing.defaultMethod)
            click("settings-back")
            click("settings-back")

            click("settings-section-about")
            rule.onNodeWithTag("settings-page-about").assertExists()
            rule.onNodeWithText("کار با برنامه").assertDoesNotExist()
            shot("03-about")
            click("settings-back")
            click("settings-section-help")
            rule.onNodeWithTag("settings-page-help").assertExists()
            rule.onNodeWithTag("settings-page-about").assertDoesNotExist()
            rule.onNodeWithText("کار با برنامه").assertExists()
            rule.onNodeWithText("واژه‌ها").assertExists()
            shot("04-help")
            click("settings-back")
            click("settings-section-health")
            scroll("health-applied")
            rule.onNodeWithTag("health-applied").assertTextContains(Persian.digits(container.state.value!!.appliedIds.size), substring = true)
            rule.onNodeWithText("ساخت پشتیبان").assertDoesNotExist()
            shot("05-data-health")
            click("health-open-backup")
            rule.onNodeWithTag("settings-page-backup").assertExists()
            rule.onNodeWithTag("health-applied").assertDoesNotExist()
            shot("06-backup")

            tab("غذاها")
            rule.onNodeWithTag("food-search").assertDoesNotExist()
            click("food-filter-menu")
            rule.onNodeWithTag("food-filter-all-sections").assertIsDisplayed()
            rule.onNodeWithTag("food-search").assertDoesNotExist()
            shot("07-toolbar-filter")
            click("food-filter-category-غذای اصلی")
            scroll("food-${foodId.value}")
            rule.onNodeWithTag("food-${teaId.value}").assertDoesNotExist()
            click("food-filter-menu")
            click("food-filter-all-sections")
            click("food-search-toggle")
            edit("food-search", "چای آزمایش")
            scroll("food-${teaId.value}")
            rule.onNodeWithTag("food-${foodId.value}").assertDoesNotExist()
            shot("08-independent-search")
            click("food-search-toggle")
            rule.onNodeWithTag("food-search").assertDoesNotExist()

            val saved = container.state.value!!.recipes.getValue(foodId)
            val savedPrices = container.state.value!!.menuPrices.getValue(foodId)
            val lastAt = container.state.value!!.lastAt
            rule.onNodeWithTag("food-list").performScrollToNode(hasTestTag("food-${foodId.value}"))
            // The reference layout initially expands the second dish; search preserves that state.
            if (rule.onAllNodesWithTag("food-open-${foodId.value}").fetchSemanticsNodes().isEmpty()) {
                click("food-${foodId.value}")
            }
            click("food-open-${foodId.value}")
            edit("dish-name", "تغییر ذخیره‌نشده")
            shot("09-saved-food-calculator-reset")
            click("calculator-reset")
            assertEquals(QuickInput(), container.quickDraft.value)
            assertEquals(saved, container.state.value!!.recipes.getValue(foodId))
            assertEquals(savedPrices, container.state.value!!.menuPrices.getValue(foodId))
            assertEquals(lastAt, container.state.value!!.lastAt)
            rule.onNodeWithTag("calculator-reset").assertIsNotEnabled()
            scroll("dish-name")
            assertEquals("", rule.onNodeWithTag("dish-name").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
            shot("10-blank-new-calculation")
            scenario.recreate()
            rule.waitForIdle()
            assertEquals(QuickInput(), container.quickDraft.value)
            rule.onNodeWithTag("calculator-reset").assertIsNotEnabled()
        }
    }

    private fun scroll(tag: String) {
        rule.waitUntil(15_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        try { rule.onNodeWithTag(tag).performScrollTo() } catch (_: AssertionError) { /* Fixed toolbar. */ }
        rule.waitForIdle()
    }
    private fun click(tag: String) { scroll(tag); rule.onNodeWithTag(tag).performClick(); rule.waitForIdle() }
    private fun edit(tag: String, value: String) {
        scroll(tag)
        rule.onNodeWithTag(tag).performClick().performTextReplacement(value)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("input keyevent 111")
        ).use { it.readBytes() }
        rule.waitForIdle()
    }
    private fun tab(label: String) {
        val tab = when (label) {
            "ماشین‌حساب" -> "calc"
            "مواد اولیه" -> "ingredients"
            "غذاها" -> "recipes"
            "تنظیمات" -> "settings"
            else -> error("Unknown tab: $label")
        }
        click("foodcost-nav-$tab")
    }
    private fun shot(name: String) {
        rule.waitForIdle()
        dismissEmulatorLauncherAnr(instrumentation)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(shots, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        File(shots, "device.txt").writeText("widthDp=${context.resources.configuration.screenWidthDp}, density=${context.resources.displayMetrics.density}, fontScale=${context.resources.configuration.fontScale}")
    }
}
