package org.cirjson.cirjackson.databind.util

import kotlin.math.max
import kotlin.reflect.KClass

/**
 * Helper class to use for constructing Object arrays by appending entries to create arrays of various lengths (length
 * that is not known a priori).
 */
class ObjectBuffer {

    private var myHead: LinkedNode<Array<Any?>>? = null

    private var myTail: LinkedNode<Array<Any?>>? = null

    /**
     * Number of total buffered entries in this buffer, counting all instances within the linked list formed by
     * following [myHead].
     */
    private var mySize = 0

    private var myFreeBuffer: Array<Any?>? = null

    /*
     *******************************************************************************************************************
     * Public API
     *******************************************************************************************************************
     */

    /**
     * Method called to start the buffering process. Will ensure that the buffer is empty, and then return an object
     * array to start chunking content on.
     */
    fun resetAndStart(): Array<Any?> {
        reset()

        if (myFreeBuffer == null) {
            myFreeBuffer = arrayOfNulls(12)
        }

        return myFreeBuffer!!
    }

    fun resetAndStart(base: Array<Any?>, count: Int): Array<Any?> {
        reset()

        if (myFreeBuffer == null || myFreeBuffer!!.size < count) {
            myFreeBuffer = arrayOfNulls(max(12, count))
        }

        base.copyInto(myFreeBuffer!!, 0, 0, count)
        return myFreeBuffer!!
    }

    /**
     * Method called to add a full Object array as a chunk buffered within this buffer, and to get a new array to fill.
     * Caller is not to use the array it gives, but to use the returned array for continued buffering.
     *
     * @param fullChunk Completed chunk that the caller is requesting to append to this buffer. It is generally a chunk
     * returned by an earlier call to [resetAndStart] or [appendCompletedChunk] (although this is not required or
     * enforced)
     *
     * @return New chunk buffer for caller to fill
     */
    fun appendCompletedChunk(fullChunk: Array<Any?>): Array<Any?> {
        val next = LinkedNode(fullChunk, null)

        if (myHead == null) {
            myHead = next
            myTail = myHead
        } else {
            myTail!!.linkNext(next)
            myTail = next
        }

        var length = fullChunk.size
        mySize += length

        if (length < SMALL_CHUNK) {
            length += length
        } else if (length < MAX_CHUNK) {
            length += length shr 2
        }

        return arrayOfNulls(length)
    }

    /**
     * Method called to indicate that the buffering process is now complete; and to construct a combined exactly sized
     * result array. Additionally, the buffer itself will be reset to reduce memory retention.
     *
     * The resulting array will be of generic `Array<Any?>` type: if a typed array is needed, use the method with
     * additional type argument.
     */
    fun completeAndClearBuffer(lastChunk: Array<Any?>, lastChunkEntries: Int): Array<Any?> {
        val totalSize = mySize + lastChunkEntries
        val result = arrayOfNulls<Any?>(totalSize)
        copyTo(result, totalSize, lastChunk, lastChunkEntries)
        reset()
        return result
    }

    /**
     * Type-safe alternative to [completeAndClearBuffer], to allow for constructing the explicitly typed result array.
     *
     * @param componentType Type of elements included in the buffer. Will be used for constructing the result array.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> completeAndClearBuffer(lastChunk: Array<Any?>, lastChunkEntries: Int,
            componentType: KClass<T>): Array<T?> {
        val totalSize = mySize + lastChunkEntries
        val result = java.lang.reflect.Array.newInstance(componentType.java, totalSize) as Array<T?>
        copyTo(result as Array<Any?>, totalSize, lastChunk, lastChunkEntries)
        reset()
        return result
    }

    fun completeAndClearBuffer(lastChunk: Array<Any?>, lastChunkEntries: Int, resultList: MutableList<Any?>) {
        var node = myHead

        while (node != null) {
            resultList.addAll(node.value().toList())
            node = node.next()
        }

        resultList.addAll(lastChunk.toList().subList(0, lastChunkEntries))
        reset()
    }

    /**
     * Helper method that can be used to check how much free capacity will this instance start with. Can be used to
     * choose the best instance to reuse, based on the size of reusable object chunk buffer holds reference to.
     */
    fun initialCapacity(): Int {
        return myFreeBuffer?.size ?: 0
    }

    /**
     * Method that can be used to check how many Objects have been buffered within this buffer.
     */
    fun bufferedSize(): Int {
        return mySize
    }

    /*
     *******************************************************************************************************************
     * Internal methods
     *******************************************************************************************************************
     */

    private fun reset() {
        if (myTail != null) {
            myFreeBuffer = myTail!!.value()
        }

        myHead = null
        myTail = null
        mySize = 0
    }

    private fun copyTo(resultArray: Array<Any?>, totalSize: Int, lastChunk: Array<Any?>, lastChunkEntries: Int) {
        var pointer = 0

        var node = myHead

        while (node != null) {
            val current = node.value()
            current.copyInto(resultArray, pointer)
            node = node.next()
            pointer += current.size
        }

        lastChunk.copyInto(resultArray, pointer, 0, lastChunkEntries)
        pointer += lastChunkEntries

        if (pointer != totalSize) {
            throw IllegalStateException("Should have gotten $totalSize entries, got $pointer")
        }
    }

    companion object {

        /**
         * Let's expand by doubling up until 64k chunks (which represent 16k entries for 32-bit machines)
         */
        private const val SMALL_CHUNK = 1 shl 14

        /**
         * Let's limit the maximum size of chunks we use; helps avoid excessive allocation overhead for huge data sets.
         * For now, let's limit to quarter million entries, 1 meg chunks for 32-bit machines.
         */
        private const val MAX_CHUNK = 1 shl 18

    }

}