package org.cirjson.cirjackson.core.support

import java.io.FilterReader
import java.io.Reader
import java.io.StringReader
import kotlin.math.min

class ThrottledReader(reader: Reader, private val myMaxChars: Int) : FilterReader(reader) {

    constructor(doc: String, maxChars: Int) : this(StringReader(doc), maxChars)

    override fun read(cbuf: CharArray): Int {
        return read(cbuf, 0, cbuf.size)
    }

    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        return `in`.read(cbuf, off, min(myMaxChars, len))
    }

}