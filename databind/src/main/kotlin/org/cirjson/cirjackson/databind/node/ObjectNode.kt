package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonPointer
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.tree.ObjectTreeNode
import org.cirjson.cirjackson.databind.CirJsonNode
import org.cirjson.cirjackson.databind.SerializationFeature
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.configuration.CirJsonNodeFeature
import org.cirjson.cirjackson.databind.util.RawValue
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*

open class ObjectNode : ContainerNode<ObjectNode>, ObjectTreeNode {

    protected val myChildren: MutableMap<String, CirJsonNode>

    constructor(nodeFactory: CirJsonNodeFactory) : super(nodeFactory) {
        myChildren = LinkedHashMap()
    }

    constructor(nodeFactory: CirJsonNodeFactory, children: MutableMap<String, CirJsonNode>) : super(nodeFactory) {
        myChildren = children
    }

    override fun internalAt(pointer: CirJsonPointer): CirJsonNode? {
        return get(pointer.matchingProperty!!)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : CirJsonNode> deepCopy(): T {
        val result = objectNode()

        for ((key, value) in myChildren) {
            result.myChildren[key] = value.deepCopy()
        }

        return result as T
    }

    /*
     *******************************************************************************************************************
     * Support for withArray()/withObject()
     *******************************************************************************************************************
     */

    override fun withObject(expressionOrProperty: String): ObjectNode {
        val pointer = cirJsonPointerIfValid(expressionOrProperty)
        return pointer?.let { withObject(it) } ?: withObjectProperty(expressionOrProperty)
    }

    override fun withObjectProperty(propertyName: String): ObjectNode {
        val child = myChildren[propertyName]

        if (child?.isNull ?: true) {
            return putObject(propertyName)
        }

        if (child.isObject) {
            return child as ObjectNode
        }

        return reportWrongNodeType(
                "Cannot replace `CirJsonNode` of type `${child::class.qualifiedName}` with `ObjectNode` for property \"$propertyName\" (default mode `OverwriteMode.NULLS`)")
    }

    override fun withArray(expressionOrProperty: String): ArrayNode {
        val pointer = cirJsonPointerIfValid(expressionOrProperty)
        return pointer?.let { withArray(it) } ?: withArrayProperty(expressionOrProperty)
    }

    override fun withArrayProperty(propertyName: String): ArrayNode {
        val child = myChildren[propertyName]

        if (child?.isNull ?: true) {
            return putArray(propertyName)
        }

        if (child.isArray) {
            return child as ArrayNode
        }

        return reportWrongNodeType(
                "Cannot replace `CirJsonNode` of type `${child::class.qualifiedName}` with `ArrayNode` for property \"$propertyName\" (default mode `OverwriteMode.NULLS`)")
    }

    override fun withObject(originalPointer: CirJsonPointer, currentPointer: CirJsonPointer,
            overwriteMode: OverwriteMode, preferIndex: Boolean): ObjectNode? {
        if (currentPointer.isMatching) {
            return this
        }

        val node = internalAt(currentPointer)

        if (node !is BaseCirJsonNode) {
            return withObjectAddTailProperty(currentPointer, preferIndex)
        }

        val found = node.internalWithObject(originalPointer, currentPointer.tail!!, overwriteMode, preferIndex)

        if (found != null) {
            return found
        }

        withXxxVerifyReplace(originalPointer, currentPointer, overwriteMode, preferIndex, node)
        return withObjectAddTailProperty(currentPointer, preferIndex)
    }

    override fun withArray(originalPointer: CirJsonPointer, currentPointer: CirJsonPointer,
            overwriteMode: OverwriteMode, preferIndex: Boolean): ArrayNode? {
        if (currentPointer.isMatching) {
            return null
        }

        val node = internalAt(currentPointer)

        if (node !is BaseCirJsonNode) {
            return withArrayAddTailProperty(currentPointer, preferIndex)
        }

        val found = node.internalWithArray(originalPointer, currentPointer.tail!!, overwriteMode, preferIndex)

        if (found != null) {
            return found
        }

        withXxxVerifyReplace(originalPointer, currentPointer, overwriteMode, preferIndex, node)
        return withArrayAddTailProperty(currentPointer, preferIndex)
    }

    protected open fun withObjectAddTailProperty(tail: CirJsonPointer, preferIndex: Boolean): ObjectNode? {
        var realTail = tail
        val propertyName = realTail.matchingProperty!!
        realTail = realTail.tail!!

        if (realTail.isMatching) {
            return putObject(propertyName)
        }

        if (preferIndex && realTail.isMaybeMatchingElement) {
            return putArray(propertyName).internalWithObjectAddTailElement(realTail, true)
        }

        return putObject(propertyName).withObjectAddTailProperty(realTail, preferIndex)
    }

    internal fun internalWithObjectAddTailProperty(tail: CirJsonPointer, preferIndex: Boolean): ObjectNode? {
        return withObjectAddTailProperty(tail, preferIndex)
    }

    protected open fun withArrayAddTailProperty(tail: CirJsonPointer, preferIndex: Boolean): ArrayNode? {
        var realTail = tail
        val propertyName = realTail.matchingProperty!!
        realTail = realTail.tail!!

        if (realTail.isMatching) {
            return putArray(propertyName)
        }

        if (preferIndex && realTail.isMaybeMatchingElement) {
            return putArray(propertyName).internalWithArrayAddTailElement(realTail, true)
        }

        return putObject(propertyName).withArrayAddTailProperty(realTail, preferIndex)
    }

    internal fun internalWithArrayAddTailProperty(tail: CirJsonPointer, preferIndex: Boolean): ArrayNode? {
        return withArrayAddTailProperty(tail, preferIndex)
    }

    /*
     *******************************************************************************************************************
     * Overrides for CirJacksonSerializable.Base
     *******************************************************************************************************************
     */

    override fun isEmpty(serializers: SerializerProvider): Boolean {
        return myChildren.isEmpty()
    }

    /*
     *******************************************************************************************************************
     * Implementation of core CirJsonNode API
     *******************************************************************************************************************
     */

    override val nodeType: CirJsonNodeType
        get() = CirJsonNodeType.OBJECT

    final override val isObject: Boolean
        get() = true

    override fun asToken(): CirJsonToken {
        return CirJsonToken.START_OBJECT
    }

    override val size: Int
        get() = myChildren.size

    override fun isEmpty(): Boolean {
        return myChildren.isEmpty()
    }

    override fun elements(): Iterator<CirJsonNode> {
        return myChildren.values.iterator()
    }

    override operator fun get(index: Int): CirJsonNode? {
        return null
    }

    override operator fun get(propertyName: String): CirJsonNode? {
        return myChildren[propertyName]
    }

    override val propertyNames: Iterator<String>
        get() = myChildren.keys.iterator()

    override fun path(index: Int): CirJsonNode {
        return MissingNode
    }

    override fun path(propertyName: String): CirJsonNode {
        return myChildren[propertyName] ?: MissingNode
    }

    override fun required(propertyName: String): CirJsonNode {
        return myChildren[propertyName] ?: reportRequiredViolation(
                "No value for property '$propertyName' of `ObjectNode`")
    }

    /**
     * Method to use for accessing all properties (with both names and values) of this CirJSON Object.
     */
    override fun fields(): Iterator<Map.Entry<String, CirJsonNode>> {
        return myChildren.entries.iterator()
    }

    /**
     * Method to use for accessing all properties (with both names and values) of this CirJSON Object.
     */
    override fun properties(): Set<Map.Entry<String, CirJsonNode>> {
        return myChildren.entries
    }

    override fun equals(other: CirJsonNode, comparator: Comparator<CirJsonNode>): Boolean {
        if (other !is ObjectNode) {
            return false
        }

        if (myChildren.size != other.myChildren.size) {
            return false
        }

        for (entry in myChildren) {
            val otherValue = other.myChildren[entry.key] ?: return false

            if (!entry.value.equals(otherValue, comparator)) {
                return false
            }
        }

        return true
    }

    /*
     *******************************************************************************************************************
     * Public API, finding value nodes
     *******************************************************************************************************************
     */

    override fun findValue(fieldName: String): CirJsonNode? {
        val node = myChildren[fieldName]

        if (node != null) {
            return node
        }

        for (child in myChildren.values) {
            val value = child.findValue(fieldName) ?: continue
            return value
        }

        return null
    }

    override fun findValues(fieldName: String, foundSoFar: MutableList<CirJsonNode>?): MutableList<CirJsonNode>? {
        var realFoundSoFar = foundSoFar

        for ((key, value) in myChildren) {
            if (fieldName == key) {
                if (realFoundSoFar == null) {
                    realFoundSoFar = ArrayList()
                }

                realFoundSoFar.add(value)
            } else {
                realFoundSoFar = value.findValues(fieldName, realFoundSoFar)
            }
        }

        return realFoundSoFar
    }

    override fun findValuesAsText(fieldName: String, foundSoFar: MutableList<String>?): MutableList<String>? {
        var realFoundSoFar = foundSoFar

        for ((key, value) in myChildren) {
            if (fieldName == key) {
                if (realFoundSoFar == null) {
                    realFoundSoFar = ArrayList()
                }

                realFoundSoFar.add(value.asText())
            } else {
                realFoundSoFar = value.findValuesAsText(fieldName, realFoundSoFar)
            }
        }

        return realFoundSoFar
    }

    override fun findParent(fieldName: String): ObjectNode? {
        val node = myChildren[fieldName]

        if (node != null) {
            return this
        }

        for (child in myChildren.values) {
            val value = child.findParent(fieldName) ?: continue
            return value as ObjectNode
        }

        return null
    }

    override fun findParents(fieldName: String, foundSoFar: MutableList<CirJsonNode>?): MutableList<CirJsonNode>? {
        var realFoundSoFar = foundSoFar

        for ((key, value) in myChildren) {
            if (fieldName == key) {
                if (realFoundSoFar == null) {
                    realFoundSoFar = ArrayList()
                }

                realFoundSoFar.add(this)
            } else {
                realFoundSoFar = value.findParents(fieldName, realFoundSoFar)
            }
        }

        return realFoundSoFar
    }

    /*
     *******************************************************************************************************************
     * Public API, serialization
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun serialize(generator: CirJsonGenerator, serializers: SerializerProvider) {
        if (myChildren.isEmpty()) {
            generator.writeStartObject(this, 0)
            generator.writeEndObject()
            return
        }

        val trimEmptyArray = !serializers.isEnabled(SerializationFeature.WRITE_EMPTY_CIRJSON_ARRAYS)
        val skipNulls = !serializers.isEnabled(CirJsonNodeFeature.WRITE_NULL_PROPERTIES)

        if (trimEmptyArray || skipNulls) {
            generator.writeStartObject(this, myChildren.size)
            serializeFilteredContents(generator, serializers, trimEmptyArray, skipNulls)
            generator.writeEndObject()
            return
        }

        generator.writeStartObject(this, myChildren.size)

        for (entry in contentsToSerialize(serializers)) {
            generator.writeName(entry.key)
            entry.value.serialize(generator, serializers)
        }

        generator.writeEndObject()
    }

    @Throws(CirJacksonException::class)
    override fun serialize(generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        val trimEmptyArray = !serializers.isEnabled(SerializationFeature.WRITE_EMPTY_CIRJSON_ARRAYS)
        val skipNulls = !serializers.isEnabled(CirJsonNodeFeature.WRITE_NULL_PROPERTIES)

        val typeIdDefinition = typeSerializer.writeTypePrefix(generator, serializers,
                typeSerializer.typeId(this, CirJsonToken.START_OBJECT))

        if (trimEmptyArray || skipNulls) {
            serializeFilteredContents(generator, serializers, trimEmptyArray, skipNulls)
            typeSerializer.writeTypeSuffix(generator, serializers, typeIdDefinition)
            return
        }

        for (entry in contentsToSerialize(serializers)) {
            generator.writeName(entry.key)
            entry.value.serialize(generator, serializers)
        }

        typeSerializer.writeTypeSuffix(generator, serializers, typeIdDefinition)
    }

    /**
     * Helper method shared and called by [serialize] in cases where actual filtering is needed based on configuration.
     */
    @Throws(CirJacksonException::class)
    protected open fun serializeFilteredContents(generator: CirJsonGenerator, context: SerializerProvider,
            trimEmptyArray: Boolean, skipNulls: Boolean) {
        for ((key, value) in contentsToSerialize(context)) {
            if (trimEmptyArray && value.isArray && value.isEmpty(context)) {
                continue
            }

            if (skipNulls && value.isNull) {
                continue
            }

            generator.writeName(key)
            value.serialize(generator, context)
        }
    }

    /**
     * Helper method for encapsulating details of accessing child node entries to serialize.
     */
    protected open fun contentsToSerialize(context: SerializerProvider): MutableMap<String, CirJsonNode> {
        if (!context.isEnabled(CirJsonNodeFeature.WRITE_PROPERTIES_SORTED)) {
            return myChildren
        }

        if (myChildren.isNotEmpty() && myChildren !is TreeMap<*, *>) {
            return TreeMap(myChildren)
        }

        return myChildren
    }

    /*
     *******************************************************************************************************************
     * Extended ObjectNode API, mutators
     *******************************************************************************************************************
     */

    /**
     * Method that will set specified property, replacing old value, if any. Note that this is identical to [replace],
     * except for return value.
     *
     * @param propertyName Name of property to set
     * 
     * @param value Value to set property to; if `null`, will be converted to a [NullNode] first (to remove a property,
     * call [remove] instead)
     *
     * @return This node after adding/replacing property value (to allow chaining)
     */
    open fun set(propertyName: String, value: CirJsonNode?): ObjectNode {
        myChildren[propertyName] = value ?: nullNode()
        return this
    }

    /**
     * Method for adding given properties to this object node, overriding any existing values for those properties.
     *
     * @param properties Properties to add
     *
     * @return This node after adding/replacing property values (to allow chaining)
     */
    open fun setAll(properties: Map<String, CirJsonNode?>): ObjectNode {
        for ((key, value) in properties) {
            myChildren[key] = value ?: nullNode()
        }

        return this
    }

    /**
     * Method for adding all properties of the given Object, overriding any existing values for those properties.
     *
     * @param other Object of which properties to add to this object
     *
     * @return This node after addition (to allow chaining)
     */
    open fun setAll(other: ObjectNode): ObjectNode {
        myChildren.putAll(other.myChildren)
        return this
    }

    /**
     * Method for replacing value of specific property with passed value, and returning previous value (or `null` if
     * none).
     *
     * @param propertyName Property of which value to replace
     * 
     * @param value Value to set property to, replacing old value if any
     *
     * @return Old value of the property; `null` if there was no such property with value
     */
    open fun replace(propertyName: String, value: CirJsonNode?): CirJsonNode? {
        return myChildren.put(propertyName, value ?: nullNode())
    }

    /**
     * Method for removing specified field properties out of this ObjectNode.
     *
     * @param propertyNames Names of properties to remove
     *
     * @return This node after removing entries
     */
    open fun without(propertyName: String): ObjectNode {
        myChildren.remove(propertyName)
        return this
    }

    /*
     *******************************************************************************************************************
     * Extended ObjectNode API, mutators, generic
     *******************************************************************************************************************
     */

    /**
     * Method that will set value of specified property if (and only if) it had no set value previously. Note that
     * explicitly set `null` is a value. Functionally equivalent to:
     * ```
     * if (get(propertyName) == null) {
     *     set(propertyName, value ?: nullNode())
     *     return null
     * } else {
     *     return get(propertyName)
     * }
     * ```
     *
     * @param propertyName Name of property to set
     * 
     * @param value Value to set to property (if and only if it had no value previously); if `null`, will be converted
     * to a [NullNode] first.
     *
     * @return Old value of the property, if any (in which case value was not changed); `null` if there was no old value
     * (in which case value is now set)
     */
    open fun putIfAbsent(propertyName: String, value: CirJsonNode?): CirJsonNode? {
        return myChildren.putIfAbsent(propertyName, value ?: nullNode())
    }

    /**
     * Method for removing a property from this `ObjectNode`. Will return previous value of the property, if such
     * property existed; `null` if not.
     *
     * @return Value of specified property, if it existed; `null` if not
     */
    open fun remove(propertyName: String): CirJsonNode? {
        return myChildren.remove(propertyName)
    }

    /**
     * Method for removing specified field properties out of this ObjectNode.
     *
     * @param propertyNames Names of fields to remove
     *
     * @return This node after removing entries
     */
    open fun remove(propertyNames: Collection<String>): ObjectNode {
        myChildren.keys.removeAll(propertyNames)
        return this
    }

    /**
     * Method for removing all properties, such that this ObjectNode will contain no properties after call.
     *
     * @return This node after removing all entries
     */
    override fun removeAll(): ObjectNode {
        myChildren.clear()
        return this
    }

    /**
     * Method for removing all field properties out of this ObjectNode **except** for ones specified in argument.
     *
     * @param propertyNames Fields to **retain** in this ObjectNode
     *
     * @return This node (to allow call chaining)
     */
    open fun retain(propertyNames: Collection<String>): ObjectNode {
        myChildren.keys.retainAll(propertyNames)
        return this
    }

    /**
     * Method for removing all properties out of this ObjectNode **except** for ones specified in argument.
     *
     * @param propertyNames Fields to **retain** in this ObjectNode
     *
     * @return This node (to allow call chaining)
     */
    open fun retain(vararg propertyNames: String): ObjectNode {
        myChildren.keys.retainAll(propertyNames.toSet())
        return this
    }

    /*
     *******************************************************************************************************************
     * Extended ObjectNode API, mutators, typed
     *******************************************************************************************************************
     */

    /**
     * Method that will construct an ArrayNode and add it as a property of this `ObjectNode`, replacing old value, if
     * any.
     * 
     * **NOTE**: Unlike all `put(...)` methods, return value is **NOT** this `ObjectNode`, but the **newly created**
     * `ArrayNode` instance.
     *
     * @return Newly constructed ArrayNode (NOT the old value, which could be of any type, nor `this` node)
     */
    open fun putArray(propertyName: String): ArrayNode {
        return arrayNode().also { putImplementation(propertyName, it) }
    }

    /**
     * Method that will construct an ObjectNode and add it as a property of this `ObjectNode`, replacing old value, if
     * any.
     * 
     * **NOTE**: Unlike all `put(...)` methods, return value is **NOT** this `ObjectNode`, but the **newly created**
     * `ObjectNode` instance.
     *
     * @return Newly constructed ObjectNode (NOT the old value, which could be of any type, nor `this` node)
     */
    open fun putObject(propertyName: String): ObjectNode {
        return objectNode().also { putImplementation(propertyName, it) }
    }

    /**
     * Method for adding an opaque Java value as the value of specified property. Value can be serialized like any other
     * property, as long as CirJackson can serialize it. Despite term "POJO" this allows use of about any type,
     * including [Maps][Map], [Collections][Collection], as well as Beans (POJOs), primitives/wrappers and even
     * [CirJsonNodes][CirJsonNode]. Method is most commonly useful when composing content to serialize from
     * heterogeneous sources.
     * 
     * NOTE: if using [CirJsonNode.toString] (or [CirJsonNode.toPrettyString]) support for serialization may be more
     * limited, compared to serializing node with specifically configured
     * [ObjectMapper][org.cirjson.cirjackson.databind.ObjectMapper].
     *
     * @param propertyName Name of property to set.
     * 
     * @param pojo value to set as the property value
     *
     * @return This `ObjectNode` (to allow chaining)
     */
    open fun putPOJO(propertyName: String, pojo: Any?): ObjectNode {
        return putImplementation(propertyName, pojoNode(pojo))
    }

    open fun putRawValue(propertyName: String, raw: RawValue?): ObjectNode {
        return putImplementation(propertyName, rawValueNode(raw))
    }

    /**
     * Method for setting value of a property to explicit `null` value.
     *
     * @param propertyName Name of property to set.
     *
     * @return This `ObjectNode` (to allow chaining)
     */
    open fun putNull(propertyName: String): ObjectNode {
        return putImplementation(propertyName, nullNode())
    }

    /**
     * Method for setting value of a field to specified numeric value.
     *
     * @return This node (to allow chaining)
     */
    open fun put(propertyName: String, value: Byte?): ObjectNode {
        return putImplementation(propertyName, value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method for setting value of a field to specified numeric value. The underlying [CirJsonNode] that will be added
     * is constructed using [CirJsonNodeFactory.numberNode], and may be "smaller" (like [ByteNode]) in cases where value
     * fits within range of a smaller integral numeric value.
     *
     * @return This node (to allow chaining)
     */
    open fun put(propertyName: String, value: Short?): ObjectNode {
        return putImplementation(propertyName, value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method for setting value of a field to specified numeric value. The underlying [CirJsonNode] that will be added
     * is constructed using [CirJsonNodeFactory.numberNode], and may be "smaller" (like [ShortNode]) in cases where
     * value fits within range of a smaller integral numeric value.
     *
     * @return This node (to allow chaining)
     */
    open fun put(propertyName: String, value: Int?): ObjectNode {
        return putImplementation(propertyName, value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method for setting value of a field to specified numeric value. The underlying [CirJsonNode] that will be added
     * is constructed using [CirJsonNodeFactory.numberNode], and may be "smaller" (like [IntNode]) in cases where value
     * fits within range of a smaller integral numeric value.
     *
     * @return This node (to allow chaining)
     */
    open fun put(propertyName: String, value: Long?): ObjectNode {
        return putImplementation(propertyName, value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method for setting value of a field to specified numeric value.
     *
     * @return This node (to allow chaining)
     */
    open fun put(propertyName: String, value: BigInteger?): ObjectNode {
        return putImplementation(propertyName, value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method for setting value of a field to specified numeric value.
     *
     * @return This node (to allow chaining)
     */
    open fun put(propertyName: String, value: Float?): ObjectNode {
        return putImplementation(propertyName, value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method for setting value of a field to specified numeric value. The underlying [CirJsonNode] that will be added
     * is constructed using [CirJsonNodeFactory.numberNode], and may be "smaller" (like [FloatNode]) in cases where
     * value fits within range of a smaller floating-point numeric value.
     *
     * @return This node (to allow chaining)
     */
    open fun put(propertyName: String, value: Double?): ObjectNode {
        return putImplementation(propertyName, value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method for setting value of a field to specified numeric value.
     *
     * @return This node (to allow chaining)
     */
    open fun put(propertyName: String, value: BigDecimal?): ObjectNode {
        return putImplementation(propertyName, value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method for setting value of a field to specified String value.
     *
     * @return This node (to allow chaining)
     */
    open fun put(propertyName: String, value: String?): ObjectNode {
        return putImplementation(propertyName, value?.let { textNode(it) } ?: nullNode())
    }

    /**
     * Method for setting value of a field to specified boolean value.
     *
     * @return This node (to allow chaining)
     */
    open fun put(propertyName: String, value: Boolean?): ObjectNode {
        return putImplementation(propertyName, value?.let { booleanNode(it) } ?: nullNode())
    }

    /**
     * Method for setting value of a field to specified binary value.
     *
     * @return This node (to allow chaining)
     */
    open fun put(propertyName: String, value: ByteArray?): ObjectNode {
        return putImplementation(propertyName, value?.let { binaryNode(it) } ?: nullNode())
    }

    /*
     *******************************************************************************************************************
     * Standard method overrides
     *******************************************************************************************************************
     */

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other !is ObjectNode) {
            return false
        }

        return childrenEqual(other)
    }

    protected open fun childrenEqual(other: ObjectNode): Boolean {
        return myChildren == other.myChildren
    }

    override fun hashCode(): Int {
        return myChildren.hashCode()
    }

    /*
     *******************************************************************************************************************
     * Internal methods (overridable)
     *******************************************************************************************************************
     */

    protected open fun putImplementation(fieldName: String, value: CirJsonNode): ObjectNode {
        myChildren[fieldName] = value
        return this
    }

}