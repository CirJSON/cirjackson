package org.cirjson.cirjackson.core.io

object CharTypes {

    private val CHARS = "0123456789ABCDEF".toCharArray()

    private val CHARS_LOWER = "0123456789abcdef".toCharArray()

    fun hexToChar(char: Int): Char {
        return CHARS[char]
    }

}