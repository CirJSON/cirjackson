package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.standard.AsArraySerializerBase
import org.cirjson.cirjackson.databind.serialization.standard.StandardContainerSerializer

@CirJacksonStandardImplementation
open class IteratorSerializer : AsArraySerializerBase<Iterator<*>> {

    constructor(elementType: KotlinType, staticTyping: Boolean, valueTypeSerializer: TypeSerializer?) : super(
            Iterator::class, elementType, staticTyping, valueTypeSerializer, null)

    constructor(source: IteratorSerializer, valueTypeSerializer: TypeSerializer?,
            elementSerializer: ValueSerializer<*>?, unwrapSingle: Boolean?, property: BeanProperty?) : super(source,
            valueTypeSerializer, elementSerializer, unwrapSingle, property)

    override fun withValueTypeSerializerImplementation(
            valueTypeSerializer: TypeSerializer): StandardContainerSerializer<*> {
        return IteratorSerializer(this, valueTypeSerializer, myElementSerializer, myUnwrapSingle, myProperty)
    }

    override fun withResolved(property: BeanProperty?, valueTypeSerializer: TypeSerializer?,
            elementSerializer: ValueSerializer<*>?, unwrapSingle: Boolean?): IteratorSerializer {
        return IteratorSerializer(this, valueTypeSerializer, elementSerializer, unwrapSingle, property)
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    override fun isEmpty(provider: SerializerProvider, value: Iterator<*>?): Boolean {
        return !value!!.hasNext()
    }

    override fun hasSingleElement(value: Iterator<*>): Boolean {
        return false
    }

    /*
     *******************************************************************************************************************
     * Serialization
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun serialize(value: Iterator<*>, generator: CirJsonGenerator, serializers: SerializerProvider) {
        generator.writeStartArray(value)
        serializeContents(value, generator, serializers)
        generator.writeEndArray()
    }

    @Throws(CirJacksonException::class)
    override fun serializeContents(value: Iterator<*>, generator: CirJsonGenerator, context: SerializerProvider) {
        if (!value.hasNext()) {
            return
        }

        val serializer = myElementSerializer

        if (serializer == null) {
            serializeDynamicContents(value, generator, context)
            return
        }

        val typeSerializer = myValueTypeSerializer

        do {
            val element = value.next()

            if (element == null) {
                context.defaultSerializeNullValue(generator)
            } else if (typeSerializer == null) {
                serializer.serialize(element, generator, context)
            } else {
                serializer.serializeWithType(element, generator, context, typeSerializer)
            }
        } while (value.hasNext())
    }

    @Throws(CirJacksonException::class)
    protected open fun serializeDynamicContents(value: Iterator<*>, generator: CirJsonGenerator,
            context: SerializerProvider) {
        val typeSerializer = myValueTypeSerializer

        do {
            val element = value.next()

            if (element == null) {
                context.defaultSerializeNullValue(generator)
                continue
            }

            val elementClass = element::class

            val serializer =
                    myDynamicValueSerializers.serializerFor(elementClass) ?: if (myElementType.hasGenericTypes()) {
                        findAndAddDynamic(context, context.constructSpecializedType(myElementType, elementClass))
                    } else {
                        findAndAddDynamic(context, elementClass)
                    }

            if (typeSerializer == null) {
                serializer.serialize(element, generator, context)
            } else {
                serializer.serializeWithType(element, generator, context, typeSerializer)
            }
        } while (value.hasNext())
    }

}