package org.cirjson.cirjackson.core.util

fun <T> Snapshottable<T>?.takeSnapshot(): T? {
    return this?.snapshot()
}

infix fun <A, B> A.mutableTo(that: B): MutablePair<A, B> = MutablePair(this, that)
