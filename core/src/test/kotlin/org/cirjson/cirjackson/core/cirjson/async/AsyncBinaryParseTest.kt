package org.cirjson.cirjackson.core.cirjson.async

import org.cirjson.cirjackson.core.AsyncTestBase
import org.cirjson.cirjackson.core.CirJsonToken
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AsyncBinaryParseTest : AsyncTestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testBinaryAsRoot() {
        for (asyncMode in ALL_ASYNC_MODES) {
            for (generatorMode in ALL_GENERATOR_MODES) {
                for (size in SIZES) {
                    binaryAsRoot(asyncMode, generatorMode, size, 1, Int.MAX_VALUE)
                    binaryAsRoot(asyncMode, generatorMode, size, 0, 3)
                    binaryAsRoot(asyncMode, generatorMode, size, 1, 1)
                }
            }
        }
    }

    private fun binaryAsRoot(asyncMode: Int, generatorMode: Int, size: Int, offset: Int, readSize: Int) {
        val binary = generateData(size)
        val generator = createGenerator(factory, generatorMode)
        generator.writeBinary(binary)
        generator.close()
        val smile = generator.streamWriteOutputTarget!!.toString().toByteArray()

        var parser = createAsync(factory, asyncMode, readSize, smile, offset)

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        val actual = parser.binaryValue
        assertContentEquals(binary, actual)
        assertNull(parser.nextToken())
        parser.close()

        parser = createAsync(factory, asyncMode, readSize, smile, offset)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testBinaryAsArray() {
        for (asyncMode in ALL_ASYNC_MODES) {
            for (generatorMode in ALL_GENERATOR_MODES) {
                for (size in SIZES) {
                    binaryAsArray(asyncMode, generatorMode, size, 1, Int.MAX_VALUE)
                    binaryAsArray(asyncMode, generatorMode, size, 0, 3)
                    binaryAsArray(asyncMode, generatorMode, size, 1, 1)
                }
            }
        }
    }

    private fun binaryAsArray(asyncMode: Int, generatorMode: Int, size: Int, offset: Int, readSize: Int) {
        val binary = generateData(size)
        val generator = createGenerator(factory, generatorMode)
        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.writeBinary(binary)
        generator.writeNumber(1)
        generator.writeEndArray()
        generator.close()
        val smile = generator.streamWriteOutputTarget!!.toString().toByteArray()

        var parser = createAsync(factory, asyncMode, readSize, smile, offset)

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("0", parser.currentText())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        val actual = parser.binaryValue
        assertContentEquals(binary, actual)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(1, parser.intValue)
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()

        parser = createAsync(factory, asyncMode, readSize, smile, offset)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testBinaryAsObject() {
        for (asyncMode in ALL_ASYNC_MODES) {
            for (generatorMode in ALL_GENERATOR_MODES) {
                for (size in SIZES) {
                    binaryAsObject(asyncMode, generatorMode, size, 1, Int.MAX_VALUE)
                    binaryAsObject(asyncMode, generatorMode, size, 0, 3)
                    binaryAsObject(asyncMode, generatorMode, size, 1, 1)
                }
            }
        }
    }

    private fun binaryAsObject(asyncMode: Int, generatorMode: Int, size: Int, offset: Int, readSize: Int) {
        val binary = generateData(size)
        val generator = createGenerator(factory, generatorMode)
        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeName("binary")
        generator.writeBinary(binary)
        generator.writeName("test")
        generator.writeNumber(1)
        generator.writeEndObject()
        generator.close()
        val smile = generator.streamWriteOutputTarget!!.toString().toByteArray()

        var parser = createAsync(factory, asyncMode, readSize, smile, offset)

        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertEquals("__cirJsonId__", parser.currentText())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("0", parser.currentText())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("binary", parser.currentText())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        val actual = parser.binaryValue
        assertContentEquals(binary, actual)

        val bytes = ByteArrayOutputStream(actual.size)
        assertEquals(actual.size, parser.parser.readBinaryValue(bytes))
        assertContentEquals(binary, bytes.toByteArray())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("test", parser.currentText())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(1, parser.intValue)
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()

        parser = createAsync(factory, asyncMode, readSize, smile, offset)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    private fun generateData(size: Int): ByteArray {
        return ByteArray(size) { it.mod(255.toByte()) }
    }

    companion object {

        private val SIZES = intArrayOf(1, 2, 3, 4, 5, 7, 11, 90, 350, 1900, 6000, 19000, 65000, 139000)

    }

}