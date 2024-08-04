package org.cirjson.cirjackson.core.symbols

import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.ObjectReadContext
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.TokenStreamFactory
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.cirjson.UTF8StreamCirJsonParser
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestSymbolTables : TestBase() {

    @Test
    fun testSyntheticWithChars() {
        val symbols = CharsToNameCanonicalizer.createRoot(CIRJSON_FACTORY, -1).makeChild()
        val count = 12000

        for (i in 0..<count) {
            val id = fieldNameFor(i)
            val ch = id.toCharArray()
            symbols.findSymbol(ch, 0, ch.size, symbols.calculateHash(id))
        }

        assertEquals(count, symbols.size)
        assertEquals(16384, symbols.bucketCount)
        assertEquals(3417, symbols.collisionCount)
        assertEquals(6, symbols.maxCollisionLength)
        symbols.verifyInternalConsistency()
    }

    @Test
    fun testSyntheticWithBytesNew() {
        val symbols = ByteQuadsCanonicalizer.createRoot(33333).makeChild(TokenStreamFactory.Feature.collectDefaults())
        assertTrue(symbols.isCanonicalizing)

        val count = 12000

        for (i in 0..<count) {
            val id = fieldNameFor(i)
            val quads = calcQuads(id.toByteArray(StandardCharsets.UTF_8))
            symbols.addName(id, quads, quads.size)
        }

        assertEquals(count, symbols.size)
    }

    @Test
    fun testThousandsOfSymbolsWithChars() {
        val symbolsRoot = ByteQuadsCanonicalizer.createRoot(33333)
        var expected = 0
        lateinit var symbols: ByteQuadsCanonicalizer

        for (doc in 0..<100) {
            symbols = symbolsRoot.makeChild(TokenStreamFactory.Feature.collectDefaults())

            for (i in 0..<250) {
                val name = "f_${doc}_$i"
                val quads = calcQuads(name.toByteArray(StandardCharsets.UTF_8))
                symbols.addName(name, quads, quads.size)
                val n = symbols.findName(quads, quads.size)
                assertEquals(name, n)
            }

            symbols.release()
            expected += 250

            if (expected > ByteQuadsCanonicalizer.MAX_ENTRIES_FOR_REUSE) {
                expected = 0
            }

            assertEquals(expected, symbolsRoot.size)
        }

        assertEquals(6250, symbols.size)
        assertEquals(4761, symbols.primaryCount)
        assertEquals(1190, symbols.secondaryCount)
        assertEquals(299, symbols.tertiaryCount)
        assertEquals(0, symbols.spilloverCount)
    }

    @Test
    fun testByteBasedSymbolTable() {
        val cirJson = apostropheToQuote(
                "{'__cirJsonId__':'root', 'abc':1, 'abc\\u0000':2, '\\u0000abc':3, 'abc123':4,'abcd1234':5,'abcd1234a':6,'abcd1234abcd':7,'abcd1234abcd1':8}")
        val factory = CirJsonFactory()

        var parser = factory.createParser(ObjectReadContext.empty(), cirJson.toByteArray(StandardCharsets.UTF_8))
        var symbols = (parser as UTF8StreamCirJsonParser).symbols
        assertEquals(0, symbols.size)
        streamThrough(parser)
        assertEquals(9, symbols.size)
        parser.close()

        parser = factory.createParser(ObjectReadContext.empty(), cirJson.toByteArray(StandardCharsets.UTF_8))
        streamThrough(parser)
        symbols = (parser as UTF8StreamCirJsonParser).symbols
        assertEquals(9, symbols.size)
        parser.close()

        parser = factory.createParser(ObjectReadContext.empty(), cirJson.toByteArray(StandardCharsets.UTF_8))
        streamThrough(parser)
        symbols = (parser as UTF8StreamCirJsonParser).symbols
        assertEquals(9, symbols.size)
        parser.close()
    }

    @Suppress("ControlFlowWithEmptyBody")
    private fun streamThrough(parser: CirJsonParser) {
        while (parser.nextToken() != null) {
        }
    }

    @Test
    fun testCollisionsWithChars() {
        val symbols = CharsToNameCanonicalizer.createRoot(CIRJSON_FACTORY, -1).makeChild()
        val count = 30000

        for (i in 0..<count) {
            val id = (10000 + i).toString()
            val ch = id.toCharArray()
            symbols.findSymbol(ch, 0, ch.size, symbols.calculateHash(id))
        }

        assertEquals(count, symbols.size)
        assertEquals(65536, symbols.bucketCount)
        assertEquals(7194, symbols.collisionCount)
        assertEquals(5, symbols.maxCollisionLength)
    }

    @Test
    fun testCollisionsWithBytesNewA() {
        val symbols = ByteQuadsCanonicalizer.createRoot(1).makeChild(TokenStreamFactory.Feature.collectDefaults())
        val count = 43000

        for (i in 0..<count) {
            val id = (10000 + i).toString()
            val quads = calcQuads(id.toByteArray(StandardCharsets.UTF_8))
            symbols.addName(id, quads, quads.size)
        }

        assertEquals(count, symbols.size)
        assertEquals(65536, symbols.bucketCount)
        assertEquals(32342, symbols.primaryCount)
        assertEquals(8863, symbols.secondaryCount)
        assertEquals(1795, symbols.tertiaryCount)
        assertEquals(0, symbols.spilloverCount)
    }

    @Test
    fun testCollisionsWithBytesNewB() {
        val symbols = ByteQuadsCanonicalizer.createRoot(1).makeChild(TokenStreamFactory.Feature.collectDefaults())
        val count = 10000

        for (i in 0..<count) {
            val id = i.toString()
            val quads = calcQuads(id.toByteArray(StandardCharsets.UTF_8))
            symbols.addName(id, quads, quads.size)
        }

        assertEquals(count, symbols.size)
        assertEquals(16384, symbols.bucketCount)
        assertEquals(5402, symbols.primaryCount)
        assertEquals(2744, symbols.secondaryCount)
        assertEquals(1834, symbols.tertiaryCount)
        assertEquals(20, symbols.spilloverCount)
    }

    @Test
    fun testShortNameCollisionsViaParser() {
        val factory = CirJsonFactory()
        val cirJson = shortDoc()
        var parser = factory.createParser(ObjectReadContext.empty(), cirJson)
        streamThrough(parser)
        parser.close()

        parser = factory.createParser(ObjectReadContext.empty(), cirJson.toByteArray(StandardCharsets.UTF_8))
        streamThrough(parser)
        parser.close()
    }

    private fun shortDoc(): String {
        val stringBuilder = StringBuilder()
        stringBuilder.append("{\n")
        stringBuilder.append("\"__cirJsonId__\":\"root\"")

        for (i in 0..<400) {
            stringBuilder.append(",\n\"")
            val c = i.toChar()

            if (c.isLetterOrDigit()) {
                stringBuilder.append(c)
            } else {
                stringBuilder.append("\\u${i.toString(16).lowercase().padStart(4, '0')}")
            }

            stringBuilder.append("\" : ")
            stringBuilder.append(i)
        }

        stringBuilder.append("\n}")
        return stringBuilder.toString()
    }

    @Test
    fun testShortQuotedDirectChars() {
        val symbols = CharsToNameCanonicalizer.createRoot(CIRJSON_FACTORY, -1).makeChild()
        val count = 400

        for (i in 0..<count) {
            val id = "\\u${i.toString(16).lowercase().padStart(4, '0')}"
            val ch = id.toCharArray()
            symbols.findSymbol(ch, 0, ch.size, symbols.calculateHash(id))
        }

        assertEquals(count, symbols.size)
        assertEquals(1024, symbols.bucketCount)
        assertEquals(54, symbols.collisionCount)
        assertEquals(2, symbols.maxCollisionLength)
    }

    @Test
    fun testShortQuotedDirectBytes() {
        val symbols = ByteQuadsCanonicalizer.createRoot(123).makeChild(TokenStreamFactory.Feature.collectDefaults())
        val count = 400

        for (i in 0..<count) {
            val id = "\\u${i.toString(16).lowercase().padStart(4, '0')}"
            val quads = calcQuads(id.toByteArray(StandardCharsets.UTF_8))
            symbols.addName(id, quads, quads.size)
        }

        assertEquals(count, symbols.size)
        assertEquals(512, symbols.bucketCount)
        assertEquals(285, symbols.primaryCount)
        assertEquals(90, symbols.secondaryCount)
        assertEquals(25, symbols.tertiaryCount)
        assertEquals(0, symbols.spilloverCount)
    }

    @Test
    fun testShortNameCollisionsDirectChars() {
        val symbols = CharsToNameCanonicalizer.createRoot(CIRJSON_FACTORY, -1).makeChild()
        val count = 600

        for (i in 0..<count) {
            val id = i.toChar().toString()
            val ch = id.toCharArray()
            symbols.findSymbol(ch, 0, ch.size, symbols.calculateHash(id))
        }

        assertEquals(count, symbols.size)
        assertEquals(1024, symbols.bucketCount)
        assertEquals(24, symbols.collisionCount)
        assertEquals(1, symbols.maxCollisionLength)
    }

    @Test
    fun testShortNameCollisionsDirectBytes() {
        val symbols = ByteQuadsCanonicalizer.createRoot(333).makeChild(TokenStreamFactory.Feature.collectDefaults())
        val count = 700

        for (i in 0..<count) {
            val id = i.toChar().toString()
            val quads = calcQuads(id.toByteArray(StandardCharsets.UTF_8))
            symbols.addName(id, quads, quads.size)
        }

        assertEquals(count, symbols.size)
        assertEquals(1024, symbols.bucketCount)
        assertEquals(564, symbols.primaryCount)
        assertEquals(122, symbols.secondaryCount)
        assertEquals(14, symbols.tertiaryCount)
        assertEquals(0, symbols.spilloverCount)
    }

    @Test
    fun testLongSymbols17Bytes() {
        val symbolsBytes = ByteQuadsCanonicalizer.createRoot(3).makeChild(TokenStreamFactory.Feature.collectDefaults())
        val symbolsChars = CharsToNameCanonicalizer.createRoot(CIRJSON_FACTORY, 3).makeChild()

        for (i in 1001..1050) {
            val id = "lengthMatters$i"
            val quads = calcQuads(id.toByteArray(StandardCharsets.UTF_8))
            symbolsBytes.addName(id, quads, quads.size)
            val ch = id.toCharArray()
            symbolsChars.findSymbol(ch, 0, ch.size, symbolsChars.calculateHash(id))
        }

        assertEquals(50, symbolsBytes.size)
        assertEquals(50, symbolsChars.size)
    }

    companion object {

        private val CIRJSON_FACTORY = CirJsonFactory()

    }

}