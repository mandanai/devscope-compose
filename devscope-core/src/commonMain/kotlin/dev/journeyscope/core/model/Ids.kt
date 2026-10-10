package dev.journeyscope.core.model

import kotlin.jvm.JvmInline

@JvmInline
public value class TraceId(
    public val value: String,
) {
    override fun toString(): String = value
}

@JvmInline
public value class SpanId(
    public val value: String,
) {
    override fun toString(): String = value
}

@JvmInline
public value class EventId(
    public val value: String,
) {
    override fun toString(): String = value
}
