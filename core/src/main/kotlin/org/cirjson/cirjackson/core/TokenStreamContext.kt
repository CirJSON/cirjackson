package org.cirjson.cirjackson.core

import org.cirjson.cirjackson.core.io.CharTypes
import org.cirjson.cirjackson.core.io.ContentReference
import kotlin.math.max

/**
 * Shared base class for streaming processing contexts used during reading and writing of token streams using Streaming
 * API.
 *
 * This context is also exposed to applications: context object can be used by applications to get an idea of relative
 * position of the parser/generator within content being processed. This allows for some contextual processing: for
 * example, output within Array context can differ from that of Object context. Perhaps more importantly context is
 * hierarchic so that enclosing contexts can be inspected as well. All levels also include information about current
 * property name (for Objects) and element index (for Arrays).
 *
 * @property myType Indicates logical type of context as one of `TYPE_xxx` constants.
 *
 * @property myIndex Index of the currently processed entry. Starts with -1 to signal that no entries have been started,
 * and gets advanced each time a new entry is started, either by encountering an expected separator, or with new values
 * if no separators are expected (the case for root context).
 */
abstract class TokenStreamContext protected constructor(protected var myType: Int, protected var myIndex: Int) {

    /**
     * The nesting depth is a count of objects and arrays that have not been closed, `{` and `[` respectively.
     */
    var nestingDepth = 0
        protected set

    /**
     * Accessor for finding parent context of this context; will return `null` for root context.
     */
    abstract val parent: TokenStreamContext?

    /**
     * Accessor that returns true if this context is an Array context; that is, content is being read from or written to
     * a CirJSON Array.
     */
    val isInArray
        get() = myType == TYPE_ARRAY

    /**
     * Accessor that returns true if this context is a Root context; that is, content is being read from or written to
     * without enclosing array or object structure.
     */
    val isInRoot
        get() = myType == TYPE_ROOT

    /**
     * Accessor that returns true if this context is an Object context; that is, content is being read from or written
     * to a CirJSON Object.
     */
    val isInObject
        get() = myType == TYPE_OBJECT

    /**
     * Accessor for simple type description of current context; either ROOT (for root-level values), OBJECT (for Object
     * property names and values) or ARRAY (for elements of CirJSON Arrays)
     */
    val typeDescription: String
        get() {
            return when (myType) {
                TYPE_ROOT -> "root"
                TYPE_ARRAY -> "Array"
                TYPE_OBJECT -> "Object"
                else -> "?"
            }
        }

    /**
     * Number of entries that are complete and started.
     */
    val entryCount
        get() = myIndex + 1

    /**
     * Index of the currently processed entry, if any
     */
    val currentIndex
        get() = max(myIndex, 0)

    /**
     * Accessor that may be called to verify whether this context has valid index: will return `false` before the first
     * entry of Object context or before first element of Array context; otherwise returns `true`.
     */
    val isIndexValid
        get() = if (myType == TYPE_ARRAY) myIndex > 0 else myIndex >= 0

    /**
     * Accessor that may be called to check if this context is either:
     * * Object, with at least one entry written (partially or completely)
     * * Array, with at least one entry written (partially or completely)
     *
     * and if so, return `true`; otherwise return `false`. Latter case includes Root context (always), and Object/Array
     * contexts before any entries/elements have been read or written.
     *
     * Accessor is mostly used to determine whether this context should be used for constructing [CirJsonPointer]
     */
    val hasPathSegment: Boolean
        get() {
            return when (myType) {
                TYPE_ARRAY -> isIndexValid
                TYPE_OBJECT -> hasCurrentName
                else -> false
            }
        }

    /**
     * Accessor for name associated with the current location. Non-null for `PROPERTY_NAME` and value events that
     * directly follow Property names; `null` for root level and array values.
     */
    abstract val currentName: String?

    open val hasCurrentName
        get() = currentName != null

    constructor() : this(0, 0)

    /**
     * Copy constructor used by subclasses for creating copies for buffering.
     *
     * @param base Context instance to copy type and index from
     */
    constructor(base: TokenStreamContext) : this(base.myType, base.myIndex)

    /**
     * Method for accessing currently active value being used by data-binding (as the source of streaming data to write,
     * or destination of data being read), at this level in hierarchy.
     *
     * Note that "current value" is NOT populated (or used) by Streaming parser or generator; it is only used by
     * higher-level data-binding functionality. The reason it is included here is that it can be stored and accessed
     * hierarchically, and gets passed through data-binding.
     *
     * Default implementation returns `null`
     *
     * @return Currently active value, if one has been assigned.
     */
    open fun currentValue(): Any? {
        return null
    }

    /**
     * Method to call to pass value to be returned via [currentValue]; typically called indirectly through
     * [CirJsonParser.assignCurrentValue] or [CirJsonGenerator.assignCurrentValue].
     *
     * Default implementation does nothing
     *
     * @param value Current value to assign to this context
     */
    open fun assignCurrentValue(value: Any?) {}

    /**
     * Factory method for constructing a [CirJsonPointer] that points to the current location within the stream that
     * this context is for, excluding information about "root context" (only relevant for multi-root-value cases)
     *
     * @return Pointer instance constructed
     */
    fun pathAsPointer(): CirJsonPointer {
        return pathAsPointer(false)
    }

    /**
     * Factory method for constructing a [CirJsonPointer] that points to the current location within the stream that
     * this context is for, optionally including "root value index"
     *
     * @param includeRoot Whether root-value offset is included as the first segment or not
     *
     * @return Pointer instance constructed
     */
    fun pathAsPointer(includeRoot: Boolean): CirJsonPointer {
        return CirJsonPointer.forPath(this, includeRoot)
    }

    /**
     * Optional method that may be used to access starting location of this context: for example, in case of CirJSON
     * `Object` context, offset at which `[` token was read or written. Often used for error reporting purposes.
     * Implementations that do not keep track of such location are expected to return [CirJsonLocation.NA]; this is what
     * the default implementation does.
     *
     * @param reference Source reference needed to construct location instance
     *
     * @return Location pointing to the point where the context start marker was found (or written); never `null`.
     */
    open fun startLocation(reference: ContentReference): CirJsonLocation {
        return CirJsonLocation.NA
    }

    /**
     * Overridden to provide developer readable "CirJsonPath" representation of the context.
     *
     * @return Simple developer-readable description this context layer (note: NOT constructed with parents, unlike
     * [pathAsPointer])
     */
    override fun toString(): String {
        return StringBuilder(64).apply {
            when (myType) {
                TYPE_ROOT -> {
                    append("/")
                }

                TYPE_ARRAY -> {
                    append('[')
                    append(currentIndex)
                    append(']')
                }

                else -> {
                    append('{')
                    val currentName = currentName

                    if (currentName != null) {
                        append('"')
                        CharTypes.appendQuoted(this, currentName)
                        append('"')
                    } else {
                        append('?')
                    }

                    append('}')
                }
            }
        }.toString()
    }

    companion object {

        /**
         * Indicator for "Root Value" context (has no parent)
         */
        const val TYPE_ROOT = 0

        /**
         * Indicator for "Array" context
         */
        const val TYPE_ARRAY = 1

        /**
         * Indicator for "Object" context
         */
        const val TYPE_OBJECT = 2

    }

}