package org.jellyfin.mobile.testing.endtoend

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.runner.Description
import org.junit.runners.model.Statement

class SuiteAreaRuleTest {

    @Retention(AnnotationRetention.RUNTIME)
    annotation class BrowseArea

    @Retention(AnnotationRetention.RUNTIME)
    annotation class PlaybackArea

    @Retention(AnnotationRetention.RUNTIME)
    annotation class Unrelated

    @BrowseArea
    class InOneArea

    @Unrelated
    class InNoArea

    @BrowseArea
    @PlaybackArea
    class InTwoAreas

    @BrowseArea
    open class TaggedBase

    // Area tags aren't @Inherited, and the runner's annotation filter reads only the class's own
    class UntaggedSubclass : TaggedBase()

    private val areas = setOf(BrowseArea::class.java, PlaybackArea::class.java)

    private fun run(testClass: Class<*>): Boolean {
        var ran = false
        val base = object : Statement() {
            override fun evaluate() {
                ran = true
            }
        }
        SuiteAreaRule(areas).apply(base, Description.createTestDescription(testClass, "test")).evaluate()
        return ran
    }

    @Test
    fun `a class in exactly one area runs its tests`() {
        run(InOneArea::class.java) shouldBe true
    }

    @Test
    fun `a class in no area fails, naming it`() {
        shouldThrow<AssertionError> { run(InNoArea::class.java) }.message shouldContain "InNoArea is in areas []"
    }

    @Test
    fun `a class in two areas fails, naming them`() {
        shouldThrow<AssertionError> { run(InTwoAreas::class.java) }.message shouldContain
            "InTwoAreas is in areas [BrowseArea, PlaybackArea]"
    }

    @Test
    fun `a subclass needs its own area`() {
        suiteAreaProblem(UntaggedSubclass::class.java, areas) shouldContain "UntaggedSubclass"
    }
}
