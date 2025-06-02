package org.cirjson.cirjackson.core.util

import org.cirjson.cirjackson.core.SerializableString
import org.cirjson.cirjackson.core.io.CharacterEscapes
import org.cirjson.cirjackson.core.io.SerializedString

/**
 * Convenience [CharacterEscapes] implementation that escapes Unicode characters `0x2028` and `0x2029` (in addition to
 * characters escaped otherwise), which are apparently considered linefeeds as per newer JavaScript specifications, and
 * consequently problematic when using CirJSONP.
 */
object CirJsonpCharacterEscapes : CharacterEscapes() {

    private val myAsciiEscapes = standardAsciiEscapesForCirJSON

    private val myEscapeFor2028 = SerializedString("\\u2028")

    private val myEscapeFor2029 = SerializedString("\\u2029")

    override fun getEscapeSequence(ch: Int): SerializableString? {
        return when (ch) {
            0x2028 -> myEscapeFor2028
            0x2029 -> myEscapeFor2029
            else -> null
        }
    }

    override val escapeCodesForAscii: IntArray
        get() = myAsciiEscapes

}