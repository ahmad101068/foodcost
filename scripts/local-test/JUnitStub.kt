// Local runs only: stands in for JUnit's @Test so the same test files compile without Gradle.
// Never added to the Gradle build, which uses the real JUnit 5.
package org.junit.jupiter.api

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Test
