package org.cirjson.cirjackson.core.write

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.StreamWriteFeature
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.exception.StreamWriteException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class GeneratorDuplicateHandlingTest : TestBase() {

    @Test
    fun testSimpleDuplicates() {
        for (mode in ALL_GENERATOR_MODES) {
            simpleDuplicates(mode)
        }
    }

    private fun simpleDuplicates(mode: Int) {
        var factory = newStreamFactory()
        assertFalse(factory.isEnabled(StreamWriteFeature.STRICT_DUPLICATE_DETECTION))
        simpleDuplicates(createGenerator(factory, mode))
        simpleDuplicates(createGenerator(factory, mode), "b")

        factory = factory.rebuild().enable(StreamWriteFeature.STRICT_DUPLICATE_DETECTION).build()
        assertTrue(factory.isEnabled(StreamWriteFeature.STRICT_DUPLICATE_DETECTION))

        var generator = createGenerator(factory, mode)

        try {
            simpleDuplicates(generator)
            fail("Should have thrown an exception")
        } catch (e: StreamWriteException) {
            verifyException(e, "duplicate Object property \"a\"")
        }

        generator.close()
        generator = createGenerator(factory, mode)

        try {
            simpleDuplicates(generator, "x")
            fail("Should have thrown an exception")
        } catch (e: StreamWriteException) {
            verifyException(e, "duplicate Object property \"x\"")
        }

        generator.close()
    }

    private fun simpleDuplicates(generator: CirJsonGenerator) {
        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeNumberProperty("a", 1)
        generator.writeNumberProperty("a", 2)
        generator.writeEndObject()
        generator.close()
    }

    private fun simpleDuplicates(generator: CirJsonGenerator, name: String) {
        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.writeNumber(3)
        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeNumberProperty("foo", 1)
        generator.writeNumberProperty("bar", 2)
        generator.writeNumberProperty(name, 1)
        generator.writeNumberProperty("bar2", 1)
        generator.writeNumberProperty(name, 2)
        generator.writeEndObject()
        generator.close()
    }

}