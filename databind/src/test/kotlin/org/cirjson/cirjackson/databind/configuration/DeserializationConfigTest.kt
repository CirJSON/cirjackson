package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.core.StreamReadFeature
import org.cirjson.cirjackson.core.cirjson.CirJsonReadFeature
import org.cirjson.cirjackson.databind.DeserializationFeature
import org.cirjson.cirjackson.databind.MapperFeature
import org.cirjson.cirjackson.databind.ObjectMapper
import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.testutil.DatabindTestUtil
import kotlin.test.*

class DeserializationConfigTest : DatabindTestUtil() {

    @Test
    fun testFeatureDefaults() {
        val mapper = ObjectMapper()
        val config = mapper.deserializationConfigAccess()

        assertTrue(config.isEnabled(MapperFeature.USE_ANNOTATIONS))
        assertFalse(config.isEnabled(MapperFeature.USE_GETTERS_AS_SETTERS))
        assertTrue(config.isEnabled(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS))

        assertFalse(config.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS))
        assertFalse(config.isEnabled(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS))
        assertTrue(config.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
    }

    @Test
    fun testBasicFeatures() {
        val config = MAPPER.deserializationConfigAccess()

        assertTrue(config.hasDeserializationFeatures(DeserializationFeature.EAGER_DESERIALIZER_FETCH.mask))
        assertFalse(config.hasDeserializationFeatures(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY.mask))
        assertTrue(config.hasSomeOfFeatures(
                DeserializationFeature.EAGER_DESERIALIZER_FETCH.mask + DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY.mask))
        assertFalse(config.hasSomeOfFeatures(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY.mask))

        assertNotSame(config, config.with(DeserializationFeature.EAGER_DESERIALIZER_FETCH,
                DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY))
    }

    @Test
    fun testStreamReadFeatures() {
        val config = MAPPER.deserializationConfigAccess()

        assertNotSame(config, config.with(StreamReadFeature.IGNORE_UNDEFINED))
        assertNotSame(config,
                config.withFeatures(StreamReadFeature.IGNORE_UNDEFINED, StreamReadFeature.STRICT_DUPLICATE_DETECTION))

        assertSame(config, config.without(StreamReadFeature.IGNORE_UNDEFINED))
        assertSame(config, config.withoutFeatures(StreamReadFeature.IGNORE_UNDEFINED,
                StreamReadFeature.STRICT_DUPLICATE_DETECTION))
    }

    @Test
    fun testCirJsonReadFeatures() {
        val config = MAPPER.deserializationConfigAccess()
        val disabledByDefault1 = CirJsonReadFeature.ALLOW_JAVA_COMMENTS
        val disabledByDefault2 = CirJsonReadFeature.ALLOW_MISSING_VALUES
        val config2 = config.with(disabledByDefault1)
        assertNotSame(config, config2)
        val config3 = config.withFeatures(disabledByDefault2, disabledByDefault1)
        assertNotSame(config, config3)

        assertNotSame(config3, config3.without(disabledByDefault1))
        assertNotSame(config3, config3.withoutFeatures(disabledByDefault2, disabledByDefault1))
    }

    @Test
    fun testEnumIndexes() {
        var max = 0

        for (feature in DeserializationFeature.entries) {
            max = maxOf(max, feature.ordinal)
        }

        assertFalse(max >= 31, "Max number of DeserializationFeature enums reached: $max")
    }

    @Test
    fun testMiscellaneous() {
        var config = MAPPER.deserializationConfigAccess()
        assertEquals(ConfigOverrides.INCLUDE_DEFAULT, config.defaultPropertyInclusion)
        assertEquals(ConfigOverrides.INCLUDE_DEFAULT, config.getDefaultPropertyInclusion(String::class))

        assertSame(config, config.withRootName(null as PropertyName?))

        val newConfig = config.withRootName(PropertyName.construct("foobar"))
        assertNotSame(config, newConfig)
        config = newConfig
        assertSame(config, config.withRootName(PropertyName.construct("foobar")))

        assertSame(config, config.with(config.attributes))
        assertNotSame(config, config.with(ContextAttributes.Implementation.construct(mapOf("a" to "b"))))
    }

    companion object {

        val MAPPER = newCirJsonMapper()

    }

}