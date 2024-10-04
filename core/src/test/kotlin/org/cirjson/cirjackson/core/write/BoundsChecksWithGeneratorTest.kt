package org.cirjson.cirjackson.core.write

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.ObjectWriteContext
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.exception.StreamWriteException
import org.cirjson.cirjackson.core.write.BoundsChecksWithGeneratorTest.*
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.fail

class BoundsChecksWithGeneratorTest : TestBase() {

    private val factory = newStreamFactory()

    private val byteGeneratorCreator =
            GeneratorCreator { factory.createGenerator(ObjectWriteContext.empty(), ByteArrayOutputStream()) }

    private val charGeneratorCreator =
            GeneratorCreator { factory.createGenerator(ObjectWriteContext.empty(), StringWriter()) }

    /*
     *******************************************************************************************************************
     * Test methods, ByteArray backed
     *******************************************************************************************************************
     */

    private val writeBinaryFromBytes =
            ByteBackedOperation { generator, data, offset, length -> generator.writeBinary(data, offset, length) }

    private val writeRawUTF8StringFromBytes = ByteBackedOperation { generator, data, offset, length ->
        generator.writeRawUTF8String(data, offset, length)
    }

    private val writeUTF8StringFromBytes =
            ByteBackedOperation { generator, data, offset, length -> generator.writeUTF8String(data, offset, length) }

    @Test
    fun testBoundsWithByteArrayInputFromBytes() {
        boundsWithByteArrayInput(byteGeneratorCreator, true)
    }

    @Test
    fun testBoundsWithByteArrayInputFromChars() {
        boundsWithByteArrayInput(charGeneratorCreator, false)
    }

    private fun boundsWithByteArrayInput(creator: GeneratorCreator, isByteBacked: Boolean) {
        boundsWithByteArrayInput(creator, writeBinaryFromBytes)

        if (isByteBacked) {
            boundsWithByteArrayInput(creator, writeRawUTF8StringFromBytes)
            boundsWithByteArrayInput(creator, writeUTF8StringFromBytes)
        }
    }

    private fun boundsWithByteArrayInput(creator: GeneratorCreator, operation: ByteBackedOperation) {
        val data = ByteArray(10)
        boundsWithByteArrayInput(creator, operation, data, -1, 4)
        boundsWithByteArrayInput(creator, operation, data, -6, 4)
        boundsWithByteArrayInput(creator, operation, data, 4, -1)
        boundsWithByteArrayInput(creator, operation, data, 4, -6)
        boundsWithByteArrayInput(creator, operation, data, 9, 5)
        boundsWithByteArrayInput(creator, operation, data, Int.MAX_VALUE, 4)
        boundsWithByteArrayInput(creator, operation, data, Int.MAX_VALUE, Int.MAX_VALUE)
    }

    private fun boundsWithByteArrayInput(creator: GeneratorCreator, operation: ByteBackedOperation, data: ByteArray,
            offset: Int, length: Int) {
        val generator = creator.create()

        try {
            operation.call(generator, data, offset, length)
            fail("Should have thrown an exception")
        } catch (e: StreamWriteException) {
            verifyException(e, "Invalid 'offset'")
            verifyException(e, "'length'")
            verifyException(e, "arguments for `ByteArray` of length ${data.size}")
        }
    }

    /*
     *******************************************************************************************************************
     * Test methods, CharArray backed
     *******************************************************************************************************************
     */

    private val writeNumberFromChars =
            CharBackedOperation { generator, data, offset, length -> generator.writeNumber(data, offset, length) }

    private val writeRawFromChars =
            CharBackedOperation { generator, data, offset, length -> generator.writeRaw(data, offset, length) }

    private val writeRawValueFromChars =
            CharBackedOperation { generator, data, offset, length -> generator.writeRawValue(data, offset, length) }

    @Test
    fun testBoundsWithCharArrayInputFromBytes() {
        boundsWithCharArrayInput(byteGeneratorCreator)
    }

    @Test
    fun testBoundsWithCharArrayInputFromChars() {
        boundsWithCharArrayInput(charGeneratorCreator)
    }

