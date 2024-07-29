package org.cirjson.cirjackson.core.extensions

@Suppress("DEPRECATION")
fun String(ascii: ByteArray, hiByte: Int, offset: Int, count: Int): String {
    return java.lang.String(ascii, hiByte, offset, count).toString()
}
