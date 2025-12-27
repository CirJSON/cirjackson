package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.databind.CirJsonNode
import org.cirjson.cirjackson.databind.util.RawValue
import java.math.BigDecimal
import java.math.BigInteger

abstract class ContainerNode<T : ContainerNode<T>> protected constructor(
        protected val myNodeFactory: CirJsonNodeFactory) : BaseCirJsonNode(), CirJsonNodeCreator {

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