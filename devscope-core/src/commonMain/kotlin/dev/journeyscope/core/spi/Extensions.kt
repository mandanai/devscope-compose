package dev.journeyscope.core.spi

import dev.journeyscope.core.model.JourneyTrace
import dev.journeyscope.core.model.TraceId

public fun interface JourneyExporter {
    public suspend fun export(trace: JourneyTrace)
}

public interface JourneyStore {
    public suspend fun save(trace: JourneyTrace)

    public suspend fun load(id: TraceId): JourneyTrace?

    public suspend fun loadAll(): List<JourneyTrace>
}

/** Post-processing hook for finished traces; returning null discards the trace from further pipelines. */
public fun interface JourneyProcessor {
    public suspend fun process(trace: JourneyTrace): JourneyTrace?
}
