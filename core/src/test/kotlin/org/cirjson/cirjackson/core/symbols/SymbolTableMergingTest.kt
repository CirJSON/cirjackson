package org.cirjson.cirjackson.core.symbols

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class SymbolTableMergingTest : TestBase() {

    @Test
    fun testCalculateHash() {
        val symbols = CharsToNameCanonicalizer.createRoot(CirJsonFactory())
        val str1 = "foo".toCharArray()
        val str2 = " foo ".toCharArray()

        assertEquals(symbols.calculateHash(str1, 0, 3), symbols.calculateHash(str2, 1, 3))
    }

    @Test
    fun testSymbolsWithClose() {
        for (mode in ALL_NON_THROTTLED_PARSER_MODES) {
            testWithClose(mode)
        }
    }

    private fun testWithClose(mode: Int) {
        val factory = MyCirJsonFactory()
        val parser = createParser(factory, mode, CIRJSON)

        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())

        assertEquals(0, if (mode == MODE_READER) factory.charSymbolCount() else factory.byteSymbolCount())
        parser.close()
        assertEquals(3, if (mode == MODE_READER) factory.charSymbolCount() else factory.byteSymbolCount())
    }

    @Test
    fun testSymbolsWithEOF() {
        for (mode in ALL_NON_THROTTLED_PARSER_MODES) {
            testWithEOF(mode)
        }
    }

    private fun testWithEOF(mode: Int) {
        val factory = MyCirJsonFactory()
        val parser = createParser(factory, mode, CIRJSON)

        while (parser.nextToken() != null) {
            assertEquals(0, if (mode == MODE_READER) factory.charSymbolCount() else factory.byteSymbolCount())
        }

        assertEquals(4, if (mode == MODE_READER) factory.charSymbolCount() else factory.byteSymbolCount())
        parser.close()
        assertEquals(4, if (mode == MODE_READER) factory.charSymbolCount() else factory.byteSymbolCount())
    }

    /**
     * To peek into state of "root" symbol tables (parent of all symbol tables for parsers constructed by this factory)
     * we need to add some methods.
     */
    private class MyCirJsonFactory : CirJsonFactory() {

        fun byteSymbolCount(): Int = myByteSymbolCanonicalizer.size

        fun charSymbolCount(): Int = myRootCharSymbols.size

    }

    companion object {

        const val CIRJSON = "{ \"__cirJsonId__\" : \"root\", \"a\" : 3, \"aaa\" : 4, \"_a\" : 0 }"

    }

}