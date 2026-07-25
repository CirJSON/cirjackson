package org.cirjson.cirjackson.databind.deserialization.cirjackson

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.configuration.CirJsonNodeFeature
import org.cirjson.cirjackson.databind.deserialization.standard.StandardDeserializer
import org.cirjson.cirjackson.databind.node.ArrayNode
import org.cirjson.cirjackson.databind.node.CirJsonNodeFactory
import org.cirjson.cirjackson.databind.node.ContainerNode
import org.cirjson.cirjackson.databind.node.ObjectNode
import org.cirjson.cirjackson.databind.type.LogicalType
import org.cirjson.cirjackson.databind.util.RawValue
import java.math.BigDecimal
import kotlin.math.max
import kotlin.math.min
import kotlin.reflect.KClass

/**
 * Base class for all actual [CirJsonNode] deserializer implementations. Uses iteration instead of recursion: this
 * allows handling of very deeply nested input structures.
 *
 * This class should only be extended by internal CirJackson deserializers. It is not intended to be used by custom
 * deserializers.
 */
abstract class BaseNodeDeserializer<T : CirJsonNode> : StandardDeserializer<T> {

    protected val mySupportsUpdate: Boolean?

    protected val myMergeArrays: Boolean

    protected val myMergeObjects: Boolean

    constructor(valueClass: KClass<T>, supportsUpdate: Boolean?) : super(valueClass) {
        mySupportsUpdate = supportsUpdate
        myMergeArrays = true
        myMergeObjects = true
    }

    protected constructor(source: BaseNodeDeserializer<*>, mergeArrays: Boolean, mergeObjects: Boolean) : super(
            source) {
        mySupportsUpdate = source.mySupportsUpdate
        myMergeArrays = mergeArrays
        myMergeObjects = mergeObjects
    }

    override fun logicalType(): LogicalType {
        return LogicalType.UNTYPED
    }

    override val isCacheable: Boolean
        get() = true

    override fun supportsUpdate(config: DeserializationConfig): Boolean? {
        return mySupportsUpdate
    }

    override fun createContextual(context: DeserializationContext, property: BeanProperty?): ValueDeserializer<*> {
        val config = context.config
        val arrayMerge = config.getDefaultMergeable(ArrayNode::class)
        val objectMerge = config.getDefaultMergeable(ObjectNode::class)
        val nodeMerge = config.getDefaultMergeable(CirJsonNode::class)

        val mergeArrays = shouldMerge(arrayMerge, nodeMerge)
        val mergeObjects = shouldMerge(objectMerge, nodeMerge)

        if (mergeArrays == myMergeArrays && mergeObjects == myMergeObjects) {
            return this
        }

        return createWithMerge(mergeArrays, mergeObjects)
    }

    protected abstract fun createWithMerge(mergeArrays: Boolean, mergeObjects: Boolean): BaseNodeDeserializer<*>

    /*
     *******************************************************************************************************************
     * Duplicate handling
     *******************************************************************************************************************
     */

    /**
     * Method called when there is a duplicate value for an Object property. By default, we don't care, and the last
     * value is used. Can be overridden to provide alternate handling, such as throwing an exception, or choosing
     * different strategy for combining values or choosing which one to keep.
     *
     * @param propertyName Name of the property for which duplicate value was found
     *
     * @param objectNode Object node that contains values
     *
     * @param oldValue Value that existed for the object node before newValue was added
     *
     * @param newValue Newly added value just added to the object node
     */
    @Throws(CirJacksonException::class)
    protected open fun handleDuplicateProperty(parser: CirJsonParser, context: DeserializationContext,
            nodeFactory: CirJsonNodeFactory, propertyName: String, objectNode: ObjectNode, oldValue: CirJsonNode,
            newValue: CirJsonNode) {
        if (context.isEnabled(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)) {
            return context.reportInputMismatch(CirJsonNode::class,
                    "Duplicate property \"$propertyName\" for `ObjectNode`: not allowed when `DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY` enabled")
        }

        if (!context.isEnabled(StreamReadCapability.DUPLICATE_PROPERTIES)) {
            return
        }

        if (oldValue.isArray) {
            (oldValue as ArrayNode).add(newValue)
            objectNode.replace(propertyName, oldValue)
        } else {
            val array = nodeFactory.arrayNode()
            array.add(oldValue)
            array.add(newValue)
            objectNode.replace(propertyName, array)
        }
    }

