package dev.chayaphon.journeyscope.navigation

import dev.chayaphon.journeyscope.core.Journey
import dev.chayaphon.journeyscope.core.JourneyScopeConfig
import dev.chayaphon.journeyscope.core.JourneyTracer
import dev.chayaphon.journeyscope.core.model.JourneyEvent
import dev.chayaphon.journeyscope.core.model.MetadataValue
import dev.chayaphon.journeyscope.core.model.SpanCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavigationTracerTest {
    private fun newJourney(): Journey = JourneyTracer(JourneyScopeConfig(enabled = true)).startJourney("J")

    private fun Journey.events(): List<JourneyEvent> = checkNotNull(snapshot()).events

    private fun JourneyEvent.text(key: String): String? = (metadata[key] as? MetadataValue.Text)?.value

    @Test
    fun navigateRecordsOriginTargetAndStacks() {
        val journey = newJourney()
        val tracer = NavigationTracer()
        assertTrue(tracer.navigate("product", "cart", listOf("product"), listOf("product", "cart"), journey))
        val event = journey.events().single()
        assertEquals(SpanCategory.NAVIGATION, event.category)
        assertEquals("Navigate: product → cart", event.name)
        assertEquals("product", event.text("nav.from"))
        assertEquals("cart", event.text("nav.to"))
        assertEquals("product", event.text("nav.stack.before"))
        assertEquals("product > cart", event.text("nav.stack.after"))
        assertEquals(journey.id.value, event.text("nav.trace"))
        assertEquals("product → cart · stack 1 → 2", event.text("detail"))
    }

    @Test
    fun everyActionKindIsRecorded() {
        val journey = newJourney()
        val tracer = NavigationTracer()
        tracer.navigate("a", "b", listOf("a"), listOf("a", "b"), journey)
        tracer.pop("b", "a", listOf("a", "b"), listOf("a"), journey)
        tracer.replace("a", "c", listOf("a"), listOf("c"), journey)
        tracer.switchStack("home", "cart", listOf("c"), listOf("cart"), journey)
        tracer.destinationEntered("cart", listOf("cart"), journey)
        tracer.destinationExited("cart", listOf("cart"), journey)
        tracer.snapshot(listOf("cart"), journey)
        assertEquals(
            listOf("Navigate", "Pop", "Replace", "StackSwitch", "DestinationEntered", "DestinationExited", "BackStackSnapshot"),
            journey.events().map { it.text("nav.action") },
        )
    }

    @Test
    fun argumentsNeverReachTheTrace() {
        val journey = newJourney()
        val tracer = NavigationTracer()
        tracer.navigate(
            "product/12345",
            "profile/jane@example.com?token=abc",
            listOf("product/12345"),
            listOf("product/12345", "order/550e8400-e29b-41d4-a716-446655440000"),
            journey,
        )
        val event = journey.events().single()
        assertEquals("product/{arg}", event.text("nav.from"))
        assertEquals("profile/{arg}", event.text("nav.to"))
        assertEquals("product/{arg} > order/{arg}", event.text("nav.stack.after"))
        assertFalse(event.name.contains("12345"))
        assertFalse(event.name.contains("jane"))
    }

    @Test
    fun templatesSurviveRedaction() {
        assertEquals("product/{id}", RouteRedactor.Default("product/{id}"))
    }

    @Test
    fun journeyComesFromProviderWhenNotPassed() {
        val journey = newJourney()
        val tracer = NavigationTracer(NavigationTraceConfig(journey = { journey }))
        assertTrue(tracer.snapshot(listOf("a")))
        assertEquals(1, journey.events().size)
    }

    @Test
    fun withoutAJourneyNothingIsRecorded() {
        assertFalse(NavigationTracer().snapshot(listOf("a")))
    }

    @Test
    fun stackIsBoundedToMaxDepth() {
        val journey = newJourney()
        val tracer = NavigationTracer(NavigationTraceConfig(maxStackDepth = 2))
        tracer.snapshot(listOf("a", "b", "c"), journey)
        assertEquals("b > c", journey.events().single().text("nav.stack.after"))
    }

    @Test
    fun completionIsOnlyRecordedWhenMarked() {
        val journey = newJourney()
        val tracer = NavigationTracer()
        tracer.navigate("a", "b", listOf("a"), listOf("a", "b"), journey)
        assertTrue(journey.events().none { (it.metadata["nav.completed"] as? MetadataValue.Flag)?.value == true })
        tracer.markCompleted("b", journey)
        val completed = journey.events().last()
        assertEquals(MetadataValue.Flag(true), completed.metadata["nav.completed"])
    }

    @Test
    fun faultyRedactorNeverThrows() {
        val journey = newJourney()
        val tracer = NavigationTracer(NavigationTraceConfig(routeRedactor = { error("boom") }))
        assertFalse(tracer.navigate("a", "b", emptyList(), emptyList(), journey))
        assertNull(journey.events().firstOrNull())
    }
}