    private fun boundsWithCharArrayInput(creator: GeneratorCreator) {
        boundsWithCharArrayInput(creator, writeNumberFromChars)
        boundsWithCharArrayInput(creator, writeRawFromChars)
        boundsWithCharArrayInput(creator, writeRawValueFromChars)
    }

    private fun boundsWithCharArrayInput(creator: GeneratorCreator, operation: CharBackedOperation) {
        val data = CharArray(10)
        boundsWithCharArrayInput(creator, operation, data, -1, 4)
        boundsWithCharArrayInput(creator, operation, data, -6, 4)
        boundsWithCharArrayInput(creator, operation, data, 4, -1)
        boundsWithCharArrayInput(creator, operation, data, 4, -6)
        boundsWithCharArrayInput(creator, operation, data, 9, 5)
        boundsWithCharArrayInput(creator, operation, data, Int.MAX_VALUE, 4)
        boundsWithCharArrayInput(creator, operation, data, Int.MAX_VALUE, Int.MAX_VALUE)
    }

    private fun boundsWithCharArrayInput(creator: GeneratorCreator, operation: CharBackedOperation, data: CharArray,
            offset: Int, length: Int) {
        val generator = creator.create()

        try {
            operation.call(generator, data, offset, length)
            fail("Should have thrown an exception")
        } catch (e: StreamWriteException) {
            verifyException(e, "Invalid 'offset'")
            verifyException(e, "'length'")
            verifyException(e, "arguments for `CharArray` of length ${data.size}")
        }
    }

    /*
     *******************************************************************************************************************
     * Test methods, CharArray backed
     *******************************************************************************************************************
     */

    private val writeRawFromString =
            StringBackedOperation { generator, data, offset, length -> generator.writeRaw(data, offset, length) }

    private val writeRawValueFromString =
            StringBackedOperation { generator, data, offset, length -> generator.writeRawValue(data, offset, length) }

    @Test
    fun testBoundsWithStringInputFromBytes() {
        boundsWithStringInput(byteGeneratorCreator)
    }

    @Test
    fun testBoundsWithStringInputFromChars() {
        boundsWithStringInput(charGeneratorCreator)
    }

    private fun boundsWithStringInput(creator: GeneratorCreator) {
        boundsWithStringInput(creator, writeRawFromString)
        boundsWithStringInput(creator, writeRawValueFromString)
    }

    private fun boundsWithStringInput(creator: GeneratorCreator, operation: StringBackedOperation) {
        val data = String(CharArray(10))
        boundsWithStringInput(creator, operation, data, -1, 4)
        boundsWithStringInput(creator, operation, data, -6, 4)
        boundsWithStringInput(creator, operation, data, 4, -1)
        boundsWithStringInput(creator, operation, data, 4, -6)
        boundsWithStringInput(creator, operation, data, 9, 5)
        boundsWithStringInput(creator, operation, data, Int.MAX_VALUE, 4)
        boundsWithStringInput(creator, operation, data, Int.MAX_VALUE, Int.MAX_VALUE)
    }

    private fun boundsWithStringInput(creator: GeneratorCreator, operation: StringBackedOperation, data: String,
            offset: Int, length: Int) {
        val generator = creator.create()

        try {
            operation.call(generator, data, offset, length)
            fail("Should have thrown an exception")
        } catch (e: StreamWriteException) {
            verifyException(e, "Invalid 'offset'")
            verifyException(e, "'length'")
            verifyException(e, "arguments for `String` of length ${data.length}")
        }
    }

    /*
     *******************************************************************************************************************
     * Helper interfaces
     *******************************************************************************************************************
     */

    private fun interface GeneratorCreator {

        fun create(): CirJsonGenerator

    }

    private fun interface ByteBackedOperation {

        fun call(generator: CirJsonGenerator, data: ByteArray, offset: Int, length: Int)

    }

    private fun interface CharBackedOperation {

        fun call(generator: CirJsonGenerator, data: CharArray, offset: Int, length: Int)

    }

    private fun interface StringBackedOperation {

        fun call(generator: CirJsonGenerator, data: String, offset: Int, length: Int)

    }

}