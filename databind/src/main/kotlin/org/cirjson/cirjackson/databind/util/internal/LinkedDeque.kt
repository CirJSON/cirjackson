package org.cirjson.cirjackson.databind.util.internal

import java.util.*

/**
 * Linked list implementation of the [Deque] interface where the link pointers are tightly integrated with the element.
 * Linked deques have no capacity restrictions; they grow as necessary to support usage. They are not thread-safe; in
 * the absence of external synchronization, they do not support concurrent access by multiple threads. Null elements are
 * prohibited.
 *
 * Most `LinkedDeque` operations run in constant time by assuming that the `Linked` parameter is associated with the
 * deque instance. Any usage that violates this assumption will result in non-deterministic behavior.
 *
 * The iterators returned by this class are **not** *fail-fast*: If the deque is modified at any time after the iterator
 * is created, the iterator will be in an unknown state. Thus, in the face of concurrent modification, the iterator
 * risks arbitrary, non-deterministic behavior at an undetermined time in the future.
 *
 * @param E The type of elements held in this collection
 */
internal class LinkedDeque<E : Linked<E>> : AbstractMutableCollection<E>(), Deque<E> {

    /**
     * Pointer to first node.
     *
     * Invariant: `(first == null && last == null) || (first.prev == null)`
     */
    private var myFirst: E? = null

    /**
     * Pointer to last node.
     *
     * Invariant: `(first == null && last == null) || (last.next == null)`
     */
    private var myLast: E? = null

    /**
     * Links the element to the front of the deque so that it becomes the first element.
     *
     * @param e the unlinked element
     */
    private fun linkFirst(e: E) {
        val f = myFirst
        myFirst = e

        if (f == null) {
            myLast = e
        } else {
            f.previous = e
            e.next = f
        }
    }

    private fun linkLast(e: E) {
        val l = myLast
        myLast = e

        if (l == null) {
            myFirst = e
        } else {
            l.next = e
            e.previous = l
        }
    }

    /**
     * Unlinks the non-null first element.
     *
     * @return The previous first element
     */
    private fun unlinkFirst(): E {
        val f = myFirst!!
        val next = f.next
        f.next = null
        myFirst = next

        if (next == null) {
            myLast = null
        } else {
            next.previous = null
        }

        return f
    }

    /**
     * Unlinks the non-null last element.
     *
     * @return The previous last element
     */
    private fun unlinkLast(): E {
        val l = myLast!!
        val prev = l.previous
        l.previous = null
        myLast = prev

        if (prev == null) {
            myFirst = null
        } else {
            prev.next = null
        }

        return l
    }

    /**
     * Unlinks the non-null element.
     *
     * @param e The element to unlink
     */
    private fun unlink(e: E) {
        val prev = e.previous
        val next = e.next

        if (prev == null) {
            myFirst = next
        } else {
            prev.next = next
            e.previous = null
        }

        if (next == null) {
            myLast = prev
        } else {
            next.previous = prev
            e.next = null
        }
    }

    override fun isEmpty(): Boolean {
        return myFirst == null
    }

    private fun checkNotEmpty() {
        if (isEmpty()) {
            throw NoSuchElementException()
        }
    }

    /**
     * Returns the number of elements in this deque.
     *
     * Beware that, unlike in most collections, this method is *NOT* a constant-time operation.
     *
     * @return the number of elements in this deque
     */
    override val size: Int
        get() {
            var size = 0

            var e = myFirst

            while (e != null) {
                size++
                e = e.next
            }

            return size
        }

    override fun clear() {
        var e = myFirst

        while (e != null) {
            val next = e.next
            e.previous = null
            e.next = null
            e = next
        }

        myFirst = null
        myLast = null
    }

    override fun contains(element: E): Boolean {
        return element.previous != null || element.next != null || element === myFirst
    }

    /**
     * Moves the element to the front of the deque so that it becomes the first element.
     *
     * @param e the linked element
     */
    fun moveToFront(e: E) {
        if (e !== myFirst) {
            unlink(e)
            linkFirst(e)
        }
    }

    /**
     * Moves the element to the back of the deque so that it becomes the last element.
     *
     * @param e the linked element
     */
    fun moveToBack(e: E) {
        if (e !== myLast) {
            unlink(e)
            linkLast(e)
        }
    }

    override fun add(element: E): Boolean {
        return offerLast(element)
    }

    override fun iterator(): MutableIterator<E> {
        return object : AbstractLinkedIterator(myFirst) {

            override fun computeNext(): E? {
                return cursor!!.next
            }

        }
    }

    override fun remove(): E {
        return removeFirst()
    }

    override fun remove(element: E): Boolean {
        if (contains(element)) {
            unlink(element)
            return true
        }

        return false
    }

    override fun poll(): E? {
        return pollFirst()
    }

    override fun element(): E {
        return first
    }

    override fun peek(): E? {
        return peekFirst()
    }

    override fun removeFirst(): E {
        checkNotEmpty()
        return pollFirst()!!
    }

    override fun removeLast(): E {
        checkNotEmpty()
        return pollLast()!!
    }

    override fun pollFirst(): E? {
        return if (isEmpty()) null else unlinkFirst()
    }

    override fun pollLast(): E? {
        return if (isEmpty()) null else unlinkLast()
    }

    override fun getFirst(): E {
        checkNotEmpty()
        return peekFirst()!!
    }

    override fun getLast(): E {
        checkNotEmpty()
        return peekLast()!!
    }

    override fun peekFirst(): E? {
        return myFirst
    }

    override fun peekLast(): E? {
        return myLast
    }

    override fun removeFirstOccurrence(o: Any?): Boolean {
        o as? E ?: throw IllegalArgumentException()
        return remove(o)
    }

    override fun removeLastOccurrence(o: Any?): Boolean {
        o as? E ?: throw IllegalArgumentException()
        return remove(o)
    }

    override fun pop(): E {
        return removeFirst()
    }

    override fun descendingIterator(): Iterator<E> {
        return object : AbstractLinkedIterator(myLast) {

            override fun computeNext(): E? {
                return cursor!!.previous
            }

        }
    }

    override fun push(e: E) {
        addFirst(e)
    }

    override fun offerLast(e: E): Boolean {
        if (contains(e)) {
            return false
        }

        linkLast(e)
        return true
    }

    override fun offerFirst(e: E): Boolean {
        if (contains(e)) {
            return false
        }

        linkFirst(e)
        return true
    }

    override fun addLast(e: E) {
        if (!offerLast(e)) {
            throw IllegalArgumentException()
        }
    }

    override fun addFirst(e: E) {
        if (!offerFirst(e)) {
            throw IllegalArgumentException()
        }
    }

    override fun offer(e: E): Boolean {
        return offerLast(e)
    }

    /**
     * @constructor Creates an iterator that can can traverse the deque.
     *
     * @param cursor the initial element to begin traversal from
     */
    abstract inner class AbstractLinkedIterator(var cursor: E?) : MutableIterator<E> {

        override fun next(): E {
            if (!hasNext()) {
                throw NoSuchElementException()
            }

            val e = cursor!!
            cursor = computeNext()
            return e
        }

        override fun hasNext(): Boolean {
            return cursor != null
        }

        /**
         * Retrieves the next element to traverse to or `null` if there are no more elements.
         */
        abstract fun computeNext(): E?

        override fun remove() {
            throw UnsupportedOperationException()
        }

    }

}