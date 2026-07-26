package org.cirjson.cirjackson.databind.deserialization.cirjackson

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.CirJsonTokenId
import org.cirjson.cirjackson.databind.CirJsonNode
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.node.ArrayNode
import org.cirjson.cirjackson.databind.node.ObjectNode
import kotlin.reflect.KClass

/**
 * Deserializer that can build instances of [CirJsonNode] from any CirJSON content, using appropriate [CirJsonNode]
 * type.
 */
open class CirJsonNodeDeserializer : BaseNodeDeserializer<CirJsonNode> {

    protected constructor() : super(CirJsonNode::class, null)

    protected constructor(source: CirJsonNodeDeserializer, mergeArrays: Boolean, mergeObjects: Boolean) : super(source,
            mergeArrays, mergeObjects)

    override fun createWithMerge(mergeArrays: Boolean, mergeObjects: Boolean): BaseNodeDeserializer<*> {
        return CirJsonNodeDeserializer(this, mergeArrays, mergeObjects)
    }

    /*
     *******************************************************************************************************************
     * Actual deserialization method implementations
     *******************************************************************************************************************
     */

    override fun getNullValue(context: DeserializationContext): Any {
        return context.nodeFactory.nullNode()
    }

    /**
     * Overridden variant to ensure that absent values are NOT coerced into `NullNode`, unlike incoming `null` values.
     */
    override fun getAbsentValue(context: DeserializationContext): Any? {
        return null
    }

    /**
     * Implementation that will produce types of any CirJSON nodes; not just one deserializer is registered to handle
     * (in case of more specialized handler). Overridden by typed subclasses for more thorough checking.
     */
    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): CirJsonNode? {
        val stack = ContainerStack()
        val nodeFactory = context.nodeFactory
        val token = parser.currentToken()!!

        return when (token.id) {
            CirJsonTokenId.ID_START_OBJECT -> deserializeContainerNoRecursion(parser, context, nodeFactory, stack,
                    nodeFactory.objectNode())

            CirJsonTokenId.ID_START_ARRAY -> deserializeContainerNoRecursion(parser, context, nodeFactory, stack,
                    nodeFactory.arrayNode())

            CirJsonTokenId.ID_END_OBJECT -> nodeFactory.objectNode()

            CirJsonTokenId.ID_PROPERTY_NAME -> deserializeObjectAtName(parser, context, nodeFactory, stack)

            else -> deserializeAnyScalar(parser, context)
        }
    }

    /*
     *******************************************************************************************************************
     * Specific instances for more accurate types
     *******************************************************************************************************************
     */

    /**
     * Implementation used when declared type is specifically [ObjectNode].
     */
    internal class ObjectNodeDeserializer : BaseNodeDeserializer<ObjectNode> {

        constructor() : super(ObjectNode::class, true)

        private constructor(source: ObjectNodeDeserializer, mergeArrays: Boolean, mergeObjects: Boolean) : super(source,
                mergeArrays, mergeObjects)

        override fun createWithMerge(mergeArrays: Boolean, mergeObjects: Boolean): BaseNodeDeserializer<*> {
            return ObjectNodeDeserializer(this, mergeArrays, mergeObjects)
        }

        @Throws(CirJacksonException::class)
        override fun deserialize(parser: CirJsonParser, context: DeserializationContext): ObjectNode? {
            val nodeFactory = context.nodeFactory

            return if (parser.isExpectedStartObjectToken) {
                val root = nodeFactory.objectNode()
                deserializeContainerNoRecursion(parser, context, nodeFactory, ContainerStack(), root)
                root
            } else if (parser.hasToken(CirJsonToken.PROPERTY_NAME)) {
                deserializeObjectAtName(parser, context, nodeFactory, ContainerStack())
            } else if (parser.hasToken(CirJsonToken.END_OBJECT)) {
                nodeFactory.objectNode()
            } else {
                context.handleUnexpectedToken(getValueType(context), parser) as ObjectNode?
            }
        }

        /**
         * Variant needed to support both root-level [updateObject] and merging.
         */
        @Throws(CirJacksonException::class)
        override fun deserialize(parser: CirJsonParser, context: DeserializationContext,
                intoValue: ObjectNode): ObjectNode? {
            return if (!parser.isExpectedStartObjectToken && !parser.hasToken(CirJsonToken.PROPERTY_NAME)) {
                context.handleUnexpectedToken(getValueType(context), parser) as ObjectNode?
            } else {
                updateObject(parser, context, intoValue, ContainerStack()) as ObjectNode?
            }
        }

        companion object {

            val INSTANCE = ObjectNodeDeserializer()

        }

    }

    /**
     * Implementation used when declared type is specifically [ArrayNode].
     */
    internal class ArrayNodeDeserializer : BaseNodeDeserializer<ArrayNode> {

        constructor() : super(ArrayNode::class, true)

        private constructor(source: ArrayNodeDeserializer, mergeArrays: Boolean, mergeObjects: Boolean) : super(source,
                mergeArrays, mergeObjects)

        override fun createWithMerge(mergeArrays: Boolean, mergeObjects: Boolean): BaseNodeDeserializer<*> {
            return ArrayNodeDeserializer(this, mergeArrays, mergeObjects)
        }

        @Throws(CirJacksonException::class)
        override fun deserialize(parser: CirJsonParser, context: DeserializationContext): ArrayNode? {
            if (!parser.isExpectedStartArrayToken) {
                return context.handleUnexpectedToken(getValueType(context), parser) as ArrayNode?
            }

            val nodeFactory = context.nodeFactory
            val root = nodeFactory.arrayNode()
            deserializeContainerNoRecursion(parser, context, nodeFactory, ContainerStack(), root)
            return root
        }

        /**
         * Variant needed to support both root-level [updateObject] and merging.
         */
        @Throws(CirJacksonException::class)
        override fun deserialize(parser: CirJsonParser, context: DeserializationContext,
                intoValue: ArrayNode): ArrayNode? {
            return if (!parser.isExpectedStartArrayToken) {
                context.handleUnexpectedToken(getValueType(context), parser) as ArrayNode?
            } else {
                deserializeContainerNoRecursion(parser, context, context.nodeFactory, ContainerStack(), intoValue)
                intoValue
            }
        }

        companion object {

            val INSTANCE = ArrayNodeDeserializer()

        }

    }

    companion object {

        /**
         * Singleton instance of generic deserializer for [CirJsonNode]. Only used for types other than CirJSON Object
         * and Array.
         */
        private val INSTANCE = CirJsonNodeDeserializer()

        /**
         * Factory method for accessing deserializer for specific node type
         */
        fun getDeserializer(nodeClass: KClass<*>): BaseNodeDeserializer<*> {
            return if (nodeClass == ObjectNode::class) {
                ObjectNodeDeserializer.INSTANCE
            } else if (nodeClass == ArrayNode::class) {
                ArrayNodeDeserializer.INSTANCE
            } else {
                INSTANCE
            }
        }

    }

}