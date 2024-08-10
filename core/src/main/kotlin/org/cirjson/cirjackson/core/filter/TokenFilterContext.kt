package org.cirjson.cirjackson.core.filter

import org.cirjson.cirjackson.core.TokenStreamContext

/**
 * Alternative variant of [TokenStreamContext], used when filtering content being read or written (based on
 * [TokenFilter]).
 */
open class TokenFilterContext protected constructor(type: Int, final override val parent: TokenFilterContext?,
        filter: TokenFilter, protected var myCurrentValue: Any?, isStartHandled: Boolean) :
        TokenStreamContext(type, -1) {

    protected var myChild: TokenFilterContext? = null

    /**
     * Name of the property of which value is to be parsed; only used for OBJECT contexts
     */
    final override var currentName: String? = null
        protected set

    /**
     * Filter to use for items in this state (for properties of Objects, elements of Arrays, and root-level values of
     * root context)
     */
    var filter = filter
        protected set

    /**
     * Flag that indicates that start token has been read/written, so that matching close token needs to be read/written
     * as well when context is getting closed.
     */
    var isStartHandled = isStartHandled
        protected set

    /**
     * Flag that indicates that the current name of this context still needs to be read/written, if path from root down
     * to included leaf is to be exposed.
     */
    protected var myIsNeedingToHandleName = false

    override val hasCurrentName: Boolean
        get() = currentName != null

    override fun currentValue(): Any? {
        return myCurrentValue
    }

    init {
        nestingDepth = (parent?.nestingDepth ?: -1) + 1
    }

    protected fun reset(type: Int, filter: TokenFilter, currentValue: Any?,
            isStartWritten: Boolean): TokenFilterContext {
        myType = type
        this.filter = filter
        myIndex = -1
        myCurrentValue = currentValue
        currentName = null
        isStartHandled = isStartWritten
        myIsNeedingToHandleName = false
        return this
    }

    /*
     *******************************************************************************************************************
     * Factory methods
     *******************************************************************************************************************
     */

    fun createChildArrayContext(filter: TokenFilter, currentValue: Any?, writeStart: Boolean): TokenFilterContext {
        return myChild?.reset(TYPE_ARRAY, filter, currentValue, writeStart) ?: TokenFilterContext(TYPE_ARRAY, this,
                filter, currentValue, writeStart)
    }

    fun createChildObjectContext(filter: TokenFilter, currentValue: Any?, writeStart: Boolean): TokenFilterContext {
        return myChild?.reset(TYPE_OBJECT, filter, currentValue, writeStart) ?: TokenFilterContext(TYPE_OBJECT, this,
                filter, currentValue, writeStart)
    }

    /*
     *******************************************************************************************************************
     * State changes
     *******************************************************************************************************************
     */

    fun setPropertyName(name: String): TokenFilter {
        currentName = name
        myIsNeedingToHandleName = true
        return filter
    }

    companion object {

        fun createRootContext(filter: TokenFilter): TokenFilterContext {
            return TokenFilterContext(TYPE_ROOT, null, filter, null, true)
        }

    }

}