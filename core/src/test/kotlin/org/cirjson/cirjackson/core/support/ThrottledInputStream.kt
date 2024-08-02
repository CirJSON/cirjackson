package org.cirjson.cirjackson.core.support

import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.InputStream
import kotlin.math.min

class ThrottledInputStream(input: InputStream, private val myMaxBytes: Int) : FilterInputStream(input) {

    constructor(data: ByteArray, maxBytes: Int) : this(ByteArrayInputStream(data), maxBytes)

    override fun read(b: ByteArray): Int {
        return read(b, 0, b.size)
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        return `in`.read(b, off, min(myMaxBytes, len))
    }

}