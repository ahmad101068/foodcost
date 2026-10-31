package ir.foodcost.app

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.presentation.Labels
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Walks through what a hall owner does first: two ingredients with their prices, plain rice as a
 * preparation, a koobideh plate with its menu price, then the menu status and the settings.
 * Screenshots of each step are left in the app's files for the workflow to collect.
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
    fun aPlateIsCostedAndPricedFromAnEmptyApp() {
        try {
            walk()
        } catch (e: Throwable) {
            shot("99-failure")
            throw e
        }
    }

    private fun walk() {
        waitFor("هنوز غذایی در منو نیست")
        shot("01-menu-empty")

        // Rice, with 2% lost in washing, bought in 10 kg bags.
        click("ثبت مواد اولیه")
        click("ماده تازه")
        type("نام ماده", "برنج طارم")
        replace("بازدهی", "98")
        shot("02-new-ingredient")
        click("ذخیره")
        waitFor("مقدار خرید")
        replace("مقدار خرید", "10")
        type("مبلغ کل", "4600000")
        waitFor("هر کیلو ۴۶۰٬۰۰۰ تومان")
        shot("03-purchase")
        click("ثبت قیمت")
        waitFor("قیمت امروز")
        shot("04-ingredient")
        back()

        // Minced meat, bought by the kilo.
        click("ماده تازه")
        type("نام ماده", "گوشت چرخ‌کرده")
        click("ذخیره")
        waitFor("مقدار خرید")
        type("مبلغ کل", "1600000")
        click("ثبت قیمت")
        waitFor("قیمت امروز")
        back()
        waitFor("هر کیلو ۱٬۶۰۰٬۰۰۰ تومان")
        shot("05-ingredients")

        // Plain rice, made in the kitchen and used inside plates.
        click("دستورها")
        click("دستور تازه")
        type("نام", "چلو")
        click(Labels.kind(RecipeKind.PREP))
        shot("06-new-recipe")
        click("ذخیره")
        click("افزودن ماده")
        click("برنج طارم")
        type("مقدار", "150")
        shot("07-line")
        click("ذخیره")
        waitFor("۱۵۰ گرم")
        back()

        // The plate: 220 g of meat and one portion of rice, sold at 1,350,000 toman.
        click("دستور تازه")
        type("نام", "چلوکباب کوبیده")
        click("ذخیره")
        click("افزودن ماده")
        shot("08-picker")
        click("گوشت چرخ‌کرده")
        type("مقدار", "220")
        click("ذخیره")
        waitFor("۲۲۰ گرم")
        click("افزودن ماده")
        click("چلو")
        type("مقدار", "1")
        click("ذخیره")
        waitFor("۱ پرس")
        click("ثبت قیمت منو")
        type("قیمت (تومان)", "1350000")
        shot("09-menu-price")
        click("ذخیره")
        waitFor("تغییر قیمت منو")
        shot("10-recipe")

        // The live calculator opens on a complete recipe and shows immediately recomputed metrics.
        click("ماشین‌حساب لحظه‌ای قیمت و سود")
        waitFor("درصد فودکاست")
        waitFor("مانده پس از هزینه‌های واردشده")
        shot("10a-instant-calculator")
        click("بستن")
        back()
        shot("11-recipes")

        // With the default 3% for salt and spices a portion costs 435,080 toman, so 1,350,000 is
        // just under the suggested 1,370,000.
        click("منو")
        waitFor("۴۳۵٬۰۸۰ تومان")
        waitFor("نزدیک مرز")
        shot("12-menu")

        click("تنظیمات")
        waitFor("ذخیرهٔ قیمت‌گذاری")
        shot("13-settings")
    }

    private fun waitFor(text: String) {
        rule.waitUntil(timeoutMillis = 15_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun click(text: String) {
        waitFor(text)
        val node = rule.onAllNodesWithText(text).onFirst()
        try {
            node.performScrollTo()
        } catch (e: AssertionError) {
            // Not inside a scrolling column.
        }
        node.performClick()
        rule.waitForIdle()
    }

    private fun type(label: String, value: String) {
        waitFor(label)
        rule.onNodeWithText(label).performTextInput(value)
    }

    private fun replace(label: String, value: String) {
        waitFor(label)
        rule.onNodeWithText(label).performTextReplacement(value)
    }

    private fun back() {
        rule.onNodeWithContentDescription("بازگشت").performClick()
        rule.waitForIdle()
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        Thread.sleep(400)
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(shots, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
