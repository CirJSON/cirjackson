package org.cirjson.cirjackson.core

enum class CirJsonEncoding(val javaName: String, val isBigEndian: Boolean, val bits: Int) {

    UTF8("UTF-8", false, 8),

    UTF16_BE("UTF-16BE", true, 16),

    UTF16_LE("UTF-16LE", false, 16),

    UTF32_BE("UTF-32BE", true, 32),

    UTF32_LE("UTF-32LE", false, 32);

}