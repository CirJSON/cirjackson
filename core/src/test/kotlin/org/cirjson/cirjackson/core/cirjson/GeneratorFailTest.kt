package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.exception.StreamWriteException
import kotlin.test.Test
import kotlin.test.fail

class GeneratorFailTest : TestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testDuplicateFieldNameWrites() {
        for (mode in ALL_GENERATOR_MODES) {
            duplicateFieldNameWrites(mode)
        }
    }

    private fun duplicateFieldNameWrites(mode: Int) {
        val generator = createGenerator(factory, mode)
        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeName("a")

        try {
            generator.writeName("b")
            generator.flush()
            val res = generator.streamWriteOutputTarget!!.toString()
            fail("Should not have let two consecutive ${generator::class.java.name}.writeName() succeed: output = $res")
        } catch (e: StreamWriteException) {
            verifyException(e, "Cannot write a property name, expecting a value")
        }
    }

    @Test
    fun testFailOnWritingStringNotFieldName() {
        for (mode in ALL_GENERATOR_MODES) {
            failOnWritingStringNotFieldName(mode)
        }
    }

    private fun failOnWritingStringNotFieldName(mode: Int) {
        val generator = createGenerator(factory, mode)
        generator.writeStartObject()
        generator.writeObjectId(Any())

        try {
            generator.writeString("a")
            generator.flush()
            val res = generator.streamWriteOutputTarget!!.toString()
            fail("Should not have let ${generator::class.java.name}.writeString() be used in place of 'writeName()': output = $res")
        } catch (e: StreamWriteException) {
            verifyException(e, "Cannot write a String")
        }
    }

    @Test
    fun testFailOnWritingFieldNameInRoot() {
        for (mode in ALL_GENERATOR_MODES) {
            failOnWritingFieldNameInRoot(mode)
        }
    }

    private fun failOnWritingFieldNameInRoot(mode: Int) {
        val generator = createGenerator(factory, mode)

        try {
            generator.writeName("a")
            generator.flush()
            val res = generator.streamWriteOutputTarget!!.toString()
            fail("Should not have let ${generator::class.java.name}.writeName() be used in root context: output = $res")
        } catch (e: StreamWriteException) {
            verifyException(e, "Cannot write a property name")
        }
    }

}