package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.databind.CirJsonNode
import org.cirjson.cirjackson.databind.util.RawValue
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Base class that specifies methods for getting access to Node instances (newly constructed, or shared, depending on
 * type), as well as basic implementation of the methods. Designed to be sub-classed if extended functionality
 * (additions to behavior of node types, mostly) is needed.
 * 
 * Note the behavior of "exact BigDecimal value" (aka "strip trailing zeroes of BigDecimal or not"):
 * [org.cirjson.cirjackson.databind.configuration.CirJsonNodeFeature.STRIP_TRAILING_BIG_DECIMAL_ZEROES] setting is used
 * to externally configure this behavior. Note, too, that this factory will no longer handle this normalization (if
 * enabled): caller (like [org.cirjson.cirjackson.databind.deserialization.cirjackson.CirJsonNodeDeserializer]) is
 * expected to handle it.
 */
open class CirJsonNodeFactory : CirJsonNodeCreator {

    /*
     *******************************************************************************************************************
     * Metadata/config access
     *******************************************************************************************************************
     */

    open val maxElementIndexForInsert: Int
        get() = MAX_ELEMENT_INDEX_FOR_INSERT

    /*
     *******************************************************************************************************************
     * Factory methods for literal values
     *******************************************************************************************************************
     */

    /**
     * Factory method for getting an instance of CirJSON boolean value (either literal `true` or `false`)
     */
    override fun booleanNode(value: Boolean): ValueNode {
        return if (value) BooleanNode.TRUE else BooleanNode.FALSE
    }

    override fun missingNode(): CirJsonNode {
        return MissingNode
    }

    /**
     * Factory method for getting an instance of CirJSON `null` node (which represents literal `null` value)
     */
    override fun nullNode(): NullNode {
        return NullNode.INSTANCE
    }

    /*
     *******************************************************************************************************************
     * Factory methods for numeric values
     *******************************************************************************************************************
     */

    /**
     * Factory method for getting an instance of CirJSON numeric value that expresses given 8-bit value, which may be
     * `null`. Due to possibility of `null`, returning type is not guaranteed to be [NumericNode], but just [ValueNode].
     */
    override fun numberNode(value: Byte?): ValueNode {
        return value?.let { ByteNode.valueOf(it) } ?: nullNode()
    }

    /**
     * Factory method for getting an instance of CirJSON numeric value that expresses given 16-bit value, which may be
     * `null`. Due to possibility of `null`, returning type is not guaranteed to be [NumericNode], but just [ValueNode].
     */
    override fun numberNode(value: Short?): ValueNode {
        return value?.let { ShortNode.valueOf(it) } ?: nullNode()
    }

    /**
     * Factory method for getting an instance of CirJSON numeric value that expresses given 32-bit value, which may be
     * `null`. Due to possibility of `null`, returning type is not guaranteed to be [NumericNode], but just [ValueNode].
     */
    override fun numberNode(value: Int?): ValueNode {
        return value?.let { IntNode.valueOf(it) } ?: nullNode()
    }

    /**
     * Factory method for getting an instance of CirJSON numeric value that expresses given 64-bit value, which may be
     * `null`. Due to possibility of `null`, returning type is not guaranteed to be [NumericNode], but just [ValueNode].
     */
    override fun numberNode(value: Long?): ValueNode {
        return value?.let { LongNode.valueOf(it) } ?: nullNode()
    }

    /**
     * Factory method for getting an instance of CirJSON numeric value that expresses given unlimited range integer
     * value
     */
    override fun numberNode(value: BigInteger?): ValueNode {
        return value?.let { BigIntegerNode.valueOf(it) } ?: nullNode()
    }

    /**
     * Factory method for getting an instance of CirJSON numeric value that expresses given 32-bit floating point value,
     * which may be `null`. Due to possibility of `null`, returning type is not guaranteed to be [NumericNode], but just
     * [ValueNode].
     */
    override fun numberNode(value: Float?): ValueNode {
        return value?.let { FloatNode.valueOf(it) } ?: nullNode()
    }

    /**
     * Factory method for getting an instance of CirJSON numeric value that expresses given 64-bit floating point value,
     * which may be `null`. Due to possibility of `null`, returning type is not guaranteed to be [NumericNode], but just
     * [ValueNode].
     */
    override fun numberNode(value: Double?): ValueNode {
        return value?.let { DoubleNode.valueOf(it) } ?: nullNode()
    }

    /**
     * Factory method for getting an instance of CirJSON numeric value that expresses given unlimited precision floating
     * point value
     * 
     * Note that no normalization is performed here; caller may choose to do that, based on
     * [org.cirjson.cirjackson.databind.configuration.CirJsonNodeFeature.STRIP_TRAILING_BIG_DECIMAL_ZEROES] setting.
     */
    override fun numberNode(value: BigDecimal?): ValueNode {
        return value?.let { BigDecimalNode.valueOf(it) } ?: nullNode()
    }

    /*
     *******************************************************************************************************************
     * Factory methods for literal values
     *******************************************************************************************************************
     */

    /**
     * Factory method for constructing a node that represents CirJSON String value, which may be `null`. Due to
     * possibility of `null`, returning type is not guaranteed to be [TextNode], but just [ValueNode].
     */
    override fun textNode(text: String?): ValueNode {
        return TextNode.valueFrom(text) ?: nullNode()
    }

    /**
     * Factory method for constructing a node that represents given binary data, and will get serialized as equivalent
     * base64-encoded String value, which may be `null`. Due to possibility of `null`, returning type is not guaranteed
     * to be [BinaryNode], but just [ValueNode].
     */
    override fun binaryNode(data: ByteArray?): ValueNode {
        return BinaryNode.valueOf(data) ?: nullNode()
    }

    /**
     * Factory method for constructing a node that represents given binary data, and will get serialized as equivalent
     * base64-encoded String value, which may be `null`. Due to possibility of `null`, returning type is not guaranteed
     * to be [BinaryNode], but just [ValueNode].
     */
    override fun binaryNode(data: ByteArray?, offset: Int, length: Int): ValueNode {
        return BinaryNode.valueOf(data, offset, length) ?: nullNode()
    }

    /*
     *******************************************************************************************************************
     * Factory methods for literal values
     *******************************************************************************************************************
     */

    /**
     * Factory method for constructing an empty CirJSON Array node
     */
    override fun arrayNode(): ArrayNode {
        return ArrayNode(this)
    }

    /**
     * Factory method for constructing a CirJSON Array node with an initial capacity
     */
    override fun arrayNode(capacity: Int): ArrayNode {
        return ArrayNode(this, capacity)
    }

    /**
     * Factory method for constructing an empty CirJSON Object ("struct") node
     */
    override fun objectNode(): ObjectNode {
        return ObjectNode(this)
    }

    /**
     * Factory method for constructing a wrapper for POJO objects; these will get serialized using data binding, usually
     * as CirJSON Objects, but in some cases as CirJSON Strings or other node types.
     */
    override fun pojoNode(pojo: Any?): ValueNode {
        return POJONode(pojo)
    }

    override fun rawValueNode(value: RawValue?): ValueNode {
        return POJONode(value)
    }

    companion object {

        /**
         * Constant that defines maximum `CirJsonPointer` element index used for inserts.
         */
        const val MAX_ELEMENT_INDEX_FOR_INSERT = 9999

        /**
         * Default singleton instance that construct "standard" node instances: given that this class is stateless, a
         * globally shared singleton can be used.
         * 
         * Default is to make no changes; no normalization by default.
         */
        val instance = CirJsonNodeFactory()

    }

}