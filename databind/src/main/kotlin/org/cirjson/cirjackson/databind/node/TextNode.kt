package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.io.NumberInput
import org.cirjson.cirjackson.core.util.ByteArrayBuilder
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.exception.InvalidFormatException
import kotlin.math.max
import kotlin.math.min

/**
 * Value node that contains a text value.
 */
open class TextNode protected constructor(protected val myValue: String) : ValueNode() {

    override val nodeType: CirJsonNodeType
        get() = CirJsonNodeType.STRING

    override fun asToken(): CirJsonToken {
        return CirJsonToken.VALUE_STRING
    }

    override fun textValue(): String? {
        return myValue
    }

    /**
     * Method for accessing textual contents assuming they were base64 encoded; if so, they are decoded and resulting
     * binary data is returned.
     *
     * @throws CirJacksonException if textual contents are not valid Base64 content
     */
    @Throws(CirJacksonException::class)
    open fun getBinaryValue(base64Variant: Base64Variant): ByteArray {
        val string = myValue.trim()
        val initialBlockSize = 4 + (string.length shr 2) * 3
        val builder = ByteArrayBuilder(max(16, min(0x10000, initialBlockSize)))

        try {
            base64Variant.decode(string, builder)
        } catch (e: IllegalArgumentException) {
            throw InvalidFormatException.from(null,
                    "Cannot access contents of TextNode as binary due to broken Base64 encoding: ${e.message}", string,
                    ByteArray::class)
        }

        return builder.toByteArray()
    }

    @Throws(CirJacksonException::class)
    override fun binaryValue(): ByteArray? {
        return getBinaryValue(Base64Variants.defaultVariant)
    }

    /*
     *******************************************************************************************************************
     * General type coercions
     *******************************************************************************************************************
     */

    override fun asText(): String {
        return myValue
    }

    override fun asText(defaultValue: String): String {
        return myValue
    }

    override fun asBoolean(defaultValue: Boolean): Boolean {
        val value = myValue.trim()

        return when (value) {
            "true" -> true
            "false" -> false
            else -> defaultValue
        }
    }

    override fun asInt(defaultValue: Int): Int {
        return NumberInput.parseAsInt(myValue, defaultValue)
    }

    override fun asLong(defaultValue: Long): Long {
        return NumberInput.parseAsLong(myValue, defaultValue)
    }

    override fun asDouble(defaultValue: Double): Double {
        return NumberInput.parseAsDouble(myValue, defaultValue, false)
    }

    /*
     *******************************************************************************************************************
     * Serialization
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun serialize(generator: CirJsonGenerator, context: SerializerProvider) {
        generator.writeString(myValue)
    }

    /*
     *******************************************************************************************************************
     * Overridden standard methods
     *******************************************************************************************************************
     */

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other !is TextNode) {
            return false
        }

        return myValue == other.myValue
    }

    override fun hashCode(): Int {
        return myValue.hashCode()
    }

    companion object {

        internal val EMPTY_STRING_NODE = TextNode("")

        /**
         * Factory method that should be used to construct instances. For some common cases, can reuse canonical
         * instances: currently this is the case for empty Strings, in future possible for others as well. If `null`
         * is passed, will return `null`.
         *
         * @return Resulting [TextNode] object, if `value` is NOT `null`; `null` if it is.
         */
        fun valueFrom(value: String?): TextNode? {
            value ?: return null

            if (value.isEmpty()) {
                return EMPTY_STRING_NODE
            }

            return TextNode(value)
        }

    }

}