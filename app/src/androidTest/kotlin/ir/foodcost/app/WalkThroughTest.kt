package ir.foodcost.app

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.core.pricing.PricingScope
import ir.foodcost.core.pricing.PricingSettings
import ir.foodcost.presentation.Labels
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal

/**
 * Walks through the app the way the hall owner uses it: the koobideh priced in the calculator with
 * his own numbers, saved, found among the dishes, re-priced when meat gets dearer (a slip of a zero
 * flagged on the way, and the change shown before it is saved), an ingredient price updated from the
 * list, the detailed screens, then a food cost target for main dishes and the calculation shared as
 * text. Last, the switch to rials keeps the calculation as it was, and the phone's largest text cuts
 * off no amount. Screenshots of each step are left in the app's files for the workflow to collect.
 */
@RunWith(AndroidJUnit4::class)
class WalkThroughTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val container get() = (instrumentation.targetContext.applicationContext as FoodCostApplication).container
    private val shots = File(instrumentation.targetContext.filesDir, "screenshots").apply {
        deleteRecursively()
        mkdirs()
    }

    /** Every instrumented test must begin with a clean app database and settings. */
    @org.junit.Before
    fun resetWalkthroughState() {
        val context = instrumentation.targetContext
        context.deleteDatabase(ir.foodcost.app.data.SqliteOperationStore.DATABASE)
        context.getSharedPreferences("foodcost", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "calculator-draft.json").delete()
    }

    @Test
    fun aPlateIsCostedInTheCalculatorAndSaved() {
        ActivityScenario.launch(MainActivity::class.java).use {
            try {
                walk()
            } catch (e: Throwable) {
                shot("99-failure")
                throw e
            }
        }
    }

    private fun walk() {
        // Start the visual flow explicitly: emulator IME state need not give the
        // first field focus after another test or an activity recreation.
        waitForTag("row-0-name")
        scrolled(rule.onNodeWithTag("row-0-name")).performClick()
        waitForFocus("row-0-name")
        show("calculator-dashboard")
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
        waitForFigure("cost", "۳۶۴٬۱۰۵")
        waitForFigure("suggested", "۱٬۱۵۰٬۰۰۰")
        show("calculator-dashboard")
        shot("02-calculator")

        // Sold at 630,000 toman (VAT included): 572,727 net, a 63.6% food cost, 208,622 left. Under-priced.
        // The keyboard's tick closes it, and the display shows the line under its figures again.
        typeTag("menu-price", "630000")
        rule.onNodeWithTag("menu-price").performImeAction()
        waitForFigure("net-sales", "۵۷۲٬۷۲۷")
        waitForFigure("food-cost", "۶۳٫۶٪")
        waitForFigure("gross-profit", "۲۰۸٬۶۲۲")
        waitFor("خیلی پایین")
        show("calculator-dashboard")
        shot("03-under-priced")

        // A tap on a figure explains the word with the dish's own numbers; the glossary is a tap away.
        rule.onNodeWithTag("food-cost").performClick()
        waitForPart("۳۶۴٬۱۰۵ ÷ ۵۷۲٬۷۲۷ × ۱۰۰")
        shot("04-term")
        click("همهٔ واژه‌ها")
        waitForTag("term-food-cost")
        waitForPart("مثال: چلوکباب کوبیده")
        shot("05-help-terms")
        back()

        // A takeaway box of 15,000 toman: one tap adds it, and takeaway shows beside the restaurant.
        openCalculatorDetails()
        click("ظرف و بسته‌بندی")
        waitForFocus("extra-0-amount")
        typeTag("extra-0-amount", "15000")
        rule.onNodeWithTag("extra-0-amount").performImeAction()
        waitFor("بیرون‌بر")
        waitForPart("۱۹۳٬۶۲۲")
        waitForFigure("gross-profit", "۲۰۸٬۶۲۲") // the box is not used at the table
        show("channel-remaining")
        shot("06-takeaway")

        // A new dish of new ingredients changes nothing saved, so it is saved at once.
        typeTag("dish-name", "چلوکباب کوبیده")
        click("ذخیره")
        waitForPart("در غذاها ذخیره شد")
        shot("07-saved")

        // The dishes tab shows the plate and that its price no longer covers it.
        click("غذاها")
        waitFor("چلوکباب کوبیده")
        waitFor("خیلی پایین")
        waitFor("۱ غذا باید گران‌تر شود")
        shot("08-dishes")

        // The saved dish opens in the calculator. A meat price with a zero too many is flagged at once.
        val savedDish = requireNotNull(container.state.value?.recipes?.values?.firstOrNull {
            it.name == "چلوکباب کوبیده" && !it.archived
        }) { "The freshly saved dish is missing from the ledger" }
        // The saved quick-calculator recipe must be compatible before navigating. This
        // distinguishes a malformed saved recipe from a lost card tap in emulator tests.
        val savedState = requireNotNull(container.state.value)
        checkNotNull(ir.foodcost.presentation.QuickCalculator.open(
            savedState, savedDish.id, java.time.LocalDate.now(),
            ir.foodcost.presentation.MoneyFormat(container.currency.value),
        )) { "Saved dish is not readable by the quick calculator" }
        // A real touch must open the card while the previous save feedback is still visible.
        waitForTag("food-${savedDish.id.value}")
        scrolled(rule.onNodeWithTag("food-${savedDish.id.value}"))
            .performClick()
        waitForTag("row-0-price")
        replaceTag("row-0-price", "16000000")
        waitForPart("۱۰ برابر قیمت قبلی")
        show("row-0-warning")
        shot("09-price-slip")

        // Dearer meat, typed right: the cost shows at once.
        replaceTag("row-0-price", "1600000")
        waitForFigure("cost", "۳۹۰٬۳۷۰")
        shot("10-dish")

        // Before the save, what it changes: the plate's figures and the meat price for every dish.
        click("ذخیره")
        waitForTag("review-save")
        waitForPart("از ۱٬۴۵۰٬۰۰۰ به ۱٬۶۰۰٬۰۰۰")
        shot("11-review")
        rule.onNodeWithTag("review-save").performClick()
        waitForPart("در غذاها ذخیره شد")
        back()

        // A new onion price from the list, in one field.
        click("مواد")
        // LazyColumn only exposes on-screen cards to the semantics tree. Scroll to the saved
        // meat ingredient before asserting its price; alphabetical order puts rice first.
        rule.onNodeWithTag("ingredients-list").performScrollToNode(hasText("گوشت"))
        waitFor("هر کیلو ۱٬۶۰۰٬۰۰۰ تومان")
        shot("12-ingredients")
        rule.onNodeWithTag("ingredients-list").performScrollToNode(hasContentDescription("قیمت تازهٔ پیاز"))
        rule.onNodeWithContentDescription("قیمت تازهٔ پیاز").performClick()
        replaceTag("quick-price", "90000")
        click("ذخیره")
        waitFor("هر کیلو ۹۰٬۰۰۰ تومان")
        shot("13-quick-price")

        // The detailed screens are still there: a purchase by the bag, and plain rice as a preparation.
        rule.onNodeWithTag("ingredients-list").performScrollToNode(hasText("برنج"))
        click("برنج")
        click("قیمت تازه")
        replace("مقدار خرید", "10")
        type("مبلغ کل", "3600000")
        click("ثبت قیمت")
        waitFor("قیمت امروز")
        shot("14-ingredient")
        back()

        click("غذاها")
        click("دستور پایه (برای ترکیبات مشترک)")
        type("نام", "چلو")
        click("ذخیره")
        click("افزودن ماده")
        click("برنج")
        type("مقدار", "150")
        click("ذخیره")
        waitFor("۱۵۰ گرم")
        shot("15-recipe")
        back()

        // The three pricing methods put the same price, retaining the recurring markup's precision.
        settingsOverview()
        clickTag("settings-section-pricing")
        clickTag("pricing-scope-general")
        waitFor("ذخیرهٔ قیمت‌گذاری")
        click("حاشیهٔ سود")
        waitFor("65")
        click("مارک‌آپ")
        waitForPart("185.714285714285")
        waitForPart("یعنی فودکاست ۳۵٪ و حاشیهٔ سود ۶۵٪")
        quiet()
        shot("16-settings")
        click("فودکاست هدف")
        waitFor("35")
        clickTag("settings-save-pricing")
        waitForPricing { it.scope == PricingScope.GENERAL }
        waitForPart("تنظیمات ذخیره شد")

        // The guide to using the app, from the settings.
        clickTag("settings-back")
        clickTag("settings-section-help")
        click("کار با برنامه")
        waitFor("حساب یک غذا")
        shot("17-help-guide")
        back()

        // The scope selector is exclusive: section targets are editable only in section mode.
        clickTag("settings-back")
        clickTag("settings-section-pricing")
        clickTag("pricing-scope-sections")
        clickTag("settings-save-pricing")
        waitForPricing { it.scope == PricingScope.MENU_SECTIONS }
        waitForPart("تنظیمات ذخیره شد")
        clickTag("settings-back")
        clickTag("settings-section-sections")

        // Main dishes aim at a 30% food cost; a tap on the section puts the keyboard on its target.
        click("غذای اصلی")
        waitForFocus("category-0-percent")
        typeTag("category-0-percent", "30")
        show("category-0-name")
        shot("18-sections")
        clickTag("settings-save-sections")
        waitForPricing { it.methodByCategory["غذای اصلی"] == PricingMethod.TargetFoodCost(BigDecimal(30)) }
        waitForPart("تنظیمات ذخیره شد")

        // Online sales with a 20% commission: the online column joins the restaurant and takeaway.
        clickTag("settings-back")
        clickTag("settings-section-costs")
        type("کمیسیون سامانه", "20")
        clickTag("settings-save-costs")
        waitForPricing { it.online.commissionPercent.compareTo(BigDecimal(20)) == 0 }
        waitForPart("تنظیمات ذخیره شد")
        click("ماشین‌حساب")
        openCalculatorDetails()
        waitFor("آنلاین")
        quiet()
        show("channel-remaining")
        shot("19-online")

        // The koobideh put among the main dishes is priced at their 30%: 1,340,000 toman.
        clickTag("menu-section-selector")
        click("غذای اصلی")
        waitForPart("۱٬۳۴۰٬۰۰۰")
        show("dish-name")
        shot("20-section-price")

        share()
        darkTheme()

        // Switched to rials, the calculation is the same plate: 364,105 toman is 3,641,050 rials.
        settingsOverview()
        clickTag("settings-section-currency")
        waitFor("تومان")
        rule.onAllNodesWithText("ریال جدید").assertCountEquals(0)
        click("ریال")
        clickTag("settings-back")
        clickTag("settings-section-backup")
        backupPassword()
        click("ماشین‌حساب")
        waitForFigure("cost", "۳٬۶۴۱٬۰۵۰")
        largeText()
    }

    /**
     * The amounts in rials with the phone's text larger and largest, for a look that no amount is
     * cut off. Every required picture must be produced.
     */
    private fun largeText() {
        try {
            if (rule.onAllNodesWithText("بعداً").fetchSemanticsNodes().isNotEmpty()) click("بعداً")
            textSize("1.5")
            show("cost")
            shot("24-larger-text")
            textSize("2.0")
            show("cost")
            shot("25-largest-text")
            show("row-0-price")
            shot("26-largest-text-rows")
            show("channel-remaining")
            shot("27-largest-text-channels")
            for (index in 0..2) {
                for (field in listOf("price", "cost", "remaining")) {
                    val tag = if (index == 0 && field == "remaining") "channel-remaining" else "channel-$index-$field"
                    val node = rule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
                    val layouts = mutableListOf<TextLayoutResult>()
                    node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { check(it(layouts)) }
                    check(layouts.isNotEmpty() && layouts.none { it.hasVisualOverflow }) {
                        "$tag overflows at 2x text: " + layouts.joinToString { layout ->
                            "size=${layout.size}, paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}, " +
                                "constraints=${layout.layoutInput.constraints}, width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}"
                        }
                    }
                }
            }
            shot("27b-largest-text-online")
            click("غذاها")
            waitForTag("food-list")
            rule.onNodeWithTag("food-list").performScrollToNode(hasText("چلوکباب کوبیده", substring = true))
            rule.onAllNodesWithText("چلوکباب کوبیده").onFirst().assertIsDisplayed()
            shot("28-largest-text-dishes")
        } finally {
            shell("settings put system font_scale 1.0")
        }
    }

    /** A backup is offered a password, with why it is worth one; too short a password is refused. */
    private fun backupPassword() {
        click("ساخت پشتیبان")
        waitFor("ساخت بدون رمز")
        shot("29-backup-password")
        type("رمز (اگر می‌خواهید)", "123")
        click("ساخت با رمز")
        waitFor("رمز دست‌کم ۶ حرف باشد.")
        shot("30-backup-short-password")
        click("برگشت")
        rule.waitUntil(timeoutMillis = 15_000) { rule.onAllNodesWithText("ساخت با رمز").fetchSemanticsNodes().isEmpty() }
    }

    /** Sets the phone's text size; the app's screen is made again with it. */
    private fun textSize(scale: String) {
        shell("settings put system font_scale $scale")
        Thread.sleep(3000)
        rule.waitForIdle()
        waitForTag("cost")
    }

    /** The calculation handed to the phone's share sheet as text. Every required picture must be produced. */
    private fun share() {
        val device = UiDevice.getInstance(instrumentation)
        val app = instrumentation.targetContext.packageName
        try {
            rule.onNodeWithContentDescription("فرستادن نتیجه").performClick()
            device.wait(Until.gone(By.pkg(app).depth(0)), 5_000)
            Thread.sleep(1500)
            shot("21-share")
        } finally {
            if (device.currentPackageName != app) device.pressBack()
            device.wait(Until.hasObject(By.pkg(app).depth(0)), 5_000)
            rule.waitForIdle()
        }
    }

    /** Two pictures in the dark theme, for a look at its colors. Every required picture must be produced. */
    private fun darkTheme() {
        try {
            shell("cmd uimode night yes")
            Thread.sleep(1500)
            click("ماشین‌حساب")
            shot("22-dark-calculator")
            click("غذاها")
            shot("23-dark-dishes")
        } finally {
            shell("cmd uimode night no")
        }
    }

    /** Lets the message at the bottom run out, so it does not cover the picture. */
    private fun quiet() {
        rule.mainClock.advanceTimeBy(5_000)
        rule.waitForIdle()
    }

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }

    private fun typeRow(index: Int, name: String, price: String, qty: String) {
        typeTag("row-$index-name", name)
        typeTag("row-$index-price", price)
        typeTag("row-$index-qty", qty)
    }

    private fun settingsOverview() {
        click("تنظیمات")
        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodesWithTag("settings-overview").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithTag("settings-back").fetchSemanticsNodes().isNotEmpty()
        }
        if (rule.onAllNodesWithTag("settings-overview").fetchSemanticsNodes().isEmpty()) clickTag("settings-back")
        waitForTag("settings-overview")
    }

    private fun openCalculatorDetails() {
        if (rule.onAllNodesWithTag("channel-remaining").fetchSemanticsNodes().isEmpty()) {
            clickTag("calculator-details-toggle")
        }
    }

    private fun waitForPricing(check: (PricingSettings) -> Boolean) {
        rule.waitUntil(timeoutMillis = 15_000) { container.state.value?.pricing?.let(check) == true }
    }

    private fun clickTag(tag: String) {
        waitForTag(tag)
        scrolled(rule.onNodeWithTag(tag)).performClick()
        rule.waitForIdle()
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

    /** Waits until the display's figure [tag] shows [number]. */
    private fun waitForFigure(tag: String, number: String) {
        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodes(hasTestTag(tag) and hasText(number, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Scrolls the node tagged [tag] into sight, for a picture. */
    private fun show(tag: String) {
        waitForTag(tag)
        scrolled(rule.onNodeWithTag(tag))
        rule.waitForIdle()
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

    /**
     * The CI emulator's launcher sometimes stops answering under load, and the system's dialog about
     * it covers the screen. The app is not involved; "Wait" closes the dialog and kills nothing.
     */
    private fun dismissSystemDialogs() {
        val device = UiDevice.getInstance(instrumentation)
        repeat(3) {
            val wait = device.findObject(By.res("android", "aerr_wait")) ?: device.findObject(By.text("Wait")) ?: return
            try {
                wait.click()
            } catch (e: StaleObjectException) {
                // The dialog went away by itself between being found and the click.
            }
            device.waitForIdle()
        }
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        dismissSystemDialogs()
        // The emulator draws slowly; without the pause a picture can still show the screen before.
        Thread.sleep(1200)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Screenshot $name was not captured" }
        File(shots, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Screenshot $name could not be encoded" }
        }
    }
}
