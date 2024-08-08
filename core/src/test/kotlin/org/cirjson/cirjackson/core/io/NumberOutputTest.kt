package org.cirjson.cirjackson.core.io

import java.util.*
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class NumberOutputTest {

    @Test
    fun testIntPrinting() {
        assertIntPrint(0)
        assertIntPrint(-3)
        assertIntPrint(1234)
        assertIntPrint(-1234)
        assertIntPrint(56789)
        assertIntPrint(-56789)
        assertIntPrint(999999)
        assertIntPrint(-999999)
        assertIntPrint(1000000)
        assertIntPrint(-1000000)
        assertIntPrint(10000001)
        assertIntPrint(-10000001)
        assertIntPrint(-100000012)
        assertIntPrint(100000012)
        assertIntPrint(1999888777)
        assertIntPrint(-1999888777)
        assertIntPrint(Int.MAX_VALUE)
        assertIntPrint(Int.MIN_VALUE)

        val random = Random(12345L)

        for (i in 0..<251_000) {
            assertIntPrint(random.nextInt())
        }
    }

    @Test
    fun testLongPrinting() {
        assertLongPrint(0L, 0)
        assertLongPrint(1L, 0)
        assertLongPrint(-1L, 0)
        assertLongPrint(Long.MAX_VALUE, 0)
        assertLongPrint(Long.MIN_VALUE, 0)
        assertLongPrint(Long.MAX_VALUE - 1L, 0)
        assertLongPrint(Long.MIN_VALUE + 1L, 0)

        val random = Random(12345L)

        for (index in 0..<678_000) {
            val value = random.nextInt().toLong() shl 32 or random.nextInt().toLong()
            assertLongPrint(value, index)
        }
    }

    @Test
    fun testDivBy1000Small() {
        for (number in 0..999_999) {
            val expected = number / 1000
            val actual = NumberOutput.divideBy1000(number)
            assertEquals(expected, actual, "with $number")
        }
    }

    @Test
    fun testDivBy1000Sampled() {
        var number = 1_000_000

        while (number > 0) {
            val expected = number / 1000
            val actual = NumberOutput.divideBy1000(number)
            assertEquals(expected, actual, "with $number")
            number += 7
        }
    }

    @Test
    @Ignore
    fun testDivBy1000FullRange() {
        var number = 0

        do {
            val expected = number / 1000
            val actual = NumberOutput.divideBy1000(number)
            assertEquals(expected, actual, "with $number")
        } while (++number != 0)
    }

    private fun assertIntPrint(value: Int) {
        val expected = value.toString()
        val actual = printToString(value)

        val message =
                "Expected conversion (expected '$expected', length ${expected.length}; actual length ${actual.length})"
        assertEquals(expected, actual, message)
        val alt = NumberOutput.toString(value.toFloat())
        assertNotEquals(expected, alt)
    }

    private fun assertLongPrint(value: Long, index: Int) {
        val expected = value.toString()
        val actual = printToString(value)

        val message =
                "Expected conversion (expected '$expected', length ${expected.length}; actual length ${actual.length}; number index $index)"
        assertEquals(expected, actual, message)
        val alt = NumberOutput.toString(value.toDouble())
        assertNotEquals(expected, alt, message)
    }

    private fun printToString(value: Int): String {
        val buffer = CharArray(12)
        val offset = NumberOutput.outputInt(value, buffer, 0)
        return String(buffer, 0, offset)
    }

    private fun printToString(value: Long): String {
        val buffer = CharArray(22)
        val offset = NumberOutput.outputLong(value, buffer, 0)
        return String(buffer, 0, offset)
    }

}