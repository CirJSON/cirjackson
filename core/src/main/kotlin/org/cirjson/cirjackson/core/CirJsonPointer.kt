package org.cirjson.cirjackson.core

import org.cirjson.cirjackson.core.io.NumberInput

/**
 * Pointer instances can be used to locate logical CirJSON nodes for things like
 * tree traversal (see [TreeNode.at]).
 *
 * Instances are fully immutable and can be cached, shared between threads.
 *
 * @property myAsString We will retain representation of the pointer, as a String, so that [toString] should be as
 * efficient as possible.
 *
 * NOTE: There is no accompanying [myAsStringOffset] that MUST be considered with this String; this `String` may contain
 * preceding path, as it is now full path of parent pointer, except for the outermost pointer instance.
 *
 * @property myNextSegment Reference to rest of the pointer beyond currently matching segment (if any); `null` if this
 * pointer refers to the matching segment.
 */
class CirJsonPointer private constructor(private val myAsString: String, private val myAsStringOffset: Int,
        val matchingProperty: String?, val matchingIndex: Int, private val myNextSegment: CirJsonPointer?) {

    /**
     * Constructor used for creating "empty" instance, used to represent state that matches current node.
     */
    constructor() : this("", 0, null, -1, null)

    constructor(myAsString: String, myAsStringOffset: Int, matchingProperty: String,
            myNextSegment: CirJsonPointer?) : this(myAsString, myAsStringOffset, matchingProperty,
            parseIndex(matchingProperty), myNextSegment)

    /*
     *******************************************************************************************************************
     * Helper classes
     *******************************************************************************************************************
     */

    /**
     * Helper class used to replace call stack when parsing [CirJsonPointer] expressions.
     */
    private data class PointerParent(val parent: PointerParent?, val fullPathOffset: Int, val segment: String)

    /**
     * Helper class used to contain a single segment when constructing [CirJsonPointer] from context.
     */
    private data class PointerSegment(val next: PointerSegment?, val property: String?, val index: Int,
            var pathOffset: Int = 0, var prev: PointerSegment? = null) {

        init {
            next?.prev = this
        }

    }

    companion object {

        private const val ESC = '~'

        private const val ESC_SLASH = "~1"

        private const val ESC_TILDE = "~0"

        private const val SEPARATOR = '/'

        /**
         * Marker instance used to represent segment that matches current
         * node or position (that is, returns true for
         * [matches]).
         */
        private val EMPTY = CirJsonPointer()

        private fun parseIndex(string: String): Int {
            val length = string.length

            if (length == 0 || length > 10) {
                return -1
            }

            var c = string[0]

            if (c <= '0') {
                return if (length == 1 && c == '0') 0 else -1
            }

            if (c > '9') {
                return -1
            }

            for (i in 0..<length) {
                c = string[i]
                if (c !in '0'..'9') {
                    return -1
                }
            }

            return if (length == 10) {
                val long = string.toLong()

                if (long > Int.MAX_VALUE) {
                    -1
                } else {
                    long.toInt()
                }
            } else {
                NumberInput.parseInt(string)
            }
        }

        /**
         * Factory method that will construct a pointer instance that describes path to location given
         * [TokenStreamContext] points to.
         *
         * @param streamContext Context to build pointer expression fot
         *
         * @param includeRoot Whether to include number offset for virtual "root context" or not.
         *
         * @return [CirJsonPointer] path to location of given context
         */
        fun forPath(streamContext: TokenStreamContext?, includeRoot: Boolean): CirJsonPointer {
            var context = streamContext
            context ?: return EMPTY

            if (!context.hasPathSegment) {
                if (!includeRoot || !context.isInRoot || !context.isIndexValid) {
                    context = context.parent
                }
            }

            var next: PointerSegment? = null
            var approxLength = 0

            while (context != null) {
                if (context.isInObject) {
                    val propName = context.currentName ?: ""
                    approxLength += 2 + propName.length
                    next = PointerSegment(next, propName, -1)
                } else if (context.isInArray || includeRoot) {
                    val index = context.currentIndex
                    approxLength += 6
                    next = PointerSegment(next, null, index)
                }

                context = context.parent
            }

            next ?: return EMPTY

            val pathBuilder = StringBuilder(approxLength)
            lateinit var last: PointerSegment

            while (next != null) {
                last = next
                next.pathOffset = pathBuilder.length
                pathBuilder.append(SEPARATOR)

                if (next.property != null) {
                    appendEscaped(pathBuilder, next.property!!)
                } else {
                    pathBuilder.append(next.index)
                }

                next = next.next
            }

            return createCurrentPointer(pathBuilder.toString(), last)
        }

        private fun appendEscaped(stringBuilder: StringBuilder, segment: String) {
            for (c in segment) {
                val toAppend = if (c == SEPARATOR) ESC_SLASH else if (c == ESC) ESC_TILDE else c
                stringBuilder.append(toAppend)
            }
        }

        private fun createCurrentPointer(fullPath: String, last: PointerSegment): CirJsonPointer {
            var currentSegment: PointerSegment? = last
            var currentPointer = EMPTY

            while (currentSegment != null) {
                if (currentSegment.property != null) {
                    currentPointer = CirJsonPointer(fullPath, currentSegment.pathOffset, currentSegment.property!!,
                            currentPointer)
                } else {
                    val index = currentSegment.index
                    currentPointer =
                            CirJsonPointer(fullPath, currentSegment.pathOffset, index.toString(), index, currentPointer)
                }

                currentSegment = currentSegment.next
            }

            return currentPointer
        }

    }

}