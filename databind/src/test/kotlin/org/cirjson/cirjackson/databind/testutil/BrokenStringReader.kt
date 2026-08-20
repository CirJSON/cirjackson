package org.cirjson.cirjackson.databind.testutil

import java.io.FilterReader
import java.io.IOException
import java.io.StringReader

class BrokenStringReader(content: String, private val myMessage: String?) : FilterReader(StringReader(content)) {

    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        val i = super.read(cbuf, off, len)

        if (i < 0) {
            throw IOException(myMessage)
        }

        return i
    }

}