    /*
     *******************************************************************************************************************
     * Helper methods, deserialization
     *******************************************************************************************************************
     */

    /**
     * Alternate deserialization method used when parser already points to first PROPERTY_NAME and not START_OBJECT.
     */
    @Throws(CirJacksonException::class)
    protected fun deserializeObjectAtName(parser: CirJsonParser, context: DeserializationContext,
            nodeFactory: CirJsonNodeFactory, stack: ContainerStack): ObjectNode {
        val node = nodeFactory.objectNode()

        var key = parser.currentName()

        while (key != null) {
            val token = parser.nextToken() ?: CirJsonToken.NOT_AVAILABLE

            val value = when (token.id) {
                CirJsonTokenId.ID_START_OBJECT -> deserializeContainerNoRecursion(parser, context, nodeFactory, stack,
                        nodeFactory.objectNode())

                CirJsonTokenId.ID_START_ARRAY -> deserializeContainerNoRecursion(parser, context, nodeFactory, stack,
                        nodeFactory.arrayNode())

                else -> deserializeAnyScalar(parser, context)
            }

            val oldValue = node.replace(key, value)

            if (oldValue != null) {
                handleDuplicateProperty(parser, context, nodeFactory, key, node, oldValue, value)
            }

            key = parser.nextName()
        }

        return node
    }

    /**
     * Alternate deserialization method that is to update existing [ObjectNode] if possible.
     */
    @Throws(CirJacksonException::class)
    protected fun updateObject(parser: CirJsonParser, context: DeserializationContext, node: ObjectNode,
            stack: ContainerStack): CirJsonNode? {
        var key = if (parser.isExpectedStartObjectToken) {
            parser.nextName()
        } else if (!parser.hasToken(CirJsonToken.PROPERTY_NAME)) {
            return deserialize(parser, context)
        } else {
            parser.currentName()
        }

        val nodeFactory = context.nodeFactory

        while (key != null) {
            var token = parser.nextToken()

            val old = node[key]

            if (old is ObjectNode) {
                if (token == CirJsonToken.START_OBJECT && myMergeObjects) {
                    val newValue = updateObject(parser, context, old, stack)

                    if (newValue !== old) {
                        node[key] = newValue
                    }

                    key = parser.nextName()
                    continue
                }
            } else if (old is ArrayNode) {
                if (token == CirJsonToken.START_ARRAY && myMergeArrays) {
                    deserializeContainerNoRecursion(parser, context, nodeFactory, stack, old)
                    key = parser.nextName()
                    continue
                }
            }

            token = token ?: CirJsonToken.NOT_AVAILABLE

            val value = when (token.id) {
                CirJsonTokenId.ID_START_OBJECT -> deserializeContainerNoRecursion(parser, context, nodeFactory, stack,
                        nodeFactory.objectNode())

                CirJsonTokenId.ID_START_ARRAY -> deserializeContainerNoRecursion(parser, context, nodeFactory, stack,
                        nodeFactory.arrayNode())

                CirJsonTokenId.ID_STRING -> nodeFactory.textNode(parser.text)

                CirJsonTokenId.ID_NUMBER_INT -> fromInt(parser, context, nodeFactory)

                CirJsonTokenId.ID_TRUE -> nodeFactory.booleanNode(true)

                CirJsonTokenId.ID_FALSE -> nodeFactory.booleanNode(false)

                CirJsonTokenId.ID_NULL -> if (!context.isEnabled(CirJsonNodeFeature.READ_NULL_PROPERTIES)) {
                    key = parser.nextName()
                    continue
                } else {
                    nodeFactory.nullNode()
                }

                else -> deserializeRareScalar(parser, context)
            }

            node[key] = value
        }

        return node
    }

