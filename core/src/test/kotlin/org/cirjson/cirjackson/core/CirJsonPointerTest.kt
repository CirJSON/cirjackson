package org.cirjson.cirjackson.core

import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.exception.StreamReadException
import kotlin.test.*

class CirJsonPointerTest : TestBase() {

    @Test
    fun testSimplePath() {
        val input = "/Image/15/name"

        var pointer: CirJsonPointer? = CirJsonPointer.compile(input)
        assertFalse(pointer!!.isMatching)
        assertEquals(-1, pointer.matchingIndex)
        assertEquals("Image", pointer.matchingProperty)
        assertEquals("/Image/15", pointer.head!!.toString())
        assertEquals(input, pointer.toString())

        pointer = pointer.tail
        assertNotNull(pointer)
        assertFalse(pointer.isMatching)
        assertEquals(15, pointer.matchingIndex)
        assertEquals("15", pointer.matchingProperty)
        assertEquals("/15", pointer.head!!.toString())
        assertEquals("/15/name", pointer.toString())

        assertEquals("", pointer.head!!.head!!.toString())
        assertNull(pointer.head!!.head!!.head)

        pointer = pointer.tail
        assertNotNull(pointer)
        assertFalse(pointer.isMatching)
        assertEquals(-1, pointer.matchingIndex)
        assertEquals("name", pointer.matchingProperty)
        assertEquals("/name", pointer.toString())
        assertEquals("", pointer.head!!.toString())
        assertSame(EMPTY_POINTER, pointer.head)

        pointer = pointer.tail
        assertTrue(pointer!!.isMatching)
        assertNull(pointer.tail)
        assertNull(pointer.head)
        assertNull(pointer.matchingProperty)
        assertEquals(-1, pointer.matchingIndex)
    }

    @Test
    fun testSimplePathLonger() {
        val input = "/a/b/c/d/e/f/0"
        val pointer = CirJsonPointer.compile(input)
        assertFalse(pointer.isMatching)
        assertEquals(-1, pointer.matchingIndex)
        assertEquals("a", pointer.matchingProperty)
        assertEquals("/a/b/c/d/e/f", pointer.head!!.toString())
        assertEquals("/b/c/d/e/f/0", pointer.tail!!.toString())
        assertEquals("/0", pointer.last!!.toString())
        assertEquals(input, pointer.toString())
    }

    @Test
    fun testSimpleTail() {
        val input = "/root/leaf"
        val pointer = CirJsonPointer.compile(input)

        assertEquals("/leaf", pointer.tail!!.toString())
        assertEquals("", pointer.tail!!.tail!!.toString())
    }

    @Test
    fun testWonkyNumber() {
        val pointer = CirJsonPointer.compile("/1e0")
        assertFalse(pointer.isMatching)
    }

    @Test
    fun testZeroIndex() {
        var pointer = CirJsonPointer.compile("/0")
        assertEquals(0, pointer.matchingIndex)
        pointer = CirJsonPointer.compile("/00")
        assertEquals(-1, pointer.matchingIndex)
    }

    @Test
    fun testLast() {
        var input = "/Image/name"

        var pointer = CirJsonPointer.compile(input)
        var leaf = pointer.last!!

        assertEquals("/name", leaf.toString())
        assertEquals("name", leaf.matchingProperty)

        input = "/Image/15/name"

        pointer = CirJsonPointer.compile(input)
        leaf = pointer.last!!

        assertEquals("/name", leaf.toString())
        assertEquals("name", leaf.matchingProperty)
    }

    @Test
    fun testEmptyPointer() {
        assertSame(EMPTY_POINTER, CirJsonPointer.compile(""))
        assertEquals("", EMPTY_POINTER.toString())

        assertFalse(EMPTY_POINTER.isMaybeMatchingProperty)
        assertFalse(EMPTY_POINTER.isMaybeMatchingElement)
        assertEquals(-1, EMPTY_POINTER.matchingIndex)
        assertNull(EMPTY_POINTER.matchingProperty)
    }

