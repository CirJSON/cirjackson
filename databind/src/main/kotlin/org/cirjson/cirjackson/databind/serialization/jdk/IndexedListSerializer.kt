package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.standard.AsArraySerializerBase

/**
 * This is an optimized serializer for Lists that can be efficiently traversed by index (as opposed to others, such as
 * [java.util.LinkedList], that cannot).
 */
@CirJacksonStandardImplementation
class IndexedListSerializer : AsArraySerializerBase<Any> {

    constructor(elementType: KotlinType, staticTyping: Boolean, valueTypeSerializer: TypeSerializer?,
            elementSerializer: ValueSerializer<*>?) : super(List::class, elementType, staticTyping, valueTypeSerializer,
            elementSerializer)

    constructor(source: IndexedListSerializer, valueTypeSerializer: TypeSerializer?,
            elementSerializer: ValueSerializer<*>?, unwrapSingle: Boolean?, property: BeanProperty?) : super(source,
            valueTypeSerializer, elementSerializer, unwrapSingle, property)

    override fun withValueTypeSerializerImplementation(valueTypeSerializer: TypeSerializer): IndexedListSerializer {
        return IndexedListSerializer(this, valueTypeSerializer, myElementSerializer, myUnwrapSingle, myProperty)
    }

    override fun withResolved(property: BeanProperty?, valueTypeSerializer: TypeSerializer?,
            elementSerializer: ValueSerializer<*>?, unwrapSingle: Boolean?): IndexedListSerializer {
        return IndexedListSerializer(this, valueTypeSerializer, elementSerializer, unwrapSingle, property)
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    override fun isEmpty(provider: SerializerProvider, value: Any?): Boolean {
        return (value as List<*>?)!!.isEmpty()
    }

    override fun hasSingleElement(value: Any): Boolean {
        return (value as List<*>).size == 1
    }

    /*
     *******************************************************************************************************************
     * Serialization
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun serialize(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider) {
        val size = (value as List<*>).size

        if (size == 1) {
            if (myUnwrapSingle ?: serializers.isEnabled(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)) {
                serializeContents(value, generator, serializers)
                return
            }
        }

        generator.writeStartArray(value, size)
        serializeContents(value, generator, serializers)
        generator.writeEndArray()
    }

    @Throws(CirJacksonException::class)
    override fun serializeContents(value: Any, generator: CirJsonGenerator, context: SerializerProvider) {
        if (myElementSerializer != null) {
            serializeContentsUsing(value as List<*>, generator, context, myElementSerializer)
            return
        }

        if (myValueTypeSerializer != null) {
            serializeTypedContents(value as List<*>, generator, context)
            return
        }

        val size = (value as List<*>).size

        if (size == 0) {
            return
        }

        var i = 0

        try {
            while (i < size) {
                val element = value[i]

                if (element == null) {
                    context.defaultSerializeNullValue(generator)
                    i++
                    continue
                }

                val elementClass = element::class

                val serializer =
                        myDynamicValueSerializers.serializerFor(elementClass) ?: if (myElementType.hasGenericTypes) {
                            findAndAddDynamic(context, context.constructSpecializedType(myElementType, elementClass))
                        } else {
                            findAndAddDynamic(context, elementClass)
                        }

                serializer.serialize(element, generator, context)
                i++
            }
        } catch (e: Exception) {
            wrapAndThrow(context, e, value, i)
        }
    }

    @Throws(CirJacksonException::class)
    fun serializeContentsUsing(value: List<*>, generator: CirJsonGenerator, context: SerializerProvider,
            serializer: ValueSerializer<Any>) {
        val size = value.size

        if (size == 0) {
            return
        }

        val typeSerializer = myValueTypeSerializer

        for (i in 0..<size) {
            val element = value[i]

            try {
                if (element == null) {
                    context.defaultSerializeNullValue(generator)
                } else if (typeSerializer == null) {
                    serializer.serialize(element, generator, context)
                } else {
                    serializer.serializeWithType(element, generator, context, typeSerializer)
                }
            } catch (e: Exception) {
                wrapAndThrow(context, e, value, i)
            }
        }
    }

    @Throws(CirJacksonException::class)
    fun serializeTypedContents(value: List<*>, generator: CirJsonGenerator, context: SerializerProvider) {
        val size = value.size

        if (size == 0) {
            return
        }

        var i = 0

        try {
            var serializers = myDynamicValueSerializers

            while (i < size) {
                val element = value[i]

                if (element == null) {
                    context.defaultSerializeNullValue(generator)
                    i++
                    continue
                }

                val elementClass = element::class
                var serializer = serializers.serializerFor(elementClass)

                if (serializer == null) {
                    serializer = if (myElementType.hasGenericTypes) {
                        findAndAddDynamic(context, context.constructSpecializedType(myElementType, elementClass))
                    } else {
                        findAndAddDynamic(context, elementClass)
                    }

                    serializers = myDynamicValueSerializers
                }

                serializer.serializeWithType(element, generator, context, myValueTypeSerializer!!)
            }
        } catch (e: Exception) {
            wrapAndThrow(context, e, value, i)
        }
    }

}