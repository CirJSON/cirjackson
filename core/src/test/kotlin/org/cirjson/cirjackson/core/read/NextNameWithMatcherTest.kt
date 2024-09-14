package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.symbols.PropertyNameMatcher
import org.cirjson.cirjackson.core.util.Named
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class NextNameWithMatcherTest : TestBase() {

    private val factory = newStreamFactory()

    private val matcherCaseSensitive = factory.constructNameMatcher(NAMED_LIST, true)

    private val matcherCaseInsensitive =
            factory.constructCaseInsensitiveNameMatcher(NAMED_LIST, true, Locale("en", "US"))

    @Test
    fun testSimpleCaseSensitive() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            simple(createParser(mode, DOC), matcherCaseSensitive, NAMES)
        }
    }

    @Test
    fun testSimpleCaseInsensitive() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            simple(createParser(mode, DOC), matcherCaseInsensitive, NAMES)
            simple(createParser(mode, DOC_CASE_MISMATCH), matcherCaseInsensitive, NAMES_CASE_MISMATCH)
        }
    }

    private fun simple(parser: CirJsonParser, matcher: PropertyNameMatcher, names: List<String>) {
        assertEquals(PropertyNameMatcher.MATCH_ODD_TOKEN, parser.nextNameMatch(matcher))
        assertToken(CirJsonToken.START_OBJECT, parser.currentToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertEquals(1, parser.nextNameMatch(matcher))
        assertEquals(names[1], parser.currentName)
        assertEquals(PropertyNameMatcher.MATCH_ODD_TOKEN, parser.nextNameMatch(matcher))
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.currentToken())
        assertEquals(4, parser.intValue)

        assertEquals(0, parser.nextNameMatch(matcher))
        assertEquals(names[0], parser.currentName)
        assertEquals(PropertyNameMatcher.MATCH_ODD_TOKEN, parser.nextNameMatch(matcher))
        assertToken(CirJsonToken.VALUE_TRUE, parser.currentToken())

        assertEquals(2, parser.nextNameMatch(matcher))
        assertEquals(names[2], parser.currentName)
        assertEquals(PropertyNameMatcher.MATCH_ODD_TOKEN, parser.nextNameMatch(matcher))
        assertToken(CirJsonToken.VALUE_STRING, parser.currentToken())
        assertEquals("Billy-Bob Burger", parser.text)

        assertEquals(PropertyNameMatcher.MATCH_UNKNOWN_NAME, parser.nextNameMatch(matcher))
        assertEquals("extra", parser.currentName)
        assertEquals(PropertyNameMatcher.MATCH_ODD_TOKEN, parser.nextNameMatch(matcher))
        assertToken(CirJsonToken.START_ARRAY, parser.currentToken())
        assertEquals(PropertyNameMatcher.MATCH_ODD_TOKEN, parser.nextNameMatch(matcher))
        assertToken(CirJsonToken.VALUE_STRING, parser.currentToken())
        assertEquals(PropertyNameMatcher.MATCH_ODD_TOKEN, parser.nextNameMatch(matcher))
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.currentToken())
        assertEquals(0, parser.intValue)
        assertEquals(PropertyNameMatcher.MATCH_ODD_TOKEN, parser.nextNameMatch(matcher))
        assertToken(CirJsonToken.END_ARRAY, parser.currentToken())

        assertEquals(3, parser.nextNameMatch(matcher))
        assertEquals(names[3], parser.currentName)
        assertEquals(PropertyNameMatcher.MATCH_ODD_TOKEN, parser.nextNameMatch(matcher))
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.currentToken())
        assertEquals(0.25, parser.doubleValue)

        assertEquals(PropertyNameMatcher.MATCH_END_OBJECT, parser.nextNameMatch(matcher))
        assertToken(CirJsonToken.END_OBJECT, parser.currentToken())

        parser.close()
    }

    companion object {

        private val NAMES = listOf("enabled", "a", "longerName", "otherStuff3")

        private val NAMES_CASE_MISMATCH = listOf("ENABLED", "A", "LongerName", "otherStuff3")

        private val NAMED_LIST = namedFromStrings()

        private val DOC = apostropheToQuote(
                "{ '__cirJsonId__' : 'root', 'a' : 4, 'enabled' : true, 'longerName' : 'Billy-Bob Burger', 'extra' : [ 'extra', 0], 'otherStuff3' : 0.25 }")

        private val DOC_CASE_MISMATCH = apostropheToQuote(
                "{ '__cirJsonId__' : 'root', 'A' : 4, 'ENABLED' : true, 'LongerName' : 'Billy-Bob Burger', 'extra' : ['extra', 0 ], 'otherStuff3' : 0.25 }")

        private fun namedFromStrings(): List<Named> {
            return NAMES.map { Named.fromString(it)!! }
        }

    }

}