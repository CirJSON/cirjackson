package org.cirjson.cirjackson.core.io

import ch.randelshofer.fastdoubleparser.JavaBigDecimalParser
import java.math.BigDecimal

/**
 * Internal CirJackson Helper class used to implement more optimized parsing of [BigDecimal] for huge values (over 500
 * characters).
 *
 * This class is not meant to be used directly. It is designed to be used by CirJackson CirJSON parsers (and parsers for
 * other CirJackson supported data formats). The parsers check for invalid characters and the length of the number.
 * Without these checks, this parser is susceptible to performing badly with invalid inputs. If you need to parse
 * numbers directly, please use JavaBigDecimalParser in
 * [fastdoubleparser](https://github.com/wrandelshofer/FastDoubleParser) instead.
 *
 * Based on ideas from
 * [this git commit](https://github.com/eobermuhlner/big-math/commit/7a5419aac8b2adba2aa700ccf00197f97b2ad89f)
 */
internal object BigDecimalParser {

    /**
     * Internal CirJackson method. Please do not use.
     *
     * Note: Caller MUST pre-validate that given String represents a valid representation of [BigDecimal] value: parsers
     * in `cirjackson-core` do that; other code must do the same.
     *
     * @param string Value to parse
     *
     * @return BigDecimal value
     *
     * @throws NumberFormatException for decoding failures
     */
    fun parse(string: String): BigDecimal {
        return parse(string.toCharArray())
    }

    /**
     * Internal CirJackson method. Please do not use.
     *
     * Note: Caller MUST pre-validate that given String represents a valid representation of [BigDecimal] value: parsers
     * in `cirjackson-core` do that; other code must do the same.
     *
     * @param chars Buffer that contains value to parse
     *
     * @param offset Offset of the first character to decode
     *
     * @param length Length of value to parse in buffer
     *
     * @return BigDecimal value
     *
     * @throws NumberFormatException for decoding failures
     */
    fun parse(chars: CharArray, offset: Int, length: Int): BigDecimal {
        try {
            return if (length < 500) {
                BigDecimal(chars, offset, length)
            } else {
                JavaBigDecimalParser.parseBigDecimal(chars, offset, length)
            }
        } catch (e: NumberFormatException) {
            throw parseFailure(e, String(chars, offset, length))
        } catch (e: ArithmeticException) {
            throw parseFailure(e, String(chars, offset, length))
        }
    }

    /**
     * Internal CirJackson method. Please do not use.
     *
     * Note: Caller MUST pre-validate that given String represents a valid representation of [BigDecimal] value: parsers
     * in `cirjackson-core` do that; other code must do the same.
     *
     * @param chars Value to parse
     *
     * @return BigDecimal value
     *
     * @throws NumberFormatException for decoding failures
     */
    fun parse(chars: CharArray): BigDecimal {
        return parse(chars, 0, chars.size)
    }

    /**
     * Internal CirJackson method. Please do not use.
     *
     * Note: Caller MUST pre-validate that given String represents a valid representation of [BigDecimal] value: parsers
     * in `cirjackson-core` do that; other code must do the same.
     *
     * @param string Value to parse
     *
     * @return BigDecimal value
     *
     * @throws NumberFormatException for decoding failures
     */
    fun parseWithFastParser(string: String): BigDecimal {
        try {
            return JavaBigDecimalParser.parseBigDecimal(string)
        } catch (e: NumberFormatException) {
            throw parseFailure(e, string)
        } catch (e: ArithmeticException) {
            throw parseFailure(e, string)
        }
    }

    /**
     * Internal CirJackson method. Please do not use.
     *
     * Note: Caller MUST pre-validate that given String represents a valid representation of [BigDecimal] value: parsers
     * in `cirjackson-core` do that; other code must do the same.
     *
     * @param chars Buffer that contains value to parse
     *
     * @param offset Offset of the first character to decode
     *
     * @param length Length of value to parse in buffer
     *
     * @return BigDecimal value
     *
     * @throws NumberFormatException for decoding failures
     */
    fun parseWithFastParser(chars: CharArray, offset: Int, length: Int): BigDecimal {
        try {
            return JavaBigDecimalParser.parseBigDecimal(chars, offset, length)
        } catch (e: NumberFormatException) {
            throw parseFailure(e, String(chars, offset, length))
        } catch (e: ArithmeticException) {
            throw parseFailure(e, String(chars, offset, length))
        }
    }

    private fun parseFailure(e: Exception, value: String): NumberFormatException {
        val description = e.message ?: "Not a valid number representation"
        val toReport = getValueDesc(value)
        return NumberFormatException(
                "Value $toReport can not be deserialized as `java.math.BigDecimal`, reason: $description")
    }

    private fun getValueDesc(value: String): String {
        val length = value.length

        return if (length <= MAX_CHARS_TO_REPORT) {
            "\"$value\""
        } else {
            "\"${value.substring(0, MAX_CHARS_TO_REPORT)}\" (truncated to $MAX_CHARS_TO_REPORT chars (from $length))"
        }
    }

}