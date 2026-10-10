package dev.journeyscope.ui.model

private const val NANOS_PER_MILLI = 1_000_000L
private const val SUB_MILLI_THRESHOLD_NANOS = 500_000L

internal fun Long.nanosToMillis(): Long = (this + SUB_MILLI_THRESHOLD_NANOS) / NANOS_PER_MILLI

internal fun groupThousands(value: Long): String {
    val digits = value.toString()
    return buildString {
        digits.forEachIndexed { index, char ->
            if (index > 0 && char.isDigit() && digits[index - 1].isDigit() && (digits.length - index) % 3 == 0) append(',')
            append(char)
        }
    }
}

internal fun formatDuration(nanos: Long): String =
    when {
        nanos <= 0L -> "0 ms"
        nanos < SUB_MILLI_THRESHOLD_NANOS -> "<1 ms"
        else -> "${groupThousands(nanos.nanosToMillis())} ms"
    }

internal fun spokenDuration(nanos: Long): String =
    when {
        nanos <= 0L -> "0 milliseconds"
        nanos < SUB_MILLI_THRESHOLD_NANOS -> "less than 1 millisecond"
        else -> nanos.nanosToMillis().let { if (it == 1L) "1 millisecond" else "$it milliseconds" }
    }

internal fun percentOf(
    part: Long,
    total: Long,
): Int = if (total <= 0L) 0 else (part * 100.0 / total + 0.5).toInt()
