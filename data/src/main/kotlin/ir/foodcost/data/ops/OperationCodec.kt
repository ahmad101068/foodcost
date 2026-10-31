package ir.foodcost.data.ops

import ir.foodcost.core.costing.CostingRules
import ir.foodcost.core.model.ComponentRef
import ir.foodcost.core.model.IngredientId
import ir.foodcost.core.model.PurchasePrice
import ir.foodcost.core.model.QtyBasis
import ir.foodcost.core.model.RecipeId
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.model.RecipeLine
import ir.foodcost.core.model.RecipeVersion
import ir.foodcost.core.model.YieldStage
import ir.foodcost.core.money.PriceRounding
import ir.foodcost.core.pricing.CostBasis
import ir.foodcost.core.pricing.Overhead
import ir.foodcost.core.pricing.PriceStatus
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.core.pricing.PricingSettings
import ir.foodcost.core.reports.MenuSnapshot
import ir.foodcost.core.units.Conversions
import ir.foodcost.core.units.Dimension
import ir.foodcost.core.units.Uom
import ir.foodcost.data.clock.Hlc
import ir.foodcost.data.json.Json
import ir.foodcost.data.json.JsonException
import ir.foodcost.data.json.JsonNull
import ir.foodcost.data.json.JsonObject
import ir.foodcost.data.json.JsonValue
import ir.foodcost.data.json.asArray
import ir.foodcost.data.json.asBool
import ir.foodcost.data.json.asDecimal
import ir.foodcost.data.json.asObject
import ir.foodcost.data.json.asString
import ir.foodcost.data.json.json
import ir.foodcost.data.json.jsonArray
import ir.foodcost.data.json.jsonObject
import java.time.LocalDate

/**
 * Writes operations as JSON and reads them back, field by field. The format is written out by
 * hand on purpose: it is stored for years and shared with the server, so it must not shift when
 * a class is renamed.
 */
object OperationCodec {
    fun encode(op: Operation): JsonObject = jsonObject(
        "id" to op.id.value.json,
        "at" to jsonObject("wall" to op.at.wallMillis.json, "counter" to op.at.counter.json, "device" to op.at.device.json),
        "actor" to jsonObject("id" to op.actor.id.json, "role" to op.actor.role.name.json),
        "reason" to op.reason?.json,
        "type" to typeOf(op.change).json,
        "data" to encodeChange(op.change),
    )

    /** Reads one operation. A value the core refuses, such as a yield of zero, is reported as damaged JSON too. */
    fun decode(o: JsonObject): Operation {
        val id = o.string("id")
        val at = o.obj("at")
        val actor = o.obj("actor")
        return try {
            Operation(
                id = OpId(id),
                at = Hlc(at.long("wall"), at.int("counter"), at.string("device")),
                actor = Actor(actor.string("id"), enumOf<Role>(actor.string("role"))),
                change = decodeChange(o.string("type"), o.obj("data")),
                reason = o.stringOrNull("reason"),
            )
        } catch (e: JsonException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw JsonException("Operation $id: ${e.message}")
        }
    }

    private fun typeOf(c: Change): String = when (c) {
        is Change.IngredientSaved -> "IngredientSaved"
        is Change.PriceRecorded -> "PriceRecorded"
        is Change.PriceVoided -> "PriceVoided"
        is Change.RecipeSaved -> "RecipeSaved"
        is Change.RecipeVersionSaved -> "RecipeVersionSaved"
        is Change.RecipeVersionRemoved -> "RecipeVersionRemoved"
        is Change.MenuPriceSet -> "MenuPriceSet"
        is Change.MenuPriceRemoved -> "MenuPriceRemoved"
        is Change.SettingsSaved -> "SettingsSaved"
        is Change.PeriodClosed -> "PeriodClosed"
        is Change.PeriodReopened -> "PeriodReopened"
    }

