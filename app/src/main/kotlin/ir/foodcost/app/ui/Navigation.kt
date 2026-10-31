package ir.foodcost.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.graphics.vector.ImageVector
import ir.foodcost.app.AppContainer
import ir.foodcost.core.costing.Section
import ir.foodcost.data.ledger.Ledger
import ir.foodcost.presentation.Labels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** A place in the app. Ids are kept as text, so the way back survives the app being recreated. */
sealed interface Screen {
    val key: String

    data object Menu : Screen {
        override val key: String = "menu"
    }

    data object Recipes : Screen {
        override val key: String = "recipes"
    }

    data object Ingredients : Screen {
        override val key: String = "ingredients"
    }

    data object Settings : Screen {
        override val key: String = "settings"
    }

    data class Recipe(val id: String) : Screen {
        override val key: String get() = "recipe|$id"
    }

    /** A recipe line: a new one when [section] and [index] are null. */
    data class Line(val recipeId: String, val section: Section?, val index: Int?) : Screen {
        override val key: String get() = "line|$recipeId|${section?.name.orEmpty()}|${index?.toString().orEmpty()}"
    }

    data class Ingredient(val id: String) : Screen {
        override val key: String get() = "ingredient|$id"
    }

    /** The ingredient form: a new ingredient when [id] is null. */
    data class EditIngredient(val id: String?) : Screen {
        override val key: String get() = "edit-ingredient|${id.orEmpty()}"
    }

    data class NewPrice(val ingredientId: String) : Screen {
        override val key: String get() = "price|$ingredientId"
    }

    companion object {
        fun fromKey(key: String): Screen? {
            val parts = key.split('|')
            return when (parts[0]) {
                "menu" -> Menu
                "recipes" -> Recipes
                "ingredients" -> Ingredients
                "settings" -> Settings
                "recipe" -> parts.getOrNull(1)?.let { Recipe(it) }
                "line" -> if (parts.size == 4) {
                    Line(
                        recipeId = parts[1],
                        section = Section.entries.firstOrNull { it.name == parts[2] },
                        index = parts[3].toIntOrNull(),
                    )
                } else {
                    null
                }
                "ingredient" -> parts.getOrNull(1)?.let { Ingredient(it) }
                "edit-ingredient" -> EditIngredient(parts.getOrNull(1)?.takeIf { it.isNotEmpty() })
                "price" -> parts.getOrNull(1)?.let { NewPrice(it) }
                else -> null
            }
        }
    }
}

enum class Tab(val label: String, val icon: ImageVector, val screen: Screen) {
    MENU("منو", Icons.Filled.Home, Screen.Menu),
    RECIPES("دستورها", Icons.AutoMirrored.Filled.List, Screen.Recipes),
    INGREDIENTS("مواد", Icons.Filled.ShoppingCart, Screen.Ingredients),
    SETTINGS("تنظیمات", Icons.Filled.Settings, Screen.Settings),
}

/** The way back: a stack of screens whose bottom is always a tab. */
class Navigator(private val stack: SnapshotStateList<Screen>) {
    val current: Screen get() = stack.last()

    val tab: Tab get() = Tab.entries.firstOrNull { it.screen == stack.first() } ?: Tab.MENU

    val canGoBack: Boolean get() = stack.size > 1

    fun open(screen: Screen) {
        stack.add(screen)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    /** Replaces the current screen, so going back skips it. */
    fun replace(screen: Screen) {
        stack[stack.lastIndex] = screen
    }

    fun tab(tab: Tab) {
        stack.clear()
        stack.add(tab.screen)
    }

    companion object {
        val Saver: Saver<SnapshotStateList<Screen>, Any> = listSaver(
            save = { list -> list.map { it.key } },
            restore = { keys -> keys.mapNotNull { Screen.fromKey(it) }.ifEmpty { listOf(Screen.Menu) }.toMutableStateList() },
        )
    }
}

/** Runs changes for the screens and tells the user when one is refused. */
class AppActions(
    private val container: AppContainer,
    private val scope: CoroutineScope,
    val snackbar: SnackbarHostState,
) {
    /** Runs [change] off the main thread. If it is refused, says why; otherwise passes its result to [onDone]. */
    fun <T> run(change: (Ledger) -> T, onDone: (T) -> Unit = {}) {
        scope.launch {
            val result = try {
                container.change(change)
            } catch (e: IllegalArgumentException) {
                snackbar.showSnackbar("این مقدار پذیرفته نشد.")
                return@launch
            }
            if (result is Ledger.Outcome.Rejected) {
                snackbar.showSnackbar(Labels.rejection(result.reason))
            } else {
                onDone(result)
            }
        }
    }

    fun say(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }
}
