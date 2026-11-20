package ir.foodcost.app

import android.graphics.Bitmap
import android.content.Context
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
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import ir.foodcost.presentation.*
import ir.foodcost.app.data.SqliteOperationStore
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Check actual Android font scaling and window size, including Persian word boundaries. */
@RunWith(AndroidJUnit4::class)
class NavigationAccessibilityTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(command))
        .bufferedReader().use { it.readText().trim() }

    @Test fun largestFontOnNarrowScreenKeepsWholeWordsAndClickableTabs() {
        check(shell("getprop ro.kernel.qemu") == "1") { "Disposable emulator only" }
        inst.targetContext.deleteDatabase(SqliteOperationStore.DATABASE)
        inst.targetContext.getSharedPreferences("foodcost", Context.MODE_PRIVATE).edit().clear().commit()
        File(inst.targetContext.filesDir, "calculator-draft.json").delete()
        val container = (inst.targetContext.applicationContext as FoodCostApplication).container
        val sample = QuickInput(dishName = "چلوکباب کوبیده", menuPrice = "500000",
            rows = listOf(QuickRow("برنج آزمایش", "100000", "1000")))
        runBlocking { container.change { ledger ->
            if (ledger.state.recipes.values.none { it.name == sample.dishName }) {
                val checked = QuickCalculator.check(sample, ledger.state, MoneyFormat(container.currency.value), LocalDate.now())
                check(checked is Checked.Valid)
                QuickCalculator.save(ledger, checked.value, LocalDate.now())
            }
        } }
        container.quickDraft.value = sample
        container.saveDraft()
        container.snoozeBackupReminder()
        val oldFont = shell("settings get system font_scale")
        val oldSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
        val density = inst.targetContext.resources.displayMetrics.density
        try {
            shell("wm size ${(320 * density).roundToInt()}x${(740 * density).roundToInt()}")
            shell("settings put system font_scale 2.0")
            ActivityScenario.launch(MainActivity::class.java).use {
                rule.waitUntil(15000) { rule.onAllNodesWithTag("foodcost-nav-settings").fetchSemanticsNodes().isNotEmpty() }
                val dir = File(inst.targetContext.filesDir, "complete-audit-screens").apply { mkdirs() }
                val records = org.json.JSONArray()
                val config = inst.targetContext.resources.configuration
                File(dir, "navigation-device.txt").writeText("widthDp=${config.screenWidthDp}\nfontScale=${config.fontScale}\ndensity=$density\n")
                assertTrue(config.fontScale >= 1.99f)
                for (tab in listOf("calc", "ingredients", "recipes", "settings")) {
                    val tag = "foodcost-nav-$tab"
                    val node = rule.onNodeWithTag(tag)
                    node.assertIsDisplayed().assertHasClickAction()
                    // The overridden virtual display can offset injected pointer coordinates.
                    // Exercise the accessible tab action, and require its actual selected state.
                    node.performSemanticsAction(SemanticsActions.OnClick) { check(it()) }
                    rule.waitUntil(15000) { rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true }
                    node.assertIsSelected()
                    val bounds = node.fetchSemanticsNode().boundsInRoot
                    assertTrue(bounds.width >= 48 * density && bounds.height >= 48 * density)
                    val label = rule.onNode(hasAnyAncestor(hasTestTag(tag)) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
                    val layouts = mutableListOf<TextLayoutResult>()
                    label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    val layout = layouts.single()
                    val text = layout.layoutInput.text.text
                    val lines = (0 until layout.lineCount).map { line ->
                        listOf(layout.getLineLeft(line), layout.getLineRight(line), layout.getLineBottom(line))
                    }
                    records.put(org.json.JSONObject().apply {
                        put("tab", tab); put("text", text); put("widthPx", layout.size.width); put("heightPx", layout.size.height)
                        put("rawParagraphOverflow", layout.hasVisualOverflow)
                        put("linesLeftRightBottomPx", org.json.JSONArray(lines.map { org.json.JSONArray(it) }))
                    })
                    File(dir, "navigation-text-layouts.json").writeText(records.toString(2))
                    val bitmap = checkNotNull(inst.uiAutomation.takeScreenshot())
                    File(dir, "navigation-320-200-$tab.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    // RTL paragraph flags can report unused intrinsic width as overflow. Check
                    // the actual laid-out line bounds, retaining the raw flag for inspection.
                    assertTrue("Clipped navigation text in $tab: $lines, size=${layout.size}",
                        lines.all { (left, right, bottom) -> left >= -1 && right <= layout.size.width + 1 && bottom <= layout.size.height + 1 })
                    for (line in 0 until layout.lineCount - 1) {
                        val end = layout.getLineEnd(line)
                        assertTrue("A Persian word split inside $tab: $text", text[end - 1].isWhitespace() || text.getOrNull(end)?.isWhitespace() == true)
                    }
                    if (tab == "recipes") {
                        for (textToCheck in listOf("مناسب", "نیازمند بررسی", sample.dishName)) {
                            if (textToCheck == sample.dishName)
                                rule.onNodeWithTag("food-list").performScrollToNode(hasText(textToCheck))
                            val item = rule.onAllNodesWithText(textToCheck, useUnmergedTree = true).onFirst()
                            item.performScrollTo()
                            val textLayouts = mutableListOf<TextLayoutResult>()
                            item.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(textLayouts) }
                            for (textLayout in textLayouts) {
                                for (line in 0 until textLayout.lineCount - 1) {
                                    val end = textLayout.getLineEnd(line)
                                    val value = textLayout.layoutInput.text.text
                                    assertTrue("A word split inside $value", value[end - 1].isWhitespace() || value.getOrNull(end)?.isWhitespace() == true)
                                }
                            }
                        }
                        val dishImage = checkNotNull(inst.uiAutomation.takeScreenshot())
                        File(dir, "05-recipe-320dp-font200.png").outputStream().use { dishImage.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }
                }
                val bitmap = checkNotNull(inst.uiAutomation.takeScreenshot())
                File(dir, "04-navigation-320dp-font200.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } finally {
            shell("settings put system font_scale ${oldFont.takeUnless { it == "null" } ?: "1.0"}")
            shell("wm size ${oldSize ?: "reset"}")
        }
    }
}
