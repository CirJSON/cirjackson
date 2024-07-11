package org.cirjson.cirjackson.core.cirjson.async

import org.cirjson.cirjackson.core.ObjectReadContext
import org.cirjson.cirjackson.core.cirjson.CirJsonParserBase
import org.cirjson.cirjackson.core.io.IOContext
import org.cirjson.cirjackson.core.symbols.ByteQuadsCanonicalizer

/**
 * Intermediate base class for non-blocking CirJSON parsers.
 *
 * @property mySymbols Symbol table that contains property names encountered so far.
 */
abstract class NonBlockingCirJsonParserBase(objectReadContext: ObjectReadContext, ioContext: IOContext,
        streamReadFeatures: Int, formatReadFeatures: Int, protected val mySymbols: ByteQuadsCanonicalizer) :
        CirJsonParserBase(objectReadContext, ioContext, streamReadFeatures, formatReadFeatures) {

    protected var myQuadBuffer = IntArray(8)

    companion object {

        /*
         ***************************************************************************************************************
         * Major state constants
         ***************************************************************************************************************
         */

        /**
         * State right after parser has been constructed, before seeing the first byte to handle possible (but optional)
         * BOM.
         */
        const val MAJOR_INITIAL: Int = 0

        /**
         * State right after parser a root value has been finished, but next token has not yet been recognized.
         */
        const val MAJOR_ROOT: Int = 1

        const val MAJOR_OBJECT_PROPERTY_FIRST: Int = 2

        const val MAJOR_OBJECT_PROPERTY_NEXT: Int = 3

        const val MAJOR_OBJECT_VALUE: Int = 4

        const val MAJOR_ARRAY_ELEMENT_FIRST: Int = 5

        const val MAJOR_ARRAY_ELEMENT_NEXT: Int = 6

        /**
         * State after non-blocking input source has indicated that no more input is forthcoming AND we have exhausted
         * all the input
         */
        const val MAJOR_CLOSED: Int = 7

        /*
         ***************************************************************************************************************
         * Minor state constants
         ***************************************************************************************************************
         */

        /**
         * State in which part of (UTF-8) BOM has been detected, but not yet completely.
         */
        const val MINOR_ROOT_BOM: Int = 1

        /**
         * State between root-level value, waiting for at least one white-space character as separator
         */
        const val MINOR_ROOT_NEED_SEPARATOR: Int = 2

        /**
         * State between root-level value, having processed at least one white-space character, and expecting either
         * more, start of a value, or end of input stream.
         */
        const val MINOR_ROOT_GOT_SEPARATOR: Int = 3

        /**
         * State before property name itself, waiting for quote (or unquoted name)
         */
        const val MINOR_PROPERTY_LEADING_WS: Int = 4

        /**
         * State before property name, expecting comma (or closing curly), then property name
         */
        const val MINOR_PROPERTY_LEADING_COMMA: Int = 5

        /**
         * State within regular (double-quoted) property name
         */
        const val MINOR_PROPERTY_NAME: Int = 7

        /**
         * State within regular (double-quoted) property name, within escape (having encountered either just backslash,
         * or backslash and 'u' and 0 - 3 hex digits,
         */
        const val MINOR_PROPERTY_NAME_ESCAPE: Int = 8

        const val MINOR_PROPERTY_APOS_NAME: Int = 9

        const val MINOR_PROPERTY_UNQUOTED_NAME: Int = 10

        const val MINOR_VALUE_LEADING_WS: Int = 12

        const val MINOR_VALUE_EXPECTING_COMMA: Int = 13

        const val MINOR_VALUE_EXPECTING_COLON: Int = 14

        const val MINOR_VALUE_WS_AFTER_COMMA: Int = 15

        const val MINOR_VALUE_TOKEN_NULL: Int = 16

        const val MINOR_VALUE_TOKEN_TRUE: Int = 17

        const val MINOR_VALUE_TOKEN_FALSE: Int = 18

        const val MINOR_VALUE_TOKEN_NON_STD: Int = 19

        const val MINOR_NUMBER_PLUS: Int = 22

        const val MINOR_NUMBER_MINUS: Int = 23

        /**
         * Zero as first, possibly trimming multiple
         */
        const val MINOR_NUMBER_ZERO: Int = 24

        /**
         * "-0" (and possibly more zeroes) receive
         */
        const val MINOR_NUMBER_MINUS_ZERO: Int = 25

        const val MINOR_NUMBER_INTEGER_DIGITS: Int = 26

        const val MINOR_NUMBER_FRACTION_DIGITS: Int = 30

        const val MINOR_NUMBER_EXPONENT_MARKER: Int = 31

        const val MINOR_NUMBER_EXPONENT_DIGITS: Int = 32

        const val MINOR_VALUE_STRING: Int = 40

        const val MINOR_VALUE_STRING_ESCAPE: Int = 41

        const val MINOR_VALUE_STRING_UTF8_2: Int = 42

        const val MINOR_VALUE_STRING_UTF8_3: Int = 43

        const val MINOR_VALUE_STRING_UTF8_4: Int = 44

        const val MINOR_VALUE_APOS_STRING: Int = 45

        /**
         * Special state at which point decoding of a non-quoted token has encountered a problem; that is, either not
         * matching fully (like "truf" instead of "true", at "tru"), or not having trailing separator (or end of input),
         * like "trueful". Attempt is made, then, to decode likely full input token to report suitable error.
         */
        const val MINOR_VALUE_TOKEN_ERROR: Int = 50

        const val MINOR_COMMENT_LEADING_SLASH: Int = 51

        const val MINOR_COMMENT_CLOSING_ASTERISK: Int = 52

        const val MINOR_COMMENT_C: Int = 53

        const val MINOR_COMMENT_CPP: Int = 54

        const val MINOR_COMMENT_YAML: Int = 55

    }

}