    @Test
    fun testPointerWithEmptyPropertyName() {
        val pointer = CirJsonPointer.compile("/")
        assertNotNull(pointer)
        assertNotSame(EMPTY_POINTER, pointer)

        assertEquals("/", pointer.toString())
        assertTrue(pointer.isMaybeMatchingProperty)
        assertFalse(pointer.isMaybeMatchingElement)
        assertEquals(-1, pointer.matchingIndex)
        assertEquals("", pointer.matchingProperty)
        assertTrue(pointer.matchesProperty(""))
        assertFalse(pointer.matchesElement(0))
        assertFalse(pointer.matchesElement(-1))
        assertFalse(pointer.matchesProperty("1"))
    }

    @Test
    fun testEquality() {
        assertNotEquals(CirJsonPointer.empty(), CirJsonPointer.compile("/"))

        assertEquals(CirJsonPointer.compile("/foo/3"), CirJsonPointer.compile("/foo/3"))
        assertNotEquals(CirJsonPointer.empty(), CirJsonPointer.compile("/12"))
        assertNotEquals(CirJsonPointer.compile("/12"), CirJsonPointer.empty())

        assertEquals(CirJsonPointer.compile("/a/b/c").tail, CirJsonPointer.compile("/foo/b/c").tail)

        val pointer = CirJsonPointer.compile("/abc/def")
        val tail = CirJsonPointer.compile("/def")
        assertEquals(pointer.tail, tail)
        assertEquals(tail, pointer.tail)

        assertNotEquals("/" as Any, CirJsonPointer.empty())
    }

    @Test
    fun testProperties() {
        assertTrue(CirJsonPointer.compile("/foo").isMaybeMatchingProperty)
        assertFalse(CirJsonPointer.compile("/foo").isMaybeMatchingElement)

        assertTrue(CirJsonPointer.compile("/12").isMaybeMatchingElement)
        assertTrue(CirJsonPointer.compile("/12").isMaybeMatchingProperty)
    }

    @Test
    fun testAppend() {
        val input = "/Image/15/name"
        val stringToAppend = "/extension"

        val pointer = CirJsonPointer.compile(input)
        val toAppend = CirJsonPointer.compile(stringToAppend)

        val appended = pointer.append(toAppend)

        assertEquals("extension", appended.last!!.matchingProperty)

        assertEquals("/Image/15/name/extension", appended.toString())
    }

    @Test
    fun testAppendWithFinalSlash() {
        val input = "/Image/15/name/"
        val stringToAppend = "/extension"

        val pointer = CirJsonPointer.compile(input)
        val toAppend = CirJsonPointer.compile(stringToAppend)

        val appended = pointer.append(toAppend)

        assertEquals("extension", appended.last!!.matchingProperty)

        assertEquals("/Image/15/name//extension", appended.toString())
    }

    @Test
    fun testAppendProperty() {
        val input = "/Image/15/name"
        val stringToAppendNoSlash = "extension"
        val stringToAppendWithSlash = "/extension~"

        val pointer = CirJsonPointer.compile(input)
        val appendedNoSlash = pointer.appendProperty(stringToAppendNoSlash)
        val appendedWithSlash = pointer.appendProperty(stringToAppendWithSlash)

        assertEquals(stringToAppendNoSlash, appendedNoSlash.last!!.matchingProperty)
        assertEquals("/Image/15/name/extension", appendedNoSlash.toString())

        assertEquals(stringToAppendWithSlash, appendedWithSlash.last!!.matchingProperty)
        assertEquals("/Image/15/name/~1extension~0", appendedWithSlash.toString())
    }

    @Test
    fun testAppendPropertyEmpty() {
        val base = "/Image/72/src"

        val basePointer = CirJsonPointer.compile(base)

        assertSame(basePointer, basePointer.appendProperty(null))

        val sub = basePointer.appendProperty("")
        assertNotSame(basePointer, sub)

        assertEquals("$base/", sub.toString())
    }

