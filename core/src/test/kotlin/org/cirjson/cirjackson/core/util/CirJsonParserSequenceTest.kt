package org.cirjson.cirjackson.core.util

import org.cirjson.cirjackson.core.ObjectReadContext
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.cirjson.ReaderBasedCirJsonParser
import org.cirjson.cirjackson.core.cirjson.UTF8StreamCirJsonParser
import org.cirjson.cirjackson.core.symbols.ByteQuadsCanonicalizer
import org.cirjson.cirjackson.core.symbols.CharsToNameCanonicalizer
import java.io.ByteArrayInputStream
import kotlin.test.*

class CirJsonParserSequenceTest : TestBase() {

    @Test
    fun testClose() {
        val context = testIOContext()
        val readerBasedCirJsonParser = ReaderBasedCirJsonParser(ObjectReadContext.empty(), context, 2, 0, null,
                CharsToNameCanonicalizer.createRoot(CirJsonFactory()))
        val cirJsonParserSequence =
                CirJsonParserSequence.createFlattened(true, readerBasedCirJsonParser, readerBasedCirJsonParser)

        assertFalse(cirJsonParserSequence.isClosed)

        cirJsonParserSequence.close()

        assertTrue(readerBasedCirJsonParser.isClosed)
        assertNull(cirJsonParserSequence.nextToken())
    }

    @Test
    fun testSkipChildren() {
        val cirJsonParser = createParser(MODE_READER, "1")
        val context = testIOContext()
        val byteArray = ByteArray(8)
        val byteArrayInputStream = ByteArrayInputStream(byteArray, 0, 58)
        val utf8StreamCirJsonParser = UTF8StreamCirJsonParser(ObjectReadContext.empty(), context, 0, 0,
                byteArrayInputStream, ByteQuadsCanonicalizer.createRoot(), byteArray, -1, 9, 0, true)
        val cirJsonParserDelegate = CirJsonParserDelegate(cirJsonParser)
        val cirJsonParserSequence =
                CirJsonParserSequence.createFlattened(true, utf8StreamCirJsonParser, cirJsonParserDelegate)
        val cirJsonParserSequence2 = cirJsonParserSequence.skipChildren() as CirJsonParserSequence

        assertEquals(2, cirJsonParserSequence2.containedParsersCount())
    }

}