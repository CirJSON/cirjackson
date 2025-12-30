package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.core.Base64Variants
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.SerializerProvider

/**
 * Value node that contains Base64 encoded binary value, which will be output and stored as CirJSON String value.
 */
open class BinaryNode : ValueNode {

    protected val myData: ByteArray

    protected constructor(data: ByteArray) : super() {
        myData = data
    }

    protected constructor(data: ByteArray, offset: Int, length: Int) : super() {
        myData = if (offset == 0 && length == data.size) {
            data
        } else {
            data.copyOfRange(offset, offset + length)
        }
    }

    override val nodeType: CirJsonNodeType
        get() = CirJsonNodeType.BINARY

    override fun asToken(): CirJsonToken {
        return CirJsonToken.VALUE_EMBEDDED_OBJECT
    }

    override fun binaryValue(): ByteArray {
        return myData
    }

    /**
     * This is not quite as efficient as using [serialize], but will work correctly.
     */
    override fun asText(): String {
        return Base64Variants.defaultVariant.encode(myData, false)
    }

    @Throws(CirJacksonException::class)
    override fun serialize(generator: CirJsonGenerator, context: SerializerProvider) {
        generator.writeBinary(context.config.base64Variant, myData, 0, myData.size)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other !is BinaryNode) {
            return false
        }

        return myData.contentEquals(other.myData)
    }

    override fun hashCode(): Int {
        return myData.size
    }

    companion object {

        internal val EMPTY_BINARY_NODE = BinaryNode(byteArrayOf())

        fun valueOf(data: ByteArray?): BinaryNode? {
            data ?: return null

            if (data.isEmpty()) {
                return EMPTY_BINARY_NODE
            }

            return BinaryNode(data)
        }

        fun valueOf(data: ByteArray?, offset: Int, length: Int): BinaryNode? {
            data ?: return null

            if (length == 0) {
                return EMPTY_BINARY_NODE
            }

            return BinaryNode(data, offset, length)
        }

    }

}