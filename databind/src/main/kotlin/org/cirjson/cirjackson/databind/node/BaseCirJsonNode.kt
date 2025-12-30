package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.databind.CirJsonNode
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.exception.CirJsonNodeException
import org.cirjson.cirjackson.databind.util.name
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Abstract base class common to all standard [CirJsonNode] implementations. The main addition here is that we declare
 * that subclasses must implement [org.cirjson.cirjackson.databind.CirJacksonSerializable]. This simplifies object
 * mapping aspects a bit, as no external serializers are needed.
 * 
 * Note that support for [java.io.Serializable] is added here and so all subtypes are fully JDK serializable: but also
 * note that serialization is as CirJSON and should only be used for interoperability purposes where other approaches
 * are not available.
 */
abstract class BaseCirJsonNode protected constructor() : CirJsonNode() {

    /*
     *******************************************************************************************************************
     * Defaulting for introspection
     *******************************************************************************************************************
     */

    override val isEmbeddedValue: Boolean
        get() = false

    /*
     *******************************************************************************************************************
     * Basic definitions for non-container types
     *******************************************************************************************************************
     */

    final override fun findPath(fieldName: String): CirJsonNode {
        return findValue(fieldName) ?: MissingNode
    }

    abstract override fun hashCode(): Int

    /*
     *******************************************************************************************************************
     * Construction
     *******************************************************************************************************************
     */

    override fun required(propertyName: String): CirJsonNode {
        return reportRequiredViolation("Node of type `${this::class.name}` has no fields")
    }

    override fun required(index: Int): CirJsonNode {
        return reportRequiredViolation("Node of type `${this::class.name}` has no indexed values")
    }

    /*
     *******************************************************************************************************************
     * Support for traversal-as-stream
     *******************************************************************************************************************
     */

    override fun traverse(objectReadContext: ObjectReadContext): CirJsonParser {
        return TreeTraversingParser(this, objectReadContext)
    }

    /**
     * Method that can be used for efficient type detection when using stream abstraction for traversing nodes. Will
     * return the first [CirJsonToken] that equivalent stream event would produce (for most nodes there is just one
     * token but for structured/container types multiple)
     */
    abstract override fun asToken(): CirJsonToken

    /**
     * Returns code that identifies type of underlying numeric value, if (and only if) node is a number node.
     */
    override val numberType: CirJsonParser.NumberType?
        get() = null

    /*
     *******************************************************************************************************************
     * withXxx traversal
     *******************************************************************************************************************
     */

    override fun withObject(pointer: CirJsonPointer, overwriteMode: OverwriteMode, preferIndex: Boolean): ObjectNode {
        if (pointer.isMatching) {
            if (this is ObjectNode) {
                return this
            }

            return reportWrongNodeType(
                    "Can only call `withObject()` with empty CirJSON Pointer on `ObjectNode`, not ${this::class.name}")
        }

        return withObject(pointer, pointer, overwriteMode, preferIndex) ?: reportWrongNodeType(
                "Cannot replace context node (of type ${this::class.name}) using `withObject()` with  CirJSON Pointer '$pointer'")
    }

    protected open fun withObject(originalPointer: CirJsonPointer, currentPointer: CirJsonPointer,
            overwriteMode: OverwriteMode, preferIndex: Boolean): ObjectNode? {
        return null
    }

    internal fun internalWithObject(originalPointer: CirJsonPointer, currentPointer: CirJsonPointer,
            overwriteMode: OverwriteMode, preferIndex: Boolean): ObjectNode? {
        return withObject(originalPointer, currentPointer, overwriteMode, preferIndex)
    }

    protected open fun withXxxVerifyReplace(originalPointer: CirJsonPointer, currentPointer: CirJsonPointer,
            overwriteMode: OverwriteMode, preferIndex: Boolean, toReplace: CirJsonNode) {
        if (withXxxMayReplace(toReplace, overwriteMode)) {
            return
        }

        return reportWrongNodeType(
                "Cannot replace `CirJsonNode` of type ${toReplace::class.name} for property \"${currentPointer.matchingProperty}\" in CirJSON Pointer \"$originalPointer\" (mode `OverwriteMode.$overwriteMode`)")
    }

    protected open fun withXxxMayReplace(node: CirJsonNode, overwriteMode: OverwriteMode): Boolean {
        return when (overwriteMode) {
            OverwriteMode.NONE -> false
            OverwriteMode.NULLS -> node.isNull
            OverwriteMode.SCALARS -> !node.isContainerNode
            OverwriteMode.ALL -> true
        }
    }

    override fun withArray(pointer: CirJsonPointer, overwriteMode: OverwriteMode, preferIndex: Boolean): ArrayNode {
        if (pointer.isMatching) {
            if (this is ArrayNode) {
                return this
            }

            return reportWrongNodeType(
                    "Can only call `withArray()` with empty CirJSON Pointer on `ArrayNode`, not ${this::class.name}")
        }

        return withArray(pointer, pointer, overwriteMode, preferIndex) ?: reportWrongNodeType(
                "Cannot replace context node (of type ${this::class.name}) using `withArray()` with  CirJSON Pointer '$pointer'")
    }

    protected open fun withArray(originalPointer: CirJsonPointer, currentPointer: CirJsonPointer,
            overwriteMode: OverwriteMode, preferIndex: Boolean): ArrayNode? {
        return null
    }

    internal fun internalWithArray(originalPointer: CirJsonPointer, currentPointer: CirJsonPointer,
            overwriteMode: OverwriteMode, preferIndex: Boolean): ArrayNode? {
        return withArray(originalPointer, currentPointer, overwriteMode, preferIndex)
    }

    /*
     *******************************************************************************************************************
     * CirJacksonSerializable
     *******************************************************************************************************************
     */

    /**
     * Method called to serialize node instances using given generator.
     */
    @Throws(CirJacksonException::class)
    abstract override fun serialize(generator: CirJsonGenerator, context: SerializerProvider)

    /**
     * Type information is needed, even if JsonNode instances are "plain" CirJSON, since they may be mixed with other
     * types.
     */
    @Throws(CirJacksonException::class)
    abstract override fun serializeWithType(generator: CirJsonGenerator, context: SerializerProvider,
            typeSerializer: TypeSerializer)

    /*
     *******************************************************************************************************************
     * Standard method overrides
     *******************************************************************************************************************
     */

    override fun toString(): String {
        return InternalNodeSerializer.toString(this)
    }

    override fun toPrettyString(): String {
        return InternalNodeSerializer.toPrettyString(this)
    }

    /*
     *******************************************************************************************************************
     * Other helper methods for subtypes
     *******************************************************************************************************************
     */

    /**
     * Helper method that throws [CirJsonNodeException] as a result of this node being of wrong type
     */
    protected open fun <T> reportWrongNodeType(message: String): T {
        throw CirJsonNodeException.from(this, message)
    }

    protected open fun bigIntegerFromBigDecimal(value: BigDecimal): BigInteger {
        StreamReadConstraints.defaults().validateBigIntegerScale(value.scale())
        return value.toBigInteger()
    }

    open fun cirJsonPointerIfValid(expressionOrProperty: String): CirJsonPointer? {
        if (expressionOrProperty.isEmpty() || expressionOrProperty.startsWith('/')) {
            return CirJsonPointer.compile(expressionOrProperty)
        }

        return null
    }

}