package ir.foodcost.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ir.foodcost.app.AppContainer
import ir.foodcost.app.ui.theme.FoodCostTheme

@Composable
fun FoodCostApp(container: AppContainer) {
    FoodCostTheme {
        val state by container.state.collectAsState()
        LaunchedEffect(Unit) { container.open() }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(contentAlignment = Alignment.Center) {
                val current = state
                if (current == null) CircularProgressIndicator() else Text("فودکاست: ${current.ingredients.size} ماده")
            }
        }
    }
}