    private fun encodeChange(c: Change): JsonObject = when (c) {
        is Change.IngredientSaved -> fields("id" to c.id.value.json) {
            opt("name", c.name) { it.json }
            opt("baseDimension", c.baseDimension) { it.name.json }
            opt("category", c.category) { it?.json ?: JsonNull }
            opt("yields", c.yields) { list -> jsonArray(list.map { jsonObject("name" to it.name.json, "ratio" to it.ratio.json) }) }
            opt("conversions", c.conversions) { encodeConversions(it) }
            opt("archived", c.archived) { it.json }
        }
        is Change.PriceRecorded -> jsonObject("priceId" to c.priceId.json, "price" to encodePrice(c.price))
        is Change.PriceVoided -> jsonObject("priceId" to c.priceId.json)
        is Change.RecipeSaved -> fields("id" to c.id.value.json) {
            opt("name", c.name) { it.json }
            opt("kind", c.kind) { it.name.json }
            opt("category", c.category) { it?.json ?: JsonNull }
            opt("pricing", c.pricing) { it?.let(::encodeMethod) ?: JsonNull }
            opt("archived", c.archived) { it.json }
        }
        is Change.RecipeVersionSaved -> jsonObject("recipeId" to c.recipeId.value.json, "version" to encodeVersion(c.version))
        is Change.RecipeVersionRemoved -> jsonObject("recipeId" to c.recipeId.value.json, "validFrom" to c.validFrom.toString().json)
        is Change.MenuPriceSet -> jsonObject(
            "recipeId" to c.recipeId.value.json,
            "effectiveFrom" to c.effectiveFrom.toString().json,
            "rials" to c.rials.json,
        )
        is Change.MenuPriceRemoved -> jsonObject(
            "recipeId" to c.recipeId.value.json,
            "effectiveFrom" to c.effectiveFrom.toString().json,
        )
        is Change.SettingsSaved -> jsonObject(
            "costing" to c.costing?.let(::encodeCosting),
            "pricing" to c.pricing?.let(::encodePricing),
        )
        is Change.PeriodClosed -> jsonObject("through" to c.through.toString().json, "snapshot" to c.snapshot?.let(::encodeSnapshot))
        is Change.PeriodReopened -> jsonObject("from" to c.from.toString().json)
    }

    private fun decodeChange(type: String, d: JsonObject): Change = when (type) {
        "IngredientSaved" -> Change.IngredientSaved(
            id = IngredientId(d.string("id")),
            name = d.opt("name") { it.asString("name") },
            baseDimension = d.opt("baseDimension") { enumOf<Dimension>(it.asString("baseDimension")) },
            category = d.opt("category") { if (it == JsonNull) null else it.asString("category") },
            yields = d.opt("yields") { v ->
                v.asArray("yields").map {
                    val y = it.asObject("yield")
                    YieldStage(y.string("name"), y.decimal("ratio"))
                }
            },
            conversions = d.opt("conversions") { decodeConversions(it.asObject("conversions")) },
            archived = d.opt("archived") { it.asBool("archived") },
        )
        "PriceRecorded" -> Change.PriceRecorded(d.string("priceId"), decodePrice(d.obj("price")))
        "PriceVoided" -> Change.PriceVoided(d.string("priceId"))
        "RecipeSaved" -> Change.RecipeSaved(
            id = RecipeId(d.string("id")),
            name = d.opt("name") { it.asString("name") },
            kind = d.opt("kind") { enumOf<RecipeKind>(it.asString("kind")) },
            category = d.opt("category") { if (it == JsonNull) null else it.asString("category") },
            pricing = d.opt("pricing") { if (it == JsonNull) null else decodeMethod(it.asObject("pricing")) },
            archived = d.opt("archived") { it.asBool("archived") },
        )
        "RecipeVersionSaved" -> Change.RecipeVersionSaved(RecipeId(d.string("recipeId")), decodeVersion(d.obj("version")))
        "RecipeVersionRemoved" -> Change.RecipeVersionRemoved(RecipeId(d.string("recipeId")), date(d.string("validFrom")))
        "MenuPriceSet" -> Change.MenuPriceSet(RecipeId(d.string("recipeId")), date(d.string("effectiveFrom")), d.long("rials"))
        "MenuPriceRemoved" -> Change.MenuPriceRemoved(RecipeId(d.string("recipeId")), date(d.string("effectiveFrom")))
        "SettingsSaved" -> Change.SettingsSaved(
            costing = if (d.has("costing")) decodeCosting(d.obj("costing")) else null,
            pricing = if (d.has("pricing")) decodePricing(d.obj("pricing")) else null,
        )
        "PeriodClosed" -> Change.PeriodClosed(
            date(d.string("through")),
            if (d.has("snapshot")) decodeSnapshot(d.obj("snapshot")) else null,
        )
        "PeriodReopened" -> Change.PeriodReopened(date(d.string("from")))
        else -> throw JsonException("Unknown operation type \"$type\"")
    }

    private fun encodeConversions(c: Conversions): JsonObject = jsonObject(
        "gramsPerPiece" to c.gramsPerPiece?.json,
        "gramsPerMilliliter" to c.gramsPerMilliliter?.json,
    )

    private fun decodeConversions(o: JsonObject) = Conversions(o.decimalOrNull("gramsPerPiece"), o.decimalOrNull("gramsPerMilliliter"))

    private fun encodePrice(p: PurchasePrice): JsonObject = jsonObject(
        "ingredientId" to p.ingredientId.value.json,
        "effectiveFrom" to p.effectiveFrom.toString().json,
        "packageQty" to p.packageQty.json,
        "packageUom" to p.packageUom.name.json,
        "grossRials" to p.grossRials.json,
        "discountRials" to p.discountRials.json,
        "taxRials" to p.taxRials.json,
        "taxRecoverable" to p.taxRecoverable.json,
        "deliveryRials" to p.deliveryRials.json,
        "supplier" to p.supplier?.json,
    )