    @Throws(CirJacksonException::class)
    protected fun deserializeContainerNoRecursion(parser: CirJsonParser, context: DeserializationContext,
            nodeFactory: CirJsonNodeFactory, stack: ContainerStack, root: ContainerNode<*>): ContainerNode<*> {
        var currentNode: ContainerNode<*>? = root
        val coercionFeatures = context.deserializationFeatures and FEATURE_MASK_INT_COERCIONS
        currentNode!!

        outerLoop@ do {
            if (currentNode is ObjectNode) {
                var currentObject: ObjectNode = currentNode
                var propertyName = parser.nextName()

                while (propertyName != null) {
                    val token = parser.nextToken() ?: CirJsonToken.NOT_AVAILABLE

                    val value = when (token.id) {
                        CirJsonTokenId.ID_START_OBJECT -> {
                            val newObject = nodeFactory.objectNode()
                            val oldValue = currentObject.replace(propertyName, newObject)

                            if (oldValue != null) {
                                handleDuplicateProperty(parser, context, nodeFactory, propertyName, currentObject,
                                        oldValue, newObject)
                            }

                            stack.push(currentNode!!)
                            currentObject = newObject
                            currentNode = newObject
                            propertyName = parser.nextName()
                            continue
                        }

                        CirJsonTokenId.ID_START_ARRAY -> {
                            val arrayNode = nodeFactory.arrayNode()
                            val oldValue = currentObject.replace(propertyName, arrayNode)

                            if (oldValue != null) {
                                handleDuplicateProperty(parser, context, nodeFactory, propertyName, currentObject,
                                        oldValue, arrayNode)
                            }

                            stack.push(currentNode!!)
                            currentNode = arrayNode
                            continue@outerLoop
                        }

                        CirJsonTokenId.ID_STRING -> {
                            nodeFactory.textNode(parser.text)
                        }

                        CirJsonTokenId.ID_NUMBER_INT -> {
                            fromInt(parser, coercionFeatures, nodeFactory)
                        }

                        CirJsonTokenId.ID_NUMBER_FLOAT -> {
                            fromFloat(parser, context, nodeFactory)
                        }

                        CirJsonTokenId.ID_TRUE -> {
                            nodeFactory.booleanNode(true)
                        }

                        CirJsonTokenId.ID_FALSE -> {
                            nodeFactory.booleanNode(false)
                        }

                        CirJsonTokenId.ID_NULL -> {
                            if (!context.isEnabled(CirJsonNodeFeature.READ_NULL_PROPERTIES)) {
                                propertyName = parser.nextName()
                                continue
                            }

                            nodeFactory.nullNode()
                        }

                        else -> {
                            deserializeRareScalar(parser, context)
                        }
                    }

                    val oldValue = currentObject.replace(propertyName, value)

                    if (oldValue != null) {
                        handleDuplicateProperty(parser, context, nodeFactory, propertyName, currentObject, oldValue,
                                value)
                    }
                }
            } else {
                val currentArray = currentNode as ArrayNode

                while (true) {
                    val token = parser.nextToken() ?: CirJsonToken.NOT_AVAILABLE

                    when (token.id) {
                        CirJsonTokenId.ID_START_OBJECT -> {
                            stack.push(currentNode!!)
                            currentNode = nodeFactory.objectNode()
                            currentArray.add(currentNode)
                            continue@outerLoop
                        }

                        CirJsonTokenId.ID_START_ARRAY -> {
                            stack.push(currentNode!!)
                            currentNode = nodeFactory.arrayNode()
                            currentArray.add(currentNode)
                            continue@outerLoop
                        }

                        CirJsonTokenId.ID_END_ARRAY -> {
                            break
                        }

                        CirJsonTokenId.ID_STRING -> {
                            currentArray.add(nodeFactory.textNode(parser.text))
                        }

                        CirJsonTokenId.ID_NUMBER_INT -> {
                            currentArray.add(fromInt(parser, coercionFeatures, nodeFactory))
                        }

                        CirJsonTokenId.ID_NUMBER_FLOAT -> {
                            currentArray.add(fromFloat(parser, context, nodeFactory))
                        }

                        CirJsonTokenId.ID_TRUE -> {
                            currentArray.add(nodeFactory.booleanNode(true))
                        }

                        CirJsonTokenId.ID_FALSE -> {
                            currentArray.add(nodeFactory.booleanNode(false))
                        }

                        CirJsonTokenId.ID_NULL -> {
                            currentArray.add(nodeFactory.nullNode())
                        }

                        else -> {
                            currentArray.add(deserializeRareScalar(parser, context))
                        }
                    }
                }
            }

            currentNode = stack.popOrNull()
        } while (currentNode != null)

        return root
    }

