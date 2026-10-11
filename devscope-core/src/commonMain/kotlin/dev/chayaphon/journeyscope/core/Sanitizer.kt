package dev.chayaphon.journeyscope.core

import dev.chayaphon.journeyscope.core.model.DiagnosticMetadata
import dev.chayaphon.journeyscope.core.model.FailureInfo
import dev.chayaphon.journeyscope.core.model.MetadataValue

internal class Sanitizer(
    private val config: JourneyScopeConfig,
) {
    fun metadata(source: DiagnosticMetadata): DiagnosticMetadata {
        if (source.isEmpty) return source
        val result = LinkedHashMap<String, MetadataValue>()
        var remaining = config.maxMetadataSize
        var truncated = source.truncated
        for ((rawKey, rawValue) in source.entries) {
            if (result.size >= MAX_ENTRIES) {
                truncated = true
                break
            }
            val key = rawKey.take(MAX_KEY_LENGTH)
            val value =
                if (config.redactionPolicy.isSensitive(key)) {
                    MetadataValue.Text(config.redactionPolicy.replacement)
                } else {
                    rawValue
                }
            val budget = remaining - key.length
            if (budget < 0) {
                truncated = true
                break
            }
            val fitted = fit(value, budget)
            if (fitted !== value) truncated = true
            if (fitted == null) break
            result[key] = fitted
            remaining = budget - fitted.size
        }
        return DiagnosticMetadata(result, truncated)
    }

    fun label(name: String): String = name.take(MAX_LABEL_LENGTH)

    fun failure(throwable: Throwable): FailureInfo? {
        if (!config.recordFailures) return null
        val message =
            if (config.redactionPolicy.includeFailureMessages) {
                throwable.message?.take(config.maxMetadataSize)
            } else {
                null
            }
        val stack = if (config.captureStackTraces) throwable.stackTraceToString().take(config.maxMetadataSize) else null
        return FailureInfo(throwable::class.qualifiedName ?: throwable::class.simpleName ?: UNKNOWN_TYPE, message, stack)
    }

    private fun fit(
        value: MetadataValue,
        budget: Int,
    ): MetadataValue? =
        when {
            value.size <= budget -> value
            value is MetadataValue.Text -> MetadataValue.Text(value.value.take(budget))
            else -> null
        }

    private companion object {
        const val MAX_ENTRIES = 64
        const val MAX_KEY_LENGTH = 64
        const val MAX_LABEL_LENGTH = 256
        const val UNKNOWN_TYPE = "Throwable"
    }
}
