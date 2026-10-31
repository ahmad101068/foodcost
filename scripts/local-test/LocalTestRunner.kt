// Local runs only: finds every @Test method in the compiled test folders and runs it.
import java.io.File
import java.lang.reflect.InvocationTargetException
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    @Suppress("UNCHECKED_CAST")
    val testAnnotation = Class.forName("org.junit.jupiter.api.Test") as Class<out Annotation>
    val classNames = args.flatMap { dir ->
        val root = File(dir)
        root.walkTopDown()
            .filter { it.isFile && it.name.endsWith("Test.class") && '$' !in it.name }
            .map { it.relativeTo(root).path.removeSuffix(".class").replace(File.separatorChar, '.') }
            .toList()
    }.sorted()

    var passed = 0
    val failures = mutableListOf<String>()
    for (name in classNames) {
        val type = Class.forName(name)
        if (type.isAnnotation) continue
        val methods = type.declaredMethods.filter { it.isAnnotationPresent(testAnnotation) }.sortedBy { it.name }
        println(type.simpleName)
        for (method in methods) {
            try {
                method.invoke(type.getDeclaredConstructor().newInstance())
                passed++
                println("  ok   ${method.name}")
            } catch (e: InvocationTargetException) {
                val cause = e.targetException
                failures += "${type.simpleName}.${method.name}: $cause"
                println("  FAIL ${method.name}\n       $cause")
                cause.stackTrace.take(6).forEach { println("         at $it") }
            }
        }
    }
    println("\n$passed passed, ${failures.size} failed")
    if (failures.isNotEmpty()) exitProcess(1)
}
