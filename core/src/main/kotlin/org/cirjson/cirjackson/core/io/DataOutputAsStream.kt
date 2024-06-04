package org.cirjson.cirjackson.core.io

import java.io.DataOutput
import java.io.OutputStream

/**
 * Helper class to support use of [DataOutput] for output, directly, without caller having to provide for
 * implementation.
 */
class DataOutputAsStream(private val myOutput: DataOutput) : OutputStream() {

    override fun write(b: Int) {
        myOutput.write(b)
    }

    override fun write(b: ByteArray) {
        myOutput.write(b)
    }

    override fun write(b: ByteArray, offset: Int, length: Int) {
        myOutput.write(b, offset, length)
    }

}