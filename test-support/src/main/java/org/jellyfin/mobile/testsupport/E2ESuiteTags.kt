package org.jellyfin.mobile.testsupport

import java.io.File

/**
 * Checks an app's e2e suite tags (its e2e/E2ESuites.kt, run by scripts/run-e2e-tests.sh with
 * E2E_SUITE=...) by reading the sources under [e2eDir], since the instrumented classes aren't on the
 * unit-test classpath.
 *
 * Every class with an `@Test` must carry exactly one of [areas]: with none it would silently drop
 * out of every area run, with two it would run twice. Smoke goes on single tests, never on a class,
 * so a slow test added to the class later can't join the quick pass unnoticed.
 */
class E2ESuiteTags(private val e2eDir: File, private val areas: List<String>) {

    private val testClasses: List<TestClass> by lazy {
        e2eDir.walk().filter { it.isFile && it.extension == "kt" }.flatMap { parse(it.readText()) }.toList()
    }

    /** Everything wrong with the tags, one line per problem; empty when they're all right. */
    fun problems(): List<String> {
        if (testClasses.isEmpty()) return listOf("no e2e test classes in $e2eDir")
        return testClasses.flatMap { testClass ->
            val tagged = areas.filter { it in testClass.annotations }
            listOfNotNull(
                "${testClass.name}: in areas $tagged, expected exactly one of $areas".takeIf { tagged.size != 1 },
                "${testClass.name}: @$SMOKE on the class; tag its single tests instead"
                    .takeIf { SMOKE in testClass.annotations },
            )
        }
    }

    /** The tests in the smoke suite, as `Class.test`. */
    fun smokeTests(): List<String> = testClasses.flatMap { testClass ->
        testClass.smokeTests.map { "${testClass.name}.$it" }
    }

    private class TestClass(val name: String, val annotations: Set<String>, val smokeTests: List<String>)

    private companion object {
        const val SMOKE = "Smoke"
        const val ANNOTATIONS = """((?:@[\w.]+(?:\([^)]*\))?\s*)*)"""
        val CLASS = Regex(
            ANNOTATIONS + """((?:(?:public|internal|private|open|final|abstract|sealed|data|inner|enum|annotation)\s+)*)""" +
                """\bclass\s+(\w+)""",
        )
        val FUNCTION = Regex(
            ANNOTATIONS + """(?:(?:public|internal|private|open|override|suspend)\s+)*\bfun\s+(`[^`]+`|\w+)""",
        )
        val ANNOTATION_NAME = Regex("""@(?:[\w.]+\.)?(\w+)""")
        val NOT_TESTS = setOf("abstract", "sealed", "enum", "annotation")

        /** Strings and comments removed, so a commented-out or quoted tag isn't read as one. */
        fun stripped(source: String): String = source
            .replace(Regex("\"\"\"[\\s\\S]*?\"\"\""), "\"\"")
            .replace(Regex("\"(?:\\\\.|[^\"\\\\\n])*\""), "\"\"")
            .replace(Regex("""/\*[\s\S]*?\*/"""), " ")
            .replace(Regex("//.*"), "")

        fun annotationNames(block: String): Set<String> =
            ANNOTATION_NAME.findAll(block).map { it.groupValues[1] }.toSet()

        /** The concrete classes with tests in one file. A class's body runs up to the next class. */
        fun parse(source: String): List<TestClass> {
            val code = stripped(source)
            val classes = CLASS.findAll(code).toList()
            return classes.mapIndexedNotNull { index, match ->
                val modifiers = match.groupValues[2].split(Regex("\\s+")).toSet()
                val body = code.substring(
                    match.range.last + 1,
                    classes.getOrNull(index + 1)?.range?.first ?: code.length,
                )
                val tests = FUNCTION.findAll(body)
                    .map { annotationNames(it.groupValues[1]) to it.groupValues[2].trim('`') }
                    .filter { (annotations, _) -> "Test" in annotations }
                    .toList()
                if (tests.isEmpty() || modifiers.any { it in NOT_TESTS }) return@mapIndexedNotNull null
                TestClass(
                    name = match.groupValues[3],
                    annotations = annotationNames(match.groupValues[1]),
                    smokeTests = tests.filter { (annotations, _) -> SMOKE in annotations }.map { it.second },
                )
            }
        }
    }
}
