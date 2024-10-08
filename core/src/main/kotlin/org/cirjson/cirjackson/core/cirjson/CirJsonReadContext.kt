package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.CirJsonLocation
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TokenStreamContext
import org.cirjson.cirjackson.core.exception.StreamReadException
import org.cirjson.cirjackson.core.io.ContentReference

/**
 * Extension of [TokenStreamContext], which implements core methods needed, and also exposes more complete API to parser
 * implementation classes.
 *
 * @param parent Parent context, if any (`null` for Root context)
 *
 * @param nestingDepth Number of parents this context has (0 for Root context)
 *
 * @param myDuplicateDetector Detector used for checking duplicate names, if any (`null` if none)
 *
 * @param type Type to assign to this context node
 *
 * @param myLineNumber Line of the starting position of this context
 *
 * @param myColumnNumber Column of the starting position of this context
 */
class CirJsonReadContext(override val parent: CirJsonReadContext?, nestingDepth: Int,
        private var myDuplicateDetector: DuplicateDetector?, type: Int, private var myLineNumber: Int,
        private var myColumnNumber: Int) : TokenStreamContext() {

    private var myChild: CirJsonReadContext? = null

    private var myCurrentName: String? = null

    private var myCurrentValue: Any? = null

    val duplicateDetector
        get() = myDuplicateDetector

    @set:Throws(StreamReadException::class)
    override var currentName: String?
        get() = myCurrentName
        set(value) {
            myCurrentName = value
            if (myDuplicateDetector != null) {
                checkDuplicates(myDuplicateDetector!!, value)
            }
        }

    init {
        myIndex = -1
        myType = type
        this.nestingDepth = nestingDepth
    }

    /*
     *******************************************************************************************************************
     * Config, reuse
     *******************************************************************************************************************
     */

    /**
     * Internal method to allow instance reuse: DO NOT USE unless you absolutely know what you are doing. Clears up
     * state (including "current value"), changes type to one specified; resets current duplicate-detection state (if
     * any). Parent link left as-is since it is `final`.
     *
     * @param type Type to assign to this context node
     *
     * @param lineNumber Line of the starting position of this context
     *
     * @param columnNumber Column of the starting position of this context
     *
     * @return This context instance (to allow call-chaining)
     */
    fun reset(type: Int, lineNumber: Int, columnNumber: Int): CirJsonReadContext {
        myIndex = -1
        myType = type
        myLineNumber = lineNumber
        myColumnNumber = columnNumber
        myCurrentName = null
        myCurrentValue = null
        myDuplicateDetector?.reset()
        return this
    }

    fun withDuplicateDetector(duplicateDetector: DuplicateDetector?): CirJsonReadContext {
        myDuplicateDetector = duplicateDetector
        return this
    }

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

    fun createChildArrayContext(lineNumber: Int, columnNumber: Int): CirJsonReadContext {
        val context = myChild ?: CirJsonReadContext(this, nestingDepth + 1, myDuplicateDetector?.child(), TYPE_ARRAY,
                lineNumber, columnNumber).also { myChild = it }
        context.reset(TYPE_ARRAY, lineNumber, columnNumber)
        return context
    }

    fun createChildObjectContext(lineNumber: Int, columnNumber: Int): CirJsonReadContext {
        val context = myChild ?: CirJsonReadContext(this, nestingDepth + 1, myDuplicateDetector?.child(), TYPE_OBJECT,
                lineNumber, columnNumber).also { myChild = it }
        context.reset(TYPE_OBJECT, lineNumber, columnNumber)
        return context
    }

    /*
     *******************************************************************************************************************
     * Abstract method implementations, overrides
     *******************************************************************************************************************
     */

    override val hasCurrentName: Boolean
        get() = myCurrentName != null

    override fun startLocation(reference: ContentReference): CirJsonLocation {
        return CirJsonLocation(ContentReference.rawReference(reference), -1L, myLineNumber, myColumnNumber)
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
    fun clearAndGetParent(): CirJsonReadContext? {
        myCurrentValue = null
        return parent
    }

    val isExpectingComma: Boolean
        get() {
            val index = ++myIndex
            return myType != TYPE_ROOT && index > 0
        }

    @Throws(StreamReadException::class)
    private fun checkDuplicates(duplicateDetector: DuplicateDetector, name: String?) {
        if (!duplicateDetector.isDuplicate(name)) {
            return
        }

        val source = duplicateDetector.source
        throw StreamReadException(source as? CirJsonParser, "Duplicate Object property \"$name\"")
    }

    companion object {

        fun createRootContext(lineNumber: Int, columnNumber: Int,
                duplicateDetector: DuplicateDetector?): CirJsonReadContext {
            return CirJsonReadContext(null, 0, duplicateDetector, TYPE_ROOT, lineNumber, columnNumber)
        }

        fun createRootContext(duplicateDetector: DuplicateDetector?): CirJsonReadContext {
            return CirJsonReadContext(null, 0, duplicateDetector, TYPE_ROOT, 1, 0)
        }

    }

}