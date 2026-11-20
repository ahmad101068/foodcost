package ir.foodcost.presentation

import ir.foodcost.core.costing.CostingRules
import ir.foodcost.core.model.*
import ir.foodcost.core.money.*
import ir.foodcost.core.pricing.*
import ir.foodcost.core.units.*
import ir.foodcost.data.clock.HybridClock
import ir.foodcost.data.ledger.Ledger
import ir.foodcost.data.ledger.saveMenuSections
import ir.foodcost.data.ops.*
import ir.foodcost.data.store.InMemoryOperationStore
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.*

/** Regressions reproduced by the independent release audit. */
class CompleteAuditProbeTest {
    private val today=LocalDate.of(2026,11,18)
    private val money=MoneyFormat(DisplayCurrency.TOMAN)
    private fun ledger():Ledger {
        var clock=1_000_000L
        return Ledger(InMemoryOperationStore(),HybridClock("complete-audit"){clock++},Actor("owner",Role.OWNER)).also {
            it.saveSettings(CostingRules(BigDecimal.ZERO),PricingSettings(defaultMethod=PricingMethod.TargetFoodCost(BigDecimal("40")),vatPercent=BigDecimal.ZERO,rounding=PriceRounding(1)))
        }
    }
    private fun dish()=QuickInput(dishName="غذای ممیزی",menuPrice="500000",rows=listOf(QuickRow("ماده ممیزی","100000","1000")))
    @Test fun sectionRenameShouldRetainFoodRuleAndSuggestedPrice() {
        val l=ledger()
        l.saveSettings(pricing=l.state.pricing.copy(methodByCategory=mapOf("غذای اصلی" to PricingMethod.TargetFoodCost(BigDecimal("20")))))
        val input=dish().copy(category="غذای اصلی",categoryChosen=true)
        val id=QuickCalculator.save(l,assertIs<Checked.Valid<QuickPlan>>(QuickCalculator.check(input,l.state,money,today)).value,today)
        val before=l.state.calculator().evaluate(id,today).suggestion.menuPrice
        val form=SettingsInput.of(l.state.pricing,l.state.costing,money)
        val renamed=form.withCategory(0,form.categories[0].copy(name="غذاهای اصلی"))
        val draft=assertIs<Checked.Valid<SettingsDraft>>(renamed.checkPart(SettingsPart.SECTIONS,l.state.pricing,l.state.costing,money)).value
        l.saveMenuSections(draft.pricing,draft.sectionRenames,today)
        val after=l.state.calculator().evaluate(id,today).suggestion.menuPrice
        println("sectionRename: beforeRials=$before afterRials=$after recipeSection=${l.state.recipes[id]?.category} rules=${l.state.pricing.methodByCategory}")
        assertEquals(before,after,"Renaming an existing section must not silently switch its foods to the general pricing rule")
    }
    @Test fun ingredientNamesAreCurrentMetadataWhilePickedYieldIsDated() {
        val l=ledger()
        val id=l.createIngredient("گوشت",Dimension.MASS,yields=listOf(YieldStage("افت",BigDecimal("0.8"))))
        l.recordPrice(PurchasePrice(id,today,BigDecimal.ONE,Uom.KILOGRAM,10_000_000))
        l.updateIngredient(id,name=Opt.Value("گوشت آینده"),effectiveFrom=today.plusDays(1))
        val typed=dish().copy(rows=listOf(QuickRow("گوشت","1000000","1000")))
        val picked=typed.copy(rows=listOf(QuickCalculator.pick(l.state,typed.rows[0],id,today,money)))
        val a=QuickCalculator.compute(typed,l.state,money,today)
        val b=QuickCalculator.compute(picked,l.state,money,today)
        println("futureRename: datedName=${l.state.termsOn(today).catalog.ingredient(id)?.name} typed=${a.costPerPortion} picked=${b.costPerPortion}")
        // Names are intentionally current metadata; only yield/conversion histories are dated.
        val currentName=typed.copy(rows=listOf(typed.rows[0].copy(name="گوشت آینده")))
        assertEquals("گوشت آینده",picked.rows[0].name)
        assertEquals(b.costPerPortion,QuickCalculator.compute(currentName,l.state,money,today).costPerPortion)
        assertEquals("۱٬۲۵۰٬۰۰۰ تومان",b.costPerPortion)
    }
    @Test fun incompleteFullCostShouldNotHaveSuitableStatus() {
        val l=ledger();l.saveSettings(pricing=l.state.pricing.copy(defaultMethod=PricingMethod.Markup(BigDecimal("100"),CostBasis.FULL_COST)))
        val input=dish().copy(extras=listOf(QuickExtra("دستمزد","")))
        val result=QuickCalculator.compute(input,l.state,money,today)
        println("incompleteExtra: provisional=${result.provisional} status=${result.status} suggested=${result.suggested} leftOut=${result.leftOutNote}")
        assertEquals(PriceStatus.INCOMPLETE,result.status,"Incomplete full cost must not be classified suitable")
    }
    @Test fun malformedNestedBackupShouldBeRejectedWithoutStackOverflow() {
        val text="[".repeat(12000)+"0"+"]".repeat(12000)
        assertFalse(BackupSeal.isSealed(text))
        assertFailsWith<ir.foodcost.data.json.JsonException> { ir.foodcost.data.json.Json.parse(text) }
    }
}
