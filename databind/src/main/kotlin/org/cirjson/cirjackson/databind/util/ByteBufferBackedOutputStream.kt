package org.cirjson.cirjackson.databind.util

import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * Simple [OutputStream] implementation that appends content written in given [ByteBuffer] instance.
 */
open class ByteBufferBackedOutputStream(protected val myBuffer: ByteBuffer) : OutputStream() {

    @Throws(IOException::class)
    override fun write(b: Int) {
        myBuffer.put(b.toByte())
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
        myBuffer.put(b, off, len)
    }

    override fun close() {}

}