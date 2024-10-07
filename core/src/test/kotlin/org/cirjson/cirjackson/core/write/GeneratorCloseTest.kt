package org.cirjson.cirjackson.core.write

import org.cirjson.cirjackson.core.CirJsonEncoding
import org.cirjson.cirjackson.core.ObjectWriteContext
import org.cirjson.cirjackson.core.StreamWriteFeature
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.util.TestingByteArrayOutputStream
import org.cirjson.cirjackson.core.util.TestingStringWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneratorCloseTest : TestBase() {

    @Test
    fun testNoAutoCloseGenerator() {
        var factory = CirJsonFactory()

        assertTrue(factory.isEnabled(StreamWriteFeature.AUTO_CLOSE_TARGET))
        factory = factory.rebuild().disable(StreamWriteFeature.AUTO_CLOSE_TARGET).build()
        assertFalse(factory.isEnabled(StreamWriteFeature.AUTO_CLOSE_TARGET))
        val writer = TestingStringWriter()
        var generator = factory.createGenerator(ObjectWriteContext.empty(), writer)
        assertFalse(writer.isClosed)
        generator.writeNumber(39)
        generator.close()
        assertFalse(writer.isClosed)

        val output = TestingByteArrayOutputStream()
        generator = factory.createGenerator(ObjectWriteContext.empty(), output, CirJsonEncoding.UTF8)
        assertFalse(output.isClosed)
        generator.writeNumber(39)
        generator.close()
        assertFalse(output.isClosed)
    }

    @Test
    fun testAutoCloseGenerator() {
        val factory = CirJsonFactory.builder().enable(StreamWriteFeature.AUTO_CLOSE_TARGET).build()
        val writer = TestingStringWriter()
        var generator = factory.createGenerator(ObjectWriteContext.empty(), writer)
        assertFalse(writer.isClosed)
        generator.writeNumber(39)
        generator.close()
        assertTrue(writer.isClosed)

        val output = TestingByteArrayOutputStream()
        generator = factory.createGenerator(ObjectWriteContext.empty(), output)
        assertFalse(output.isClosed)
        generator.writeNumber(39)
        generator.close()
        assertTrue(output.isClosed)
    }

    @Test
    fun testAutoCloseArraysAndObjects() {
        for (mode in ALL_GENERATOR_MODES) {
            autoCloseArraysAndObjects(mode)
        }
    }

    private fun autoCloseArraysAndObjects(mode: Int) {
        val factory = CirJsonFactory()
        assertTrue(factory.isEnabled(StreamWriteFeature.AUTO_CLOSE_CONTENT))

        var generator = createGenerator(factory, mode)
        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.close()
        assertEquals("[\"0\"]", generator.streamWriteOutputTarget!!.toString())

        generator = createGenerator(factory, mode)
        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.close()
        assertEquals("{\"__cirJsonId__\":\"0\"}", generator.streamWriteOutputTarget!!.toString())
    }

    @Test
    fun testNoAutoCloseArraysAndObjects() {
        for (mode in ALL_GENERATOR_MODES) {
            noAutoCloseArraysAndObjects(mode)
        }
    }

    private fun noAutoCloseArraysAndObjects(mode: Int) {
        val factory = CirJsonFactory.builder().disable(StreamWriteFeature.AUTO_CLOSE_CONTENT).build()
        assertFalse(factory.isEnabled(StreamWriteFeature.AUTO_CLOSE_CONTENT))

        var generator = createGenerator(factory, mode)
        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.close()
        assertEquals("[\"0\"", generator.streamWriteOutputTarget!!.toString())

        generator = createGenerator(factory, mode)
        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.close()
        assertEquals("{\"__cirJsonId__\":\"0\"", generator.streamWriteOutputTarget!!.toString())
    }

    @Test
    fun testAutoFlushOrNot() {
        var factory = CirJsonFactory()
        assertTrue(factory.isEnabled(StreamWriteFeature.FLUSH_PASSED_TO_STREAM))
        var writer = TestingStringWriter()
        var generator = factory.createGenerator(ObjectWriteContext.empty(), writer)
        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.writeEndArray()
        assertEquals(0, writer.flushCount)
        generator.flush()
        assertEquals(1, writer.flushCount)
        generator.close()

        var output = TestingByteArrayOutputStream()
        generator = factory.createGenerator(ObjectWriteContext.empty(), output)
        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.writeEndArray()
        assertEquals(0, output.flushCount)
        generator.flush()
        assertEquals(1, output.flushCount)
        generator.close()

        factory = factory.rebuild().disable(StreamWriteFeature.FLUSH_PASSED_TO_STREAM).build()
        writer = TestingStringWriter()
        generator = factory.createGenerator(ObjectWriteContext.empty(), writer)
        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.writeEndArray()
        assertEquals(0, writer.flushCount)
        generator.flush()
        assertEquals(0, writer.flushCount)
        generator.close()
        assertEquals("[\"0\"]", writer.toString())

        output = TestingByteArrayOutputStream()
        generator = factory.createGenerator(ObjectWriteContext.empty(), output)
        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.writeEndArray()
        assertEquals(0, output.flushCount)
        generator.flush()
        assertEquals(0, output.flushCount)
        generator.close()
        assertEquals("[\"0\"]", output.toString())
    }

}