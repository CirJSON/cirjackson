package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.ObjectReadContext
import org.cirjson.cirjackson.core.StreamReadFeature
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.*

class ParserClosingTest : TestBase() {

    @Test
    fun testNoAutoCloseReader() {
        assertTrue(sharedStreamFactory().isEnabled(StreamReadFeature.AUTO_CLOSE_SOURCE))

        val factory = CirJsonFactory.builder().disable(StreamReadFeature.AUTO_CLOSE_SOURCE).build()
        assertFalse(factory.isEnabled(StreamReadFeature.AUTO_CLOSE_SOURCE))

        val doc = "[ \"root\", 1 ]"
        val reader = MyReader(doc)
        val parser = factory.createParser(ObjectReadContext.empty(), reader)

        assertFalse(reader.isClosed)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        assertFalse(reader.isClosed)
        parser.close()
        assertFalse(reader.isClosed)
    }

    @Test
    fun testAutoCloseReader() {
        val factory = CirJsonFactory.builder().enable(StreamReadFeature.AUTO_CLOSE_SOURCE).build()
        assertTrue(factory.isEnabled(StreamReadFeature.AUTO_CLOSE_SOURCE))

        val doc = "[ \"root\", 1 ]"
        var reader = MyReader(doc)
        var parser = factory.createParser(ObjectReadContext.empty(), reader)
        assertFalse(reader.isClosed)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        parser.close()
        assertTrue(reader.isClosed)

        reader = MyReader(doc)
        parser = factory.createParser(ObjectReadContext.empty(), reader)
        assertFalse(reader.isClosed)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        assertTrue(reader.isClosed)
        parser.close()
        assertTrue(reader.isClosed)
    }

    @Test
    fun testReleaseContentChars() {
        val doc = "[\"root\",true]xyz"
        val parser = sharedStreamFactory().createParser(ObjectReadContext.empty(), doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())

        val writer = StringWriter()
        assertEquals(3, parser.releaseBuffered(writer))
        assertEquals("xyz", writer.toString())
        assertEquals(0, parser.releaseBuffered(writer))
        parser.close()
    }

    @Test
    fun testNoAutoCloseInputStream() {
        val factory = CirJsonFactory.builder().disable(StreamReadFeature.AUTO_CLOSE_SOURCE).build()
        assertFalse(factory.isEnabled(StreamReadFeature.AUTO_CLOSE_SOURCE))

        val doc = "[ \"root\", 1 ]"
        val inputStream = MyInputStream(doc.toByteArray())
        val parser = factory.createParser(ObjectReadContext.empty(), inputStream)

        assertFalse(inputStream.isClosed)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        assertFalse(inputStream.isClosed)
        parser.close()
        assertFalse(inputStream.isClosed)
    }

    @Test
    fun testAutoCloseInputStream() {
        val factory = CirJsonFactory.builder().enable(StreamReadFeature.AUTO_CLOSE_SOURCE).build()
        assertTrue(factory.isEnabled(StreamReadFeature.AUTO_CLOSE_SOURCE))

        val doc = "[ \"root\", 1 ]"
        var inputStream = MyInputStream(doc.toByteArray())
        var parser = factory.createParser(ObjectReadContext.empty(), inputStream)
        assertFalse(inputStream.isClosed)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        parser.close()
        assertTrue(inputStream.isClosed)

        inputStream = MyInputStream(doc.toByteArray())
        parser = factory.createParser(ObjectReadContext.empty(), inputStream)
        assertFalse(inputStream.isClosed)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        assertTrue(inputStream.isClosed)
        parser.close()
        assertTrue(inputStream.isClosed)
    }

    @Test
    fun testReleaseContentBytes() {
        val doc = "[\"root\",1]foobar".toByteArray()
        val parser = sharedStreamFactory().createParser(ObjectReadContext.empty(), doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())

        val output = ByteArrayOutputStream()
        assertEquals(6, parser.releaseBuffered(output))
        assertContentEquals("foobar".toByteArray(), output.toByteArray())
        assertEquals(0, parser.releaseBuffered(output))
        parser.close()
    }

    private class MyReader(s: String) : StringReader(s) {

        var isClosed = false
            private set

        override fun close() {
            isClosed = true
            super.close()
        }

    }

    private class MyInputStream(buf: ByteArray) : ByteArrayInputStream(buf) {

        var isClosed = false
            private set

        override fun close() {
            isClosed = true
            super.close()
        }

    }

}