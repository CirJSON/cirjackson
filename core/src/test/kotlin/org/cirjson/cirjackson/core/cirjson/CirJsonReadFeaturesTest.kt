package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.StreamReadCapability
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.exception.StreamReadException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.fail

class CirJsonReadFeaturesTest : TestBase() {

    private val factory = sharedStreamFactory()

    @Test
    fun testDefaultSettings() {
        for (mode in ALL_PARSER_MODES) {
            val parser = createParser(factory, mode, "123")

            assertFalse(parser.isReadingTypeIdPossible)

            assertFalse(parser.streamReadCapabilities.isEnabled(StreamReadCapability.DUPLICATE_PROPERTIES))
            assertFalse(parser.streamReadCapabilities.isEnabled(StreamReadCapability.SCALARS_AS_OBJECTS))
            assertFalse(parser.streamReadCapabilities.isEnabled(StreamReadCapability.UNTYPED_SCALARS))
            assertFalse(parser.streamReadCapabilities.isEnabled(StreamReadCapability.EXACT_FLOATS))

            assertFalse((parser as CirJsonParserBase).isEnabled(CirJsonReadFeature.ALLOW_JAVA_COMMENTS))
            assertFalse(parser.isEnabled(CirJsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS))
            assertFalse(parser.isEnabled(CirJsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES))
            assertFalse(parser.isEnabled(CirJsonReadFeature.ALLOW_SINGLE_QUOTES))

            parser.close()
        }
    }

    @Test
    fun testQuotesRequired() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            quotesRequired(mode)
        }
    }

    private fun quotesRequired(mode: Int) {
        try {
            createParser(mode, "{ __cirJsonId__ : \"root\" }").use { parser ->
                assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
                parser.nextToken()
                fail("Should not pass")
            }
        } catch (e: StreamReadException) {
            verifyException(e, "was expecting double-quote to start")
        }
    }

    @Test
    fun testTabsDefault() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            tabsDefault(mode)
        }
    }

    private fun tabsDefault(mode: Int) {
        val factory = streamFactoryBuilder().disable(CirJsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS).build()

        try {
            createParser(factory, mode, "[\"tab:\t\"]").use { parser ->
                assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
                parser.nextToken()
                parser.text
                fail("Should not pass")
            }
        } catch (e: StreamReadException) {
            verifyException(e, "Illegal unquoted character")
        }
    }

    @Test
    fun testTabsEnabled() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            tabsEnabled(mode)
        }
    }

    private fun tabsEnabled(mode: Int) {
        val factory = streamFactoryBuilder().enable(CirJsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS).build()
        val name = "a\tb"
        val value = "\t"
        val doc = "{ \"__cirJsonId__\" : \"root\", ${quote(name)} : ${quote(value)} }"

        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals(name, parser.text)
        assertEquals(name, parser.currentName)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(value, parser.text)
        parser.close()
    }

}