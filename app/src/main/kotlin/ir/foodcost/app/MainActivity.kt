package ir.foodcost.app

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ir.foodcost.app.ui.FoodCostApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as FoodCostApplication).container
        setContent { FoodCostApp(container) }
    }

    // The calculation in the calculator is kept as it is typed; this catches the last keys before
    // the phone may close the app in the background.
    override fun onStop() {
        super.onStop()
        (application as FoodCostApplication).container.saveDraft()
    }

    // The activity handles a change to the dark theme itself, so the status and navigation bar
    // icons have to be told to follow it; otherwise they stay dark on a dark screen.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        enableEdgeToEdge()
    }
}
