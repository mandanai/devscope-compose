package dev.chayaphon.journeyscope.export.schema

/** Identity of the exported report format. */
public object TraceSchema {
    public const val NAME: String = "journeyscope.trace"
    public const val VERSION: Int = 1
    public const val FILE_EXTENSION: String = "jtrace.json"
    public const val MIME_TYPE: String = "application/json"
}

internal object Keys {
    const val SCHEMA = "schema"
    const val VERSION = "schemaVersion"
    const val SDK_VERSION = "sdkVersion"
    const val EXPORTED_AT = "exportedAtWallMillis"
    const val PLATFORM = "platform"
    const val APP_VERSION = "appVersion"
    const val CONFIG = "config"
    const val TRACES = "traces"

    const val ID = "id"
    const val NAME = "name"
    const val STATUS = "status"
    const val START_WALL = "startWallMillis"
    const val END_WALL = "endWallMillis"
    const val DURATION = "durationNanos"
    const val DROPPED = "droppedItems"
    const val FAILURE = "failure"
    const val FAILURE_TYPE = "type"
    const val METADATA = "metadata"
    const val METADATA_TRUNCATED = "metadataTruncated"
    const val SPANS = "spans"
    const val EVENTS = "events"
    const val FINDINGS = "findings"

    const val PARENT_ID = "parentId"
    const val SPAN_ID = "spanId"
    const val CATEGORY = "category"
    const val SEQUENCE = "sequence"
    const val START_OFFSET = "startOffsetNanos"
    const val END_OFFSET = "endOffsetNanos"
    const val OFFSET = "offsetNanos"
    const val WALL = "wallMillis"
    const val ENDED_BY_SYSTEM = "endedBySystem"

    const val RULE = "ruleId"
    const val SEVERITY = "severity"
    const val TITLE = "title"

    val ENVELOPE = setOf(SCHEMA, VERSION, SDK_VERSION, EXPORTED_AT, PLATFORM, APP_VERSION, CONFIG, TRACES)
    val TRACE = setOf(ID, NAME, STATUS, PLATFORM, START_WALL, END_WALL, DURATION, DROPPED, FAILURE, METADATA, METADATA_TRUNCATED, SPANS, EVENTS, FINDINGS)
    val SPAN = setOf(ID, PARENT_ID, NAME, CATEGORY, SEQUENCE, START_WALL, START_OFFSET, END_OFFSET, STATUS, FAILURE, ENDED_BY_SYSTEM, METADATA, METADATA_TRUNCATED)
    val EVENT = setOf(ID, SPAN_ID, NAME, CATEGORY, SEQUENCE, OFFSET, WALL, METADATA, METADATA_TRUNCATED)
    val FINDING = setOf(RULE, SEVERITY, TITLE, SPAN_ID)
}
