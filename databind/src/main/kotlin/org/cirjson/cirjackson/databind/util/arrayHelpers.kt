package org.cirjson.cirjackson.databind.util

inline fun <reified T> Array<T>.nullable(): Array<T?> {
    return Array(this.size) { this[it] }
}
