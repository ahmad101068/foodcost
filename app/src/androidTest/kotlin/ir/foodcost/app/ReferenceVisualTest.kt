package ir.foodcost.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.foodcost.app.data.SqliteOperationStore
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.core.pricing.PricingScope
import ir.foodcost.presentation.Checked
import ir.foodcost.presentation.MoneyFormat
import ir.foodcost.presentation.QuickCalculator
import ir.foodcost.presentation.QuickInput
import ir.foodcost.presentation.QuickRow
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real native screens with the reference's sample; no mock rendering or app-only test controls. */
@RunWith(AndroidJUnit4::class)
class ReferenceVisualTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val container get() = (context.applicationContext as FoodCostApplication).container
    private val shots get() = File(context.filesDir, "reference-screenshots").apply { mkdirs() }

    @Test fun referenceScreensRemainReadableWithACompleteFourIngredientDish() {
        context.deleteDatabase(SqliteOperationStore.DATABASE)
        context.getSharedPreferences("foodcost", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "calculator-draft.json").delete()
        shots.deleteRecursively()
        val today = LocalDate.now()
        val plate = QuickInput(dishName = "چلوکباب کوبیده", category = "غذای اصلی", categoryChosen = true,
            menuPrice = "630000", rows = listOf(
                QuickRow("گوشت", "1450000", "170"), QuickRow("دمبه", "1100000", "30"),
                QuickRow("پیاز", "80000", "50"), QuickRow("برنج", "350000", "200"),
            ))
        runBlocking {
            container.change { ledger ->
                ledger.saveSettings(pricing = ledger.state.pricing.copy(scope = PricingScope.GENERAL,
                    methodByCategory = linkedMapOf("غذای اصلی" to PricingMethod.TargetFoodCost(BigDecimal(30)),
                        "نوشیدنی" to PricingMethod.TargetFoodCost(BigDecimal(20)))), effectiveFrom = today)
                val money = MoneyFormat(container.currency.value)
                fun save(input: QuickInput) {
                    val plan = QuickCalculator.check(input, ledger.state, money, today)
                    check(plan is Checked.Valid) { "Reference fixture rejected: $plan" }
                    QuickCalculator.save(ledger, plan.value, today)
                }
                save(plate)
                save(QuickInput(dishName = "زرشک‌پلو با مرغ", category = "غذای اصلی", categoryChosen = true,
                    menuPrice = "670000", rows = listOf(QuickRow("مرغ", "1060680", "200"))))
                save(QuickInput(dishName = "چای", category = "نوشیدنی", categoryChosen = true,
                    menuPrice = "80000", rows = listOf(QuickRow("چای خشک", "2000000", "10"))))
            }
        }
        val statuses = ir.foodcost.presentation.DishesScreen.build(container.state.value!!, today, MoneyFormat(container.currency.value)).plates.map { it.status }.toSet()
        check(statuses.containsAll(listOf(ir.foodcost.core.pricing.PriceStatus.OK, ir.foodcost.core.pricing.PriceStatus.NEAR, ir.foodcost.core.pricing.PriceStatus.UNDER))) { "The native preview must exercise green, cream and red prices: $statuses" }
        container.snoozeBackupReminder()
        container.quickDraft.value = plate
        container.saveDraft()
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.waitUntil(15_000) { rule.onNodeWithTag("calculator-dashboard").runCatching { fetchSemanticsNode() }.isSuccess }
            shot("01-calculator")
            val fourthIngredientVisible = runCatching { rule.onNodeWithTag("row-3-qty").assertIsDisplayed() }.isSuccess
            tab("مواد"); rule.onNodeWithTag("ingredients-list").assertIsDisplayed(); shot("02-ingredients")
            tab("غذاها"); rule.onNodeWithTag("food-list").assertIsDisplayed(); shot("03-dishes")
            tab("تنظیمات"); rule.onNodeWithTag("settings-overview").assertIsDisplayed(); shot("04-settings")
            rule.onNodeWithTag("settings-section-pricing").performClick()
            rule.onNodeWithTag("settings-save-pricing").performScrollTo().assertIsDisplayed()
            rule.onNodeWithTag("pricing-scope-general").performScrollTo()
            shot("05-pricing")
            rule.onNodeWithTag("settings-back").performClick()
            tab("ماشین‌حساب")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK")).use { stream -> stream.readBytes() }
            rule.waitForIdle()
            rule.onNodeWithTag("exit-confirmation").assertIsDisplayed(); shot("06-exit")
            rule.onNodeWithTag("exit-stay").performClick()
            check(fourthIngredientVisible) { "Fourth ingredient must fit on the reference screen without scrolling" }
        }
    }
    private fun tab(label: String) { rule.onAllNodesWithText(label).onFirst().performClick(); rule.waitForIdle() }
    private fun shot(name: String) {
        rule.waitForIdle()
        dismissEmulatorLauncherAnr(instrumentation)
        Thread.sleep(500)
        File(shots, "device.txt").writeText("widthDp=${context.resources.configuration.screenWidthDp}, density=${context.resources.displayMetrics.density}, fontScale=${context.resources.configuration.fontScale}")
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(shots, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
