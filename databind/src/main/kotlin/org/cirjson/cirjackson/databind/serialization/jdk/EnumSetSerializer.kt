package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.standard.AsArraySerializerBase
import java.util.*

open class EnumSetSerializer : AsArraySerializerBase<EnumSet<out Enum<*>>> {

    constructor(elementType: KotlinType) : super(EnumSet::class, elementType, true, null, null)

    constructor(source: EnumSetSerializer, valueTypeSerializer: TypeSerializer?, elementSerializer: ValueSerializer<*>?,
            unwrapSingle: Boolean?, property: BeanProperty?) : super(source, valueTypeSerializer, elementSerializer,
            unwrapSingle, property)

    override fun withValueTypeSerializerImplementation(valueTypeSerializer: TypeSerializer): EnumSetSerializer {
        return this
    }

    override fun withResolved(property: BeanProperty?, valueTypeSerializer: TypeSerializer?,
            elementSerializer: ValueSerializer<*>?, unwrapSingle: Boolean?): EnumSetSerializer {
        return EnumSetSerializer(this, valueTypeSerializer, elementSerializer, unwrapSingle, property)
    }

    override fun isEmpty(provider: SerializerProvider, value: EnumSet<out Enum<*>>?): Boolean {
        return value!!.isEmpty()
    }

    override fun hasSingleElement(value: EnumSet<out Enum<*>>): Boolean {
        return value.size == 1
    }

    @Throws(CirJacksonException::class)
    override fun serialize(value: EnumSet<out Enum<*>>, generator: CirJsonGenerator, serializers: SerializerProvider) {
        val size = value.size

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
    override fun serializeContents(value: EnumSet<out Enum<*>>, generator: CirJsonGenerator,
            context: SerializerProvider) {
        var serializer = myElementSerializer

        for (enum in value) {
            if (serializer == null) {
                serializer = findAndAddDynamic(context, enum.declaringJavaClass.kotlin)
            }

            serializer.serialize(enum, generator, context)
        }
    }

}