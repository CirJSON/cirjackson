package org.cirjson.cirjackson.core.symbols

import java.util.*

/**
 * Intermediate base class for matchers that use hash-array based approach with Strings.
 *
 * @property mask Mask used to get index from raw hash code, within hash area.
 */
abstract class HashedMatcherBase(locale: Locale?, backupMatcher: PropertyNameMatcher?, nameLookup: Array<String>?,
        private val names: Array<String?>, private val offsets: IntArray, private val mask: Int) :
        PropertyNameMatcher(locale, backupMatcher, nameLookup) {

    constructor(base: HashedMatcherBase, fallback: HashedMatcherBase) : this(base.locale, fallback, base.nameLookup,
            base.names, base.offsets, base.mask)

    companion object {
    }

}