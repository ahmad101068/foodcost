package ir.foodcost.data

import ir.foodcost.core.bd
import ir.foodcost.core.costing.CostingRules
import ir.foodcost.core.model.ComponentRef
import ir.foodcost.core.model.ExtraCost
import ir.foodcost.core.model.IngredientId
import ir.foodcost.core.model.PurchasePrice
import ir.foodcost.core.model.QtyBasis
import ir.foodcost.core.model.RecipeId
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.model.RecipeLine
import ir.foodcost.core.model.RecipeVersion
import ir.foodcost.core.model.YieldStage
import ir.foodcost.core.pricing.CostBasis
import ir.foodcost.core.pricing.OnlineTerms
import ir.foodcost.core.pricing.Overhead
import ir.foodcost.core.pricing.PriceStatus
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.core.pricing.PricingSettings
import ir.foodcost.core.reports.MenuSnapshot
import ir.foodcost.core.units.Conversions
import ir.foodcost.core.units.Dimension
import ir.foodcost.core.units.Uom
import ir.foodcost.data.T.D1
import ir.foodcost.data.T.D31
import ir.foodcost.data.clock.Hlc
import ir.foodcost.data.json.Json
import ir.foodcost.data.json.JsonException
import ir.foodcost.data.json.JsonObject
import ir.foodcost.data.ops.Actor
import ir.foodcost.data.ops.Backup
import ir.foodcost.data.ops.Change
import ir.foodcost.data.ops.OpId
import ir.foodcost.data.ops.Operation
import ir.foodcost.data.ops.OperationCodec
import ir.foodcost.data.ops.Opt
import ir.foodcost.data.ops.Role
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OperationCodecTest {
    private val rice = IngredientId("rice")
    private val dish = RecipeId("dish")

    @Test
    fun `every kind of operation survives the trip through JSON`() {
        val changes = listOf(
            Change.IngredientSaved(
                rice, Opt.Value("برنج"), Opt.Value(Dimension.MASS), Opt.Value(null),
                Opt.Value(listOf(YieldStage("شستن", "0.98".bd))), Opt.Value(Conversions(gramsPerPiece = "120".bd)), Opt.Value(true),
            ),
            Change.IngredientSaved(rice, name = Opt.Value("برنج هاشمی")),
            Change.PriceRecorded(
                "p1",
                PurchasePrice(rice, D1, "10".bd, Uom.KILOGRAM, 46_000_000, 1_000_000, 4_500_000, true, 200_000, "فروشگاه \"شمال\""),
            ),
            Change.PriceVoided("p1"),
            Change.RecipeSaved(
                dish, Opt.Value("کباب"), Opt.Value(RecipeKind.MENU_ITEM), Opt.Value("main"),
                Opt.Value(PricingMethod.TargetMargin("30".bd, CostBasis.FULL_COST)), Opt.Value(false),
            ),
            Change.RecipeSaved(dish, pricing = Opt.Value(null)),
            Change.RecipeVersionSaved(
                dish,
                RecipeVersion(
                    D1, "13".bd,
                    listOf(
                        RecipeLine(ComponentRef.IngredientRef(rice), "1.5".bd, Uom.KILOGRAM),
                        RecipeLine(ComponentRef.IngredientRef(rice), null, Uom.GRAM, QtyBasis.AS_PURCHASED),
                    ),
                    listOf(RecipeLine(ComponentRef.RecipeRef(RecipeId("chelow")), "300".bd, Uom.GRAM)),
                    "4".bd, Uom.KILOGRAM, "2.5".bd, "یادداشت\nدو خط",
                    listOf(ExtraCost("ظرف و کیسه", 150_000, takeawayOnly = true), ExtraCost("دستمزد", 200_000)),
                ),
            ),
            Change.RecipeVersionRemoved(dish, D1),
            Change.MenuPriceSet(dish, D1, 13_500_000),
            Change.MenuPriceRemoved(dish, D1),
            Change.SettingsSaved(
                CostingRules("3".bd, mapOf("drinks" to "0".bd)),
                PricingSettings(
                    methodByCategory = mapOf("drinks" to PricingMethod.Markup("150".bd)),
                    overhead = Overhead(600_000_000, "4000".bd),
                    minimumGrossProfitRials = 500_000,
                    online = OnlineTerms("20".bd, "10".bd, "15".bd),
                ),
            ),
            Change.SettingsSaved(pricing = PricingSettings()),
            Change.PeriodClosed(
                D31,
                MenuSnapshot(D31, listOf(MenuSnapshot.Item(dish, "کباب", 4_224_082, 13_500_000, 13_300_000, "34.42".bd, PriceStatus.OK))),
            ),
            Change.PeriodClosed(D31, null),
            Change.PeriodReopened(D1),
        )
        changes.forEachIndexed { i, change ->
            val op = Operation(OpId("op$i"), Hlc(1_000L + i, i, "device-1"), Actor("u", Role.MANAGER), change, if (i % 2 == 0) "دلیل $i" else null)
            val back = OperationCodec.decode(Json.parse(Json.write(OperationCodec.encode(op))) as JsonObject)
            assertEquals(op, back, "change $i")
        }
    }

    @Test
    fun `an update writes only the fields it changes`() {
        val op = Operation(OpId("x"), Hlc(1, 0, "A"), Actor("u", Role.COOK), Change.IngredientSaved(rice, name = Opt.Value("برنج")))
        val data = OperationCodec.encode(op).obj("data")
        assertEquals(setOf("id", "name"), data.fields.keys)
        assertFalse(data.has("category"))
    }

    @Test
    fun `a file that is not a supported backup is refused`() {
        assertFailsWith<JsonException> { Backup.read("""{"format":"other","version":1,"operations":[]}""") }
        assertFailsWith<JsonException> { Backup.read("""{"format":"foodcost-backup","version":99,"operations":[]}""") }
        assertFailsWith<JsonException> { Backup.read("""{"format":"foodcost-backup","version":1,"operations":[{"id":"x"}]}""") }
        assertEquals(emptyList(), Backup.read("""{"format":"foodcost-backup","version":1,"operations":[]}"""))
    }

    @Test
    fun `a value the core refuses makes the file count as damaged`() {
        val zeroYield = Operation(
            OpId("y"), Hlc(1, 0, "A"), Actor("u", Role.OWNER),
            Change.IngredientSaved(rice, yields = Opt.Value(listOf(YieldStage("شستن", "0.5".bd)))),
        )
        val written = Backup.write(listOf(zeroYield))
        val text = written.replace(Regex("(\"ratio\"\\s*:\\s*)0\\.5")) { it.groupValues[1] + "0" }
        assertTrue(text != written)
        val error = assertFailsWith<JsonException> { Backup.read(text) }
        assertTrue("y" in error.message.orEmpty())
    }

    @Test
    fun `versions and settings saved before other costs and online terms still read`() {
        val version = OperationCodec.decode(
            Json.parse(
                """{"id":"o1","at":{"wall":1,"counter":0,"device":"d"},"actor":{"id":"u","role":"OWNER"},"type":"RecipeVersionSaved",""" +
                    """"data":{"recipeId":"dish","version":{"validFrom":"2026-10-01","portions":1,"batchLines":[],"portionLines":[],"note":""}}}""",
            ) as JsonObject,
        )
        assertEquals(emptyList(), (version.change as Change.RecipeVersionSaved).version.extras)
    }
}
