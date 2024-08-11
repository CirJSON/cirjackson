package org.cirjson.cirjackson.core.filter

import org.cirjson.cirjackson.core.CirJsonPointer

/**
 * Simple [TokenFilter] implementation that takes a single [CirJsonPointer] and matches a single value accordingly.
 * Instances are immutable and fully thread-safe, shareable, and efficient to use.
 *
 * @param myPathToMatch Content to extract
 *
 * @param myIncludeAllElements if `true` array indexes in `pointerExpression` are ignored and all elements will be
 * matched. default: `false`
 *
 * @property myPathToMatch Content to extract
 *
 * @property myIncludeAllElements If `true` include all array elements by ignoring the array index match and advancing
 * the CirJsonPointer to the next level
 */
open class CirJsonPointerBasedFilter(protected val myPathToMatch: CirJsonPointer,
        protected val myIncludeAllElements: Boolean) : TokenFilter() {

    constructor(pointerExpression: String) : this(CirJsonPointer.compile(pointerExpression), false)

    /**
     * @param pathToMatch Content to extract
     */
    constructor(pathToMatch: CirJsonPointer) : this(pathToMatch, false)

    /**
     * Overridable factory method used for creating new instances by default [includeElement] and [includeProperty]
     * methods: needs to be overridden if sub-classing this class.
     *
     * @param pathToMatch Remaining path for filter to match
     *
     * @param includeAllElements Whether to just include all array elements of matching Array-valued path automatically
     *
     * @return Filter constructed
     */
    protected open fun construct(pathToMatch: CirJsonPointer, includeAllElements: Boolean): CirJsonPointerBasedFilter {
        return CirJsonPointerBasedFilter(pathToMatch, includeAllElements)
    }

    override fun includeProperty(name: String): TokenFilter? {
        val next = myPathToMatch.matchProperty(name) ?: return null

        return if (next.isMatching) {
            INCLUDE_ALL
        } else {
            construct(next, myIncludeAllElements)
        }
    }

    override fun includeElement(index: Int): TokenFilter? {
        val next = if (myIncludeAllElements && !myPathToMatch.isMaybeMatchingElement) {
            myPathToMatch.tail
        } else {
            myPathToMatch.matchElement(index)
        } ?: return null

        return if (next.isMatching) {
            INCLUDE_ALL
        } else {
            construct(next, myIncludeAllElements)
        }
    }

    override fun includeScalar(): Boolean {
        return myPathToMatch.isMatching
    }

    override fun toString(): String {
        return "[CirJsonPointerFilter at: $myPathToMatch]"
    }

}