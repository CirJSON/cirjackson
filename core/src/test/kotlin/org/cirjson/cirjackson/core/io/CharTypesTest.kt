package org.cirjson.cirjackson.core.io

import org.cirjson.cirjackson.core.TestBase
import kotlin.test.Test
import kotlin.test.assertEquals

class CharTypesTest : TestBase() {

    @Test
    fun testAppendQuoted() {
        val inputs = arrayOf("\u0000", "\u001F", "abcd", "\u0001ABCD\u0002", "WX\u000F\u0010YZ")
        val expectedArray = arrayOf("\\u0000", "\\u001F", "abcd", "\\u0001ABCD\\u0002", "WX\\u000F\\u0010YZ")

        for ((i, input) in inputs.withIndex()) {
            val expected = expectedArray[i]
            val stringBuilder = StringBuilder()
            CharTypes.appendQuoted(stringBuilder, input)
            val actual = stringBuilder.toString()
            assertEquals(expected, actual)
        }
    }

    @Test
    fun testHexOutOfRange() {
        val inputs = charArrayOf(0.toChar(), (-1).toChar(), 1.toChar(), 129.toChar(), (-129).toChar())

        for (input in inputs) {
            assertEquals(-1, CharTypes.charToHex(input))
        }
    }

    @Test
    fun testQuoting() {
        var stringBuilder = StringBuilder()
        CharTypes.appendQuoted(stringBuilder, "\n")
        assertEquals("\\n", stringBuilder.toString())
        stringBuilder = StringBuilder()
        CharTypes.appendQuoted(stringBuilder, "\u0000")
        assertEquals("\\u0000", stringBuilder.toString())
    }

}