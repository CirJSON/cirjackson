package org.cirjson.cirjackson.core.util

import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Helper class that is similar to [java.io.ByteArrayOutputStream] in usage, but more geared to CirJackson use cases
 * internally. Specific changes include segment storage (no need to have linear backing buffer, can avoid reallocations,
 * copying), as well API not based on [OutputStream]. In short, a very much specialized builder object.
 *
 * Also implements [OutputStream] to allow efficient aggregation of output content as a byte array, similar to how
 * [java.io.ByteArrayOutputStream] works, but somewhat more efficiently for many use cases.
 *
 * NOTE: maximum size limited to Java Array maximum, 2 gigabytes: this because usage pattern is to collect content for a
 * `ByteArray` and so although theoretically this builder can aggregate more content it will not be usable as things
 * are. Behavior may be improved if we solve the access problem.
 */
class ByteArrayBuilder private constructor(private val myBufferRecycler: BufferRecycler?, initialBlock: ByteArray,
        var currentSegmentLength: Int) : OutputStream(), BufferRecycler.Gettable {

    var currentSegment: ByteArray? = initialBlock
        private set

    private var myPastLength = 0

    private val myPastBlocks = ArrayList<ByteArray>()

    constructor(bufferRecycler: BufferRecycler?, firstBlockSize: Int) : this(bufferRecycler,
            bufferRecycler?.allocateByteBuffer(BufferRecycler.BYTE_WRITE_CONCAT_BUFFER) ?: ByteArray(
                    min(firstBlockSize, MAX_BLOCK_SIZE)), 0)

    constructor(bufferRecycler: BufferRecycler?) : this(bufferRecycler, INITIAL_BLOCK_SIZE)

    constructor(firstBlockSize: Int) : this(null, firstBlockSize)

    constructor() : this(null)

    override fun bufferRecycler(): BufferRecycler? {
        return myBufferRecycler
    }

    /*
     *******************************************************************************************************************
     * OutputStream implementation
     *******************************************************************************************************************
     */

    override fun write(b: Int) {
        append(b)
    }

    override fun write(b: ByteArray) {
        write(b, 0, b.size)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        var offset = off
        var length = len

        while (true) {
            val max = currentSegment!!.size - currentSegmentLength
            val toCopy = min(length, max)

            if (toCopy > 0) {
                b.copyInto(currentSegment!!, currentSegmentLength, offset, offset + toCopy)
                offset += toCopy
                currentSegmentLength += toCopy
                length -= toCopy
            }

            if (length <= 0) {
                break
            }

            allocateMore()
        }
    }

    override fun close() {
        // no-op
    }

    override fun flush() {
        // no-op
    }

    /*
     *******************************************************************************************************************
     * Streaming API
     *******************************************************************************************************************
     */

    fun reset() {
        myPastLength = 0
        currentSegmentLength = 0

        myPastBlocks.clear()
    }

    /**
     * Number of bytes aggregated so far
     */
    val size: Int
        get() = myPastLength + currentSegmentLength

    /**
     * Clean up method to call to release all buffers this object may be using. After calling the method, no other
     * accessors can be used (and attempt to do so may result in an exception).
     */
    fun release() {
        reset()

        if (myBufferRecycler != null && currentSegment != null) {
            myBufferRecycler.releaseByteBuffer(BufferRecycler.BYTE_WRITE_CONCAT_BUFFER, currentSegment!!)
            currentSegment = null
        }
    }

    fun append(i: Int) {
        if (currentSegmentLength >= currentSegment!!.size) {
            allocateMore()
        }

        currentSegment!![currentSegmentLength++] = i.toByte()
    }

    fun appendTwoBytes(b16: Int) {
        if (currentSegmentLength + 1 < currentSegment!!.size) {
            currentSegment!![currentSegmentLength++] = (b16 shr 8).toByte()
            currentSegment!![currentSegmentLength++] = b16.toByte()
        } else {
            append(b16 shr 8)
            append(b16)
        }
    }

    fun appendThreeBytes(b24: Int) {
        if (currentSegmentLength + 2 < currentSegment!!.size) {
            currentSegment!![currentSegmentLength++] = (b24 shr 16).toByte()
            currentSegment!![currentSegmentLength++] = (b24 shr 8).toByte()
            currentSegment!![currentSegmentLength++] = b24.toByte()
        } else {
            append(b24 shr 16)
            append(b24 shr 8)
            append(b24)
        }
    }

    fun appendFourBytes(b32: Int) {
        if (currentSegmentLength + 3 < currentSegment!!.size) {
            currentSegment!![currentSegmentLength++] = (b32 shr 24).toByte()
            currentSegment!![currentSegmentLength++] = (b32 shr 16).toByte()
            currentSegment!![currentSegmentLength++] = (b32 shr 8).toByte()
            currentSegment!![currentSegmentLength++] = b32.toByte()
        } else {
            append(b32 shr 24)
            append(b32 shr 16)
            append(b32 shr 8)
            append(b32)
        }
    }

    fun toByteArray(): ByteArray {
        val totalLength = myPastLength + currentSegmentLength

        if (totalLength == 0) {
            return NO_BYTES
        }

        val result = ByteArray(totalLength)
        var offset = 0

        for (block in myPastBlocks) {
            val length = block.size
            block.copyInto(result, offset, 0, length)
            offset += length
        }

        currentSegment!!.copyInto(result, offset, 0, currentSegmentLength)
        offset += currentSegmentLength

        if (offset != totalLength) {
            throw RuntimeException("Internal error: total len assumed to be $totalLength, copied $offset bytes")
        }

        if (myPastBlocks.isNotEmpty()) {
            reset()
        }

        return result
    }

    /**
     * Method functionally same as calling:
     * ```
     * val result = toByteArray()
     * release()
     * return result
     * ```
     * that is; aggregates output contained in the builder (if any), clear state; returns buffer(s) to [BufferRecycler]
     * configured, if any, and returns output to caller.
     *
     * @return Byte array with built contents
     */
    fun getClearAndRelease(): ByteArray {
        val result = toByteArray()
        release()
        return result
    }

    /*
     *******************************************************************************************************************
     * Non-stream API (similar to TextBuffer)
     *******************************************************************************************************************
     */

    /**
     * Method called when starting "manual" output: will clear out current state and return the first segment buffer to
     * fill
     *
     * @return Segment to use for writing
     */
    fun resetAndGetFirstSegment(): ByteArray {
        reset()
        return currentSegment!!
    }

    /**
     * Method called when the current segment buffer is full; will append to current contents, allocate a new segment
     * buffer and return it
     *
     * @return Segment to use for writing
     */
    fun finishCurrentSegment(): ByteArray {
        allocateMore()
        return currentSegment!!
    }

    /**
     * Method that will complete "manual" output process, coalesce content (if necessary) and return results as a
     * contiguous buffer.
     *
     * @param lastBlockLength Amount of content in the current segment
     * buffer.
     *
     * @return Coalesced contents
     */
    fun completeAndCoalesce(lastBlockLength: Int): ByteArray {
        currentSegmentLength = lastBlockLength
        return toByteArray()
    }

    /*
     *******************************************************************************************************************
     * Internal helpers
     *******************************************************************************************************************
     */

    private fun allocateMore() {
        val newPastLength = myPastLength + currentSegment!!.size

        if (newPastLength < 0) {
            throw IllegalStateException("Maximum array size (2GB) exceeded by `ByteArrayBuilder`")
        }

        myPastLength = newPastLength
        val newSize = min(max(myPastLength shr 1, INITIAL_BLOCK_SIZE + INITIAL_BLOCK_SIZE), MAX_BLOCK_SIZE)
        myPastBlocks.add(currentSegment!!)
        currentSegment = ByteArray(newSize)
        currentSegmentLength = 0
    }

    companion object {

        val NO_BYTES = ByteArray(0)

        /**
         * Size of the first block we will allocate.
         */
        private const val INITIAL_BLOCK_SIZE = 500

        /**
         * Maximum block size we will use for individual non-aggregated blocks. Limit is 128k chunks.
         */
        private const val MAX_BLOCK_SIZE = (1 shl 17)

        fun fromInitial(initialBlock: ByteArray, length: Int): ByteArrayBuilder {
            return ByteArrayBuilder(null, initialBlock, length)
        }

    }

}