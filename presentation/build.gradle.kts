// What the app's screens show and how typed text is read: Persian numbers, Jalali dates, money,
// forms and screen models. Plain Kotlin, so all of it is tested without a phone.
plugins {
    `java-library`
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":data"))

    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
