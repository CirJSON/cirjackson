package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.databind.node.ArrayNode
import org.cirjson.cirjackson.databind.node.ObjectNode
import org.cirjson.cirjackson.databind.type.SimpleType

open class ObjectReader : Versioned, TreeCodec {

    /*
     *******************************************************************************************************************
     * Lifecycle, construction
     *******************************************************************************************************************
     */

    override fun version(): Version {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * TreeCodec implementation
     *******************************************************************************************************************
     */

    override fun createObjectNode(): ObjectNode {
        TODO("Not yet implemented")
    }

    override fun createArrayNode(): ArrayNode {
        TODO("Not yet implemented")
    }

    override fun booleanNode(boolean: Boolean): CirJsonNode {
        TODO("Not yet implemented")
    }

    override fun stringNode(text: String?): CirJsonNode {
        TODO("Not yet implemented")
    }

    override fun missingNode(): CirJsonNode {
        TODO("Not yet implemented")
    }

    override fun nullNode(): CirJsonNode {
        TODO("Not yet implemented")
    }

    override fun treeAsTokens(node: TreeNode): CirJsonParser {
        TODO("Not yet implemented")
    }

    override fun <T : TreeNode> readTree(parser: CirJsonParser): T? {
        TODO("Not yet implemented")
    }

    override fun writeTree(generator: CirJsonGenerator, tree: TreeNode) {
        TODO("Not yet implemented")
    }

    companion object {

        val CIRJSON_NODE_TYPE: KotlinType = SimpleType.constructUnsafe(CirJsonNode::class)

    }

}