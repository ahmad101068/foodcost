package ir.foodcost.app

import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.foodcost.app.data.SqliteOperationStore
import ir.foodcost.core.model.PurchasePrice
import ir.foodcost.core.model.YieldStage
import ir.foodcost.core.pricing.OnlineTerms
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.core.pricing.PricingScope
import ir.foodcost.core.units.Dimension
import ir.foodcost.core.units.Uom
import ir.foodcost.data.ops.Opt
import ir.foodcost.presentation.*
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Native screens and real UI actions for the approved grouped lists and calculator continuation. */
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
            menuPrice = "630000", rows = listOf(QuickRow("گوشت", "1450000", "170"), QuickRow("دمبه", "1100000", "30"),
                QuickRow("پیاز", "80000", "50"), QuickRow("برنج", "350000", "200")))
        var sampleMeat = ""
        var missingSpice = ""
        runBlocking {
            container.change { ledger ->
                ledger.saveSettings(pricing = ledger.state.pricing.copy(scope = PricingScope.GENERAL,
                    online = OnlineTerms(commissionPercent = BigDecimal(20)),
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
                ledger.state.ingredients.values.toList().forEach { ingredient ->
                    val category = when (ingredient.name) { "برنج" -> "غلات و ادویه"; "پیاز" -> "سبزیجات"; "چای خشک" -> "نوشیدنی"; else -> "پروتئینی" }
                    ledger.updateIngredient(ingredient.id, category = Opt.Value(category))
                }
                val meat = ledger.createIngredient("گوشت گوساله", Dimension.MASS, category = "پروتئینی", yields = listOf(YieldStage("پاک‌کردن", BigDecimal("0.95"))))
                sampleMeat = meat.value
                ledger.recordPrice(PurchasePrice(meat, today.minusDays(1), BigDecimal.ONE, Uom.KILOGRAM, 14_000_000))
                ledger.recordPrice(PurchasePrice(meat, today, BigDecimal.ONE, Uom.KILOGRAM, 14_500_000))
                missingSpice = ledger.createIngredient("زعفران", Dimension.MASS, category = "غلات و ادویه").value
            }
        }
        val statuses = DishesScreen.build(container.state.value!!, today, MoneyFormat(container.currency.value)).plates.map { it.status }.toSet()
        check(statuses.containsAll(listOf(ir.foodcost.core.pricing.PriceStatus.OK, ir.foodcost.core.pricing.PriceStatus.NEAR, ir.foodcost.core.pricing.PriceStatus.UNDER)))
        container.snoozeBackupReminder()
        container.quickDraft.value = plate.copy(extras = listOf(QuickExtra("ظرف و بسته‌بندی", "15000", true), QuickExtra("دستمزد", "10000")))
        container.saveDraft()
        ActivityScenario.launch(MainActivity::class.java).use {
            waitTag("calculator-dashboard")
            shot("01-calculator")
            val fourthIngredientVisible = runCatching { rule.onNodeWithTag("row-3-qty").assertIsDisplayed() }.isSuccess

            // The same package costs the takeaway channel, not in-person sales.
            clickTag("channel-tab-0"); figure("channel-remaining", "۱۹۸٬۶۲۲")
            figure("channel-0-net", "۵۷۲٬۷۲۷"); figure("channel-0-cost", "۳۷۴٬۱۰۵")
            clickTag("channel-tab-1"); figure("channel-1-remaining", "۱۸۳٬۶۲۲"); figure("channel-1-cost", "۳۸۹٬۱۰۵")
            clickTag("channel-tab-2"); figure("channel-2-remaining", "۶۹٬۰۷۷")
            clickTag("channel-tab-0")
            clickTag("extra-0-scope"); clickTag("extra-0-scope-all"); figure("channel-remaining", "۱۸۳٬۶۲۲")
            clickTag("extra-0-scope"); clickTag("extra-0-scope-takeaway"); figure("channel-remaining", "۱۹۸٬۶۲۲")
            alignCalculatorSection("calculator-ingredients")
            shot("07-calculator-lower")

            tab("مواد")
            rule.onNodeWithTag("ingredients-list").performScrollToNode(hasTestTag("ingredient-$sampleMeat"))
            if (!exists("ingredient-history-$sampleMeat")) clickTag("ingredient-$sampleMeat")
            rule.onNodeWithTag("ingredients-list").performScrollToIndex(0)
            figure("ingredient-price-$sampleMeat", "۱٬۴۵۰٬۰۰۰")
            shot("02-ingredients")
            clickTag("ingredients-filter-missing")
            rule.onNodeWithTag("ingredient-register-$missingSpice").assertIsDisplayed()
            rule.onAllNodesWithTag("ingredient-$sampleMeat").assertCountEquals(0)
            shot("09-missing-price")
            clickTag("ingredients-filter-all")

            tab("غذاها")
            val tea = container.state.value!!.recipes.values.single { it.name == "چای" }
            val plateId = container.state.value!!.recipes.values.single { it.name == plate.dishName }.id.value
            clickTag("food-filter-needs")
            rule.onAllNodesWithTag("food-${tea.id.value}").assertCountEquals(0)
            clickTag("food-filter-suitable")
            rule.onNodeWithTag("food-${tea.id.value}").assertIsDisplayed()
            figure("food-price-${tea.id.value}", "۸۰٬۰۰۰")
            rule.onAllNodesWithTag("food-$plateId").assertCountEquals(0)
            clickTag("food-filter-all")
            rule.onNodeWithTag("food-list").performScrollToIndex(0)
            shot("03-dishes")

            tab("تنظیمات")
            rule.onNodeWithTag("settings-section-sections").assertDoesNotExist()
            shot("04-settings")
            clickTag("settings-section-pricing")
            clickTag("settings-section-sections")
            waitTag("settings-page-sections")
            rule.onNodeWithTag("category-0-percent").assertIsNotEnabled()
            shot("08-menu-sections")
            clickTag("settings-back")
            waitTag("general-pricing-percent")
            rule.onNodeWithTag("settings-save-pricing").performScrollTo().assertIsDisplayed()
            rule.onNodeWithTag("pricing-scope-general").performScrollTo()
            shot("05-pricing")
            clickTag("settings-back")
            tab("ماشین‌حساب")
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK")).use { stream -> stream.readBytes() }
            rule.waitForIdle()
            rule.onNodeWithTag("exit-confirmation").assertIsDisplayed(); shot("06-exit")
            clickTag("exit-stay")
            check(fourthIngredientVisible) { "Fourth ingredient must fit without scrolling at normal font size" }
        }
    }
    private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun waitTag(tag: String) { rule.waitUntil(15_000) { exists(tag) } }
    private fun figure(tag: String, number: String) { rule.waitUntil(15_000) { rule.onAllNodes(hasTestTag(tag) and hasText(number, substring = true)).fetchSemanticsNodes().isNotEmpty() } }
    private fun clickTag(tag: String) {
        waitTag(tag)
        val node = rule.onNodeWithTag(tag)
        try { node.performScrollTo() } catch (_: AssertionError) { /* Fixed toolbar outside scroll. */ }
        node.performClick(); rule.waitForIdle()
    }
    private fun tab(label: String) { rule.onAllNodesWithText(label).onFirst().performClick(); rule.waitForIdle() }
    private fun alignCalculatorSection(tag: String) {
        rule.onNodeWithTag(tag).performScrollTo()
        val top = rule.onNodeWithTag("calculator-scroll").fetchSemanticsNode().boundsInRoot.top
        val delta = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top - top
        rule.onNodeWithTag("calculator-scroll").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, delta) }
        rule.waitForIdle()
    }
    private fun shot(name: String) {
        rule.waitForIdle(); dismissEmulatorLauncherAnr(instrumentation); Thread.sleep(500)
        File(shots, "device.txt").writeText("widthDp=${context.resources.configuration.screenWidthDp}, density=${context.resources.displayMetrics.density}, fontScale=${context.resources.configuration.fontScale}")
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(shots, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
