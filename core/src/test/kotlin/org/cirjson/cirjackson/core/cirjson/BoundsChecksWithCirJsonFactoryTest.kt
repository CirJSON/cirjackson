package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.ObjectReadContext
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.exception.StreamReadException
import kotlin.test.Test
import kotlin.test.fail

class BoundsChecksWithCirJsonFactoryTest : TestBase() {

    @Test
    fun testBoundsWithByteArrayInput() {
        val factory = newStreamFactory()

        boundsWithByteArrayInput { data, offset, length ->
            factory.createParser(ObjectReadContext.empty(), data, offset, length)
        }
    }

    private fun boundsWithByteArrayInput(creator: ByteBackedCreator) {
        boundsWithByteArrayInput(creator, BYTE_DATA, -1, 1)
        boundsWithByteArrayInput(creator, BYTE_DATA, 4, -1)
        boundsWithByteArrayInput(creator, BYTE_DATA, 4, -6)
        boundsWithByteArrayInput(creator, BYTE_DATA, 9, 5)
        boundsWithByteArrayInput(creator, BYTE_DATA, Int.MAX_VALUE, 4)
        boundsWithByteArrayInput(creator, BYTE_DATA, Int.MAX_VALUE, Int.MAX_VALUE)
        boundsWithByteArrayInput(creator, null, 0, 3)
    }

    private fun boundsWithByteArrayInput(creator: ByteBackedCreator, data: ByteArray?, offset: Int, length: Int) {
        try {
            creator.call(data, offset, length)
            fail("Should not pass")
        } catch (e: StreamReadException) {
            if (data != null) {
                verifyException(e, "Invalid 'offset'")
                verifyException(e, "'len'")
                verifyException(e, "arguments for `ByteArray` of length ${data.size}")
            } else {
                verifyException(e, "Invalid `ByteArray` argument: `null`")
            }
        }
    }

    @Test
    fun testBoundsWithCharArrayInput() {
        val factory = newStreamFactory()

        boundsWithCharArrayInput { data, offset, length ->
            factory.createParser(ObjectReadContext.empty(), data, offset, length)
        }
    }

    private fun boundsWithCharArrayInput(creator: CharBackedCreator) {
        boundsWithCharArrayInput(creator, CHAR_DATA, -1, 1)
        boundsWithCharArrayInput(creator, CHAR_DATA, 4, -1)
        boundsWithCharArrayInput(creator, CHAR_DATA, 4, -6)
        boundsWithCharArrayInput(creator, CHAR_DATA, 9, 5)
        boundsWithCharArrayInput(creator, CHAR_DATA, Int.MAX_VALUE, 4)
        boundsWithCharArrayInput(creator, CHAR_DATA, Int.MAX_VALUE, Int.MAX_VALUE)
        boundsWithCharArrayInput(creator, null, 0, 3)
    }

    private fun boundsWithCharArrayInput(creator: CharBackedCreator, data: CharArray?, offset: Int, length: Int) {
        try {
            creator.call(data, offset, length)
            fail("Should not pass")
        } catch (e: StreamReadException) {
            if (data != null) {
                verifyException(e, "Invalid 'offset'")
                verifyException(e, "'len'")
                verifyException(e, "arguments for `CharArray` of length ${data.size}")
            } else {
                verifyException(e, "Invalid `CharArray` argument: `null`")
            }
        }
    }

    fun interface ByteBackedCreator {

        fun call(data: ByteArray?, offset: Int, length: Int)

    }

    fun interface CharBackedCreator {

        fun call(data: CharArray?, offset: Int, length: Int)

    }

    companion object {

        private val BYTE_DATA = ByteArray(10)

        private val CHAR_DATA = CharArray(10)

    }

}