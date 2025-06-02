package org.cirjson.cirjackson.databind.util

/**
 * Node of a forward-only linked list.
 */
class LinkedNode<T : Any>(private val myValue: T, private var myNext: LinkedNode<T>?) {

    fun linkNext(next: LinkedNode<T>) {
        if (myNext != null) {
            throw IllegalStateException()
        }

        myNext = next
    }

    fun next(): LinkedNode<T>? {
        return myNext
    }

    fun value(): T {
        return myValue
    }

    companion object {

        /**
         * Convenience method that can be used to check if a linked list with given head node (which may be `null` to
         * indicate an empty list) contains given value. It checks using `===` instead of `==`.
         *
         * @param T Type argument that defines contents of the linked list parameter
         *
         * @param node Head node of the linked list, or `null` to indicate an empty list
         *
         * @param value Value to look for
         *
         * @return `true` if the linked list contains the value, `false` otherwise. If [node] is `null`, it returns
         * `false`.
         */
        fun <T : Any> contains(node: LinkedNode<T>?, value: T): Boolean {
            var current = node

            while (current != null) {
                if (current.value() === value) {
                    return true
                }

                current = current.next()
            }

            return false
        }

    }

}