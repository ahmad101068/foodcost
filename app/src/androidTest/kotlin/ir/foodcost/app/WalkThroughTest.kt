package ir.foodcost.app

import android.graphics.Bitmap
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.presentation.Labels
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Walks through the app the way the hall owner uses it: the koobideh priced in the calculator with
 * his own numbers, saved, found on the menu, re-priced when meat gets dearer, an ingredient price
 * updated from the list, then the detailed screens. Screenshots of each step are left in the
 * app's files for the workflow to collect.
 */
@RunWith(AndroidJUnit4::class)
class WalkThroughTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val shots = File(instrumentation.targetContext.filesDir, "screenshots").apply {
        deleteRecursively()
        mkdirs()
    }

    @Test
    fun aPlateIsCostedInTheCalculatorAndSaved() {
        try {
            walk()
        } catch (e: Throwable) {
            shot("99-failure")
            throw e
        }
    }

    private fun walk() {
        // The empty calculator opens with the keyboard on the first field.
        waitForFocus("row-0-name")
        shot("01-calculator-empty")

        // Prices per kilo in toman, grams per portion: 364,105 toman a portion with 3% for spices.
        // The keyboard's Next key walks the row, name, price and quantity, then on to the next row.
        typeTag("row-0-name", "گوشت")
        next("row-0-name", "row-0-price")
        typeTag("row-0-price", "1450000")
        next("row-0-price", "row-0-qty")
        typeTag("row-0-qty", "170")
        next("row-0-qty", "row-1-name")
        typeRow(1, "دمبه", "1100000", "30")
        typeRow(2, "پیاز", "80000", "50")
        typeRow(3, "برنج", "350000", "200")
        waitFor("۳۶۴٬۱۰۵ تومان")
        waitFor("۱٬۱۵۰٬۰۰۰ تومان")
        shot("02-calculator")

        // Sold at 630,000 toman (VAT included) the food cost is 63.6%: under-priced.
        typeTag("menu-price", "630000")
        waitFor("زیر قیمت")
        waitForPart("فودکاست ۶۳٫۶٪")
        shot("03-under-priced")

        typeTag("dish-name", "چلوکباب کوبیده")
        click("ذخیره")
        waitForPart("در غذاها ذخیره شد")
        shot("04-saved")

        click("منو")
        waitFor("چلوکباب کوبیده")
        waitFor("زیر قیمت")
        shot("05-menu")

        // The saved dish opens in the calculator; dearer meat shows at once.
        click("چلوکباب کوبیده")
        waitForTag("row-0-price")
        replaceTag("row-0-price", "1600000")
        waitFor("۳۹۰٬۳۷۰ تومان")
        shot("06-dish")
        click("ذخیره")
        waitForPart("در غذاها ذخیره شد")
        back()

        // A new onion price from the list, in one field.
        click("مواد")
        waitFor("هر کیلو ۱٬۶۰۰٬۰۰۰ تومان")
        shot("07-ingredients")
        rule.onNodeWithContentDescription("قیمت تازهٔ پیاز").performClick()
        replaceTag("quick-price", "90000")
        click("ذخیره")
        waitFor("هر کیلو ۹۰٬۰۰۰ تومان")
        shot("08-quick-price")

        // The detailed screens are still there: a purchase by the bag, and plain rice as a preparation.
        click("برنج")
        click("قیمت تازه")
        replace("مقدار خرید", "10")
        type("مبلغ کل", "3600000")
        click("ثبت قیمت")
        waitFor("قیمت امروز")
        shot("09-ingredient")
        back()

        click("دستورها")
        click("دستور تازه")
        type("نام", "چلو")
        click(Labels.kind(RecipeKind.PREP))
        click("ذخیره")
        click("افزودن ماده")
        click("برنج")
        type("مقدار", "150")
        click("ذخیره")
        waitFor("۱۵۰ گرم")
        shot("10-recipe")
        back()

        click("تنظیمات")
        waitFor("ذخیرهٔ قیمت‌گذاری")
        shot("11-settings")
    }

    private fun typeRow(index: Int, name: String, price: String, qty: String) {
        typeTag("row-$index-name", name)
        typeTag("row-$index-price", price)
        typeTag("row-$index-qty", qty)
    }

    private fun waitFor(text: String) {
        rule.waitUntil(timeoutMillis = 15_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForPart(text: String) {
        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForFocus(tag: String) {
        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Presses the keyboard's action key in [from] and checks that [to] takes the typing. */
    private fun next(from: String, to: String) {
        rule.onNodeWithTag(from).performImeAction()
        waitForFocus(to)
    }

    private fun waitForTag(tag: String) {
        rule.waitUntil(timeoutMillis = 15_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun scrolled(node: SemanticsNodeInteraction): SemanticsNodeInteraction {
        try {
            node.performScrollTo()
        } catch (e: AssertionError) {
            // Not inside a scrolling column.
        }
        return node
    }

    private fun click(text: String) {
        waitFor(text)
        scrolled(rule.onAllNodesWithText(text).onFirst()).performClick()
        rule.waitForIdle()
    }

    private fun type(label: String, value: String) {
        waitFor(label)
        scrolled(rule.onNodeWithText(label)).performTextInput(value)
    }

    private fun replace(label: String, value: String) {
        waitFor(label)
        scrolled(rule.onNodeWithText(label)).performTextReplacement(value)
    }

    private fun typeTag(tag: String, value: String) {
        waitForTag(tag)
        scrolled(rule.onNodeWithTag(tag)).performTextInput(value)
    }

    private fun replaceTag(tag: String, value: String) {
        waitForTag(tag)
        scrolled(rule.onNodeWithTag(tag)).performTextReplacement(value)
    }

    private fun back() {
        rule.onNodeWithContentDescription("بازگشت").performClick()
        rule.waitForIdle()
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        // The emulator draws slowly; without the pause a picture can still show the screen before.
        Thread.sleep(1200)
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(shots, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
