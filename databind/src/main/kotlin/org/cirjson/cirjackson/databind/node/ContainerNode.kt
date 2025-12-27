package org.cirjson.cirjackson.databind.node

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
     * CirJsonNodeCreator implementation
     *******************************************************************************************************************
     */

    override fun booleanNode(value: Boolean): ValueNode {
        TODO("Not yet implemented")
    }

    override fun missingNode(): CirJsonNode {
        TODO("Not yet implemented")
    }

    override fun nullNode(): ValueNode {
        TODO("Not yet implemented")
    }

    override fun arrayNode(): ArrayNode {
        TODO("Not yet implemented")
    }

    override fun arrayNode(capacity: Int): ArrayNode {
        TODO("Not yet implemented")
    }

    override fun objectNode(): ObjectNode {
        TODO("Not yet implemented")
    }

    override fun numberNode(value: Byte?): ValueNode {
        TODO("Not yet implemented")
    }

    override fun numberNode(value: Short?): ValueNode {
        TODO("Not yet implemented")
    }

    override fun numberNode(value: Int?): ValueNode {
        TODO("Not yet implemented")
    }

    override fun numberNode(value: Long?): ValueNode {
        TODO("Not yet implemented")
    }

    override fun numberNode(value: BigInteger?): ValueNode {
        TODO("Not yet implemented")
    }

    override fun numberNode(value: Float?): ValueNode {
        TODO("Not yet implemented")
    }

    override fun numberNode(value: Double?): ValueNode {
        TODO("Not yet implemented")
    }

    override fun numberNode(value: BigDecimal?): ValueNode {
        TODO("Not yet implemented")
    }

    override fun textNode(text: String?): ValueNode {
        TODO("Not yet implemented")
    }

    override fun binaryNode(data: ByteArray?): ValueNode {
        TODO("Not yet implemented")
    }

    override fun binaryNode(data: ByteArray?, offset: Int, length: Int): ValueNode {
        TODO("Not yet implemented")
    }

    override fun pojoNode(pojo: Any?): ValueNode {
        TODO("Not yet implemented")
    }

    override fun rawValueNode(value: RawValue?): ValueNode {
        TODO("Not yet implemented")
    }

}