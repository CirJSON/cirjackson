package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.CirJacksonSerializable
import org.cirjson.cirjackson.databind.SerializerProvider

/**
 * Value node that contains a wrapped POJO, to be serialized as a CirJSON constructed through data mapping (usually done
 * by calling [org.cirjson.cirjackson.databind.ObjectMapper]).
 */
open class POJONode(protected val myValue: Any?) : ValueNode() {

    /*
     *******************************************************************************************************************
     * Base class overrides
     *******************************************************************************************************************
     */

    override val nodeType: CirJsonNodeType
        get() = CirJsonNodeType.POJO

    override val isEmbeddedValue: Boolean
        get() = true

    override fun asToken(): CirJsonToken {
        return CirJsonToken.VALUE_EMBEDDED_OBJECT
    }

    /**
     * As it is possible that some implementations embed ByteArray as POJONode (despite optimal being [BinaryNode]),
     * there is support for exposing binary data here too.
     */
    override fun binaryValue(): ByteArray? {
        return myValue as? ByteArray ?: super.binaryValue()
    }

    /*
     *******************************************************************************************************************
     * General type coercions
     *******************************************************************************************************************
     */

    override fun asText(): String {
        return myValue?.toString() ?: "null"
    }

    override fun asText(defaultValue: String): String {
        return myValue?.toString() ?: defaultValue
    }

    override fun asBoolean(defaultValue: Boolean): Boolean {
        return myValue as? Boolean ?: defaultValue
    }

    override fun asInt(defaultValue: Int): Int {
        return (myValue as? Number)?.toInt() ?: defaultValue
    }

    override fun asLong(defaultValue: Long): Long {
        return (myValue as? Number)?.toLong() ?: defaultValue
    }

    override fun asDouble(defaultValue: Double): Double {
        return (myValue as? Number)?.toDouble() ?: defaultValue
    }

    /*
     *******************************************************************************************************************
     * Public API, serialization
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun serialize(generator: CirJsonGenerator, context: SerializerProvider) {
        when (myValue) {
            null -> context.defaultSerializeNullValue(generator)
            is CirJacksonSerializable -> myValue.serialize(generator, context)
            else -> context.writeValue(generator, myValue)
        }
    }

    /*
     *******************************************************************************************************************
     * Extended API
     *******************************************************************************************************************
     */

    /**
     * Method that can be used to access the POJO this node wraps.
     */
    open val pojo: Any?
        get() = myValue

    /*
     *******************************************************************************************************************
     * Overridden standard methods
     *******************************************************************************************************************
     */

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other !is POJONode) {
            return false
        }

        return myValue == other.myValue
    }

    override fun hashCode(): Int {
        return myValue.hashCode()
    }

}