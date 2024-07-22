package org.cirjson.cirjackson.core.extensions

import java.io.Writer

fun Writer.write(b: ByteArray, off: Int, len: Int) {
    write(b.toCharArray(), off, len)
}
