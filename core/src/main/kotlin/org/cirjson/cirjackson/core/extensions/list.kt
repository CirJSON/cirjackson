package org.cirjson.cirjackson.core.extensions

fun <T> List<T>.clone(): List<T> {
    return ArrayList(this)
}

fun <T : Any> Array<out T?>.countNotNull(): Int = filterNotNull().size
