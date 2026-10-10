package dev.journeyscope.export.json

/** Strict RFC 8259 parser with hard limits; input is untrusted and never evaluated. */
internal class JsonReader private constructor(
    private val text: String,
    private val limits: JsonLimits,
) {
    private var pos = 0
    private var nodes = 0

    private fun parseDocument(): JsonValue {
        skipWhitespace()
        val value = parseValue(depth = 1)
        skipWhitespace()
        if (pos != text.length) fail("trailing content")
        return value
    }

    private fun parseValue(depth: Int): JsonValue {
        if (depth > limits.maxDepth) fail("nesting exceeds ${limits.maxDepth}")
        if (++nodes > limits.maxNodes) fail("more than ${limits.maxNodes} values")
        if (pos >= text.length) fail("unexpected end")
        return when (val c = text[pos]) {
            '{' -> parseObject(depth)
            '[' -> parseArray(depth)
            '"' -> JsonValue.Text(parseString())
            't' -> literal("true", JsonValue.Bool(true))
            'f' -> literal("false", JsonValue.Bool(false))
            'n' -> literal("null", JsonValue.Null)
            else -> if (c == '-' || c in '0'..'9') parseNumber() else fail("unexpected '$c'")
        }
    }

    private fun parseObject(depth: Int): JsonValue {
        pos++
        val fields = LinkedHashMap<String, JsonValue>()
        skipWhitespace()
        if (peek() == '}') {
            pos++
            return JsonValue.Obj(fields)
        }
        while (true) {
            skipWhitespace()
            if (peek() != '"') fail("expected key")
            val key = parseString()
            if (key in fields) fail("duplicate key")
            skipWhitespace()
            expect(':')
            skipWhitespace()
            fields[key] = parseValue(depth + 1)
            skipWhitespace()
            when (peek()) {
                ',' -> {
                    pos++
                }

                '}' -> {
                    pos++
                    return JsonValue.Obj(fields)
                }

                else -> {
                    fail("expected ',' or '}'")
                }
            }
        }
    }

    private fun parseArray(depth: Int): JsonValue {
        pos++
        val items = ArrayList<JsonValue>()
        skipWhitespace()
        if (peek() == ']') {
            pos++
            return JsonValue.Array(items)
        }
        while (true) {
            skipWhitespace()
            items += parseValue(depth + 1)
            skipWhitespace()
            when (peek()) {
                ',' -> {
                    pos++
                }

                ']' -> {
                    pos++
                    return JsonValue.Array(items)
                }

                else -> {
                    fail("expected ',' or ']'")
                }
            }
        }
    }

    private fun parseString(): String {
        pos++
        val out = StringBuilder()
        while (true) {
            if (pos >= text.length) fail("unterminated string")
            val c = text[pos++]
            when {
                c == '"' -> return out.toString()
                c == '\\' -> out.append(parseEscape())
                c < ' ' -> fail("control character in string")
                else -> out.append(c)
            }
            if (out.length > limits.maxStringLength) fail("string exceeds ${limits.maxStringLength}")
        }
    }

    private fun parseEscape(): Char {
        if (pos >= text.length) fail("unterminated escape")
        return when (val e = text[pos++]) {
            '"' -> {
                '"'
            }

            '\\' -> {
                '\\'
            }

            '/' -> {
                '/'
            }

            'b' -> {
                '\b'
            }

            'f' -> {
                '\u000C'
            }

            'n' -> {
                '\n'
            }

            'r' -> {
                '\r'
            }

            't' -> {
                '\t'
            }

            'u' -> {
                if (pos + HEX_WIDTH > text.length) fail("bad unicode escape")
                val code = text.substring(pos, pos + HEX_WIDTH).toIntOrNull(HEX_RADIX) ?: fail("bad unicode escape")
                pos += HEX_WIDTH
                code.toChar()
            }

            else -> {
                fail("bad escape '$e'")
            }
        }
    }

    private fun parseNumber(): JsonValue {
        val start = pos
        if (peek() == '-') pos++
        if (peek() == '0') {
            pos++
        } else {
            digits()
        }
        var decimal = false
        if (peek() == '.') {
            decimal = true
            pos++
            digits()
        }
        if (peek() == 'e' || peek() == 'E') {
            decimal = true
            pos++
            if (peek() == '+' || peek() == '-') pos++
            digits()
        }
        val raw = text.substring(start, pos)
        if (!decimal) raw.toLongOrNull()?.let { return JsonValue.Integer(it) }
        val value = raw.toDoubleOrNull() ?: fail("bad number")
        if (!value.isFinite()) fail("non-finite number")
        return JsonValue.Decimal(value)
    }

    private fun digits() {
        val start = pos
        while (pos < text.length && text[pos] in '0'..'9') pos++
        if (pos == start) fail("expected digit")
    }

    private fun literal(
        word: String,
        value: JsonValue,
    ): JsonValue {
        if (!text.startsWith(word, pos)) fail("bad literal")
        pos += word.length
        return value
    }

    private fun skipWhitespace() {
        while (pos < text.length && (text[pos] == ' ' || text[pos] == '\n' || text[pos] == '\r' || text[pos] == '\t')) pos++
    }

    private fun peek(): Char = if (pos < text.length) text[pos] else '\u0000'

    private fun expect(c: Char) {
        if (peek() != c) fail("expected '$c'")
        pos++
    }

    private fun fail(message: String): Nothing = throw JsonException("$message at $pos")

    companion object {
        private const val HEX_WIDTH = 4
        private const val HEX_RADIX = 16

        fun parse(
            text: String,
            limits: JsonLimits,
        ): JsonValue {
            if (text.length > limits.maxBytes) throw JsonException("document exceeds ${limits.maxBytes} bytes")
            return JsonReader(text, limits).parseDocument()
        }
    }
}
