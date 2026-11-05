// Makes the data fixtures of Legacy074ReadTest and Legacy074RowsTest with the code of 0.7.4 itself.
// It is not part of the build: compile it against the core and data modules of commit ca6ae78
// (git worktree add /tmp/v074 ca6ae78) and run it with the repository's root as the argument:
//
//   kotlinc -d core.jar /tmp/v074/core/src/main/kotlin
//   kotlinc -cp core.jar -d data.jar /tmp/v074/data/src/main/kotlin
//   kotlinc -cp core.jar:data.jar -d gen.jar scripts/legacy/Generate074.kt
//   java -cp kotlin-stdlib.jar:core.jar:data.jar:gen.jar Generate074Kt .
//
// A kitchen as a phone on 0.7.4 would have it: dated yields and settings, prices on several days,
// a preparation inside two dishes, menu prices, the dish screen's header saves (name, kind and
// section, from that day), a dish made a preparation, then October closed, and a price after it.
// Ids are random, so the files differ each time it runs; the figures are what 0.7.4 worked out.

import ir.foodcost.core.costing.CostingRules
import ir.foodcost.core.model.ComponentRef
import ir.foodcost.core.model.PurchasePrice
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.model.RecipeLine
import ir.foodcost.core.model.RecipeVersion
import ir.foodcost.core.model.YieldStage
import ir.foodcost.core.reports.MenuSnapshot
import ir.foodcost.core.units.Dimension
import ir.foodcost.core.units.Uom
import ir.foodcost.data.clock.HybridClock
import ir.foodcost.data.json.Json
import ir.foodcost.data.ledger.Ledger
import ir.foodcost.data.ops.Actor
import ir.foodcost.data.ops.OperationCodec
import ir.foodcost.data.ops.Opt
import ir.foodcost.data.ops.Role
import ir.foodcost.data.store.InMemoryOperationStore
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

private val D1 = LocalDate.of(2026, 10, 1)
private val D15 = LocalDate.of(2026, 10, 15)
private val D31 = LocalDate.of(2026, 10, 31)
private val N2 = LocalDate.of(2026, 11, 2)
private val N10 = LocalDate.of(2026, 11, 10)
private val DAYS = listOf(D1, D15, D31, N2, N10)

private fun Ledger.Outcome.ok() = check(this is Ledger.Outcome.Applied) { "refused: $this" }

private fun line(id: ir.foodcost.core.model.IngredientId, grams: String) = RecipeLine(ComponentRef.IngredientRef(id), BigDecimal(grams), Uom.GRAM)

