package org.cirjson.cirjackson.core.io

import org.cirjson.cirjackson.core.CirJsonEncoding
import org.cirjson.cirjackson.core.ErrorReportConfiguration
import org.cirjson.cirjackson.core.StreamReadConstraints
import org.cirjson.cirjackson.core.StreamWriteConstraints
import org.cirjson.cirjackson.core.util.BufferRecycler
import org.cirjson.cirjackson.core.util.ReadConstrainedTextBuffer
import org.cirjson.cirjackson.core.util.TextBuffer

/**
 * To limit number of configuration and state objects to pass, all contextual objects that need to be passed by the
 * factory to readers and writers are combined under this object. One instance is created for each reader and writer.
 *
 * @constructor Main constructor to use.
 *
 * @param streamReadConstraints Constraints for streaming reads
 * @param streamWriteConstraints Constraints for streaming writes
 * @param errorReportConfiguration Configuration for error reporting
 * @param bufferRecycler BufferRecycler to use, if any (`null` if none)
 * @param contentReference Input source reference for location reporting
 * @param isResourceManaged Whether input source is managed (owned) by Jackson library
 * @param encoding Encoding in use
 */
open class IOContext(val streamReadConstraints: StreamReadConstraints,
        val streamWriteConstraints: StreamWriteConstraints, val errorReportConfiguration: ErrorReportConfiguration,
        val bufferRecycler: BufferRecycler?, val contentReference: ContentReference?, val isResourceManaged: Boolean,
        var encoding: CirJsonEncoding?) : AutoCloseable {

    /**
     * Flag that indicates whether this context instance should release configured `bufferRecycler` or not: if it does,
     * it needs to call (via [BufferRecycler.releaseToPool] when closed; if not, should do nothing (recycler life-cycle
     * is externally managed)
     */
    protected var myShouldReleaseRecycler = true

    /**
     * Reference to the allocated I/O buffer for low-level input reading, if any allocated.
     */
    protected var myReadIOBuffer: ByteArray? = null

    /**
     * Reference to the allocated I/O buffer used for low-level encoding-related buffering.
     */
    protected var myWriteEncodingBuffer: ByteArray? = null

    /**
     * Reference to the buffer allocated for temporary use with base64 encoding or decoding.
     */
    protected var myBase64Buffer: ByteArray? = null

    /**
     * Reference to the buffer allocated for tokenization purposes, in which character input is read, and from which it
     * can be further returned.
     */
    protected var myTokenCBuffer: CharArray? = null

    /**
     * Reference to the buffer allocated for buffering it for output, before being encoded: generally this means
     * concatenating output, then encoding when buffer fills up.
     */
    protected var myConcatCBuffer: CharArray? = null

    /**
     * Reference temporary buffer Parser instances need if calling app decides it wants to access name via
     * 'getTextCharacters' method. Regular text buffer can not be used as it may contain textual representation of the
     * value token.
     */
    protected var myNameCopyBuffer: CharArray? = null

    private var myIsClosed = false

    override fun close() {
        if (!myIsClosed) {
            myIsClosed = true

            if (myShouldReleaseRecycler) {
                myShouldReleaseRecycler = false
                bufferRecycler!!.releaseToPool()
            }
        }
    }

    /**
     * Method to call to prevent [bufferRecycler] release upon [close]: called when [bufferRecycler] life-cycle is
     * externally managed.
     */
    fun markBufferRecyclerReleased(): IOContext {
        myShouldReleaseRecycler = false
        return this
    }

    /*
     *******************************************************************************************************************
     * Public API, buffer management
     *******************************************************************************************************************
     */

    fun constructTextBuffer(): TextBuffer {
        return TextBuffer(bufferRecycler)
    }

    fun constructReadConstrainedTextBuffer(): ReadConstrainedTextBuffer {
        return ReadConstrainedTextBuffer(streamReadConstraints, bufferRecycler)
    }

    /**
     * Method for recycling or allocation byte buffer of "read I/O" type.
     *
     * Note: the method can only be called once during its life cycle. This is to protect against accidental sharing.
     *
     * @return Allocated or recycled byte buffer
     */
    fun allocateReadIOBuffer(): ByteArray {
        verifyAllocation(myReadIOBuffer)
        return bufferRecycler!!.allocateByteBuffer(BufferRecycler.BYTE_READ_IO_BUFFER).also { myReadIOBuffer = it }
    }

    /**
     * Variant of {@link allocReadIOBuffer} that specifies smallest acceptable buffer size.
     *
     * @param minSize Minimum size of the buffer to recycle or allocate
     *
     * @return Allocated or recycled byte buffer
     */
    fun allocateReadIOBuffer(minSize: Int): ByteArray {
        verifyAllocation(myReadIOBuffer)
        return bufferRecycler!!.allocateByteBuffer(BufferRecycler.BYTE_READ_IO_BUFFER, minSize)
                .also { myReadIOBuffer = it }
    }

    /**
     * Method for recycling or allocation byte buffer of "write encoding" type.
     *
     * Note: the method can only be called once during its life cycle. This is to protect against accidental sharing.
     *
     * @return Allocated or recycled byte buffer
     */
    fun allocateWriteEncodingBuffer(): ByteArray {
        verifyAllocation(myWriteEncodingBuffer)
        return bufferRecycler!!.allocateByteBuffer(BufferRecycler.BYTE_WRITE_ENCODING_BUFFER)
                .also { myWriteEncodingBuffer = it }
    }

    /**
     * Variant of {@link allocWriteEncodingBuffer} that specifies smallest acceptable buffer size.
     *
     * @param minSize Minimum size of the buffer to recycle or allocate
     *
     * @return Allocated or recycled byte buffer
     */
    fun allocateWriteEncodingBuffer(minSize: Int): ByteArray {
        verifyAllocation(myWriteEncodingBuffer)
        return bufferRecycler!!.allocateByteBuffer(BufferRecycler.BYTE_WRITE_ENCODING_BUFFER, minSize)
                .also { myWriteEncodingBuffer = it }
    }

    /**
     * Method for recycling or allocation byte buffer of "base 64 encode/decode" type.
     *
     * Note: the method can only be called once during its life cycle. This is to protect against accidental sharing.
     *
     * @return Allocated or recycled byte buffer
     */
    fun allocateBase64Buffer(): ByteArray {
        verifyAllocation(myBase64Buffer)
        return bufferRecycler!!.allocateByteBuffer(BufferRecycler.BYTE_BASE64_CODEC_BUFFER).also { myBase64Buffer = it }
    }

    /**
     * Variant of {@link allocBase64Buffer} that specifies smallest acceptable buffer size.
     *
     * @param minSize Minimum size of the buffer to recycle or allocate
     *
     * @return Allocated or recycled byte buffer
     */
    fun allocateBase64Buffer(minSize: Int): ByteArray {
        verifyAllocation(myBase64Buffer)
        return bufferRecycler!!.allocateByteBuffer(BufferRecycler.BYTE_BASE64_CODEC_BUFFER, minSize)
                .also { myBase64Buffer = it }
    }

    /**
     * Method for recycling or allocation char buffer of "tokenization" type.
     *
     * Note: the method can only be called once during its life cycle. This is to protect against accidental sharing.
     *
     * @return Allocated or recycled char buffer
     */
    fun allocateTokenBuffer(): CharArray {
        verifyAllocation(myTokenCBuffer)
        return bufferRecycler!!.allocateCharBuffer(BufferRecycler.CHAR_TOKEN_BUFFER).also { myTokenCBuffer = it }
    }

    /**
     * Variant of {@link allocateTokenBuffer} that specifies smallest acceptable buffer size.
     *
     * @param minSize Minimum size of the buffer to recycle or allocate
     *
     * @return Allocated or recycled char buffer
     */
    fun allocateTokenBuffer(minSize: Int): CharArray {
        verifyAllocation(myTokenCBuffer)
        return bufferRecycler!!.allocateCharBuffer(BufferRecycler.CHAR_TOKEN_BUFFER, minSize)
                .also { myTokenCBuffer = it }
    }

    /**
     * Method for recycling or allocation char buffer of "value buffering" type.
     *
     * Note: the method can only be called once during its life cycle. This is to protect against accidental sharing.
     *
     * @return Allocated or recycled char buffer
     */
    fun allocConcatBuffer(): CharArray {
        verifyAllocation(myConcatCBuffer)
        return bufferRecycler!!.allocateCharBuffer(BufferRecycler.CHAR_CONCAT_BUFFER).also { myConcatCBuffer = it }
    }

    /**
     * Method for recycling or allocation char buffer of "name storing" type that specifies smallest acceptable buffer
     * size.
     *
     * Note: the method can only be called once during its life cycle. This is to protect against accidental sharing.
     *
     * @return Allocated or recycled char buffer
     */
    fun allocateNameCopyBuffer(minSize: Int): CharArray {
        verifyAllocation(myNameCopyBuffer)
        return bufferRecycler!!.allocateCharBuffer(BufferRecycler.CHAR_NAME_COPY_BUFFER, minSize)
                .also { myNameCopyBuffer = it }
    }

    /**
     * Method to call when all the processing buffers can be safely recycled.
     *
     * @param buffer Buffer instance to release (return for recycling)
     */
    fun releaseReadIOBuffer(buffer: ByteArray?) {
        if (buffer == null) {
            return
        }

        verifyRelease(buffer, myReadIOBuffer!!)
        myReadIOBuffer = null
        bufferRecycler!!.releaseByteBuffer(BufferRecycler.BYTE_READ_IO_BUFFER, buffer)
    }

    /**
     * Method to call when all the processing buffers can be safely recycled.
     *
     * @param buffer Buffer instance to release (return for recycling)
     */
    fun releaseWriteEncodingBuffer(buffer: ByteArray?) {
        if (buffer == null) {
            return
        }

        verifyRelease(buffer, myWriteEncodingBuffer!!)
        myWriteEncodingBuffer = null
        bufferRecycler!!.releaseByteBuffer(BufferRecycler.BYTE_WRITE_ENCODING_BUFFER, buffer)
    }

    /**
     * Method to call when all the processing buffers can be safely recycled.
     *
     * @param buffer Buffer instance to release (return for recycling)
     */
    fun releaseBase64Buffer(buffer: ByteArray?) {
        if (buffer == null) {
            return
        }

        verifyRelease(buffer, myBase64Buffer!!)
        myBase64Buffer = null
        bufferRecycler!!.releaseByteBuffer(BufferRecycler.BYTE_BASE64_CODEC_BUFFER, buffer)
    }

    /**
     * Method to call when all the processing buffers can be safely recycled.
     *
     * @param buffer Buffer instance to release (return for recycling)
     */
    fun releaseTokenBuffer(buffer: CharArray?) {
        if (buffer == null) {
            return
        }

        verifyRelease(buffer, myTokenCBuffer!!)
        myReadIOBuffer = null
        bufferRecycler!!.releaseCharBuffer(BufferRecycler.CHAR_TOKEN_BUFFER, buffer)
    }

    /**
     * Method to call when all the processing buffers can be safely recycled.
     *
     * @param buffer Buffer instance to release (return for recycling)
     */
    fun releaseConcatBuffer(buffer: CharArray?) {
        if (buffer == null) {
            return
        }

        verifyRelease(buffer, myConcatCBuffer!!)
        myConcatCBuffer = null
        bufferRecycler!!.releaseCharBuffer(BufferRecycler.CHAR_CONCAT_BUFFER, buffer)
    }

    /**
     * Method to call when all the processing buffers can be safely recycled.
     *
     * @param buffer Buffer instance to release (return for recycling)
     */
    fun releaseNameCopyBuffer(buffer: CharArray?) {
        if (buffer == null) {
            return
        }

        verifyRelease(buffer, myNameCopyBuffer!!)
        myNameCopyBuffer = null
        bufferRecycler!!.releaseCharBuffer(BufferRecycler.CHAR_NAME_COPY_BUFFER, buffer)
    }

    /*
     *******************************************************************************************************************
     * Internal helpers
     *******************************************************************************************************************
     */

    protected fun verifyAllocation(buffer: Any?) {
        if (buffer != null) {
            throw IllegalStateException("Trying to call same allocXxx() method second time")
        }
    }

    protected fun verifyRelease(toRelease: ByteArray, source: ByteArray) {
        if (toRelease !== source && toRelease.size < source.size) {
            throw wrongBuffer()
        }
    }

    protected fun verifyRelease(toRelease: CharArray, source: CharArray) {
        if (toRelease !== source && toRelease.size < source.size) {
            throw wrongBuffer()
        }
    }

    private fun wrongBuffer(): IllegalArgumentException {
        return IllegalArgumentException("Trying to release buffer smaller than original")
    }

}