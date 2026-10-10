package dev.journeyscope.export.json

/** Minimal JSON tree used by the report reader and writer. */
public sealed class JsonValue {
    public data object Null : JsonValue()

    public data class Bool(
        val value: Boolean,
    ) : JsonValue()

    public data class Integer(
        val value: Long,
    ) : JsonValue()

    public data class Decimal(
        val value: Double,
    ) : JsonValue()

    public data class Text(
        val value: String,
    ) : JsonValue()

    public data class Array(
        val items: List<JsonValue>,
    ) : JsonValue()

    public data class Obj(
        val fields: Map<String, JsonValue>,
    ) : JsonValue()
}

/** Bounds enforced by the JSON reader against hostile input. */
public data class JsonLimits(
    val maxBytes: Int = 4 * 1024 * 1024,
    val maxDepth: Int = 16,
    val maxNodes: Int = 200_000,
    val maxStringLength: Int = 4096,
) {
    init {
        require(maxBytes > 0 && maxDepth > 0 && maxNodes > 0 && maxStringLength > 0) { "limits must be positive" }
    }
}

/** Thrown by the reader for malformed JSON or when a [JsonLimits] bound is exceeded. */
public class JsonException(
    message: String,
) : Exception(message)
