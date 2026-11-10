package ir.foodcost.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import ir.foodcost.app.AppContainer
import ir.foodcost.app.ui.theme.FoodCostTheme
import ir.foodcost.core.model.IngredientId
import ir.foodcost.core.model.RecipeId
import ir.foodcost.core.money.DisplayCurrency
import ir.foodcost.data.ledger.LedgerState
import ir.foodcost.presentation.BackupTexts
import ir.foodcost.presentation.MoneyFormat
import ir.foodcost.presentation.SettingsInput

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
    var settingsInput by remember {
        mutableStateOf(SettingsInput.of(state.pricing, state.costing, money))
    }
    var settingsBaseline by remember { mutableStateOf(settingsInput) }
    var settingsCurrency by remember { mutableStateOf(currency) }
    // Reconcile a committed ledger change before the next UI interaction. A LaunchedEffect can
    // run *after* a scope chip was tapped, treating that new choice as an old draft and silently
    // restoring the previous policy. SideEffect runs at the end of the composition which observed
    // the new ledger, before the user can interact with that rendered frame.
    val latestSettings = remember(state.pricing, state.costing, money) {
        SettingsInput.of(state.pricing, state.costing, money)
    }
    SideEffect {
        if (settingsBaseline != latestSettings || settingsCurrency != currency) {
            val draft = settingsInput.inCurrency(settingsCurrency, currency)
            val previous = settingsBaseline.inCurrency(settingsCurrency, currency)
            settingsInput = rebaseSettingsDraft(draft, previous, latestSettings)
            settingsBaseline = latestSettings
            settingsCurrency = currency
        }
    }
    var settingsPage by rememberSaveable { mutableStateOf(SettingsPage.OVERVIEW) }
    var confirmExit by remember { mutableStateOf(false) }
    val activity = LocalContext.current.findActivity()

    // Child screens handle their own Back first; the calculator root asks before finishing.
    BackHandler {
        when {
            nav.canGoBack -> nav.back()
            nav.tab != Tab.CALC -> nav.tab(Tab.CALC)
            else -> confirmExit = true
        }
    }

    when (val screen = nav.current) {
        Screen.Calculator -> CalculatorScreen(container, null, state, money, today, nav, actions)
        is Screen.Dish -> CalculatorScreen(container, RecipeId(screen.id), state, money, today, nav, actions)
        Screen.Recipes -> RecipesScreen(state, money, today, nav, actions)
        Screen.Ingredients -> IngredientsScreen(state, money, today, nav, actions)
        Screen.Settings -> SettingsScreen(container, state, money, today, nav, actions,
            input = settingsInput, onInputChange = { settingsInput = it },
            page = settingsPage, onPageChange = { settingsPage = it })
        is Screen.Recipe -> RecipeScreen(RecipeId(screen.id), state, money, today, nav, actions)
        is Screen.Line -> LineEditorScreen(screen, state, money, today, nav, actions)
        is Screen.Ingredient -> IngredientScreen(IngredientId(screen.id), state, money, today, nav, actions)
        is Screen.EditIngredient -> IngredientEditScreen(screen.id?.let { IngredientId(it) }, state, today, nav, actions)
        is Screen.NewPrice -> PriceEntryScreen(IngredientId(screen.ingredientId), state, money, today, nav, actions)
        is Screen.Help -> HelpScreen(screen, container, money, nav, actions)
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            modifier = Modifier.testTag("exit-confirmation"),
            title = { Text("خروج از برنامه؟") },
            text = { Text("می‌خواهید از برنامه خارج شوید؟") },
            confirmButton = {
                TextButton(onClick = { confirmExit = false; activity?.finish() }, modifier = Modifier.testTag("exit-confirm")) { Text("خروج") }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false }, modifier = Modifier.testTag("exit-stay")) { Text("ماندن") }
            },
        )
    }

    // A data file too damaged to read was set aside, not deleted: said until the user has read it.
    var setAside by remember { mutableStateOf(container.setAsideToWarn()) }
    setAside?.let { file ->
        AlertDialog(
            onDismissRequest = { setAside = null }, // said again next time, until it is acknowledged
            title = { Text("پروندهٔ داده‌ها خراب بود") },
            text = { Text(BackupTexts.setAside(file)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        container.acknowledgeSetAside()
                        setAside = null
                    },
                ) { Text("فهمیدم") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        container.acknowledgeSetAside()
                        setAside = null
                        nav.tab(Tab.SETTINGS)
                    },
                ) { Text("دیدن راه‌ها") }
            },
        )
    }

    // Stored changes the app could not read are said as soon as they are found, and again only
    // when there are more of them: until the user chooses what to do, no figure is shown.
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
            dismissButton = {
                TextButton(
                    onClick = {
                        container.acknowledgeUnreadable()
                        unreadable = 0
                        nav.tab(Tab.SETTINGS)
                    },
                ) { Text("دیدن راه‌ها") }
            },
        )
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val next = current.baseContext
        if (next === current) return null
        current = next
    }
    return current as? Activity
}

/** Update saved groups without discarding edits in another settings section. */
private fun rebaseSettingsDraft(draft: SettingsInput, previous: SettingsInput, latest: SettingsInput): SettingsInput {
    var result = latest
    val pricingChanged = draft.copy(
        categories = previous.categories, miscPercent = previous.miscPercent,
        monthlyFixed = previous.monthlyFixed, monthlyPortions = previous.monthlyPortions,
        onlineCommission = previous.onlineCommission, onlineDiscount = previous.onlineDiscount, onlineUplift = previous.onlineUplift,
        conversionOrigin = previous.conversionOrigin,
    ) != previous
    if (pricingChanged) result = result.copy(
        method = draft.method, percent = draft.percent, basis = draft.basis, scope = draft.scope,
        carried = draft.carried, conversionOrigin = draft.conversionOrigin,
        vatPercent = draft.vatPercent, menuPriceIncludesVat = draft.menuPriceIncludesVat,
        roundingStepRials = draft.roundingStepRials, roundingDirection = draft.roundingDirection,
    )
    if (draft.categories != previous.categories) result = result.copy(categories = draft.categories)
    if (draft.miscPercent != previous.miscPercent || draft.monthlyFixed != previous.monthlyFixed ||
        draft.monthlyPortions != previous.monthlyPortions || draft.onlineCommission != previous.onlineCommission ||
        draft.onlineDiscount != previous.onlineDiscount || draft.onlineUplift != previous.onlineUplift
    ) result = result.copy(
        miscPercent = draft.miscPercent, monthlyFixed = draft.monthlyFixed, monthlyPortions = draft.monthlyPortions,
        onlineCommission = draft.onlineCommission, onlineDiscount = draft.onlineDiscount, onlineUplift = draft.onlineUplift,
    )
    return result
}

private fun SettingsInput.inCurrency(from: DisplayCurrency, to: DisplayCurrency): SettingsInput {
    if (from == to) return this
    val fixed = MoneyFormat(from).parse(monthlyFixed)?.let { MoneyFormat(to).editable(it) } ?: monthlyFixed
    return copy(monthlyFixed = fixed)
}
