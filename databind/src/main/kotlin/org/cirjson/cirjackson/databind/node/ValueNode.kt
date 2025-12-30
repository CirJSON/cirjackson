package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonPointer
import org.cirjson.cirjackson.databind.CirJsonNode
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer

/**
 * This intermediate base class is used for all leaf nodes, that is, all non-container (array or object) nodes, except
 * for the "missing node".
 */
abstract class ValueNode protected constructor() : BaseCirJsonNode() {

    override fun internalAt(pointer: CirJsonPointer): CirJsonNode? {
        return null
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : CirJsonNode> deepCopy(): T {
        return this as T
    }

    @Throws(CirJacksonException::class)
    override fun serialize(generator: CirJsonGenerator, context: SerializerProvider,
            typeSerializer: TypeSerializer) {
        val typeIdDefinition =
                typeSerializer.writeTypePrefix(generator, context, typeSerializer.typeId(this, asToken()))
        serialize(generator, context)
        typeSerializer.writeTypeSuffix(generator, context, typeIdDefinition)
    }

    /*
     *******************************************************************************************************************
     * Basic property access
     *******************************************************************************************************************
     */

    override fun isEmpty(): Boolean {
        return true
    }

    /*
     *******************************************************************************************************************
     * Navigation methods
     *******************************************************************************************************************
     */

    final override fun get(index: Int): CirJsonNode? {
        return null
    }

    final override fun path(index: Int): CirJsonNode {
        return MissingNode
    }

    final override fun has(index: Int): Boolean {
        return false
    }

    final override fun hasNonNull(index: Int): Boolean {
        return false
    }

    final override fun get(propertyName: String): CirJsonNode? {
        return null
    }

    final override fun path(propertyName: String): CirJsonNode {
        return MissingNode
    }

    final override fun has(fieldName: String): Boolean {
        return false
    }

    final override fun hasNonNull(fieldName: String): Boolean {
        return false
    }

    /*
     *******************************************************************************************************************
     * Find methods: all "leaf" nodes return the same for these
     *******************************************************************************************************************
     */

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

}