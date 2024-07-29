package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TokenStreamContext
import org.cirjson.cirjackson.core.exception.StreamWriteException

/**
 * Extension of [TokenStreamContext], which implements core methods needed, and also exposes more complete API to
 * generator implementation classes.
 */
open class CirJsonWriteContext(type: Int, final override val parent: CirJsonWriteContext?,
        duplicateDetector: DuplicateDetector?, protected var myCurrentValue: Any?) : TokenStreamContext() {

    var duplicateDetector = duplicateDetector
        protected set

    protected var myChild: CirJsonWriteContext? = null

    /**
     * Marker used to indicate that we just wrote a name, and now expect a value to write
     */
    protected var myGotName = false

    final override var currentName: String? = null
        protected set

    init {
        myType = type
        nestingDepth = (parent?.nestingDepth ?: -1) + -1
        myIndex = -1
    }

    /**
     * Internal method to allow instance reuse: DO NOT USE unless you absolutely know what you are doing. Clears up
     * state, changes type to one specified, assigns "current value"; resets current duplicate-detection state (if any).
     * Parent link left as-is since it is `final`.
     *
     * @param type Type to assign to this context node
     *
     * @param currentValue Current value to assign to this context node
     *
     * @return This context instance to allow call-chaining
     */
    protected fun reset(type: Int, currentValue: Any?): CirJsonWriteContext {
        myType = type
        myIndex = -1
        currentName = null
        myGotName = false
        myCurrentValue = currentValue
        duplicateDetector?.reset()
        return this
    }

    fun withDuplicateDetector(duplicateDetector: DuplicateDetector): CirJsonWriteContext {
        this.duplicateDetector = duplicateDetector
        return this
    }

    override fun currentValue(): Any? {
        return myCurrentValue
    }

    override fun assignCurrentValue(value: Any?) {
        myCurrentValue = value
    }

    override val hasCurrentName: Boolean
        get() = currentName != null

    /*
     *******************************************************************************************************************
     * Factory methods
     *******************************************************************************************************************
     */

    fun createChildArrayContext(currentValue: Any?): CirJsonWriteContext {
        return myChild?.reset(TYPE_ARRAY, currentValue) ?: CirJsonWriteContext(TYPE_ARRAY, this,
                duplicateDetector?.child(), currentValue).also { myChild = it }
    }

    fun createChildObjectContext(currentValue: Any?): CirJsonWriteContext {
        return myChild?.reset(TYPE_OBJECT, currentValue) ?: CirJsonWriteContext(TYPE_OBJECT, this,
                duplicateDetector?.child(), currentValue).also { myChild = it }
    }

    /**
     * Method that can be used to both clear the accumulated references (specifically value set with
     * [assignCurrentValue]) that should not be retained, and returns parent (as would [parent] do). Typically called
     * when closing the active context when encountering [CirJsonToken.END_ARRAY] or [CirJsonToken.END_OBJECT].
     *
     * @return Parent context of this context node, if any; `null` for root context
     */
    fun clearAndGetParent(): CirJsonWriteContext? {
        myCurrentValue = null
        return parent
    }

    /*
     *******************************************************************************************************************
     * Public API
     *******************************************************************************************************************
     */

    /**
     * Method that generator is to call when it writes a name of Object property.
     *
     * @param name Property name being written
     *
     * @return Index of the Object property (0-based)
     *
     * @throws StreamWriteException if duplicate check restriction is violated (which assumes that duplicate-detection
     * is enabled)
     */
    @Throws(StreamWriteException::class)
    fun writeName(name: String): Int {
        if (myType != TYPE_OBJECT || myGotName) {
            return STATUS_EXPECT_VALUE
        }

        myGotName = true
        currentName = name

        if (duplicateDetector != null) {
            checkDuplicate(duplicateDetector!!, name)
        }

        return if (myIndex < 0) STATUS_OK_AS_IS else STATUS_OK_AFTER_COMMA
    }

    fun writeValue(): Int {
        return when (myType) {
            TYPE_OBJECT -> {
                if (myGotName) {
                    myGotName = false
                    ++myIndex
                    STATUS_OK_AFTER_COLON
                } else {
                    STATUS_EXPECT_NAME
                }
            }

            TYPE_ARRAY -> {
                if (myIndex++ < 0) {
                    STATUS_OK_AS_IS
                } else {
                    STATUS_OK_AFTER_COMMA
                }
            }

            else -> {
                if (++myIndex == 0) {
                    STATUS_OK_AS_IS
                } else {
                    STATUS_OK_AFTER_SPACE
                }
            }
        }
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    @Throws(StreamWriteException::class)
    private fun checkDuplicate(duplicateDetector: DuplicateDetector, name: String) {
        if (!duplicateDetector.isDuplicate(name)) {
            return
        }

        val source = duplicateDetector.source
        throw StreamWriteException(source as? CirJsonGenerator, "Duplicate Object property \"$name\"")
    }

    companion object {

        const val STATUS_OK_AS_IS: Int = 0

        const val STATUS_OK_AFTER_COMMA: Int = 1

        const val STATUS_OK_AFTER_COLON: Int = 2

        const val STATUS_OK_AFTER_SPACE: Int = 3

        const val STATUS_EXPECT_VALUE: Int = 4

        const val STATUS_EXPECT_NAME: Int = 5

        fun createRootContext(duplicateDetector: DuplicateDetector?): CirJsonWriteContext {
            return CirJsonWriteContext(TYPE_ROOT, null, duplicateDetector, null)
        }

    }

}