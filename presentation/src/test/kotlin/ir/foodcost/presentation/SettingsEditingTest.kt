package ir.foodcost.presentation

import ir.foodcost.core.costing.CostingRules
import ir.foodcost.core.money.DisplayCurrency
import ir.foodcost.core.pricing.PricingSettings
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SettingsEditingTest {
    private fun baseline() = SettingsInput.of(PricingSettings(), CostingRules(), MoneyFormat(DisplayCurrency.TOMAN))
    @Test
    fun `a saved value remains clean if the next commit arrives before rendering`() {
        val baseline = baseline().copy(percent = "40")
        val submitted = baseline.copy(percent = "150")
        val latest = baseline.copy(percent = "200")
        val saved = acknowledgeSettingsSave(submitted, baseline, submitted, submitted, SettingsPart.PRICING)
        assertEquals("200", rebaseSettingsDraft(saved, submitted, latest).percent)
        assertEquals("200", acknowledgeSettingsSave(submitted, baseline, submitted, latest, SettingsPart.PRICING).percent)
    }

    @Test
    fun `a second edit made during save and another page draft are retained`() {
        val baseline = baseline().copy(percent = "40", monthlyFixed = "100")
        val submitted = baseline.copy(percent = "150")
        val draft = submitted.copy(percent = "175", monthlyFixed = "250")
        val saved = acknowledgeSettingsSave(draft, baseline, submitted, submitted, SettingsPart.PRICING)
        assertEquals("175", saved.percent)
        assertEquals("250", saved.monthlyFixed)
    }

    @Test
    fun `a saved section rename starts a fresh identity for the next rename`() {
        val old = CategoryTargetInput(name = "اصلی", originalName = "اصلی")
        val baseline = baseline().copy(categories = listOf(old))
        val submitted = baseline.copy(categories = listOf(old.copy(name = "جدید")))
        val latest = submitted.copy(categories = listOf(old.copy(name = "جدید", originalName = "جدید")))
        assertEquals(latest.categories,
            acknowledgeSettingsSave(submitted, baseline, submitted, latest, SettingsPart.SECTIONS).categories)
    }
}
