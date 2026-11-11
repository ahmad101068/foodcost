package ir.foodcost.data

import ir.foodcost.core.bd
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.core.pricing.PricingScope
import ir.foodcost.core.pricing.PricingSettings
import ir.foodcost.core.reports.MenuSnapshot
import ir.foodcost.data.clock.Hlc
import ir.foodcost.data.json.Json
import ir.foodcost.data.json.JsonException
import ir.foodcost.data.json.JsonObject
import ir.foodcost.data.ledger.Ledger
import ir.foodcost.data.ledger.RejectReason
import ir.foodcost.data.ops.Actor
import ir.foodcost.data.ops.Backup
import ir.foodcost.data.ops.Change
import ir.foodcost.data.ops.OpId
import ir.foodcost.data.ops.Operation
import ir.foodcost.data.ops.OperationCodec
import ir.foodcost.data.ops.Role
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PricingScopeCodecTest {
    private val rules = PricingSettings(
        defaultMethod = PricingMethod.TargetFoodCost("35".bd),
        methodByCategory = mapOf("main" to PricingMethod.TargetFoodCost("20".bd)),
        generalSections = listOf("dessert"),
    )

    private fun operation(settings: PricingSettings): Operation = Operation(
        OpId("scope-settings"), Hlc(1, 0, "A"), Actor("owner", Role.OWNER),
        Change.SettingsSaved(pricing = settings),
    )

    private fun decode(text: String): Operation = OperationCodec.decode(Json.parse(text) as JsonObject)

    @Test
    fun `both scope choices survive operation and backup round trips without losing inactive rules`() {
        for (scope in PricingScope.entries) {
            val original = operation(rules.copy(scope = scope))
            val encoded = Json.write(OperationCodec.encode(original))
            assertEquals(original, decode(encoded))
            assertEquals(listOf(original), Backup.read(Backup.write(listOf(original))))
            val pricing = (decode(encoded).change as Change.SettingsSaved).pricing!!
            assertEquals(rules.defaultMethod, pricing.defaultMethod)
            assertEquals(rules.methodByCategory, pricing.methodByCategory)
            assertEquals(rules.generalSections, pricing.generalSections)
        }
    }

    @Test
    fun `legacy rows without a scope retain the section policy and canonical representation`() {
        val original = operation(rules)
        val legacy = Json.write(OperationCodec.encode(original))
        assertFalse("\"scope\"" in legacy)
        val decoded = decode(legacy)
        val pricing = (decoded.change as Change.SettingsSaved).pricing!!
        assertEquals(PricingScope.MENU_SECTIONS, pricing.scope)
        assertEquals(rules, pricing)
        assertEquals(legacy, Json.write(OperationCodec.encode(decoded)))

        // Explicit legacy scope is accepted too; absence is the canonical spelling.
        val explicit = legacy.replace("\"defaultMethod\":", "\"scope\":\"MENU_SECTIONS\",\"defaultMethod\":")
        assertEquals(decoded, decode(explicit))
    }

    @Test
    fun `an unknown pricing scope is refused rather than interpreted as another pricing policy`() {
        val text = Json.write(OperationCodec.encode(operation(rules.copy(scope = PricingScope.GENERAL))))
        assertTrue("\"scope\":\"GENERAL\"" in text)
        assertFailsWith<JsonException> { decode(text.replace("\"GENERAL\"", "\"FUTURE_POLICY\"")) }
    }

    @Test
    fun `general-only pricing requires backup version eight even in an emergency backup`() {
        val legacy = operation(rules)
        val general = operation(rules.copy(scope = PricingScope.GENERAL)).copy(id = OpId("general-settings"))
        fun version(text: String) = (Json.parse(text) as JsonObject).int("version")

        // Existing legacy files retain their previous minimum readable version.
        assertEquals(2, version(Backup.write(listOf(legacy))))
        assertEquals(8, version(Backup.write(listOf(legacy, general))))
        val unreadable = listOf(Backup.Unreadable("damaged", "unreadable stored row"))
        val emergency = Backup.write(listOf(legacy, general), unreadable)
        assertEquals(8, version(emergency))
        assertEquals(listOf(legacy, general), Backup.read(emergency))
        assertEquals(unreadable, Backup.unreadable(emergency))
        assertEquals(7, version(Backup.write(listOf(legacy), unreadable)))
    }

    @Test
    fun `a future scope switch changes future prices but leaves closed figures intact after restoring`() {
        val kitchen = Kitchen(T.ledger("source"))
        val ledger = kitchen.ledger
        ledger.saveSettings(pricing = rules, effectiveFrom = T.D1).applied()
        val before = MenuSnapshot.of(ledger.calculator().menu(T.D31))
        ledger.closePeriod(T.D31).applied()
        val official = ledger.state.closedPeriods.single().snapshot

        ledger.saveSettings(pricing = rules.copy(scope = PricingScope.GENERAL), effectiveFrom = T.N2).applied()
        assertEquals(before, MenuSnapshot.of(ledger.calculator().menu(T.D31)))
        assertEquals(official, ledger.state.closedPeriods.single().snapshot)
        val oldSuggestion = ledger.calculator().evaluate(kitchen.kebab, T.D31).suggestion.menuPrice
        val newSuggestion = ledger.calculator().evaluate(kitchen.kebab, T.N2).suggestion.menuPrice
        assertNotEquals(oldSuggestion, newSuggestion)
        assertEquals(rules.methodByCategory, ledger.state.pricing.methodByCategory)

        val restored = T.ledger("restored")
        val operations = Backup.read(ledger.exportBackup())
        assertEquals(operations.size, restored.receive(operations))
        assertEquals(PricingScope.GENERAL, restored.state.pricing.scope)
        assertEquals(before, MenuSnapshot.of(restored.calculator().menu(T.D31)))
        assertEquals(official, restored.state.closedPeriods.single().snapshot)
        assertEquals(newSuggestion, restored.calculator().evaluate(kitchen.kebab, T.N2).suggestion.menuPrice)

        // Restoring the preserved section policy makes its original target active again.
        restored.saveSettings(pricing = rules, effectiveFrom = T.N2.plusDays(1)).applied()
        assertEquals(oldSuggestion, restored.calculator().evaluate(kitchen.kebab, T.N2.plusDays(1)).suggestion.menuPrice)
        assertEquals(before, MenuSnapshot.of(restored.calculator().menu(T.D31)))
    }

    @Test
    fun `a scope change on a closed day is refused`() {
        val kitchen = Kitchen(T.ledger("locked"))
        kitchen.ledger.saveSettings(pricing = rules, effectiveFrom = T.D1).applied()
        kitchen.ledger.closePeriod(T.D31).applied()
        assertEquals(
            RejectReason.PERIOD_CLOSED,
            kitchen.ledger.saveSettings(pricing = rules.copy(scope = PricingScope.GENERAL), effectiveFrom = T.D15).rejection(),
        )
        assertEquals(PricingScope.MENU_SECTIONS, kitchen.ledger.state.pricing.scope)
    }
}
