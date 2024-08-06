package org.cirjson.cirjackson.core.io

import org.cirjson.cirjackson.core.TestBase
import java.math.BigInteger
import kotlin.test.*

class NumberInputTest : TestBase() {

    @Test
    fun testNastySmallDouble() {
        val double = "2.2250738585072012e-308"
        assertEquals(double.toDouble(), NumberInput.parseDouble(double, false))
        assertEquals(double.toDouble(), NumberInput.parseDouble(double, true))
    }

    @Test
    fun testParseFloat() {
        var float = "1.199999988079071"
        assertEquals(1.1999999f, NumberInput.parseFloat(float, false))
        assertEquals(1.1999999f, NumberInput.parseFloat(float, true))
        assertEquals(1.2f, NumberInput.parseDouble(float, false).toFloat())
        assertEquals(1.2f, NumberInput.parseDouble(float, true).toFloat())

        float = "7.006492321624086e-46"
        assertEquals("1.4E-45", NumberInput.parseFloat(float, false).toString())
        assertEquals("1.4E-45", NumberInput.parseFloat(float, true).toString())
    }

    @Test
    fun testParseLongBigInteger() {
        val stringBuilder = StringBuilder()

        for (i in 1..1000) {
            stringBuilder.append(7)
        }

        var test = stringBuilder.toString()
        assertEquals(BigInteger(test), NumberInput.parseBigInteger(test, false))
        assertEquals(BigInteger(test), NumberInput.parseBigInteger(test, true))

        for (i in 1..1000) {
            stringBuilder.append(7)
        }

        test = stringBuilder.toString()
        assertEquals(BigInteger(test), NumberInput.parseBigInteger(test, false))
        assertEquals(BigInteger(test), NumberInput.parseBigInteger(test, true))
    }

    @Test
    fun testParseBigIntegerWithRadix() {
        val value = "1ABCDEF"
        val radix = 16
        val expected = BigInteger(value, radix)
        assertEquals(expected, NumberInput.parseBigInteger(value, radix, false))
        assertEquals(expected, NumberInput.parseBigInteger(value, radix, true))
    }

    @Test
    fun testParseBigIntegerFailsWithENotation() {
        try {
            NumberInput.parseBigInteger("1e10", false)
            fail("expected NumberFormatException")
        } catch (e: NumberFormatException) {
            verifyException(e, "1e10")
        }
    }

    @Test
    fun testLooksLikeValidNumber() {
        assertTrue(NumberInput.looksLikeValidNumber("0"))
        assertTrue(NumberInput.looksLikeValidNumber("1"))
        assertTrue(NumberInput.looksLikeValidNumber("-1"))
        assertTrue(NumberInput.looksLikeValidNumber("+1")) // non-CirJSON
        assertTrue(NumberInput.looksLikeValidNumber("0001")) // non-CirJSON

        assertTrue(NumberInput.looksLikeValidNumber(".0")) // non-CirJSON
        assertTrue(NumberInput.looksLikeValidNumber(".01")) // non-CirJSON
        assertTrue(NumberInput.looksLikeValidNumber("-.01")) // non-CirJSON
        assertTrue(NumberInput.looksLikeValidNumber("+.01")) // non-CirJSON
        assertTrue(NumberInput.looksLikeValidNumber("-.0")) // non-CirJSON

        assertTrue(NumberInput.looksLikeValidNumber("0.01"))
        assertTrue(NumberInput.looksLikeValidNumber("-0.10"))
        assertTrue(NumberInput.looksLikeValidNumber("+0.25")) // non-CirJSON

        assertTrue(NumberInput.looksLikeValidNumber("+.01"))
        assertTrue(NumberInput.looksLikeValidNumber("-.0"))
        assertTrue(NumberInput.looksLikeValidNumber("-.0"))

        assertTrue(NumberInput.looksLikeValidNumber("1E10"))
        assertTrue(NumberInput.looksLikeValidNumber("-1E10"))
        assertTrue(NumberInput.looksLikeValidNumber("1e-10"))
        assertTrue(NumberInput.looksLikeValidNumber("1e+10"))
        assertTrue(NumberInput.looksLikeValidNumber("+1e+10"))
        assertTrue(NumberInput.looksLikeValidNumber("1.4E-45"))
        assertTrue(NumberInput.looksLikeValidNumber("1.4e+45"))

        assertFalse(NumberInput.looksLikeValidNumber(""))
        assertFalse(NumberInput.looksLikeValidNumber(" "))
        assertFalse(NumberInput.looksLikeValidNumber("   "))
        assertFalse(NumberInput.looksLikeValidNumber("."))
        assertFalse(NumberInput.looksLikeValidNumber("0."))
        assertFalse(NumberInput.looksLikeValidNumber("10_000"))
    }

}