package org.cirjson.cirjackson.core.extensions

fun IntArray?.growBy(toAdd: Int): IntArray {
    if (this == null) {
        return IntArray(toAdd)
    }

    val newSize = size + toAdd

    if (newSize < 0) {
        throw IllegalArgumentException("Unable to grow array to longer than `Int.MAX_VALUE`")
    }

    return copyOf(newSize)
}
