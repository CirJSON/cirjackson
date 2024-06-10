package org.cirjson.cirjackson.core.io

import ch.randelshofer.fastdoubleparser.JavaDoubleParser
import ch.randelshofer.fastdoubleparser.JavaFloatParser
import org.cirjson.cirjackson.core.extentions.pow
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
     * Parses a String to a Double
     *
     * @param str a string representing a number to parse
     *
     * @param useFastParser whether to use {@code FastDoubleParser} or standard JDK parser
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
     * @param array a char array containing a number to parse
     *
     * @param useFastParser whether to use {@code FastDoubleParser}
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
     * @param array a char array containing a number to parse
     *
     * @param offset the offset to apply when parsing the number in the char array
     *
     * @param len the length of the number in the char array
     *
     * @param useFastParser whether to use {@code FastDoubleParser}
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
     * Parses a String to a Float
     *
     * @param str a string representing a number to parse
     *
     * @param useFastParser whether to use {@code FastFloatParser} or standard JDK parser
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
     * @param array a char array containing a number to parse
     *
     * @param useFastParser whether to use {@code FastFloatParser}
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
     * @param array a char array containing a number to parse
     *
     * @param offset the offset to apply when parsing the number in the char array
     *
     * @param len the length of the number in the char array
     *
     * @param useFastParser whether to use {@code FastFloatParser}
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
     * Fast method for parsing unsigned integers that are known to fit into
     * regular 32-bit signed int type. This means that length is
     * between 1 and 9 digits (inclusive) and there is no sign character.
     *
     * Note: public to let unit tests call it; not meant to be used by any
     * code outside this package.
     *
     * @param chars Buffer that contains integer value to decode
     *
     * @param offset Offset of the first digit character in buffer
     *
     * @param length Length of the number to decode (in characters)
     *
     * @return Decoded {@code int} value
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
     * Fast method for parsing unsigned integers that are known to fit into
     * regular 62-bit signed int type. This means that length is
     * between 10 and 18 digits (inclusive) and there is no sign character.
     *
     * Note: public to let unit tests call it; not meant to be used by any
     * code outside this package.
     *
     * @param chars Buffer that contains integer value to decode
     *
     * @param offset Offset of the first digit character in buffer
     *
     * @param length Length of the number to decode (in characters)
     *
     * @return Decoded {@code int} value
     */
    fun parseLong(chars: CharArray, offset: Int, length: Int): Long {
        val length2 = length - 9
        val result = parseInt(chars, offset, length).toLong() * L_BILLION
        return result + parseInt(chars, offset + length2, 9).toLong()
    }

}