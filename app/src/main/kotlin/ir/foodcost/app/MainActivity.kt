package ir.foodcost.app

import android.content.res.Configuration
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ir.foodcost.app.ui.FoodCostApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        configureSystemBars()
        super.onCreate(savedInstanceState)
        val container = (application as FoodCostApplication).container
        setContent { FoodCostApp(container) }
    }

    // Back on the screen, perhaps the next morning: prices recorded from now on get the new date.
    override fun onStart() {
        super.onStart()
        (application as FoodCostApplication).container.refreshToday()
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
        configureSystemBars()
    }

    private fun configureSystemBars() {
        val darkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        // The premium header is dark emerald even in light mode. White status icons are essential.
        // Navigation gestures remain dark on the light canvas and light in dark mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = if (darkMode) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
    }
}
