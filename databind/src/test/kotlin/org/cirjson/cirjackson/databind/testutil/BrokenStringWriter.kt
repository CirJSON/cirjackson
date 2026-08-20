package org.cirjson.cirjackson.databind.testutil

import java.io.FilterWriter
import java.io.IOException
import java.io.StringWriter

class BrokenStringWriter(private val myMessage: String?) : FilterWriter(StringWriter()) {

    override fun write(cbuf: CharArray, off: Int, len: Int) {
        throw IOException(myMessage)
    }

    override fun write(str: String, off: Int, len: Int) {
        throw IOException(myMessage)
    }

    override fun write(c: Int) {
        throw IOException(myMessage)
    }

}