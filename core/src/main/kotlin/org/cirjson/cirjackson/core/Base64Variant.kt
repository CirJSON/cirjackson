package org.cirjson.cirjackson.core

import org.cirjson.cirjackson.core.util.ByteArrayBuilder
import org.cirjson.cirjackson.core.util.Named

/**
 * Class used to define specific details of which variant of Base64 encoding/decoding is to be used. Although there is
 * somewhat standard basic version (so-called "MIME Base64"), other variants exists, see
 * [Base64 Wikipedia entry](https://en.wikipedia.org/wiki/Base64) for details.
 */
class Base64Variant : Named {

    /**
     * Decoding table used for base 64 decoding.
     */
    private val myAsciiToBase64 = IntArray(128)

    /**
     * Encoding table used for base 64 decoding when output is done as characters.
     */
    private val myBase64ToAsciiChar = CharArray(64)

    /**
     * Alternative encoding table used for base 64 decoding when output is done as ascii bytes.
     */
    private val myBase64ToAsciiByte = ByteArray(64)

    /**
     * Symbolic name of variant; used for diagnostics/debugging.
     *
     * Note that this is the only non-transient field; used when reading back from serialized state.
     */
    override val name: String

    /**
     * Character used for padding, if any ([PADDING_CHAR_NONE] if not).
     */
    val paddingChar: Char

    /**
     * Code of character used for padding, if any (code of [PADDING_CHAR_NONE] if not).
     */
    val paddingByte: Byte
        get() = paddingChar.code.toByte()

    /**
     * Maximum number of encoded base64 characters to output during encoding before adding a linefeed, if line length is
     * to be limited ([Int.MAX_VALUE] if not limited).
     *
     * Note: for some output modes (when writing attributes) linefeeds may need to be avoided, and this value ignored.
     */
    val maxLineLength: Int

    /**
     * Whether this variant uses padding when writing out content or not.
     */
    val isUsingPadding: Boolean

    /**
     * Indicator on how this Base64 encoding will handle possible padding in content when reading.
     */
    val paddingReadBehaviour: PaddingReadBehaviour

    constructor(name: String, base64Alphabet: String, writePadding: Boolean, paddingChar: Char, maxLineLength: Int) {
        this.name = name
        isUsingPadding = writePadding
        this.paddingChar = paddingChar
        this.maxLineLength = maxLineLength

        val alphabetLength = base64Alphabet.length

        if (alphabetLength != 64) {
            throw IllegalArgumentException("Base64Alphabet length must be exactly 64 (was $alphabetLength)")
        }

        base64Alphabet.toCharArray(myBase64ToAsciiChar)
        myAsciiToBase64.fill(BASE64_VALUE_INVALID)

        for (i in 0..<alphabetLength) {
            val alpha = myBase64ToAsciiChar[i]
            myBase64ToAsciiByte[i] = alpha.code.toByte()
            myAsciiToBase64[alpha.code] = i
        }

        if (writePadding) {
            myAsciiToBase64[paddingChar.code] = BASE64_VALUE_PADDING
        }

        paddingReadBehaviour = if (writePadding) {
            PaddingReadBehaviour.PADDING_REQUIRED
        } else {
            PaddingReadBehaviour.PADDING_FORBIDDEN
        }
    }

    /**
     * "Copy constructor" that can be used when the base alphabet is identical to one used by another variant except for
     * the maximum line length (and obviously, name).
     *
     * @param base Variant to use for settings not specific by other parameters
     *
     * @param name Name of this variant
     *
     * @param maxLineLength Maximum length (in characters) of lines to output before using linefeed
     */
    constructor(base: Base64Variant, name: String, maxLineLength: Int) : this(base, name, base.isUsingPadding,
            base.paddingChar, maxLineLength)

    /**
     * "Copy constructor" that can be used when the base alphabet is identical to one used by another variant, but other
     * details (padding, maximum line length) differ
     *
     * @param base Variant to use for settings not specific by other parameters
     *
     * @param name Name of this variant
     *
     * @param writePadding Whether variant will use padding when encoding
     *
     * @param paddingChar Padding character used for encoding, excepted on reading, if any
     *
     * @param maxLineLength Maximum length (in characters) of lines to output before using linefeed
     */
    constructor(base: Base64Variant, name: String, writePadding: Boolean, paddingChar: Char, maxLineLength: Int) : this(
            base, name, writePadding, paddingChar, base.paddingReadBehaviour, maxLineLength)

