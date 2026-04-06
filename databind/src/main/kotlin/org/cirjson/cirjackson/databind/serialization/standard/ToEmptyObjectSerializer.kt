package org.cirjson.cirjackson.databind.serialization.standard

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import kotlin.reflect.KClass

/**
 * Simple "bogus" serializer that will just serialize an empty Object for any given value. Quite similar to
 * [org.cirjson.cirjackson.databind.serialization.implementation.UnknownSerializer] with the exception that
 * serialization never fails.
 */
@CirJacksonStandardImplementation
open class ToEmptyObjectSerializer : StandardSerializer<Any> {

    protected constructor(raw: KClass<*>) : super(raw)

    protected constructor(type: KotlinType) : super(type)

    @Throws(CirJacksonException::class)
    override fun serialize(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider) {
        generator.writeStartObject(value, 0)
        generator.writeEndObject()
    }

    @Throws(CirJacksonException::class)
    override fun serializeWithType(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        val typeIdDefinition = typeSerializer.writeTypePrefix(generator, serializers,
                typeSerializer.typeId(value, CirJsonToken.START_OBJECT))
        typeSerializer.writeTypeSuffix(generator, serializers, typeIdDefinition)
    }

    override fun isEmpty(provider: SerializerProvider, value: Any?): Boolean {
        return true
    }

    @Throws(CirJacksonException::class)
    override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
        visitor.expectObjectFormat(typeHint)
    }

    companion object {

        fun construct(raw: KClass<*>): ToEmptyObjectSerializer {
            return ToEmptyObjectSerializer(raw)
        }

        fun construct(type: KotlinType): ToEmptyObjectSerializer {
            return ToEmptyObjectSerializer(type)
        }

    }

}