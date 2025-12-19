package org.cirjson.cirjackson.core

import org.cirjson.cirjackson.core.tree.ArrayTreeNode
import org.cirjson.cirjackson.core.tree.ObjectTreeNode

/**
 * Interface that defines objects that can read and write [TreeNode] instances using Streaming API.
 */
interface TreeCodec {

    /*
     *******************************************************************************************************************
     * Factory methods
     *******************************************************************************************************************
     */

    fun createArrayNode(): ArrayTreeNode

    fun createObjectNode(): ObjectTreeNode

    fun booleanNode(boolean: Boolean): TreeNode

    fun stringNode(text: String?): TreeNode

    fun missingNode(): TreeNode

    fun nullNode(): TreeNode

    /*
     *******************************************************************************************************************
     * Read methods
     *******************************************************************************************************************
     */

    fun treeAsTokens(node: TreeNode): CirJsonParser

    @Throws(CirJacksonException::class)
    fun <T : TreeNode> readTree(parser: CirJsonParser): T?

    /*
     *******************************************************************************************************************
     * Write methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    fun writeTree(generator: CirJsonGenerator, tree: TreeNode)

}