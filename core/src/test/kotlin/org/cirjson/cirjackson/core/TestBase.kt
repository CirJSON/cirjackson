package org.cirjson.cirjackson.core

import java.nio.charset.StandardCharsets
import kotlin.test.fail

open class TestBase {

    /*
     *******************************************************************************************************************
     * Assertions
     *******************************************************************************************************************
     */

    /**
     * @param e Exception to check
     *
     * @param anyMatches Array of Strings of which AT LEAST ONE ("any") has to be included in `e.message` -- using
     * case-INSENSITIVE comparison
     */
    protected fun verifyException(e: Throwable, vararg anyMatches: String) {
        val message = e.message
        val lowercaseMessage = message?.lowercase() ?: ""

        for (match in anyMatches) {
            val lowercaseMatch = match.lowercase()

            if (lowercaseMatch in lowercaseMessage) {
                return
            }
        }

        fail("Expected an exception with one of substrings (${
            anyMatches.joinToString(", ")
        })): got one with message \"$message\"")
    }

    /*
     *******************************************************************************************************************
     * Misc other
     *******************************************************************************************************************
     */

    protected fun utf8Bytes(str: String): ByteArray {
        return str.toByteArray(StandardCharsets.UTF_8)
    }

    protected fun calcQuads(word: String): IntArray {
        return calcQuads(utf8Bytes(word))
    }

    protected fun calcQuads(wordBytes: ByteArray): IntArray {
        val length = wordBytes.size
        val result = IntArray((length + 3) / 4)
        var i = 0

        while (i < length) {
            var x = wordBytes[i].toInt() and 0xFF

            if (++i < length) {
                x = x shl 8 or (wordBytes[i].toInt() and 0xFF)

                if (++i < length) {
                    x = x shl 8 or (wordBytes[i].toInt() and 0xFF)

                    if (++i < length) {
                        x = x shl 8 or (wordBytes[i].toInt() and 0xFF)
                    }
                }
            }

            result[i shr 2] = x

            i++
        }

        return result
    }

    companion object {
    }

}