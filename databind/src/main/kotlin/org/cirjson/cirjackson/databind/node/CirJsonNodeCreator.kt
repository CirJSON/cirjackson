package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.databind.CirJsonNode
import org.cirjson.cirjackson.databind.util.RawValue
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Interface that defines common "creator" functionality implemented both by [CirJsonNodeFactory] and [ContainerNode]
 * (that is, CirJSON Object and Array nodes).
 */
interface CirJsonNodeCreator {

    /*
     *******************************************************************************************************************
     * Enumerated/singleton types
     *******************************************************************************************************************
     */

    fun booleanNode(value: Boolean): ValueNode

    fun nullNode(): ValueNode

    fun missingNode(): CirJsonNode

    /*
     *******************************************************************************************************************
     * Numeric types
     *******************************************************************************************************************
     */

    fun numberNode(value: Byte?): ValueNode

    fun numberNode(value: Short?): ValueNode

    fun numberNode(value: Int?): ValueNode

    fun numberNode(value: Long?): ValueNode

    fun numberNode(value: BigInteger?): ValueNode

    fun numberNode(value: Float?): ValueNode

    fun numberNode(value: Double?): ValueNode

    fun numberNode(value: BigDecimal?): ValueNode

    /*
     *******************************************************************************************************************
     * Textual nodes
     *******************************************************************************************************************
     */

    fun textNode(text: String?): ValueNode

    /*
     *******************************************************************************************************************
     * Other value (non-structured) nodes
     *******************************************************************************************************************
     */

    fun binaryNode(data: ByteArray): ValueNode

    fun binaryNode(data: ByteArray, offset: Int, length: Int): ValueNode

    fun pojoNode(pojo: Any): ValueNode

    /**
     * Factory method to use for adding "raw values"; pre-encoded values that are included exactly as-is when the node
     * is serialized. This may be used, for example, to include fully serialized CirJSON subtrees. Note that the concept
     * may not work with all backends, and since no translation of any kinds is done, it will not work when converting
     * between data formats.
     */
    fun rawValueNode(value: RawValue?): ValueNode

    /*
     *******************************************************************************************************************
     * Structured nodes
     *******************************************************************************************************************
     */

    fun arrayNode(): ArrayNode

    /**
     * Factory method for constructing a CirJSON Array node with an initial capacity
     */
    fun arrayNode(capacity: Int): ArrayNode

    fun objectNode(): ObjectNode

}