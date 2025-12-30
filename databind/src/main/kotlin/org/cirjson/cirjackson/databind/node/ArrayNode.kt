package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonPointer
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.tree.ArrayTreeNode
import org.cirjson.cirjackson.databind.CirJsonNode
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.exception.CirJsonNodeException
import org.cirjson.cirjackson.databind.util.RawValue
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Node class that represents Arrays mapped from CirJSON content.
 */
open class ArrayNode : ContainerNode<ArrayNode>, ArrayTreeNode {

    private val myChildren: MutableList<CirJsonNode>

    constructor(nodeFactory: CirJsonNodeFactory) : super(nodeFactory) {
        myChildren = ArrayList()
    }

    constructor(nodeFactory: CirJsonNodeFactory, capacity: Int) : super(nodeFactory) {
        myChildren = ArrayList(capacity)
    }

    constructor(nodeFactory: CirJsonNodeFactory, children: MutableList<CirJsonNode>) : super(nodeFactory) {
        myChildren = children
    }

    override fun internalAt(pointer: CirJsonPointer): CirJsonNode? {
        return get(pointer.matchingIndex)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : CirJsonNode> deepCopy(): T {
        val result = arrayNode(myChildren.size)

        for (child in myChildren) {
            result.myChildren.add(child.deepCopy())
        }

        return result as T
    }

    /*
     *******************************************************************************************************************
     * Support for withArray()/withObject()
     *******************************************************************************************************************
     */

    override fun withObject(originalPointer: CirJsonPointer, currentPointer: CirJsonPointer,
            overwriteMode: OverwriteMode, preferIndex: Boolean): ObjectNode? {
        if (currentPointer.isMatching) {
            return null
        }

        val node = internalAt(currentPointer)

        if (node !is BaseCirJsonNode) {
            return withObjectAddTailElement(currentPointer, preferIndex)
        }

        val found = node.internalWithObject(originalPointer, currentPointer.tail!!, overwriteMode, preferIndex)

        if (found != null) {
            return found
        }

        withXxxVerifyReplace(originalPointer, currentPointer, overwriteMode, preferIndex, node)
        return withObjectAddTailElement(currentPointer, preferIndex)
    }

    override fun withArray(originalPointer: CirJsonPointer, currentPointer: CirJsonPointer,
            overwriteMode: OverwriteMode, preferIndex: Boolean): ArrayNode? {
        if (currentPointer.isMatching) {
            return null
        }

        val node = internalAt(currentPointer)

        if (node !is BaseCirJsonNode) {
            return withArrayAddTailElement(currentPointer, preferIndex)
        }

        val found = node.internalWithArray(originalPointer, currentPointer.tail!!, overwriteMode, preferIndex)

        if (found != null) {
            return found
        }

        withXxxVerifyReplace(originalPointer, currentPointer, overwriteMode, preferIndex, node)
        return withArrayAddTailElement(currentPointer, preferIndex)
    }

    protected open fun withObjectAddTailElement(tail: CirJsonPointer, preferIndex: Boolean): ObjectNode? {
        var realTail = tail
        val index = realTail.matchingIndex

        if (index < 0) {
            return null
        }

        realTail = realTail.tail!!

        if (realTail.isMatching) {
            val result = objectNode()
            withXxxSetArrayElement(index, result)
            return result
        }

        if (preferIndex && realTail.isMaybeMatchingElement) {
            val next = arrayNode()
            withXxxSetArrayElement(index, next)
            return next.withObjectAddTailElement(realTail, true)
        }

        val next = objectNode()
        withXxxSetArrayElement(index, next)
        return next.internalWithObjectAddTailProperty(realTail, preferIndex)
    }

    internal fun internalWithObjectAddTailElement(tail: CirJsonPointer, preferIndex: Boolean): ObjectNode? {
        return withObjectAddTailElement(tail, preferIndex)
    }

    protected open fun withArrayAddTailElement(tail: CirJsonPointer, preferIndex: Boolean): ArrayNode? {
        var realTail = tail
        val index = realTail.matchingIndex

        if (index < 0) {
            return null
        }

        realTail = realTail.tail!!

        if (realTail.isMatching) {
            val result = arrayNode()
            withXxxSetArrayElement(index, result)
            return result
        }

        if (preferIndex && realTail.isMaybeMatchingElement) {
            val next = arrayNode()
            withXxxSetArrayElement(index, next)
            return next.withArrayAddTailElement(realTail, true)
        }

        val next = objectNode()
        withXxxSetArrayElement(index, next)
        return next.internalWithArrayAddTailProperty(realTail, preferIndex)
    }

    internal fun internalWithArrayAddTailElement(tail: CirJsonPointer, preferIndex: Boolean): ArrayNode? {
        return withArrayAddTailElement(tail, preferIndex)
    }

    protected open fun withXxxSetArrayElement(index: Int, value: CirJsonNode) {
        if (index < size) {
            set(index, value)
            return
        }

        val max = myNodeFactory!!.maxElementIndexForInsert

        if (index > max) {
            return reportUnsupportedOperation(
                    "Too big Array index ($index; max $max) to use for insert with `CirJsonPointer`")
        }

        while (index >= size) {
            addNull()
        }

        set(index, value)
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
        get() = CirJsonNodeType.ARRAY

    override val isArray: Boolean
        get() = true

    override fun asToken(): CirJsonToken {
        return CirJsonToken.START_ARRAY
    }

    override val size: Int
        get() = myChildren.size

    override fun isEmpty(): Boolean {
        return myChildren.isEmpty()
    }

    override fun elements(): Iterator<CirJsonNode> {
        return myChildren.iterator()
    }

    override operator fun get(index: Int): CirJsonNode? {
        return myChildren.getOrNull(index)
    }

    override operator fun get(propertyName: String): CirJsonNode? {
        return null
    }

    override fun path(propertyName: String): CirJsonNode {
        return MissingNode
    }

    override fun path(index: Int): CirJsonNode {
        return myChildren.getOrNull(index) ?: MissingNode
    }

    override fun required(index: Int): CirJsonNode {
        if (index !in myChildren.indices) {
            return reportRequiredViolation("No value at index #$index [0, ${myChildren.size}) of `ArrayNode`")
        }

        return myChildren[index]
    }

    override fun equals(other: CirJsonNode, comparator: Comparator<CirJsonNode>): Boolean {
        if (other !is ArrayNode) {
            return false
        }

        val length = myChildren.size

        if (length != other.size) {
            return false
        }

        for (i in 0..<length) {
            if (!myChildren[i].equals(other.myChildren[i], comparator)) {
                return false
            }
        }

        return true
    }

    /*
     *******************************************************************************************************************
     * Public API, serialization
     *******************************************************************************************************************
     */

    override fun serialize(generator: CirJsonGenerator, context: SerializerProvider) {
        val size = myChildren.size

        generator.writeStartArray(this, size)

        for (i in 0..<size) {
            myChildren[i].serialize(generator, context)
        }

        generator.writeEndArray()
    }

    override fun serializeWithType(generator: CirJsonGenerator, context: SerializerProvider,
            typeSerializer: TypeSerializer) {
        val typeIdDefinition = typeSerializer.writeTypePrefix(generator, context,
                typeSerializer.typeId(this, CirJsonToken.START_ARRAY))

        for (node in myChildren) {
            node.serialize(generator, context)
        }

        typeSerializer.writeTypeSuffix(generator, context, typeIdDefinition)
    }

    /*
     *******************************************************************************************************************
     * Public API, finding value nodes
     *******************************************************************************************************************
     */

    override fun findValue(fieldName: String): CirJsonNode? {
        for (node in myChildren) {
            val value = node.findValue(fieldName) ?: continue
            return value
        }

        return null
    }

    override fun findValues(fieldName: String, foundSoFar: MutableList<CirJsonNode>?): MutableList<CirJsonNode>? {
        var realFoundSoFar = foundSoFar

        for (node in myChildren) {
            realFoundSoFar = node.findValues(fieldName, realFoundSoFar)
        }

        return realFoundSoFar
    }

    override fun findValuesAsText(fieldName: String, foundSoFar: MutableList<String>?): MutableList<String>? {
        var realFoundSoFar = foundSoFar

        for (node in myChildren) {
            realFoundSoFar = node.findValuesAsText(fieldName, realFoundSoFar)
        }

        return realFoundSoFar
    }

    override fun findParent(fieldName: String): CirJsonNode? {
        for (node in myChildren) {
            val value = node.findParent(fieldName) ?: continue
            return value
        }

        return null
    }

    override fun findParents(fieldName: String, foundSoFar: MutableList<CirJsonNode>?): MutableList<CirJsonNode>? {
        var realFoundSoFar = foundSoFar

        for (node in myChildren) {
            realFoundSoFar = node.findParents(fieldName, realFoundSoFar)
        }

        return realFoundSoFar
    }

    /*
     *******************************************************************************************************************
     * Extended ArrayNode API, accessors
     *******************************************************************************************************************
     */

    /**
     * Method that will set specified element, replacing old value.
     *
     * @param value to set element to; if `null`, will be converted to a [NullNode] first (to remove field entry, call
     * [remove] instead)
     *
     * @return This node after adding/replacing property value (to allow chaining)
     *
     * @throws IndexOutOfBoundsException If Array does not have specified element (that is, index is outside valid range
     * of elements in array)
     */
    open fun set(index: Int, value: CirJsonNode?): ArrayNode {
        if (index !in myChildren.indices) {
            throw IndexOutOfBoundsException("Illegal index $index, array size $size")
        }

        myChildren[index] = value ?: nullNode()
        return this
    }

    /**
     * Method that will set specified element, replacing old value.
     *
     * @param value to set element to; if `null`, will be converted to a [NullNode] first  (to remove field entry, call
     * [remove] instead)
     *
     * @return Old value of the element, if any; `null` if no such element existed.
     *
     * @throws IndexOutOfBoundsException If Array does not have specified element (that is, index is outside valid range
     * of elements in array)
     */
    open fun replace(index: Int, value: CirJsonNode?): CirJsonNode? {
        if (index !in myChildren.indices) {
            throw IndexOutOfBoundsException("Illegal index $index, array size $size")
        }

        return myChildren.set(index, value ?: nullNode())
    }

    /**
     * Method for adding specified node at the end of this array.
     *
     * @return This node, to allow chaining
     */
    open fun add(value: CirJsonNode?): ArrayNode {
        addImplementation(value ?: nullNode())
        return this
    }

    /**
     * Method for adding all child nodes of given Array, appending to child nodes this array contains
     *
     * @param other Array to add contents from
     *
     * @return This node (to allow chaining)
     */
    open fun addAll(other: ArrayNode): ArrayNode {
        myChildren.addAll(other.myChildren)
        return this
    }

    /**
     * Method for adding given nodes as child nodes of this array node.
     *
     * @param nodes Nodes to add
     *
     * @return This node (to allow chaining)
     */
    open fun addAll(nodes: Collection<CirJsonNode>): ArrayNode {
        for (node in nodes) {
            add(node)
        }

        return this
    }

    /**
     * Method for inserting specified child node as an element of this Array. If index is `0` or less, it will be
     * inserted as the first element; if `>= size`, appended at the end, and otherwise inserted before existing element
     * in specified index. No exceptions are thrown for any index.
     *
     * @return This node (to allow chaining)
     */
    open fun insert(index: Int, value: CirJsonNode?): ArrayNode {
        return insertImplementation(index, value ?: nullNode())
    }

    /**
     * Method for removing an entry from this ArrayNode. Will return value of the entry at specified index, if entry
     * existed; `null` if not.
     *
     * @return Node removed, if any; `null` if none
     */
    open fun removeAt(index: Int): CirJsonNode? {
        if (index !in myChildren.indices) {
            return null
        }

        return myChildren.removeAt(index)
    }

    /**
     * Method for removing all elements of this array, leaving the array empty.
     *
     * @return This node (to allow chaining)
     */
    override fun removeAll(): ArrayNode {
        myChildren.clear()
        return this
    }

    /*
     *******************************************************************************************************************
     * Extended ArrayNode API, mutators, generic; addXxx()/insertXxx()/setXxx()
     *******************************************************************************************************************
     */

    /**
     * Method that will construct an ArrayNode and add it at the end of this array node.
     *
     * @return Newly constructed ArrayNode (NOTE: NOT `this` ArrayNode)
     */
    open fun addArray(): ArrayNode {
        return arrayNode().also { addImplementation(it) }
    }

    /**
     * Method that will construct an ObjectNode and add it at the end of this array node.
     *
     * @return Newly constructed ObjectNode (NOTE: NOT `this` ArrayNode)
     */
    open fun addObject(): ObjectNode {
        return objectNode().also { addImplementation(it) }
    }

    /**
     * Method that will construct a POJONode and add it at the end of this array node.
     *
     * @return This array node, to allow chaining
     */
    open fun addPOJO(pojo: Any?): ArrayNode {
        return addImplementation(pojo?.let { pojoNode(it) } ?: nullNode())
    }

    /**
     * @return This array node, to allow chaining
     */
    open fun addRawValue(raw: RawValue?): ArrayNode {
        return addImplementation(raw?.let { rawValueNode(it) } ?: nullNode())
    }

    /**
     * Method that will add a `null` value at the end of this array node.
     *
     * @return This array node, to allow chaining
     */
    open fun addNull(): ArrayNode {
        return addImplementation(nullNode())
    }

    /**
     * Method for adding specified number at the end of this array.
     *
     * @return This array node, to allow chaining
     */
    open fun add(value: Byte?): ArrayNode {
        return addImplementation(value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method for adding specified number at the end of this array.
     *
     * @return This array node, to allow chaining
     */
    open fun add(value: Short?): ArrayNode {
        return addImplementation(value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method for adding specified number at the end of this array.
     *
     * @return This array node, to allow chaining
     */
    open fun add(value: Int?): ArrayNode {
        return addImplementation(value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method for adding specified number at the end of this array.
     *
     * @return This array node, to allow chaining
     */
    open fun add(value: Long?): ArrayNode {
        return addImplementation(value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method for adding specified number at the end of this array.
     *
     * @return This array node, to allow chaining
     */
    open fun add(value: BigInteger?): ArrayNode {
        return addImplementation(value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method for adding specified number at the end of this array.
     *
     * @return This array node, to allow chaining
     */
    open fun add(value: Float?): ArrayNode {
        return addImplementation(value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method for adding specified number at the end of this array.
     *
     * @return This array node, to allow chaining
     */
    open fun add(value: Double?): ArrayNode {
        return addImplementation(value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method for adding specified number at the end of this array.
     *
     * @return This array node, to allow chaining
     */
    open fun add(value: BigDecimal?): ArrayNode {
        return addImplementation(value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method for adding specified String value at the end of this array.
     *
     * @return This array node, to allow chaining
     */
    open fun add(value: String?): ArrayNode {
        return addImplementation(value?.let { textNode(it) } ?: nullNode())
    }

    /**
     * Method for adding specified boolean value at the end of this array.
     *
     * @return This array node, to allow chaining
     */
    open fun add(value: Boolean?): ArrayNode {
        return addImplementation(value?.let { booleanNode(it) } ?: nullNode())
    }

    /**
     * Method for adding specified binary value at the end of this array (note: when serializing as CirJSON, will be
     * output Base64 encoded)
     *
     * @return This array node, to allow chaining
     */
    open fun add(value: ByteArray?): ArrayNode {
        return addImplementation(value?.let { binaryNode(it) } ?: nullNode())
    }

    /**
     * Method for creating an array node, inserting it at the specified point in the array, and returning the **newly
     * created array** (note: NOT 'this' array)
     *
     * @return Newly constructed `ArrayNode` (note! NOT `this` ArrayNode)
     */
    open fun insertArray(index: Int): ArrayNode {
        return arrayNode().also { insertImplementation(index, it) }
    }

    /**
     * Method for creating an [ObjectNode], appending it at the end of this array, and returning the **newly created
     * node** (note: NOT 'this' array)
     *
     * @return Newly constructed `ObjectNode` (note! NOT `this` ArrayNode)
     */
    open fun insertObject(index: Int): ObjectNode {
        return objectNode().also { insertImplementation(index, it) }
    }

    /**
     * Method that will insert a `null` value at specified position in this array.
     *
     * @return This array node, to allow chaining
     */
    open fun insertNull(index: Int): ArrayNode {
        return insertImplementation(index, nullNode())
    }

    /**
     * Method that will construct a POJONode and insert it at specified position in this array.
     *
     * @return This array node, to allow chaining
     */
    open fun insertPOJO(index: Int, pojo: Any?): ArrayNode {
        return insertImplementation(index, pojo?.let { pojoNode(it) } ?: nullNode())
    }

    /**
     * @return This array node, to allow chaining
     */
    open fun insertRawValue(index: Int, raw: RawValue?): ArrayNode {
        return insertImplementation(index, raw?.let { rawValueNode(it) } ?: nullNode())
    }

    /**
     * Method that will insert specified numeric value at specified position in this array.
     *
     * @return This array node, to allow chaining
     */
    open fun insert(index: Int, value: Byte?): ArrayNode {
        return insertImplementation(index, value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method that will insert specified numeric value at specified position in this array.
     *
     * @return This array node, to allow chaining
     */
    open fun insert(index: Int, value: Short?): ArrayNode {
        return insertImplementation(index, value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method that will insert specified numeric value at specified position in this array.
     *
     * @return This array node, to allow chaining
     */
    open fun insert(index: Int, value: Int?): ArrayNode {
        return insertImplementation(index, value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method that will insert specified numeric value at specified position in this array.
     *
     * @return This array node, to allow chaining
     */
    open fun insert(index: Int, value: Long?): ArrayNode {
        return insertImplementation(index, value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method that will insert specified numeric value at specified position in this array.
     *
     * @return This array node, to allow chaining
     */
    open fun insert(index: Int, value: BigInteger?): ArrayNode {
        return insertImplementation(index, value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method that will insert specified numeric value at specified position in this array.
     *
     * @return This array node, to allow chaining
     */
    open fun insert(index: Int, value: Float?): ArrayNode {
        return insertImplementation(index, value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method that will insert specified numeric value at specified position in this array.
     *
     * @return This array node, to allow chaining
     */
    open fun insert(index: Int, value: Double?): ArrayNode {
        return insertImplementation(index, value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method that will insert specified numeric value at specified position in this array.
     *
     * @return This array node, to allow chaining
     */
    open fun insert(index: Int, value: BigDecimal?): ArrayNode {
        return insertImplementation(index, value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method that will insert specified String at specified position in this array.
     *
     * @return This array node, to allow chaining
     */
    open fun insert(index: Int, value: String?): ArrayNode {
        return insertImplementation(index, value?.let { textNode(it) } ?: nullNode())
    }

    /**
     * Method that will insert specified boolean value at specified position in this array.
     *
     * @return This array node, to allow chaining
     */
    open fun insert(index: Int, value: Boolean?): ArrayNode {
        return insertImplementation(index, value?.let { booleanNode(it) } ?: nullNode())
    }

    /**
     * Method that will insert specified binary value at specified position in this array (note: when written as
     * CirJSON, will be Base64 encoded)
     *
     * @return This array node, to allow chaining
     */
    open fun insert(index: Int, value: ByteArray?): ArrayNode {
        return insertImplementation(index, value?.let { binaryNode(it) } ?: nullNode())
    }

    /**
     * @return This node (to allow chaining)
     */
    open fun setNull(index: Int): ArrayNode {
        return setImplementation(index, nullNode())
    }

    /**
     * @return This node (to allow chaining)
     */
    open fun setPOJO(index: Int, pojo: Any?): ArrayNode {
        return setImplementation(index, pojo?.let { pojoNode(it) } ?: nullNode())
    }

    /**
     * @return This node (to allow chaining)
     */
    open fun setRawValue(index: Int, raw: RawValue?): ArrayNode {
        return setImplementation(index, raw?.let { rawValueNode(it) } ?: nullNode())
    }

    /**
     * Method for setting value of a field to specified numeric value.
     *
     * @return This node (to allow chaining)
     */
    open fun set(index: Int, value: Byte?): ArrayNode {
        return setImplementation(index, value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method for setting value of a field to specified numeric value.
     *
     * @return This node (to allow chaining)
     */
    open fun set(index: Int, value: Short?): ArrayNode {
        return setImplementation(index, value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method for setting value of a field to specified numeric value.
     *
     * @return This node (to allow chaining)
     */
    open fun set(index: Int, value: Int?): ArrayNode {
        return setImplementation(index, value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method for setting value of a field to specified numeric value.
     *
     * @return This node (to allow chaining)
     */
    open fun set(index: Int, value: Long?): ArrayNode {
        return setImplementation(index, value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method for setting value of a field to specified numeric value.
     *
     * @return This node (to allow chaining)
     */
    open fun set(index: Int, value: BigInteger?): ArrayNode {
        return setImplementation(index, value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method for setting value of a field to specified numeric value.
     *
     * @return This node (to allow chaining)
     */
    open fun set(index: Int, value: Float?): ArrayNode {
        return setImplementation(index, value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method for setting value of a field to specified numeric value.
     *
     * @return This node (to allow chaining)
     */
    open fun set(index: Int, value: Double?): ArrayNode {
        return setImplementation(index, value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method for setting value of a field to specified numeric value.
     *
     * @return This node (to allow chaining)
     */
    open fun set(index: Int, value: BigDecimal?): ArrayNode {
        return setImplementation(index, value?.let { numberNode(it) } ?: nullNode())
    }

    /**
     * Method for setting value of a field to specified String.
     *
     * @return This node (to allow chaining)
     */
    open fun set(index: Int, value: String?): ArrayNode {
        return setImplementation(index, value?.let { textNode(it) } ?: nullNode())
    }

    /**
     * Method for setting value of a field to specified boolean value.
     *
     * @return This node (to allow chaining)
     */
    open fun set(index: Int, value: Boolean?): ArrayNode {
        return setImplementation(index, value?.let { booleanNode(it) } ?: nullNode())
    }

    /**
     * Method for setting value of a field to specified binary value (note: when written as CirJSON, will be Base64
     * encoded)
     *
     * @return This node (to allow chaining)
     */
    open fun set(index: Int, value: ByteArray?): ArrayNode {
        return setImplementation(index, value?.let { binaryNode(it) } ?: nullNode())
    }

    /*
     *******************************************************************************************************************
     * Standard methods
     *******************************************************************************************************************
     */

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other !is ArrayNode) {
            return false
        }

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

    protected open fun setImplementation(index: Int, node: CirJsonNode): ArrayNode {
        if (index !in myChildren.indices) {
            throw CirJsonNodeException.from(this, "Illegal index $index, array size $size")
        }

        myChildren[index] = node
        return this
    }

    protected open fun addImplementation(node: CirJsonNode): ArrayNode {
        myChildren.add(node)
        return this
    }

    protected open fun insertImplementation(index: Int, node: CirJsonNode): ArrayNode {
        if (index < 0) {
            myChildren.add(0, node)
        } else if (index >= myChildren.size) {
            myChildren.add(node)
        } else {
            myChildren.add(index, node)
        }

        return this
    }

}