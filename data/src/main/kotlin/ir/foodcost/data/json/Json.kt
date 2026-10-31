package ir.foodcost.data.json

import java.math.BigDecimal

/**
 * A small JSON model for backups and sync, with no dependencies.
 * Numbers keep their exact text, so amounts and quantities survive a round trip digit for digit.
 */
sealed interface JsonValue

data class JsonObject(val fields: Map<String, JsonValue>) : JsonValue {
    operator fun get(key: String): JsonValue? = fields[key]

    fun has(key: String): Boolean = fields.containsKey(key)

    fun obj(key: String): JsonObject = required(key).asObject(key)

    fun array(key: String): List<JsonValue> = (required(key) as? JsonArray ?: wrongType(key, "an array")).items

    fun string(key: String): String = (required(key) as? JsonString ?: wrongType(key, "a string")).value

    fun stringOrNull(key: String): String? = when (val v = fields[key]) {
        null, JsonNull -> null
        is JsonString -> v.value
        else -> wrongType(key, "a string")
    }

    fun decimal(key: String): BigDecimal = required(key).asDecimal(key)

    fun decimalOrNull(key: String): BigDecimal? = fields[key]?.takeUnless { it == JsonNull }?.asDecimal(key)

    fun long(key: String): Long = decimal(key).longValueExact()

    fun longOrNull(key: String): Long? = decimalOrNull(key)?.longValueExact()

    fun int(key: String): Int = decimal(key).intValueExact()

    fun bool(key: String): Boolean = (required(key) as? JsonBool ?: wrongType(key, "true or false")).value

    private fun required(key: String): JsonValue = fields[key] ?: throw JsonException("Missing \"$key\"")
}

data class JsonArray(val items: List<JsonValue>) : JsonValue

data class JsonString(val value: String) : JsonValue

data class JsonNumber(val text: String) : JsonValue {
    fun toBigDecimal(): BigDecimal = BigDecimal(text)
}

data class JsonBool(val value: Boolean) : JsonValue

data object JsonNull : JsonValue

class JsonException(message: String) : IllegalArgumentException(message)

fun JsonValue.asObject(what: String = "value"): JsonObject = this as? JsonObject ?: wrongType(what, "an object")

fun JsonValue.asString(what: String = "value"): String = (this as? JsonString ?: wrongType(what, "a string")).value

fun JsonValue.asDecimal(what: String = "value"): BigDecimal = (this as? JsonNumber ?: wrongType(what, "a number")).toBigDecimal()

fun JsonValue.asBool(what: String = "value"): Boolean = (this as? JsonBool ?: wrongType(what, "true or false")).value

fun JsonValue.asArray(what: String = "value"): List<JsonValue> = (this as? JsonArray ?: wrongType(what, "an array")).items

private fun wrongType(what: String, expected: String): Nothing = throw JsonException("\"$what\" must be $expected")

/** An object from key-value pairs; a Kotlin null value leaves the key out, [JsonNull] writes null. */
fun jsonObject(vararg pairs: Pair<String, JsonValue?>): JsonObject {
    val fields = LinkedHashMap<String, JsonValue>()
    for ((key, value) in pairs) if (value != null) fields[key] = value
    return JsonObject(fields)
}

fun jsonArray(items: List<JsonValue>): JsonArray = JsonArray(items)

val String.json: JsonString get() = JsonString(this)
val Boolean.json: JsonBool get() = JsonBool(this)
val Long.json: JsonNumber get() = JsonNumber(toString())
val Int.json: JsonNumber get() = JsonNumber(toString())
val BigDecimal.json: JsonNumber get() = JsonNumber(toPlainString())

object Json {
    fun write(value: JsonValue): String = StringBuilder().also { write(value, it) }.toString()

    fun parse(text: String): JsonValue = Parser(text).document()

    private fun write(value: JsonValue, out: StringBuilder) {
        when (value) {
            is JsonObject -> {
                out.append('{')
                var first = true
                for ((key, item) in value.fields) {
                    if (!first) out.append(',')
                    first = false
                    writeString(key, out)
                    out.append(':')
                    write(item, out)
                }
                out.append('}')
            }
            is JsonArray -> {
                out.append('[')
                value.items.forEachIndexed { i, item ->
                    if (i > 0) out.append(',')
                    write(item, out)
                }
                out.append(']')
            }
            is JsonString -> writeString(value.value, out)
            is JsonNumber -> out.append(value.text)
            is JsonBool -> out.append(value.value)
            JsonNull -> out.append("null")
        }
    }

