package org.cirjson.cirjackson.core.filter

/**
 * Strategy class that can be implemented to specify actual inclusion/exclusion criteria for filtering, used by
 * [FilteringGeneratorDelegate].
 */
open class TokenFilter {

    /**
     * Enumeration that controls how TokenFilter return values are interpreted.
     */
    enum class Inclusion {

        /**
         * Tokens will only be included if the filter returns TokenFilter.INCLUDE_ALL
         */
        ONLY_INCLUDE_ALL,

        /**
         * When TokenFilter.INCLUDE_ALL is returned, the corresponding token will be included as well as enclosing
         * tokens up to the root
         */
        INCLUDE_ALL_AND_PATH,

        /**
         * Tokens will be included if any non-null filter is returned. The exception is if a property name returns a
         * non-null filter, but the property value returns a null filter. In this case the property name and value will
         * both be omitted.
         */
        INCLUDE_NON_NULL

    }

}