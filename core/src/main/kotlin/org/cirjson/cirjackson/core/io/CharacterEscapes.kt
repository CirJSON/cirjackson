package org.cirjson.cirjackson.core.io

import org.cirjson.cirjackson.core.SerializableString

/**
 * Abstract base class that defines interface for customizing character escaping aspects for String values, for formats
 * that use escaping. For CirJSON, this applies to both property names and String values.
 */
abstract class CharacterEscapes {

    /**
     * Accessor generators can call to get lookup table for determining escape handling for first 128 characters of
     * Unicode (ASCII characters. Caller is not to modify contents of this array, since this is expected to be a shared
     * copy.
     *
     * It returns an array with size of at least 128, where first 128 entries have either one of `ESCAPE_xxx` constants,
     * or non-zero positive integer (meaning of which is data format specific; for CirJSON it means that combination of
     * backslash and character with that value is to be used) to indicate that specific escape sequence is to be used.
     */
    abstract val escapeCodesForAscii: IntArray

    /**
     * Method generators can call to get lookup table for determining exact escape sequence to use for given character.
     * It can be called for any character, but typically is called for either for ASCII characters for which custom
     * escape sequence is needed; or for any non-ASCII character.
     *
     * @param ch Character to look escape sequence for
     *
     * @return Escape sequence to use for the character, if any; `null` if not
     */
    abstract fun getEscapeSequence(ch: Int): SerializableString?

    companion object {

        /**
         * Value used for lookup tables to indicate that matching characters do not need to be escaped.
         */
        const val ESCAPE_NONE = 0

        /**
         * Value used for lookup tables to indicate that matching characters are to be escaped using standard escaping;
         * for CirJSON, this means (for example) using "backslash - u" escape method.
         */
        const val ESCAPE_STANDARD = -1

        /**
         * Value used for lookup tables to indicate that matching characters will need custom escapes; and that another
         * call to [getEscapeSequence] is needed to figure out exact escape sequence to output.
         */
        const val ESCAPE_CUSTOM = -2

        /**
         * Helper accessor that can be used to get a copy of standardCir CirJSON escape definitions; this is useful when
         * just wanting to slightly customize definitions. Caller can modify this array as it sees fit and usually
         * returns modified instance via [escapeCodesForAscii]
         *
         * @return Set of escapes, similar to [escapeCodesForAscii] (array of 128 `Int`s), but a copy that caller
         * owns and is free to modify
         */
        val standardAsciiEscapesForCirJSON
            get() = CharTypes.sevenBitOutputEscapes

    }

}