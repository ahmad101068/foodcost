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
import ir.foodcost.data.ops.Seen
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
            Change.IngredientSaved(rice, yields = Opt.Value(listOf(YieldStage("شستن", "0.9".bd))), effectiveFrom = D31),
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
            Change.RecipeSaved(dish, category = Opt.Value("drinks"), effectiveFrom = D31),
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
            Change.SettingsSaved(CostingRules("9".bd), effectiveFrom = D31),
            Change.PeriodClosed(
                D31,
                MenuSnapshot(D31, listOf(MenuSnapshot.Item(dish, "کباب", 4_224_082, 13_500_000, 13_300_000, "34.42".bd, PriceStatus.OK))),
            ),
            Change.PeriodClosed(D31, null),
            Change.PeriodClosed(
                D31,
                null,
                seen = mapOf(
                    "device-1" to Seen(Hlc(1_000, 3, "device-1"), seq = 12, missing = listOf(4, 9)),
                    "phone 2" to Seen(Hlc(900, 0, "phone 2")),
                ),
            ),
            Change.PeriodReopened(D1),
        )
        changes.forEachIndexed { i, change ->
            val op = Operation(
                OpId("op$i"), Hlc(1_000L + i, i, "device-1"), Actor("u", Role.MANAGER), change,
                if (i % 2 == 0) "دلیل $i" else null,
                seq = if (i % 3 == 0) null else i + 1L, // changes made before they were numbered have none
            )
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
        assertFailsWith<JsonException> { Backup.read("""{"format":"foodcost-backup","version":5,"operations":[]}""") }
    }

    @Test
    fun `a backup with a close that says what it had seen is version 4, which the versions before refuse rather than misread`() {
        val close = Operation(
            OpId("c"), Hlc(5, 0, "A"), Actor("u", Role.OWNER),
            Change.PeriodClosed(D31, null, seen = mapOf("A" to Seen(Hlc(4, 0, "A"), seq = 3, missing = listOf(2)))),
            seq = 4,
        )
        val written = Backup.write(listOf(close))
        assertEquals(4, (Json.parse(written) as JsonObject).int("version"))
        assertEquals(listOf(close), Backup.read(written))

        // A close of 0.7.1 kept only the latest clock of each device: it reads as having none of
        // their numbered changes, and every change before that clock that has no number.
        val older = Backup.read(
            """{"format":"foodcost-backup","version":3,"operations":[{"id":"c","at":{"wall":5,"counter":0,"device":"A"},""" +
                """"actor":{"id":"u","role":"OWNER"},"type":"PeriodClosed","data":{"through":"2026-10-31","seen":{"A":{"wall":4,"counter":0}}}}]}""",
        )
        assertEquals(mapOf("A" to Seen(Hlc(4, 0, "A"))), (older.single().change as Change.PeriodClosed).seen)
        // A missing number above the highest one is damage, and the file is refused.
        assertFailsWith<JsonException> {
            Backup.read(written.replace(Regex("\"missing\"\\s*:\\s*\\[2]"), "\"missing\":[7]"))
        }
        // Without such a close a file stays version 2, which 0.7.0 opens.
        assertEquals(2, (Json.parse(Backup.write(emptyList())) as JsonObject).int("version"))
    }

    @Test
    fun `a backup says it has dated changes, so a version of the app that cannot read the day refuses it`() {
        val dated = Operation(
            OpId("d"), Hlc(1, 0, "A"), Actor("u", Role.OWNER),
            Change.IngredientSaved(rice, yields = Opt.Value(listOf(YieldStage("پاک کردن", "0.8".bd))), effectiveFrom = D1),
        )
        val written = Backup.write(listOf(dated))
        val root = Json.parse(written) as JsonObject
        assertEquals(2, root.int("version")) // the versions before read only 1
        assertEquals(listOf(dated), Backup.read(written))

        // A file of version 1 is read as before: its changes count from no day in particular.
        val old = written.replace(Regex("\"version\"\\s*:\\s*2"), "\"version\":1").replace(Regex(",?\"effectiveFrom\"\\s*:\\s*\"[^\"]*\""), "")
        assertTrue(old != written)
        assertEquals(null, (Backup.read(old).single().change as Change.IngredientSaved).effectiveFrom)
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
