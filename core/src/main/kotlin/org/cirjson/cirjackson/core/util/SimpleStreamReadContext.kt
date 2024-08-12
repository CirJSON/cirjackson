package org.cirjson.cirjackson.core.util

import org.cirjson.cirjackson.core.CirJsonLocation
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TokenStreamContext
import org.cirjson.cirjackson.core.cirjson.DuplicateDetector
import org.cirjson.cirjackson.core.exception.StreamReadException
import org.cirjson.cirjackson.core.io.ContentReference

/**
 * Basic implementation of [TokenStreamContext] useful for most format backend [CirJsonParser] implementations (with
 * notable exception of CirJSON that needs a bit more advanced state).
 */
open class SimpleStreamReadContext(type: Int, override val parent: SimpleStreamReadContext?, nestingDepth: Int,
        duplicateDetector: DuplicateDetector?, protected var myLineNumber: Int, protected var myColumnNumber: Int) :
        TokenStreamContext(type, -1) {

    var duplicateDetector = duplicateDetector
        protected set

    /**
     * Simple instance reuse slots; speed up things a bit (10-15%) for docs with lots of small arrays/objects
     */
    protected var myChildToRecycle: SimpleStreamReadContext? = null

    protected var myCurrentName: String? = null

    protected var myCurrentValue: Any? = null

    @set:Throws(StreamReadException::class)
    override var currentName: String?
        get() = myCurrentName
        set(value) {
            myCurrentName = value!!

            if (duplicateDetector != null) {
                checkDuplicate(duplicateDetector!!, value)
            }
        }

    init {
        this.nestingDepth = nestingDepth
    }

    fun reset(type: Int, lineNumber: Int, columnNumber: Int): SimpleStreamReadContext {
        myType = type
        myCurrentValue = null
        myLineNumber = lineNumber
        myColumnNumber = columnNumber
        myIndex = -1
        myCurrentName = null
        duplicateDetector?.reset()
        return this
    }

    override fun currentValue(): Any? {
        return myCurrentValue
    }

    override fun assignCurrentValue(value: Any?) {
        myCurrentValue = value
    }

    override val hasCurrentName: Boolean
        get() = myCurrentName != null

    override fun startLocation(reference: ContentReference): CirJsonLocation {
        return CirJsonLocation(reference, -1L, myLineNumber, myColumnNumber)
    }

    /*
     *******************************************************************************************************************
     * Factory methods
     *******************************************************************************************************************
     */

    fun createChildArrayContext(lineNumber: Int, columnNumber: Int): SimpleStreamReadContext {
        return myChildToRecycle?.reset(TYPE_ARRAY, lineNumber, columnNumber) ?: SimpleStreamReadContext(TYPE_ARRAY,
                this, nestingDepth + 1, duplicateDetector?.child(), lineNumber, columnNumber).also {
            myChildToRecycle = it
        }
    }

    fun createChildObjectContext(lineNumber: Int, columnNumber: Int): SimpleStreamReadContext {
        return myChildToRecycle?.reset(TYPE_OBJECT, lineNumber, columnNumber) ?: SimpleStreamReadContext(TYPE_OBJECT,
                this, nestingDepth + 1, duplicateDetector?.child(), lineNumber, columnNumber).also {
            myChildToRecycle = it
        }
    }

    /*
     *******************************************************************************************************************
     * Extended API
     *******************************************************************************************************************
     */

    /**
     * Method that can be used to both clear the accumulated references (specifically value set with
     * [assignCurrentValue]) that should not be retained, and returns parent (as would [parent] do). Typically called
     * when closing the active context when encountering [CirJsonToken.END_ARRAY] or [CirJsonToken.END_OBJECT].
     *
     * @return Parent context of this context node, if any; `null` for root context
     */
    fun clearAndGetParent(): SimpleStreamReadContext? {
        myCurrentValue = null
        return parent
    }

    /*
     *******************************************************************************************************************
     * State changes
     *******************************************************************************************************************
     */

    /**
     * Method to call to advance index within current context: to be called when a new token found within current
     * context (property name for objects, value for root and array contexts)
     *
     * @return Index after increment
     */
    fun valueRead(): Int {
        return ++myIndex
    }

    @Throws(StreamReadException::class)
    protected open fun checkDuplicate(duplicateDetector: DuplicateDetector, name: String) {
        if (!duplicateDetector.isDuplicate(name)) {
            return
        }

        val source = duplicateDetector.source
        throw StreamReadException(source as? CirJsonParser, "Duplicate Object property \"$name\"")
    }

    companion object {

        fun createRootContext(lineNumber: Int, columnNumber: Int,
                duplicateDetector: DuplicateDetector?): SimpleStreamReadContext {
            return SimpleStreamReadContext(TYPE_ROOT, null, 0, duplicateDetector, lineNumber, columnNumber)
        }

        fun createRootContext(duplicateDetector: DuplicateDetector?): SimpleStreamReadContext {
            return createRootContext(1, 0, duplicateDetector)
        }

    }

}