package org.cirjson.cirjackson.core.util

import org.cirjson.cirjackson.core.TestBase
import kotlin.test.*

class TextBufferTest : TestBase() {

    @Test
    fun testSimple() {
        val textBuffer = TextBuffer(BufferRecycler())
        textBuffer.append('a')
        textBuffer.append(charArrayOf('X', 'b'), 1, 1)
        textBuffer.append("c", 0, 1)

        assertTrue(textBuffer.isHavingTextAsCharacters)
        assertEquals(3, textBuffer.contentsAsArray().size)
        assertEquals("abc", textBuffer.toString())
        assertNotNull(textBuffer.expandCurrentSegment())
    }

    @Test
    fun testLonger() {
        val textBuffer = TextBuffer(null)

        for (i in 1..2000) {
            textBuffer.append("abc", 0, 3)
        }

        val string = textBuffer.contentsAsString()
        assertEquals(6000, string.length)
        assertEquals(6000, textBuffer.contentsAsArray().size)

        textBuffer.resetWithShared(charArrayOf('a'), 0, 1)
        assertTrue(textBuffer.isHavingTextAsCharacters)
    }

    @Test
    fun testLongAppend() {
        val length = TextBuffer.MAX_SEGMENT_LEN * 3 / 2
        val stringBuilder = StringBuilder()

        for (i in 1..length) {
            stringBuilder.append('X')
        }

        val string = stringBuilder.toString()
        val expanded = "a${string}c"

        var textBuffer = TextBuffer(BufferRecycler())
        textBuffer.append('a')
        textBuffer.append(string, 0, length)
        textBuffer.append('c')
        assertEquals(length + 2, textBuffer.size)
        assertEquals(expanded, textBuffer.contentsAsString())

        textBuffer = TextBuffer(BufferRecycler())
        textBuffer.append('a')
        textBuffer.append(string.toCharArray(), 0, length)
        textBuffer.append('c')
        assertEquals(length + 2, textBuffer.size)
        assertEquals(expanded, textBuffer.contentsAsString())
    }

    @Test
    fun testExpand() {
        val textBuffer = TextBuffer(BufferRecycler())
        var buffer = textBuffer.currentSegment

        while (buffer.size < 500_000) {
            val old = buffer
            buffer = textBuffer.expandCurrentSegment()

            if (old.size >= buffer.size) {
                fail("Expected buffer of ${old.size} to expand, did not, length now ${buffer.size}")
            }
        }

        textBuffer.resetWithString("Foobar")
        assertEquals("Foobar", textBuffer.contentsAsString())
    }

    @Test
    fun testEmpty() {
        val textBuffer = TextBuffer(BufferRecycler())
        textBuffer.resetWithEmpty()

        assertEquals(0, textBuffer.textBuffer.size)
        textBuffer.contentsAsString()
        assertEquals(0, textBuffer.textBuffer.size)
    }

    @Test
    fun testResetWithAndSetCurrentAndReturn() {
        val textBuffer = TextBuffer(null)
        textBuffer.resetWith('l')
        textBuffer.setCurrentAndReturn(349)
    }

    @Test
    fun testCurrentSegment() {
        val textBuffer = TextBuffer(null)
        textBuffer.emptyAndGetCurrentSegment()
        textBuffer.setCurrentAndReturn(500)
        textBuffer.currentSegment
        assertEquals(500, textBuffer.size)
    }

    @Test
    fun testAppendTakingTwoAndThreeIntegers() {
        val textBuffer = TextBuffer(BufferRecycler())
        textBuffer.ensureNotShared()
        val charArray = textBuffer.textBuffer
        textBuffer.append(charArray, 0, 200)
        textBuffer.append("5rmk0rx(C@aVYGN@Q", 2, 3)
        assertEquals(3, textBuffer.currentSegmentSize)
    }

    @Test
    fun testEnsureNotSharedAndResetWithString() {
        val textBuffer = TextBuffer(BufferRecycler())
        textBuffer.resetWithString("")

        assertFalse(textBuffer.isHavingTextAsCharacters)

        textBuffer.ensureNotShared()

        assertEquals(0, textBuffer.currentSegmentSize)
    }

    @Test
    fun testTextBufferAndEmptyAndCurrentSegmentAndFinishCurrentSegment() {
        val textBuffer = TextBuffer(BufferRecycler())
        textBuffer.emptyAndGetCurrentSegment()
        textBuffer.finishCurrentSegment()
        textBuffer.textBuffer

        assertEquals(200, textBuffer.size)
    }

    @Test
    fun testTextBufferAndAppendTakingCharAndContentsAsArray() {
        val textBuffer = TextBuffer(BufferRecycler())
        textBuffer.append('(')
        textBuffer.contentsAsArray()
        textBuffer.textBuffer
    }

    @Test
    fun testTextBufferAndResetWithString() {
        val textBuffer = TextBuffer(BufferRecycler())
        textBuffer.resetWithString("")

        assertFalse(textBuffer.isHavingTextAsCharacters)

        textBuffer.textBuffer

        assertTrue(textBuffer.isHavingTextAsCharacters)
    }

    @Test
    fun testResetWithString() {
        val textBuffer = TextBuffer(BufferRecycler())
        textBuffer.ensureNotShared()
        textBuffer.finishCurrentSegment()

        assertEquals(200, textBuffer.size)

        textBuffer.resetWithString("asdf")

        assertEquals(0, textBuffer.textOffset)
    }

    @Test
    fun testCurrentSegmentSizeAndResetWith() {
        val textBuffer = TextBuffer(null)
        textBuffer.resetWith('.')
        textBuffer.resetWith('q')

        assertEquals(1, textBuffer.currentSegmentSize)
    }

    @Test
    fun testSizeAndFinishCurrentSegmentAndResetWith() {
        val textBuffer = TextBuffer(null)
        textBuffer.resetWith('.')
        textBuffer.finishCurrentSegment()
        textBuffer.resetWith('q')

        assertEquals(2, textBuffer.size)
    }

    @Test
    fun testContentsAsFloat() {
        val textBuffer = TextBuffer(null)
        textBuffer.resetWithString("1.2345678")
        assertEquals(1.2345678f, textBuffer.contentAsFloat(false))
    }

    @Test
    fun testContentsAsFloatFastParser() {
        val textBuffer = TextBuffer(null)
        textBuffer.resetWithString("1.2345678")
        assertEquals(1.2345678f, textBuffer.contentAsFloat(true))
    }

    @Test
    fun testContentsAsDouble() {
        val textBuffer = TextBuffer(null)
        textBuffer.resetWithString("1.234567890123456789")
        assertEquals(1.2345678901234567, textBuffer.contentsAsDouble(true))
    }

    @Test
    fun testContentsAsDoubleFastParser() {
        val textBuffer = TextBuffer(null)
        textBuffer.resetWithString("1.234567890123456789")
        assertEquals(1.2345678901234567, textBuffer.contentsAsDouble(true))
    }

}