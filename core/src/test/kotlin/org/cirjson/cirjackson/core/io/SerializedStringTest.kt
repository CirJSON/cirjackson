package org.cirjson.cirjackson.core.io

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class SerializedStringTest {

    @Test
    fun testAppending() {
        val serializedString = SerializedString(INPUT1)
        assertEquals(INPUT1, serializedString.value)
        assertEquals(INPUT1.hashCode(), serializedString.hashCode())
        assertSame(serializedString.value, serializedString.toString())
        assertEquals(QUOTED, String(serializedString.asQuotedChars()))
        assertEquals(QUOTED, String(serializedString.asQuotedUTF8()))

        val output = ByteArrayOutputStream()
        assertEquals(QUOTED.length, serializedString.writeQuotedUTF8(output))
        assertEquals(QUOTED, output.toString(Charsets.UTF_8))
        output.reset()
        assertEquals(INPUT1.length, serializedString.writeUnquotedUTF8(output))
        assertEquals(INPUT1, output.toString(Charsets.UTF_8))

        val bytes = ByteArray(100)
        assertEquals(QUOTED.length, serializedString.appendQuotedUTF8(bytes, 3))
        assertEquals(QUOTED, String(bytes, 3, QUOTED.length))
        bytes.fill(0)
        assertEquals(INPUT1.length, serializedString.appendUnquotedUTF8(bytes, 5))
        assertEquals(INPUT1, String(bytes, 5, INPUT1.length))

        val chars = CharArray(100)
        assertEquals(QUOTED.length, serializedString.appendQuoted(chars, 3))
        assertEquals(QUOTED, String(chars, 3, QUOTED.length))
        chars.fill(0.toChar())
        assertEquals(INPUT1.length, serializedString.appendUnquoted(chars, 5))
        assertEquals(INPUT1, String(chars, 5, INPUT1.length))

        val byteBuffer = ByteBuffer.allocate(QUOTED.length)
        assertEquals(QUOTED.length, serializedString.putQuotedUTF8(byteBuffer))
        assertEquals(QUOTED, String(byteBuffer.array(), 0, QUOTED.length))
        byteBuffer.rewind()
        assertEquals(INPUT1.length, serializedString.putUnquotedUTF8(byteBuffer))
        assertEquals(INPUT1, String(byteBuffer.array(), 0, INPUT1.length))
    }

    @Test
    fun testFailedAccess() {
        val serializedString = SerializedString(INPUT2)
        val buffer = ByteArray(INPUT2.length - 2)
        val ch = CharArray(INPUT2.length - 2)
        val byteBuffer = ByteBuffer.allocate(INPUT2.length - 2)

        assertEquals(-1, serializedString.appendQuotedUTF8(buffer, 0))
        assertEquals(-1, serializedString.appendQuoted(ch, 0))
        assertEquals(-1, serializedString.putQuotedUTF8(byteBuffer))

        byteBuffer.rewind()
        assertEquals(-1, serializedString.appendUnquotedUTF8(buffer, 0))
        assertEquals(-1, serializedString.appendUnquoted(ch, 0))
        assertEquals(-1, serializedString.putUnquotedUTF8(byteBuffer))
    }

    @Test
    fun testAppendQuotedUTF8() {
        val serializedString = SerializedString(QUOTED)
        assertEquals(QUOTED, serializedString.value)
        val buffer = ByteArray(100)
        val length = serializedString.appendQuotedUTF8(buffer, 3)
        assertEquals("\\\\\\\"quo\\\\\\\\ted\\\\\\\"", String(buffer, 3, length))
    }

    @Test
    fun testEquals() {
        val serializedString1 = SerializedString(QUOTED)
        val serializedString2 = SerializedString(QUOTED)
        val serializedString3 = SerializedString(INPUT1)

        assertEquals(serializedString1, serializedString1)
        assertEquals(serializedString1, serializedString2)
        assertNotEquals(serializedString1, Any())
        assertNotEquals(serializedString1, null as Any?)
        assertNotEquals(serializedString1, serializedString3)
    }

    companion object {

        private const val QUOTED = "\\\"quo\\\\ted\\\""

        private const val INPUT1 = "\"quo\\ted\""

        private const val INPUT2 = "Bit longer text"

    }

}