package org.cirjson.cirjackson.core.cirjson.async

import org.cirjson.cirjackson.core.AsyncTestBase
import org.cirjson.cirjackson.core.CirJsonToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AsyncRootNumbersTest : AsyncTestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testRootIntegers() {
        val baseValues = arrayOf("10" to 10, "0" to 0, "-1234" to -1234)
        val indentedValues = baseValues.flatMap { (valueString, value) ->
            listOf(valueString to value, "    $valueString" to value, "$valueString    " to value,
                    "    $valueString    " to value)
        }
        val values = indentedValues.map { (valueString, value) -> utf8Bytes(valueString) to value }

        for (mode in ALL_ASYNC_MODES) {
            for ((doc, value) in values) {
                rootIntegers(doc, value, mode, 0, 99)
                rootIntegers(doc, value, mode, 0, 5)
                rootIntegers(doc, value, mode, 0, 3)
                rootIntegers(doc, value, mode, 0, 2)
                rootIntegers(doc, value, mode, 0, 1)

                rootIntegers(doc, value, mode, 1, 99)
                rootIntegers(doc, value, mode, 1, 3)
                rootIntegers(doc, value, mode, 1, 1)
            }
        }
    }

    private fun rootIntegers(doc: ByteArray, value: Int, mode: Int, padding: Int, bytesPerFeed: Int) {
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertNull(parser.currentToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(value, parser.intValue, "doc: \"${String(doc)}\", value: $value")
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testRootDoublesSimple() {
        val baseValues = arrayOf("10.0" to 10.0, "-1234.25" to -1234.25, "0.125" to 0.125)
        val indentedValues = baseValues.flatMap { (valueString, value) ->
            listOf(valueString to value, "    $valueString" to value, "$valueString    " to value,
                    "    $valueString    " to value)
        }
        val values = indentedValues.map { (valueString, value) -> utf8Bytes(valueString) to value }

        for (mode in ALL_ASYNC_MODES) {
            for ((doc, value) in values) {
                rootDoubles(doc, value, mode, 0, 99)
                rootDoubles(doc, value, mode, 0, 5)
                rootDoubles(doc, value, mode, 0, 3)
                rootDoubles(doc, value, mode, 0, 2)
                rootDoubles(doc, value, mode, 0, 1)

                rootDoubles(doc, value, mode, 1, 99)
                rootDoubles(doc, value, mode, 1, 3)
                rootDoubles(doc, value, mode, 1, 1)
            }
        }
    }

    @Test
    fun testRootDoublesScientific() {
        val baseValues = arrayOf("9e3" to 9e3, "9e-2" to 9e-2, "-12.5e3" to -12.5e3, "-12.5E3" to -12.5e3,
                "-12.5E-2" to -12.5e-2, "0e1" to 0e1)
        val indentedValues = baseValues.flatMap { (valueString, value) ->
            listOf(valueString to value, "    $valueString" to value, "$valueString    " to value,
                    "    $valueString    " to value)
        }
        val values = indentedValues.map { (valueString, value) -> utf8Bytes(valueString) to value }

        for (mode in ALL_ASYNC_MODES) {
            for ((doc, value) in values) {
                rootDoubles(doc, value, mode, 0, 99)
                rootDoubles(doc, value, mode, 0, 5)
                rootDoubles(doc, value, mode, 0, 3)
                rootDoubles(doc, value, mode, 0, 2)
                rootDoubles(doc, value, mode, 0, 1)

                rootDoubles(doc, value, mode, 1, 99)
                rootDoubles(doc, value, mode, 1, 3)
                rootDoubles(doc, value, mode, 1, 1)
            }
        }
    }

    private fun rootDoubles(doc: ByteArray, value: Double, mode: Int, padding: Int, bytesPerFeed: Int) {
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertNull(parser.currentToken())
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(value, parser.doubleValue, "doc: \"${String(doc)}\", value: $value")
        assertNull(parser.nextToken())
        parser.close()
    }

}