package ir.foodcost.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ir.foodcost.app.AppContainer
import ir.foodcost.app.ui.theme.FoodCostTheme
import ir.foodcost.core.model.IngredientId
import ir.foodcost.core.model.RecipeId
import ir.foodcost.core.money.DisplayCurrency
import ir.foodcost.data.ledger.LedgerState
import ir.foodcost.presentation.BackupTexts
import ir.foodcost.presentation.MoneyFormat

@Composable
fun FoodCostApp(container: AppContainer) {
    FoodCostTheme {
        val state by container.state.collectAsState()
        val currency by container.currency.collectAsState()
        LaunchedEffect(Unit) { container.open() }
        val current = state
        if (current == null) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        } else {
            AppContent(container, current, currency)
        }
    }
}

@Composable
private fun AppContent(container: AppContainer, state: LedgerState, currency: DisplayCurrency) {
    val stack = rememberSaveable(saver = Navigator.Saver) { mutableStateListOf<Screen>(Screen.Calculator) }
    val nav = remember(stack) { Navigator(stack) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val actions = remember(container, scope, snackbar) { AppActions(container, scope, snackbar) }
    val money = remember(currency) { MoneyFormat(currency) }
    val today by container.today.collectAsState()

    // Back goes up one screen; on another tab it goes to the calculator before leaving the app.
    BackHandler(enabled = nav.canGoBack || nav.tab != Tab.CALC) {
        if (nav.canGoBack) nav.back() else nav.tab(Tab.CALC)
    }

    when (val screen = nav.current) {
        Screen.Calculator -> CalculatorScreen(container, null, state, money, today, nav, actions)
        is Screen.Dish -> CalculatorScreen(container, RecipeId(screen.id), state, money, today, nav, actions)
        Screen.Recipes -> RecipesScreen(state, money, today, nav, actions)
        Screen.Ingredients -> IngredientsScreen(state, money, today, nav, actions)
        Screen.Settings -> SettingsScreen(container, state, money, today, nav, actions)
        is Screen.Recipe -> RecipeScreen(RecipeId(screen.id), state, money, today, nav, actions)
        is Screen.Line -> LineEditorScreen(screen, state, money, today, nav, actions)
        is Screen.Ingredient -> IngredientScreen(IngredientId(screen.id), state, money, today, nav, actions)
        is Screen.EditIngredient -> IngredientEditScreen(screen.id?.let { IngredientId(it) }, state, today, nav, actions)
        is Screen.NewPrice -> PriceEntryScreen(IngredientId(screen.ingredientId), state, money, today, nav, actions)
        is Screen.Help -> HelpScreen(screen, container, money, nav, actions)
    }

    // Stored changes the app could not read are said as soon as they are found, and again only
    // when there are more of them: the figures on every screen are without them.
    var unreadable by remember { mutableIntStateOf(container.unreadableToWarn()) }
    if (unreadable > 0) {
        AlertDialog(
            onDismissRequest = { unreadable = 0 }, // said again next time, until it is acknowledged
            title = { Text("بخشی از داده‌ها خوانده نشد") },
            text = { Text(BackupTexts.unreadable(unreadable)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        container.acknowledgeUnreadable()
                        unreadable = 0
                    },
                ) { Text("فهمیدم") }
            },
        )
    }
}
