package org.cirjson.cirjackson.core.io

import ch.randelshofer.fastdoubleparser.JavaDoubleParser
import ch.randelshofer.fastdoubleparser.JavaFloatParser
import org.cirjson.cirjackson.core.extensions.pow
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.min

/**
 * Helper class for efficient parsing of various CirJSON numbers.
 *
 * NOTE! Does NOT validate against maximum length limits: caller must do that if and as necessary.
 */
object NumberInput {

    /**
     * Constants needed for parsing longs from basic int parsing methods
     */
    private const val L_BILLION = 1000000000L

    private val MIN_LONG_STR_NO_SIGN = Long.MIN_VALUE.toString().substring(1)

    private val MAX_LONG_STR = Long.MAX_VALUE.toString()

    /**
     * Regex used to pre-validate "Stringified Numbers": slightly looser than CirJSON Number definition (allows leading
     * zeroes, positive sign).
     */
    private val PATTERN_FLOAT = Regex("[+-]?[0-9]*[.]?[0-9]+([eE][+-]?[0-9]+)?")

    /**
     * Parses a String to a Double
     *
     * @param str a string representing a number to parse
     *
     * @param useFastParser whether to use `FastDoubleParser` or standard JDK parser
     *
     * @return closest matching Double
     *
     * @throws NumberFormatException if string cannot be represented by a Double
     */
    @Throws(NumberFormatException::class)
    fun parseDouble(str: String, useFastParser: Boolean): Double {
        return if (useFastParser) JavaDoubleParser.parseDouble(str) else str.toDouble()
    }

    /**
     * Parses a CharArray to a Double
     *
     * @param charArray a char array containing a number to parse
     *
     * @param useFastParser whether to use `FastDoubleParser`
     *
     * @return closest matching Double
     *
     * @throws NumberFormatException if value cannot be represented by a Double
     */
    @Throws(NumberFormatException::class)
    fun parseDouble(charArray: CharArray, useFastParser: Boolean): Double {
        return parseDouble(charArray, 0, charArray.size, useFastParser)
    }

    /**
     * Parses a CharArray to a Double
     *
     * @param charArray a char array containing a number to parse
     *
     * @param offset the offset to apply when parsing the number in the char array
     *
     * @param length the length of the number in the char array
     *
     * @param useFastParser whether to use `FastDoubleParser`
     *
     * @return closest matching Double
     *
     * @throws NumberFormatException if value cannot be represented by a Double
     */
    @Throws(NumberFormatException::class)
    fun parseDouble(charArray: CharArray, offset: Int, length: Int, useFastParser: Boolean): Double {
        return if (useFastParser) {
            JavaDoubleParser.parseDouble(charArray, offset, length)
        } else {
            String(charArray, offset, length).toDouble()
        }
    }

    /**
     * Parses a String to a Double, or returns the default value
     *
     * @param string a string representing a number to parse
     *
     * @param default the default to return if `s` is not a parseable number
     *
     * @param useFastParser whether to use `FastDoubleParser` or standard JDK parser
     *
     * @return closest matching double (or `default` if there is an issue with `string`) where useFastParser=false
     */
    fun parseAsDouble(string: String?, default: Double, useFastParser: Boolean): Double {
        val s = string?.trim()

        if (s.isNullOrEmpty()) {
            return default
        }

        return try {
            parseDouble(s, useFastParser)
        } catch (_: NumberFormatException) {
            default
        }
    }

    /**
     * Parses a String to a Float
     *
     * @param str a string representing a number to parse
     *
     * @param useFastParser whether to use `FastFloatParser` or standard JDK parser
     *
     * @return closest matching Float
     *
     * @throws NumberFormatException if string cannot be represented by a Float
     */
    @Throws(NumberFormatException::class)
    fun parseFloat(str: String, useFastParser: Boolean): Float {
        return if (useFastParser) JavaFloatParser.parseFloat(str) else str.toFloat()
    }

    /**
     * Parses a CharArray to a Float
     *
     * @param charArray a char array containing a number to parse
     *
     * @param useFastParser whether to use `FastFloatParser`
     *
     * @return closest matching Float
     *
     * @throws NumberFormatException if value cannot be represented by a Float
     */
    @Throws(NumberFormatException::class)
    fun parseFloat(charArray: CharArray, useFastParser: Boolean): Float {
        return parseFloat(charArray, 0, charArray.size, useFastParser)
    }

    /**
     * Parses a CharArray to a Float
     *
     * @param charArray a char array containing a number to parse
     *
     * @param offset the offset to apply when parsing the number in the char array
     *
     * @param length the length of the number in the char array
     *
     * @param useFastParser whether to use `FastFloatParser`
     *
     * @return closest matching Float
     *
     * @throws NumberFormatException if value cannot be represented by a Float
     */
    @Throws(NumberFormatException::class)
    fun parseFloat(charArray: CharArray, offset: Int, length: Int, useFastParser: Boolean): Float {
        return if (useFastParser) {
            JavaFloatParser.parseFloat(charArray, offset, length)
        } else {
            String(charArray, offset, length).toFloat()
        }
    }