    private fun decodePrice(o: JsonObject) = PurchasePrice(
        ingredientId = IngredientId(o.string("ingredientId")),
        effectiveFrom = date(o.string("effectiveFrom")),
        packageQty = o.decimal("packageQty"),
        packageUom = enumOf<Uom>(o.string("packageUom")),
        grossRials = o.long("grossRials"),
        discountRials = o.long("discountRials"),
        taxRials = o.long("taxRials"),
        taxRecoverable = o.bool("taxRecoverable"),
        deliveryRials = o.long("deliveryRials"),
        supplier = o.stringOrNull("supplier"),
    )

    private fun encodeLine(l: RecipeLine): JsonObject = jsonObject(
        when (val c = l.component) {
            is ComponentRef.IngredientRef -> "ingredient" to c.id.value.json
            is ComponentRef.RecipeRef -> "recipe" to c.id.value.json
        },
        "qty" to l.qty?.json,
        "uom" to l.uom.name.json,
        "basis" to l.basis.name.json,
    )

    private fun decodeLine(o: JsonObject) = RecipeLine(
        component = when {
            o.has("ingredient") -> ComponentRef.IngredientRef(IngredientId(o.string("ingredient")))
            o.has("recipe") -> ComponentRef.RecipeRef(RecipeId(o.string("recipe")))
            else -> throw JsonException("A recipe line needs \"ingredient\" or \"recipe\"")
        },
        qty = o.decimalOrNull("qty"),
        uom = enumOf<Uom>(o.string("uom")),
        basis = enumOf<QtyBasis>(o.string("basis")),
    )

    private fun encodeVersion(v: RecipeVersion): JsonObject = jsonObject(
        "validFrom" to v.validFrom.toString().json,
        "portions" to v.portions.json,
        "batchLines" to jsonArray(v.batchLines.map(::encodeLine)),
        "portionLines" to jsonArray(v.portionLines.map(::encodeLine)),
        "outputQty" to v.outputQty?.json,
        "outputUom" to v.outputUom?.name?.json,
        "miscPercent" to v.miscPercent?.json,
        "note" to v.note.json,
    )

    private fun decodeVersion(o: JsonObject) = RecipeVersion(
        validFrom = date(o.string("validFrom")),
        portions = o.decimal("portions"),
        batchLines = o.array("batchLines").map { decodeLine(it.asObject("line")) },
        portionLines = o.array("portionLines").map { decodeLine(it.asObject("line")) },
        outputQty = o.decimalOrNull("outputQty"),
        outputUom = o.stringOrNull("outputUom")?.let { enumOf<Uom>(it) },
        miscPercent = o.decimalOrNull("miscPercent"),
        note = o.string("note"),
    )

    private fun encodeMethod(m: PricingMethod): JsonObject = when (m) {
        is PricingMethod.TargetFoodCost -> jsonObject("method" to "TargetFoodCost".json, "percent" to m.percent.json)
        is PricingMethod.TargetMargin -> jsonObject(
            "method" to "TargetMargin".json, "percent" to m.percent.json, "basis" to m.basis.name.json,
        )
        is PricingMethod.Markup -> jsonObject("method" to "Markup".json, "percent" to m.percent.json, "basis" to m.basis.name.json)
    }

    private fun decodeMethod(o: JsonObject): PricingMethod = when (val method = o.string("method")) {
        "TargetFoodCost" -> PricingMethod.TargetFoodCost(o.decimal("percent"))
        "TargetMargin" -> PricingMethod.TargetMargin(o.decimal("percent"), enumOf<CostBasis>(o.string("basis")))
        "Markup" -> PricingMethod.Markup(o.decimal("percent"), enumOf<CostBasis>(o.string("basis")))
        else -> throw JsonException("Unknown pricing method \"$method\"")
    }

    private fun encodeCosting(c: CostingRules): JsonObject = jsonObject(
        "defaultMiscPercent" to c.defaultMiscPercent.json,
        "miscPercentByCategory" to JsonObject(c.miscPercentByCategory.mapValues { (_, v) -> v.json }),
    )

    private fun decodeCosting(o: JsonObject) = CostingRules(
        o.decimal("defaultMiscPercent"),
        o.obj("miscPercentByCategory").fields.mapValues { (k, v) -> v.asDecimal(k) },
    )

