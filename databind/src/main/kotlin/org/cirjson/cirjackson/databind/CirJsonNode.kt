package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.CirJsonPointer
import org.cirjson.cirjackson.core.TreeNode
import org.cirjson.cirjackson.databind.node.CirJsonNodeType

/**
 * Base class for all CirJSON nodes, which form the basis of CirJSON Tree Model that CirJackson implements. One way to
 * think of these nodes is to consider them similar to DOM nodes in XML DOM trees.
 * 
 * As a general design rule, most accessors ("getters") are included in this base class, to allow for traversing
 * structure without type casts. Most mutators, however, need to be accessed through specific subclasses (such as
 * `ObjectNode` and `ArrayNode`). This seems sensible because proper type information is generally available when
 * building or modifying trees, but less often when reading a tree (newly built from parsed CirJSON content).
 * 
 * Actual concrete subclasses can be found from package [org.cirjson.cirjackson.databind.node].
 * 
 * Note that it is possible to "read" from nodes, using method [TreeNode.traverse], which will result in a
 * [CirJsonParser] being constructed. This can be used for (relatively) efficient conversations between different
 * representations; and it is what core databind uses for methods like [ObjectMapper.treeToValue] and
 * [ObjectMapper.treeAsTokens]
 */
abstract class CirJsonNode protected constructor() : CirJacksonSerializable.Base(), TreeNode, Iterable<CirJsonNode> {

    /*
     *******************************************************************************************************************
     * Construction, related
     *******************************************************************************************************************
     */

    /**
     * Method that can be called to get a node that is guaranteed not to allow changing of this node through mutators on
     * this node or any of its children. This means it can either make a copy of this node (and all mutable children and
     * grand children nodes), or node itself if it is immutable.
     * 
     * Note: return type is guaranteed to have same type as the node method is called on; which is why method is
     * declared with local generic type.
     *
     * @return Node that is either a copy of this node (and all non-leaf children); or, for immutable leaf nodes, node
     * itself.
     */
    abstract fun <T : CirJsonNode> deepCopy(): T

    /*
     *******************************************************************************************************************
     * TreeNode implementation
     *******************************************************************************************************************
     */

    override val size: Int
        get() = 0

    /**
     * Convenience method that is functionally same as:
     * ```
     * size == 0
     * ```
     * for all node types.
     */
    open fun isEmpty(): Boolean {
        return size == 0
    }

    final override val isValueNode: Boolean
        get() = when (nodeType) {
            CirJsonNodeType.ARRAY, CirJsonNodeType.OBJECT, CirJsonNodeType.MISSING -> false
            else -> true
        }

    final override val isContainerNode: Boolean
        get() {
            val type = nodeType
            return type == CirJsonNodeType.OBJECT || type == CirJsonNodeType.ARRAY
        }

    override val isMissingNode: Boolean
        get() = false

    override val isArray: Boolean
        get() = false

    override val isObject: Boolean
        get() = false

    override val propertyNames: Iterator<String>
        get() = TODO("Not yet implemented")

    final override fun at(pointer: CirJsonPointer): TreeNode {
        TODO("Not yet implemented")
    }

    final override fun at(pointerExpression: String): TreeNode {
        TODO("Not yet implemented")
    }

    final override val isNull: Boolean
        get() = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * Public API, type introspection
     *******************************************************************************************************************
     */

    /**
     * Return the type of this node
     *
     * @return the node type as a [CirJsonNodeType] enum value
     */
    abstract val nodeType: CirJsonNodeType

    /*
     *******************************************************************************************************************
     * Public API, container access
     *******************************************************************************************************************
     */

    final override fun iterator(): Iterator<CirJsonNode> {
        TODO("Not yet implemented")
    }

    /**
     * Configuration setting used with [CirJsonNode.withObject] method overrides, to indicate which overwrites are
     * acceptable if the path pointer indicates has incompatible nodes (for example, instead of Object node a Null node
     * is encountered). Overwrite means that the existing value is replaced with compatible type, potentially losing
     * existing values or even subtrees.
     * 
     * Default value if `NULLS` which only allows Null-value nodes to be replaced but no other types.
     */
    enum class OverwriteMode {

        /**
         * Mode in which no values may be overwritten, not even `NullNodes`; only compatible paths may be traversed.
         */
        NONE,

        /**
         * Mode in which explicit `NullNodes` may be replaced but no other node types.
         */
        NULLS,

        /**
         * Mode in which all scalar value nodes may be replaced, but not Array or Object nodes.
         */
        SCALARS,

        /**
         * Mode in which all incompatible node types may be replaced, including Array and Object nodes where necessary.
         */
        ALL

    }

}