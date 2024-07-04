package org.cirjson.cirjackson.core.symbols

import java.util.*

/**
 * Intermediate base class for matchers that use hash-array based approach with Strings.
 *
 * @property myMask Mask used to get index from raw hash code, within hash area.
 */
abstract class HashedMatcherBase(locale: Locale?, backupMatcher: PropertyNameMatcher?, nameLookup: Array<String>?,
        private val myNames: Array<String?>, private val myOffsets: IntArray, private val myMask: Int) :
        PropertyNameMatcher(locale, backupMatcher, nameLookup) {

    constructor(base: HashedMatcherBase, fallback: HashedMatcherBase) : this(base.myLocale, fallback, base.nameLookup,
            base.myNames, base.myOffsets, base.myMask)

    constructor(base: HashedMatcherBase, nameLookup: Array<String>?) : this(base.myLocale, base.myBackupMatcher,
            nameLookup, base.myNames, base.myOffsets, base.myMask)

    final override fun matchName(toMatch: String): Int {
        val index = hash(toMatch.hashCode(), myMask)

        if (myNames[index] === toMatch) {
            return myOffsets[index]
        }

        val index2 = myMask + 1 + (index shr 1)

        if (myNames[index2] === toMatch) {
            return myOffsets[index2]
        }

        return matchName2(toMatch, index, index2)
    }

    private fun matchName2(toMatch: String, index: Int, index2: Int): Int {
        var name = myNames[index]

        if (toMatch == name) {
            return myOffsets[index]
        }

        if (name != null) {
            name = myNames[index2]

            if (toMatch == name) {
                return myOffsets[index2]
            }

            if (name != null) {
                return matchSpill(toMatch)
            }
        }

        return matchSecondary(toMatch)
    }

    private fun matchSpill(toMatch: String): Int {
        var index = myMask + 1
        index += index shr 1

        val end = myNames.size

        while (index < end) {
            val name = myNames[index]

            if (toMatch == name) {
                return myOffsets[index]
            }

            if (name == null) {
                break
            }

            index++
        }

        return matchSecondary(toMatch)
    }

    /*
     *******************************************************************************************************************
     * For tests
     *******************************************************************************************************************
     */

    internal fun spillCount(): Int {
        val spillStart = myMask + 1 + ((myMask + 1) shr 1)
        var count = 0

        for (i in spillStart..<myNames.size) {
            if (myNames[i] != null) {
                ++count
            }
        }

        return count
    }

    internal fun secondaryCount(): Int {
        val spillStart = myMask + 1 + ((myMask + 1) shr 1)
        var count = 0

        for (i in myMask + 1..<spillStart) {
            if (myNames[i] != null) {
                ++count
            }
        }

        return count
    }

}