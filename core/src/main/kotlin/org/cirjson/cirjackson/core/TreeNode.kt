package org.cirjson.cirjackson.core

/**
 * Marker interface used to denote CirJSON Tree nodes, as far as the core package knows them (which is very little):
 * mostly needed to allow [ObjectReadContext] and [ObjectWriteContext] to have some level of interoperability. Most
 * functionality is within `CirJsonNode` base class in `databind` package.
 */
interface TreeNode {

    /*
     *******************************************************************************************************************
     * Minimal introspection
     *******************************************************************************************************************
     */

    /**
     * Method that can be used for efficient type detection when using stream abstraction for traversing nodes. Will
     * return the first [CirJsonToken] that equivalent stream event would produce (for most nodes there is just one
     * token but for structured/container types multiple)
     *
     * @return [CirJsonToken] that is most closely associated with the node type
     */
    fun asToken(): CirJsonToken

    /**
     * If this node is a numeric type (as per [CirJsonToken.isNumeric]), returns native type that node uses to store the
     * numeric value; otherwise returns `null`.
     */
    val numberType: CirJsonParser.NumberType?

    /**
     * Accessor that returns number of child nodes this node contains:
     * for Array nodes, number of child elements, for Object nodes,
     * number of properties, and for all other nodes 0.
     */
    val size: Int

    /**
     * Accessor that returns true for all value nodes: ones that are not containers, and that do not represent "missing"
     * nodes in the path. Such value nodes represent String, Number, Boolean and null values from CirJSON.
     *
     * Note: one and only one of methods [isValueNode], [isContainerNode] and [isMissingNode] ever returns true for any
     * given node.
     */
    val isValueNode: Boolean

    /**
     * Accessor that returns true for container nodes: Arrays and Objects.
     *
     * Note: one and only one of methods [isValueNode], [isContainerNode] and [isMissingNode] ever returns true for any
     * given node.
     */
    val isContainerNode: Boolean

    /**
     * Accessor that returns true for "virtual" nodes which represent missing entries constructed by path accessor
     * methods when there is no actual node matching given criteria.
     *
     * Note: one and only one of methods [isValueNode], [isContainerNode] and [isMissingNode] ever returns true for any
     * given node.
     */
    val isMissingNode: Boolean

    /**
     * Accessor that returns `true` if this node is an Array node, `false` otherwise. Note that if `true` is returned,
     * [isContainerNode] must also return `true`.
     */
    val isArray: Boolean

    /**
     * Accessor that returns `true` if this node is an Object node, `false` otherwise. Note that if `true` is returned,
     * [isContainerNode] must also return `true`.
     */
    val isObject: Boolean

    /**
     * Accessor that returns `true` if this node is a node that represents logical `null` value.
     */
    val isNull: Boolean

    /**
     * Accessor that returns `true` if this node represents an embedded "foreign" (or format-specific, native) object
     * (like POJO), not represented as regular content-- ones that streaming api exposes as
     * [CirJsonToken.VALUE_EMBEDDED_OBJECT]. Such nodes are used to pass information that either native format can not
     * express as-is, metadata not included within at all, or something else that requires special handling.
     */
    val isEmbeddedValue: Boolean

    /*
     *******************************************************************************************************************
     * Basic traversal through structured entries (Arrays, Objects)
     *******************************************************************************************************************
     */

    /**
     * Method for accessing value of the specified property of an Object node. If this node is not an Object (or it does
     * not have a value for specified property) `null` is returned.
     *
     * NOTE: handling of explicit null values may vary between implementations; some trees may retain explicit nulls,
     * others not.
     *
     * @param propertyName Name of the property to access
     *
     * @return Node that represent value of the specified property, if this node is an Object and has value for the
     * specified property; `null` otherwise.
     */
    operator fun get(propertyName: String): TreeNode?

    /**
     * Method for accessing value of the specified element of an array node. For other nodes, `null` is returned.
     *
     * For array nodes, index specifies exact location within array and allows for efficient iteration over child
     * elements (underlying storage is guaranteed to be efficiently indexable, i.e. has random-access to elements). If
     * index is less than 0, or equal-or-greater than `node.size`, `null` is returned; no exception is thrown for any
     * index.
     *
     * @param index Index of the Array node element to access
     *
     * @return Node that represent value of the specified element, if this node is an array and has specified element;
     * `null` otherwise.
     */
    operator fun get(index: Int): TreeNode?

    /**
     * Method for accessing value of the specified property of an Object node. For other nodes, a "missing node"
     * (virtual node for which [isMissingNode] returns `true`) is returned.
     *
     * @param propertyName Name of the property to access
     *
     * @return Node that represent value of the specified Object property, if this node is an object and has value for
     * the specified property; otherwise "missing node" is returned.
     */
    fun path(propertyName: String): TreeNode

    /**
     * Method for accessing value of the specified element of an array node. For other nodes, a "missing node" (virtual
     * node for which [isMissingNode] returns `true`) is returned.
     *
     * For array nodes, index specifies exact location within array and allows for efficient iteration over child
     * elements (underlying storage is guaranteed to be efficiently indexable, i.e. has random-access to elements). If
     * index is less than 0, or equal-or-greater than `node.size`, "missing node" is returned; no exception is thrown
     * for any index.
     *
     * @param index Index of the Array node element to access
     *
     * @return Node that represent value of the specified element, if this node is an array and has specified element;
     * otherwise "missing node" is returned.
     */
    fun path(index: Int): TreeNode

    /**
     * Accessor for names of all properties for this node, if (and only if) this node is an Object node. Number of
     * property names accessible will be [size].
     *
     * Returns an iterator for traversing names of all properties this Object node has (if Object node); empty
     * [Iterator] otherwise (never `null`).
     */
    val propertyNames: Iterator<String>

    /**
     * Method for locating node specified by given CirJSON pointer instances. Method will never return `null`; if no
     * matching node exists, will return a node for which [isMissingNode] returns `true`.
     *
     * @param pointer [CirJsonPointer] expression for descendant node to return
     *
     * @return Node that matches given CirJSON Pointer, if any; otherwise, if no match exists, will return a "missing"
     * node (for which [isMissingNode] returns `true`).
     */
    fun at(pointer: CirJsonPointer): TreeNode

    /**
     * Convenience method that is functionally equivalent to:
     * ```
     *   return at(CirJsonPointer.valueOf(pointerExpression))
     * ```
     *
     * Note that if the same expression is used often, it is preferable to construct [CirJsonPointer] instance once and
     * reuse it: this method will not perform any caching of compiled expressions.
     *
     * @param pointerExpression Expression to compile as a [CirJsonPointer] instance
     *
     * @return Node that matches given CirJSON Pointer, if any: otherwise, if no match exists, will return a "missing"
     * node (for which [isMissingNode] returns `true`).
     */
    fun at(pointerExpression: String): TreeNode

}