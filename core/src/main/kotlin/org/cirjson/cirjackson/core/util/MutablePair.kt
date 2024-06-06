package org.cirjson.cirjackson.core.util

class MutablePair<A, B>(var first: A, var second: B) {

    override fun toString(): String = "($first, $second)"

}