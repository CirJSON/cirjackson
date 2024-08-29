package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.exception.CirJacksonIOException
import org.cirjson.cirjackson.core.io.IOContext
import org.cirjson.cirjackson.core.io.InputDecorator
import org.cirjson.cirjackson.core.io.OutputDecorator
import org.cirjson.cirjackson.core.support.MockDataInput
import org.cirjson.cirjackson.core.util.CirJsonGeneratorDecorator
import org.cirjson.cirjackson.core.util.CirJsonGeneratorDelegate
import java.io.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TestDecorators : TestBase() {

    @Test
    fun testInputDecoration() {
        val factory = CirJsonFactory.builder().inputDecorator(SimpleInputDecorator()).build()
        var parser = factory.createParser(ObjectReadContext.empty(), StringReader("{ }"))
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(789, parser.intValue)
        parser.close()

        parser = factory.createParser(ObjectReadContext.empty(), ByteArrayInputStream("[ ]".toByteArray()))
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(123, parser.intValue)
        parser.close()

        val bytes = "[ ]".toByteArray()

        parser = factory.createParser(ObjectReadContext.empty(), bytes)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(456, parser.intValue)
        parser.close()

        parser = factory.createParser(ObjectReadContext.empty(), bytes, 0, bytes.size)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(456, parser.intValue)
        parser.close()

        val chars = "  [ ]".toCharArray()
        parser = factory.createParser(ObjectReadContext.empty(), chars, 0, chars.size)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(789, parser.intValue)
        parser.close()

        parser = factory.createParser(ObjectReadContext.empty(), MockDataInput("  { }"))
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(987, parser.intValue)
        parser.close()
    }

    @Test
    fun testOutputDecoration() {
        val factory = CirJsonFactory.builder().outputDecorator(SimpleOutputDecorator()).build()
        val writer = StringWriter()
        var generator = factory.createGenerator(ObjectWriteContext.empty(), writer)
        generator.close()
        assertEquals("456", writer.toString())

        val output = ByteArrayOutputStream()
        generator = factory.createGenerator(ObjectWriteContext.empty(), output, CirJsonEncoding.UTF8)
        generator.close()
        assertEquals("123", output.toString())

        output.reset()
        generator = factory.createGenerator(ObjectWriteContext.empty(), output)
        generator.close()
        assertEquals("123", output.toString())
    }

    @Test
    fun testGeneratorDecorator() {
        var factory = CirJsonFactory.builder().generatorDecorator(SimpleGeneratorDecorator()).build()

        var writer = StringWriter()

        factory.createGenerator(ObjectWriteContext.empty(), writer).use { generator ->
            generateForDecorator(generator)
        }

        assertEquals(EXPECTED, writer.toString())

        val output = ByteArrayOutputStream()
        factory.createGenerator(ObjectWriteContext.empty(), output).use { generator ->
            generateForDecorator(generator)
        }

        assertEquals(EXPECTED, output.toString())

        factory = factory.copy()
        writer = StringWriter()

        factory.createGenerator(ObjectWriteContext.empty(), writer).use { generator ->
            generateForDecorator(generator)
        }

        assertEquals(EXPECTED, output.toString())

        factory = factory.rebuild().build()
        writer = StringWriter()

        factory.createGenerator(ObjectWriteContext.empty(), writer).use { generator ->
            generateForDecorator(generator)
        }

        assertEquals(EXPECTED, output.toString())
    }

    private fun generateForDecorator(generator: CirJsonGenerator) {
        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeStringProperty("password", "s3cr37x!!")
    }

    private class SimpleInputDecorator : InputDecorator() {

        @Throws(CirJacksonException::class)
        override fun decorate(context: IOContext, input: InputStream): InputStream {
            return ByteArrayInputStream("123".toByteArray())
        }

        @Throws(CirJacksonException::class)
        override fun decorate(context: IOContext, data: ByteArray?, offset: Int, length: Int): InputStream {
            return ByteArrayInputStream("456".toByteArray())
        }

        override fun decorate(context: IOContext, reader: Reader): Reader {
            return StringReader("789")
        }

        override fun decorate(context: IOContext, input: DataInput): DataInput {
            return MockDataInput("987 ")
        }

    }

    private class SimpleOutputDecorator : OutputDecorator() {

        override fun decorate(context: IOContext, output: OutputStream): OutputStream {
            return try {
                output.write("123".toByteArray())
                output.flush()
                ByteArrayOutputStream()
            } catch (e: IOException) {
                throw CirJacksonIOException.construct(e, null)
            }
        }

        override fun decorate(context: IOContext, writer: Writer): Writer {
            return try {
                writer.write("456")
                writer.flush()
                StringWriter()
            } catch (e: IOException) {
                throw CirJacksonIOException.construct(e, null)
            }
        }

    }

    private class SimpleGeneratorDecorator : CirJsonGeneratorDecorator {

        override fun generate(factory: TokenStreamFactory, generator: CirJsonGenerator): CirJsonGenerator {
            return TextHider(generator)
        }

        private class TextHider(generator: CirJsonGenerator) : CirJsonGeneratorDelegate(generator) {

            override fun writeString(value: String?): CirJsonGenerator {
                delegate.writeString("***")
                return this
            }

        }

    }

    companion object {

        private const val EXPECTED = "{\"__cirJsonId__\":\"0\",\"password\":\"***\"}"

    }

}