    private constructor(base: Base64Variant, name: String, writePadding: Boolean, paddingChar: Char,
            paddingReadBehaviour: PaddingReadBehaviour, maxLineLength: Int) {
        this.name = name
        base.myBase64ToAsciiByte.copyInto(myBase64ToAsciiByte)
        base.myBase64ToAsciiChar.copyInto(myBase64ToAsciiChar)
        base.myAsciiToBase64.copyInto(myAsciiToBase64)

        isUsingPadding = writePadding
        this.paddingChar = paddingChar
        this.maxLineLength = maxLineLength
        this.paddingReadBehaviour = paddingReadBehaviour
    }

    private constructor(base: Base64Variant, paddingReadBehaviour: PaddingReadBehaviour) : this(base, base.name,
            base.isUsingPadding, base.paddingChar, paddingReadBehaviour, base.maxLineLength)

    /**
     * Base64Variant which does not require padding on read
     *
     * @return Base64Variant which does not require padding on read
     */
    fun withPaddingAllowed(): Base64Variant {
        return withReadBehaviour(PaddingReadBehaviour.PADDING_ALLOWED)
    }

    /**
     * Base64Variant which does not accept padding on read
     *
     * @return Base64Variant which requires padding on read
     */
    fun withPaddingRequired(): Base64Variant {
        return withReadBehaviour(PaddingReadBehaviour.PADDING_REQUIRED)
    }

    /**
     * Base64Variant which does not accept padding on read
     *
     * @return Base64Variant which does not accept padding on read
     */
    fun withPaddingForbidden(): Base64Variant {
        return withReadBehaviour(PaddingReadBehaviour.PADDING_FORBIDDEN)
    }

    /**
     * Gives the variant based on the [readBehaviour]
     *
     * @param readBehaviour Padding read behavior desired
     *
     * @return Instance with desired padding read behavior setting (this if already has setting; new instance otherwise)
     */
    fun withReadBehaviour(readBehaviour: PaddingReadBehaviour): Base64Variant {
        return if (readBehaviour != paddingReadBehaviour) Base64Variant(this, readBehaviour) else this
    }

    /**
     * If this variant requires padding on content decoded
     */
    val isRequiringPaddingOnRead
        get() = paddingReadBehaviour == PaddingReadBehaviour.PADDING_REQUIRED

    /**
     * If this variant accepts padding on content decoded
     */
    val isAcceptingPaddingOnRead
        get() = paddingReadBehaviour != PaddingReadBehaviour.PADDING_FORBIDDEN

    /**
     * Method that verifies if this variant uses [char] as the padding Char.
     *
     * @param char The char to verify
     */
    fun usesPaddingChar(char: Char): Boolean {
        return paddingChar == char
    }

    /**
     * Method that verifies if the code of this variant's padding char is [code]
     *
     * @param code The code to verify
     */
    fun usesPaddingChar(code: Int): Boolean {
        return paddingChar.code == code
    }

    /*
     *******************************************************************************************************************
     * Decoding support
     *******************************************************************************************************************
     */

    /**
     * Method that decodes a character
     *
     * @param char Character to decode
     *
     * @return 6-bit decoded value, if valid character;
     */
    fun decodeBase64Char(char: Char): Int {
        return decodeBase64Char(char.code)
    }

    /**
     * Method that decodes a character from its code
     *
     * @param code Character's code to decode
     *
     * @return 6-bit decoded value, if valid character;
     */
    fun decodeBase64Char(code: Int): Int {
        return if (code <= 127) myAsciiToBase64[code] else BASE64_VALUE_INVALID
    }

    /**
     * Method that decodes a character from its code
     *
     * @param byte Character's code to decode
     *
     * @return 6-bit decoded value, if valid character;
     */
    fun decodeBase64Byte(byte: Byte): Int {
        return decodeBase64Char(byte.toInt())
    }

