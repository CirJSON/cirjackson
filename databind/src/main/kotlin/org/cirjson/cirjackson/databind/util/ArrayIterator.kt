package org.cirjson.cirjackson.databind.util

/**
 * Iterator implementation used to efficiently expose the contents of an Array as read-only iterator.
 */
open class ArrayIterator<T>(private val myArray: Array<T>) : Iterator<T>, Iterable<T> {

    private var myIndex = 0

    override fun hasNext(): Boolean {
        return myIndex < myArray.size
    }

    override fun next(): T {
        if (myIndex >= myArray.size) {
            throw NoSuchElementException()
        }

        return myArray[myIndex++]
    }

    override fun iterator(): Iterator<T> {
        return this
    }

}