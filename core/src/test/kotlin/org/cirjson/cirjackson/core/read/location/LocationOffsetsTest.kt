package org.cirjson.cirjackson.core.read.location

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.ObjectReadContext
import org.cirjson.cirjackson.core.TestBase
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocationOffsetsTest : TestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testSimpleInitialOffsets() {
        val doc = "{ \"__cirJsonId__\":\"root\" }"
        var parser = factory.createParser(ObjectReadContext.empty(), doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())

        var location = parser.currentTokenLocation()
        assertEquals(-1L, location.byteOffset)
        assertEquals(0L, location.charOffset)
        assertEquals(1, location.lineNumber)
        assertEquals(1, location.columnNumber)

        location = parser.currentLocation()
        assertEquals(-1L, location.byteOffset)
        assertEquals(1L, location.charOffset)
        assertEquals(1, location.lineNumber)
        assertEquals(2, location.columnNumber)

        parser.close()

        parser = factory.createParser(ObjectReadContext.empty(), doc.toByteArray())
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())

        location = parser.currentTokenLocation()
        assertEquals(0L, location.byteOffset)
        assertEquals(-1L, location.charOffset)
        assertEquals(1, location.lineNumber)
        assertEquals(1, location.columnNumber)

        location = parser.currentLocation()
        assertEquals(1L, location.byteOffset)
        assertEquals(-1L, location.charOffset)
        assertEquals(1, location.lineNumber)
        assertEquals(2, location.columnNumber)

        parser.close()
    }

    @Test
    fun testOffsetWithInputOffset() {
        val doc = "   { \"__cirJsonId__\":\"root\" }  "
        val dataChars = doc.toCharArray()
        var parser = factory.createParser(ObjectReadContext.empty(), dataChars, 3, dataChars.size - 5)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())

        var location = parser.currentTokenLocation()
        assertEquals(-1L, location.byteOffset)
        assertEquals(0L, location.charOffset)
        assertEquals(1, location.lineNumber)
        assertEquals(1, location.columnNumber)

        location = parser.currentLocation()
        assertEquals(-1L, location.byteOffset)
        assertEquals(1L, location.charOffset)
        assertEquals(1, location.lineNumber)
        assertEquals(2, location.columnNumber)

        parser.close()

        val dataBytes = doc.toByteArray()
        parser = factory.createParser(ObjectReadContext.empty(), dataBytes, 3, dataBytes.size - 5)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())

        location = parser.currentTokenLocation()
        assertEquals(0L, location.byteOffset)
        assertEquals(-1L, location.charOffset)
        assertEquals(1, location.lineNumber)
        assertEquals(1, location.columnNumber)

        location = parser.currentLocation()
        assertEquals(1L, location.byteOffset)
        assertEquals(-1L, location.charOffset)
        assertEquals(1, location.lineNumber)
        assertEquals(2, location.columnNumber)

        parser.close()
    }

    @Test
    fun testOffsetWithoutInputOffset() {
        val doc = "   { \"__cirJsonId__\":\"root\" }  "
        var parser = factory.createParser(ObjectReadContext.empty(), doc.toCharArray())
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())

        var location = parser.currentTokenLocation()
        assertEquals(-1L, location.byteOffset)
        assertEquals(3L, location.charOffset)
        assertEquals(1, location.lineNumber)
        assertEquals(4, location.columnNumber)

        location = parser.currentLocation()
        assertEquals(-1L, location.byteOffset)
        assertEquals(4L, location.charOffset)
        assertEquals(1, location.lineNumber)
        assertEquals(5, location.columnNumber)

        parser.close()

        parser = factory.createParser(ObjectReadContext.empty(), doc.toByteArray())
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())

        location = parser.currentTokenLocation()
        assertEquals(3L, location.byteOffset)
        assertEquals(-1L, location.charOffset)
        assertEquals(1, location.lineNumber)
        assertEquals(4, location.columnNumber)

        location = parser.currentLocation()
        assertEquals(4L, location.byteOffset)
        assertEquals(-1L, location.charOffset)
        assertEquals(1, location.lineNumber)
        assertEquals(5, location.columnNumber)

        parser.close()
    }

    @Test
    fun testWithLazyStringRead() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            if (mode == MODE_DATA_INPUT) {
                continue
            }

            withLazyStringRead(mode)
        }
    }

    private fun withLazyStringRead(mode: Int) {
        val doc = "[\"text\"]"
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(3, parser.currentLocation().columnNumber)
        parser.finishToken()
        assertEquals(8, parser.currentLocation().columnNumber)
        parser.finishToken()
        assertEquals(8, parser.currentLocation().columnNumber)
        assertEquals("text", parser.text)
        assertEquals(8, parser.currentLocation().columnNumber)
        parser.close()
    }

    @Test
    fun testWithLazyStringReadDataInput() {
        val doc = "[\"text\"]"
        val parser = createParser(factory, MODE_DATA_INPUT, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        val location = parser.currentLocation()
        assertEquals(1, location.lineNumber)
        parser.finishToken()
        assertEquals("text", parser.text)
        parser.close()
    }

    @Test
    fun testUtf8Bom() {
        val doc = withUtf8Bom("{ \"__cirJsonId__\":\"root\" }".toByteArray())
        val parser = factory.createParser(ObjectReadContext.empty(), doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())

        var location = parser.currentTokenLocation()
        assertEquals(3L, location.byteOffset)
        assertEquals(-1L, location.charOffset)
        assertEquals(1, location.lineNumber)
        assertEquals(4, location.columnNumber)

        location = parser.currentLocation()
        assertEquals(4L, location.byteOffset)
        assertEquals(-1L, location.charOffset)
        assertEquals(1, location.lineNumber)
        assertEquals(5, location.columnNumber)

        parser.close()
    }

    @Test
    fun testUtf8BomWithPadding() {
        val doc = withUtf8Bom("   { \"__cirJsonId__\":\"root\" }".toByteArray())
        val parser = factory.createParser(ObjectReadContext.empty(), doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())

        var location = parser.currentTokenLocation()
        assertEquals(6L, location.byteOffset)
        assertEquals(-1L, location.charOffset)
        assertEquals(1, location.lineNumber)
        assertEquals(7, location.columnNumber)

        location = parser.currentLocation()
        assertEquals(7L, location.byteOffset)
        assertEquals(-1L, location.charOffset)
        assertEquals(1, location.lineNumber)
        assertEquals(8, location.columnNumber)

        parser.close()
    }

    @Test
    fun testUtf8BomWithInputOffset() {
        val doc = withUtf8Bom(withUtf8Bom("{ \"__cirJsonId__\":\"root\" }".toByteArray()))
        val parser = factory.createParser(ObjectReadContext.empty(), doc, 3, doc.size - 3)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())

        var location = parser.currentTokenLocation()
        assertEquals(3L, location.byteOffset)
        assertEquals(-1L, location.charOffset)
        assertEquals(1, location.lineNumber)
        assertEquals(4, location.columnNumber)

        location = parser.currentLocation()
        assertEquals(4L, location.byteOffset)
        assertEquals(-1L, location.charOffset)
        assertEquals(1, location.lineNumber)
        assertEquals(5, location.columnNumber)

        parser.close()
    }

    private fun withUtf8Bom(bytes: ByteArray): ByteArray {
        val array = ByteArray(bytes.size + 3)
        array[0] = 0xEF.toByte()
        array[1] = 0xBB.toByte()
        array[2] = 0xBF.toByte()
        bytes.copyInto(array, 3)
        return array
    }

    @Test
    fun testBigPayload() {
        val doc = "{\"__cirJsonId__\":\"root\",\"key\":\"${generateBigPayloadValue()}\"}"
        val parser = factory.createParser(ObjectReadContext.empty(), doc.toByteArray())

        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        var location = parser.currentTokenLocation()
        assertEquals(0L, location.byteOffset)
        assertEquals(-1L, location.charOffset)
        assertEquals(1, location.lineNumber)
        assertEquals(1, location.columnNumber)
        location = parser.currentLocation()
        assertEquals(1L, location.byteOffset)
        assertEquals(-1L, location.charOffset)
        assertEquals(1, location.lineNumber)
        assertEquals(2, location.columnNumber)

        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        location = parser.currentTokenLocation()
        assertEquals(24L, location.byteOffset)
        assertEquals(-1L, location.charOffset)
        assertEquals(1, location.lineNumber)
        assertEquals(25, location.columnNumber)
        location = parser.currentLocation()
        assertEquals(31L, location.byteOffset)
        assertEquals(-1L, location.charOffset)
        assertEquals(1, location.lineNumber)
        assertEquals(32, location.columnNumber)

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        location = parser.currentTokenLocation()
        assertEquals(30L, location.byteOffset)
        assertEquals(-1L, location.charOffset)
        assertEquals(1, location.lineNumber)
        assertEquals(31, location.columnNumber)
        location = parser.currentLocation()
        assertEquals(31L, location.byteOffset)
        assertEquals(-1L, location.charOffset)
        assertEquals(1, location.lineNumber)
        assertEquals(32, location.columnNumber)

        parser.textCharacters
        location = parser.currentTokenLocation()
        assertEquals(30L, location.byteOffset)
        assertEquals(-1L, location.charOffset)
        assertEquals(1, location.lineNumber)
        assertEquals(31, location.columnNumber)
        location = parser.currentLocation()
        assertEquals(doc.length - 1L, location.byteOffset)
        assertEquals(-1L, location.charOffset)
        assertEquals(1, location.lineNumber)
        assertEquals(doc.length, location.columnNumber)
    }

    private fun generateBigPayloadValue(): String {
        val stringBuilder = StringBuilder(50000)
        val random = Random(50000L)

        for (i in 1..50000) {
            val ch = ('A'.code + random.nextInt(26)).toChar()
            stringBuilder.append(ch)
        }

        return stringBuilder.toString()
    }

    @Test
    fun testEofLocationViaReader() {
        val doc = "42"
        val parser = factory.createParser(ObjectReadContext.empty(), doc)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        var location = parser.currentLocation()
        assertEquals(-1L, location.byteOffset)
        assertEquals(2L, location.charOffset)
        assertEquals(1, location.lineNumber)
        assertEquals(3, location.columnNumber)

        assertNull(parser.nextToken())
        location = parser.currentLocation()
        assertEquals(-1L, location.byteOffset)
        assertEquals(2L, location.charOffset)
        assertEquals(1, location.lineNumber)
        assertEquals(3, location.columnNumber)
    }

    @Test
    fun testEofLocationViaInputStream() {
        val doc = "42"
        val parser = factory.createParser(ObjectReadContext.empty(), doc.toByteArray())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        var location = parser.currentLocation()
        assertEquals(2L, location.byteOffset)
        assertEquals(-1L, location.charOffset)
        assertEquals(1, location.lineNumber)
        assertEquals(3, location.columnNumber)

        assertNull(parser.nextToken())
        location = parser.currentLocation()
        assertEquals(2L, location.byteOffset)
        assertEquals(-1L, location.charOffset)
        assertEquals(1, location.lineNumber)
        assertEquals(3, location.columnNumber)
    }

}