    /*
     *******************************************************************************************************************
     * Encoding support
     *******************************************************************************************************************
     */

    /**
     * Method that encodes given right-aligned (LSB) 24-bit value into 4 base64 characters, stored in given result
     * buffer. Caller must ensure there is sufficient space for 4 encoded characters at specified position.
     *
     * @param value 3-byte value to encode
     *
     * @param buffer Output buffer to append characters to
     *
     * @param outputPointer Starting position within `buffer` to append encoded characters
     *
     * @return Pointer in output buffer after appending 4 encoded characters
     */
    fun encodeBase64Chunk(value: Int, buffer: CharArray, outputPointer: Int): Int {
        var pointer = outputPointer
        buffer[pointer++] = myBase64ToAsciiChar[(value shr 18) and 0x3F]
        buffer[pointer++] = myBase64ToAsciiChar[(value shr 12) and 0x3F]
        buffer[pointer++] = myBase64ToAsciiChar[(value shr 6) and 0x3F]
        buffer[pointer++] = myBase64ToAsciiChar[value and 0x3F]
        return pointer
    }

    /**
     * Method that encodes given right-aligned (LSB) 24-bit value into a [StringBuilder].
     *
     * @param stringBuilder The [StringBuilder] that will receive the encoded value
     *
     * @param value 3-byte value to encode
     */
    fun encodeBase64Chunk(stringBuilder: StringBuilder, value: Int) {
        stringBuilder.append(myBase64ToAsciiChar[(value shr 18) and 0x3F])
        stringBuilder.append(myBase64ToAsciiChar[(value shr 12) and 0x3F])
        stringBuilder.append(myBase64ToAsciiChar[(value shr 6) and 0x3F])
        stringBuilder.append(myBase64ToAsciiChar[value and 0x3F])
    }

    /**
     * Method that outputs partial chunk (which only encodes one or two bytes of data). Data given is still aligned same
     * as if it as full data; that is, missing data is at the "right end" (LSB) of int.
     *
     * @param bits 24-bit chunk containing 1 or 2 bytes to encode
     *
     * @param outputBytes Number of input bytes to encode (either 1 or 2)
     *
     * @param buffer Output buffer to append characters to
     *
     * @param outputPointer Starting position within `buffer` to append encoded characters
     *
     * @return Pointer in output buffer after appending encoded characters (2, 3 or 4)
     */
    fun encodeBase64Partial(bits: Int, outputBytes: Int, buffer: CharArray, outputPointer: Int): Int {
        var pointer = outputPointer
        buffer[pointer++] = myBase64ToAsciiChar[(bits shr 18) and 0x3F]
        buffer[pointer++] = myBase64ToAsciiChar[(bits shr 12) and 0x3F]

        if (isUsingPadding) {
            buffer[pointer++] = if (outputBytes == 2) myBase64ToAsciiChar[(bits shr 6) and 0x3F] else paddingChar
            buffer[pointer++] = paddingChar
        } else if (outputBytes == 2) {
            buffer[pointer++] = myBase64ToAsciiChar[(bits shr 6) and 0x3F]
        }

        return pointer
    }

    /**
     * Method that outputs partial chunk (which only encodes one or two bytes of data). Data given is still aligned same
     * as if it as full data; that is, missing data is at the "right end" (LSB) of int.
     *
     * @param stringBuilder The [StringBuilder] that will receive the encoded value
     *
     * @param bits 24-bit chunk containing 1 or 2 bytes to encode
     *
     * @param outputBytes Number of input bytes to encode (either 1 or 2)
     */
    fun encodeBase64Partial(stringBuilder: StringBuilder, bits: Int, outputBytes: Int) {
        stringBuilder.append(myBase64ToAsciiChar[(bits shr 18) and 0x3F])
        stringBuilder.append(myBase64ToAsciiChar[(bits shr 12) and 0x3F])

        if (isUsingPadding) {
            val toAppend = if (outputBytes == 2) myBase64ToAsciiChar[(bits shr 6) and 0x3F] else paddingChar
            stringBuilder.append(toAppend)
            stringBuilder.append(paddingByte)
        } else if (outputBytes == 2) {
            stringBuilder.append(myBase64ToAsciiChar[(bits shr 6) and 0x3F])
        }
    }

