package org.cirjson.cirjackson.core.util

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class CirJsonBufferRecyclersTest : TestBase() {

    @Test
    fun testParserWithThreadLocalPool() {
        testParsers(CirJsonRecyclerPools.threadLocalPool(), -1, -1)
    }

    @Test
    fun testParserWithNoOpLocalPool() {
        testParsers(CirJsonRecyclerPools.nonRecyclingPool(), 0, 0)
    }

    @Test
    fun testParserWithDequePool() {
        testParsers(CirJsonRecyclerPools.newConcurrentDequePool(), 0, 1)
        testParsers(CirJsonRecyclerPools.sharedConcurrentDequePool(), null, null)
    }

    @Test
    fun testParserWithBoundedPool() {
        testParsers(CirJsonRecyclerPools.newBoundedPool(5), 0, 1)
        testParsers(CirJsonRecyclerPools.sharedBoundedPool(), null, null)
    }

    private fun testParsers(pool: RecyclerPool<BufferRecycler>, expectedSizeBefore: Int?, expectedSizeAfter: Int?) {
        val factory = CirJsonFactory.builder().recyclerPool(pool).build()
        val content = apostropheToQuote("{'__cirJsonId__':'root','a':123,'b':'foobar'}")

        for (mode in ALL_NON_THROTTLED_PARSER_MODES) {
            val parser = createParser(factory, mode, content)
            testParser(parser, pool, expectedSizeBefore, expectedSizeAfter)
        }
    }

    private fun testParser(parser: CirJsonParser, pool: RecyclerPool<BufferRecycler>, expectedSizeBefore: Int?,
            expectedSizeAfter: Int?) {
        if (expectedSizeBefore != null) {
            assertEquals(expectedSizeBefore, pool.pooledCount())
        }

        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertEquals("__cirJsonId__", parser.currentName)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("root", parser.text)
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("a", parser.currentName)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(123, parser.intValue)
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("b", parser.currentName)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("foobar", parser.text)

        parser.close()

        if (expectedSizeAfter != null) {
            assertEquals(expectedSizeAfter, pool.pooledCount())
        }
    }

    @Test
    fun testGeneratorWithThreadLocalPool() {
        testGenerators(CirJsonRecyclerPools.threadLocalPool(), -1, -1)
    }

    @Test
    fun testGeneratorWithNoOpLocalPool() {
        testGenerators(CirJsonRecyclerPools.nonRecyclingPool(), 0, 0)
    }

    @Test
    fun testGeneratorWithDequePool() {
        testGenerators(CirJsonRecyclerPools.newConcurrentDequePool(), 0, 1)
        testGenerators(CirJsonRecyclerPools.sharedConcurrentDequePool(), null, null)
    }

    @Test
    fun testGeneratorWithBoundedPool() {
        testGenerators(CirJsonRecyclerPools.newBoundedPool(5), 0, 1)
        testGenerators(CirJsonRecyclerPools.sharedBoundedPool(), null, null)
    }

    private fun testGenerators(pool: RecyclerPool<BufferRecycler>, expectedSizeBefore: Int?, expectedSizeAfter: Int?) {
        val factory = CirJsonFactory.builder().recyclerPool(pool).build()

        for (mode in ALL_GENERATOR_MODES) {
            val generator = createGenerator(factory, mode)
            testGenerator(generator, pool, expectedSizeBefore, expectedSizeAfter)
        }
    }

    private fun testGenerator(generator: CirJsonGenerator, pool: RecyclerPool<BufferRecycler>, expectedSizeBefore: Int?,
            expectedSizeAfter: Int?) {
        if (expectedSizeBefore != null) {
            assertEquals(expectedSizeBefore, pool.pooledCount())
        }

        generator.use {
            it.writeStartObject()
            it.writeObjectId(Any())
            it.writeNumberProperty("a", -42)
            it.writeStringProperty("b", "barfoo")
            it.writeEndObject()
        }

        if (expectedSizeAfter != null) {
            assertEquals(expectedSizeAfter, pool.pooledCount())
        }

        val output = generator.streamWriteOutputTarget!!
        assertEquals(apostropheToQuote("{'__cirJsonId__':'0','a':-42,'b':'barfoo'}"), output.toString())
    }

    @Test
    fun testCopyWithThreadLocalPool() {
        testCopy(CirJsonRecyclerPools.threadLocalPool())
    }

    @Test
    fun testCopyWithNoOpLocalPool() {
        testCopy(CirJsonRecyclerPools.nonRecyclingPool())
    }

    @Test
    fun testCopyWithDequePool() {
        testCopy(CirJsonRecyclerPools.newConcurrentDequePool())
        testCopy(CirJsonRecyclerPools.sharedConcurrentDequePool())
    }

    @Test
    fun testCopyWithBoundedPool() {
        testCopy(CirJsonRecyclerPools.newBoundedPool(5))
        testCopy(CirJsonRecyclerPools.sharedBoundedPool())
    }

    private fun testCopy(pool: RecyclerPool<BufferRecycler>) {
        val factory = CirJsonFactory.builder().recyclerPool(pool).build()
        val content = apostropheToQuote("{'__cirJsonId__':'root','a':123,'b':'foobar'}")

        for (parserMode in ALL_NON_THROTTLED_PARSER_MODES) {
            for (generatorMode in ALL_GENERATOR_MODES) {
                val parser = createParser(factory, parserMode, content)
                val generator = createGenerator(factory, generatorMode)
                testCopy(parser, generator)
            }
        }
    }

    private fun testCopy(parser: CirJsonParser, generator: CirJsonGenerator) {
        while (parser.nextToken() != null) {
            generator.copyCurrentEvent(parser)
        }

        parser.close()
        generator.close()

        val output = generator.streamWriteOutputTarget!!
        assertEquals(apostropheToQuote("{'__cirJsonId__':'root','a':123,'b':'foobar'}"), output.toString())
    }

}