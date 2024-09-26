package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.ObjectReadContext
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.exception.CirJacksonIOException
import org.cirjson.cirjackson.core.exception.StreamReadException
import kotlin.test.Test
import kotlin.test.fail

class UTF32ParseTest : TestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testSimpleEOFs() {
        val data = byteArrayOf(0x00, 0x00, 0x00, 0x20, 0x00, 0x00, 0x00, 0x20)

        for (length in 5..7) {
            val parser = factory.createParser(ObjectReadContext.empty(), data, 0, length)

            try {
                parser.nextToken()
                fail("Should have thrown an exception")
            } catch (e: CirJacksonIOException) {
                verifyException(e, "Unexpected EOF")
                verifyException(e, "of a 4-byte UTF-32 char")
            }

            parser.close()
        }
    }

    @Test
    fun testSimpleInvalidUTF32() {
        val data = byteArrayOf(0x00, 0x00, 0x00, 0x20, 0xFE.toByte(), 0xFF.toByte(), 0x00, 0x01)
        val parser = factory.createParser(ObjectReadContext.empty(), data)

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: CirJacksonIOException) {
            verifyException(e, "Invalid UTF-32 character 0xfefe0001")
        }

        parser.close()
    }

    @Test
    fun testSimpleSevenNullBytes() {
        val data = ByteArray(7)
        val parser = factory.createParser(ObjectReadContext.empty(), data)

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Illegal character ((CTRL-CHAR, code 0))")
        }

        parser.close()
    }

}