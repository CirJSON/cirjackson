package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.StreamReadFeature
import org.cirjson.cirjackson.core.StreamWriteFeature
import org.cirjson.cirjackson.core.TokenStreamFactory
import org.cirjson.cirjackson.core.cirjson.CirJsonWriteFeature
import org.cirjson.cirjackson.core.util.MinimalPrettyPrinter
import org.cirjson.cirjackson.databind.cirjson.CirJsonMapper
import org.cirjson.cirjackson.databind.configuration.DeserializationContexts
import org.cirjson.cirjackson.databind.introspection.CirJacksonAnnotationIntrospector
import org.cirjson.cirjackson.databind.module.SimpleModule
import org.cirjson.cirjackson.databind.node.CirJsonNodeFactory
import org.cirjson.cirjackson.databind.testutil.DatabindTestUtil
import java.io.ByteArrayInputStream
import java.io.DataInput
import java.io.DataInputStream
import java.io.StringReader
import java.nio.file.Files
import java.util.*
import kotlin.test.*

// TODO: to complete
class ObjectMapperTest : DatabindTestUtil() {

    /*
     *******************************************************************************************************************
     * Test methods, config
     *******************************************************************************************************************
     */

    @Test
    fun testFeatureDefaults() {
        assertTrue(MAPPER.isEnabled(TokenStreamFactory.Feature.CANONICALIZE_PROPERTY_NAMES))
        assertTrue(MAPPER.isEnabled(CirJsonWriteFeature.QUOTE_PROPERTY_NAMES))
        assertTrue(MAPPER.isEnabled(StreamReadFeature.AUTO_CLOSE_SOURCE))
        assertTrue(MAPPER.isEnabled(StreamWriteFeature.AUTO_CLOSE_TARGET))
        assertFalse(MAPPER.isEnabled(CirJsonWriteFeature.ESCAPE_NON_ASCII))
        assertTrue(MAPPER.isEnabled(CirJsonWriteFeature.WRITE_NAN_AS_STRINGS))

        val mapper = CirJsonMapper.builder().disable(StreamWriteFeature.FLUSH_PASSED_TO_STREAM)
                .disable(CirJsonWriteFeature.WRITE_NAN_AS_STRINGS).build()
        assertFalse(mapper.isEnabled(StreamWriteFeature.FLUSH_PASSED_TO_STREAM))
        assertFalse(mapper.isEnabled(CirJsonWriteFeature.WRITE_NAN_AS_STRINGS))
    }

    /*
     *******************************************************************************************************************
     * Test methods, other
     *******************************************************************************************************************
     */

    @Test
    fun testProperties() {
        assertNotNull(MAPPER.nodeFactory)

        val nodeFactory = CirJsonNodeFactory()
        val mapper = CirJsonMapper.builder().nodeFactory(nodeFactory).build()

        assertNull(mapper.injectableValues)
        assertSame(nodeFactory, mapper.nodeFactory)
    }

    @Test
    fun testConfigForPropertySorting() {
        var mapper = MAPPER
        assertFalse(mapper.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY))
        assertTrue(mapper.isEnabled(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST))

