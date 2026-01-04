package org.cirjson.cirjackson.core.symbols

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SymbolsViaParserTest : TestBase() {

    @Test
    fun test17CharSymbols() {
        test17Chars(MODE_READER)
    }

    @Test
    fun test17DataInput() {
        test17Chars(MODE_DATA_INPUT)
    }

    @Test
    fun test17ByteSymbols() {
        test17Chars(MODE_INPUT_STREAM)
    }

    @Test
    fun testSymbolTableExpansionChars() {
        testSymbolTableExpansion(MODE_READER)
    }

    @Test
    fun testSymbolTableExpansionDataInput() {
        testSymbolTableExpansion(MODE_DATA_INPUT)
    }

    @Test
    fun testSymbolTableExpansionBytes() {
        testSymbolTableExpansion(MODE_INPUT_STREAM)
    }

    /*
     *******************************************************************************************************************
     * Secondary test methods
     *******************************************************************************************************************
     */

    private fun test17Chars(mode: Int) {
        val doc = createDoc17()
        val parser = createParser(mode, doc)

        val symbols = HashSet<String>()
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        for (i in 1..50) {
            assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
            symbols.add(parser.currentName()!!)
            assertEquals("lengthMatters${1000 + i}", parser.currentName()!!)
            assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        }

        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertEquals(50, symbols.size)
        parser.close()
    }

    private fun createDoc17(): String {
        val stringBuilder = StringBuilder(1000)
        stringBuilder.append("{\n")
        stringBuilder.append("\"__cirJsonId__\": \"root\"")

        for (i in 1..50) {
            stringBuilder.append(",\n\"lengthMatters").append(1000 + i).append("\": true")
        }

        stringBuilder.append("\n}")
        return stringBuilder.toString()
    }

    private fun testSymbolTableExpansion(mode: Int) {
        for (i in 1..200) {
            val field = i.toString()
            val doc = "{ \"__cirJsonId__\": \"root\", \"$field\" : \"test\" }"
            val parser = createParser(mode, doc)
            assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
            assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
            assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
            assertEquals(field, parser.currentName()!!)
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
            assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
            assertNull(parser.nextToken())
            parser.close()
        }
    }

}