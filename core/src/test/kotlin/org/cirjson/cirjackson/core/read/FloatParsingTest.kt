package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.StreamReadFeature
import org.cirjson.cirjackson.core.TestBase
import kotlin.test.Test
import kotlin.test.assertEquals

@Suppress("FloatingPointLiteralPrecision")
class FloatParsingTest : TestBase() {

    @Test
    fun testFloatArray() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            floatArray(mode, true)
            floatArray(mode, false)
        }
    }

    private fun floatArray(mode: Int, useFastParser: Boolean) {
        val factory = if (useFastParser) {
            streamFactoryBuilder().enable(StreamReadFeature.USE_FAST_DOUBLE_PARSER).build()
        } else {
            newStreamFactory()
        }
        val parser = createParser(factory, mode, DOC)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(7.038531e-26f, parser.floatValue)

        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(1.199999988079071f, parser.floatValue)

        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(3.4028235677973366e38f, parser.floatValue)

        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(7.006492321624086e-46f, parser.floatValue)

        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())

        parser.close()
    }

    companion object {

        private val DOC = readResource("/data/floats.cirjson")

    }

}