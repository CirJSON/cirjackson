package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.CirJsonNode
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer

abstract class ValueNode protected constructor() : BaseCirJsonNode() {

    override fun serialize(generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Navigation methods
     *******************************************************************************************************************
     */

    final override fun get(index: Int): CirJsonNode? {
        TODO("Not yet implemented")
    }

    final override fun path(index: Int): CirJsonNode {
        TODO("Not yet implemented")
    }

    final override fun get(propertyName: String): CirJsonNode? {
        TODO("Not yet implemented")
    }

    final override fun path(propertyName: String): CirJsonNode {
        TODO("Not yet implemented")
    }

}