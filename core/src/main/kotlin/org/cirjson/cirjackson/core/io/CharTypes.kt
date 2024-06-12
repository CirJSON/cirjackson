package org.cirjson.cirjackson.core.io

object CharTypes {

    private val CHARS = "0123456789ABCDEF".toCharArray()

    private val CHARS_LOWER = "0123456789abcdef".toCharArray()

    private val BYTES = ByteArray(CHARS.size) { CHARS[it].code.toByte() }

    private val BYTES_LOWER = ByteArray(CHARS_LOWER.size) { CHARS_LOWER[it].code.toByte() }

    /**
     * Lookup table used for determining which input characters need special handling when contained in text segment.
     */
    private val INPUT_CODES = IntArray(256).apply {
        for (index in indices) {
            if (index < 32) {
                this[index] = -1
            }
        }

        this['"'.code] = 1
        this['\\'.code] = 1
    }

    /**
     * Additionally we can combine UTF-8 decoding info into similar data table.
     */
    private val INPUT_CODES_UTF8 = INPUT_CODES.copyOf().apply {
        for (i in 128..<256) {
            this[i] = if (i and 0xE0 == 0xC0) {
                2
            } else if (i and 0xF0 == 0xE0) {
                3
            } else if (i and 0xF8 == 0xF0) {
                4
            } else {
                -1
            }
        }
    }

    /**
     * To support non-default (and non-standard) unquoted Object Property names mode, need to have alternate checking.
     *
     * Basically this is list of 8-bit ASCII characters that are legal as part of Javascript identifier
     */
    private val INPUT_CODES_JS_NAMES = IntArray(256) { -1 }.apply {
        for (i in 33..<256) {
            if (i.toChar().isJavaIdentifierPart()) {
                this[i] = 0
            }
        }

        for (c in charArrayOf('@', '#', '*', '-', '+')) {
            this[c.code] = 0
        }
    }

    /**
     * This table is similar to Latin-1, except that it marks all "high-bit" code as ok. They will be validated at a later point, when decoding name
     */
    private val INPUT_CODES_UTF8_JS_NAMES = INPUT_CODES_JS_NAMES.copyOf().apply {
        fill(0, 128, 128)
    }

    /**
     * Decoding table used to quickly determine characters that are relevant within comment content.
     */
    private val INPUT_CODES_COMMENTS = IntArray(256).apply {
        INPUT_CODES_UTF8.copyInto(this, 128, 128)
        fill(-1, 0, 32)
        this['\t'.code] = 0
        this['\n'.code] = '\n'.code
        this['\r'.code] = '\r'.code
        this['*'.code] = '*'.code
    }

    /**
     * Decoding table used for skipping white space and comments.
     */
    private val INPUT_CODES_WHITE_SPACE = IntArray(256).apply {
        INPUT_CODES_UTF8.copyInto(this, 128, 128)
        fill(-1, 0, 32)
        this[' '.code] = 1
        this['\t'.code] = 1
        this['\n'.code] = '\n'.code
        this['\r'.code] = '\r'.code
        this['/'.code] = '/'.code
        this['#'.code] = '#'.code
    }

    /**
     * Lookup table used for determining which output characters in 7-bit ASCII range need to be quoted.
     */
    private val OUTPUT_ESCAPES_128_NO_SLASH = IntArray(128).apply {
        for (i in 0..<32) {
            this[i] = CharacterEscapes.ESCAPE_STANDARD
        }

        this['"'.code] = '"'.code
        this['\\'.code] = '\\'.code
        this[0x08] = 'b'.code
        this[0x09] = 't'.code
        this[0x0C] = 'f'.code
        this[0x0A] = 'n'.code
        this[0x0D] = 'r'.code
    }

    /**
     * Lookup table same as [OUTPUT_ESCAPES_128_NO_SLASH] except that forward slash ('/') is also escaped
     */
    private val OUTPUT_ESCAPES_128_WITH_SLASH = OUTPUT_ESCAPES_128_NO_SLASH.copyOf().apply {
        this['/'.code] = '/'.code
    }

    /**
     * Lookup table for the first 256 Unicode characters (ASCII / UTF-8) range. For actual hex digits, contains
     * corresponding value; for others -1.
     */
    private val HEX_VALUES = IntArray(256) { -1 }.apply {
        for (i in 0..<10) {
            this['0'.code + i] = i

            if (i < 6) {
                this['a'.code + i] = 10 + i
                this['A'.code + i] = 10 + i
            }
        }
    }

    private val ALT_ESCAPE_NO_SLASH = arrayOfNulls<IntArray>(128)

