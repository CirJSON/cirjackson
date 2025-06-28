package org.cirjson.cirjackson.databind.util

import org.cirjson.cirjackson.core.CirJsonLocation
import org.cirjson.cirjackson.core.TokenStreamContext
import org.cirjson.cirjackson.core.cirjson.CirJsonReadContext
import org.cirjson.cirjackson.core.io.ContentReference

/**
 * Implementation of [TokenStreamContext] used by [TokenBuffer] to link back to the original context to try to keep
 * location information consistent between source location and buffered content when it's re-read from the buffer.
 */
open class TokenBufferReadContext : TokenStreamContext {

    protected val myParent: TokenStreamContext?

    protected var myCurrentName: String?

    protected var myCurrentValue: Any?

    protected val myStartLocation: CirJsonLocation

    /*
     *******************************************************************************************************************
     * Construction
     *******************************************************************************************************************
     */

    protected constructor(base: TokenStreamContext, contentReference: ContentReference) : super(base) {
        myParent = base.parent
        myCurrentName = base.currentName
        myCurrentValue = base.currentValue()
        myStartLocation = (base as? CirJsonReadContext)?.startLocation(contentReference) ?: CirJsonLocation.NA
    }

    protected constructor(base: TokenStreamContext, startLocation: CirJsonLocation) : super(base) {
        myParent = base.parent
        myCurrentName = base.currentName
        myCurrentValue = base.currentValue()
        myStartLocation = startLocation
    }

    /**
     * Constructor for cases where there is no real surrounding context: only create virtual ROOT
     */
    protected constructor() : super(TYPE_ROOT, -1) {
        myParent = null
        myCurrentName = null
        myCurrentValue = null
        myStartLocation = CirJsonLocation.NA
    }

    protected constructor(parent: TokenBufferReadContext, type: Int, index: Int) : super(type, index) {
        myParent = parent
        myCurrentName = null
        myCurrentValue = null
        myStartLocation = parent.myStartLocation
    }

    /*
     *******************************************************************************************************************
     * Location/state information (minus source reference)
     *******************************************************************************************************************
     */

    override fun currentValue(): Any? {
        return myCurrentValue
    }

    override fun assignCurrentValue(value: Any?) {
        myCurrentValue = value
    }

    /*
     *******************************************************************************************************************
     * Factory methods
     *******************************************************************************************************************
     */

    fun createChildArrayContext(): TokenBufferReadContext {
        ++myIndex
        return TokenBufferReadContext(this, TYPE_ARRAY, -1)
    }

    fun createChildObjectContext(): TokenBufferReadContext {
        ++myIndex
        return TokenBufferReadContext(this, TYPE_OBJECT, -1)
    }

    fun parentOrCopy(): TokenBufferReadContext {
        myParent ?: return TokenBufferReadContext()
        return myParent as? TokenBufferReadContext ?: TokenBufferReadContext(myParent, myStartLocation)
    }

    /*
     *******************************************************************************************************************
     * Abstract methods implementations
     *******************************************************************************************************************
     */

    override var currentName: String?
        get() = myCurrentName
        set(newName) {
            myCurrentName = newName
        }

    override val hasCurrentName: Boolean
        get() = myCurrentName != null

    override val parent: TokenStreamContext?
        get() = myParent

    /*
     *******************************************************************************************************************
     * Extended support for context updates
     *******************************************************************************************************************
     */

    fun updateForValue() {
        ++myIndex
    }

    companion object {

        /*
         ***************************************************************************************************************
         * Factory methods
         ***************************************************************************************************************
         */

        fun createRootContext(originalContext: TokenStreamContext?): TokenBufferReadContext {
            originalContext ?: return TokenBufferReadContext()
            return TokenBufferReadContext(originalContext, ContentReference.unknown())
        }

    }

}