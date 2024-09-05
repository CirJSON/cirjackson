package org.cirjson.cirjackson.core.cirjson.async

import org.cirjson.cirjackson.core.AsyncTestBase
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TokenStreamFactory
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull

class AsyncParserNamesTest : AsyncTestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testLongNames() {
        val name = generateLongNames()

        for (mode in ALL_ASYNC_MODES) {
            longNames(mode, 0, name, 99)
            longNames(mode, 0, name, 5)
            longNames(mode, 0, name, 3)
            longNames(mode, 0, name, 2)
            longNames(mode, 0, name, 1)

            longNames(mode, 1, name, 99)
            longNames(mode, 1, name, 3)
            longNames(mode, 1, name, 1)
        }
    }

    private fun generateLongNames(): String {
        val stringBuilder = StringBuilder(5050)
        val random = Random(123)

        while (stringBuilder.length < 5000) {
            val ch = random.nextInt(96)

            if (ch < 32) {
                stringBuilder.append((ch + 48).toChar())
            } else if (ch < 64) {
                stringBuilder.append((ch + 128).toChar())
            } else {
                stringBuilder.append((ch + 4000).toChar())
            }
        }

        return stringBuilder.toString()
    }

    @Test
    fun testLongerNames() {
        val name = generateLongerNames()

        for (mode in ALL_ASYNC_MODES) {
            longNames(mode, 0, name, 99)
            longNames(mode, 0, name, 5)
            longNames(mode, 0, name, 3)
            longNames(mode, 0, name, 2)
            longNames(mode, 0, name, 1)

            longNames(mode, 1, name, 99)
            longNames(mode, 1, name, 3)
            longNames(mode, 1, name, 1)
        }
    }

    private fun generateLongerNames(): String {
        val stringBuilder = StringBuilder(9050)

        var i = 1

        while (stringBuilder.length < 9000) {
            stringBuilder.append('.').append(i++)
        }

        return stringBuilder.toString()
    }

    private fun longNames(mode: Int, padding: Int, name: String, bytesPerFeed: Int) {
        val doc = utf8Bytes("{\"__cirJsonId__\":\"root\", ${quote(name)}:13}")
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertNull(parser.currentToken())
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals(name, parser.currentName)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(13, parser.intValue)
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testSymbolTable() {
        for (mode in ALL_ASYNC_MODES) {
            symbolTable(mode, 0, 99)
            symbolTable(mode, 0, 5)
            symbolTable(mode, 0, 3)
            symbolTable(mode, 0, 2)
            symbolTable(mode, 0, 1)

            symbolTable(mode, 1, 99)
            symbolTable(mode, 1, 3)
            symbolTable(mode, 1, 1)
        }
    }

    private fun symbolTable(mode: Int, padding: Int, bytesPerFeed: Int) {
        val factory = newStreamFactory()
        val key = "a"
        val doc = utf8Bytes("{ \"__cirJsonId__\":\"root\", ${quote(key)}:1, \"foobar\":2, \"longerName\":3 }")
        var parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        val symbols1 = (parser.parser as NonBlockingCirJsonParserBase).symbolTableForTests()
        assertEquals(0, symbols1.size)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(1, symbols1.size)
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals(key, parser.currentName)
        assertEquals(2, symbols1.size)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("foobar", parser.currentName)
        assertEquals(3, symbols1.size)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("longerName", parser.currentName)
        assertEquals(4, symbols1.size)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()

        parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        val symbols2 = (parser.parser as NonBlockingCirJsonParserBase).symbolTableForTests()
        assertNotSame(symbols1, symbols2)
        assertEquals(4, symbols2.size)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(4, symbols2.size)
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals(key, parser.currentName)
        assertEquals(4, symbols2.size)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("foobar", parser.currentName)
        assertEquals(4, symbols2.size)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("longerName", parser.currentName)
        assertEquals(4, symbols2.size)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        assertEquals(4, symbols2.size)
        parser.close()

        assertEquals(4, symbols2.size)
        parser.close()
    }

    @Test
    fun testSymbolTableWithIntern() {
        for (mode in ALL_ASYNC_MODES) {
            symbolTableWithIntern(mode, 0, 99)
            symbolTableWithIntern(mode, 0, 5)
            symbolTableWithIntern(mode, 0, 3)
            symbolTableWithIntern(mode, 0, 2)
            symbolTableWithIntern(mode, 0, 1)

            symbolTableWithIntern(mode, 1, 99)
            symbolTableWithIntern(mode, 1, 3)
            symbolTableWithIntern(mode, 1, 1)
        }
    }

    private fun symbolTableWithIntern(mode: Int, padding: Int, bytesPerFeed: Int) {
        val factory = CirJsonFactory.builder().enable(TokenStreamFactory.Feature.INTERN_PROPERTY_NAMES).build()
        val key = "a"
        val doc = utf8Bytes("{ \"__cirJsonId__\":\"root\", ${quote(key)}:1, \"foobar\":2, \"longerName\":3 }")
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        val symbols1 = (parser.parser as NonBlockingCirJsonParserBase).symbolTableForTests()
        assertEquals(0, symbols1.size)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(1, symbols1.size)
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals(key, parser.currentName)
        assertEquals(2, symbols1.size)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("foobar", parser.currentName)
        assertEquals(3, symbols1.size)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("longerName", parser.currentName)
        assertEquals(4, symbols1.size)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

}