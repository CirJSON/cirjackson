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
abstract class PropertyNameMatcher(protected val locale: Locale?, protected val backupMatcher: PropertyNameMatcher?,
        val nameLookup: Array<String>?) : Serializable {

    companion object {

        const val MATCH_UNKNOWN_NAME = -2

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