    fun encodeBase64BitsAsByte(value: Int): Byte {
        return myBase64ToAsciiByte[value]
    }

    /**
     * Method that encodes given right-aligned (LSB) 24-bit value into 4 base64 bytes (ascii), stored in given result
     * buffer.
     *
     * @param value 3-byte value to encode
     *
     * @param buffer Output buffer to append characters (as bytes) to
     *
     * @param outputPointer Starting position within `buffer` to append encoded characters
     *
     * @return Pointer in output buffer after appending 4 encoded characters
     */
    fun encodeBase64Chunk(value: Int, buffer: ByteArray, outputPointer: Int): Int {
        var pointer = outputPointer
        buffer[pointer++] = myBase64ToAsciiByte[(value shr 18) and 0x3F]
        buffer[pointer++] = myBase64ToAsciiByte[(value shr 12) and 0x3F]
        buffer[pointer++] = myBase64ToAsciiByte[(value shr 6) and 0x3F]
        buffer[pointer++] = myBase64ToAsciiByte[value and 0x3F]
        return pointer
    }

    /**
     * Method that outputs partial chunk (which only encodes one or two bytes of data). Data given is still aligned same
     * as if it as full data; that is, missing data is at the "right end" (LSB) of int.
     *
     * @param bits 24-bit chunk containing 1 or 2 bytes to encode
     *
     * @param outputBytes Number of input bytes to encode (either 1 or 2)
     *
     * @param buffer Output buffer to append characters to
     *
     * @param outputPointer Starting position within `buffer` to append encoded characters
     *
     * @return Pointer in output buffer after appending encoded characters (2, 3 or 4)
     */
    fun encodeBase64Partial(bits: Int, outputBytes: Int, buffer: ByteArray, outputPointer: Int): Int {
        var pointer = outputPointer
        buffer[pointer++] = myBase64ToAsciiByte[(bits shr 18) and 0x3F]
        buffer[pointer++] = myBase64ToAsciiByte[(bits shr 12) and 0x3F]

        if (isUsingPadding) {
            buffer[pointer++] = if (outputBytes == 2) myBase64ToAsciiByte[(bits shr 6) and 0x3F] else paddingByte
            buffer[pointer++] = paddingByte
        } else if (outputBytes == 2) {
            buffer[pointer++] = myBase64ToAsciiByte[(bits shr 6) and 0x3F]
        }

        return pointer
    }

    /*
     *******************************************************************************************************************
     * Convenience conversion methods for String to/from bytes use case
     *******************************************************************************************************************
     */

    /**
     * Convenience method for converting given byte array as base64 encoded String using this variant's settings.
     * Resulting value is "raw", that is, not enclosed in double-quotes.
     *
     * @param input Byte array to encode
     *
     * @return Base64 encoded String of encoded `input` bytes, not surrounded by quotes
     */
    fun encode(input: ByteArray): String {
        return encode(input, false)
    }

    /**
     * Convenience method for converting given byte array as base64 encoded String using this variant's settings,
     * optionally enclosed in double-quotes. Linefeeds added, if needed, are expressed as 2-character CirJSON (and Java
     * source) escape sequence of backslash + `n`.
     *
     * @param input Byte array to encode
     *
     * @param addQuotes Whether to surround resulting value in double quotes or not
     *
     * @return Base64 encoded String of encoded `input` bytes, possibly surrounded by quotes (if `addQuotes` enabled)
     */
    fun encode(input: ByteArray, addQuotes: Boolean): String {
        return encode(input, addQuotes, "\\n")
    }

