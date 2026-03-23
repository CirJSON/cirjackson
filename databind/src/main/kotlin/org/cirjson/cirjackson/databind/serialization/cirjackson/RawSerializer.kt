package org.cirjson.cirjackson.databind.serialization.cirjackson

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.standard.StandardSerializer
import kotlin.reflect.KClass

/**
 * This is a simple dummy serializer that will just output raw values by calling `toString()` on value to serialize.
 *
 * @constructor Constructor takes in expected type of values; but since caller typically cannot really provide actual
 * type parameter, we will just take wild card and coerce type.
 */
open class RawSerializer<T : Any>(clazz: KClass<*>) : StandardSerializer<T>(clazz) {

    @Throws(CirJacksonException::class)
    override fun serialize(value: T, generator: CirJsonGenerator, serializers: SerializerProvider) {
        generator.writeRawValue(value.toString())
    }

    @Throws(CirJacksonException::class)
    override fun serializeWithType(value: T, generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        val typeIdDefinition = typeSerializer.writeTypePrefix(generator, serializers,
                typeSerializer.typeId(value, CirJsonToken.VALUE_EMBEDDED_OBJECT))
        serialize(value, generator, serializers)
        typeSerializer.writeTypeSuffix(generator, serializers, typeIdDefinition)
    }

    override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
        visitStringFormat(visitor, typeHint)
    }

}