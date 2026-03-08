package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.standard.AsArraySerializerBase
import org.cirjson.cirjackson.databind.serialization.standard.StandardContainerSerializer

/**
 * Fallback serializer for cases where Collection is not known to be of type for which more specializer serializer
 * exists (such as index-accessible List). If so, we will just construct an [Iterator] to iterate over elements.
 */
open class CollectionSerializer : AsArraySerializerBase<Collection<*>> {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    constructor(elementType: KotlinType, staticTyping: Boolean, valueTypeSerializer: TypeSerializer?,
            elementSerializer: ValueSerializer<Any>?) : super(Collection::class, elementType, staticTyping,
            valueTypeSerializer, elementSerializer)

    protected constructor(source: CollectionSerializer, valueTypeSerializer: TypeSerializer?,
            elementSerializer: ValueSerializer<*>?, unwrapSingle: Boolean?, property: BeanProperty?) : super(source,
            valueTypeSerializer, elementSerializer, unwrapSingle, property)

    override fun withValueTypeSerializerImplementation(
            valueTypeSerializer: TypeSerializer): StandardContainerSerializer<*> {
        return CollectionSerializer(this, valueTypeSerializer, myElementSerializer, myUnwrapSingle, myProperty)
    }

    override fun withResolved(property: BeanProperty?, valueTypeSerializer: TypeSerializer?,
            elementSerializer: ValueSerializer<*>?, unwrapSingle: Boolean?): AsArraySerializerBase<Collection<*>> {
        return CollectionSerializer(this, valueTypeSerializer, elementSerializer, unwrapSingle, property)
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    override fun isEmpty(provider: SerializerProvider, value: Collection<*>?): Boolean {
        return value!!.isEmpty()
    }

    override fun hasSingleElement(value: Collection<*>): Boolean {
        return value.size == 1
    }

    /*
     *******************************************************************************************************************
     * Actual serialization
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun serialize(value: Collection<*>, generator: CirJsonGenerator, serializers: SerializerProvider) {
        val length = value.size

        if (length == 1) {
            if (myUnwrapSingle ?: serializers.isEnabled(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)) {
                serializeContents(value, generator, serializers)
                return
            }
        }

        generator.writeStartArray(value, length)
        serializeContents(value, generator, serializers)
        generator.writeEndArray()
    }

    @Throws(CirJacksonException::class)
    override fun serializeContents(value: Collection<*>, generator: CirJsonGenerator, context: SerializerProvider) {
        if (myElementSerializer != null) {
            serializeContentsUsing(value, generator, context, myElementSerializer)
            return
        }

        val iterator = value.iterator()

        if (!iterator.hasNext()) {
            return
        }

        var serializers = myDynamicValueSerializers
        val typeSerializer = myValueTypeSerializer

        var i = 0

        try {
            do {
                val element = iterator.next()

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

                if (typeSerializer == null) {
                    serializer.serialize(element, generator, context)
                } else {
                    serializer.serializeWithType(element, generator, context, typeSerializer)
                }

                i++
            } while (iterator.hasNext())
        } catch (e: Exception) {
            wrapAndThrow(context, e, value, i)
        }
    }

    @Throws(CirJacksonException::class)
    open fun serializeContentsUsing(value: Collection<*>, generator: CirJsonGenerator, context: SerializerProvider,
            serializer: ValueSerializer<Any>) {
        val iterator = value.iterator()

        if (!iterator.hasNext()) {
            return
        }

        val typeSerializer = myValueTypeSerializer
        var i = 0

        do {
            val element = iterator.next()

            try {
                if (element == null) {
                    context.defaultSerializeNullValue(generator)
                } else if (typeSerializer == null) {
                    serializer.serialize(element, generator, context)
                } else {
                    serializer.serializeWithType(element, generator, context, typeSerializer)
                }

                i++
            } catch (e: Exception) {
                wrapAndThrow(context, e, value, i)
            }
        } while (iterator.hasNext())
    }

}