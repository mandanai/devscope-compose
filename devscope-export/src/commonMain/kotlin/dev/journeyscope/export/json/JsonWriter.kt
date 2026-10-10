package dev.journeyscope.export.json

internal object JsonWriter {
    fun write(value: JsonValue): String = StringBuilder().also { append(it, value) }.toString()

    private fun append(
        out: StringBuilder,
        value: JsonValue,
    ) {
        when (value) {
            JsonValue.Null -> {
                out.append("null")
            }

            is JsonValue.Bool -> {
                out.append(value.value)
            }

            is JsonValue.Integer -> {
                out.append(value.value)
            }

            is JsonValue.Decimal -> {
                out.append(if (value.value.isFinite()) value.value.toString() else "null")
            }

            is JsonValue.Text -> {
                quote(out, value.value)
            }

            is JsonValue.Array -> {
                out.append('[')
                value.items.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    append(out, item)
                }
                out.append(']')
            }

            is JsonValue.Obj -> {
                out.append('{')
                var first = true
                for ((key, item) in value.fields) {
                    if (!first) out.append(',')
                    first = false
                    quote(out, key)
                    out.append(':')
                    append(out, item)
                }
                out.append('}')
            }
        }
    }

    private fun quote(
        out: StringBuilder,
        text: String,
    ) {
        out.append('"')
        for (c in text) {
            when {
                c == '"' -> {
                    out.append("\\\"")
                }

                c == '\\' -> {
                    out.append("\\\\")
                }

                c == '\n' -> {
                    out.append("\\n")
                }

                c == '\r' -> {
                    out.append("\\r")
                }

                c == '\t' -> {
                    out.append("\\t")
                }

                c < ' ' || c == ' ' || c == ' ' -> {
                    out.append("\\u")
                    out.append(c.code.toString(HEX_RADIX).padStart(HEX_WIDTH, '0'))
                }

                else -> {
                    out.append(c)
                }
            }
        }
        out.append('"')
    }

    private const val HEX_RADIX = 16
    private const val HEX_WIDTH = 4
}