    @Throws(CirJacksonException::class)
    protected fun deserializeAnyScalar(parser: CirJsonParser, context: DeserializationContext): CirJsonNode {
        val nodeFactory = context.nodeFactory

        return when (parser.currentTokenId()) {
            CirJsonTokenId.ID_END_OBJECT -> nodeFactory.objectNode()
            CirJsonTokenId.ID_STRING -> nodeFactory.textNode(parser.text)
            CirJsonTokenId.ID_NUMBER_INT -> fromInt(parser, context, nodeFactory)
            CirJsonTokenId.ID_NUMBER_FLOAT -> fromFloat(parser, context, nodeFactory)
            CirJsonTokenId.ID_TRUE -> nodeFactory.booleanNode(true)
            CirJsonTokenId.ID_FALSE -> nodeFactory.booleanNode(false)
            CirJsonTokenId.ID_NULL -> nodeFactory.nullNode()
            CirJsonTokenId.ID_EMBEDDED_OBJECT -> fromEmbedded(parser, context)
            else -> context.handleUnexpectedToken(handledType(), parser) as CirJsonNode
        }
    }

    @Throws(CirJacksonException::class)
    protected fun deserializeRareScalar(parser: CirJsonParser, context: DeserializationContext): CirJsonNode {
        return when (parser.currentTokenId()) {
            CirJsonTokenId.ID_END_OBJECT -> context.nodeFactory.objectNode()
            CirJsonTokenId.ID_NUMBER_FLOAT -> fromFloat(parser, context, context.nodeFactory)
            CirJsonTokenId.ID_EMBEDDED_OBJECT -> fromEmbedded(parser, context)
            else -> context.handleUnexpectedToken(getValueType(context), parser) as CirJsonNode
        }
    }

    @Throws(CirJacksonException::class)
    protected fun fromInt(parser: CirJsonParser, coercionFeatures: Int, nodeFactory: CirJsonNodeFactory): CirJsonNode {
        if (coercionFeatures != 0) {
            return if (DeserializationFeature.USE_BIG_INTEGER_FOR_INTS.isEnabledIn(coercionFeatures)) {
                nodeFactory.numberNode(parser.bigIntegerValue)
            } else {
                nodeFactory.numberNode(parser.longValue)
            }
        }

        val numberType = parser.numberType

        return when (numberType) {
            CirJsonParser.NumberType.INT -> nodeFactory.numberNode(parser.intValue)
            CirJsonParser.NumberType.LONG -> nodeFactory.numberNode(parser.longValue)
            else -> nodeFactory.numberNode(parser.bigIntegerValue)
        }
    }

    @Throws(CirJacksonException::class)
    protected fun fromInt(parser: CirJsonParser, context: DeserializationContext,
            nodeFactory: CirJsonNodeFactory): CirJsonNode {
        val features = context.deserializationFeatures

        val numberType = if (features and FEATURE_MASK_INT_COERCIONS != 0) {
            if (DeserializationFeature.USE_BIG_INTEGER_FOR_INTS.isEnabledIn(features)) {
                CirJsonParser.NumberType.BIG_INTEGER
            } else if (DeserializationFeature.USE_LONG_FOR_INTS.isEnabledIn(features)) {
                CirJsonParser.NumberType.LONG
            } else {
                parser.numberType
            }
        } else {
            parser.numberType
        }

        return when (numberType) {
            CirJsonParser.NumberType.INT -> nodeFactory.numberNode(parser.intValue)
            CirJsonParser.NumberType.LONG -> nodeFactory.numberNode(parser.longValue)
            else -> nodeFactory.numberNode(parser.bigIntegerValue)
        }
    }

