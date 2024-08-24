package org.cirjson.cirjackson.core

import org.cirjson.cirjackson.core.cirjson.async.NonBlockingByteArrayCirJsonParser
import org.cirjson.cirjackson.core.cirjson.async.NonBlockingByteBufferCirJsonParser
import org.cirjson.cirjackson.core.support.AsyncByteArrayReaderWrapper
import org.cirjson.cirjackson.core.support.AsyncByteBufferReaderWrapper
import org.cirjson.cirjackson.core.support.AsyncReaderWrapperBase

abstract class AsyncTestBase : TestBase() {

    protected fun cirJsonDoc(doc: String): ByteArray {
        return utf8Bytes(doc)
    }

    companion object {

        const val UNICODE_2_BYTES = 167.toChar()

        const val UNICODE_3_BYTES = 0x4567.toChar()

        const val UNICODE_SEGMENT = "[$UNICODE_2_BYTES/$UNICODE_3_BYTES]"

        fun asyncForBytes(factory: TokenStreamFactory, bytesPerFeed: Int, doc: ByteArray,
                padding: Int): AsyncReaderWrapperBase {
            return AsyncByteArrayReaderWrapper(
                    factory.createNonBlockingByteArrayParser<NonBlockingByteArrayCirJsonParser>(
                            ObjectReadContext.empty()), bytesPerFeed, doc, padding)
        }

        fun asyncForByteBuffer(factory: TokenStreamFactory, bytesPerFeed: Int, doc: ByteArray,
                padding: Int): AsyncReaderWrapperBase {
            return AsyncByteBufferReaderWrapper(
                    factory.createNonBlockingByteBufferParser<NonBlockingByteBufferCirJsonParser>(
                            ObjectReadContext.empty()), bytesPerFeed, doc, padding)
        }

    }

}