    /**
     * Convenience method for converting given byte array as base64 encoded String using this variant's settings,
     * optionally enclosed in double-quotes. Linefeed character to use is passed explicitly.
     *
     * @param input Byte array to encode
     *
     * @param addQuotes Whether to surround resulting value in double quotes or not
     *
     * @param linefeed Linefeed to use for encoded content
     *
     * @return Base64 encoded String of encoded `input` bytes
     */
    fun encode(input: ByteArray, addQuotes: Boolean, linefeed: String): String {
        val inputEnd = input.size
        val stringBuilder = StringBuilder(inputEnd + (inputEnd shr 2) + (inputEnd shr 3))

        if (addQuotes) {
            stringBuilder.append('"')
        }

        var chunksBeforeLF = maxLineLength shr 2
        var inputPointer = 0
        val safeInputEnd = inputEnd - 3

        while (inputPointer <= safeInputEnd) {
            var value = input[inputPointer++].toInt() shl 8
            value = value or (input[inputPointer++].toInt() and 0xFF)
            value = (value shl 8) or (input[inputPointer++].toInt() and 0xFF)
            encodeBase64Chunk(stringBuilder, value)

            if (--chunksBeforeLF <= 0) {
                stringBuilder.append(linefeed)
                chunksBeforeLF = maxLineLength shr 2
            }
        }

        val inputLeft = inputEnd - inputPointer

        if (inputLeft > 0) {
            var value = input[inputPointer++].toInt() shl 16

            if (inputLeft == 2) {
                value = value or (input[inputPointer].toInt() and 0xFF shl 8)
            }

            encodeBase64Partial(stringBuilder, value, inputLeft)
        }

        if (addQuotes) {
            stringBuilder.append('"')
        }

        return stringBuilder.toString()
    }

    /**
     * Convenience method for decoding contents of a Base64-encoded String, using this variant's settings.
     *
     * @param input Base64-encoded input String to decode
     *
     * @return Byte array of decoded contents
     *
     * @throws IllegalArgumentException if input is not valid base64 encoded data
     */
    @Throws(IllegalArgumentException::class)
    fun decode(input: String): ByteArray {
        ByteArrayBuilder().use { b ->
            decode(input, b)
            return b.toByteArray()
        }
    }

    /**
     * Convenience method for decoding contents of a Base64-encoded String, using this variant's settings and appending
     * decoded binary data using provided [ByteArrayBuilder].
     *
     * NOTE: builder will NOT be reset before decoding (nor cleared afterward); assumption is that caller will ensure it
     * is given in proper state, and used as appropriate afterward.
     *
     * @param string Input to decode
     *
     * @param builder Builder used for assembling decoded content
     *
     * @throws IllegalArgumentException if input is not valid base64 encoded data
     */
    @Throws(IllegalArgumentException::class)
    fun decode(string: String, builder: ByteArrayBuilder) {
        var pointer = 0
        val length = string.length

        mainLoop@ while (true) {
            var char: Char

            do {
                if (pointer >= length) {
                    break@mainLoop
                }

                char = string[pointer++]
            } while (char.code <= INT_SPACE)

            var bits = decodeBase64Char(char)

            if (bits < 0) {
                reportInvalidBase64(char, 0, null)
            } else if (pointer >= length) {
                reportBase64EOF()
            }

            char = string[pointer++]
            var decodedData = bits
            bits = decodeBase64Char(char)

            if (bits < 0) {
                reportInvalidBase64(char, 1, null)
            }

            decodedData = decodedData shl 6 or bits

            if (pointer >= length) {
                if (!isRequiringPaddingOnRead) {
                    decodedData = decodedData shr 4
                    builder.append(decodedData)
                    break
                }

                reportBase64EOF()
            }

            char = string[pointer++]
            bits = decodeBase64Char(char)

            if (bits < 0) {
                if (bits != BASE64_VALUE_PADDING) {
                    reportInvalidBase64(char, 2, null)
                } else if (!isAcceptingPaddingOnRead) {
                    reportBase64UnexpectedPadding()
                } else if (pointer >= length) {
                    reportBase64EOF()
                }

                char = string[pointer++]

                if (!usesPaddingChar(char)) {
                    reportInvalidBase64(char, 3, "expected padding character '$paddingChar'")
                }

                decodedData = decodedData shr 4
                builder.append(decodedData)
                continue
            }

            if (pointer >= length) {
                if (!isRequiringPaddingOnRead) {
                    decodedData = decodedData shr 2
                    builder.appendTwoBytes(decodedData)
                    break
                }

                reportBase64EOF()
            }

            char = string[pointer++]
            bits = decodeBase64Char(char)

            if (bits < 0) {
                if (bits != BASE64_VALUE_PADDING) {
                    reportInvalidBase64(char, 3, null)
                } else if (!isAcceptingPaddingOnRead) {
                    reportBase64UnexpectedPadding()
                }

                decodedData = decodedData shr 2
                builder.appendTwoBytes(decodedData)
            } else {
                decodedData = decodedData shl 6 or bits
                builder.appendThreeBytes(decodedData)
            }
        }
    }

