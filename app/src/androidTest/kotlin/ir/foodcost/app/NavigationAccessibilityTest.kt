package ir.foodcost.app

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
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
        val oldFont = shell("settings get system font_scale")
        val oldSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
        val density = inst.targetContext.resources.displayMetrics.density
        try {
            shell("wm size ${(320 * density).roundToInt()}x${(740 * density).roundToInt()}")
            shell("settings put system font_scale 2.0")
            ActivityScenario.launch(MainActivity::class.java).use {
                rule.waitUntil(15000) { rule.onAllNodesWithTag("foodcost-nav-settings").fetchSemanticsNodes().isNotEmpty() }
                for (tab in listOf("calc", "ingredients", "recipes", "settings")) {
                    val tag = "foodcost-nav-$tab"
                    val node = rule.onNodeWithTag(tag)
                    node.assertIsDisplayed().assertHasClickAction()
                    val bounds = node.fetchSemanticsNode().boundsInRoot
                    assertTrue(bounds.width >= 48 * density && bounds.height >= 48 * density)
                    val label = rule.onNode(hasAnyAncestor(hasTestTag(tag)) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
                    val layouts = mutableListOf<TextLayoutResult>()
                    label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    val layout = layouts.single()
                    assertFalse("Text overflow in $tab", layout.hasVisualOverflow)
                    val text = layout.layoutInput.text.text
                    for (line in 0 until layout.lineCount - 1) {
                        val end = layout.getLineEnd(line)
                        assertTrue("A Persian word split inside $tab: $text", text[end - 1].isWhitespace() || text.getOrNull(end)?.isWhitespace() == true)
                    }
                    node.performClick()
                    rule.waitForIdle()
                }
                val dir = File(inst.targetContext.filesDir, "complete-audit-screens").apply { mkdirs() }
                val bitmap = checkNotNull(inst.uiAutomation.takeScreenshot())
                File(dir, "04-navigation-320dp-font200.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } finally {
            shell("settings put system font_scale ${oldFont.takeUnless { it == "null" } ?: "1.0"}")
            shell("wm size ${oldSize ?: "reset"}")
        }
    }
}
