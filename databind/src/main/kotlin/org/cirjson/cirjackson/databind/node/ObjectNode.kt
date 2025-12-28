package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonPointer
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.tree.ObjectTreeNode
import org.cirjson.cirjackson.databind.CirJsonNode
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer

open class ObjectNode : ContainerNode<ObjectNode>, ObjectTreeNode {

    constructor(nodeFactory: CirJsonNodeFactory, children: MutableMap<String, CirJsonNode>) : super(nodeFactory) {
    }

    /*
     *******************************************************************************************************************
     * Support for withArray()/withObject()
     *******************************************************************************************************************
     */

    internal fun internalWithObjectAddTailProperty(tail: CirJsonPointer, preferIndex: Boolean): ObjectNode? {
        TODO("Not yet implemented")
    }

    internal fun internalWithArrayAddTailProperty(tail: CirJsonPointer, preferIndex: Boolean): ArrayNode? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Implementation of core CirJsonNode API
     *******************************************************************************************************************
     */

    override fun asToken(): CirJsonToken {
        TODO("Not yet implemented")
    }

    override operator fun get(index: Int): CirJsonNode? {
        TODO("Not yet implemented")
    }

    override operator fun get(propertyName: String): CirJsonNode? {
        TODO("Not yet implemented")
    }

    override fun path(index: Int): CirJsonNode {
        TODO("Not yet implemented")
    }

    override fun path(propertyName: String): CirJsonNode {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public API, serialization
     *******************************************************************************************************************
     */

    override fun serialize(generator: CirJsonGenerator, serializers: SerializerProvider) {
        TODO("Not yet implemented")
    }

    override fun serialize(generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        TODO("Not yet implemented")
    }

}