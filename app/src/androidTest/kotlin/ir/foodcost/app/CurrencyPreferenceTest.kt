package ir.foodcost.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.foodcost.core.money.DisplayCurrency
import ir.foodcost.presentation.MoneyFormat
import ir.foodcost.presentation.QuickDrafts
import ir.foodcost.presentation.QuickExtra
import ir.foodcost.presentation.QuickInput
import ir.foodcost.presentation.QuickRow
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Hide the obsolete display choice while retaining the meaning of old saved amounts. */
@RunWith(AndroidJUnit4::class)
class CurrencyPreferenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("foodcost", Context.MODE_PRIVATE)
    private val draftFile get() = File(context.filesDir, "calculator-draft.json")

    @Before fun cleanBefore() = clean()
    @After fun cleanAfter() = clean()

    @Test fun explicitNewRialDraftMigratesToTomanWithoutChangingAmounts() = checkMigration(withCurrency = true)

    @Test fun oldDraftWithoutCurrencyUsesThePreferenceBeforeItIsMigrated() = checkMigration(withCurrency = false)

    private fun checkMigration(withCurrency: Boolean) {
        prefs.edit().putString("currency", DisplayCurrency.NEW_RIAL.name).commit()
        val before = QuickInput(
            dishName = "غذای قدیمی",
            rows = listOf(QuickRow("گوشت", "1450", "170")),
            menuPrice = "630", discountMode = QuickInput.FIXED, discountValue = "10",
            extras = listOf(QuickExtra("ظرف", "15", true)),
        )
        val encoded = QuickDrafts.encode(before, DisplayCurrency.NEW_RIAL)
        val saved = if (withCurrency) encoded else encoded.replace("\"currency\":\"NEW_RIAL\",", "")
        if (!withCurrency) assertFalse(saved.contains("\"currency\""))
        draftFile.writeText(saved)

        val container = AppContainer(context)
        assertEquals(DisplayCurrency.TOMAN, container.currency.value)
        assertEquals(DisplayCurrency.TOMAN.name, prefs.getString("currency", null))
        val after = container.quickDraft.value
        assertEquals(before.dishName, after.dishName)
        val migratedRows = after.rows.filterNot { it.isBlank }
        assertEquals(1, migratedRows.size)
        assertEquals(before.rows.single().qty, migratedRows.single().qty)
        val old = MoneyFormat(DisplayCurrency.NEW_RIAL)
        val current = MoneyFormat(DisplayCurrency.TOMAN)
        assertEquals(old.parse(before.menuPrice), current.parse(after.menuPrice))
        assertEquals(old.parse(before.rows.single().price), current.parse(migratedRows.single().price))
        assertEquals(old.parse(before.discountValue), current.parse(after.discountValue))
        assertEquals(old.parse(before.extras.single().amount), current.parse(after.extras.single().amount))
        assertEquals("630000", after.menuPrice)

        container.saveDraft()
        assertTrue(draftFile.readText().contains("\"currency\":\"TOMAN\""))
        assertEquals(after, QuickDrafts.decode(draftFile.readText(), DisplayCurrency.TOMAN))
    }

    private fun clean() {
        prefs.edit().clear().commit()
        listOf("", ".bak", ".new", ".tmp").forEach { File(context.filesDir, "calculator-draft.json$it").delete() }
    }
}
