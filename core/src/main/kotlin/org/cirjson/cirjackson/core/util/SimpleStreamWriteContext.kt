package org.cirjson.cirjackson.core.util

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TokenStreamContext
import org.cirjson.cirjackson.core.cirjson.DuplicateDetector
import org.cirjson.cirjackson.core.exception.StreamReadException
import org.cirjson.cirjackson.core.exception.StreamWriteException

/**
 * Basic implementation of [TokenStreamContext] useful for most format backend [CirJsonGenerator] implementations (with
 * notable exception of CirJSON that needs a bit more advanced state).
 */
class SimpleStreamWriteContext(type: Int, override val parent: SimpleStreamWriteContext?, nestingDepth: Int,
        duplicateDetector: DuplicateDetector?, private var myCurrentValue: Any?) : TokenStreamContext(type, -1) {

    var duplicateDetector = duplicateDetector
        private set

    /**
     * Name of the property of which value is to be written; only used for OBJECT contexts.
     */
    private var myCurrentName: String? = null

    /**
     * Simple instance reuse slots; speed up things a bit (10-15%) for docs with lots of small arrays/objects
     */
    private var myChildToRecycle: SimpleStreamWriteContext? = null

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

    override fun currentValue(): Any? {
        return myCurrentValue
    }

    override fun assignCurrentValue(value: Any?) {
        myCurrentValue = value
    }

    override val hasCurrentName: Boolean
        get() = myCurrentName != null

    private var myGotPropertyId = false

    fun reset(type: Int, currentValue: Any?): SimpleStreamWriteContext {
        myType = type
        myCurrentName = null
        myIndex = -1
        myGotPropertyId = false
        myCurrentValue = currentValue
        duplicateDetector?.reset()
        return this
    }

    fun withDuplicateDetector(duplicateDetector: DuplicateDetector): SimpleStreamWriteContext {
        this.duplicateDetector = duplicateDetector
        return this
    }

    /*
     *******************************************************************************************************************
     * Factory methods
     *******************************************************************************************************************
     */

    fun createChildArrayContext(currentValue: Any?): SimpleStreamWriteContext {
        return myChildToRecycle?.reset(TYPE_ARRAY, currentValue) ?: SimpleStreamWriteContext(TYPE_ARRAY, this,
                nestingDepth + 1, duplicateDetector?.child(), currentValue).also { myChildToRecycle = it }
    }

    fun createChildObjectContext(currentValue: Any?): SimpleStreamWriteContext {
        return myChildToRecycle?.reset(TYPE_OBJECT, currentValue) ?: SimpleStreamWriteContext(TYPE_OBJECT, this,
                nestingDepth + 1, duplicateDetector?.child(), currentValue).also { myChildToRecycle = it }
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
    fun clearAndGetParent(): SimpleStreamWriteContext? {
        myCurrentValue = null
        return parent
    }

    /*
     *******************************************************************************************************************
     * State changes
     *******************************************************************************************************************
     */

    /**
     * Method that writer is to call before it writes an Object Property name.
     *
     * @param name Name of Object property name being written
     *
     * @return `true` if name writing should proceed; `false` if not
     *
     * @throws StreamWriteException If write fails due to duplicate check
     */
    @Throws(StreamWriteException::class)
    fun writeName(name: String): Boolean {
        if (myType != TYPE_OBJECT || myGotPropertyId) {
            return false
        }

        myGotPropertyId = true
        myCurrentName = name

        if (duplicateDetector != null) {
            checkDuplicate(duplicateDetector!!, name)
        }

        return true
    }

    fun writeValue(): Boolean {
        if (myType == TYPE_OBJECT) {
            if (!myGotPropertyId) {
                return false
            }

            myGotPropertyId = false
        }

        ++myIndex
        return true
    }

    @Throws(StreamWriteException::class)
    private fun checkDuplicate(duplicateDetector: DuplicateDetector, name: String) {
        if (!duplicateDetector.isDuplicate(name)) {
            return
        }

        val source = duplicateDetector.source
        throw StreamWriteException(source as? CirJsonGenerator, "Duplicate Object property \"$name\"")
    }

    companion object {

        fun createRootContext(duplicateDetector: DuplicateDetector?): SimpleStreamWriteContext {
            return SimpleStreamWriteContext(TYPE_ROOT, null, 0, duplicateDetector, null)
        }

    }

}