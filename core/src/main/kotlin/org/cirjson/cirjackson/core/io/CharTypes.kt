package org.cirjson.cirjackson.core.io

object CharTypes {

    private val CHARS = "0123456789ABCDEF".toCharArray()

    private val CHARS_LOWER = "0123456789abcdef".toCharArray()

    private val BYTES = ByteArray(CHARS.size) { CHARS[it].code.toByte() }

    private val BYTES_LOWER = ByteArray(CHARS_LOWER.size) { CHARS_LOWER[it].code.toByte() }

    fun copyHexChars(uppercase: Boolean): CharArray {
        return if (uppercase) CHARS.copyOf() else CHARS_LOWER.copyOf()
    }

    fun copyHexBytes(uppercase: Boolean): ByteArray {
        return if (uppercase) BYTES.copyOf() else BYTES_LOWER.copyOf()
    }

    fun hexToChar(char: Int): Char {
        return CHARS[char]
    }

}