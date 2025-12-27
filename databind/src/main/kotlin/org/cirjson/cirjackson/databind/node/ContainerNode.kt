package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.core.CirJsonPointer
import org.cirjson.cirjackson.databind.CirJsonNode
import org.cirjson.cirjackson.databind.util.RawValue
import java.math.BigDecimal
import java.math.BigInteger

/**
 * This intermediate base class is used for all container nodes, specifically, array and object nodes.
 */
abstract class ContainerNode<T : ContainerNode<T>> : BaseCirJsonNode, CirJsonNodeCreator {

    /**
     * We will keep a reference to the Object (usually TreeMapper) that can construct instances of nodes to add to this
     * container node.
     */
    protected val myNodeFactory: CirJsonNodeFactory?

    protected constructor(nodeFactory: CirJsonNodeFactory?) : super() {
        myNodeFactory = nodeFactory
    }

    protected constructor() : super() {
        myNodeFactory = null
    }

    override fun asText(): String {
        return ""
    }

    /*
     *******************************************************************************************************************
     * Methods reset as abstract to force real implementation
     *******************************************************************************************************************
     */

    abstract override val size: Int

    abstract override fun get(index: Int): CirJsonNode?

    abstract override fun get(propertyName: String): CirJsonNode?

    abstract override fun withObject(originalPointer: CirJsonPointer, currentPointer: CirJsonPointer,
            overwriteMode: OverwriteMode, preferIndex: Boolean): ObjectNode?

    /*
     *******************************************************************************************************************
     * CirJsonNodeCreator implementation, Enumerated/singleton types
     *******************************************************************************************************************
     */

    final override fun booleanNode(value: Boolean): ValueNode {
        return myNodeFactory!!.booleanNode(value)
    }

    final override fun missingNode(): CirJsonNode {
        return myNodeFactory!!.missingNode()
    }

    final override fun nullNode(): ValueNode {
        return myNodeFactory!!.nullNode()
    }

    /*
     *******************************************************************************************************************
     * CirJsonNodeCreator implementation, just dispatch to real creator
     *******************************************************************************************************************
     */

    /**
     * Factory method that constructs and returns an empty [ArrayNode]. Construction is done using registered
     * [CirJsonNodeFactory].
     */
    final override fun arrayNode(): ArrayNode {
        return myNodeFactory!!.arrayNode()
    }

    /**
     * Factory method that constructs and returns an [ArrayNode] with an initial capacity. Construction is done using
     * registered [CirJsonNodeFactory]
     * 
     * @param capacity the initial capacity of the ArrayNode
     */
    final override fun arrayNode(capacity: Int): ArrayNode {
        return myNodeFactory!!.arrayNode(capacity)
    }

    /**
     * Factory method that constructs and returns an empty [ObjectNode]. Construction is done using registered
     * [CirJsonNodeFactory].
     */
    final override fun objectNode(): ObjectNode {
        return myNodeFactory!!.objectNode()
    }

    final override fun numberNode(value: Byte?): ValueNode {
        return myNodeFactory!!.numberNode(value)
    }

    final override fun numberNode(value: Short?): ValueNode {
        return myNodeFactory!!.numberNode(value)
    }

    final override fun numberNode(value: Int?): ValueNode {
        return myNodeFactory!!.numberNode(value)
    }

    final override fun numberNode(value: Long?): ValueNode {
        return myNodeFactory!!.numberNode(value)
    }

    final override fun numberNode(value: BigInteger?): ValueNode {
        return myNodeFactory!!.numberNode(value)
    }

    final override fun numberNode(value: Float?): ValueNode {
        return myNodeFactory!!.numberNode(value)
    }

    final override fun numberNode(value: Double?): ValueNode {
        return myNodeFactory!!.numberNode(value)
    }

    final override fun numberNode(value: BigDecimal?): ValueNode {
        return myNodeFactory!!.numberNode(value)
    }

    final override fun textNode(text: String?): ValueNode {
        return myNodeFactory!!.textNode(text)
    }

    final override fun binaryNode(data: ByteArray?): ValueNode {
        return myNodeFactory!!.binaryNode(data)
    }

    final override fun binaryNode(data: ByteArray?, offset: Int, length: Int): ValueNode {
        return myNodeFactory!!.binaryNode(data, offset, length)
    }

    final override fun pojoNode(pojo: Any?): ValueNode {
        return myNodeFactory!!.pojoNode(pojo)
    }

    final override fun rawValueNode(value: RawValue?): ValueNode {
        return myNodeFactory!!.rawValueNode(value)
    }

    /*
     *******************************************************************************************************************
     * Common mutators
     *******************************************************************************************************************
     */

    /**
     * Method for removing all children container has (if any)
     *
     * @return Container node itself (to allow method call chaining)
     */
    abstract fun removeAll(): T

}