    @Throws(IllegalArgumentException::class)
    internal fun reportInvalidBase64(char: Char, index: Int, message: String?) {
        var base = when {
            char.code <= INT_SPACE -> {
                "Illegal white space character (code 0x${
                    char.code.toString(16)
                }) as character #${index + 1} of 4-char base64 unit: can only used between units"
            }

            usesPaddingChar(char) -> {
                "Unexpected padding character ('$paddingChar') as character #${index + 1} of 4-char base64 unit: padding only legal as 3rd or 4th character"
            }

            !char.isDefined() || char.isISOControl() -> {
                "Illegal character (code 0x${char.code.toString(16)}) in base64 content"
            }

            else -> {
                "Illegal character '$char' (code 0x${char.code.toString(16)}) in base64 content"
            }
        }

        if (message != null) {
            base = "$base: $message"
        }

        throw IllegalArgumentException(base)
    }

    @Throws(IllegalArgumentException::class)
    private fun reportBase64EOF() {
        throw IllegalArgumentException(missingPaddingMessage)
    }

    /**
     * Helper accessor that will construct a message to use in exceptions for cases where input ends prematurely in
     * place where padding would be expected.
     */
    internal val missingPaddingMessage: String
        get() {
            return "Unexpected end of base64-encoded String: base64 variant '$name' expects padding (one or more " +
                    "'$paddingChar' characters) at the end. This Base64Variant might have been incorrectly configured"
        }

    @Throws(IllegalArgumentException::class)
    private fun reportBase64UnexpectedPadding() {
        throw IllegalArgumentException(unexpectedPaddingMessage)
    }

    /**
     * Helper accessor that will construct a message to use in exceptions for cases where input ends prematurely in
     * place where padding is not expected.
     */
    private val unexpectedPaddingMessage: String
        get() {
            return "Unexpected end of base64-encoded String: base64 variant '$name' expects no padding at the end " +
                    "while decoding. This Base64Variant might have been incorrectly configured"
        }

    /*
     *******************************************************************************************************************
     * Overridden standard methods
     *******************************************************************************************************************
     */

    override fun toString(): String {
        return name
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (javaClass != (other as? Base64Variant)?.javaClass) {
            return false
        }

        return paddingChar == other.paddingChar && maxLineLength == other.maxLineLength &&
                isUsingPadding == other.isUsingPadding && paddingReadBehaviour == other.paddingReadBehaviour &&
                name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    /**
     * Defines how the Base64Variant deals with Padding while reading
     */
    enum class PaddingReadBehaviour {

        /**
         * Padding is not allowed in Base64 content being read (finding something that looks like padding at the end of
         * content results in an exception)
         */
        PADDING_FORBIDDEN,

        /**
         * Padding is required in Base64 content being read (missing padding for incomplete ending quartet results in an
         * exception)
         */
        PADDING_REQUIRED,

        /**
         * Padding is allowed but not required in Base64 content being read: no exception thrown based on existence or
         * absence, as long as proper padding characters are used.
         */
        PADDING_ALLOWED

    }

    companion object {

        private const val INT_SPACE = 0x20

        /**
         * Placeholder used by "no padding" variant, to be used when a character value is needed.
         */
        internal const val PADDING_CHAR_NONE = '\u0000'

        /**
         * Marker used to denote ascii characters that do not correspond to a 6-bit value (in this variant), and is not
         * used as a padding character.
         */
        const val BASE64_VALUE_INVALID = -1

        /**
         * Marker used to denote ascii character (in decoding table) that is the padding character using this variant
         * (if any).
         */
        const val BASE64_VALUE_PADDING = -2

    }

}