package ir.foodcost.app

import android.app.Application

class FoodCostApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
