package org.cirjson.cirjackson.core.write

import org.cirjson.cirjackson.core.ObjectWriteContext
import org.cirjson.cirjackson.core.StreamWriteConstraints
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.exception.StreamConstraintsException
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.fail

class WriterBasedCirJsonGeneratorTest : TestBase() {

    private val factory =
            CirJsonFactory.builder().streamWriteConstraints(StreamWriteConstraints.builder().maxNestingDepth(1).build())
                    .build()

    @Test
    fun testNestingDepthWithSmallLimitNestedArray() {
        val writer = StringWriter()
        val generator = factory.createGenerator(ObjectWriteContext.empty(), writer)

        try {
            generator.writeStartObject()
            generator.writeObjectId(Any())
            generator.writeName("array")
            generator.writeStartArray()
            fail("Should have thrown an exception")
        } catch (e: StreamConstraintsException) {
            verifyException(e,
                    "Document nesting depth (2) exceeds the maximum allowed (1, from `StreamWriteConstraints.maxNestingDepth`)")
        }

        generator.close()
    }

    @Test
    fun testNestingDepthWithSmallLimitNestedObject() {
        val writer = StringWriter()
        val generator = factory.createGenerator(ObjectWriteContext.empty(), writer)

        try {
            generator.writeStartObject()
            generator.writeObjectId(Any())
            generator.writeName("object")
            generator.writeStartObject()
            fail("Should have thrown an exception")
        } catch (e: StreamConstraintsException) {
            verifyException(e,
                    "Document nesting depth (2) exceeds the maximum allowed (1, from `StreamWriteConstraints.maxNestingDepth`)")
        }

        generator.close()
    }

}