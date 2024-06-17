package org.cirjson.cirjackson.core.util

/**
 * Value class used with some {@link org.cirjson.cirjackson.core.PrettyPrinter} implements
 *
 * @see org.cirjson.cirjackson.core.util.DefaultPrettyPrinter
 * @see org.cirjson.cirjackson.core.util.MinimalPrettyPrinter
 */
data class Separators(val rootSeparator: String, val objectNameValueSeparator: Char,
        val objectNameValueSpacing: Spacing, val objectEntrySeparator: Char, val objectEntrySpacing: Spacing,
        val objectEmptySeparator: String, val arrayElementSeparator: Char, val arrayElementSpacing: Spacing,
        val arrayEmptySeparator: String) {

    constructor() : this(':', ',', ',')

    /**
     * Constructor for creating an instance with default settings for all separators.
     *
     * @param objectNameValueSeparator Separator between Object property name and value
     *
     * @param objectEntrySeparator Separator between name-value entries in Object
     *
     * @param arrayElementSeparator Separator between Array elements
     */
    constructor(objectNameValueSeparator: Char, objectEntrySeparator: Char, arrayElementSeparator: Char) : this(
            DEFAULT_ROOT_VALUE_SEPARATOR, objectNameValueSeparator, Spacing.BOTH, objectEntrySeparator, Spacing.NONE,
            DEFAULT_OBJECT_EMPTY_SEPARATOR, arrayElementSeparator, Spacing.NONE, DEFAULT_ARRAY_EMPTY_SEPARATOR)

    /**
     * Define the spacing around elements like commas and colons.
     */
    enum class Spacing(val spacesBefore: String, val spacesAfter: String) {

        NONE("", ""),

        BEFORE(" ", ""),

        AFTER("", " "),

        BOTH(" ", " ");

        fun apply(separator: Char): String {
            return "$spacesBefore$separator$spacesAfter"
        }

    }

    companion object {

        /**
         * Constant that specifies default "root-level" separator to use between root values: a single space character.
         */
        const val DEFAULT_ROOT_VALUE_SEPARATOR = " "

        /**
         * String to use in empty Object to separate start and end markers. Default is single space, resulting in output
         * of `{ }`.
         */
        const val DEFAULT_OBJECT_EMPTY_SEPARATOR = " "

        /**
         * String to use in empty Array to separate start and end markers. Default is single space, resulting in output
         * of `[ ]`.
         */
        const val DEFAULT_ARRAY_EMPTY_SEPARATOR = " "

        fun createDefaultInstance(): Separators {
            return Separators()
        }

    }

}