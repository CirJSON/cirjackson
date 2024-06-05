package org.cirjson.cirjackson.core.util

fun <T> Snapshottable<T>?.takeSnapshot(): T? {
    return this?.snapshot()
}