    /**
     * Fast method for parsing unsigned integers that are known to fit into regular 32-bit signed int type. This means
     * that length is between 1 and 9 digits (inclusive) and there is no sign character.
     *
     * Note: public to let unit tests call it; not meant to be used by any code outside this package.
     *
     * @param chars Buffer that contains integer value to decode
     *
     * @param offset Offset of the first digit character in buffer
     *
     * @param length Length of the number to decode (in characters)
     *
     * @return Decoded `Int` value
     */
    fun parseInt(chars: CharArray, offset: Int, length: Int): Int {
        var off = offset
        var len = length

        if (len > 0 && chars[off] == '+') {
            off++
            len--
        }

        var num = chars[off + len - 1].code - '0'.code

        for (i in min(len, 9) downTo 2) {
            num += (chars[off++].code - '0'.code) * (10 pow (i - 1))
        }

        return num
    }

    /**
     * Helper method to (more) efficiently parse integer numbers from String values. Input String must be simple Java
     * Int value. No range checks are made to verify that the value fits in 32-bit Java `Int`: caller is expected to
     * only calls this in cases where this can be guaranteed (basically: number of digits does not exceed 9)
     *
     * NOTE: semantics differ significantly from the other `parseInt`.
     *
     * @param string String that contains integer value to decode
     *
     * @return Decoded `int` value
     */
    fun parseInt(string: String): Int {
        var c = string[0]
        val length = string.length
        val neg = c == '-'
        var offset = 1

        if (neg) {
            if (length == 1 || length > 10) {
                return string.toInt()
            }

            c = string[offset++]
        } else if (length > 9) {
            return string.toInt()
        }

        if (c !in '0'..'9') {
            return string.toInt()
        }

        var num = c - '0'

        while (offset < length) {
            c = string[offset++]

            if (c !in '0'..'9') {
                return string.toInt()
            }

            num = num * 10 + (c - '0')
        }

        return if (neg) -num else num
    }

    fun parseAsInt(string: String?, default: Int): Int {
        var s = string?.trim()

        if (s.isNullOrEmpty()) {
            return default
        }

        var length = s.length
        var i = 0
        val sign = s[0]

        if (sign == '+') {
            s = s.substring(1)
            length = s.length
        } else if (sign == '-') {
            i = 1
        }

        while (i < length) {
            val c = s[i]

            if (c !in '0'..'9') {
                return try {
                    parseDouble(s, true).toInt()
                } catch (_: NumberFormatException) {
                    default
                }
            }

            ++i
        }

        return try {
            s.toInt()
        } catch (_: NumberFormatException) {
            default
        }
    }

    /**
     * Fast method for parsing unsigned integers that are known to fit into regular 62-bit signed int type. This means
     * that length is between 10 and 18 digits (inclusive) and there is no sign character.
     *
     * @param chars Buffer that contains integer value to decode
     *
     * @param offset Offset of the first digit character in buffer
     *
     * @param length Length of the number to decode (in characters)
     *
     * @return Decoded `int` value
     */
    internal fun parseLong(chars: CharArray, offset: Int, length: Int): Long {
        val length2 = length - 9
        val result = parseInt(chars, offset, length).toLong() * L_BILLION
        return result + parseInt(chars, offset + length2, 9).toLong()
    }

    /**
     * Parses an unsigned long made up of exactly 19 digits.
     *
     * It is the callers responsibility to make sure the input is exactly 19 digits. and fits into a 64bit long by
     * calling [inLongRange] first.
     *
     * Note that input String must NOT contain leading minus sign (even if `negative` is set to true).
     *
     * @param chars Buffer that contains integer value to decode
     *
     * @param offset Offset of the first digit character in buffer
     *
     * @param negative Whether original number had a minus sign
     *
     * @return Decoded `Long` value
     */
    fun parseLong19(chars: CharArray, offset: Int, negative: Boolean): Long {
        var num = 0L

        for (i in 0..<19) {
            val c = chars[offset + i]
            num = num * 10 + (c - '0')
        }

        return if (negative) -num else num
    }

    fun parseLong(string: String): Long {
        val length = string.length

        return if (length <= 9) {
            parseInt(string).toLong()
        } else {
            string.toLong()
        }
    }

    fun parseAsLong(string: String?, default: Long): Long {
        var s = string?.trim()

        if (s.isNullOrEmpty()) {
            return default
        }

        var length = s.length
        var i = 0
        val sign = s[0]

        if (sign == '+') {
            s = s.substring(1)
            length = s.length
        } else if (sign == '-') {
            i = 1
        }

        while (i < length) {
            val c = s[i]

            if (c !in '0'..'9') {
                return try {
                    parseDouble(s, true).toLong()
                } catch (_: NumberFormatException) {
                    default
                }
            }

            ++i
        }

        return try {
            s.toLong()
        } catch (_: NumberFormatException) {
            default
        }
    }