        var serializationConfig = mapper.serializationConfigAccess()
        assertFalse(serializationConfig.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY))
        assertFalse(serializationConfig.shouldSortPropertiesAlphabetically())
        assertTrue(serializationConfig.isEnabled(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST))

        var deserializationConfig = mapper.deserializationConfigAccess()
        assertFalse(deserializationConfig.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY))
        assertFalse(deserializationConfig.shouldSortPropertiesAlphabetically())
        assertTrue(deserializationConfig.isEnabled(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST))

        mapper = cirJsonMapperBuilder().enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .disable(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST).build()
        assertTrue(mapper.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY))
        assertFalse(mapper.isEnabled(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST))

        serializationConfig = mapper.serializationConfigAccess()
        assertTrue(serializationConfig.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY))
        assertTrue(serializationConfig.shouldSortPropertiesAlphabetically())
        assertFalse(serializationConfig.isEnabled(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST))

        deserializationConfig = mapper.deserializationConfigAccess()
        assertTrue(deserializationConfig.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY))
        assertTrue(deserializationConfig.shouldSortPropertiesAlphabetically())
        assertFalse(deserializationConfig.isEnabled(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST))
    }

    @Test
    fun testDeserializationContextCache() {
        var mapper = newCirJsonMapper()
        val cirJson = "{ \"__cirJsonId__\": \"root\", \"x\" : 3 }"

        var deserializationContexts = mapper.deserializationContexts() as DeserializationContexts.DefaultImplementation
        var cache = deserializationContexts.cacheForTests()!!

        assertEquals(0, cache.cachedDeserializersCount())
//        val bean = mapper.readValue(cirJson, Bean::class)
//        assertNotNull(bean)
//        assertEquals(2, cache.cachedDeserializersCount())
//        cache.flushCachedDeserializers()
//        assertEquals(0, cache.cachedDeserializersCount())
    }

    @Test
    fun testRegisterDependentModules() {
        val secondModule = object : SimpleModule() {

            override val registrationId: Any
                get() = "dep1"

        }

        val thirdModule = object : SimpleModule() {

            override val registrationId: Any
                get() = "dep2"

        }

        val mainModule = object : SimpleModule() {

            override val dependencies: Iterable<CirJacksonModule>
                get() = listOf(secondModule, thirdModule)

            override val registrationId: Any
                get() = "main"

        }

        val mapper = cirJsonMapperBuilder().addModule(mainModule).build()

        val modules = mapper.registeredModules
        val ids = modules.map { it.registrationId }
        assertEquals(listOf("dep1", "dep2", "main"), ids)
    }

    @Test
    fun testHasExplicitTimeZone() {
        val defaultTimeZone = TimeZone.getTimeZone("UTC")

        assertFalse(MAPPER.serializationConfigAccess().hasExplicitTimeZone())
        assertFalse(MAPPER.deserializationConfigAccess().hasExplicitTimeZone())
        assertEquals(defaultTimeZone, MAPPER.serializationConfigAccess().timeZone)
        assertEquals(defaultTimeZone, MAPPER.deserializationConfigAccess().timeZone)
        assertFalse(MAPPER.reader().config.hasExplicitTimeZone())
        assertFalse(MAPPER.writer().config.hasExplicitTimeZone())

        val timeZone = TimeZone.getTimeZone("GMT+4")

        val mapper = cirJsonMapperBuilder().defaultTimeZone(timeZone).build()
        assertTrue(mapper.serializationConfigAccess().hasExplicitTimeZone())
        assertTrue(mapper.deserializationConfigAccess().hasExplicitTimeZone())
        assertSame(timeZone, mapper.serializationConfigAccess().timeZone)
        assertSame(timeZone, mapper.deserializationConfigAccess().timeZone)
        assertTrue(mapper.reader().config.hasExplicitTimeZone())
        assertTrue(mapper.writer().config.hasExplicitTimeZone())

        var reader = MAPPER.reader().with(timeZone)
        assertTrue(reader.config.hasExplicitTimeZone())
        assertSame(timeZone, reader.config.timeZone)
        var writer = MAPPER.writer().with(timeZone)
        assertTrue(writer.config.hasExplicitTimeZone())
        assertSame(timeZone, writer.config.timeZone)

        reader = reader.with(null as TimeZone?)
        assertFalse(reader.config.hasExplicitTimeZone())
        assertEquals(defaultTimeZone, reader.config.timeZone)
        writer = writer.with(null as TimeZone?)
        assertFalse(writer.config.hasExplicitTimeZone())
        assertEquals(defaultTimeZone, writer.config.timeZone)
    }

    @Test
    fun testCreateParser_InputStream() {
        val inputStream = ByteArrayInputStream("\"value\"".toByteArray())
        val parser = MAPPER.createParser(inputStream)

        assertEquals("value", parser.nextTextValue())
    }

    @Test
    fun testCreateParser_File() {
        val path = Files.createTempFile("", "")
        Files.writeString(path, "\"value\"")
        val parser = MAPPER.createParser(path.toFile())

        assertEquals("value", parser.nextTextValue())
    }

    @Test
    fun testCreateParser_Path() {
        val path = Files.createTempFile("", "")
        Files.writeString(path, "\"value\"")
        val parser = MAPPER.createParser(path)

        assertEquals("value", parser.nextTextValue())
    }

    @Test
    fun testCreateParser_Url() {
        val path = Files.createTempFile("", "")
        Files.writeString(path, "\"value\"")
        val parser = MAPPER.createParser(path.toUri().toURL())

        assertEquals("value", parser.nextTextValue())
    }

    @Test
    fun testCreateParser_Reader() {
        val reader = StringReader("\"value\"")
        val parser = MAPPER.createParser(reader)

        assertEquals("value", parser.nextTextValue())
    }

    @Test
    fun testCreateParser_ByteArray() {
        val bytes = "\"value\"".toByteArray()
        val parser = MAPPER.createParser(bytes)

        assertEquals("value", parser.nextTextValue())
    }

    @Test
    fun testCreateParser_String() {
        val string = "\"value\""
        val parser = MAPPER.createParser(string)

        assertEquals("value", parser.nextTextValue())
    }

    @Test
    fun testCreateParser_CharArray() {
        val chars = "\"value\"".toCharArray()
        val parser = MAPPER.createParser(chars)

        assertEquals("value", parser.nextTextValue())
    }

    @Test
    fun testCreateParser_DataInput() {
        val inputStream = ByteArrayInputStream("\"value\"".toByteArray())
        val dataInput = DataInputStream(inputStream)
        val parser = MAPPER.createParser(dataInput as DataInput)

        assertEquals("value", parser.nextTextValue())
    }

    open class Bean(var value: Int = 3)

    open class EmptyBean

    open class MyAnnotationIntrospector : CirJacksonAnnotationIntrospector()

    open class FooPrettyPrinter : MinimalPrettyPrinter(" /*foo*/ ") {

        override fun writeArrayValueSeparator(generator: CirJsonGenerator) {
            generator.writeRaw(" , ")
        }

    }

    companion object {

        private val MAPPER = newCirJsonMapper()

    }

}