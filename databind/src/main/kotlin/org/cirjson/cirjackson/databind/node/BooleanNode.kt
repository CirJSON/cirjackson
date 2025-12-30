package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.SerializerProvider

/**
 * This concrete value class is used to contain boolean (`true` / `false`) values. Only two instances are ever created,
 * to minimize memory usage.
 */
class BooleanNode private constructor(private val myValue: Boolean) : ValueNode() {

    /*
     *******************************************************************************************************************
     * CirJsonNode implementation
     *******************************************************************************************************************
     */

    override val nodeType: CirJsonNodeType
        get() = CirJsonNodeType.BOOLEAN

    override fun asToken(): CirJsonToken {
        return if (myValue) CirJsonToken.VALUE_TRUE else CirJsonToken.VALUE_FALSE
    }

    override fun booleanValue(): Boolean {
        return myValue
    }

    override fun asText(): String {
        return myValue.toString()
    }

    override fun asBoolean(): Boolean {
        return myValue
    }

    override fun asBoolean(defaultValue: Boolean): Boolean {
        return myValue
    }

    override fun asInt(defaultValue: Int): Int {
        return if (myValue) 1 else 0
    }

    override fun asLong(defaultValue: Long): Long {
        return if (myValue) 1L else 0L
    }

    override fun asDouble(defaultValue: Double): Double {
        return if (myValue) 1.0 else 0.0
    }

    override fun serialize(generator: CirJsonGenerator,
            context: SerializerProvider) {
        generator.writeBoolean(myValue)
    }

    override fun hashCode(): Int {
        return if (myValue) 3 else 1
    }

    override fun equals(other: Any?): Boolean {
        return this === other
    }

    companion object {

        val TRUE = BooleanNode(true)

        val FALSE = BooleanNode(false)

        fun valueOf(value: Boolean): BooleanNode {
            return if (value) TRUE else FALSE
        }

    }

}