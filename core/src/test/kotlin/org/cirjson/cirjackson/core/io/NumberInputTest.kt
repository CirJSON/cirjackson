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

    @Test
    fun testIntParsing() {
        var testChars = "123456789".toCharArray()

        assertEquals(3, NumberInput.parseInt(testChars, 2, 1))
        assertEquals(123, NumberInput.parseInt(testChars, 0, 3))
        assertEquals(2345, NumberInput.parseInt(testChars, 1, 4))
        assertEquals(9, NumberInput.parseInt(testChars, 8, 1))
        assertEquals(456789, NumberInput.parseInt(testChars, 3, 6))
        assertEquals(23456, NumberInput.parseInt(testChars, 1, 5))
        assertEquals(123456789, NumberInput.parseInt(testChars, 0, 9))

        testChars = "32".toCharArray()
        assertEquals(32, NumberInput.parseInt(testChars, 0, 2))
        testChars = "189".toCharArray()
        assertEquals(189, NumberInput.parseInt(testChars, 0, 3))

        testChars = "10".toCharArray()
        assertEquals(10, NumberInput.parseInt(testChars, 0, 2))
        assertEquals(0, NumberInput.parseInt(testChars, 1, 1))
    }

    @Test
    fun testIntParsingWithStrings() {
        assertEquals(3, NumberInput.parseInt("3"))
        assertEquals(3, NumberInput.parseInt("+3"))
        assertEquals(0, NumberInput.parseInt("0"))
        assertEquals(-3, NumberInput.parseInt("-3"))
        assertEquals(27, NumberInput.parseInt("27"))
        assertEquals(-31, NumberInput.parseInt("-31"))
        assertEquals(271, NumberInput.parseInt("271"))
        assertEquals(-131, NumberInput.parseInt("-131"))
        assertEquals(2709, NumberInput.parseInt("2709"))
        assertEquals(-9999, NumberInput.parseInt("-9999"))
        assertEquals(Int.MIN_VALUE, NumberInput.parseInt("${Int.MIN_VALUE}"))
        assertEquals(Int.MAX_VALUE, NumberInput.parseInt("${Int.MAX_VALUE}"))
    }

    @Test
    fun testLongParsing() {
        val testChars = "123456789012345678".toCharArray()

        assertEquals(123456789012345678L, NumberInput.parseLong(testChars, 0, testChars.size))
    }

    @Test
    fun testLongParsingWithStrings() {
        assertEquals(3, NumberInput.parseLong("3"))
        assertEquals(3, NumberInput.parseLong("+3"))
        assertEquals(0, NumberInput.parseLong("0"))
        assertEquals(-3, NumberInput.parseLong("-3"))
        assertEquals(27, NumberInput.parseLong("27"))
        assertEquals(-31, NumberInput.parseLong("-31"))
        assertEquals(271, NumberInput.parseLong("271"))
        assertEquals(-131, NumberInput.parseLong("-131"))
        assertEquals(2709, NumberInput.parseLong("2709"))
        assertEquals(-9999, NumberInput.parseLong("-9999"))
        assertEquals(Long.MIN_VALUE, NumberInput.parseLong("${Long.MIN_VALUE}"))
        assertEquals(Long.MAX_VALUE, NumberInput.parseLong("${Long.MAX_VALUE}"))
        assertEquals(Int.MIN_VALUE - 1L, NumberInput.parseLong("${Int.MIN_VALUE - 1L}"))
        assertEquals(Int.MAX_VALUE + 1L, NumberInput.parseLong("${Int.MAX_VALUE + 1L}"))
    }

    @Test
    fun testLongBoundsChecks() {
        val minLong = Long.MIN_VALUE.toString().substring(1)
        val belowMinLong = BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE).toString().substring(1)
        val maxLong = Long.MAX_VALUE.toString()
        val aboveMaxLong = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE).toString()
        val otherValue = "1323372036854775807"
        val overflow = "9999999999999999999"

        assertTrue(NumberInput.inLongRange(minLong, true))
        assertTrue(NumberInput.inLongRange(maxLong, false))
        assertTrue(NumberInput.inLongRange(otherValue, true))
        assertTrue(NumberInput.inLongRange(otherValue, false))

        assertFalse(NumberInput.inLongRange(overflow, true))
        assertFalse(NumberInput.inLongRange(overflow, false))
        assertFalse(NumberInput.inLongRange(belowMinLong, true))
        assertFalse(NumberInput.inLongRange(aboveMaxLong, false))

        var chars = minLong.toCharArray()
        assertTrue(NumberInput.inLongRange(chars, 0, chars.size, true))
        chars = maxLong.toCharArray()
        assertTrue(NumberInput.inLongRange(chars, 0, chars.size, false))
        chars = otherValue.toCharArray()
        assertTrue(NumberInput.inLongRange(chars, 0, chars.size, true))
        assertTrue(NumberInput.inLongRange(chars, 0, chars.size, false))

        chars = belowMinLong.toCharArray()
        assertFalse(NumberInput.inLongRange(chars, 0, chars.size, true))
        chars = aboveMaxLong.toCharArray()
        assertFalse(NumberInput.inLongRange(chars, 0, chars.size, false))
        chars = overflow.toCharArray()
        assertFalse(NumberInput.inLongRange(chars, 0, chars.size, true))
        assertFalse(NumberInput.inLongRange(chars, 0, chars.size, false))
    }

    @Test
    fun testIntOverflow() {
        try {
            NumberInput.parseInt("2147483648")
            fail("Should have thrown an exception")
        } catch (e: NumberFormatException) {
            verifyException(e, "For input string: \"2147483648\"")
        }

        try {
            NumberInput.parseInt("-2147483649")
            fail("Should have thrown an exception")
        } catch (e: NumberFormatException) {
            verifyException(e, "For input string: \"-2147483649\"")
        }
    }

    @Test
    fun testLongOverflow() {
        try {
            NumberInput.parseInt("9223372036854775808")
            fail("Should have thrown an exception")
        } catch (e: NumberFormatException) {
            verifyException(e, "For input string: \"9223372036854775808\"")
        }

        try {
            NumberInput.parseInt("-9223372036854775809")
            fail("Should have thrown an exception")
        } catch (e: NumberFormatException) {
            verifyException(e, "For input string: \"-9223372036854775809\"")
        }
    }

}