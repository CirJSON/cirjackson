package org.cirjson.cirjackson.core.util

/**
 * This is a small utility class, whose main functionality is to allow simple reuse of raw byte/char buffers. It is
 * usually allocated through [RecyclerPool]: multiple pool implementations exists.
 *
 * The default pool implementation uses `ThreadLocal` combined with `SoftReference`. The end result is a low-overhead
 * GC-cleanable recycling: hopefully ideal for use by stream readers.
 */
class BufferRecycler : RecyclerPool.WithPool<BufferRecycler> {

    private var myPool: RecyclerPool<BufferRecycler>? = null

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

    fun allocateCharBuffer(index: Int, minSize: Int): CharArray {
        TODO()
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
         * Buffer used as input buffer for tokenization for character-based parsers.
         */
        const val CHAR_TOKEN_BUFFER = 0

    }

}