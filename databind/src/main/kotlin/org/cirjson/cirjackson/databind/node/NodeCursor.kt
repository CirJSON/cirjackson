package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TokenStreamContext
import org.cirjson.cirjackson.databind.CirJsonNode

/**
 * Helper class used by [TreeTraversingParser] to keep track of current location within traversed CirJSON tree.
 *
 * @property myParent Parent cursor of this cursor, if any; `null` for root cursors.
 */
abstract class NodeCursor(contextType: Int, protected val myParent: NodeCursor?) : TokenStreamContext() {

    /**
     * Current field name
     */
    protected var myCurrentName: String? = null

    protected var myCurrentValue: Any? = null

    init {
        myType = contextType
        myIndex = -1
    }

    /*
     *******************************************************************************************************************
     * CirJsonStreamContext implementation
     *******************************************************************************************************************
     */

    final override val parent: NodeCursor?
        get() = myParent

    final override val currentName: String?
        get() = myCurrentName

    open fun overrideCurrentName(name: String?) {
        myCurrentName = name
    }

    override fun currentValue(): Any? {
        return myCurrentValue
    }

    override fun assignCurrentValue(value: Any?) {
        myCurrentValue = value
    }

    /*
     *******************************************************************************************************************
     * Extended API
     *******************************************************************************************************************
     */

    abstract fun nextToken(): CirJsonToken?

    abstract fun currentNode(): CirJsonNode?

    abstract fun startArray(): NodeCursor

    abstract fun startObject(): NodeCursor

    /**
     * Method called to create a new context for iterating all contents of the current structured value (CirJSON array
     * or object)
     */
    fun iterateChildren(): NodeCursor {
        val node = currentNode() ?: throw IllegalStateException("No current node")

        return if (node.isArray) {
            ArrayCursor(node, this)
        } else if (node.isObject) {
            ObjectCursor(node, this)
        } else {
            throw IllegalStateException("Current node of type ${node::class.qualifiedName}")
        }
    }

    /*
     *******************************************************************************************************************
     * Concrete implementations
     *******************************************************************************************************************
     */

    /**
     * Context for all root-level value nodes (including Arrays and Objects): only context for scalar values.
     */
    protected class RootCursor(node: CirJsonNode, parent: NodeCursor?) : NodeCursor(TYPE_ROOT, parent) {

        private var myNode: CirJsonNode? = node

        private var myDone = false

        override fun overrideCurrentName(name: String?) {
            // No-op
        }

        override fun nextToken(): CirJsonToken? {
            if (myDone) {
                myNode = null
                return null
            }

            myIndex++
            myDone = true
            return myNode!!.asToken()
        }

        override fun currentNode(): CirJsonNode? {
            return myNode?.takeIf { myDone }
        }

        override fun startArray(): NodeCursor {
            return ArrayCursor(myNode!!, this)
        }

        override fun startObject(): NodeCursor {
            return ObjectCursor(myNode!!, this)
        }

    }

    protected class ArrayCursor(node: CirJsonNode, parent: NodeCursor?) : NodeCursor(TYPE_ARRAY, parent) {

        private val myContents = node.elements()

        private var myCurrentElement: CirJsonNode? = null

        override fun nextToken(): CirJsonToken {
            if (!myContents.hasNext()) {
                myCurrentElement = null
                return CirJsonToken.END_ARRAY
            }

            myIndex++
            myCurrentElement = myContents.next()
            return myCurrentElement!!.asToken()
        }

        override fun currentNode(): CirJsonNode? {
            return myCurrentElement
        }

        override fun startArray(): NodeCursor {
            return ArrayCursor(myCurrentElement!!, this)
        }

        override fun startObject(): NodeCursor {
            return ObjectCursor(myCurrentElement!!, this)
        }

    }

    protected class ObjectCursor(node: CirJsonNode, parent: NodeCursor?) : NodeCursor(TYPE_OBJECT, parent) {

        private val myContents = node.fields()

        private var myCurrentEntry: Map.Entry<String, CirJsonNode>? = null

        private var myNeedEntry = true

        override fun nextToken(): CirJsonToken {
            if (!myNeedEntry) {
                myNeedEntry = true
                return myCurrentEntry!!.value.asToken()
            }

            if (!myContents.hasNext()) {
                myCurrentName = null
                myCurrentEntry = null
                return CirJsonToken.END_OBJECT
            }

            myIndex++
            myNeedEntry = false
            myCurrentEntry = myContents.next()
            myCurrentName = myCurrentEntry?.key
            return CirJsonToken.PROPERTY_NAME
        }

        override fun currentNode(): CirJsonNode? {
            return myCurrentEntry?.value
        }

        override fun startArray(): NodeCursor {
            return ArrayCursor(currentNode()!!, this)
        }

        override fun startObject(): NodeCursor {
            return ObjectCursor(currentNode()!!, this)
        }

    }

    companion object {

        fun createRoot(node: CirJsonNode): NodeCursor {
            return RootCursor(node, null)
        }

    }

}