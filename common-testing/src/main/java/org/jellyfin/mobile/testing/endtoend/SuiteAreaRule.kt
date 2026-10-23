package org.jellyfin.mobile.testing.endtoend

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Fails every test of a class that isn't in exactly one of [areas]: the annotations an app's e2e
 * suites are run by (scripts/run-e2e-tests.sh, E2E_SUITE=...). With none, the class would silently
 * drop out of every area run; with two, it would run twice. It reads the running class itself, so
 * inherited tests and any source layout count, but it only fires when the class runs: the full suite
 * (the default) is what catches a class with no area.
 */
class SuiteAreaRule(private val areas: Set<Class<out Annotation>>) : TestRule {

    override fun apply(base: Statement, description: Description): Statement {
        val problem = suiteAreaProblem(description.testClass, areas) ?: return base
        return object : Statement() {
            override fun evaluate() = throw AssertionError(problem)
        }
    }
}

/** Why [testClass] isn't in exactly one of [areas], or null when it is. */
internal fun suiteAreaProblem(testClass: Class<*>, areas: Set<Class<out Annotation>>): String? {
    val tagged = areas.filter(testClass::isAnnotationPresent)
    if (tagged.size == 1) return null
    return "${testClass.simpleName} is in areas ${tagged.map { it.simpleName }}, " +
        "expected exactly one of ${areas.map { it.simpleName }}"
}
