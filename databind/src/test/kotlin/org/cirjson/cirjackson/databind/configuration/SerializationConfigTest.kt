package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.core.StreamWriteFeature
import org.cirjson.cirjackson.core.cirjson.CirJsonWriteFeature
import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.SerializationFeature
import org.cirjson.cirjackson.databind.testutil.DatabindTestUtil
import kotlin.test.*

class SerializationConfigTest : DatabindTestUtil() {

    @Test
    fun testSerializationFeature() {
        val config = MAPPER.serializationConfigAccess()
        assertTrue(config.hasSerializationFeatures(SerializationFeature.FAIL_ON_EMPTY_BEANS.mask))
        assertFalse(config.hasSerializationFeatures(SerializationFeature.CLOSE_CLOSEABLE.mask))
        assertEquals(ConfigOverrides.INCLUDE_DEFAULT, config.defaultPropertyInclusion)
        assertEquals(ConfigOverrides.INCLUDE_DEFAULT, config.getDefaultPropertyInclusion(String::class))
        assertFalse(config.useRootWrapping())

        assertNotSame(config,
                config.with(SerializationFeature.INDENT_OUTPUT, SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS))

        assertSame(config, config.withRootName(null as PropertyName?))

        val newConfig = config.withRootName(PropertyName.construct("foobar"))
        assertNotSame(config, newConfig)
        assertTrue(newConfig.useRootWrapping())

        assertSame(config, config.with(config.attributes))
        assertNotSame(config, config.with(ContextAttributes.Implementation.construct(mapOf("a" to "b"))))
    }

    @Test
    fun testStreamWriteFeatures() {
        val config = MAPPER.serializationConfigAccess()
        assertFalse(config.hasFormatFeature(CirJsonWriteFeature.ESCAPE_NON_ASCII))
        assertNotSame(config, config.with(CirJsonWriteFeature.ESCAPE_NON_ASCII))
        val newConfig = config.withFeatures(StreamWriteFeature.IGNORE_UNKNOWN)
        assertNotSame(config, newConfig)
        assertTrue(newConfig.isEnabled(StreamWriteFeature.IGNORE_UNKNOWN))

        assertSame(config, config.without(CirJsonWriteFeature.ESCAPE_NON_ASCII))
        assertSame(config, config.without(StreamWriteFeature.IGNORE_UNKNOWN))
    }

    @Test
    fun testFormatFeature() {
        val disabledByDefault = CirJsonWriteFeature.ESCAPE_NON_ASCII
        val enabledByDefault = CirJsonWriteFeature.QUOTE_PROPERTY_NAMES

        val config1 = MAPPER.serializationConfigAccess()
        val config2 = config1.with(disabledByDefault)
        assertNotSame(config1, config2)
        val config3 = config1.withFeatures(disabledByDefault, enabledByDefault)
        assertNotSame(config1, config3)

        assertNotSame(config3, config3.without(enabledByDefault))
        assertNotSame(config3, config3.withoutFeatures(disabledByDefault, enabledByDefault))
    }

    companion object {

        val MAPPER = newCirJsonMapper()

    }

}