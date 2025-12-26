package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.CirJsonPointer
import org.cirjson.cirjackson.core.TreeNode
import org.cirjson.cirjackson.databind.exception.CirJsonNodeException
import org.cirjson.cirjackson.databind.node.CirJsonNodeType
import org.cirjson.cirjackson.databind.node.MissingNode
import org.cirjson.cirjackson.databind.util.emptyIterator

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
 * Note that it is possible to "read" from nodes, using method [traverse], which will result in a [CirJsonParser] being
 * constructed. This can be used for (relatively) efficient conversations between different representations; and it is
 * what core databind uses for methods like [ObjectMapper.treeToValue] and [ObjectMapper.treeAsTokens]
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

    /**
     * Method for accessing value of the specified element of an array node. For other nodes, `null` is always returned.
     * 
     * For array nodes, index specifies exact location within array and allows for efficient iteration over child
     * elements (underlying storage is guaranteed to be efficiently indexable, i.e. has random-access to elements). If
     * index is less than 0, or equal-or-greater than `node.size`, `null` is returned; no exception is thrown for any
     * index.
     * 
     * NOTE: if the element value has been explicitly set as `null` (which is different from removal!), a
     * [org.cirjson.cirjackson.databind.node.NullNode] will be returned, not `null`.
     *
     * @return Node that represent value of the specified element, if this node is an array and has specified element.
     * `null` otherwise.
     */
    abstract override fun get(index: Int): CirJsonNode?

    /**
     * Method for accessing value of the specified field of an object node. If this node is not an object (or it does
     * not have a value for specified field name), or if there is no field with such name, `null` is returned.
     * 
     * NOTE: if the property value has been explicitly set as `null` (which is different from removal!), a
     * [org.cirjson.cirjackson.databind.node.NullNode] will be returned, not `null`.
     *
     * @return Node that represent value of the specified field, if this node is an object and has value for the
     * specified field. `null` otherwise.
     */
    override fun get(propertyName: String): CirJsonNode? {
        return null
    }

    /**
     * This method is similar to [get], except that instead of returning `null` if no such value exists (due to this
     * node not being an object, or object not having value for the specified field), a "missing node" (node that
     * returns `true` for [isMissingNode]) will be returned. This allows for convenient and safe chained access via path
     * calls.
     */
    abstract override fun path(propertyName: String): CirJsonNode

    /**
     * This method is similar to [get], except that instead of returning `null` if no such element exists (due to index
     * being out of range, or this node not being an array), a "missing node" (node that returns `true` for
     * [isMissingNode]) will be returned. This allows for convenient and safe chained access via path calls.
     */
    abstract override fun path(index: Int): CirJsonNode

    override val propertyNames: Iterator<String>
        get() = emptyIterator()

    /**
     * Method for locating node specified by given CirJSON pointer instances. Method will never return `null`; if no
     * matching node exists, will return a node for which [isMissingNode] returns `true`.
     *
     * @return Node that matches given CirJSON Pointer: if no match exists, will return a node for which [isMissingNode]
     * returns `true`.
     */
    final override fun at(pointer: CirJsonPointer): CirJsonNode {
        if (pointer.isMatching) {
            return this
        }

        val node = internalAt(pointer) ?: return MissingNode
        return node.at(pointer.tail!!)
    }

    /**
     * Convenience method that is functionally equivalent to:
     * ```
     * return at(CirJsonPointer.valueOf(pointerExpression))
     * ```
     * 
     * Note that if the same expression is used often, it is preferable to construct [CirJsonPointer] instance once and
     * reuse it: this method will not perform any caching of compiled expressions.
     *
     * @param pointerExpression Expression to compile as a [CirJsonPointer] instance
     *
     * @return Node that matches given CirJSON Pointer: if no match exists, will return a node for which [isMissingNode]
     * returns `true`.
     */
    final override fun at(pointerExpression: String): CirJsonNode {
        return at(CirJsonPointer.compile(pointerExpression))
    }

    /**
     * Helper method used by other methods for traversing the next step of given path expression, and returning matching
     * value node if any. If there is no match, `null` is returned.
     *
     * @param pointer Path expression to use
     *
     * @return Either matching [CirJsonNode] for the first step of path or `null` if no match (including case that this
     * node is not a container)
     */
    protected abstract fun internalAt(pointer: CirJsonPointer): CirJsonNode?

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

    /**
     * Accessor that can be used to check if the node is a wrapper for a POJO ("Plain Old Java Object" aka "bean").
     * Returns `true` only for instances of `POJONode`.
     *
     * @return `true` if this node wraps a POJO
     */
    val isPojo: Boolean
        get() = nodeType == CirJsonNodeType.POJO

    /**
     * @return `true` if this node represents a numeric CirJSON value
     */
    val isNumber: Boolean
        get() = nodeType == CirJsonNodeType.NUMBER

    /**
     *
     * @return `true` if this node represents an integral (integer) numeric CirJSON value
     */
    open val isIntegralNumber: Boolean
        get() = false

    /**
     * @return `true` if this node represents a non-integral numeric CirJSON value
     */
    open val isFloatingPointNumber: Boolean
        get() = false

    /**
     * Accessor that can be used to check whether contained value is a number represented as `Short`. Note, however,
     * that even if this accessor returns `false`, it is possible that conversion would be possible from other numeric
     * types -- to check if this is possible, use [canConvertToInt] instead.
     *
     * @return `true` if the value contained by this node is stored as `Short`
     */
    open val isShort: Boolean
        get() = false

    /**
     * Accessor that can be used to check whether contained value is a number represented as `Int`. Note, however, that
     * even if this accessor returns `false`, it is possible that conversion would be possible from other numeric types
     * -- to check if this is possible, use [canConvertToInt] instead.
     *
     * @return `true` if the value contained by this node is stored as `Int`
     */
    open val isInt: Boolean
        get() = false

    /**
     * Accessor that can be used to check whether contained value is a number represented as `Long`. Note, however, that
     * even if this accessor returns `false`, it is possible that conversion would be possible from other numeric types
     * -- to check if this is possible, use [canConvertToLong] instead.
     *
     * @return `true` if the value contained by this node is stored as `Long`
     */
    open val isLong: Boolean
        get() = false

    open val isFloat: Boolean
        get() = false

    open val isDouble: Boolean
        get() = false

    open val isBigDecimal: Boolean
        get() = false

    open val isBigInteger: Boolean
        get() = false

    /**
     * Accessor that checks whether this node represents basic CirJSON String value.
     */
    val isTextual: Boolean
        get() = nodeType == CirJsonNodeType.STRING

    /**
     * Accessor that can be used to check if this node was created from CirJSON boolean value (literals "true" and
     * "false").
     */
    val isBoolean: Boolean
        get() = nodeType == CirJsonNodeType.BOOLEAN

    /**
     * Accessor that can be used to check if this node was created from CirJSON literal `null` value.
     */
    final override val isNull: Boolean
        get() = nodeType == CirJsonNodeType.NULL

    /**
     * Accessor that can be used to check if this node represents binary data (Base64 encoded). Although this will be
     * externally written as CirJSON String value, [isTextual] will return `false` if this method returns `true`.
     *
     * @return `true` if this node represents base64 encoded binary data
     */
    val isBinary: Boolean
        get() = nodeType == CirJsonNodeType.BINARY

    /*
     *******************************************************************************************************************
     * Public API, container access
     *******************************************************************************************************************
     */

    /**
     * Same as calling [elements]; implemented so that convenience "for-each" loop can be used for looping over elements
     * of CirJSON Array constructs.
     */
    final override fun iterator(): Iterator<CirJsonNode> {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Helper methods,  for subclasses
     *******************************************************************************************************************
     */

    /**
     * Helper method that throws [DatabindException] as a result of violating "required-constraint" for this node (for
     * [required] or related methods).
     */
    protected open fun <T> reportRequiredViolation(message: String) {
        throw CirJsonNodeException.from(this, message)
    }

    protected open fun <T> reportUnsupportedOperation(message: String) {
        throw CirJsonNodeException.from(this, message)
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