package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.cirjson.async.NonBlockingByteArrayCirJsonParser
import org.cirjson.cirjackson.core.cirjson.async.NonBlockingByteBufferCirJsonParser
import org.cirjson.cirjackson.core.io.SerializedString
import java.io.*
import java.nio.ByteBuffer
import java.nio.file.Files
import kotlin.test.*

class CirJsonFactoryTest : TestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testStreamWriteFeatures() {
        var factory = CirJsonFactory.builder().enable(StreamWriteFeature.IGNORE_UNKNOWN).build()
        assertTrue(factory.isEnabled(StreamWriteFeature.IGNORE_UNKNOWN))
        factory = factory.rebuild().disable(StreamWriteFeature.IGNORE_UNKNOWN).build()
        assertFalse(factory.isEnabled(StreamWriteFeature.IGNORE_UNKNOWN))
    }

    @Test
    fun testStreamReadFeatures() {
        var factory = CirJsonFactory.builder().enable(StreamReadFeature.IGNORE_UNDEFINED).build()
        assertTrue(factory.isEnabled(StreamReadFeature.IGNORE_UNDEFINED))
        factory = factory.rebuild().disable(StreamReadFeature.IGNORE_UNDEFINED).build()
        assertFalse(factory.isEnabled(StreamReadFeature.IGNORE_UNDEFINED))
    }

    @Test
    fun testCirJsonWriteFeatures() {
        var factory = CirJsonFactory.builder().enable(CirJsonWriteFeature.ESCAPE_NON_ASCII).build()
        assertTrue(factory.isEnabled(CirJsonWriteFeature.ESCAPE_NON_ASCII))
        factory = factory.rebuild().disable(CirJsonWriteFeature.ESCAPE_NON_ASCII).build()
        assertFalse(factory.isEnabled(CirJsonWriteFeature.ESCAPE_NON_ASCII))
    }

    @Test
    fun testCirJsonReadFeatures() {
        var factory = CirJsonFactory.builder().enable(CirJsonReadFeature.ALLOW_TRAILING_COMMA).build()
        assertTrue(factory.isEnabled(CirJsonReadFeature.ALLOW_TRAILING_COMMA))
        factory = factory.rebuild().disable(CirJsonReadFeature.ALLOW_TRAILING_COMMA).build()
        assertFalse(factory.isEnabled(CirJsonReadFeature.ALLOW_TRAILING_COMMA))
    }

    @Test
    fun testFactoryFeatures() {
        var factory = CirJsonFactory.builder().enable(TokenStreamFactory.Feature.CANONICALIZE_PROPERTY_NAMES).build()
        assertTrue(factory.isEnabled(TokenStreamFactory.Feature.CANONICALIZE_PROPERTY_NAMES))
        factory = factory.rebuild().disable(TokenStreamFactory.Feature.CANONICALIZE_PROPERTY_NAMES).build()
        assertFalse(factory.isEnabled(TokenStreamFactory.Feature.CANONICALIZE_PROPERTY_NAMES))
    }

    @Test
    fun testFactoryMiscellaneous() {
        assertNull(factory.inputDecorator)
        assertNull(factory.outputDecorator)

        assertFalse(factory.canUseSchema(BogusSchema()))

        assertEquals(CirJsonReadFeature::class.java, factory.formatReadFeatureType)
        assertEquals(CirJsonWriteFeature::class.java, factory.formatWriteFeatureType)
    }

    @Test
    fun testCirJsonWithFiles() {
        val file = File.createTempFile("cirjackson-test", null)
        file.deleteOnExit()

        val factory = CirJsonFactory()

        val generator = factory.createGenerator(ObjectWriteContext.empty(), file, CirJsonEncoding.UTF16_LE)
        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.writeRaw("   ")
        generator.writeEndArray()
        generator.close()

        var parser = factory.createParser(ObjectReadContext.empty(), file)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        parser.close()

        parser = factory.createParser(ObjectReadContext.empty(), file.toURI().toURL())
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        parser.close()

        file.delete()
    }

    @Test
    fun testCopy() {
        var factory = CirJsonFactory()

        assertFalse(factory.isEnabled(TokenStreamFactory.Feature.INTERN_PROPERTY_NAMES))
        assertFalse(factory.isEnabled(CirJsonReadFeature.ALLOW_JAVA_COMMENTS))
        assertFalse(factory.isEnabled(CirJsonWriteFeature.ESCAPE_NON_ASCII))

        factory = factory.rebuild().enable(TokenStreamFactory.Feature.INTERN_PROPERTY_NAMES)
                .enable(CirJsonReadFeature.ALLOW_JAVA_COMMENTS).enable(CirJsonWriteFeature.ESCAPE_NON_ASCII).build()

        assertTrue(factory.isEnabled(TokenStreamFactory.Feature.INTERN_PROPERTY_NAMES))
        assertTrue(factory.isEnabled(CirJsonReadFeature.ALLOW_JAVA_COMMENTS))
        assertTrue(factory.isEnabled(CirJsonWriteFeature.ESCAPE_NON_ASCII))

        val factory2 = factory.copy()
        assertTrue(factory2.isEnabled(TokenStreamFactory.Feature.INTERN_PROPERTY_NAMES))
        assertTrue(factory2.isEnabled(CirJsonReadFeature.ALLOW_JAVA_COMMENTS))
        assertTrue(factory2.isEnabled(CirJsonWriteFeature.ESCAPE_NON_ASCII))
    }

    @Test
    fun testRootValues() {
        assertEquals(" ", factory.rootValueSeparator)
        val builder = CirJsonFactory.builder().rootValueSeparator("/")
        assertEquals(SerializedString("/"), builder.rootValueSeparator)
        val factory = builder.build()
        assertEquals("/", factory.rootValueSeparator)

        val writer = StringWriter()
        val generator = factory.createGenerator(ObjectWriteContext.empty(), writer)
        generator.writeNumber(1)
        generator.writeNumber(2)
        generator.writeNumber(3)
        generator.close()
        assertEquals("1/2/3", writer.toString())
    }

    @Test
    fun testCreateGeneratorOutputStream() {
        val outputStream = ByteArrayOutputStream()
        var generator = CirJsonFactory().createGenerator(ObjectWriteContext.empty(), outputStream)

        generator.writeString("value")
        generator.close()

        assertEquals("\"value\"", String(outputStream.toByteArray(), Charsets.UTF_8))

        outputStream.write(1)

        outputStream.reset()
        generator = CirJsonFactory().createGenerator(ObjectWriteContext.empty(), outputStream)

        generator.writeString("value")
        generator.close()

        assertEquals("\"value\"", String(outputStream.toByteArray(), Charsets.UTF_8))

        outputStream.write(1)
    }

    @Test
    fun testCreateGeneratorFile() {
        val path = Files.createTempFile("", "")
        val generator =
                CirJsonFactory().createGenerator(ObjectWriteContext.empty(), path.toFile(), CirJsonEncoding.UTF8)

        generator.writeString("value")
        generator.close()

        assertEquals("\"value\"", String(Files.readAllBytes(path), Charsets.UTF_8))
    }

    @Test
    fun testCreateGeneratorPath() {
        val path = Files.createTempFile("", "")
        val generator = CirJsonFactory().createGenerator(ObjectWriteContext.empty(), path, CirJsonEncoding.UTF8)

        generator.writeString("value")
        generator.close()

        assertEquals("\"value\"", String(Files.readAllBytes(path), Charsets.UTF_8))
    }

    @Test
    fun testCreateGeneratorWriter() {
        val writer = StringWriter()
        val generator = CirJsonFactory().createGenerator(ObjectWriteContext.empty(), writer)

        generator.writeString("value")
        generator.close()

        assertEquals("\"value\"", writer.toString())

        writer.append('1')
    }

    @Test
    fun testCreateGeneratorDataOutput() {
        val outputStream = ByteArrayOutputStream()
        val dataOutput = DataOutputStream(outputStream) as DataOutput
        val generator = CirJsonFactory().createGenerator(ObjectWriteContext.empty(), dataOutput)

        generator.writeString("value")
        generator.close()

        assertEquals("\"value\"", String(outputStream.toByteArray(), Charsets.UTF_8))

        outputStream.write(1)
    }

    @Test
    fun testCreateParserInputStream() {
        val inputStream = ByteArrayInputStream("\"value\"".toByteArray(Charsets.UTF_8))
        val parser = CirJsonFactory().createParser(ObjectReadContext.empty(), inputStream)

        assertEquals("value", parser.nextTextValue())
        parser.close()
    }

    @Test
    fun testCreateParserFile() {
        val path = Files.createTempFile("", "")
        Files.write(path, "\"value\"".toByteArray(Charsets.UTF_8))
        val parser = CirJsonFactory().createParser(ObjectReadContext.empty(), path.toFile())

        assertEquals("value", parser.nextTextValue())
        parser.close()
    }

    @Test
    fun testCreateParserPath() {
        val path = Files.createTempFile("", "")
        Files.write(path, "\"value\"".toByteArray(Charsets.UTF_8))
        val parser = CirJsonFactory().createParser(ObjectReadContext.empty(), path)

        assertEquals("value", parser.nextTextValue())
        parser.close()
    }

    @Test
    fun testCreateParserUrl() {
        val path = Files.createTempFile("", "")
        Files.write(path, "\"value\"".toByteArray(Charsets.UTF_8))
        val parser = CirJsonFactory().createParser(ObjectReadContext.empty(), path.toUri().toURL())

        assertEquals("value", parser.nextTextValue())
        parser.close()
    }

    @Test
    fun testCreateParserReader() {
        val reader = StringReader("\"value\"")
        val parser = CirJsonFactory().createParser(ObjectReadContext.empty(), reader)

        assertEquals("value", parser.nextTextValue())
        parser.close()
    }

    @Test
    fun testCreateParserByteArray() {
        val bytes = "\"value\"".toByteArray(Charsets.UTF_8)
        var parser = CirJsonFactory().createParser(ObjectReadContext.empty(), bytes)

        assertEquals("value", parser.nextTextValue())
        parser.close()

        parser = CirJsonFactory().createParser(ObjectReadContext.empty(), "\"\"value\"\"".toByteArray(Charsets.UTF_8),
                1, bytes.size)

        assertEquals("value", parser.nextTextValue())
        parser.close()
    }

    @Test
    fun testCreateParserString() {
        val string = "\"value\""
        val parser = CirJsonFactory().createParser(ObjectReadContext.empty(), string)

        assertEquals("value", parser.nextTextValue())
        parser.close()
    }

    @Test
    fun testCreateParserCharArray() {
        val chars = "\"value\"".toCharArray()
        var parser = CirJsonFactory().createParser(ObjectReadContext.empty(), chars)

        assertEquals("value", parser.nextTextValue())
        parser.close()

        parser = CirJsonFactory().createParser(ObjectReadContext.empty(), "\"\"value\"\"".toCharArray(), 1, chars.size)

        assertEquals("value", parser.nextTextValue())
        parser.close()
    }

    @Test
    fun testCreateParserDataInput() {
        val inputStream = ByteArrayInputStream("\"value\"".toByteArray(Charsets.UTF_8))
        val dataInput = DataInputStream(inputStream) as DataInput
        val parser = CirJsonFactory().createParser(ObjectReadContext.empty(), dataInput)

        assertEquals("value", parser.nextTextValue())
        parser.close()
    }

    @Test
    fun testCanonicalization() {
        canonicalization(true)
        canonicalization(false)
    }

    private fun canonicalization(canonicalize: Boolean) {
        val content = "{\"__cirJsonId__\":\"root\",\"a\":true,\"a\":false}"
        val factory =
                CirJsonFactory.builder().configure(TokenStreamFactory.Feature.CANONICALIZE_PROPERTY_NAMES, canonicalize)
                        .build()

        factory.createParser(ObjectReadContext.empty(), content).use { parser ->
            verifyCanonicalizationResult(parser, canonicalize)
        }

        factory.createParser(ObjectReadContext.empty(), content.toByteArray()).use { parser ->
            verifyCanonicalizationResult(parser, canonicalize)
        }

        factory.createParser(ObjectReadContext.empty(), content.toCharArray()).use { parser ->
            verifyCanonicalizationResult(parser, canonicalize)
        }

        factory.createParser(ObjectReadContext.empty(), ByteArrayInputStream(content.toByteArray())).use { parser ->
            verifyCanonicalizationResult(parser, canonicalize)
        }

        factory.createParser(ObjectReadContext.empty(), StringReader(content)).use { parser ->
            verifyCanonicalizationResult(parser, canonicalize)
        }

        factory.createParser(ObjectReadContext.empty(),
                DataInputStream(ByteArrayInputStream(content.toByteArray())) as DataInput).use { parser ->
            verifyCanonicalizationResult(parser, canonicalize)
        }

        factory.createNonBlockingByteArrayParser<NonBlockingByteArrayCirJsonParser>(ObjectReadContext.empty())
                .use { parser ->
                    val data = content.toByteArray()
                    parser.feedInput(data, 0, data.size)
                    verifyCanonicalizationResult(parser, canonicalize)
                }

        factory.createNonBlockingByteBufferParser<NonBlockingByteBufferCirJsonParser>(ObjectReadContext.empty())
                .use { parser ->
                    val data = content.toByteArray()
                    val buffer = ByteBuffer.allocate(data.size)
                    buffer.put(data)
                    buffer.rewind()
                    parser.feedInput(buffer)
                    verifyCanonicalizationResult(parser, canonicalize)
                }
    }

    private fun verifyCanonicalizationResult(parser: CirJsonParser, canonicalize: Boolean) {
        System.gc()
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        val name1 = parser.nextName()
        assertEquals("a", name1)
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        val name2 = parser.nextName()
        assertEquals("a", name2)

        if (canonicalize) {
            assertSame(name1, name2)
        } else {
            assertNotSame(name1, name2)
        }

        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
    }

    private class BogusSchema : FormatSchema {

        override val schemaType: String = "test"

    }

}