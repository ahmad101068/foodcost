package ir.foodcost.presentation

import ir.foodcost.core.money.DisplayCurrency
import ir.foodcost.core.pricing.PriceStatus
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.data.clock.HybridClock
import ir.foodcost.data.ledger.Ledger
import ir.foodcost.data.ops.Actor
import ir.foodcost.data.ops.Role
import ir.foodcost.data.store.InMemoryOperationStore
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HelpTest {
    private val toman = MoneyFormat(DisplayCurrency.TOMAN)
    private val today: LocalDate = LocalDate.of(2026, 11, 3)

    private fun ledger(): Ledger {
        var wall = 1_000_000L
        return Ledger(InMemoryOperationStore(), HybridClock("A") { wall++ }, Actor("owner", Role.OWNER))
    }

    private fun koobideh(ledger: Ledger = ledger(), input: QuickInput = Guide.sample(toman)): QuickResult =
        QuickCalculator.compute(input, ledger.state, toman, today)

    private fun example(id: String): String = Glossary.term(id)!!.example!!

    private fun amount(text: String?): String = text!!.removeSuffix(" تومان")

    @Test
    fun `every term has its own id and a meaning, and the figures point at real terms`() {
        assertEquals(Glossary.TERMS.size, Glossary.TERMS.map { it.id }.toSet().size)
        assertTrue(Glossary.TERMS.all { it.title.isNotBlank() && it.meaning.isNotBlank() })
        assertTrue(koobideh().explanations.keys.all { Glossary.term(it) != null })
        assertEquals(8, Guide.STEPS.size)
    }

    @Test
    fun `a term is found by any of its names, however it is typed`() {
        assertEquals(Glossary.MARKUP, Glossary.search("مارک آپ").first().id)
        assertEquals(Glossary.MARGIN, Glossary.search("مارجین").first().id)
        assertEquals(Glossary.COST, Glossary.search("قیمت تمام شده").first().id)
        assertEquals(Glossary.OVERHEAD, Glossary.search("اجاره").first().id)
        assertEquals(Glossary.TERMS, Glossary.search(" "))
    }

    @Test
    fun `each figure is explained with the dish's own numbers`() {
        val lines = koobideh().explanations
        assertEquals("بهای مواد این پرس ۳۶۴٬۱۰۵ تومان است و هزینهٔ دیگری برایش ثبت نشده.", lines[Glossary.COST])
        assertEquals("۶۳۰٬۰۰۰ ÷ ۱٫۱ = ۵۷۲٬۷۲۷ تومان", lines[Glossary.NET_SALES])
        assertEquals("۳۶۴٬۱۰۵ ÷ ۵۷۲٬۷۲۷ × ۱۰۰ = ۶۳٫۶٪", lines[Glossary.FOOD_COST])
        assertEquals("۵۷۲٬۷۲۷ − ۳۶۴٬۱۰۵ = ‎+۲۰۸٬۶۲۲ تومان", lines[Glossary.REMAINING])
        assertEquals("(۵۷۲٬۷۲۷ − ۳۶۴٬۱۰۵) ÷ ۵۷۲٬۷۲۷ × ۱۰۰ = ۳۶٫۴٪", lines[Glossary.MARGIN])
        assertEquals("(۵۷۲٬۷۲۷ − ۳۶۴٬۱۰۵) ÷ ۳۶۴٬۱۰۵ × ۱۰۰ = ۵۷٫۳٪", lines[Glossary.MARKUP])
        assertEquals("۵۷۲٬۷۲۷ × ۳۵٪ = ۲۰۰٬۴۵۵ تومان", lines[Glossary.MAX_COST])
        assertEquals("۳۶۴٬۱۰۵ ÷ ۳۵٪ = ۱٬۰۴۰٬۳۰۰، با مالیات ۱٬۱۴۴٬۳۳۰، گرد شده ۱٬۱۵۰٬۰۰۰ تومان", lines[Glossary.SUGGESTED])

        assertEquals("۳۵۳٬۵۰۰ جمع مواد + ۱۰٬۶۰۵ هزینه‌های جزئی (۳٪) = ۳۶۴٬۱۰۵ تومان", lines[Glossary.FOOD])
        assertEquals("۵۷۲٬۷۲۷ − ۳۶۴٬۱۰۵ = ‎+۲۰۸٬۶۲۲ تومان", lines[Glossary.GROSS_PROFIT])

        val withWage = koobideh(input = Guide.sample(toman).plusExtra(QuickExtra("دستمزد", "20000")))
        assertEquals("۳۶۴٬۱۰۵ بهای مواد + ۲۰٬۰۰۰ هزینه‌های دیگر = ۳۸۴٬۱۰۵ تومان", withWage.explanations[Glossary.COST])
        // The display's figures and what tapping them explains end in the same number.
        assertTrue(withWage.explanations.getValue(Glossary.FOOD).endsWith(withWage.costPerPortion))
        assertTrue(withWage.explanations.getValue(Glossary.GROSS_PROFIT).endsWith(withWage.grossProfit!!))
        assertTrue(withWage.explanations.getValue(Glossary.COST).endsWith(withWage.totalCost))
        assertTrue(withWage.explanations.getValue(Glossary.REMAINING).endsWith(withWage.remaining!!))
        assertEquals("۵۷۲٬۷۲۷ − ۳۸۴٬۱۰۵ = ‎+۱۸۸٬۶۲۲ تومان", withWage.explanations[Glossary.REMAINING])
        // Without a menu price only the costs and the suggestion can be explained.
        assertEquals(setOf(Glossary.FOOD, Glossary.COST, Glossary.SUGGESTED), koobideh(input = Guide.sample(toman).copy(menuPrice = "")).explanations.keys)
    }

    @Test
    fun `the glossary's examples use the numbers the calculator gives`() {
        val result = koobideh()
        val food = amount(result.costPerPortion)
        val net = amount(result.netSales)
        assertTrue(example(Glossary.FOOD_COST).contains("$food ÷ $net × ۱۰۰ = ${result.foodCostPercent}"))
        assertTrue(example(Glossary.FOOD).contains(food))
        assertTrue(example(Glossary.NET_SALES).contains(net))
        assertTrue(example(Glossary.REMAINING).contains(amount(result.remaining).removePrefix("‎+")))
        assertTrue(example(Glossary.GROSS_PROFIT).contains(amount(result.grossProfit).removePrefix("‎+")))
        assertTrue(example(Glossary.MAX_COST).contains(amount(result.maxCost)))
        assertTrue(example(Glossary.SUGGESTED).contains(amount(result.suggested)))
        assertTrue(result.explanations.getValue(Glossary.SUGGESTED).contains("با مالیات ۱٬۱۴۴٬۳۳۰"))
        assertTrue(example(Glossary.STATUS).contains("۱٬۱۴۴٬۳۳۰") && example(Glossary.ROUNDING).contains("۱٬۱۴۴٬۳۳۰"))
        assertEquals(PriceStatus.UNDER, result.status)

        val withWage = koobideh(input = Guide.sample(toman).plusExtra(QuickExtra("دستمزد", "20000")))
        assertTrue(example(Glossary.COST).contains(amount(withWage.totalCost)))
        assertTrue(example(Glossary.REMAINING).contains(amount(withWage.remaining).removePrefix("‎+")))
        val withBox = koobideh(input = Guide.sample(toman).plusExtra(QuickExtra("ظرف و بسته‌بندی", "15000", takeawayOnly = true)))
        val takeaway = withBox.channels.single { it.label == "بیرون‌بر" }
        assertTrue(example(Glossary.EXTRAS).contains(amount(takeaway.remaining).removePrefix("‎+")))
        assertTrue(example(Glossary.CHANNELS).contains(amount(takeaway.priceForSameRemaining)))

        val ledger = ledger()
        ledger.saveSettings(ledger.state.costing, ledger.state.pricing.copy(methodByCategory = mapOf("غذای اصلی" to PricingMethod.TargetFoodCost(BigDecimal(30)))))
        val main = koobideh(ledger, Guide.sample(toman).copy(category = "غذای اصلی"))
        assertTrue(example(Glossary.SECTION).contains(amount(main.suggested)))
    }

    @Test
    fun `the guide's sample is the koobideh of the examples, in any currency`() {
        assertEquals("۳۶۴٬۱۰۵ تومان", koobideh().costPerPortion)
        val rial = MoneyFormat(DisplayCurrency.RIAL)
        assertEquals("۳٬۶۴۱٬۰۵۰ ریال", QuickCalculator.compute(Guide.sample(rial), ledger().state, rial, today).costPerPortion)
        assertTrue(Guide.sample(toman).rows.last().isBlank)
    }
}
