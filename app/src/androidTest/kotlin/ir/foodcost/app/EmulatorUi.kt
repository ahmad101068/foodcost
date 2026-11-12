package ir.foodcost.app

import android.app.Instrumentation
import android.util.Log
import android.os.ParcelFileDescriptor
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/** The CI system launcher can ANR under load and take input focus away from the tested app. */
internal fun dismissEmulatorLauncherAnr(instrumentation: Instrumentation) {
    val device = UiDevice.getInstance(instrumentation)
    if (!device.hasObject(By.res("android", "aerr_close")) &&
        !device.hasObject(By.res("android", "aerr_wait"))) return
    val launcher = device.hasObject(By.textContains("Pixel Launcher")) ||
        device.hasObject(By.textContains("Launcher3"))
    if (!launcher) {
        val texts = device.findObjects(By.pkg("android")).mapNotNull { it.text }
            .filter { it.isNotBlank() }.distinct().take(30).joinToString(" | ")
        val lastAnr = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("dumpsys activity lastanr"),
        ).use { it.readBytes().toString(Charsets.UTF_8).take(6000) }
        error("Unexpected ANR dialog; only the system launcher may be dismissed. Texts: $texts\nLast ANR: $lastAnr")
    }
    val close = device.findObject(By.res("android", "aerr_close"))
        ?: error("Launcher ANR has no Close app action")
    Log.w("FoodCostTest", "Closing the CI system launcher's ANR dialog before app input")
    try {
        close.click()
    } catch (gone: StaleObjectException) {
        // It disappeared between detection and the click; the visibility assertion still applies.
    }
    check(device.wait(Until.gone(By.res("android", "aerr_close")), 5_000)) {
        "Launcher ANR still covers the app"
    }
}
