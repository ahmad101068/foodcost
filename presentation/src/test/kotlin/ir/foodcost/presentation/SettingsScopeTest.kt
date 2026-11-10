package ir.foodcost.presentation

import ir.foodcost.core.bd
import ir.foodcost.core.costing.CostingRules
import ir.foodcost.core.money.DisplayCurrency
import ir.foodcost.core.pricing.OnlineTerms
import ir.foodcost.core.pricing.Overhead
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.core.pricing.PricingScope
import ir.foodcost.core.pricing.PricingSettings
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SettingsScopeTest {
    private val money = MoneyFormat(DisplayCurrency.RIAL)
    private val costing = CostingRules(defaultMiscPercent = 3.bd)
    private val pricing = PricingSettings(
        defaultMethod = PricingMethod.TargetFoodCost("35.00".bd),
        methodByCategory = mapOf(
            "غذای اصلی" to PricingMethod.TargetFoodCost("30.00".bd),
            "نوشیدنی" to PricingMethod.Markup(200.bd),
        ),
        generalSections = listOf("صبحانه"),
    )

    private fun input(rules: PricingSettings = pricing) = SettingsInput.of(rules, costing, money)
    private fun saved(form: SettingsInput, part: SettingsPart, rules: PricingSettings = pricing) =
        assertIs<Checked.Valid<SettingsDraft>>(form.checkPart(part, rules, costing, money)).value

    @Test
    fun `activating general pricing keeps every saved section target and activating sections keeps the general target`() {
        val general = saved(input().copy(scope = PricingScope.GENERAL, percent = "40"), SettingsPart.PRICING)
        assertEquals(PricingScope.GENERAL, general.pricing.scope)
        assertEquals(PricingMethod.TargetFoodCost(40.bd), general.pricing.defaultMethod)
        assertEquals(pricing.methodByCategory, general.pricing.methodByCategory)
        assertEquals(pricing.generalSections, general.pricing.generalSections)
        assertEquals(costing, general.costing)

        // The disabled general field may contain an unfinished draft; selecting section pricing
        // must retain its confirmed value rather than validate or save that draft.
        val sections = saved(
            input(general.pricing).copy(scope = PricingScope.MENU_SECTIONS, percent = "unfinished", carried = true),
            SettingsPart.PRICING,
            general.pricing,
        )
        assertEquals(PricingScope.MENU_SECTIONS, sections.pricing.scope)
        assertEquals(general.pricing.defaultMethod, sections.pricing.defaultMethod)
        assertEquals(pricing.methodByCategory, sections.pricing.methodByCategory)
    }

    @Test
    fun `an unconfirmed or invalid active general percentage cannot be saved`() {
        for (form in listOf(
            input().copy(scope = PricingScope.GENERAL, carried = true),
            input().copy(scope = PricingScope.GENERAL, percent = "100"),
        )) {
            val errors = assertIs<Checked.Invalid>(form.checkPart(SettingsPart.PRICING, pricing, costing, money)).errors
            assertEquals(setOf(Field.PERCENT), errors.keys)
        }
    }

    @Test
    fun `saving general pricing ignores unfinished input from costs and section pages`() {
        val form = input().copy(
            scope = PricingScope.GENERAL,
            percent = "40",
            monthlyFixed = "bad", monthlyPortions = "0", miscPercent = "100", onlineCommission = "100",
            categories = listOf(CategoryTargetInput("", "invalid")),
        )
        val draft = saved(form, SettingsPart.PRICING)
        assertEquals(PricingMethod.TargetFoodCost(40.bd), draft.pricing.defaultMethod)
        assertEquals(pricing.methodByCategory, draft.pricing.methodByCategory)
        assertEquals(pricing.overhead, draft.pricing.overhead)
        assertEquals(pricing.online, draft.pricing.online)
        assertEquals(costing, draft.costing)
    }

    @Test
    fun `saving costs and online terms cannot overwrite pricing drafts or section rules`() {
        val form = input().copy(
            scope = PricingScope.GENERAL, percent = "bad", carried = true, vatPercent = "100",
            categories = listOf(CategoryTargetInput("broken", "bad")),
            miscPercent = "5", monthlyFixed = "1000000", monthlyPortions = "100",
            onlineCommission = "20", onlineDiscount = "10", onlineUplift = "15",
        )
        val draft = saved(form, SettingsPart.COSTS)
        assertEquals(pricing.copy(overhead = Overhead(1_000_000, 100.bd), online = OnlineTerms(20.bd, 10.bd, 15.bd)), draft.pricing)
        assertEquals(costing.copy(defaultMiscPercent = 5.bd), draft.costing)
    }

    @Test
    fun `saving an active section target ignores unconfirmed general drafts`() {
        val form = input().copy(percent = "bad", carried = true, vatPercent = "100", onlineCommission = "100")
            .withCategory(0, input().categories[0].copy(percent = "25"))
        val draft = saved(form, SettingsPart.SECTIONS)
        assertEquals(pricing.copy(methodByCategory = pricing.methodByCategory + ("غذای اصلی" to PricingMethod.TargetFoodCost(25.bd))), draft.pricing)
        assertEquals(costing, draft.costing)

        val invalid = form.withCategory(0, form.categories[0].copy(percent = "0"))
        assertEquals(
            setOf(Field.CATEGORY),
            assertIs<Checked.Invalid>(invalid.checkPart(SettingsPart.SECTIONS, pricing, costing, money)).errors.keys,
        )
    }

    @Test
    fun `general mode leaves disabled targets intact while section labels can be added and renamed`() {
        val rules = pricing.copy(scope = PricingScope.GENERAL)
        val original = input(rules)
        val renamed = original.withCategory(0, original.categories[0].copy(name = "غذاهای اصلی"))
        val edited = renamed
            .withCategory(1, renamed.categories[1].copy(percent = "invalid"))
            .plusCategory("دسر")
        val draft = saved(edited, SettingsPart.SECTIONS, rules)
        assertEquals(PricingScope.GENERAL, draft.pricing.scope)
        assertEquals(
            mapOf("غذاهای اصلی" to pricing.methodByCategory.getValue("غذای اصلی"), "نوشیدنی" to pricing.methodByCategory.getValue("نوشیدنی")),
            draft.pricing.methodByCategory,
        )
        assertEquals(listOf("صبحانه", "دسر"), draft.pricing.generalSections)
        assertEquals(pricing.defaultMethod, draft.pricing.defaultMethod)
    }

    @Test
    fun `duplicate section names remain invalid even when section pricing is inactive`() {
        val rules = pricing.copy(scope = PricingScope.GENERAL)
        val form = input(rules).plusCategory("غذای اصلی")
        assertEquals(
            setOf(Field.CATEGORY),
            assertIs<Checked.Invalid>(form.checkPart(SettingsPart.SECTIONS, rules, costing, money)).errors.keys,
        )
    }
}
