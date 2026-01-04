package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestWithTonsaSymbols : TestBase() {

    @Test
    fun testSimple() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            simple(mode)
        }
    }

    private fun simple(mode: Int) {
        val factory = newStreamFactory()
        val doc = buildDoc()

        for (x in 0..<3) {
            val parser = createParser(factory, mode, doc)
            assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
            assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

            for (i in 0..<PROP_COUNT) {
                assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
                assertEquals(fieldNameFor(i), parser.currentName())
                assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
                assertEquals(i, parser.intValue)
            }

            assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
            parser.close()
        }
    }

    private fun buildDoc(): String {
        val stringBuilder = StringBuilder(PROP_COUNT * 12 + 22)
        stringBuilder.append("{\"__cirJsonId__\":\"root\"")

        for (i in 0..<PROP_COUNT) {
            stringBuilder.append(",\"")
            stringBuilder.append(fieldNameFor(i))
            stringBuilder.append("\":")
            stringBuilder.append(i)
        }

        stringBuilder.append("}")
        return stringBuilder.toString()
    }

    companion object {

        private const val PROP_COUNT = 5000

    }

}