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

    /**
     * Method that will return version information stored in and read from jar that contains this class.
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

    /**
     * Convenience method that binds content read using given parser, using configuration of this reader, except that
     * content is bound as CirJSON tree instead of configured root value type. Returns [CirJsonNode] that represents the
     * root of the resulting tree, if there was content to read, or `null` if no more content is accessible via passed
     * [CirJsonParser].
     * 
     * NOTE! Behavior with end-of-input (no more content) differs between this `readTree` method, and all other methods
     * that take input source: latter will return "missing node", NOT `null`
     * 
     * Note: if an object was specified with [withValueToUpdate], it will be ignored.
     */
    @Throws(CirJacksonException::class)
    override fun <T : TreeNode> readTree(parser: CirJsonParser): T? {
        TODO("Not yet implemented")
    }

    override fun writeTree(generator: CirJsonGenerator, tree: TreeNode) {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Deserialization methods; first ones for pre-constructed parsers
     *******************************************************************************************************************
     */

    /**
     * Method that binds content read using given parser, using configuration of this reader, including expected result
     * type. Value return is either newly constructed, or root value that was specified with [withValueToUpdate].
     */
    @Throws(CirJacksonException::class)
    open fun <T : Any> readValue(content: ByteArray): T {
        TODO("Not yet implemented")
    }

    companion object {

        val CIRJSON_NODE_TYPE: KotlinType = SimpleType.constructUnsafe(CirJsonNode::class)

    }

}