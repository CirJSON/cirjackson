package org.cirjson.cirjackson.databind.util

import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.min

/**
 * Simple [InputStream] implementation that exposes currently available content of a [ByteBuffer].
 */
open class ByteBufferBackedInputStream(protected val myBuffer: ByteBuffer) : InputStream() {

    override fun available(): Int {
        return myBuffer.remaining()
    }

    override fun close() {}

    @Throws(IOException::class)
    override fun read(): Int {
        return if (myBuffer.hasRemaining()) myBuffer.get().toInt() and 0xFF else -1
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (!myBuffer.hasRemaining()) {
            return -1
        }

        val length = min(len, myBuffer.remaining())
        myBuffer.get(b, off, length)
        return length
    }

}