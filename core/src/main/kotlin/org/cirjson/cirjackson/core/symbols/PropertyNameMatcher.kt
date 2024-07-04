package org.cirjson.cirjackson.core.symbols

import org.cirjson.cirjackson.core.util.InternCache
import org.cirjson.cirjackson.core.util.Named
import java.io.Serializable
import java.util.*

/**
 * Interface for implementations used for efficient matching of Object property names from input stream (via parser) to
 * higher-level abstractions like properties that databind uses.
 *
 * Used to avoid two-phase lookups -- first from input stream to strings; then from strings to entities -- but details
 * may heavily depend on format parser (some formats can optimize better than others).
 *
 * @property nameLookup Array of names that this matcher may match (with indices that match non-negative values by
 * `matchXxx` methods)
 */
abstract class PropertyNameMatcher(protected val myLocale: Locale?, protected val myBackupMatcher: PropertyNameMatcher?,
        val nameLookup: Array<String>?) : Serializable {

    /*
     *******************************************************************************************************************
     * Public API: lookup by String
     *******************************************************************************************************************
     */

    /**
     * Lookup method that does not assume name to be matched to be [String.intern]ed (although passing interned String
     * is likely to result in more efficient matching).
     *
     * @param toMatch Name to match
     *
     * @return Index of the name matched, if any (non-negative number); or an error code (negative constant `MATCH_xxx`)
     * if none
     */
    abstract fun matchName(toMatch: String): Int

    /*
     *******************************************************************************************************************
     * Public API: lookup by quad-bytes
     *******************************************************************************************************************
     */

    abstract fun matchByQuad(q1: Int): Int

    abstract fun matchByQuad(q1: Int, q2: Int): Int

    abstract fun matchByQuad(q1: Int, q2: Int, q3: Int): Int

    abstract fun matchByQuad(quads: IntArray, quadLength: Int): Int

    /*
     *******************************************************************************************************************
     * Configuration access
     *******************************************************************************************************************
     */

    /**
     * Secondary lookup method used for matchers that operate with more complex matching rules, such as case-insensitive
     * matchers.
     *
     * @param toMatch Name to match
     *
     * @return Index for the match, if any (non-negative); or error code if no match
     */
    protected fun matchSecondary(toMatch: String): Int {
        return myBackupMatcher?.matchName(toMatch.lowercase(myLocale!!)) ?: MATCH_UNKNOWN_NAME
    }

    companion object {

        /**
         * Marker for case where `CirJsonToken.END_OBJECT` encountered.
         */
        const val MATCH_END_OBJECT = -1

        /**
         * Marker for case where property name encountered but not one of matches.
         */
        const val MATCH_UNKNOWN_NAME = -2

        /**
         * Marker for case where token encountered is neither `PROPERTY_NAME` nor `END_OBJECT`.
         */
        const val MATCH_ODD_TOKEN = -3

        private val INTERNER = InternCache.INSTANCE

        fun stringsFromNames(propertyNames: List<Named?>, alreadyInterned: Boolean): List<String?> {
            val result = ArrayList<String?>()

            for (name in propertyNames) {
                result.add(fromName(name, alreadyInterned))
            }

            return result
        }

        private fun fromName(named: Named?, alreadyInterned: Boolean): String? {
            named ?: return null
            val name = named.name
            return if (alreadyInterned) name else INTERNER.intern(name)
        }

        fun findSize(size: Int): Int {
            return when {
                size <= 5 -> 8

                size <= 11 -> 16

                size <= 23 -> 32

                else -> {
                    val needed = size + (size shr 2) + (size shr 4)
                    var result = 64

                    while (result < needed) {
                        result += result
                    }

                    result
                }
            }
        }

        fun hash(h: Int, mask: Int): Int {
            return (h + (h shr 3)) and mask
        }

        fun lowercase(locale: Locale, names: List<String?>): List<String?> {
            val result = ArrayList<String?>(names.size)

            for (name in names) {
                result.add(name?.lowercase(locale))
            }

            return result
        }

    }

}