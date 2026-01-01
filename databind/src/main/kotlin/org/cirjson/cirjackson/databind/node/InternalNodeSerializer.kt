package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.CirJacksonSerializable
import org.cirjson.cirjackson.databind.CirJsonNode
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjson.CirJsonMapper
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import kotlin.math.max
import kotlin.math.min

/**
 * Helper object used to support non-recursive serialization for use by [BaseCirJsonNode.toString] (and related) method.
 */
object InternalNodeSerializer {

    private val MAPPER = CirJsonMapper.shared()

    private val COMPACT_WRITER = MAPPER.writer()

    private val PRETTY_WRITER = MAPPER.writerWithDefaultPrettyPrinter()

    private fun wrapper(root: BaseCirJsonNode): CirJacksonSerializable {
        return WrapperForSerializer(root)
    }

    fun toString(root: BaseCirJsonNode): String {
        return COMPACT_WRITER.writeValueAsString(wrapper(root))
    }

    fun toPrettyString(root: BaseCirJsonNode): String {
        return PRETTY_WRITER.writeValueAsString(wrapper(root))
    }

    /**
     * Intermediate serializer we need to implement non-recursive serialization of [BaseCirJsonNode]
     */
    private class WrapperForSerializer(private val myRoot: BaseCirJsonNode) : CirJacksonSerializable.Base() {

        private var myContext: SerializerProvider? = null

        @Throws(CirJacksonException::class)
        override fun serialize(generator: CirJsonGenerator, context: SerializerProvider) {
            myContext = context
            serializeNonRecursive(generator, myRoot)
        }

        @Throws(CirJacksonException::class)
        override fun serializeWithType(generator: CirJsonGenerator, context: SerializerProvider,
                typeSerializer: TypeSerializer) {
            serialize(generator, context)
        }

        @Throws(CirJacksonException::class)
        private fun serializeNonRecursive(generator: CirJsonGenerator, node: CirJsonNode) {
            when (node) {
                is ObjectNode -> {
                    generator.writeStartObject(node, node.size)
                    serializeNonRecursive(generator, IteratorStack(), node.fields())
                }

                is ArrayNode -> {
                    generator.writeStartArray(node, node.size)
                    serializeNonRecursive(generator, IteratorStack(), node.elements())
                }

                else -> {
                    node.serialize(generator, myContext!!)
                }
            }
        }

        @Throws(CirJacksonException::class)
        @Suppress("UNCHECKED_CAST")
        private fun serializeNonRecursive(generator: CirJsonGenerator, stack: IteratorStack,
                rootIterator: Iterator<*>) {
            var currentIterator = rootIterator

            while (true) {
                while (currentIterator.hasNext()) {
                    val element = currentIterator.next()!!

                    val value = if (element is Map.Entry<*, *>) {
                        val entry = element as Map.Entry<String, CirJsonNode>
                        generator.writeName(entry.key)
                        entry.value
                    } else {
                        element as CirJsonNode
                    }

                    when (value) {
                        is ObjectNode -> {
                            stack.push(currentIterator)
                            currentIterator = value.fields()
                            generator.writeStartObject(value, value.size)
                        }

                        is ArrayNode -> {
                            stack.push(currentIterator)
                            currentIterator = value.elements()
                            generator.writeStartArray(value, value.size)
                        }

                        is POJONode -> {
                            try {
                                value.serialize(generator, myContext!!)
                            } catch (e: CirJacksonException) {
                                generator.writeString("[ERROR: (${e::class.qualifiedName}) ${e.message}]")
                            }
                        }

                        else -> {
                            value.serialize(generator, myContext!!)
                        }
                    }
                }

                if (generator.streamWriteContext.isInArray) {
                    generator.writeEndArray()
                } else {
                    generator.writeEndObject()
                }

                currentIterator = stack.popOrNull() ?: return
            }
        }

    }

    /**
     * Optimized variant similar in functionality to (a subset of) [ArrayDeque]; used to hold enclosing Array/Object
     * nodes during recursion-as-iteration.
     */
    private class IteratorStack {

        private var myStack: Array<Iterator<*>?>? = null

        private var myTop = 0

        private var myEnd = 0

        fun push(iterator: Iterator<*>) {
            if (myTop < myEnd) {
                myStack!![myTop++] = iterator
                return
            }

            if (myStack == null) {
                myEnd = 10
                myStack = arrayOfNulls(myEnd)
            } else {
                myEnd += min(4000, max(20, myEnd shr 1))
                myStack = myStack!!.copyOf(myEnd)
            }

            myStack!![myTop++] = iterator
        }

        fun popOrNull(): Iterator<*>? {
            if (myTop == 0) {
                return null
            }

            return myStack!![--myTop]
        }

    }

}