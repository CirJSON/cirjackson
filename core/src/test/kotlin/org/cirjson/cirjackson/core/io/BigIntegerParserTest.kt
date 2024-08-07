package org.cirjson.cirjackson.core.io

import org.cirjson.cirjackson.core.TestBase
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

class BigIntegerParserTest : TestBase() {

    @Test
    fun testFastParseBigIntegerFailsWithENotation() {
        val num = "2e308"
        assertFailsWith(NumberFormatException::class) { BigIntegerParser.parseWithFastParser(num) }
    }

    @Test
    fun testLongInvalidStringFastParseBigInteger() {
        try {
            BigIntegerParser.parseWithFastParser(generateLongInvalidString())
            fail("expected NumberFormatException")
        } catch (e: NumberFormatException) {
            assertTrue(e.message!!.startsWith("Value \"AAAAA"), "exception message starts as expected?")
            assertTrue("truncated" in e.message!!, "exception message value contains truncated")
            assertTrue("BigInteger" in e.message!!, "exception message value contains BigInteger")
        }
    }

    @Test
    fun testLongInvalidStringFastParseBigIntegerRadix() {
        try {
            BigIntegerParser.parseWithFastParser(generateLongInvalidString(), 8)
            fail("expected NumberFormatException")
        } catch (e: NumberFormatException) {
            assertTrue(e.message!!.startsWith("Value \"AAAAA"), "exception message starts as expected?")
            assertTrue("truncated" in e.message!!, "exception message value contains truncated")
            assertTrue("BigInteger" in e.message!!, "exception message value contains BigInteger")
        }
    }

    private fun generateLongInvalidString(): String {
        val length = 1500
        val stringBuilder = StringBuilder(length)

        for (i in 1..length) {
            stringBuilder.append('A')
        }

        return stringBuilder.toString()
    }

}