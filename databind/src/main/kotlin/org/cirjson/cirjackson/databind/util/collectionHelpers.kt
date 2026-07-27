package org.cirjson.cirjackson.databind.util

fun <E> List<E>.asMutable(): MutableList<E> {
    return this as? MutableList ?: toMutableList()
}