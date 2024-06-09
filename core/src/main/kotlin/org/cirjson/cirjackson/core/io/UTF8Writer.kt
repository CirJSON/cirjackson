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

    companion object {

        internal const val SURR1_FIRST: Int = 0xD800

        internal const val SURR1_LAST: Int = 0xDBFF

        internal const val SURR2_FIRST: Int = 0xDC00

        internal const val SURR2_LAST: Int = 0xDFFF

        val SURROGATE_BASE = 0x10000 - SURR2_FIRST - (SURR1_FIRST shl 10)

    }

}