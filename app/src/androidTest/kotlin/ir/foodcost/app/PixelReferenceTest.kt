package ir.foodcost.app

import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.foodcost.app.data.SqliteOperationStore
import ir.foodcost.core.model.PurchasePrice
import ir.foodcost.core.money.PriceRounding
import ir.foodcost.core.pricing.*
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

/** Actual native evidence with synthetic ledger data, never bundled as production sample data. */
@RunWith(AndroidJUnit4::class)
class PixelReferenceTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context
        get() = instrumentation.targetContext

    private val container
        get() = (context.applicationContext as FoodCostApplication).container

    private val shots
        get() = File(context.filesDir, "pixel083-screenshots").apply { mkdirs() }

    @Test
    fun captureFiveNativeReferenceScreensAndVerifyLiveFinancialValues() {
        context.deleteDatabase(SqliteOperationStore.DATABASE)
        context.getSharedPreferences("foodcost", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "calculator-draft.json").delete()
        shots.deleteRecursively()
        val today = LocalDate.now()
        val calc =
            QuickInput(
                dishName = "چلوکباب کوبیده",
                menuPrice = "630000",
                rows =
                    listOf(
                        QuickRow("گوشت گوساله", "1450000", "170"),
                        QuickRow("برنج ایرانی", "350000", "200"),
                        QuickRow("روغن", "80000", "50"),
                    ),
                extras = listOf(QuickExtra("هزینه جانبی", "30000")),
            )
        runBlocking {
            container.change { ledger ->
                ledger.saveSettings(
                    pricing =
                        ledger.state.pricing.copy(
                            scope = PricingScope.GENERAL,
                            defaultMethod = PricingMethod.TargetFoodCost(BigDecimal(30)),
                            vatPercent = BigDecimal(10),
                            menuPriceIncludesVat = true,
                            rounding = PriceRounding(10_000, PriceRounding.Direction.NEAREST),
                        ),
                    effectiveFrom = today,
                )
                val money = MoneyFormat(container.currency.value)
                fun save(input: QuickInput) {
                    val checked = QuickCalculator.check(input, ledger.state, money, today)
                    check(checked is Checked.Valid)
                    QuickCalculator.save(ledger, checked.value, today)
                }
                save(
                    calc.copy(menuPrice = "1650000", rows = calc.rows.take(2), extras = emptyList())
                )
                save(
                    QuickInput(
                        dishName = "جوجه کباب",
                        menuPrice = "1600000",
                        rows = listOf(QuickRow("مرغ", "1060000", "400")),
                    )
                )
                save(
                    QuickInput(
                        dishName = "زرشک‌پلو با مرغ",
                        menuPrice = "1620000",
                        rows = listOf(QuickRow("مرغ", "1060000", "700")),
                    )
                )
                ledger.createIngredient("روغن", Dimension.VOLUME)
                val onion = ledger.createIngredient("پیاز", Dimension.MASS)
                ledger.recordPrice(
                    PurchasePrice(onion, today, BigDecimal.ONE, Uom.KILOGRAM, 800_000)
                )
                val paste = ledger.createIngredient("رب گوجه", Dimension.MASS)
                ledger.recordPrice(
                    PurchasePrice(paste, today, BigDecimal.ONE, Uom.KILOGRAM, 2_800_000)
                )
            }
        }
        container.snoozeBackupReminder()
        container.quickDraft.value = calc
        container.saveDraft()
        val money = MoneyFormat(container.currency.value)
        val computed = QuickCalculator.compute(calc, container.state.value!!, money, today)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitTag("calculator-dashboard")
            scenario.onActivity { activity ->
                (activity.getSystemService(Context.INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager)
                    .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
                activity.window.decorView.clearFocus()
            }
            instrumentation.waitForIdleSync()
            Thread.sleep(800)
            val displayHeight =
                androidx.test.uiautomator.UiDevice.getInstance(instrumentation).displayHeight
            repeat(3) {
                var keyboardVisible = false
                scenario.onActivity { activity ->
                    val frame = android.graphics.Rect()
                    activity.window.decorView.getWindowVisibleDisplayFrame(frame)
                    keyboardVisible = frame.bottom < displayHeight * 0.75f
                }
                if (keyboardVisible) shell("input keyevent KEYCODE_BACK")
                instrumentation.waitForIdleSync()
                Thread.sleep(600)
            }
            // Match the reference's idle input state through a real focus action; no pixels or
            // values are replaced. Startup autofocus and the separate IME-state test stay intact.
            shell("input keyevent KEYCODE_TAB")
            rule.waitForIdle()
            rule.onNodeWithTag("foodcost-nav-calc").performSemanticsAction(
                SemanticsActions.RequestFocus
            ) {
                check(it())
            }
            rule.waitForIdle()
            rule.onAllNodesWithTag("exit-confirmation").assertCountEquals(0)
            rule.onNodeWithTag("cost").assertTextContains(computed.costPerPortion, substring = true)
            shot("01-calculator")
            tab("مواد اولیه", "مواد")
            waitTag("ingredients-list")
            shot("02-ingredients")
            tab("غذاها")
            waitTag("food-list")
            shot("03-recipes")
            tab("تنظیمات")
            waitTag("settings-section-pricing")
            shot("04-settings")
            click("settings-section-pricing")
            waitTag("general-pricing-percent")
            shot("05-general-pricing")
            // A reversible edit proves results are backed by the live draft, not screenshot text.
            rule
                .onNodeWithTag("general-pricing-percent")
                .performScrollTo()
                .performTextReplacement("35")
            rule.onNodeWithTag("general-pricing-percent").assertTextContains("35")
            rule.onNodeWithTag("general-pricing-percent").performTextReplacement("30")
            File(shots, "financial-values.txt")
                .writeText(
                    "Food=${computed.costPerPortion}\nSuggested=${computed.suggested}\nGross=${computed.grossProfit}\nFoodCost=${computed.foodCostPercent}\nScope=${container.state.value!!.pricing.scope}\n"
                )
        }
    }

    @Test
    fun captureEmptyKeyboardAndValidationStates() {
        context.deleteDatabase(SqliteOperationStore.DATABASE)
        context.getSharedPreferences("foodcost", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "calculator-draft.json").delete()
        container.snoozeBackupReminder()
        container.quickDraft.value = QuickInput()
        ActivityScenario.launch(MainActivity::class.java).use {
            waitTag("row-0-name")
            shot("10-keyboard")
            shell("input keyevent KEYCODE_BACK")
            rule.waitForIdle()
            shot("11-empty-calculator")
            rule.onNodeWithTag("menu-price").performScrollTo().performTextReplacement("invalid")
            shell("input keyevent KEYCODE_BACK")
            rule.waitForIdle()
            shot("12-invalid-number")
            tab("مواد اولیه", "مواد")
            shot("13-empty-ingredients")
            tab("غذاها")
            shot("14-empty-recipes")
        }
    }

    private fun waitTag(tag: String) {
        rule.waitUntil(30_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun click(tag: String) {
        waitTag(tag)
        val n = rule.onNodeWithTag(tag)
        runCatching { n.performScrollTo() }
        n.performClick()
        rule.waitForIdle()
    }

    private fun tab(label: String, legacy: String? = null) {
        val current = rule.onAllNodesWithText(label)
        if (current.fetchSemanticsNodes().isNotEmpty()) current.onLast().performClick()
        else rule.onAllNodesWithText(legacy!!).onLast().performClick()
        rule.waitForIdle()
    }

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(command)
            )
            .use { it.readBytes() }
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        dismissEmulatorLauncherAnr(instrumentation)
        Thread.sleep(500)
        val c = context.resources.configuration
        val dm = context.resources.displayMetrics
        File(shots, "device.txt")
            .writeText(
                "widthDp=${c.screenWidthDp}\nheightDp=${c.screenHeightDp}\ndensity=${dm.density}\nfontScale=${c.fontScale}\napi=${android.os.Build.VERSION.SDK_INT}\n"
            )
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(shots, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        File(shots, "$name-semantics.txt")
            .writeText(rule.onAllNodes(isRoot(), useUnmergedTree = true).onFirst().printToString())
        val collection =
            rule.onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
                useUnmergedTree = true,
            )
        val count = collection.fetchSemanticsNodes().size
        val output = org.json.JSONArray()
        for (i in 0 until count) {
            val layouts = mutableListOf<TextLayoutResult>()
            collection[i].performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                it(layouts)
            }
            layouts.forEach { layout ->
                output.put(
                    org.json.JSONObject().apply {
                        put("text", layout.layoutInput.text.text)
                        put("fontSizeSp", layout.layoutInput.style.fontSize.value)
                        put("lineHeightSp", layout.layoutInput.style.lineHeight.value)
                        put("lines", layout.lineCount)
                        put("overflow", layout.hasVisualOverflow)
                        put("overflowWidth", layout.didOverflowWidth)
                        put("overflowHeight", layout.didOverflowHeight)
                        put("widthPx", layout.size.width)
                        put("heightPx", layout.size.height)
                        put("lastLineBottomPx", layout.getLineBottom(layout.lineCount - 1))
                        put("lastLineRightPx", layout.getLineRight(layout.lineCount - 1))
                        put("lastLineLeftPx", layout.getLineLeft(layout.lineCount - 1))
                        val maxLineWidth =
                            (0 until layout.lineCount).maxOf {
                                layout.getLineRight(it) - layout.getLineLeft(it)
                            }
                        put("maxGlyphLineWidthPx", maxLineWidth)
                        put("glyphWidthFits", maxLineWidth <= layout.size.width + 1)
                        put(
                            "glyphHeightFits",
                            layout.getLineBottom(layout.lineCount - 1) <= layout.size.height + 1,
                        )
                    }
                )
            }
        }
        File(shots, "$name-text-layouts.json").writeText(output.toString(2))
        val touch = org.json.JSONArray()
        rule.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes().forEach {
            node ->
            val bounds = node.touchBoundsInRoot
            val visible = node.boundsInRoot
            touch.put(
                org.json.JSONObject().apply {
                    put("tag", node.config.getOrNull(SemanticsProperties.TestTag))
                    put("text", node.config.getOrNull(SemanticsProperties.Text)?.joinToString())
                    put(
                        "description",
                        node.config
                            .getOrNull(SemanticsProperties.ContentDescription)
                            ?.joinToString(),
                    )
                    put(
                        "visibleBoundsPx",
                        org.json.JSONArray(
                            listOf(visible.left, visible.top, visible.right, visible.bottom)
                        ),
                    )
                    put(
                        "touchBoundsPx",
                        org.json.JSONArray(
                            listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)
                        ),
                    )
                    put("touchWidthDp", bounds.width / dm.density)
                    put("touchHeightDp", bounds.height / dm.density)
                }
            )
        }
        File(shots, "$name-touch-bounds.json").writeText(touch.toString(2))
    }
}
