package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.CirJsonNode
import org.cirjson.cirjackson.databind.SerializerProvider

/**
 * This singleton value class is used to contain explicit CirJSON `null` value.
 */
open class NullNode protected constructor() : ValueNode() {

    override val nodeType: CirJsonNodeType
        get() = CirJsonNodeType.NULL

    override fun asToken(): CirJsonToken {
        return CirJsonToken.VALUE_NULL
    }

    override fun asText(defaultValue: String): String {
        return defaultValue
    }

    override fun asText(): String {
        return "null"
    }

    override fun <T : CirJsonNode> requireNonNull(): T {
        return reportRequiredViolation("requireNonNull() called on `NullNode`")
    }

    @Throws(CirJacksonException::class)
    final override fun serialize(generator: CirJsonGenerator, context: SerializerProvider) {
        context.defaultSerializeNullValue(generator)
    }

    override fun equals(other: Any?): Boolean {
        return other is NullNode
    }

    override fun hashCode(): Int {
        return CirJsonNodeType.NULL.ordinal
    }

    companion object {

        val INSTANCE = NullNode()

    }

}