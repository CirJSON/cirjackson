package org.cirjson.cirjackson.core.io

import org.cirjson.cirjackson.core.CirJsonEncoding
import org.cirjson.cirjackson.core.TestBase
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class MergedStreamTest : TestBase() {

    @Test
    fun testSimple() {
        val context = testIOContext()
        val first = context.allocateReadIOBuffer()
        "ABCDE".toByteArray(StandardCharsets.UTF_8).copyInto(first, 99, 0, 5)
        val second = "FGHIJ".toByteArray(StandardCharsets.UTF_8)

        assertNull(context.contentReference!!.rawContent)
        assertFalse(context.isResourceManaged)
        context.encoding = CirJsonEncoding.UTF8
        val mergedStream = MergedStream(context, ByteArrayInputStream(second), first, 99, 99 + 5)
        context.close()

        assertEquals(5, mergedStream.available())
        assertFalse(mergedStream.markSupported())
        mergedStream.mark(1)
        assertEquals('A'.code and 0xFF, mergedStream.read())
        assertEquals(3, mergedStream.skip(3))

        val buffer = ByteArray(5)

        assertEquals(1, mergedStream.read(buffer, 1, 3))
        assertEquals('E'.code.toByte(), buffer[1])

        assertEquals(3, mergedStream.read(buffer, 0, 3))
        assertEquals('F'.code.toByte(), buffer[0])
        assertEquals('G'.code.toByte(), buffer[1])
        assertEquals('H'.code.toByte(), buffer[2])

        assertEquals(2, mergedStream.available())
        assertEquals(2, mergedStream.skip(200))

        mergedStream.close()
    }

}