    /**
     * Helper method for determining if given String representation of an integral number would fit in 64-bit Long or
     * not. Note that input String must NOT contain leading minus sign (even if 'negative' is set to true).
     *
     * @param chars Buffer that contains long value to check
     *
     * @param offset Offset of the first digit character in buffer
     *
     * @param length Length of the number to decode (in characters)
     *
     * @param negative Whether original number had a minus sign (which is NOT passed to this method) or not
     *
     * @return `true` if specified String representation is within `Long` range; `false` if not.
     */
    fun inLongRange(chars: CharArray, offset: Int, length: Int, negative: Boolean): Boolean {
        val comparedString = if (negative) MIN_LONG_STR_NO_SIGN else MAX_LONG_STR
        val comparedLength = comparedString.length

        if (length < comparedLength) {
            return true
        } else if (length > comparedLength) {
            return false
        }

        for (i in 0..<comparedLength) {
            val diff = chars[offset + i] - comparedString[i]

            if (diff != 0) {
                return diff < 0
            }
        }

        return true
    }

    /**
     * Similar to [inLongRange], but with String argument
     *
     * @param string String that contains `Long` value to check
     *
     * @param negative Whether original number had a minus sign (which is NOT passed to this method) or not
     *
     * @return `True` if specified String representation is within `Long` range; `false` if not.
     */
    fun inLongRange(string: String, negative: Boolean): Boolean {
        val length = string.length
        val comparedString = if (negative) MIN_LONG_STR_NO_SIGN else MAX_LONG_STR
        val comparedLength = comparedString.length

        if (length < comparedLength) {
            return true
        } else if (length > comparedLength) {
            return false
        }

        val diff = string.compareTo(comparedString)

        return if (diff != 0) diff < 0 else true
    }

    /**
     * Parses a string to a BigDecimal
     *
     * @param string a string representing a number to parse
     *
     * @param useFastParser whether to use `FastDoubleParser` or standard parser
     *
     * @return a BigDecimal
     *
     * @throws NumberFormatException if the string cannot be represented by a BigDecimal
     */
    @Throws(NumberFormatException::class)
    fun parseBigDecimal(string: String, useFastParser: Boolean): BigDecimal {
        return if (useFastParser) {
            BigDecimalParser.parseWithFastParser(string)
        } else {
            BigDecimalParser.parse(string)
        }
    }

    /**
     * Parses a char array to a BigDecimal
     *
     * @param chars a char array with text that makes up a number
     *
     * @param offset the offset to apply when parsing the number in the char array
     *
     * @param length the length of the number in the char array
     *
     * @param useFastParser whether to use `FastDoubleParser` or standard parser
     *
     * @return a BigDecimal
     *
     * @throws NumberFormatException if the char array cannot be represented by a BigDecimal
     */
    @Throws(NumberFormatException::class)
    fun parseBigDecimal(chars: CharArray, offset: Int, length: Int, useFastParser: Boolean): BigDecimal {
        return if (useFastParser) {
            BigDecimalParser.parseWithFastParser(chars, offset, length)
        } else {
            BigDecimalParser.parse(chars, offset, length)
        }
    }

    /**
     * Parses a char array to a BigDecimal
     *
     * @param chars a char array with text that makes up a number
     *
     * @param useFastParser whether to use `FastDoubleParser` or standard parser
     *
     * @return a BigDecimal
     *
     * @throws NumberFormatException if the char array cannot be represented by a BigDecimal
     */
    @Throws(NumberFormatException::class)
    fun parseBigDecimal(chars: CharArray, useFastParser: Boolean): BigDecimal {
        return parseBigDecimal(chars, 0, chars.size, useFastParser)
    }

    /**
     * Parses a string to a BigInteger
     *
     * @param string a string representing a number to parse
     *
     * @param useFastParser whether to use `FastIntParser` or standard parser
     *
     * @return a BigInteger
     *
     * @throws NumberFormatException if string cannot be represented by a BigInteger
     */
    @Throws(NumberFormatException::class)
    fun parseBigInteger(string: String, useFastParser: Boolean): BigInteger {
        return if (useFastParser) {
            BigIntegerParser.parseWithFastParser(string)
        } else {
            BigInteger(string)
        }
    }

    /**
     * Parses a string to a BigInteger according to the specified radix
     *
     * @param string a string representing a number to parse
     *
     * @param radix for parse
     *
     * @param useFastParser whether to use `FastIntParser` or standard parser
     *
     * @return a BigInteger
     *
     * @throws NumberFormatException if string cannot be represented by a BigInteger
     */
    @Throws(NumberFormatException::class)
    fun parseBigInteger(string: String, radix: Int, useFastParser: Boolean): BigInteger {
        return if (useFastParser) {
            BigIntegerParser.parseWithFastParser(string, radix)
        } else {
            BigInteger(string, radix)
        }
    }

}