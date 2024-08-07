package org.cirjson.cirjackson.core.io

import org.cirjson.cirjackson.core.TestBase
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class BigDecimalParserTest : TestBase() {

    @Test
    fun testLongInvalidStringParse() {
        try {
            BigDecimalParser.parse(generateLongInvalidString())
            fail("expected NumberFormatException")
        } catch (e: NumberFormatException) {
            assertTrue(e.message!!.startsWith("Value \"AAAAA"), "exception message starts as expected?")
            assertTrue("truncated" in e.message!!, "exception message value contains truncated")
        }
    }

    @Test
    fun testLongInvalidStringFastParse() {
        try {
            BigDecimalParser.parseWithFastParser(generateLongInvalidString())
            fail("expected NumberFormatException")
        } catch (e: NumberFormatException) {
            assertTrue(e.message!!.startsWith("Value \"AAAAA"), "exception message starts as expected?")
            assertTrue("truncated" in e.message!!, "exception message value contains truncated")
        }
    }

    @Test
    fun testLongValidStringParse() {
        val num = generateLongValidString()
        val expected = BigDecimal(num)

        assertEquals(expected, BigDecimalParser.parse(num))
        assertEquals(expected, BigDecimalParser.parse(num.toCharArray()))
        assertEquals(expected, BigDecimalParser.parse(num.toCharArray(), 0, num.length))
    }

    @Test
    fun testLongValidStringFastParse() {
        val num = generateLongValidString()
        val expected = BigDecimal(num)

        assertEquals(expected, BigDecimalParser.parseWithFastParser(num))
        assertEquals(expected, BigDecimalParser.parseWithFastParser(num.toCharArray(), 0, num.length))
    }

    private fun generateLongInvalidString(): String {
        val length = 1500
        val stringBuilder = StringBuilder(length)

        for (i in 1..length) {
            stringBuilder.append('A')
        }

        return stringBuilder.toString()
    }

    private fun generateLongValidString(): String {
        val length = 500
        val stringBuilder = StringBuilder(length + 5)
        stringBuilder.append("0.")

        for (i in 1..length) {
            stringBuilder.append('0')
        }

        stringBuilder.append('1')
        return stringBuilder.toString()
    }

}