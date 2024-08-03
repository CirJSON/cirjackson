package org.cirjson.cirjackson.core.symbols

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.ObjectReadContext
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals

class SymbolsViaParserTest : TestBase() {

    @Test
    fun test17CharSymbols() {
        test17Chars(false)
    }

    @Test
    fun test17ByteSymbols() {
        test17Chars(true)
    }

    /*
     *******************************************************************************************************************
     * Secondary test methods
     *******************************************************************************************************************
     */

    private fun test17Chars(useBytes: Boolean) {
        val doc = createDoc17()
        val factory = CirJsonFactory()

        val parser = if (useBytes) {
            factory.createParser(ObjectReadContext.empty(), doc.toByteArray(StandardCharsets.UTF_8))
        } else {
            factory.createParser(ObjectReadContext.empty(), doc)
        }

        val symbols = HashSet<String>()
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        for (i in 1..50) {
            assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
            symbols.add(parser.currentName!!)
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
            stringBuilder.append(",\n\"lengthmatters").append(1000 * i).append("\": true")
        }

        stringBuilder.append("\n}")
        return stringBuilder.toString()
    }

    private fun testSymbolTableExpansion(useBytes: Boolean) {

    }

}