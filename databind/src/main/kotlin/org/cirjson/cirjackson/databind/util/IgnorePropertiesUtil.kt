package org.cirjson.cirjackson.databind.util

object IgnorePropertiesUtil {

    /**
     * Decide if a property needs to be ignored or not, given a set of field to ignore and a set of field to include.
     */
    fun shouldIgnore(value: Any, toIgnore: Collection<String>?, toInclude: Collection<String>?): Boolean {
        if (toIgnore == null && toInclude == null) {
            return false
        }

        toInclude ?: return value in toIgnore!!

        toIgnore ?: return value in toInclude

        return value !in toInclude || value in toIgnore
    }

    /**
     * Factory method for creating and return a [Checker] instance if (and only if) one is needed.
     *
     * @param toIgnore Set of property names to ignore (can be `null`)
     *
     * @param toInclude Set of only property names to include (if `null`, undefined)
     *
     * @return Checker, if validity checks are needed; `null` otherwise
     */
    fun buildCheckerIfNeeded(toIgnore: Set<String>?, toInclude: Set<String>?): Checker? {
        if (toInclude == null && toIgnore.isNullOrEmpty()) {
            return null
        }

        return Checker.construct(toIgnore, toInclude)
    }

    /**
     * Helper that encapsulates logic for combining two sets of "included names": default logic is to do intersection
     * (name must be in both to be included in the result)
     *
     * @param prevToInclude Existing set of names to include, if defined; null means "not defined"
     *
     * @param newToInclude New set of names to be included, if defined; null means "not defined"
     *
     * @return Resulting set of names, using intersection if neither `null`; or the non-`null` one (if only one is
     * `null`); or `null` if both arguments `null`.
     */
    fun combineNamesToInclude(previousToInclude: Set<String>?, newToInclude: Set<String>?): Set<String>? {
        previousToInclude ?: return newToInclude
        newToInclude ?: return previousToInclude

        val result = hashSetOf<String>()

        for (property in newToInclude) {
            if (property in previousToInclude) {
                result.add(property)
            }
        }

        return result
    }

    /**
     * Helper class to encapsulate logic from [IgnorePropertiesUtil.shouldIgnore]
     */
    class Checker private constructor(toIgnore: Set<String>?, private val myToInclude: Set<String>?) {

        private val myToIgnore = toIgnore ?: emptySet()

        fun shouldIgnore(propertyName: Any): Boolean {
            return (myToInclude?.contains(propertyName) ?: false) || propertyName in myToIgnore
        }

        companion object {

            fun construct(toIgnore: Set<String>?, toInclude: Set<String>?): Checker {
                return Checker(toIgnore, toInclude)
            }

        }

    }

}