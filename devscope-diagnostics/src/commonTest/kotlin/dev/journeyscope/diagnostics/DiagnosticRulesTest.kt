package dev.journeyscope.diagnostics

import dev.journeyscope.core.model.SpanCategory
import dev.journeyscope.core.model.SpanStatus
import dev.journeyscope.core.model.TraceStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class DiagnosticRulesTest {
    @Test
    fun demoJourneyReportsNetworkDominance() {
        val report = analyzer().analyze(addToCartDemo())

        val dominant = report.findings.single { it.ruleId == "timing.dominant" }
        assertTrue("approximately 74.3% of this sequential journey" in dominant.evidence.first())
        assertEquals("74.3%", dominant.metrics["share"])
        assertTrue(dominant.inference.orEmpty().contains("does not prove"))
        assertTrue("timing.database_significant" !in report.ids())
        assertTrue("journey.slow" !in report.ids())
    }

    @Test
    fun durationsInEvidenceAreRoundedNotTruncated() {
        val trace =
            TraceBuilder().span("api", SpanCategory.NETWORK, 0, 1000).build(totalMs = 2000).let {
                it.copy(spans = it.spans.map { span -> span.copy(endNanos = 1_999_600_000L) }, endNanos = 2_000_000_000L)
            }
        val finding = analyzer().analyze(trace).findings.single { it.ruleId == "network.slow" }
        assertTrue("2000 ms" in finding.evidence.first(), finding.evidence.first())
    }

    @Test
    fun overlappingJourneyDoesNotClaimSequential() {
        val trace =
            TraceBuilder()
                .span("a", SpanCategory.NETWORK, 0, 900)
                .span("b", SpanCategory.NETWORK, 100, 900, metadata = network(path = "/other"))
                .build()
        val dominant = analyzer().analyze(trace).findings.single { it.ruleId == "timing.dominant" }
        assertFalse("sequential journey" in dominant.evidence.first())
        assertTrue("overlapping" in dominant.evidence.first())
    }

    @Test
    fun slowJourneyThresholdBoundary() {
        fun ids(totalMs: Long) = analyzer().analyze(TraceBuilder().span("s", SpanCategory.CUSTOM, 0, totalMs).build()).ids()
        assertFalse("journey.slow" in ids(1999))
        assertTrue("journey.slow" in ids(2000))
    }

    @Test
    fun slowDatabaseThresholdAndLeafSelection() {
        fun ids(ms: Long) =
            analyzer()
                .analyze(
                    TraceBuilder()
                        .span("tx", SpanCategory.DATABASE, 0, ms)
                        .span("q", SpanCategory.DATABASE, 0, ms, parent = "tx")
                        .build(),
                ).findings
                .filter { it.ruleId == "database.slow" }
        assertTrue(ids(99).isEmpty())
        val findings = ids(100)
        assertEquals(1, findings.size)
        assertEquals(1, findings.single().relatedSpanIds.size)
    }

    @Test
    fun slowNetworkSkipsFailedSpansWhichAreReportedAsFailures() {
        val trace =
            TraceBuilder()
                .span("api", SpanCategory.NETWORK, 0, 1500, status = SpanStatus.FAILED, metadata = network(failure = "TIMEOUT"))
                .build()
        val ids = analyzer().analyze(trace).ids()
        assertTrue("network.failed" in ids)
        assertFalse("network.slow" in ids)
    }

    @Test
    fun failureClassification() {
        val trace =
            TraceBuilder()
                .span("ok-business", SpanCategory.NETWORK, 0, 100, metadata = network(status = 200, business = "FAILURE"))
                .span("cancelled", SpanCategory.NETWORK, 100, 50, status = SpanStatus.CANCELLED, metadata = network(path = "/c"))
                .build()
        val report = analyzer().analyze(trace)

        val business = report.findings.single { it.ruleId == "business.validation_failed" }
        assertTrue("despite HTTP 200" in business.evidence.first())
        assertFalse("network.failed" in report.ids())
    }

    @Test
    fun failedValidationSpanIsReported() {
        val trace = TraceBuilder().span("v", SpanCategory.BUSINESS_VALIDATION, 0, 5, status = SpanStatus.FAILED).build()
        assertTrue("business.validation_failed" in analyzer().analyze(trace).ids())
    }

    @Test
    fun repeatedRequestsNeedMinimumCount() {
        fun ids(count: Int): List<String> {
            val builder = TraceBuilder()
            repeat(count) { builder.span("r$it", SpanCategory.NETWORK, it * 100L, 50, metadata = network(path = "/items")) }
            return analyzer().analyze(builder.build()).ids()
        }
        assertFalse("network.repeated" in ids(2))
        assertTrue("network.repeated" in ids(3))
    }

    @Test
    fun repeatedRequestsAfterFailuresAreCalledPossibleRetries() {
        val trace =
            TraceBuilder()
                .span("r0", SpanCategory.NETWORK, 0, 50, status = SpanStatus.FAILED, metadata = network())
                .span("r1", SpanCategory.NETWORK, 100, 50, status = SpanStatus.FAILED, metadata = network())
                .span("r2", SpanCategory.NETWORK, 200, 50, metadata = network())
                .build()
        val finding = analyzer().analyze(trace).findings.single { it.ruleId == "network.repeated" }
        assertTrue("retries" in finding.inference.orEmpty())
    }

    @Test
    fun sequentialDatabaseBurstNeedsFiveOperations() {
        fun ids(count: Int): List<String> {
            val builder = TraceBuilder().span("repo", SpanCategory.REPOSITORY, 0, count * 10L)
            repeat(count) { builder.span("q$it", SpanCategory.DATABASE, it * 10L, 5, parent = "repo") }
            return analyzer().analyze(builder.build()).ids()
        }
        assertFalse("database.sequential_burst" in ids(4))
        assertTrue("database.sequential_burst" in ids(5))
    }

    @Test
    fun gapRuleRespectsMinimumAndRelativeThreshold() {
        fun ids(gapMs: Long) =
            analyzer()
                .analyze(
                    TraceBuilder().span("a", SpanCategory.CUSTOM, 0, 100).span("b", SpanCategory.CUSTOM, 100 + gapMs, 100).build(),
                ).ids()
        assertFalse("journey.gap" in ids(100))
        assertTrue("journey.gap" in ids(400))
    }

    @Test
    fun interruptedJourneyIsFlaggedButRunningIsNot() {
        val interrupted =
            TraceBuilder(TraceStatus.INTERRUPTED)
                .span("a", SpanCategory.NETWORK, 0, 100, status = SpanStatus.INTERRUPTED, endedBySystem = true)
                .build()
        assertTrue("journey.unfinished" in analyzer().analyze(interrupted).ids())

        val running = TraceBuilder(TraceStatus.RUNNING).span("a", SpanCategory.NETWORK, 0, null).build()
        assertTrue(analyzer().analyze(running, nowNanos = 5_000_000_000).findings.none { it.ruleId == "journey.unfinished" })
    }

    @Test
    fun missingCheckpointIsReportedWithEmissionEvidence() {
        val config = DiagnosticConfig(expectedSequences = listOf(sequenceOf("emit", "checkpoint")))
        val trace =
            TraceBuilder()
                .span(
                    "emit",
                    SpanCategory.STATE_EMISSION,
                    0,
                    5,
                ).event("CartUiState emitted", SpanCategory.STATE_EMISSION, 5)
                .build()

        val finding = analyzer(config).analyze(trace).findings.single { it.ruleId == "journey.checkpoint_missing" }

        assertTrue(finding.evidence.any { "checkpoint" in it })
        assertTrue(
            finding.evidence.any {
                "UI state emission was observed, but the configured completion checkpoint was not recorded" in it
            },
        )
    }

    @Test
    fun checkpointRuleIgnoresFailedJourneysAndCompleteSequences() {
        val config = DiagnosticConfig(expectedSequences = listOf(sequenceOf("emit")))
        val ok = TraceBuilder().span("emit", SpanCategory.CUSTOM, 0, 5).build()
        assertFalse("journey.checkpoint_missing" in analyzer(config).analyze(ok).ids())
        val failed = TraceBuilder(TraceStatus.FAILED).span("x", SpanCategory.CUSTOM, 0, 5).build()
        assertFalse("journey.checkpoint_missing" in analyzer(config).analyze(failed).ids())
    }

    @Test
    fun databaseSignificantWhenNotDominant() {
        val trace =
            TraceBuilder()
                .span("api", SpanCategory.NETWORK, 0, 400)
                .span("db", SpanCategory.DATABASE, 400, 300)
                .span("ui", SpanCategory.UI_RENDER, 700, 200)
                .build()
        val ids = analyzer().analyze(trace).ids()
        assertTrue("timing.database_significant" in ids)
        assertFalse("timing.dominant" in ids)
    }

    @Test
    fun disabledRulesAndSeverityOverridesAreHonoured() {
        val config =
            DiagnosticConfig(
                disabledRules = setOf("timing.dominant"),
                severityOverrides = mapOf("network.slow" to DiagnosticSeverity.Error),
            )
        val trace = TraceBuilder().span("api", SpanCategory.NETWORK, 0, 900, metadata = network()).build()
        val report = analyzer(config).analyze(trace)
        assertFalse("timing.dominant" in report.ids())
        assertEquals(DiagnosticSeverity.Error, report.findings.single { it.ruleId == "network.slow" }.severity)
    }

    @Test
    fun customRulesRunAndThrowingRulesAreIsolated() {
        val custom =
            object : DiagnosticRule {
                override val id = "custom.always"

                override fun evaluate(context: DiagnosticContext) =
                    listOf(
                        DiagnosticFinding("custom.always", DiagnosticSeverity.Info, DiagnosticCategory("Custom"), "Custom", listOf("seen")),
                    )
            }
        val broken =
            object : DiagnosticRule {
                override val id = "custom.broken"

                override fun evaluate(context: DiagnosticContext): List<DiagnosticFinding> = error("boom")
            }
        val report = DiagnosticAnalyzer(rules = DiagnosticRules.default() + custom + broken).analyze(addToCartDemo())
        assertTrue("custom.always" in report.ids())
        assertEquals(listOf("custom.broken"), report.ruleFailures.map { it.ruleId })
    }

    @Test
    fun healthyFastJourneyProducesNoFindings() {
        val trace =
            TraceBuilder()
                .span("vm", SpanCategory.VIEW_MODEL, 0, 20)
                .span("api", SpanCategory.NETWORK, 20, 120, metadata = network(status = 200))
                .span("db", SpanCategory.DATABASE, 140, 15)
                .span("ui", SpanCategory.UI_RENDER, 155, 25)
                .build()
        val report = analyzer(DiagnosticConfig(slowJourney = 2000.milliseconds)).analyze(trace)
        assertTrue(report.findings.none { it.severity != DiagnosticSeverity.Info }, report.ids().toString())
    }

    @Test
    fun findingsAreOrderedBySeverity() {
        val trace =
            TraceBuilder()
                .span("api", SpanCategory.NETWORK, 0, 900, status = SpanStatus.FAILED, metadata = network(failure = "TIMEOUT"))
                .build()
        val severities = analyzer().analyze(trace).findings.map { it.severity }
        assertEquals(severities.sortedDescending(), severities)
    }
}
