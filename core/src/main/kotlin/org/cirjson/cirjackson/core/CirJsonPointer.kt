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

    private var myHead: CirJsonPointer? = null

    /**
     * Lazily-calculated hash code: need to retain hash code now that we can no longer rely on [myAsString] being the
     * exact full representation (it is often "more", including parent path).
     */
    private val myHashCode by lazy {
        toString().hashCode()
    }

    /**
     * Constructor used for creating "empty" instance, used to represent state that matches current node.
     */
    constructor() : this("", 0, null, -1, null)

    constructor(myAsString: String, myAsStringOffset: Int, matchingProperty: String,
            myNextSegment: CirJsonPointer?) : this(myAsString, myAsStringOffset, matchingProperty,
            parseIndex(matchingProperty), myNextSegment)

    private fun constructHead(): CirJsonPointer {
        val last = last!!

        if (last === this) {
            return EMPTY
        }

        val suffixLength = last.length
        val fullString = toString()

        return CirJsonPointer(fullString.substring(0, fullString.length - suffixLength), 0, matchingProperty,
                matchingIndex, myNextSegment!!.constructHead(suffixLength, last))
    }

    fun constructHead(suffixLength: Int, last: CirJsonPointer): CirJsonPointer {
        return if (this === last) {
            EMPTY
        } else {
            val str = toString()
            CirJsonPointer(str.substring(0, str.length - suffixLength), 0, matchingProperty, matchingIndex,
                    myNextSegment!!.constructHead(suffixLength, last))
        }
    }

    /*
     *******************************************************************************************************************
     * Public API
     *******************************************************************************************************************
     */

    /**
     * Length of String representation of this pointer instance.
     *
     * Functionally same as:
     * ```
     *  toString().length
     * ```
     * but more efficient as it avoids likely String allocation.
     */
    val length
        get() = myAsString.length - myAsStringOffset

    val isMatching
        get() = myNextSegment == null

    /**
     * True if the root selector matches property name (that is, could match Property value of Object node)
     */
    val isMaybeMatchingProperty = matchingProperty != null

    /**
     * True if the root selector matches element index (that is, could match an element of Array node)
     */
    val isMaybeMatchingElement = matchingIndex >= 0

    /**
     * The leaf of current CirJSON Pointer expression: leaf is the last non-null segment of current CirJSON Pointer.
     */
    val last: CirJsonPointer?
        get() {
            var current = this

            if (current === EMPTY) {
                return null
            }

            lateinit var next: CirJsonPointer

            while (current.myNextSegment?.also { next = it } !== EMPTY) {
                current = next
            }

            return current
        }

    /**
     * Accessor for getting a "sub-pointer" (or sub-path), instance where current segment has been removed and pointer
     * includes rest of the segments. For example, for CirJSON Pointer "/root/branch/leaf", this method would return
     * pointer "/branch/leaf". For matching state (last segment), will return `null`.
     *
     * Note that this is a very cheap method to call as it simply returns "next" segment (which has been constructed
     * when pointer instance was constructed).
     */
    val tail
        get() = myNextSegment

    /**
     * Accessor for getting a pointer instance that is identical to this instance except that the last segment has been
     * dropped. For example, for CirJSON Pointer "/root/branch/leaf", this method would return pointer "/root/branch"
     * (compared to [tail] that would return "/branch/leaf").
     *
     * Note that whereas [tail] is a very cheap operation to call (as "tail" already exists for single-linked forward
     * direction), this method has to fully construct a new instance by traversing the chain of segments.
     */
    val head: CirJsonPointer?
        get() {
            var head = myHead

            if (head == null) {
                if (this !== EMPTY) {
                    head = constructHead()
                }

                myHead = head
            }

            return head
        }

    /**
     * Mutant factory method that will return
     * * `tail` if `this` instance is "empty" pointer, OR
     * * `this` instance if `tail` is "empty" pointer, OR
     * * Newly constructed [CirJsonPointer] instance that starts with all segments of `this`, followed by all segments
     * of `tail`.
     *
     * @param tail [CirJsonPointer] instance to append to this one, to create a new pointer instance
     *
     * @return Either `this` instance, `tail`, or a newly created combination, as per description above.
     */
    fun append(tail: CirJsonPointer): CirJsonPointer {
        return if (this === EMPTY) {
            tail
        } else if (tail === EMPTY) {
            this
        } else {
            compile("$this$tail")
        }
    }

    /**
     * ATTENTION! [CirJsonPointer] is head-centric, tail appending is much costlier than head appending. It is
     * recommended that this method is used sparingly due to possible sub-par performance. Mutant factory method that
     * will return:
     * * `this` instance if `property` is null, OR
     * * Newly constructed [CirJsonPointer] instance that starts with all segments of `this`, followed by new segment of
     * 'property' name.
     *
     * `property` is name to match: value is escaped as necessary (for any contained slashes or tildes).
     *
     * @param property new segment property name
     *
     * @return Either `this` instance, or a newly created combination, as per description above.
     */
    fun appendProperty(property: String?): CirJsonPointer {
        property ?: return this

        val stringBuilder = toStringBuilder(property.length + 2).apply { append(SEPARATOR) }
        appendEscaped(stringBuilder, property)
        return compile(stringBuilder.toString())
    }

    /**
     * Functionally equivalent to:
     * ```
     *   StringBuilder(toString())
     * ```
     * but possibly more efficient
     *
     * @param slack Number of characters to reserve in StringBuilder beyond minimum copied
     *
     * @return a new StringBuilder
     */
    private fun toStringBuilder(slack: Int): StringBuilder {
        if (myAsStringOffset <= 0) {
            return StringBuilder(myAsString)
        }

        val length = myAsString.length
        return StringBuilder(length - myAsStringOffset + slack).apply {
            append(myAsString, myAsStringOffset, length)
        }
    }

    /**
     * ATTENTION! [CirJsonPointer] is head-centric, tail appending is much costlier than head appending. It is
     * recommended that this method is used sparingly due to possible sub-par performance. Mutant factory method that
     * will return newly constructed [CirJsonPointer] instance that starts with all segments of `this`, followed by new
     * segment of element `index`. Element `index` should be non-negative.
     *
     * @param index new segment element index
     *
     * @return Newly created combination, as per description above.
     *
     * @throws IllegalArgumentException if element index is negative
     */
    fun appendIndex(index: Int): CirJsonPointer {
        if (index < 0) {
            throw IllegalArgumentException("Negative index cannot be appended")
        }

        return compile(toStringBuilder(8).append(SEPARATOR).append(index).toString())
    }

    /**
     * Method that may be called to see if the pointer head (first segment) would match property (of a CirJSON Object)
     * with given name.
     *
     * @param name Name of Object property to match
     *
     * @return `true` if the pointer head matches specified property name
     */
    fun matchesProperty(name: String): Boolean {
        return myNextSegment != null && matchingProperty == name
    }

    /**
     * Method that may be called to check whether the pointer head (first segment) matches specified Object property (by
     * name) and if so, return [CirJsonPointer] that represents rest of the path after match. If there is no match,
     * `null` is returned.
     *
     * @param name Name of Object property to match
     *
     * @return Remaining path after matching specified property, if there is match; `null` otherwise
     */
    fun matchProperty(name: String): CirJsonPointer? {
        return myNextSegment?.takeIf { matchingProperty == name }
    }

    /**
     * Method that may be called to see if the pointer would match Array element (of a CirJSON Array) with given index.
     *
     * @param index Index of Array element to match
     *
     * @return `True` if the pointer head matches specified Array index
     *
     * @since 2.5
     */
    fun matchesElement(index: Int): Boolean {
        return index >= 0 && matchingIndex == index
    }

    /**
     * Method that may be called to check whether the pointer head (first segment) matches specified Array index and if
     * so, return [CirJsonPointer] that represents rest of the path after match. If there is no match, `null` is
     * returned.
     *
     * @param index Index of Array element to match
     *
     * @return Remaining path after matching specified index, if there is match; `null` otherwise
     */
    fun matchElement(index: Int): CirJsonPointer? {
        return myNextSegment?.takeIf { index >= 0 && matchingIndex == index }
    }

    /*
     *******************************************************************************************************************
     * Standard method overrides
     *******************************************************************************************************************
     */

    override fun toString(): String {
        return if (myAsStringOffset <= 0) {
            myAsString
        } else {
            myAsString.substring(myAsStringOffset)
        }
    }

    override fun hashCode(): Int {
        return myHashCode
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (javaClass != (other as? CirJsonPointer)?.javaClass) {
            return false
        }

        return compare(myAsString, myAsStringOffset, other.myAsString, other.myAsStringOffset)
    }

    private fun compare(string1: String, offset1: Int, string2: String, offset2: Int): Boolean {
        var realOffset1 = offset1
        var realOffset2 = offset2
        val end1 = string1.length

        if (end1 - realOffset1 != string2.length - realOffset2) {
            return false
        }

        while (realOffset1 < end1) {
            if (string1[realOffset1++] != string2[realOffset2++]) {
                return false
            }
        }

        return true
    }

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

        /**
         * Accessor for an "empty" expression, that is, one you can get by calling [compile] with "" (empty String).
         *
         * NOTE: this is different from expression for `"/"` which would instead match Object node property with empty
         * String ("") as name.
         *
         * @return "Empty" pointer expression instance that matches given root value
         */
        fun empty(): CirJsonPointer {
            return EMPTY
        }

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
                    val index = if (context.isInArray) context.currentIndex - 1 else context.currentIndex
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

                currentSegment = currentSegment.prev
            }

            return currentPointer
        }

        /**
         * Factory method that parses given input and construct matching pointer instance, if it represents a valid
         * CirJSON Pointer: if not, a [IllegalArgumentException] is thrown.
         *
         * @param expression Pointer expression to compile
         *
         * @return Compiled [CirJsonPointer] path expression
         *
         * @throws IllegalArgumentException Thrown if the input does not present a valid CirJSON Pointer expression:
         * currently the only such expression is one that does NOT start with a slash ('/').
         */
        @Throws(IllegalArgumentException::class)
        fun compile(expression: String?): CirJsonPointer {
            if (expression.isNullOrEmpty()) {
                return EMPTY
            }

            if (expression[0] != SEPARATOR) {
                throw IllegalArgumentException(
                        "Invalid input: JSON Pointer expression must start with '/': \"$expression\"")
            }

            return parseTail(expression)
        }

        private fun parseTail(fullPath: String): CirJsonPointer {
            var parent: PointerParent? = null
            var i = 1
            val end = fullPath.length
            var startOffset = 0

            while (i < end) {
                val c = fullPath[i]

                if (c == SEPARATOR) {
                    parent = PointerParent(parent, startOffset, fullPath.substring(startOffset + 1, i))
                    startOffset = i++
                    continue
                }

                i++

                if (c == ESC && i < end) {
                    val stringBuilder = StringBuilder(32)
                    i = extractEscapedSegment(fullPath, startOffset + 1, i, stringBuilder)
                    val segment = stringBuilder.toString()

                    if (i < 0) {
                        return buildPath(fullPath, startOffset, segment, parent)
                    }

                    parent = PointerParent(parent, startOffset, segment)
                    startOffset = i++
                }
            }

            return buildPath(fullPath, startOffset, fullPath.substring(startOffset + 1), parent)
        }

        /**
         * Method called to extract the next segment of the path, in case where we seem to have encountered a (tilde-)escaped character within segment.
         *
         * @param input Full input for the tail being parsed
         *
         * @param firstCharOffset Offset of the first character of segment (one after slash)
         *
         * @param index Offset to character after tilde
         *
         * @param stringBuilder StringBuilder into which unquoted segment is added
         *
         * @return Offset at which slash was encountered, if any, or -1 if expression ended without seeing unescaped slash
         */
        private fun extractEscapedSegment(input: String, firstCharOffset: Int, index: Int,
                stringBuilder: StringBuilder): Int {
            var i = index
            val end = input.length
            val toCopy = i - 1 - firstCharOffset

            if (toCopy > 0) {
                stringBuilder.append(input, firstCharOffset, i - 1)
            }

            appendEscape(stringBuilder, input[i++])

            while (i < end) {
                val c = input[i]

                if (c == SEPARATOR) {
                    return i
                }

                i++

                if (c == ESC && i < end) {
                    appendEscape(stringBuilder, input[i++])
                    continue
                }

                stringBuilder.append(c)
            }

            return -1
        }

        private fun appendEscape(stringBuilder: StringBuilder, char: Char) {
            val toAppend = when (char) {
                '0' -> {
                    ESC
                }

                '1' -> {
                    SEPARATOR
                }

                else -> {
                    stringBuilder.append(ESC)
                    char
                }
            }

            stringBuilder.append(toAppend)
        }

        private fun buildPath(fullPath: String, fullPathOffset: Int, segment: String,
                parent: PointerParent?): CirJsonPointer {
            var realParent: PointerParent? = parent
            var current = CirJsonPointer(fullPath, fullPathOffset, segment, EMPTY)

            while (realParent != null) {
                current = CirJsonPointer(fullPath, realParent.fullPathOffset, realParent.segment, current)
                realParent = realParent.parent
            }

            return current
        }

        /**
         * Alias for [compile]; added to make instances automatically deserializable by CirJackson databind.
         *
         * @param expression Pointer expression to compile
         *
         * @return Compiled [CirJsonPointer] path expression
         */
        fun valueOf(expression: String?): CirJsonPointer {
            return compile(expression)
        }

    }

}