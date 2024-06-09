package org.cirjson.cirjackson.core.io

import java.io.OutputStream
import java.io.Writer

class UTF8Writer(private val myContext: IOContext, private val myOutput: OutputStream) : Writer() {

    override fun close() {
        TODO("Not yet implemented")
    }

    override fun flush() {
        TODO("Not yet implemented")
    }

    override fun write(cbuf: CharArray, off: Int, len: Int) {
        TODO("Not yet implemented")
    }

    override fun write(c: Int) {
        TODO("Not yet implemented")
    }

    override fun write(cbuf: CharArray) {
        TODO("Not yet implemented")
    }

    override fun write(str: String) {
        TODO("Not yet implemented")
    }

    override fun write(str: String, off: Int, len: Int) {
        TODO("Not yet implemented")
    }

    override fun append(c: Char): Writer {
        TODO("Not yet implemented")
    }

}