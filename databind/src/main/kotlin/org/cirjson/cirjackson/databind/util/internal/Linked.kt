package org.cirjson.cirjackson.databind.util.internal

import java.util.*

/**
 * An element that is linked on the [Deque].
 */
internal interface Linked<T : Linked<T>> {

    /**
     * The previous element or `null` if either the element is unlinked or is the first element on the deque.
     */
    var previous: T?

    /**
     * The next element or `null` if either the element is unlinked or is the last element on the deque.
     */
    var next: T?

}