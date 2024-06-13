package org.cirjson.cirjackson.core.io

import ch.randelshofer.fastdoubleparser.JavaBigIntegerParser
import java.math.BigInteger

/**
 * Helper class used to implement more optimized parsing of [BigInteger] for huge values (over 500 characters).
 */
internal object BigIntegerParser {

    fun parseWithFastParser(string: String): BigInteger {
        try {
            return JavaBigIntegerParser.parseBigInteger(string)
        } catch (e: NumberFormatException) {
            throw NumberFormatException(message(e, string))
        }
    }

    fun parseWithFastParser(string: String, radix: Int): BigInteger {
        try {
            return JavaBigIntegerParser.parseBigInteger(string, radix)
        } catch (e: NumberFormatException) {
            throw NumberFormatException(message(e, string, radix))
        }
    }

    private fun message(e: NumberFormatException, string: String, radix: Int? = null): String {
        val report = if (string.length >= MAX_CHARS_TO_REPORT) {
            "${string.substring(0, MAX_CHARS_TO_REPORT)} [truncated]"
        } else {
            string
        }

        val start = "Value \"$report\" can not be represented as `java.math.BigInteger`"

        return if (radix != null) {
            "$start with radix $radix, reason: ${e.message}"
        } else {
            "$start, reason: ${e.message}"
        }
    }

}