    @Test
    fun testAppendIndex() {
        val input = "/Image/15/name"
        val index = 12

        val pointer = CirJsonPointer.compile(input)
        val appended = pointer.appendIndex(index)

        assertEquals(12, appended.last!!.matchingIndex)
    }

    @Test
    fun testQuotedPath() {
        val input = "/w~1out/til~0de/~1ab"

        var pointer: CirJsonPointer? = CirJsonPointer.compile(input)
        assertFalse(pointer!!.isMatching)
        assertEquals(-1, pointer.matchingIndex)
        assertEquals("w/out", pointer.matchingProperty)
        assertEquals("/w~1out/til~0de", pointer.head!!.toString())
        assertEquals(input, pointer.toString())

        pointer = pointer.tail
        assertNotNull(pointer)
        assertFalse(pointer.isMatching)
        assertEquals(-1, pointer.matchingIndex)
        assertEquals("til~de", pointer.matchingProperty)
        assertEquals("/til~0de", pointer.head!!.toString())
        assertEquals("/til~0de/~1ab", pointer.toString())

        pointer = pointer.tail
        assertNotNull(pointer)
        assertFalse(pointer.isMatching)
        assertEquals(-1, pointer.matchingIndex)
        assertEquals("/ab", pointer.matchingProperty)
        assertEquals("", pointer.head!!.toString())
        assertEquals("/~1ab", pointer.toString())

        pointer = pointer.tail
        assertNotNull(pointer)
        assertTrue(pointer.isMatching)
        assertNull(pointer.tail)
    }

    @Test
    fun testLongNumbers() {
        val longId = 1L + Int.MAX_VALUE

        val input = "/User/$longId"

        var pointer: CirJsonPointer? = CirJsonPointer.compile(input)
        assertEquals("User", pointer!!.matchingProperty)
        assertEquals(input, pointer.toString())

        pointer = pointer.tail
        assertNotNull(pointer)
        assertFalse(pointer.isMatching)
        assertEquals(-1, pointer.matchingIndex)
        assertEquals(longId.toString(), pointer.matchingProperty)

        pointer = pointer.tail
        assertNotNull(pointer)
        assertTrue(pointer.isMatching)
        assertNull(pointer.tail)
    }

    @Test
    fun testAppendWithTail() {
        val original = CirJsonPointer.compile("/a1/b/c")
        val tailPointer = original.tail!!
        assertEquals("/b/c", tailPointer.toString())

        val other = CirJsonPointer.compile("/a2")
        assertEquals("/a2", other.toString())

        assertEquals("/a2/b/c", other.append(tailPointer).toString())

        assertEquals("/b/c/a2", tailPointer.append(other).toString())

        assertEquals("/b/c/xyz", tailPointer.appendProperty("xyz").toString())
    }

    @Test
    fun testDeepCirJsonPointer() {
        val maxDepth = 120_000
        val input = generateDeepString(maxDepth)
        val factory = CirJsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(Int.MAX_VALUE).build()).build()
        val parser = createParser(factory, MODE_READER, input)

        try {
            while (true) {
                parser.nextToken()
            }
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected end")
            val parsingContext = parser.streamReadContext!!
            val pointer = parsingContext.pathAsPointer()
            val pointerString = pointer.toString()
            val expected = String(CharArray(maxDepth - 1)).replace("\u0000", "/0")
            assertEquals(expected, pointerString)
        }

        parser.close()
    }

    @Suppress("SameParameterValue")
    private fun generateDeepString(maxDepth: Int): String {
        val stringBuilder = StringBuilder(maxDepth * 10)

        for (i in 0..<maxDepth) {
            stringBuilder.append("[\"$i\",")
        }

        return stringBuilder.toString().removeSuffix(",")
    }

    companion object {

        private val EMPTY_POINTER = CirJsonPointer.empty()

    }

}