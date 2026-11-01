package ir.foodcost.presentation

import ir.foodcost.core.model.IngredientId
import ir.foodcost.core.model.RecipeId
import ir.foodcost.core.units.Uom
import ir.foodcost.data.json.Json
import ir.foodcost.data.json.JsonException
import ir.foodcost.data.json.JsonObject
import ir.foodcost.data.json.asObject
import ir.foodcost.data.json.json
import ir.foodcost.data.json.jsonArray
import ir.foodcost.data.json.jsonObject

/**
 * The calculator's calculation as text, so it is still there after the phone closes the app.
 * Everything is kept as typed; a draft that cannot be read is dropped rather than half restored.
 */
object QuickDrafts {
    private const val VERSION = 1

    fun encode(input: QuickInput): String = Json.write(
        jsonObject(
            "v" to VERSION.json,
            "dishName" to input.dishName.json,
            "rows" to jsonArray(
                input.rows.map { row ->
                    jsonObject(
                        "name" to row.name.json,
                        "price" to row.price.json,
                        "qty" to row.qty.json,
                        "uom" to row.uom.name.json,
                        "ingredientId" to row.ingredientId?.value?.json,
                    )
                },
            ),
            "portions" to input.portions.json,
            "menuPrice" to input.menuPrice.json,
            "recipeId" to input.recipeId?.value?.json,
            "miscPercent" to input.miscPercent?.json,
            "extras" to jsonArray(
                input.extras.map { extra ->
                    jsonObject("name" to extra.name.json, "amount" to extra.amount.json, "takeawayOnly" to extra.takeawayOnly.json)
                },
            ),
            "category" to input.category?.json,
        ),
    )

    /** The draft in [text], or null when it is not one this version can read. */
    fun decode(text: String): QuickInput? = try {
        val o = Json.parse(text).asObject("draft")
        if (o.int("v") != VERSION) null else read(o)
    } catch (e: JsonException) {
        null
    } catch (e: ArithmeticException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun read(o: JsonObject): QuickInput {
        val rows = o.array("rows").map { item ->
            val r = item.asObject("row")
            QuickRow(
                name = r.string("name"),
                price = r.string("price"),
                qty = r.string("qty"),
                uom = Uom.entries.firstOrNull { it.name == r.string("uom") } ?: Uom.GRAM,
                ingredientId = r.stringOrNull("ingredientId")?.let { IngredientId(it) },
            )
        }
        val extras = o.array("extras").map { item ->
            val e = item.asObject("extra")
            QuickExtra(e.string("name"), e.string("amount"), e.bool("takeawayOnly"))
        }
        return QuickInput(
            dishName = o.string("dishName"),
            // The calculator always ends with an empty row to type into.
            rows = if (rows.lastOrNull()?.isBlank == true) rows else rows + QuickRow(),
            portions = o.string("portions"),
            menuPrice = o.string("menuPrice"),
            recipeId = o.stringOrNull("recipeId")?.let { RecipeId(it) },
            miscPercent = o.decimalOrNull("miscPercent"),
            extras = extras,
            category = o.stringOrNull("category"),
        )
    }
}
