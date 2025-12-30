package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonPointer
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.CirJsonNode
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer

/**
 * This singleton node class is generated to denote "missing nodes" along paths that do not exist. For example, if a
 * path via element of an array is requested for an element outside range of elements in the array; or for a non-array
 * value, result will be reference to this node.
 * 
 * In most respects this placeholder node will act as [NullNode]; for example, for purposes of value conversions, value
 * is considered to be `null` and represented as value zero when used for numeric conversions.
 */
object MissingNode : BaseCirJsonNode() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : CirJsonNode> deepCopy(): T {
        return this as T
    }

    override val nodeType: CirJsonNodeType
        get() = CirJsonNodeType.MISSING

    override val isMissingNode: Boolean
        get() = true

    override fun asToken(): CirJsonToken {
        return CirJsonToken.NOT_AVAILABLE
    }

    override fun asText(): String {
        return ""
    }

    override fun asText(defaultValue: String): String {
        return defaultValue
    }

    @Throws(CirJacksonException::class)
    override fun serialize(generator: CirJsonGenerator, context: SerializerProvider) {
        generator.writeNull()
    }

    @Throws(CirJacksonException::class)
    override fun serialize(generator: CirJsonGenerator, context: SerializerProvider,
            typeSerializer: TypeSerializer) {
        generator.writeNull()
    }

    override fun <T : CirJsonNode> require(): T {
        return reportRequiredViolation("require() called on `MissingNode`")
    }

    override fun <T : CirJsonNode> requireNonNull(): T {
        return reportRequiredViolation("requireNonNull() called on `MissingNode`")
    }

    override fun get(index: Int): CirJsonNode? {
        return null
    }

    override fun path(propertyName: String): CirJsonNode {
        return this
    }

    override fun path(index: Int): CirJsonNode {
        return this
    }

    override fun internalAt(pointer: CirJsonPointer): CirJsonNode {
        return this
    }

    override fun findValue(fieldName: String): CirJsonNode? {
        return null
    }

    override fun findParent(fieldName: String): CirJsonNode? {
        return null
    }

    override fun findValues(fieldName: String, foundSoFar: MutableList<CirJsonNode>?): MutableList<CirJsonNode>? {
        return foundSoFar
    }

    override fun findValuesAsText(fieldName: String, foundSoFar: MutableList<String>?): MutableList<String>? {
        return foundSoFar
    }

    override fun findParents(fieldName: String, foundSoFar: MutableList<CirJsonNode>?): MutableList<CirJsonNode>? {
        return foundSoFar
    }

    /*
     *******************************************************************************************************************
     * Standard method overrides
     *******************************************************************************************************************
     */

    override fun equals(other: Any?): Boolean {
        return this === other
    }

    override fun hashCode(): Int {
        return CirJsonNodeType.MISSING.ordinal
    }

    override fun toString(): String {
        return ""
    }

    override fun toPrettyString(): String {
        return ""
    }

}