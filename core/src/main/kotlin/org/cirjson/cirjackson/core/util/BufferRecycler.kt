package org.cirjson.cirjackson.core.util

import java.util.concurrent.atomic.AtomicReferenceArray

/**
 * This is a small utility class, whose main functionality is to allow simple reuse of raw byte/char buffers. It is
 * usually allocated through [RecyclerPool]: multiple pool implementations exists.
 *
 * The default pool implementation uses `ThreadLocal` combined with `SoftReference`. The end result is a low-overhead
 * GC-cleanable recycling: hopefully ideal for use by stream readers.
 *
 * @constructor Alternate constructor to be used by subclasses, to allow customization of number of low-level buffers in
 * use.
 *
 * @param byteBufferCount Number of `ByteArray` buffers to allocate
 *
 * @param charBufferCount Number of `CharArray` buffers to allocate
 */
open class BufferRecycler protected constructor(byteBufferCount: Int, charBufferCount: Int) :
        RecyclerPool.WithPool<BufferRecycler> {

    protected val myByteBuffers = AtomicReferenceArray<ByteArray?>(byteBufferCount)

    protected val myCharBuffers = AtomicReferenceArray<CharArray?>(charBufferCount)

    private var myPool: RecyclerPool<BufferRecycler>? = null

    /**
     * Default constructor used for creating instances of this default implementation.
     */
    constructor() : this(4, 4)

    /**
     * True if this recycler is linked to pool and may be released with [releaseToPool]; false if no linkage exists.
     */
    val isLinkedWithPool: Boolean
        get() = myPool != null

    /*
     *******************************************************************************************************************
     * WithPool implementation
     *******************************************************************************************************************
     */

    /**
     * Method called by owner of this recycler instance, to provide reference to [RecyclerPool] into which instance is
     * to be released (if any)
     */
    override fun withPool(pool: RecyclerPool<BufferRecycler>): BufferRecycler {
        if (myPool != null) {
            throw IllegalStateException("BufferRecycler already linked to pool: $pool")
        }

        myPool = pool
        return this
    }

    /**
     * Method called when owner of this recycler no longer wishes use it; this should return it to pool passed via
     * `withPool` (if any).
     */
    override fun releaseToPool() {
        if (myPool != null) {
            val tempPool = myPool!!
            myPool = null
            tempPool.releasePooled(this)
        }
    }

    /*
     *******************************************************************************************************************
     * Public API, byte buffers
     *******************************************************************************************************************
     */

    fun allocateByteBuffer(index: Int): ByteArray {
        return allocateByteBuffer(index, 0)
    }

    open fun allocateByteBuffer(index: Int, minSize: Int): ByteArray {
        var realMinSize = minSize
        val defSize = byteBufferLength(index)

        if (realMinSize < defSize) {
            realMinSize = defSize
        }

        var buffer = myByteBuffers.getAndSet(index, null)

        if (buffer == null || buffer.size < realMinSize) {
            buffer = byteAllocation(realMinSize)
        }

        return buffer
    }

    open fun releaseByteBuffer(index: Int, buffer: ByteArray) {
        val oldBuffer = myByteBuffers[index]

        if (oldBuffer == null || buffer.size > oldBuffer.size) {
            myByteBuffers[index] = buffer
        }
    }

    /*
     *******************************************************************************************************************
     * Public API, char buffers
     *******************************************************************************************************************
     */

    fun allocateCharBuffer(index: Int): CharArray {
        return allocateCharBuffer(index, 0)
    }

    open fun allocateCharBuffer(index: Int, minSize: Int): CharArray {
        var realMinSize = minSize
        val defSize = charBufferLength(index)

        if (realMinSize < defSize) {
            realMinSize = defSize
        }

        var buffer = myCharBuffers.getAndSet(index, null)

        if (buffer == null || buffer.size < realMinSize) {
            buffer = charAllocation(realMinSize)
        }

        return buffer
    }

    open fun releaseCharBuffer(index: Int, buffer: CharArray) {
        val oldBuffer = myCharBuffers[index]

        if (oldBuffer == null || buffer.size > oldBuffer.size) {
            myCharBuffers[index] = buffer
        }
    }

    /*
     *******************************************************************************************************************
     * Overridable helper methods
     *******************************************************************************************************************
     */

    protected open fun byteBufferLength(index: Int): Int {
        return BYTE_BUFFER_LENGTHS[index]
    }

    protected open fun charBufferLength(index: Int): Int {
        return CHAR_BUFFER_LENGTHS[index]
    }

    /*
     *******************************************************************************************************************
     * Actual allocations separated for easier debugging/profiling
     *******************************************************************************************************************
     */

    protected open fun byteAllocation(size: Int): ByteArray {
        return ByteArray(size)
    }

    protected open fun charAllocation(size: Int): CharArray {
        return CharArray(size)
    }

    /**
     * Tag-on interface to allow various other types to expose [BufferRecycler] they are constructed with.
     */
    fun interface Gettable {

        /**
         * Simple way to get a new [BufferRecycler]
         *
         * @return Buffer recycler instance object is configured with, if any; whether this can be `null` depends on
         * type of object
         */
        fun bufferRecycler(): BufferRecycler?

    }

    companion object {

        /**
         * Buffer used for reading byte-based input.
         */
        const val BYTE_READ_IO_BUFFER = 0

        /**
         * Buffer used for temporarily storing encoded content; used for example by UTF-8 encoding writer
         */
        const val BYTE_WRITE_ENCODING_BUFFER = 1

        /**
         * Buffer used for temporarily concatenating output; used for example when requesting output as byte array.
         */
        const val BYTE_WRITE_CONCAT_BUFFER = 2

        /**
         * Buffer used for concatenating binary data that is either being encoded as base64 output, or decoded from
         * base64 input.
         */
        const val BYTE_BASE64_CODEC_BUFFER = 3

        /**
         * Buffer used as input buffer for tokenization for character-based parsers.
         */
        const val CHAR_TOKEN_BUFFER = 0

        /**
         * Buffer used by generators; for byte-backed generators for buffering of [String] values to output (before
         * encoding into UTF-8), and for char-backed generators as actual concatenation buffer.
         */
        const val CHAR_CONCAT_BUFFER = 1

        /**
         * Used through [TextBuffer]: directly by parsers (to concatenate String values) and indirectly via
         * [org.cirjson.cirjackson.core.io.SegmentedStringWriter] when serializing (databind level `ObjectMapper` and
         * `ObjectWriter`). In both cases used as segments (and not for whole value), but may result in retention of
         * larger chunks for big content (long text values during parsing; bigger output documents for generation).
         */
        const val CHAR_TEXT_BUFFER = 2

        /**
         * For parsers, temporary buffer into which `CharArray` for names is copied when requested as such; for
         * `WriterBasedGenerator` used for buffering during `writeString(Reader)` operation (not commonly used).
         */
        const val CHAR_NAME_COPY_BUFFER = 3

        private val BYTE_BUFFER_LENGTHS = intArrayOf(8000, 8000, 2000, 2000)

        private val CHAR_BUFFER_LENGTHS = intArrayOf(4000, 4000, 200, 200)

    }

}