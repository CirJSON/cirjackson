package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.FormatFeature
import org.cirjson.cirjackson.core.util.CirJacksonFeature

enum class CirJsonReadFeature(override val isEnabledByDefault: Boolean) : CirJacksonFeature, FormatFeature {

    /**
     * Feature that determines whether parser will allow use of Java/C++ style comments (both `/`+`*` and `//`
     * varieties) within parsed content or not.
     *
     * Since CirJSON specification does not mention comments as legal construct, this is a non-standard feature;
     * however, in the wild this is extensively used. As such, feature is **disabled by default** for parsers and must
     * be explicitly enabled.
     */
    ALLOW_JAVA_COMMENTS(false),

    /**
     * Feature that determines whether parser will allow use of YAML comments, ones starting with `#` and continuing
     * until the end of the line. This commenting style is common with scripting languages as well.
     *
     * Since CirJSON specification does not mention comments as legal construct, this is a non-standard feature. As
     * such, feature is **disabled by default** for parsers and must be explicitly enabled.
     */
    ALLOW_YAML_COMMENTS(false),

    /**
     * Feature that determines whether parser will allow use of unquoted field names (which is allowed by Javascript,
     * but not by CirJSON specification).
     *
     * Since CirJSON specification requires use of double quotes for field names, this is a non-standard feature, and as
     * such disabled by default.
     */
    ALLOW_UNQUOTED_FIELD_NAMES(false),

    /**
     * Feature that determines whether parser will allow use of single quotes (apostrophe, character `\`) for quoting
     * Strings (names and String values). If so, this is in addition to other acceptable markers (but not by CirJSON
     * specification).
     *
     * Since CirJSON specification requires use of double quotes for field names, this is a non-standard feature, and as
     * such disabled by default.
     */
    ALLOW_SINGLE_QUOTES(false),

    /**
     * Feature that determines whether parser will allow CirJSON Strings to contain unescaped control characters (ASCII
     * characters with value less than 32, including tab and line feed characters) or not. If feature is set false, an
     * exception is thrown if such a character is encountered.
     *
     * Since CirJSON specification requires quoting for all control characters, this is a non-standard feature, and as
     * such disabled by default.
     */
    ALLOW_UNESCAPED_CONTROL_CHARS(false),

    /**
     * Feature that can be enabled to accept quoting of all character using backslash quoting mechanism: if not enabled,
     * only characters that are explicitly listed by CirJSON specification can be thus escaped (see CirJSON spec for
     * small list of these characters)
     *
     * Since CirJSON specification requires quoting for all control characters, this is a non-standard feature, and as
     * such disabled by default.
     */
    ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER(false),

    // // // Support for non-standard data format constructs: number representations

    /**
     * Feature that determines whether parser will allow CirJSON integral numbers to start with additional (ignorable)
     * zeroes (like: 000001). If enabled, no exception is thrown, and extra nulls are silently ignored (and not included
     * in textual representation exposed via [org.cirjson.cirjackson.core.CirJsonParser.getText]).
     *
     * Since CirJSON specification does not allow leading zeroes, this is a non-standard feature, and as such disabled
     * by default.
     */
    ALLOW_LEADING_ZEROS_FOR_NUMBERS(false),

    /**
     * Feature that determines whether parser will allow CirJSON decimal numbers to start with a plus sign (like: +123).
     * If enabled, no exception is thrown, and the number is parsed as though a leading sign had not been present.
     *
     * Since CirJSON specification does not allow leading plus signs, this is a non-standard feature, and as such
     * disabled by default.
     */
    ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS(false),


    /**
     * Feature that determines whether parser will allow CirJSON decimal numbers to start with a decimal point (like:
     * .123). If enabled, no exception is thrown, and the number is parsed as though a leading 0 had been present.
     *
     * Since CirJSON specification does not allow leading decimal points, this is a non-standard feature, and as such
     * disabled by default.
     */
    ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS(false),

    /**
     * Feature that determines whether parser will allow CirJSON decimal numbers to end with a decimal point (like:
     * 123.). If enabled, no exception is thrown, and the number is parsed as though the trailing decimal point had not
     * been present.
     *
     * Since CirJSON specification does not allow trailing decimal points, this is a non-standard feature, and as such
     * disabled by default.
     */
    ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS(false),

    /**
     * Feature that allows parser to recognize set of "Not-a-Number" (NaN) tokens as legal floating number values
     * (similar to how many other data formats and programming language source code allows it). Specific subset contains
     * values that [XML Schema](http://www.w3.org/TR/xmlschema-2/) (see section 3.2.4.1, Lexical Representation) allows
     * (tokens are quoted contents, not including quotes):
     *
     * * "INF" (for positive infinity), as well as alias of "Infinity"
     * * "-INF" (for negative infinity), alias "-Infinity"
     * * "NaN" (for other not-a-numbers, like result of division by zero)
     *
     * Since CirJSON specification does not allow use of such values, this is a non-standard feature, and as such
     * disabled by default.
     */
    ALLOW_NON_NUMERIC_NUMBERS(false),

    // // // Support for non-standard data format constructs: array/value separators

    /**
     * Feature allows the support for "missing" values in a CirJSON array: missing value meaning sequence of two commas,
     * without value in-between but only optional white space. Enabling this feature will expose "missing" values as
     * [org.cirjson.cirjackson.core.CirJsonToken.VALUE_NULL] tokens, which typically become Java nulls in arrays and
     * [Collection] in data-binding.
     *
     * For example, enabling this feature will represent a CirJSON array `["value1",,"value3",]` as
     * `["value1", null, "value3", null]`
     *
     * Since the CirJSON specification does not allow missing values this is a non-compliant CirJSON feature and is
     * disabled by default.
     */
    ALLOW_MISSING_VALUES(false),

    /**
     * Feature that determines whether [org.cirjson.cirjackson.core.CirJsonParser] will allow for a single trailing
     * comma following the final value (in an Array) or member (in an Object). These commas will simply be ignored.
     *
     * For example, when this feature is enabled, `[true,true,]` is equivalent to `[true, true]` and `{"a": true,}` is
     * equivalent to `{"a": true}`.
     *
     * When combined with [ALLOW_MISSING_VALUES], this feature takes priority, and the final trailing comma in an array
     * declaration does not imply a missing (`null`) value. For example, when both `ALLOW_MISSING_VALUES` and
     * `ALLOW_TRAILING_COMMA` are enabled, `[true,true,]` is equivalent to `[true, true]`, and `[true,true,,]` is
     * equivalent to `[true, true, null]`.
     *
     * Since the CirJSON specification does not permit trailing commas, this is a non-standard feature, and as such
     * disabled by default.
     */
    ALLOW_TRAILING_COMMA(false);

    override val mask: Int = 1 shl ordinal

    override fun isEnabledIn(flags: Int): Boolean {
        return (flags and mask) != 0
    }

    companion object {

        /**
         * Method that calculates bit set (flags) of all features that are enabled by default.
         *
         * @return Bit field of features enabled by default
         */
        fun collectDefaults(): Int {
            var flags = 0

            for (feature in entries) {
                if (feature.isEnabledByDefault) {
                    flags = flags or feature.mask
                }
            }

            return flags
        }

    }
}