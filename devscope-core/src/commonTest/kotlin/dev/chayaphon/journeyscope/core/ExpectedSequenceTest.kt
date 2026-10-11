package dev.chayaphon.journeyscope.core

import dev.chayaphon.journeyscope.core.expectation.ExpectedSequence
import dev.chayaphon.journeyscope.core.expectation.ExpectedStep
import dev.chayaphon.journeyscope.core.model.SpanCategory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExpectedSequenceTest {
    private val sequence =
        ExpectedSequence(
            "add-to-cart",
            listOf(
                ExpectedStep.named("Click", "Button Click"),
                ExpectedStep.category("API", SpanCategory.NETWORK),
                ExpectedStep.category("State", SpanCategory.STATE),
                ExpectedStep.category("Navigation", SpanCategory.NAVIGATION),
            ),
        )

    @Test
    fun reportsOnlyConfiguredStepsAsMissing() =
        runTest {
            val journey = testTracer().startJourney("J")
            journey.recordEvent("Button Click", SpanCategory.UI_INTERACTION)
            journey.traceSuspend("POST /cart", SpanCategory.NETWORK) { }
            journey.recordEvent("state observed", SpanCategory.STATE)
            journey.finish()
            val check = sequence.evaluate(journey.trace())
            assertEquals(listOf("Navigation"), check.missing)
        }

    @Test
    fun stepsMustAppearInOrder() =
        runTest {
            val journey = testTracer().startJourney("J")
            journey.recordEvent("state observed", SpanCategory.STATE)
            journey.recordEvent("Button Click", SpanCategory.UI_INTERACTION)
            journey.finish()
            val check = sequence.evaluate(journey.trace())
            assertTrue("State" !in check.missing || "Click" in check.steps.map { it.label })
            assertEquals(listOf("API", "State", "Navigation"), check.missing)
        }

    @Test
    fun completeSequenceHasNoMissingSteps() =
        runTest {
            val journey = testTracer().startJourney("J")
            journey.recordEvent("Button Click", SpanCategory.UI_INTERACTION)
            journey.traceSuspend("POST", SpanCategory.NETWORK) { }
            journey.recordEvent("s", SpanCategory.STATE)
            journey.recordEvent("n", SpanCategory.NAVIGATION)
            journey.finish()
            assertTrue(sequence.evaluate(journey.trace()).missing.isEmpty())
        }
}