    private fun encodePricing(p: PricingSettings): JsonObject = jsonObject(
        "defaultMethod" to encodeMethod(p.defaultMethod),
        "methodByCategory" to JsonObject(p.methodByCategory.mapValues { (_, m) -> encodeMethod(m) }),
        "vatPercent" to p.vatPercent.json,
        "menuPriceIncludesVat" to p.menuPriceIncludesVat.json,
        "rounding" to jsonObject("stepRials" to p.rounding.stepRials.json, "direction" to p.rounding.direction.name.json),
        "overhead" to p.overhead?.let {
            jsonObject("monthlyFixedRials" to it.monthlyFixedRials.json, "monthlyPortions" to it.monthlyPortions.json)
        },
        "minimumGrossProfitRials" to p.minimumGrossProfitRials?.json,
        "okRatio" to p.okRatio.json,
        "nearRatio" to p.nearRatio.json,
    )

    private fun decodePricing(o: JsonObject): PricingSettings {
        val rounding = o.obj("rounding")
        return PricingSettings(
            defaultMethod = decodeMethod(o.obj("defaultMethod")),
            methodByCategory = o.obj("methodByCategory").fields.mapValues { (k, v) -> decodeMethod(v.asObject(k)) },
            vatPercent = o.decimal("vatPercent"),
            menuPriceIncludesVat = o.bool("menuPriceIncludesVat"),
            rounding = PriceRounding(rounding.long("stepRials"), enumOf<PriceRounding.Direction>(rounding.string("direction"))),
            overhead = if (o.has("overhead")) {
                val oh = o.obj("overhead")
                Overhead(oh.long("monthlyFixedRials"), oh.decimal("monthlyPortions"))
            } else {
                null
            },
            minimumGrossProfitRials = o.longOrNull("minimumGrossProfitRials"),
            okRatio = o.decimal("okRatio"),
            nearRatio = o.decimal("nearRatio"),
        )
    }

    private fun encodeSnapshot(s: MenuSnapshot): JsonObject = jsonObject(
        "date" to s.date.toString().json,
        "items" to jsonArray(
            s.items.map {
                jsonObject(
                    "recipeId" to it.recipeId.value.json,
                    "name" to it.name.json,
                    "foodCostRials" to it.foodCostRials.json,
                    "menuPriceRials" to it.menuPriceRials?.json,
                    "suggestedPriceRials" to it.suggestedPriceRials.json,
                    "foodCostPercent" to it.foodCostPercent?.json,
                    "status" to it.status.name.json,
                )
            },
        ),
    )

    private fun decodeSnapshot(o: JsonObject) = MenuSnapshot(
        date(o.string("date")),
        o.array("items").map {
            val i = it.asObject("item")
            MenuSnapshot.Item(
                RecipeId(i.string("recipeId")),
                i.string("name"),
                i.long("foodCostRials"),
                i.longOrNull("menuPriceRials"),
                i.long("suggestedPriceRials"),
                i.decimalOrNull("foodCostPercent"),
                enumOf<PriceStatus>(i.string("status")),
            )
        },
    )

    private fun date(text: String): LocalDate = try {
        LocalDate.parse(text)
    } catch (e: java.time.format.DateTimeParseException) {
        throw JsonException("\"$text\" is not a date")
    }

    private inline fun <reified E : Enum<E>> enumOf(name: String): E =
        enumValues<E>().firstOrNull { it.name == name } ?: throw JsonException("Unknown ${E::class.simpleName} \"$name\"")

    /** Builds an object whose optional fields are written only when they are set. */
    private class FieldWriter(first: Pair<String, JsonValue>) {
        val fields = linkedMapOf(first)

        fun <T> opt(key: String, value: Opt<T>, encode: (T) -> JsonValue) {
            if (value is Opt.Value) fields[key] = encode(value.value)
        }
    }

    private fun fields(first: Pair<String, JsonValue>, block: FieldWriter.() -> Unit): JsonObject =
        JsonObject(FieldWriter(first).apply(block).fields)

    /** A missing key means "keep"; a present key, even null, means "set". */
    private fun <T> JsonObject.opt(key: String, decode: (JsonValue) -> T): Opt<T> =
        if (has(key)) Opt.Value(decode(get(key)!!)) else Opt.Keep
}

/** A whole backup: every operation, in the order the device stored them. */
object Backup {
    private const val FORMAT = "foodcost-backup"
    private const val VERSION = 1

    fun write(operations: List<Operation>): String = Json.write(
        jsonObject(
            "format" to FORMAT.json,
            "version" to VERSION.json,
            "operations" to jsonArray(operations.map(OperationCodec::encode)),
        ),
    )

    fun read(text: String): List<Operation> {
        val root = Json.parse(text).asObject("backup")
        if (root.stringOrNull("format") != FORMAT) throw JsonException("This file is not a food cost backup")
        val version = root.int("version")
        if (version != VERSION) throw JsonException("Backup version $version is not supported")
        return root.array("operations").map { OperationCodec.decode(it.asObject("operation")) }
    }
}
