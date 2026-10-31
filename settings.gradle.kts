pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "foodcost-app"

include(":core", ":data", ":presentation")

// The Android app needs the Android SDK; the shared modules build without it.
if (!System.getenv("ANDROID_HOME").isNullOrBlank() || !System.getenv("ANDROID_SDK_ROOT").isNullOrBlank() || file("local.properties").exists()) {
    include(":app")
}
