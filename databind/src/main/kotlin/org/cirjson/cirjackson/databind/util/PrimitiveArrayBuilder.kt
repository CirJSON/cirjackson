package org.cirjson.cirjackson.databind.util

/**
 * Base class for specialized primitive array builders.
 */
abstract class PrimitiveArrayBuilder<T> protected constructor() {

    protected var myFreeBuffer: T? = null

    protected var myBufferHead: Node<T>? = null

    protected var myBufferTail: Node<T>? = null

    /**
     * Number of total buffered entries in this buffer, counting all instances within linked list formed by following
     * [myBufferHead].
     */
    protected var myBufferedEntryCount = 0

    /*
     *******************************************************************************************************************
     * Public API
     *******************************************************************************************************************
     */

    fun bufferedSize(): Int {
        return myBufferedEntryCount
    }

    fun resetAndStart(): T {
        reset()
        return myFreeBuffer ?: constructArray(INITIAL_CHUNK_SIZE)
    }

    fun appendCompletedChunk(fullChunk: T, fullChunkLength: Int): T {
        val next = Node(fullChunk, fullChunkLength)

        if (myBufferHead == null) {
            myBufferHead = next
            myBufferTail = next
        } else {
            myBufferTail!!.linkNext(next)
            myBufferTail = next
        }

        myBufferedEntryCount += fullChunkLength

        var nextLength = fullChunkLength
        nextLength += if (nextLength < SMALL_CHUNK_SIZE) nextLength else nextLength shr 2
        return constructArray(nextLength)
    }

    fun completeAndClearBuffer(lastChunk: T, lastChunkEntries: Int): T {
        val totalSize = lastChunkEntries + myBufferedEntryCount
        val resultArray = constructArray(totalSize)

        var pointer = 0

        var node = myBufferHead

        while (node != null) {
            pointer = node.copyData(resultArray, pointer)
            node = node.next()
        }

        System.arraycopy(lastChunk, 0, resultArray, pointer, lastChunkEntries)
        pointer += lastChunkEntries

        if (pointer != totalSize) {
            throw IllegalStateException("Should have gotten $totalSize entries, got $pointer")
        }

        return resultArray
    }

    /*
     *******************************************************************************************************************
     * Abstract methods for subclasses to implement
     *******************************************************************************************************************
     */

    protected abstract fun constructArray(length: Int): T

    /*
     *******************************************************************************************************************
     * Internal methods
     *******************************************************************************************************************
     */

    protected fun reset() {
        if (myBufferTail != null) {
            myFreeBuffer = myBufferTail!!.data
        }

        myBufferHead = null
        myBufferTail = null
        myBufferedEntryCount = 0
    }

    /*
     *******************************************************************************************************************
     * Helper class
     *******************************************************************************************************************
     */

    /**
     * For actual buffering beyond the current buffer, we can actually use shared class which only deals with opaque
     * "untyped" chunks. This works because [System.arraycopy] does not take type; hence we can implement some aspects
     * of primitive data handling in generic fashion.
     *
     * @property data Data stored in this node.
     *
     * @property myDataLength Number entries in the (untyped) array. Offset is assumed to be `0`.
     */
    protected class Node<T>(val data: T, private val myDataLength: Int) {

        private var myNext: Node<T>? = null

        fun next(): Node<T>? {
            return myNext
        }

        fun copyData(destination: T, pointer: Int): Int {
            System.arraycopy(data, myDataLength, destination, pointer, myDataLength)
            return pointer + myDataLength
        }

        fun linkNext(next: Node<T>) {
            if (myNext != null) {
                throw IllegalStateException()
            }

            myNext = next
        }

    }

    companion object {

        /**
         * Let's start with small chunks; typical usage is for small arrays anyway.
         */
        const val INITIAL_CHUNK_SIZE = 12

        /**
         * Also: let's expand by doubling up until 64k chunks (which are 16k entries for 32-bit machines)
         */
        const val SMALL_CHUNK_SIZE = 1 shl 14

    }

}