    private fun writeString(s: String, out: StringBuilder) {
        out.append('"')
        for (ch in s) {
            when {
                ch == '"' -> out.append("\\\"")
                ch == '\\' -> out.append("\\\\")
                ch == '\n' -> out.append("\\n")
                ch == '\r' -> out.append("\\r")
                ch == '\t' -> out.append("\\t")
                ch < ' ' || ch == ' ' || ch == ' ' -> out.append("\\u").append("%04x".format(ch.code))
                else -> out.append(ch)
            }
        }
        out.append('"')
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun document(): JsonValue {
            val value = value()
            skipSpace()
            if (i != s.length) fail("Unexpected text")
            return value
        }

        private fun value(): JsonValue {
            skipSpace()
            if (i >= s.length) fail("Unexpected end")
            return when (s[i]) {
                '{' -> obj()
                '[' -> array()
                '"' -> JsonString(string())
                't' -> literal("true", JsonBool(true))
                'f' -> literal("false", JsonBool(false))
                'n' -> literal("null", JsonNull)
                else -> number()
            }
        }

        private fun obj(): JsonObject {
            i++
            val fields = LinkedHashMap<String, JsonValue>()
            skipSpace()
            if (peek() == '}') {
                i++
                return JsonObject(fields)
            }
            while (true) {
                skipSpace()
                if (peek() != '"') fail("Expected a key")
                val key = string()
                skipSpace()
                expect(':')
                val item = value()
                if (fields.put(key, item) != null) fail("Duplicate key \"$key\"")
                skipSpace()
                when (next()) {
                    ',' -> continue
                    '}' -> return JsonObject(fields)
                    else -> fail("Expected , or }")
                }
            }
        }

        private fun array(): JsonArray {
            i++
            val items = ArrayList<JsonValue>()
            skipSpace()
            if (peek() == ']') {
                i++
                return JsonArray(items)
            }
            while (true) {
                items += value()
                skipSpace()
                when (next()) {
                    ',' -> continue
                    ']' -> return JsonArray(items)
                    else -> fail("Expected , or ]")
                }
            }
        }

        private fun string(): String {
            i++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) fail("Unterminated string")
                val c = s[i++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (i >= s.length) fail("Unterminated escape")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) fail("Short \\u escape")
                                val code = s.substring(i, i + 4).toIntOrNull(16) ?: fail("Bad \\u escape")
                                sb.append(code.toChar())
                                i += 4
                            }
                            else -> fail("Bad escape \\$e")
                        }
                    }
                    c < ' ' -> fail("Control character in a string")
                    else -> sb.append(c)
                }
            }
        }

        private fun number(): JsonNumber {
            val start = i
            if (peek() == '-') i++
            when {
                peek() == '0' -> i++
                peek() in '1'..'9' -> while (peek() in '0'..'9') i++
                else -> fail("Unexpected character")
            }
            if (peek() == '.') {
                i++
                if (peek() !in '0'..'9') fail("Bad number")
                while (peek() in '0'..'9') i++
            }
            if (peek() == 'e' || peek() == 'E') {
                i++
                if (peek() == '+' || peek() == '-') i++
                if (peek() !in '0'..'9') fail("Bad number")
                while (peek() in '0'..'9') i++
            }
            return JsonNumber(s.substring(start, i))
        }

        private fun literal(word: String, value: JsonValue): JsonValue {
            if (!s.startsWith(word, i)) fail("Unexpected word")
            i += word.length
            return value
        }

        private fun skipSpace() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\r' || s[i] == '\n')) i++
        }

        private fun peek(): Char = if (i < s.length) s[i] else '\u0000'

        private fun next(): Char = if (i < s.length) s[i++] else fail("Unexpected end")

        private fun expect(c: Char) {
            if (next() != c) fail("Expected '$c'")
        }

        private fun fail(message: String): Nothing = throw JsonException("$message at position $i")
    }
}
