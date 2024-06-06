package org.cirjson.cirjackson.core.io

object CharTypes {

    private const val CHARS = "0123456789ABCDEF"

    private const val CHARS_LOWER = "0123456789abcdef"

    fun hexToChar(char: Int): Char {
        return CHARS[char]
    }

}