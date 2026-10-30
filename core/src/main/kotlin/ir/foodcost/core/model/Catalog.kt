package ir.foodcost.core.model

import java.time.LocalDate

/**
 * Everything one restaurant has entered: ingredients, purchase prices, recipes and menu prices.
 * Immutable; the `with…` functions return a changed copy.
 */
class Catalog(
    ingredients: Collection<Ingredient> = emptyList(),
    prices: Collection<PurchasePrice> = emptyList(),
    recipes: Collection<Recipe> = emptyList(),
    menuPrices: Collection<MenuPrice> = emptyList(),
) {
    val ingredients: Map<IngredientId, Ingredient> = ingredients.uniqueBy("ingredient") { it.id }
    val recipes: Map<RecipeId, Recipe> = recipes.uniqueBy("recipe") { it.id }
    val prices: List<PurchasePrice> = prices.toList()
    val menuPrices: List<MenuPrice> = menuPrices.toList()

    // Stable sorts: of two prices on the same day, the one entered later wins.
    private val pricesByIngredient = this.prices.sortedBy { it.effectiveFrom }.groupBy { it.ingredientId }
    private val menuPricesByRecipe = this.menuPrices.sortedBy { it.effectiveFrom }.groupBy { it.recipeId }

    fun ingredient(id: IngredientId): Ingredient? = ingredients[id]

    fun recipe(id: RecipeId): Recipe? = recipes[id]

    /** All purchase prices of an ingredient, oldest first. */
    fun priceHistory(id: IngredientId): List<PurchasePrice> = pricesByIngredient[id].orEmpty()

    /** The purchase price in force on [date]. */
    fun priceAt(id: IngredientId, date: LocalDate): PurchasePrice? =
        priceHistory(id).lastOrNull { !it.effectiveFrom.isAfter(date) }

    /** The menu price in force on [date]. */
    fun menuPriceAt(id: RecipeId, date: LocalDate): MenuPrice? =
        menuPricesByRecipe[id].orEmpty().lastOrNull { !it.effectiveFrom.isAfter(date) }

    /** Recipes that use [component] on [date], directly or through other recipes. */
    fun usersOf(component: ComponentRef, date: LocalDate): Set<RecipeId> {
        val directUsers = HashMap<ComponentRef, MutableSet<RecipeId>>()
        for (recipe in recipes.values) {
            val version = recipe.versionAt(date) ?: continue
            for (line in version.lines) directUsers.getOrPut(line.component) { linkedSetOf() } += recipe.id
        }
        val result = linkedSetOf<RecipeId>()
        val queue = ArrayDeque(directUsers[component].orEmpty())
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (result.add(id)) queue.addAll(directUsers[ComponentRef.RecipeRef(id)].orEmpty())
        }
        return result
    }

    fun withIngredients(vararg items: Ingredient): Catalog =
        Catalog(replaceById(ingredients, items.toList()) { it.id }, prices, recipes.values, menuPrices)

    fun withPrices(vararg items: PurchasePrice): Catalog =
        Catalog(ingredients.values, prices + items, recipes.values, menuPrices)

    fun withRecipes(vararg items: Recipe): Catalog =
        Catalog(ingredients.values, prices, replaceById(recipes, items.toList()) { it.id }, menuPrices)

    fun withMenuPrices(vararg items: MenuPrice): Catalog =
        Catalog(ingredients.values, prices, recipes.values, menuPrices + items)

    private companion object {
        fun <K, V> Collection<V>.uniqueBy(what: String, key: (V) -> K): Map<K, V> {
            val map = LinkedHashMap<K, V>()
            for (value in this) {
                val k = key(value)
                require(map.put(k, value) == null) { "Duplicate $what id: $k" }
            }
            return map
        }

        fun <K, V> replaceById(current: Map<K, V>, items: List<V>, key: (V) -> K): Collection<V> {
            val map = LinkedHashMap(current)
            for (item in items) map[key(item)] = item
            return map.values
        }
    }
}
