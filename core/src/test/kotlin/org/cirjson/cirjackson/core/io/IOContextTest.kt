package org.cirjson.cirjackson.core.io

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.util.BufferRecycler
import kotlin.test.Test
import kotlin.test.assertNotNull

class IOContextTest : TestBase() {

    @Test
    fun testAllocations() {
        val context = IOContext(StreamReadConstraints.defaults(), StreamWriteConstraints.defaults(),
                ErrorReportConfiguration.defaults(), BufferRecycler(), ContentReference.rawReference("N/A"), true,
                CirJsonEncoding.UTF8)

        assertNotNull(context.constructTextBuffer())
        assertNotNull(context.allocateReadIOBuffer())

        try {
            context.allocateReadIOBuffer()
        } catch (e: IllegalStateException) {
            verifyException(e, "second time")
        }

        // Also: can't succeed with different buffer
        try {
            context.releaseReadIOBuffer(ByteArray(1))
        } catch (e: IllegalArgumentException) {
            verifyException(e, "smaller than original")
        }

        // but call with null is a NOP for convenience
        context.releaseReadIOBuffer(null)

        /* I/O Write buffer */

        assertNotNull(context.allocateWriteEncodingBuffer())

        try {
            context.allocateWriteEncodingBuffer()
        } catch (e: IllegalStateException) {
            verifyException(e, "second time")
        }

        try {
            context.releaseWriteEncodingBuffer(ByteArray(1))
        } catch (e: IllegalArgumentException) {
            verifyException(e, "smaller than original")
        }

        context.releaseWriteEncodingBuffer(null)

        /* Token (read) buffer */

        assertNotNull(context.allocateTokenBuffer())

        try {
            context.allocateTokenBuffer()
        } catch (e: IllegalStateException) {
            verifyException(e, "second time")
        }

        try {
            context.releaseTokenBuffer(CharArray(1))
        } catch (e: IllegalArgumentException) {
            verifyException(e, "smaller than original")
        }

        context.releaseTokenBuffer(null)

        /* Concat (write?) buffer */

        assertNotNull(context.allocConcatBuffer())

        try {
            context.allocConcatBuffer()
        } catch (e: IllegalStateException) {
            verifyException(e, "second time")
        }

        try {
            context.releaseConcatBuffer(CharArray(1))
        } catch (e: IllegalArgumentException) {
            verifyException(e, "smaller than original")
        }

        context.releaseConcatBuffer(null)

        /* Base64 (write?) buffer */

        assertNotNull(context.allocateBase64Buffer())

        try {
            context.allocateBase64Buffer()
        } catch (e: IllegalStateException) {
            verifyException(e, "second time")
        }

        try {
            context.releaseBase64Buffer(ByteArray(1))
        } catch (e: IllegalArgumentException) {
            verifyException(e, "smaller than original")
        }

        context.releaseBase64Buffer(null)
        context.releaseBase64Buffer(ByteArray(2048))
        context.close()
        context.close()
    }

    @Test
    fun testSizedAllocations() {
        val context = IOContext(StreamReadConstraints.defaults(), StreamWriteConstraints.defaults(),
                ErrorReportConfiguration.defaults(), BufferRecycler(), ContentReference.rawReference("N/A"), true,
                CirJsonEncoding.UTF8)

        assertNotNull(context.allocateReadIOBuffer(16))

        try {
            context.allocateReadIOBuffer(16)
        } catch (e: IllegalStateException) {
            verifyException(e, "second time")
        }

        // Also: can't succeed with different buffer
        try {
            context.releaseReadIOBuffer(ByteArray(1))
        } catch (e: IllegalArgumentException) {
            verifyException(e, "smaller than original")
        }

        // but call with null is a NOP for convenience
        context.releaseReadIOBuffer(null)

        /* I/O Write buffer */

        assertNotNull(context.allocateWriteEncodingBuffer(16))

        try {
            context.allocateWriteEncodingBuffer(16)
        } catch (e: IllegalStateException) {
            verifyException(e, "second time")
        }

        try {
            context.releaseWriteEncodingBuffer(ByteArray(1))
        } catch (e: IllegalArgumentException) {
            verifyException(e, "smaller than original")
        }

        context.releaseWriteEncodingBuffer(null)

        /* Token (read) buffer */

        assertNotNull(context.allocateTokenBuffer(16))

        try {
            context.allocateTokenBuffer(16)
        } catch (e: IllegalStateException) {
            verifyException(e, "second time")
        }

        try {
            context.releaseTokenBuffer(CharArray(1))
        } catch (e: IllegalArgumentException) {
            verifyException(e, "smaller than original")
        }

        context.releaseTokenBuffer(null)

        /* Base64 (write?) buffer */

        assertNotNull(context.allocateBase64Buffer(100))

        try {
            context.allocateBase64Buffer(100)
        } catch (e: IllegalStateException) {
            verifyException(e, "second time")
        }

        try {
            context.releaseBase64Buffer(ByteArray(1))
        } catch (e: IllegalArgumentException) {
            verifyException(e, "smaller than original")
        }

        context.releaseBase64Buffer(null)

        /* NameCopy (write?) buffer */

        assertNotNull(context.allocateNameCopyBuffer(100))

        try {
            context.allocateNameCopyBuffer(100)
        } catch (e: IllegalStateException) {
            verifyException(e, "second time")
        }

        try {
            context.releaseNameCopyBuffer(CharArray(1))
        } catch (e: IllegalArgumentException) {
            verifyException(e, "smaller than original")
        }

        context.releaseNameCopyBuffer(null)
        context.releaseNameCopyBuffer(CharArray(200))
        val buffer = context.allocateNameCopyBuffer(100)
        context.releaseNameCopyBuffer(buffer)
        context.close()
        context.close()
    }

}