package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.standard.StandardScalarSerializer
import org.cirjson.cirjackson.databind.serialization.standard.ToStringSerializer
import kotlin.reflect.KClass

/**
 * Serializer used for primitive [Boolean], as well as [java.lang.Boolean] wrapper type.
 *
 * Since this is one of "natural" (aka "native") types, no type information is ever included on serialization (unlike
 * for most other scalar types)
 *
 * @property myForPrimitive Whether type serialized is primitive ([Boolean]) or wrapper ([java.lang.Boolean]); if
 * `true`, former, if `false`, latter.
 */
@CirJacksonStandardImplementation
@Suppress("UNCHECKED_CAST")
class BooleanSerializer(private val myForPrimitive: Boolean) : StandardScalarSerializer<Any>(
        (if (myForPrimitive) Boolean::class else java.lang.Boolean::class) as KClass<*> as KClass<Any>) {

    override fun createContextual(provider: SerializerProvider, property: BeanProperty?): ValueSerializer<*> {
        val format = findFormatOverrides(provider, property, handledType()!!)
        val shape = format.shape

        return if (shape.isNumeric) {
            AsNumber(myForPrimitive)
        } else if (shape == CirJsonFormat.Shape.STRING) {
            ToStringSerializer(myHandledType)
        } else {
            this
        }
    }

    @Throws(CirJacksonException::class)
    override fun serialize(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider) {
        generator.writeBoolean(true == value)
    }

    @Throws(CirJacksonException::class)
    override fun serializeWithType(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        generator.writeBoolean(true == value)
    }

    override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
        visitor.expectBooleanFormat(typeHint)
    }

    /**
     * Alternate implementation that is used when values are to be serialized as numbers `0` (`false`) or `1` (`true`).
     *
     * @property myForPrimitive Whether type serialized is primitive ([Boolean]) or wrapper ([java.lang.Boolean]); if
     * `true`, former, if `false`, latter.
     */
    private class AsNumber(private val myForPrimitive: Boolean) : StandardScalarSerializer<Any>(
            (if (myForPrimitive) Boolean::class else java.lang.Boolean::class) as KClass<*> as KClass<Any>) {

        override fun createContextual(provider: SerializerProvider, property: BeanProperty?): ValueSerializer<*> {
            val format = findFormatOverrides(provider, property, Boolean::class)
            val shape = format.shape

            return if (shape.isNumeric) {
                this
            } else {
                BooleanSerializer(myForPrimitive)
            }
        }

        @Throws(CirJacksonException::class)
        override fun serialize(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider) {
            generator.writeNumber(if (true == value) 1 else 0)
        }

        @Throws(CirJacksonException::class)
        override fun serializeWithType(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider,
                typeSerializer: TypeSerializer) {
            generator.writeBoolean(true == value)
        }

        override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
            visitIntFormat(visitor, typeHint, CirJsonParser.NumberType.INT)
        }

    }

}