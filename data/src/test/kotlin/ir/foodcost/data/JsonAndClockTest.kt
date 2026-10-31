package ir.foodcost.data

import ir.foodcost.core.bd
import ir.foodcost.data.clock.Hlc
import ir.foodcost.data.clock.HybridClock
import ir.foodcost.data.json.Json
import ir.foodcost.data.json.JsonException
import ir.foodcost.data.json.JsonNull
import ir.foodcost.data.json.JsonNumber
import ir.foodcost.data.json.JsonObject
import ir.foodcost.data.json.json
import ir.foodcost.data.json.jsonArray
import ir.foodcost.data.json.jsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JsonAndClockTest {

    @Test
    fun `text, numbers and nesting survive a round trip exactly`() {
        val value = jsonObject(
            "name" to "چلوکباب \"ویژه\"\nبا\tکره \\ و 🍢".json,
            "control" to "\u0001\u001f".json,
            "big" to "12345678901234567890.123456789".bd.json,
            "list" to jsonArray(listOf(JsonNull, true.json, 0.json, JsonNumber("-1.5e3"))),
            "empty" to jsonObject(),
        )
        val parsed = Json.parse(Json.write(value))
        assertEquals(value, parsed)
        assertEquals("12345678901234567890.123456789".bd, (parsed as JsonObject).decimal("big"))
    }

    @Test
    fun `escapes and spaces are read`() {
        val parsed = Json.parse(" { \"a\" : \"\\u0041\\/\\u06A9\" , \"b\" : [ ] } ") as JsonObject
        assertEquals("A/ک", parsed.string("a"))
        assertEquals(emptyList(), parsed.array("b"))
    }

    @Test
    fun `broken JSON is refused`() {
        for (bad in listOf("{\"a\":1,}", "[1 2]", "\"open", "{\"a\":1,\"a\":2}", "01", "tru", "{} x", "\"\u0001\"", "")) {
            assertFailsWith<JsonException>(bad) { Json.parse(bad) }
        }
    }

    @Test
    fun `timestamps keep rising when the phone's clock is set back`() {
        val wall = FakeWall(5_000)
        val clock = HybridClock("A", wall)
        val first = clock.now()
        wall.now = 1_000
        val second = clock.now()
        val third = clock.now()
        assertTrue(first < second && second < third)
        assertEquals(5_000, third.wallMillis)
    }

    @Test
    fun `after a later timestamp arrives, local ones sort after it`() {
        val clock = HybridClock("A", FakeWall(1_000))
        val remote = Hlc(9_000, 3, "B")
        clock.receive(remote)
        assertTrue(clock.now() > remote)
    }

    @Test
    fun `timestamps order by time, then counter, then device`() {
        assertTrue(Hlc(1, 5, "Z") < Hlc(2, 0, "A"))
        assertTrue(Hlc(2, 0, "Z") < Hlc(2, 1, "A"))
        assertTrue(Hlc(2, 1, "A") < Hlc(2, 1, "B"))
    }
}
