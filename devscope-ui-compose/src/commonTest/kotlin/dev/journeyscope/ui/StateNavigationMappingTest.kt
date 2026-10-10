package dev.journeyscope.ui

import dev.journeyscope.core.JourneyScopeConfig
import dev.journeyscope.core.JourneyTracer
import dev.journeyscope.core.expectation.ExpectedSequence
import dev.journeyscope.core.expectation.ExpectedStep
import dev.journeyscope.core.model.SpanCategory
import dev.journeyscope.core.model.diagnosticMetadata
import dev.journeyscope.ui.demo.DemoJourneys
import dev.journeyscope.ui.mapping.DefaultInsightProvider
import dev.journeyscope.ui.mapping.InspectorThresholds
import dev.journeyscope.ui.mapping.MetadataDisplayPolicy
import dev.journeyscope.ui.mapping.TraceMapper
import dev.journeyscope.ui.model.JourneyCategory
import dev.journeyscope.ui.model.RowState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StateNavigationMappingTest {
    private val tracer = JourneyTracer(JourneyScopeConfig(enabled = true))

    private fun mapper(sequences: List<ExpectedSequence> = emptyList()) =
        TraceMapper(InspectorThresholds(), MetadataDisplayPolicy.Default, DefaultInsightProvider(), sequences)

    @Test
    fun stateAndNavigationEventsAppearInTheTimeline() {
        val journey = tracer.startJourney("Add to Cart")
        journey.recordEvent(
            "cart-ui-state state observed",
            SpanCategory.STATE,
            diagnosticMetadata {
                put("state.event", "observed")
                put("state.current", "Added")
                put("detail", "Adding → Added")
            },
        )
        journey.beginSpan("Cart count render", SpanCategory.UI_RENDER).end()
        journey.recordEvent(
            "Navigate: product → cart",
            SpanCategory.NAVIGATION,
            diagnosticMetadata {
                put("nav.from", "product")
                put("nav.to", "cart")
                put("nav.stack.after", "product > cart")
                put("detail", "product → cart")
            },
        )
        journey.finish()

        val rows = mapper().detail(checkNotNull(journey.snapshot()), 0L).rows
        val state = rows.single { it.title == "cart-ui-state state observed" }
        assertEquals(RowState.Event, state.state)
        assertEquals("Adding → Added", state.detail)
        assertEquals(JourneyCategory.UiVm, state.category)
        val shown = state.metadata.associate { it.key to it }
        assertEquals("Cart count render", shown.getValue("ui.checkpoint").value)
        assertTrue(shown.values.none { it.hidden })

        val nav = rows.single { it.title == "Navigate: product → cart" }
        assertEquals("product > cart", nav.metadata.single { it.key == "nav.stack.after" }.value)
        assertTrue(nav.metadata.none { it.hidden })
    }

    @Test
    fun configuredSequenceReportsOnlyItsOwnMissingSteps() {
        val journey = tracer.startJourney("Add to Cart")
        journey.recordEvent("Button Click", SpanCategory.UI_INTERACTION)
        journey.recordEvent("state observed", SpanCategory.STATE)
        journey.finish()
        val sequence = ExpectedSequence(
            "add-to-cart",
            listOf(
                ExpectedStep.named("Click", "Button Click"),
                ExpectedStep.category("State", SpanCategory.STATE),
                ExpectedStep.category("Navigation", SpanCategory.NAVIGATION),
            ),
        )
        val detail = mapper(listOf(sequence)).detail(checkNotNull(journey.snapshot()), 0L)
        val check = detail.sequences.single()
        assertEquals(listOf(true, true, false), check.steps.map { it.observed })
        assertEquals(1, check.missingCount)
    }

    @Test
    fun noSequenceConfiguredMeansNothingIsFlagged() {
        val journey = tracer.startJourney("J")
        journey.finish()
        assertTrue(mapper().detail(checkNotNull(journey.snapshot()), 0L).sequences.isEmpty())
    }

    @Test
    fun demoFixtureShowsStateThenNavigation() {
        val rows = mapper().detail(DemoJourneys.addToCartThenCart(), DemoJourneys.NOW_NANOS).rows
        val names = rows.map { it.title }
        assertTrue(names.indexOf("cart-ui-state state observed") < names.indexOf("Navigate: product/{arg} → cart"))
    }
}
