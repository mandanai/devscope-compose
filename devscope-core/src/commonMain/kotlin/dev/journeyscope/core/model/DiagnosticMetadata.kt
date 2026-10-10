package dev.journeyscope.core.model

/** Primitive metadata value; text is bounded by the configured metadata budget. */

public sealed class MetadataValue {
    public data class Text(
        val value: String,
    ) : MetadataValue()

    public data class Integer(
        val value: Long,
    ) : MetadataValue()

    public data class Decimal(
        val value: Double,
    ) : MetadataValue()

    public data class Flag(
        val value: Boolean,
    ) : MetadataValue()

    internal val size: Int
        get() =
            when (this) {
                is Text -> value.length
                is Integer, is Decimal -> NUMBER_SIZE
                is Flag -> FLAG_SIZE
            }

    private companion object {
        const val NUMBER_SIZE = 8
        const val FLAG_SIZE = 1
    }
}

/** Only primitive values are accepted, so arbitrary objects can never leak through `toString()`. */
public data class DiagnosticMetadata(
    val entries: Map<String, MetadataValue> = emptyMap(),
    /** True when sanitization dropped or shortened entries. */
    val truncated: Boolean = false,
) {
    public val isEmpty: Boolean get() = entries.isEmpty()

    public operator fun get(key: String): MetadataValue? = entries[key]

    /** Collects entries for [diagnosticMetadata]; putting an existing key replaces its value. */

    public class Builder internal constructor() {
        private val entries = LinkedHashMap<String, MetadataValue>()

        public fun put(
            key: String,
            value: String,
        ) {
            entries[key] = MetadataValue.Text(value)
        }

        public fun put(
            key: String,
            value: Long,
        ) {
            entries[key] = MetadataValue.Integer(value)
        }

        public fun put(
            key: String,
            value: Int,
        ) {
            entries[key] = MetadataValue.Integer(value.toLong())
        }

        public fun put(
            key: String,
            value: Double,
        ) {
            entries[key] = MetadataValue.Decimal(value)
        }

        public fun put(
            key: String,
            value: Boolean,
        ) {
            entries[key] = MetadataValue.Flag(value)
        }

        internal fun build(): DiagnosticMetadata = DiagnosticMetadata(entries.toMap())
    }

    public companion object {
        public val Empty: DiagnosticMetadata = DiagnosticMetadata()
    }
}

public fun diagnosticMetadata(block: DiagnosticMetadata.Builder.() -> Unit): DiagnosticMetadata = DiagnosticMetadata.Builder().apply(block).build()
