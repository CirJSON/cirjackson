package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TreeNode
import org.cirjson.cirjackson.core.tree.ArrayTreeNode
import org.cirjson.cirjackson.databind.CirJsonNode
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer

open class ArrayNode(nodeFactory: CirJsonNodeFactory, private val myChildren: MutableList<CirJsonNode> = ArrayList()) :
        ContainerNode<ArrayNode>(nodeFactory), ArrayTreeNode {

    /*
     *******************************************************************************************************************
     * Implementation of core CirJsonNode API
     *******************************************************************************************************************
     */

    override fun asToken(): CirJsonToken {
        TODO("Not yet implemented")
    }

    override fun get(index: Int): TreeNode? {
        TODO("Not yet implemented")
    }

    override fun get(propertyName: String): TreeNode? {
        TODO("Not yet implemented")
    }

    override fun path(propertyName: String): TreeNode {
        TODO("Not yet implemented")
    }

    override fun path(index: Int): TreeNode {
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