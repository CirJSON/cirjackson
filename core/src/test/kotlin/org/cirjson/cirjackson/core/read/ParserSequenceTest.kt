package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.util.CirJsonParserSequence
import kotlin.test.*

class ParserSequenceTest : TestBase() {

    @Test
    fun testSimple() {
        for (mode1 in ALL_NON_ASYNC_PARSER_MODES) {
            for (mode2 in ALL_NON_ASYNC_PARSER_MODES) {
                simple(mode1, mode2)
            }
        }
    }

    private fun simple(mode1: Int, mode2: Int) {
        val parser1 = createParser(mode1, "[ \"root1\", 1 ]")
        val parser2 = createParser(mode2, "[ \"root2\", 2 ]")
        val sequence = CirJsonParserSequence.createFlattened(false, parser1, parser2)
        assertEquals(2, sequence.containedParsersCount())

        assertFalse(parser1.isClosed)
        assertFalse(parser2.isClosed)
        assertFalse(sequence.isClosed)
        assertToken(CirJsonToken.START_ARRAY, sequence.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, sequence.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, sequence.nextToken())
        assertEquals(1, sequence.intValue)
        assertToken(CirJsonToken.END_ARRAY, sequence.nextToken())
        assertFalse(parser1.isClosed)
        assertFalse(parser2.isClosed)
        assertFalse(sequence.isClosed)

        assertToken(CirJsonToken.START_ARRAY, sequence.nextToken())
        assertTrue(parser1.isClosed)
        assertFalse(parser2.isClosed)
        assertFalse(sequence.isClosed)
        assertToken(CirJsonToken.VALUE_STRING, sequence.nextToken())

        assertToken(CirJsonToken.VALUE_NUMBER_INT, sequence.nextToken())
        assertEquals(2, sequence.intValue)
        assertToken(CirJsonToken.END_ARRAY, sequence.nextToken())
        assertTrue(parser1.isClosed)
        assertFalse(parser2.isClosed)
        assertFalse(sequence.isClosed)

        assertNull(sequence.nextToken())
        assertTrue(parser1.isClosed)
        assertTrue(parser2.isClosed)
        assertTrue(sequence.isClosed)

        sequence.close()
    }

    @Test
    fun testMultiLevel() {
        for (mode1 in ALL_NON_ASYNC_PARSER_MODES) {
            for (mode2 in ALL_NON_ASYNC_PARSER_MODES) {
                for (mode3 in ALL_NON_ASYNC_PARSER_MODES) {
                    for (mode4 in ALL_NON_ASYNC_PARSER_MODES) {
                        multiLevel(mode1, mode2, mode3, mode4)
                    }
                }
            }
        }
    }

    private fun multiLevel(mode1: Int, mode2: Int, mode3: Int, mode4: Int) {
        val parser1 = createParser(mode1, "[ \"root1\", 1 ]")
        val parser2 = createParser(mode2, "[ \"root2\", 2 ]")
        val parser3 = createParser(mode3, "[ \"root3\", 3 ]")
        val parser4 = createParser(mode4, "[ \"root4\", 4 ]")
        val baseSequence1 = CirJsonParserSequence.createFlattened(true, parser1, parser2)
        val baseSequence2 = CirJsonParserSequence.createFlattened(true, parser3, parser4)
        val sequence = CirJsonParserSequence.createFlattened(false, baseSequence1, baseSequence2)

        assertToken(CirJsonToken.START_ARRAY, sequence.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, sequence.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, sequence.nextToken())
        assertToken(CirJsonToken.END_ARRAY, sequence.nextToken())

        assertToken(CirJsonToken.START_ARRAY, sequence.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, sequence.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, sequence.nextToken())
        assertToken(CirJsonToken.END_ARRAY, sequence.nextToken())

        assertToken(CirJsonToken.START_ARRAY, sequence.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, sequence.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, sequence.nextToken())
        assertToken(CirJsonToken.END_ARRAY, sequence.nextToken())

        assertToken(CirJsonToken.START_ARRAY, sequence.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, sequence.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, sequence.nextToken())
        assertToken(CirJsonToken.END_ARRAY, sequence.nextToken())

        assertNull(sequence.nextToken())
        assertTrue(parser1.isClosed)
        assertTrue(parser2.isClosed)
        assertTrue(parser3.isClosed)
        assertTrue(parser4.isClosed)
        assertTrue(baseSequence1.isClosed)
        assertTrue(baseSequence2.isClosed)
        assertTrue(sequence.isClosed)

        sequence.close()
    }

    @Test
    fun testInitializationDisabled() {
        for (mode1 in ALL_NON_ASYNC_PARSER_MODES) {
            for (mode2 in ALL_NON_ASYNC_PARSER_MODES) {
                initializationDisabled(mode1, mode2)
            }
        }
    }

    private fun initializationDisabled(mode1: Int, mode2: Int) {
        val (parser1, parser2) = createInitializationParsers(mode1, mode2)
        val sequence = CirJsonParserSequence.createFlattened(false, parser1, parser2)

        assertToken(CirJsonToken.VALUE_NUMBER_INT, sequence.nextToken())
        assertEquals(2, sequence.intValue)
        assertToken(CirJsonToken.VALUE_TRUE, sequence.nextToken())
        assertNull(sequence.nextToken())
        sequence.close()
    }

    @Test
    fun testInitializationEnabled() {
        for (mode1 in ALL_NON_ASYNC_PARSER_MODES) {
            for (mode2 in ALL_NON_ASYNC_PARSER_MODES) {
                initializationEnabled(mode1, mode2)
            }
        }
    }

    private fun initializationEnabled(mode1: Int, mode2: Int) {
        val (parser1, parser2) = createInitializationParsers(mode1, mode2)
        val sequence = CirJsonParserSequence.createFlattened(true, parser1, parser2)

        assertToken(CirJsonToken.VALUE_NUMBER_INT, sequence.nextToken())
        assertEquals(1, sequence.intValue)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, sequence.nextToken())
        assertEquals(2, sequence.intValue)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, sequence.nextToken())
        assertEquals(3, sequence.intValue)
        assertToken(CirJsonToken.VALUE_TRUE, sequence.nextToken())
        assertNull(sequence.nextToken())
        sequence.close()
    }

    private fun createInitializationParsers(mode1: Int, mode2: Int): Pair<CirJsonParser, CirJsonParser> {
        val parser1 = createParser(mode1, "1 2 ")
        val parser2 = createParser(mode2, "3 true ")
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser1.nextToken())
        assertEquals(1, parser1.intValue)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser2.nextToken())
        assertEquals(3, parser2.intValue)
        return parser1 to parser2
    }

}