    @Throws(CirJacksonException::class)
    protected fun fromFloat(parser: CirJsonParser, context: DeserializationContext,
            nodeFactory: CirJsonNodeFactory): CirJsonNode {
        val numberType = parser.numberTypeFP

        return if (numberType == CirJsonParser.NumberTypeFP.BIG_DECIMAL) {
            val number = parser.bigDecimalValue.let {
                if (context.isEnabled(CirJsonNodeFeature.STRIP_TRAILING_BIG_DECIMAL_ZEROES)) {
                    normalize(it)
                } else {
                    it
                }
            }

            nodeFactory.numberNode(number)
        } else if (context.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)) {
            if (parser.isNaN) {
                if (context.isEnabled(CirJsonNodeFeature.FAIL_ON_NAN_TO_BIG_DECIMAL_COERCION)) {
                    context.handleWeirdNumberValue(handledType(), parser.doubleValue,
                            "Cannot convert NaN into BigDecimal") as CirJsonNode
                } else {
                    nodeFactory.numberNode(parser.doubleValue)
                }
            } else {
                val number = parser.bigDecimalValue.let {
                    if (context.isEnabled(CirJsonNodeFeature.STRIP_TRAILING_BIG_DECIMAL_ZEROES)) {
                        normalize(it)
                    } else {
                        it
                    }
                }

                nodeFactory.numberNode(number)
            }
        } else if (numberType == CirJsonParser.NumberTypeFP.FLOAT32) {
            nodeFactory.numberNode(parser.floatValue)
        } else {
            nodeFactory.numberNode(parser.doubleValue)
        }
    }

    protected open fun normalize(number: BigDecimal): BigDecimal {
        return try {
            number.stripTrailingZeros()
        } catch (_: ArithmeticException) {
            number
        }
    }

    @Throws(CirJacksonException::class)
    protected fun fromEmbedded(parser: CirJsonParser, context: DeserializationContext): CirJsonNode {
        val nodeFactory = context.nodeFactory
        val obj = parser.embeddedObject ?: return nodeFactory.nullNode()
        val type = obj::class

        if (type == ByteArray::class) {
            return nodeFactory.binaryNode(obj as ByteArray)
        }

        return (obj as? RawValue)?.let { nodeFactory.rawValueNode(it) } ?: obj as? CirJsonNode ?: nodeFactory.nullNode()
    }

    /*
     *******************************************************************************************************************
     * Helper classes
     *******************************************************************************************************************
     */

    /**
     * Optimized variant similar in functionality to (a subset of) [ArrayDeque]; used to hold enclosing Array/Object
     * nodes during recursion-as-iteration.
     */
    protected class ContainerStack {

        private var myStack: Array<ContainerNode<*>?>? = null

        private var myTop = 0

        private var myEnd = 0

        val size: Int
            get() = myTop

        fun push(node: ContainerNode<*>) {
            if (myTop < myEnd) {
                myStack!![myTop++] = node
                return
            }

            if (myStack == null) {
                myEnd = 10
                myStack = arrayOfNulls(myEnd)
            } else {
                myEnd = min(4000, max(20, myEnd shr 1))
                myStack = myStack!!.copyOf(myEnd)
            }

            myStack!![myTop++] = node
        }

        fun popOrNull(): ContainerNode<*>? {
            if (myTop == 0) {
                return null
            }

            return myStack!![--myTop]
        }

    }

    companion object {

        private fun shouldMerge(specificMerge: Boolean?, generalMerge: Boolean?): Boolean {
            return specificMerge ?: generalMerge ?: true
        }

    }

}