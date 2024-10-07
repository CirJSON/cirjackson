package org.cirjson.cirjackson.core.util

import java.io.ByteArrayOutputStream

class TestingByteArrayOutputStream : ByteArrayOutputStream() {

    private var closeCount = 0

    var flushCount = 0
        private set

    override fun close() {
        ++closeCount
        super.close()
    }

    override fun flush() {
        ++flushCount
        super.flush()
    }

    val isClosed
        get() = closeCount > 0

}