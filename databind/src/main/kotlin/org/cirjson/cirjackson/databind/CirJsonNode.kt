package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.CirJsonPointer
import org.cirjson.cirjackson.core.TreeNode
import org.cirjson.cirjackson.databind.exception.CirJsonNodeException
import org.cirjson.cirjackson.databind.node.ArrayNode
import org.cirjson.cirjackson.databind.node.CirJsonNodeType
import org.cirjson.cirjackson.databind.node.MissingNode
import org.cirjson.cirjackson.databind.node.ObjectNode
import org.cirjson.cirjackson.databind.util.emptyIterator
import java.math.BigDecimal
import java.math.BigInteger

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
 * Note that it is possible to "read" from nodes, using method [traverse], which will result in a
 * [CirJsonParser][org.cirjson.cirjackson.core.CirJsonParser] being constructed. This can be used for (relatively)
 * efficient conversations between different representations; and it is what core databind uses for methods like
 * [ObjectMapper.treeToValue] and [ObjectMapper.treeAsTokens].
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
     * Accessor that can be used to check whether contained value is a number represented as `Byte`. Note, however,
     * that even if this accessor returns `false`, it is possible that conversion would be possible from other numeric
     * types -- to check if this is possible, use [canConvertToInt] instead.
     *
     * @return `true` if the value contained by this node is stored as `Byte`
     */
    open val isByte: Boolean
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

    /**
     * Method that can be used to check whether this node is a numeric node ([isNumber] would return `true`) AND its
     * value fits within 32-bit signed integer type, `Int`. Note that floating-point numbers are convertible if the
     * integral part fits without overflow (as per standard coercion rules).
     * 
     * NOTE: this method does not consider possible value type conversion from CirJSON String into Number; so even if
     * this method returns `false`, it is possible that [asInt] could still succeed if node is a CirJSON String
     * representing integral number, or boolean.
     */
    open fun canConvertToInt(): Boolean {
        return false
    }

    /**
     * Method that can be used to check whether this node is a numeric node ([isNumber] would return `true`) AND its
     * value fits within 64-bit signed integer type, `Long`. Note that floating-point numbers are convertible if the
     * integral part fits without overflow (as per standard coercion rules).
     * 
     * NOTE: this method does not consider possible value type conversion from CirJSON String into Number; so even if
     * this method returns `false`, it is possible that [asLong] could still succeed if node is a CirJSON String
     * representing integral number, or boolean.
     */
    open fun canConvertToLong(): Boolean {
        return false
    }

    /**
     * Method that can be used to check whether contained value is numeric (returns `true` for [isNumber]) and can be
     * losslessly converted to integral number (specifically, [BigInteger] but potentially others, see [canConvertToInt]
     * and [canConvertToInt]). Latter part allows floating-point numbers (for which [isFloatingPointNumber] returns
     * `true`) that do not have fractional part. Note that "not-a-number" values of `Double` and `Float` will return
     * `false` as they can not be converted to matching integral representations.
     *
     * @return `true` if the value is an actual number with no fractional part; `false` for non-numeric types, NaN
     * representations of floating-point numbers, and floating-point numbers with fractional part.
     */
    open fun canConvertToExactIntegral(): Boolean {
        return isIntegralNumber
    }

    /*
     *******************************************************************************************************************
     * Public API, straight value access
     *******************************************************************************************************************
     */

    /**
     * Method to use for accessing String values. Does **NOT** do any conversions for non-String value nodes; for
     * non-String values (ones for which [isTextual] returns `false`) `null` will be returned. For String values, `null`
     * is never returned (but empty Strings may be)
     *
     * @return Textual value this node contains, iff it is a textual CirJSON node (comes from CirJSON String value
     * entry)
     */
    open fun textValue(): String? {
        return null
    }

    /**
     * Method to use for accessing binary content of binary nodes (nodes for which [isBinary] returns `true`); or for
     * Text Nodes (ones for which [textValue] returns non-`null` value), to read decoded base64 data. For other types of
     * nodes, returns `null`.
     *
     * @return Binary data this node contains, iff it is a binary node; `null` otherwise
     */
    open fun binaryValue(): ByteArray? {
        return null
    }

    /**
     * Method to use for accessing CirJSON boolean values (value literals 'true' and 'false'). For other types, always
     * returns `false`.
     *
     * @return Boolean value this node contains, if any; `false` for non-boolean nodes.
     */
    open fun booleanValue(): Boolean {
        return false
    }

    /**
     * Returns numeric value for this node, **if and only if** this node is numeric ([isNumber] returns `true`);
     * otherwise returns `null`
     *
     * @return Number value this node contains, if any (`null` for non-number nodes).
     */
    open fun numberValue(): Number? {
        return null
    }

    /**
     * Returns 8-bit byte value for this node, **if and only if** this node is numeric ([isNumber] returns `true`).
     * For other types returns `0`. For floating-point numbers, value is truncated using default coercion, similar to
     * how cast from double to byte operates.
     *
     * @return Byte value this node contains, if any; `0` for non-number nodes.
     */
    open fun byteValue(): Byte {
        return 0
    }

    /**
     * Returns 16-bit short value for this node, **if and only if** this node is numeric ([isNumber] returns `true`).
     * For other types returns `0`. For floating-point numbers, value is truncated using default coercion, similar to
     * how cast from double to short operates.
     *
     * @return Short value this node contains, if any; `0` for non-number nodes.
     */
    open fun shortValue(): Short {
        return 0
    }

    /**
     * Returns integer value for this node, **if and only if** this node is numeric ([isNumber] returns `true`). For
     * other types returns `0`. For floating-point numbers, value is truncated using default coercion, similar to how
     * cast from double to int operates.
     *
     * @return Integer value this node contains, if any; `0` for non-number nodes.
     */
    open fun intValue(): Int {
        return 0
    }

    /**
     * Returns 64-bit long value for this node, **if and only if** this node is numeric ([isNumber] returns `true`). For
     * other types returns `0L`. For floating-point numbers, value is truncated using default coercion, similar to how
     * cast from double to long operates.
     *
     * @return Long value this node contains, if any; `0L` for non-number nodes.
     */
    open fun longValue(): Long {
        return 0L
    }

    /**
     * Returns 32-bit floating value for this node, **if and only if** this node is numeric ([isNumber] returns `true`).
     * For other types returns `0.0f`. For integer values, conversion is done using coercion; this means that an
     * overflow is possible for `long` values
     *
     * @return 32-bit float value this node contains, if any; `0.0f` for non-number nodes.
     */
    open fun floatValue(): Float {
        return 0.0f
    }

    /**
     * Returns 64-bit floating point (double) value for this node, **if and only if** this node is numeric ([isNumber]
     * returns `true`). For other types returns `0.0`. For integer values, conversion is done using coercion; this may
     * result in overflows with [BigInteger] values.
     *
     * @return 64-bit double value this node contains, if any; `0.0` for non-number nodes.
     */
    open fun doubleValue(): Double {
        return 0.0
    }

    /**
     * Returns floating point value for this node (as [BigDecimal]), **if and only if** this node is numeric ([isNumber]
     * returns `true`). For other types returns `BigDecimal.ZERO`.
     *
     * @return [BigDecimal] value this node contains, if numeric node; `BigDecimal.ZERO` for non-number nodes.
     */
    open fun bigDecimalValue(): BigDecimal {
        return BigDecimal.ZERO
    }

    /**
     * Returns integer value for this node (as [BigInteger]), **if and only if** this node is numeric ([isNumber]
     * returns `true`). For other types returns `BigInteger.ZERO`.
     * 
     * May also throw [org.cirjson.cirjackson.core.exception.StreamConstraintsException] if the scale of the underlying
     * [BigDecimal] is too large to convert.
     *
     * @return [BigInteger] value this node contains, if numeric node; `BigInteger.ZERO` for non-number nodes.
     */
    open fun bigIntegerValue(): BigInteger {
        return BigInteger.ZERO
    }

    /*
     *******************************************************************************************************************
     * Public API, value access with conversion(s)/coercion(s)
     *******************************************************************************************************************
     */

    /**
     * Method that will return a valid String representation of
     * the container value, if the node is a value node
     * (method [isValueNode] returns `true`),
     * otherwise empty String.
     */
    abstract fun asText(): String

    /**
     * Returns the text value of this node or the provided `defaultValue` if this node does not have a text value.
     * Useful for nodes that are [MissingNode] or [org.cirjson.cirjackson.databind.node.NullNode], ensuring a default
     * value is returned instead of `null` or missing indicators.
     *
     * @param defaultValue The default value to return if this node's text value is absent.
     * 
     * @return The text value of this node, or `defaultValue` if the text value is absent.
     */
    open fun asText(defaultValue: String): String {
        return asText()
    }

    /**
     * Method that will try to convert value of this node to an `Int`. Numbers are coerced using default rules; booleans
     * convert to `0` (`false`) and `1` (`true`), and Strings are parsed using default language integer parsing rules.
     * 
     * If representation cannot be converted to an Int (including structured types like Objects and Arrays), default
     * value of `0` will be returned; no exceptions are thrown.
     */
    open fun asInt(): Int {
        return asInt(0)
    }

    /**
     * Method that will try to convert value of this node to an `Int`. Numbers are coerced using default rules; booleans
     * convert to `0` (`false`) and `1` (`true`), and Strings are parsed using default language integer parsing rules.
     * 
     * If representation cannot be converted to an Int (including structured types like Objects and Arrays), specified
     * `defaultValue` will be returned; no exceptions are thrown.
     */
    open fun asInt(defaultValue: Int): Int {
        return defaultValue
    }

    /**
     * Method that will try to convert value of this node to a `Long`. Numbers are coerced using default rules; booleans
     * convert to `0L` (`false`) and `1L` (`true`), and Strings are parsed using default language integer parsing rules.
     * 
     * If representation cannot be converted to a Long (including structured types like Objects and Arrays), default
     * value of `0L` will be returned; no exceptions are thrown.
     */
    open fun asLong(): Long {
        return asLong(0L)
    }

    /**
     * Method that will try to convert value of this node to a `Long`. Numbers are coerced using default rules; booleans
     * convert to `0L` (`false`) and `1L` (`true`), and Strings are parsed using default language integer parsing rules.
     * 
     * If representation cannot be converted to a Long (including structured types like Objects and Arrays), specified
     * `defaultValue` will be returned; no exceptions are thrown.
     */
    open fun asLong(defaultValue: Long): Long {
        return defaultValue
    }

    /**
     * Method that will try to convert value of this node to a `Double`. Numbers are coerced using default rules;
     * booleans convert to `0.0` (`false`) and `1.0` (`true`), and Strings are parsed using default language
     * floating-point parsing rules.
     * 
     * If representation cannot be converted to a Double (including structured types like Objects and Arrays), default
     * value of `0.0` will be returned; no exceptions are thrown.
     */
    open fun asDouble(): Double {
        return asDouble(0.0)
    }

    /**
     * Method that will try to convert value of this node to a `Double`. Numbers are coerced using default rules;
     * booleans convert to `0.0` (`false`) and `1.0` (`true`), and Strings are parsed using default language
     * floating-point parsing rules.
     * 
     * If representation cannot be converted to a Double (including structured types like Objects and Arrays), specified
     * `defaultValue` will be returned; no exceptions are thrown.
     */
    open fun asDouble(defaultValue: Double): Double {
        return defaultValue
    }

    /**
     * Method that will try to convert value of this node to a `Boolean`. CirJSON booleans map naturally; integer
     * numbers other than `0` map to `true` and `0` maps to `false`, and Strings 'true' and 'false' map to corresponding
     * values.
     * 
     * If representation cannot be converted to a Boolean (including structured types like Objects and Arrays), default
     * value of `false` will be returned; no exceptions are thrown.
     */
    open fun asBoolean(): Boolean {
        return asBoolean(false)
    }

    /**
     * Method that will try to convert value of this node to a `Boolean`. CirJSON booleans map naturally; integer
     * numbers other than `0` map to `true` and `0` maps to `false`, and Strings 'true' and 'false' map to corresponding
     * values.
     * 
     * If representation cannot be converted to a Boolean (including structured types like Objects and Arrays),
     * specified `defaultValue` will be returned; no exceptions are thrown.
     */
    open fun asBoolean(defaultValue: Boolean): Boolean {
        return defaultValue
    }

    /*
     *******************************************************************************************************************
     * Public API, extended traversal with "required()"
     *******************************************************************************************************************
     */

    /**
     * Method that may be called to verify that `this` node is NOT so-called "missing node": that is, one for which
     * [isMissingNode] returns `true`. If not missing node, `this` is returned to allow chaining; otherwise exception is
     * thrown.
     *
     * @return `this` node to allow chaining
     *
     * @throws IllegalArgumentException if this node is "missing node"
     */
    open fun <T : CirJsonNode> require(): T {
        return internalThis()
    }

    /**
     * Method that may be called to verify that `this` node is neither so-called "missing node" (that is, one for which
     * [isMissingNode] returns `true`) nor "null node" (one for which [isNull] returns `true`). If non-`null`
     * non-missing node, `this` is returned to allow chaining; otherwise exception is thrown.
     *
     * @return `this` node to allow chaining
     *
     * @throws IllegalArgumentException if this node is either "missing node" or "null node"
     */
    open fun <T : CirJsonNode> requireNonNull(): T {
        return internalThis()
    }

    /**
     * Method is functionally equivalent to
     * ```
     * path(propertyName).required()
     * ```
     * and can be used to check that this node is an `ObjectNode` (that is, represents CirJSON Object value) and has
     * value for specified property with key `propertyName` (but note that value may be explicit CirJSON `null` value).
     * If this node is Object Node and has value for specified property, matching value is returned; otherwise
     * [IllegalArgumentException] is thrown.
     *
     * @param propertyName Name of property to access
     *
     * @return Value of the specified property of this Object node
     *
     * @throws IllegalArgumentException if this node is not an Object node or if it does not have value for specified
     * property
     */
    open fun required(propertyName: String): CirJsonNode {
        return reportRequiredViolation("Node of type `${this::class.qualifiedName}` has no fields")
    }

    /**
     * Method is functionally equivalent to
     * ```
     * path(index).required()
     * ```
     * and can be used to check that this node is an `ArrayNode` (that is, represents CirJSON Array value) and has value
     * for specified `index` (but note that value may be explicit CirJSON `null` value). If this node is Array Node and
     * has value for specified index, value at index is returned; otherwise [IllegalArgumentException] is thrown.
     *
     * @param index Index of the value of this Array node to access
     *
     * @return Value at specified index of this Array node
     *
     * @throws IllegalArgumentException if this node is not an Array node or if it does not have value for specified
     * index
     */
    open fun required(index: Int): CirJsonNode {
        return reportRequiredViolation("Node of type `${this::class.qualifiedName}` has no indexed values")
    }

    /**
     * Method is functionally equivalent to
     * ```
     *   at(pathExpr).required()
     * ```
     * and can be used to check that there is an actual value node at specified [CirJsonPointer] starting from `this`
     * node (but note that value may be explicit JSON `null` value). If such value node exists it is returned; otherwise
     * [IllegalArgumentException] is thrown.
     *
     * @param pathExpression [CirJsonPointer] expression (as String) to use for finding value node
     *
     * @return Matching value node for given expression
     *
     * @throws IllegalArgumentException if no value node exists at given `CirJSON Pointer` path
     */
    open fun requiredAt(pathExpression: String): CirJsonNode {
        return requiredAt(CirJsonPointer.compile(pathExpression))
    }

    /**
     * Method is functionally equivalent to
     * ```
     * at(path).required()
     * ```
     * and can be used to check that there is an actual value node at specified [CirJsonPointer] starting from `this`
     * node (but note that value may be explicit CirJSON `null` value). If such value node exists it is returned;
     * otherwise [IllegalArgumentException] is thrown.
     *
     * @param path [CirJsonPointer] expression to use for finding value node
     *
     * @return Matching value node for given expression
     *
     * @throws IllegalArgumentException if no value node exists at given `CirJSON Pointer` path
     */
    fun requiredAt(path: CirJsonPointer): CirJsonNode {
        var currentExpression = path
        var current = this

        while (true) {
            if (currentExpression.isMatching) {
                return current
            }

            current = current.internalAt(currentExpression) ?: return reportRequiredViolation(
                    "No node at '$path' (unmatched part: '$currentExpression')")

            currentExpression = currentExpression.tail!!
        }
    }

    /*
     *******************************************************************************************************************
     * Public API, value find / existence check methods
     *******************************************************************************************************************
     */

    /**
     * Method that allows checking whether this node is CirJSON Object node and contains value for specified property.
     * If this is the case (including properties with explicit `null` values), returns `true`; otherwise returns
     * `false`.
     * 
     * This method is equivalent to:
     * ```
     * get(fieldName) != null
     * ```
     * (since return value of get() is node, not value node contains)
     * 
     * NOTE: when explicit `null` values are added, this method will return `true` for such properties.
     *
     * @param fieldName Name of element to check
     *
     * @return `true` if this node is a CirJSON Object node, and has a property entry with specified name (with any
     * value, including `null` value)
     */
    open fun has(fieldName: String): Boolean {
        return get(fieldName) != null
    }

    /**
     * Method that allows checking whether this node is CirJSON Array node and contains a value for specified index If
     * this is the case (including case of specified indexing having `null` as value), returns `true`; otherwise returns
     * `false`.
     * 
     * Note: array element indexes are 0-based.
     * 
     * This method is equivalent to:
     * ```
     * get(index) != null
     * ```
     * 
     * NOTE: this method will return `true` for explicitly added `null` values.
     *
     * @param index Index to check
     *
     * @return `true` if this node is a CirJSON Object node, and has a property entry with specified name (with any
     * value, including `null` value)
     */
    open fun has(index: Int): Boolean {
        return get(index) != null
    }

    /**
     * Method that is similar to [has], but that will return `false` for explicitly added `nulls`.
     * 
     * This method is functionally equivalent to:
     * ```
     * get(fieldName) != null && !get(fieldName).isNull()
     * ```
     */
    open fun hasNonNull(fieldName: String): Boolean {
        return get(fieldName)?.isNull ?: false
    }

    /**
     * Method that is similar to [has], but that will return `false` for explicitly added `nulls`.
     * 
     * This method is equivalent to:
     * ```
     * get(index) != null && !get(index).isNull()
     * ```
     */
    open fun hasNonNull(index: Int): Boolean {
        return get(index)?.isNull ?: false
    }

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
        return elements()
    }

    /**
     * Method for accessing all value nodes of this Node, iff this node is a CirJSON Array or Object node. In case of
     * Object node, field names (keys) are not included, only values. For other types of nodes, returns empty iterator.
     */
    open fun elements(): Iterator<CirJsonNode> {
        return emptyIterator()
    }

    /**
     * @return Iterator that can be used to traverse all key/value pairs for object nodes; empty iterator (no contents)
     * for other types
     */
    open fun fields(): Iterator<Map.Entry<String, CirJsonNode>> {
        return emptyIterator()
    }

    /**
     * Accessor that will return properties of `ObjectNode` similar to how [Map.entries] works; for other node types
     * will return empty [Set].
     *
     * @return Set of properties, if this node is an `ObjectNode` ([isObject] returns `true`); empty [Set] otherwise.
     */
    open fun properties(): Set<Map.Entry<String, CirJsonNode>> {
        return emptySet()
    }

    /*
     *******************************************************************************************************************
     * Public API, find methods
     *******************************************************************************************************************
     */

    /**
     * Method for finding the first CirJSON Object field with specified name in this
     * node or its child nodes, and returning value it has.
     * If no matching field is found in this node or its descendants, returns `null`.
     * 
     * Note that traversal is done in document order (that is, order in which
     * nodes are iterated if using [elements])
     *
     * @param fieldName Name of field to look for
     *
     * @return Value of first matching node found, if any; `null` if none
     */
    abstract fun findValue(fieldName: String): CirJsonNode?

    /**
     * Method for finding JSON Object fields with specified name -- both immediate child values and descendants -- and
     * returning found ones as a [List]. Note that subtree search ends when matching field is found, so possible
     * children of result nodes are **not** included. If no matching fields are found in this node or its descendants,
     * returns an empty List.
     *
     * @param fieldName Name of field to look for
     */
    fun findValues(fieldName: String): List<CirJsonNode> {
        return findValues(fieldName, null) ?: emptyList()
    }

    /**
     * Similar to [findValues], but will additionally convert values into Strings, calling [asText].
     */
    fun findValuesAsText(fieldName: String): List<String> {
        return findValuesAsText(fieldName, null) ?: emptyList()
    }

    /**
     * Method similar to [findValue], but that will return a "missing node" instead of `null` if no field is found.
     * Missing node is a specific kind of node for which [isMissingNode] returns `true`; and all value access methods
     * return empty or missing value.
     *
     * @param fieldName Name of field to look for
     *
     * @return Value of first matching node found; or if not found, a "missing node" (non-`null` instance that has no
     * value)
     */
    abstract fun findPath(fieldName: String): CirJsonNode

    /**
     * Method for finding a CirJSON Object that contains specified field, within this node or its descendants. If no
     * matching field is found in this node or its descendants, returns `null`.
     *
     * @param fieldName Name of field to look for
     *
     * @return Value of first matching node found, if any; `null` if none
     */
    abstract fun findParent(fieldName: String): CirJsonNode?

    /**
     * Method for finding a JSON Object that contains specified field, within this node or its descendants. If no
     * matching field is found in this node or its descendants, returns an empty List.
     *
     * @param fieldName Name of field to look for
     *
     * @return Value of first matching node found, if any; an empty List if none
     */
    fun findParents(fieldName: String): List<CirJsonNode> {
        return findParents(fieldName, null) ?: emptyList()
    }

    abstract fun findValues(fieldName: String, foundSoFar: MutableList<CirJsonNode>?): MutableList<CirJsonNode>?

    abstract fun findValuesAsText(fieldName: String, foundSoFar: MutableList<String>?): MutableList<String>?

    abstract fun findParents(fieldName: String, foundSoFar: MutableList<CirJsonNode>?): MutableList<CirJsonNode>?

    /*
     *******************************************************************************************************************
     * Public API, path handling
     *******************************************************************************************************************
     */

    /**
     * Method that can be called on Object or Array nodes, to access a Object-valued node pointed to by given
     * [CirJsonPointer], if such a node exists: or if not, an attempt is made to create one and return it. For example,
     * on document
     * ```
     * {
     *   "__cirJsonId__": "root",
     *   "a" : {
     *     "__cirJsonId__": "a",
     *     "b" : {
     *       "__cirJsonId__": "b",
     *       "c" : 13
     *     }
     *   }
     * }
     * ```
     * calling method with [CirJsonPointer] of `/a/b` would return [ObjectNode]
     * ```
     *  { "__cirJsonId__": "b", "c" : 13 }
     * ```
     * 
     * In cases where path leads to "missing" nodes, a path is created. So, for example, on above document, and
     * [CirJsonPointer] of `/a/x` an empty [ObjectNode] would be returned and the document would look like:
     * ```
     * {
     *   "__cirJsonId__": "root",
     *   "a" : {
     *     "__cirJsonId__": "a",
     *     "b" : {
     *       "__cirJsonId__": "b",
     *       "c" : 13
     *     },
     *     "x" : { "__cirJsonId__": "x" }
     *   }
     * }
     * ```
     * Finally, if the path is incompatible with the document -- there is an existing `CirJsonNode` through which
     * expression cannot go -- a replacement is attempted if (and only if) conversion is allowed as per `overwriteMode`
     * passed in. For example, with above document and expression of `/a/b/c`, conversion is allowed if passing
     * [OverwriteMode.SCALARS] or [OverwriteMode.ALL], and resulting document would look like:
     * ```
     * {
     *   "__cirJsonId__": "root",
     *   "a" : {
     *     "__cirJsonId__": "a",
     *     "b" : {
     *       "__cirJsonId__": "b",
     *       "c" : { "__cirJsonId__": "c" }
     *     },
     *     "x" : { "__cirJsonId__": "x" }
     *   }
     * }
     * ```
     * but if different modes (`NONE` or `NULLS`) is passed, an exception is thrown instead.
     *
     * @param pointer Pointer that indicates path to use for [ObjectNode] value to return (potentially creating one as
     * necessary)
     * 
     * @param overwriteMode Defines which node types may be converted in case of incompatible `CirJsonPointer`
     * expression: if conversion not allowed, [UnsupportedOperationException] is thrown.
     * 
     * @param preferIndex When creating a path (for empty or replacement), and path contains segment that may be an
     * array index (simple integer number like `3`), whether to construct an [ArrayNode] (`true`) or [ObjectNode]
     * (`false`). In latter case matching property with quoted number (like `"3"`) is used within Object.
     *
     * @return [ObjectNode] found or created
     *
     * @throws CirJsonNodeException if a conversion would be needed for given `CirJsonPointer`, document, but was not
     * allowed for the type encountered
     */
    open fun withObject(pointer: CirJsonPointer, overwriteMode: OverwriteMode, preferIndex: Boolean): ObjectNode {
        return reportUnsupportedOperation(
                "`CirJsonNode` not of type `ObjectNode` (but `${this::class.qualifiedName}`), cannot call `withObject()` on it")
    }

    /**
     * Method that works in one of possible ways, depending on whether `exprOrProperty` is a valid [CirJsonPointer]
     * expression or not (valid expression is either empty String `""` or starts with leading slash `/` character). If
     * it is, works as a short-cut to:
     * ```
     * withObject(CirJsonPointer.compile(expressionOrProperty))
     * ```
     * If it is NOT a valid [CirJsonPointer] expression, value is taken as a literal Object property name and calls is
     * alias for
     * ```
     * withObjectProperty(expressionOrProperty)
     * ```
     *
     * @param expressionOrProperty [CirJsonPointer] expression to use (if valid as one), or, if not (no leading "/"),
     * property name to match.
     *
     * @return [ObjectNode] found or created
     */
    open fun withObject(expressionOrProperty: String): ObjectNode {
        return reportUnsupportedOperation(
                "`CirJsonNode` not of type `ObjectNode` (but `${this::class.qualifiedName}`), cannot call `withObject()` on it")
    }

    /**
     * Shortcut equivalent to:
     * ```
     * withObject(CirJsonPointer.compile(expr), overwriteMode, preferIndex)
     * ```
     */
    fun withObject(expression: String, overwriteMode: OverwriteMode, preferIndex: Boolean): ObjectNode {
        return withObject(CirJsonPointer.compile(expression), overwriteMode, preferIndex)
    }

    /**
     * Same as [withObject] but with defaults of [OverwriteMode.NULLS] (overwrite mode) and `true` for `preferIndex`
     * (that is, will try to consider [CirJsonPointer] segments index if at all possible and only secondarily as
     * property name
     *
     * @param pointer [CirJsonPointer] that indicates path to use for Object value to return (potentially creating as
     * necessary)
     *
     * @return [ObjectNode] found or created
     */
    fun withObject(pointer: CirJsonPointer): ObjectNode {
        return withObject(pointer, OverwriteMode.NULLS, true)
    }

    /**
     * Method similar to [withObject] -- basically short-cut to:
     * ```
     * withObject(CirJsonPointer.compile("/$propertyName"), OverwriteMode.NULLS, false)
     * ```
     * that is, only matches immediate property on [ObjectNode] and will either use an existing [ObjectNode] that is
     * value of the property, or create one if no value or value is `NullNode`.
     * 
     * Will fail with an exception if:
     * 
     * * Node method called on is NOT [ObjectNode]
     *  
     * * Property has an existing value that is NOT `NullNode` (explicit `null`)
     *
     * @param propertyName Name of property that has or will have [ObjectNode] as value
     *
     * @return [ObjectNode] value of given property (existing or created)
     */
    open fun withObjectProperty(propertyName: String): ObjectNode {
        return reportUnsupportedOperation(
                "`CirJsonNode` not of type `ObjectNode` (but `${this::class.qualifiedName}`), cannot call `withObjectProperty()` on it")
    }

    /**
     * Method that can be called on Object or Array nodes, to access a Array-valued node pointed to by given
     * [CirJsonPointer], if such a node exists: or if not, an attempt is made to create one and return it. For example,
     * on document
     * ```
     * {
     *   "__cirJsonId__": "root",
     *   "a" : {
     *     "__cirJsonId__": "a",
     *     "b" : [ "b", 1, 2 ]
     *   }
     * }
     * ```
     * calling method with [CirJsonPointer] of `/a/b` would return `Array`
     * ```
     * [ "b", 1, 2 ]
     * ```
     * 
     * In cases where path leads to "missing" nodes, a path is created. So, for example, on above document, and
     * [CirJsonPointer] of `/a/x` an empty `ArrayNode` would be returned and the document would look like:
     * ```
     * {
     *   "__cirJsonId__": "root",
     *   "a" : {
     *     "__cirJsonId__": "a",
     *     "b" : [ "b", 1, 2 ],
     *     "x" : [ "x" ]
     *   }
     * }
     * ```
     * Finally, if the path is incompatible with the document -- there is an existing `CirJsonNode` through which
     * expression cannot go -- a replacement is attempted if (and only if) conversion is allowed as per `overwriteMode`
     * passed in. For example, with above document and expression of `/a/b/0`, conversion is allowed if passing
     * [OverwriteMode.SCALARS] or [OverwriteMode.ALL], and resulting document would look like:
     * ```
     * { "a" : {
     *      "b" : [ "b", [ "0" ], 2 ],
     *      "x" : [ "x" ]
     *   }
     * }
     * ```
     * but if different modes (`NONE` or `NULLS`) is passed, an exception is thrown instead.
     *
     * @param pointer Pointer that indicates path to use for [ArrayNode] value to return (potentially creating it as
     * necessary)
     * 
     * @param overwriteMode Defines which node types may be converted in case of incompatible `CirJsonPointer`
     * expression: if conversion not allowed, an exception is thrown.
     * 
     * @param preferIndex When creating a path (for empty or replacement), and path contains segment that may be an
     * array index (simple integer number like `3`), whether to construct an [ArrayNode] (`true`) or [ObjectNode]
     * (`false`). In latter case matching property with quoted number (like `"3"`) is used within Object.
     *
     * @return [ArrayNode] found or created
     *
     * @throws CirJsonNodeException if a conversion would be needed for given `CirJsonPointer`, document, but was not
     * allowed for the type encountered
     */
    open fun withArray(pointer: CirJsonPointer, overwriteMode: OverwriteMode, preferIndex: Boolean): ArrayNode {
        return reportUnsupportedOperation(
                "`CirJsonNode` not of type `ArrayNode` (but `${this::class.qualifiedName}`), cannot call `withArray()` on it")
    }

    /**
     * Shortcut equivalent to:
     * ```
     * withArray(CirJsonPointer.compile(expr))
     * ```
     */
    open fun withArray(expressionOrProperty: String): ArrayNode {
        return reportUnsupportedOperation(
                "`CirJsonNode` not of type `ArrayNode` (but `${this::class.qualifiedName}`), cannot call `withArray()` on it")
    }

    /**
     * Short-cut equivalent to:
     * ```
     * withArray(CirJsonPointer.compile(expr), overwriteMode, preferIndex)
     * ```
     */
    fun withArray(expression: String, overwriteMode: OverwriteMode, preferIndex: Boolean): ArrayNode {
        return withArray(CirJsonPointer.compile(expression), overwriteMode, preferIndex)
    }

    /**
     * Same as [withArray] but with defaults of [OverwriteMode.NULLS] (overwrite mode) and `true` for `preferIndex`.
     *
     * @param pointer Pointer that indicates path to use for [ArrayNode] to return (potentially creating as necessary)
     *
     * @return [ArrayNode] found or created
     */
    fun withArray(pointer: CirJsonPointer): ArrayNode {
        return withArray(pointer, OverwriteMode.NULLS, true)
    }

    /**
     * Method similar to [withArray] -- basically shortcut to:
     * ```
     * withArray(CirJsonPointer.compile("/$propertyName"), OverwriteMode.NULLS, false)
     * ```
     * that is, only matches immediate property on [ObjectNode] and will either use an existing [ArrayNode] that is
     * value of the property, or create one if no value or value is `NullNode`.
     * 
     * Will fail with an exception if:
     * 
     * * Node method called on is NOT [ObjectNode]
     *  
     * * Property has an existing value that is NOT `NullNode` (explicit `null`)
     *
     * @param propertyName Name of property that has or will have [ArrayNode] as value
     *
     * @return [ArrayNode] value of given property (existing or created)
     */
    open fun withArrayProperty(propertyName: String): ArrayNode {
        return reportUnsupportedOperation(
                "`CirJsonNode` not of type `ArrayNode` (but `${this::class.qualifiedName}`), cannot call `withArray()` on it")
    }

    /*
     *******************************************************************************************************************
     * Public API, comparison
     *******************************************************************************************************************
     */

    /**
     * Entry method for invoking customizable comparison, using passed-in [Comparator] object. Nodes will handle
     * traversal of structured types (arrays, objects), but defer to comparator for scalar value comparisons. If a
     * "natural" [Comparator] is passed -- one that simply calls `equals()` on one of arguments, passing the other --
     * implementation is the same as directly calling `equals()` on node.
     * 
     * Default implementation simply delegates to passed in `comparator`, with `this` as the first argument, and `other`
     * as the second argument.
     *
     * @param comparator Object called to compare two scalar [CirJsonNode] instances, and return either `0` (are equals)
     * or non-zero (not equal)
     */
    open fun equals(other: CirJsonNode, comparator: Comparator<CirJsonNode>): Boolean {
        return comparator.compare(this, other) == 0
    }

    /*
     *******************************************************************************************************************
     * Overridden standard methods
     *******************************************************************************************************************
     */

    /**
     * Method that will produce valid CirJSON using default settings of databind, as String. If you want other kinds of
     * CirJSON output (or output formatted using one of other CirJackson-supported data formats) make sure to use
     * [ObjectMapper] or [ObjectWriter] to serialize an instance, for example:
     * ```
     * val cirjson = objectMapper.writeValueAsString(rootNode)
     * ```
     * 
     * Note: method defined as abstract to ensure all implementation classes explicitly implement method, instead of
     * relying on [Any.toString] definition.
     */
    abstract override fun toString(): String

    /**
     * Alternative to [toString] that will serialize this node using CirJackson default pretty-printer.
     */
    open fun toPrettyString(): String {
        return toString()
    }

    /**
     * Equality for node objects is defined as full (deep) value equality. This means that it is possible to compare
     * complete CirJSON trees for equality by comparing equality of root nodes.
     * 
     * Note: marked as abstract to ensure all implementation classes define it properly and not rely on definition from
     * [Any].
     */
    abstract override fun equals(other: Any?): Boolean

    /*
     *******************************************************************************************************************
     * Helper methods, for subclasses
     *******************************************************************************************************************
     */

    @Suppress("UNCHECKED_CAST")
    protected open fun <T : CirJsonNode> internalThis(): T {
        return this as T
    }

    /**
     * Helper method that throws [DatabindException] as a result of violating "required-constraint" for this node (for
     * [required] or related methods).
     */
    protected open fun <T> reportRequiredViolation(message: String): T {
        throw CirJsonNodeException.from(this, message)
    }

    protected open fun <T> reportUnsupportedOperation(message: String): T {
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