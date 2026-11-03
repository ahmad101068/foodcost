package ir.foodcost.presentation

import ir.foodcost.core.model.IngredientId
import ir.foodcost.core.model.RecipeId
import ir.foodcost.core.money.DisplayCurrency
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
 * Everything is kept as typed, with the currency the amounts were typed in; a draft that cannot be
 * read is dropped rather than half restored.
 */
object QuickDrafts {
    private const val VERSION = 1

    fun encode(input: QuickInput, currency: DisplayCurrency): String = Json.write(
        jsonObject(
            "v" to VERSION.json,
            "currency" to currency.name.json,
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

    /**
     * The draft in [text] with its amounts in [currency], or null when it is not one this version
     * can read. A draft typed in another currency has its amounts converted; one written before
     * drafts kept their currency is taken as typed in [currency].
     */
    fun decode(text: String, currency: DisplayCurrency): QuickInput? = try {
        val o = Json.parse(text).asObject("draft")
        if (o.int("v") != VERSION) {
            null
        } else {
            val typedIn = o.stringOrNull("currency")?.let { name -> DisplayCurrency.entries.firstOrNull { it.name == name } }
            convert(read(o), typedIn ?: currency, currency)
        }
    } catch (e: JsonException) {
        null
    } catch (e: ArithmeticException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    /**
     * The same calculation with its amounts in [to] instead of [from]: the prices, the menu price
     * and the other costs, so a change of currency leaves every figure as it was. Anything that does
     * not read as an amount is left as typed.
     */
    fun convert(input: QuickInput, from: DisplayCurrency, to: DisplayCurrency): QuickInput {
        if (from == to) return input
        val typed = MoneyFormat(from)
        val shown = MoneyFormat(to)
        fun amount(text: String): String = typed.parse(text)?.let { shown.editable(it) } ?: text
        return input.copy(
            rows = input.rows.map { it.copy(price = amount(it.price)) },
            menuPrice = amount(input.menuPrice),
            extras = input.extras.map { it.copy(amount = amount(it.amount)) },
        )
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
