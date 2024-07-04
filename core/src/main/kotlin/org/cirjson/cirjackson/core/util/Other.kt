package org.cirjson.cirjackson.core.util

object Other {

    fun throwInternal() {
        throw RuntimeException("Internal error: this code path should never get executed")
    }

    fun <T> throwInternalReturnAny(): T {
        throw RuntimeException("Internal error: this code path should never get executed")
    }

}