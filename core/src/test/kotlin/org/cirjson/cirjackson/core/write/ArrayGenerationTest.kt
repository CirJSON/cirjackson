package org.cirjson.cirjackson.core.write

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.StreamWriteFeature
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArrayGenerationTest : TestBase() {

    private val slowFactory = newStreamFactory()

    private val fastFactory = CirJsonFactory.builder().enable(StreamWriteFeature.USE_FAST_DOUBLE_WRITER).build()

    private val factories = arrayOf(slowFactory, fastFactory)

    @Test
    fun testIntArray() {
        for (factory in factories) {
            for (generatorMode in ALL_GENERATOR_MODES) {
                for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                    intArray(factory, generatorMode, parserMode)
                }
            }
        }
    }

    private fun intArray(factory: CirJsonFactory, generatorMode: Int, parserMode: Int) {
        intArray(factory, generatorMode, parserMode, 0, 0, 0)
        intArray(factory, generatorMode, parserMode, 0, 1, 1)

        intArray(factory, generatorMode, parserMode, 1, 0, 0)
        intArray(factory, generatorMode, parserMode, 1, 1, 1)

        intArray(factory, generatorMode, parserMode, 15, 0, 0)
        intArray(factory, generatorMode, parserMode, 15, 2, 3)
        intArray(factory, generatorMode, parserMode, 39, 0, 0)
        intArray(factory, generatorMode, parserMode, 39, 4, 0)
        intArray(factory, generatorMode, parserMode, 271, 0, 0)
        intArray(factory, generatorMode, parserMode, 271, 0, 4)
        intArray(factory, generatorMode, parserMode, 5009, 0, 0)
        intArray(factory, generatorMode, parserMode, 5009, 0, 1)
    }

    private fun intArray(factory: CirJsonFactory, generatorMode: Int, parserMode: Int, elements: Int, pre: Int,
            post: Int) {
        val values = IntArray(elements + pre + post)

        for (i in pre..<pre + elements) {
            values[i] = i - pre
        }

        val generator = createGenerator(factory, generatorMode)
        generator.writeArray(values, pre, elements)
        generator.close()

        val doc = generator.streamWriteOutputTarget!!.toString()

        val parser = createParser(factory, parserMode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        for (i in 0..<elements) {
            if (i and 1 == 0) {
                assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
                assertEquals(i, parser.intValue)
            } else {
                assertEquals(i, parser.nextIntValue(-1))
                assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.currentToken())
            }
        }

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testLongArray() {
        for (factory in factories) {
            for (generatorMode in ALL_GENERATOR_MODES) {
                for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                    longArray(factory, generatorMode, parserMode)
                }
            }
        }
    }

    private fun longArray(factory: CirJsonFactory, generatorMode: Int, parserMode: Int) {
        longArray(factory, generatorMode, parserMode, 0, 0, 0)
        longArray(factory, generatorMode, parserMode, 0, 1, 1)

        longArray(factory, generatorMode, parserMode, 1, 0, 0)
        longArray(factory, generatorMode, parserMode, 1, 1, 1)

        longArray(factory, generatorMode, parserMode, 15, 0, 0)
        longArray(factory, generatorMode, parserMode, 15, 2, 3)
        longArray(factory, generatorMode, parserMode, 39, 0, 0)
        longArray(factory, generatorMode, parserMode, 39, 4, 0)
        longArray(factory, generatorMode, parserMode, 271, 0, 0)
        longArray(factory, generatorMode, parserMode, 271, 0, 4)
        longArray(factory, generatorMode, parserMode, 5009, 0, 0)
        longArray(factory, generatorMode, parserMode, 5009, 0, 1)
    }

    private fun longArray(factory: CirJsonFactory, generatorMode: Int, parserMode: Int, elements: Int, pre: Int,
            post: Int) {
        val values = LongArray(elements + pre + post)

        for (i in pre..<pre + elements) {
            values[i] = i.toLong() - pre
        }

        val generator = createGenerator(factory, generatorMode)
        generator.writeArray(values, pre, elements)
        generator.close()

        val doc = generator.streamWriteOutputTarget!!.toString()

        val parser = createParser(factory, parserMode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        for (i in 0..<elements) {
            if (i and 1 == 0) {
                assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
                assertEquals(i.toLong(), parser.longValue)
            } else {
                assertEquals(i.toLong(), parser.nextLongValue(-1L))
                assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.currentToken())
            }
        }

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testDoubleArray() {
        for (factory in factories) {
            for (generatorMode in ALL_GENERATOR_MODES) {
                for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                    doubleArray(factory, generatorMode, parserMode)
                }
            }
        }
    }

    private fun doubleArray(factory: CirJsonFactory, generatorMode: Int, parserMode: Int) {
        doubleArray(factory, generatorMode, parserMode, 0, 0, 0)
        doubleArray(factory, generatorMode, parserMode, 0, 1, 1)

        doubleArray(factory, generatorMode, parserMode, 1, 0, 0)
        doubleArray(factory, generatorMode, parserMode, 1, 1, 1)

        doubleArray(factory, generatorMode, parserMode, 15, 0, 0)
        doubleArray(factory, generatorMode, parserMode, 15, 2, 3)
        doubleArray(factory, generatorMode, parserMode, 39, 0, 0)
        doubleArray(factory, generatorMode, parserMode, 39, 4, 0)
        doubleArray(factory, generatorMode, parserMode, 271, 0, 0)
        doubleArray(factory, generatorMode, parserMode, 271, 0, 4)
        doubleArray(factory, generatorMode, parserMode, 5009, 0, 0)
        doubleArray(factory, generatorMode, parserMode, 5009, 0, 1)
    }

    private fun doubleArray(factory: CirJsonFactory, generatorMode: Int, parserMode: Int, elements: Int, pre: Int,
            post: Int) {
        val values = DoubleArray(elements + pre + post)

        for (i in pre..<pre + elements) {
            values[i] = i.toDouble() - pre
        }

        val generator = createGenerator(factory, generatorMode)
        generator.writeArray(values, pre, elements)
        generator.close()

        val doc = generator.streamWriteOutputTarget!!.toString()

        val parser = createParser(factory, parserMode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        for (i in 0..<elements) {
            assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
            assertEquals(i.toDouble(), parser.doubleValue)
        }

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testStringArray() {
        for (factory in factories) {
            for (generatorMode in ALL_GENERATOR_MODES) {
                for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                    stringArray(factory, generatorMode, parserMode)
                }
            }
        }
    }

    private fun stringArray(factory: CirJsonFactory, generatorMode: Int, parserMode: Int) {
        stringArray(factory, generatorMode, parserMode, 0, 0, 0)
        stringArray(factory, generatorMode, parserMode, 0, 1, 1)

        stringArray(factory, generatorMode, parserMode, 1, 0, 0)
        stringArray(factory, generatorMode, parserMode, 1, 1, 1)

        stringArray(factory, generatorMode, parserMode, 15, 0, 0)
        stringArray(factory, generatorMode, parserMode, 15, 2, 3)
        stringArray(factory, generatorMode, parserMode, 39, 0, 0)
        stringArray(factory, generatorMode, parserMode, 39, 4, 0)
        stringArray(factory, generatorMode, parserMode, 271, 0, 0)
        stringArray(factory, generatorMode, parserMode, 271, 0, 4)
        stringArray(factory, generatorMode, parserMode, 5009, 0, 0)
        stringArray(factory, generatorMode, parserMode, 5009, 0, 1)
    }

    private fun stringArray(factory: CirJsonFactory, generatorMode: Int, parserMode: Int, elements: Int, pre: Int,
            post: Int) {
        val byteLength = 16
        val random = Random.Default
        val values = Array(elements + pre + post) { "" }

        for (i in pre..<pre + elements) {
            val content = ByteArray(byteLength)
            random.nextBytes(content)
            values[i] = String(content)
        }

        val generator = createGenerator(factory, generatorMode)
        generator.writeArray(values, pre, elements)
        generator.close()

        val doc = generator.streamWriteOutputTarget!!.toString()

        val parser = createParser(factory, parserMode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        for (i in 0..<elements) {
            if (i and 1 == 0) {
                assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
                assertEquals(values[pre + i], parser.valueAsString)
            } else {
                assertEquals(values[pre + i], parser.nextTextValue())
                assertToken(CirJsonToken.VALUE_STRING, parser.currentToken())
            }
        }

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

}