package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.standard.AsArraySerializerBase
import org.cirjson.cirjackson.databind.serialization.standard.StandardContainerSerializer

@CirJacksonStandardImplementation
open class IterableSerializer : AsArraySerializerBase<Iterable<*>> {

    constructor(elementType: KotlinType, staticTyping: Boolean, valueTypeSerializer: TypeSerializer?) : super(
            Iterable::class, elementType, staticTyping, valueTypeSerializer, null)

    constructor(source: IterableSerializer, valueTypeSerializer: TypeSerializer?,
            elementSerializer: ValueSerializer<*>?, unwrapSingle: Boolean?, property: BeanProperty?) : super(source,
            valueTypeSerializer, elementSerializer, unwrapSingle, property)

    override fun withValueTypeSerializerImplementation(
            valueTypeSerializer: TypeSerializer): StandardContainerSerializer<*> {
        return IterableSerializer(this, valueTypeSerializer, myElementSerializer, myUnwrapSingle, myProperty)
    }

    override fun withResolved(property: BeanProperty?, valueTypeSerializer: TypeSerializer?,
            elementSerializer: ValueSerializer<*>?, unwrapSingle: Boolean?): IterableSerializer {
        return IterableSerializer(this, valueTypeSerializer, elementSerializer, unwrapSingle, property)
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    override fun isEmpty(provider: SerializerProvider, value: Iterable<*>?): Boolean {
        return !value!!.iterator().hasNext()
    }

    override fun hasSingleElement(value: Iterable<*>): Boolean {
        val iterator = value.iterator()

        if (iterator.hasNext()) {
            iterator.next()
            return !iterator.hasNext()
        }

        return false
    }

    /*
     *******************************************************************************************************************
     * Serialization
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    final override fun serialize(value: Iterable<*>, generator: CirJsonGenerator, serializers: SerializerProvider) {
        if (myUnwrapSingle ?: serializers.isEnabled(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)) {
            if (hasSingleElement(value)) {
                serializeContents(value, generator, serializers)
                return
            }
        }

        generator.writeStartArray(value)
        serializeContents(value, generator, serializers)
        generator.writeEndArray()
    }

    @Throws(CirJacksonException::class)
    override fun serializeContents(value: Iterable<*>, generator: CirJsonGenerator, context: SerializerProvider) {
        val iterator = value.iterator()

        if (!iterator.hasNext()) {
            return
        }

        val typeSerializer = myValueTypeSerializer

        do {
            val element = iterator.next()

            if (element == null) {
                context.defaultSerializeNullValue(generator)
                continue
            }

            val serializer = myElementSerializer ?: element::class.let {
                myDynamicValueSerializers.serializerFor(it) ?: if (myElementType.hasGenericTypes()) {
                    findAndAddDynamic(context, context.constructSpecializedType(myElementType, it))
                } else {
                    findAndAddDynamic(context, it)
                }
            }

            if (typeSerializer == null) {
                serializer.serialize(element, generator, context)
            } else {
                serializer.serializeWithType(element, generator, context, typeSerializer)
            }
        } while (iterator.hasNext())
    }

}