fun main(args: Array<String>) {
    val root = File(args.firstOrNull() ?: ".")
    var now = 1_790_000_000_000L
    val ledger = Ledger(InMemoryOperationStore(), HybridClock("phone") { now += 60_000; now }, Actor("owner", Role.OWNER))

    val meat = ledger.createIngredient("گوشت گوسفندی", Dimension.MASS, yields = listOf(YieldStage("پاک کردن", BigDecimal("0.9"))))
    val rice = ledger.createIngredient("برنج طارم", Dimension.MASS, yields = listOf(YieldStage("شستن", BigDecimal("0.98"))))
    val onion = ledger.createIngredient("پیاز", Dimension.MASS)
    ledger.recordPrice(PurchasePrice(meat, D1, BigDecimal.ONE, Uom.KILOGRAM, 16_000_000)).ok()
    ledger.recordPrice(PurchasePrice(rice, D1, BigDecimal("10"), Uom.KILOGRAM, 46_000_000)).ok()
    ledger.recordPrice(PurchasePrice(onion, D1, BigDecimal.ONE, Uom.KILOGRAM, 800_000)).ok()
    ledger.recordPrice(PurchasePrice(meat, D15, BigDecimal.ONE, Uom.KILOGRAM, 18_000_000)).ok()

    val chelow = ledger.createRecipe("چلو", RecipeKind.PREP)
    val kebab = ledger.createRecipe("چلوکباب", RecipeKind.MENU_ITEM, category = "main")
    val stew = ledger.createRecipe("خورش", RecipeKind.MENU_ITEM, category = "main")
    val salad = ledger.createRecipe("سالاد شیرازی", RecipeKind.MENU_ITEM)
    ledger.saveRecipeVersion(chelow, RecipeVersion(D1, batchLines = listOf(line(rice, "150")), miscPercent = BigDecimal.ZERO)).ok()
    ledger.saveRecipeVersion(
        kebab,
        RecipeVersion(D1, batchLines = listOf(line(meat, "220"), line(onion, "30"), RecipeLine(ComponentRef.RecipeRef(chelow), BigDecimal.ONE, Uom.PORTION))),
    ).ok()
    ledger.saveRecipeVersion(stew, RecipeVersion(D1, batchLines = listOf(line(meat, "120"), line(onion, "60"), RecipeLine(ComponentRef.RecipeRef(chelow), BigDecimal.ONE, Uom.PORTION)))).ok()
    ledger.saveRecipeVersion(salad, RecipeVersion(D1, batchLines = listOf(line(onion, "100")), miscPercent = BigDecimal.ZERO)).ok()
    ledger.setMenuPrice(kebab, D1, 13_500_000).ok()
    ledger.setMenuPrice(stew, D1, 9_000_000).ok()
    ledger.setMenuPrice(salad, D1, 2_500_000).ok()

    // Dated settings and a dated yield, as the app saves them.
    ledger.saveSettings(costing = CostingRules(BigDecimal("4")), effectiveFrom = D15).ok()
    ledger.updateIngredient(meat, yields = Opt.Value(listOf(YieldStage("پاک کردن", BigDecimal("0.85")))), effectiveFrom = N2).ok()
    ledger.recordPrice(PurchasePrice(onion, N2, BigDecimal.ONE, Uom.KILOGRAM, 900_000)).ok()
    ledger.setMenuPrice(kebab, N2, 14_500_000).ok()

    // The dish screen's header saves of 0.7.4: name, kind and section, with that day. The salad
    // made a preparation: 0.7.4 counted the kind on every day, the days before too.
    ledger.updateRecipe(stew, name = Opt.Value("خورش قیمه"), kind = Opt.Value(RecipeKind.MENU_ITEM), category = Opt.Value("main"), effectiveFrom = N2).ok()
    ledger.updateRecipe(salad, name = Opt.Value("سالاد شیرازی"), kind = Opt.Value(RecipeKind.PREP), category = Opt.Value(null), effectiveFrom = N2).ok()

    // October closed after all of it, then a price for a later day.
    ledger.closePeriod(D31).ok()
    ledger.recordPrice(PurchasePrice(meat, N10, BigDecimal.ONE, Uom.KILOGRAM, 20_000_000)).ok()

    val operations = ledger.operations().map { Json.write(OperationCodec.encode(it)) }
    check(operations.none { "\"\"\"" in it || "$" in it }) { "a line that does not fit in a raw string" }
    val calculator = ledger.calculator()
    val figures = DAYS.associateWith { day ->
        MenuSnapshot.of(calculator.menu(day)).items.sortedBy { it.recipeId.value }.map {
            "${it.recipeId.value}|${it.name}|${it.foodCostRials}|${it.menuPriceRials}|${it.suggestedPriceRials}|${it.foodCostPercent?.stripTrailingZeros()?.toPlainString()}|${it.status}"
        }
    }

    fun source(pkg: String, name: String) = buildString {
        appendLine("package $pkg")
        appendLine()
        appendLine("/**")
        appendLine(" * Made by scripts/legacy/Generate074.kt with the code of 0.7.4 itself (commit ca6ae78), not by")
        appendLine(" * hand: the operations of a kitchen as 0.7.4 stored them, one JSON text each, in the order it")
        appendLine(" * stored them, and the menu figures 0.7.4 worked out from them for each day. Do not edit.")
        appendLine(" */")
        appendLine("object $name {")
        appendLine("    /** The period 0.7.4 closed, through this day. */")
        appendLine("    const val CLOSED_THROUGH = \"$D31\"")
        appendLine()
        appendLine("    val operations: List<String> = listOf(")
        operations.forEach { appendLine("        \"\"\"$it\"\"\",") }
        appendLine("    )")
        appendLine()
        appendLine("    /** For each day, each dish of the menu: id|name|cost|menu price|suggested price|food cost %|status. */")
        appendLine("    val figures: Map<String, List<String>> = mapOf(")
        figures.forEach { (day, items) ->
            appendLine("        \"$day\" to listOf(")
            items.forEach { appendLine("            \"$it\",") }
            appendLine("        ),")
        }
        appendLine("    )")
        appendLine("}")
    }
    File(root, "data/src/test/kotlin/ir/foodcost/data/Legacy074.kt").writeText(source("ir.foodcost.data", "Legacy074"))
    File(root, "app/src/androidTest/kotlin/ir/foodcost/app/data/Legacy074Rows.kt").writeText(source("ir.foodcost.app.data", "Legacy074Rows"))
    println("${operations.size} operations; figures for ${figures.keys.joinToString()}")
    figures.forEach { (day, items) -> println("$day: ${items.joinToString("; ")}") }
}