    private val ALT_ESCAPE_WITH_SLASH = arrayOfNulls<IntArray>(128)

    val inputCodeLatin1
        get() = INPUT_CODES.copyOf()

    val inputCodeUtf8
        get() = INPUT_CODES_UTF8.copyOf()

    val inputCodeLatin1JsNames
        get() = INPUT_CODES_JS_NAMES.copyOf()

    val inputCodeUtf8JsNames
        get() = INPUT_CODES_UTF8_JS_NAMES.copyOf()

    val inputCodeComment
        get() = INPUT_CODES_COMMENTS.copyOf()

    val inputCodeWhitespace
        get() = INPUT_CODES_WHITE_SPACE.copyOf()

    /**
     * Accessor for getting a read-only encoding table for first 128 Unicode code points (single-byte UTF-8 characters).
     *
     * Value of 0 means "no escaping"; other positive values that value is character to use after backslash; and
     * negative values that generic (backslash - u) escaping is to be used.
     *
     * NOTE: Forward slash (`"/"`) is escaped by default.
     *
     * @return 128-entry `IntArray` that contains escape definitions
     */
    val sevenBitOutputEscapes
        get() = getSevenBitOutputEscapes('"'.code, true)

    /**
     * Alternative to [sevenBitOutputEscapes] when either a non-standard quote character is used, or forward slash is to
     * be escaped.
     *
     * @param quoteChar Character used for quoting textual values and property names; usually double-quote but sometimes
     * changed to single-quote (apostrophe)
     *
     * @param escapeSlash Whether forward slash (`"/"`) is escaped by default or not.
     *
     * @return 128-entry `IntArray` that contains escape definitions
     */
    fun getSevenBitOutputEscapes(quoteChar: Int, escapeSlash: Boolean): IntArray {
        return if (quoteChar == '"'.code) {
            if (escapeSlash) {
                OUTPUT_ESCAPES_128_WITH_SLASH.copyOf()
            } else {
                OUTPUT_ESCAPES_128_NO_SLASH.copyOf()
            }
        } else {
            altEscapesFor(quoteChar, escapeSlash).copyOf()
        }
    }

    fun copyHexChars(uppercase: Boolean): CharArray {
        return if (uppercase) CHARS.copyOf() else CHARS_LOWER.copyOf()
    }

    fun copyHexBytes(uppercase: Boolean): ByteArray {
        return if (uppercase) BYTES.copyOf() else BYTES_LOWER.copyOf()
    }

    fun charToHex(char: Char): Int {
        return HEX_VALUES[char.code and 0xFF]
    }

    fun hexToChar(char: Int): Char {
        return CHARS[char]
    }

    /**
     * Helper method for appending CirJSON-escaped version of contents into specific [StringBuilder], using default
     * CirJSON specification mandated minimum escaping rules.
     *
     * @param stringBuilder Buffer to append escaped contents in
     *
     * @param content Unescaped String value to append with escaping applied
     */
    fun appendQuoted(stringBuilder: StringBuilder, content: String) {
        val escCodes = OUTPUT_ESCAPES_128_WITH_SLASH
        val escLength = escCodes.size

        for (c in content) {
            if (c.code >= escLength || escCodes[c.code] == 0) {
                stringBuilder.append(c)
                continue
            }

            stringBuilder.append('\\')
            val escCode = escCodes[c.code]

            if (escCode >= 0) {
                stringBuilder.append(escCode.toChar())
                continue
            }

            stringBuilder.append('u')
            stringBuilder.append('0')
            stringBuilder.append('0')
            stringBuilder.append(CHARS[c.code shr 4])
            stringBuilder.append(CHARS[c.code and 0xF])
        }
    }

    private fun altEscapesFor(quoteChar: Int, escapeSlash: Boolean): IntArray {
        var esc = if (escapeSlash) ALT_ESCAPE_WITH_SLASH[quoteChar] else ALT_ESCAPE_NO_SLASH[quoteChar]

        if (esc != null) {
            return esc
        }

        esc = if (escapeSlash) OUTPUT_ESCAPES_128_WITH_SLASH.copyOf() else OUTPUT_ESCAPES_128_NO_SLASH.copyOf()

        if (esc[quoteChar] == 0) {
            val quoteStyle = when (quoteChar) {
                '\''.code, '"'.code -> quoteChar
                else -> CharacterEscapes.ESCAPE_STANDARD
            }
            esc[quoteChar] = quoteStyle
        }

        if (escapeSlash) {
            ALT_ESCAPE_WITH_SLASH[quoteChar] = esc
        } else {
            ALT_ESCAPE_NO_SLASH[quoteChar] = esc
        }

        return esc
    }

}