package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.exception.StreamWriteException
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.fail

class GeneratorFailFromReaderTest : TestBase() {

    private val factory = newStreamFactory()

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
            val reader = StringReader("a")
            generator.writeString(reader, -1)
            generator.flush()
            val res = generator.streamWriteOutputTarget!!.toString()
            fail("Should not have let ${generator::class.java.name}.writeString() be used in place of 'writeName()': output = $res")
        } catch (e: StreamWriteException) {
            verifyException(e, "cannot write a String")
        }
    }

    @Test
    fun testFailOnWritingStringFromReaderWithTooFewCharacters() {
        for (mode in ALL_GENERATOR_MODES) {
            failOnWritingStringFromReaderWithTooFewCharacters(mode)
        }
    }

    private fun failOnWritingStringFromReaderWithTooFewCharacters(mode: Int) {
        val generator = createGenerator(factory, mode)
        generator.writeStartObject()
        generator.writeObjectId(Any())

        try {
            val string = "aaaaaaaaa"
            val reader = StringReader(string)
            generator.writeName("a")
            generator.writeString(reader, string.length + 1)
            generator.flush()
            val res = generator.streamWriteOutputTarget!!.toString()
            fail("Should not have let ${generator::class.java.name}.writeString(): output = $res")
        } catch (e: StreamWriteException) {
            verifyException(e, "Didn't read enough from reader")
        }
    }

    @Test
    fun testFailOnWritingStringFromNullReader() {
        for (mode in ALL_GENERATOR_MODES) {
            failOnWritingStringFromNullReader(mode)
        }
    }

    private fun failOnWritingStringFromNullReader(mode: Int) {
        val generator = createGenerator(factory, mode)
        generator.writeStartObject()
        generator.writeObjectId(Any())

        try {
            generator.writeName("a")
            generator.writeString(null, -1)
            generator.flush()
            val res = generator.streamWriteOutputTarget!!.toString()
            fail("Should not have let ${generator::class.java.name}.writeString(): output = $res")
        } catch (e: StreamWriteException) {
            verifyException(e, "null reader")
        }
    }

}