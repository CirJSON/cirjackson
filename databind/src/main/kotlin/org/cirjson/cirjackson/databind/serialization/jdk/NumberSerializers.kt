package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.standard.StandardScalarSerializer
import org.cirjson.cirjackson.databind.serialization.standard.ToStringSerializer
import java.math.BigDecimal
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

/**
 * Container object for serializers used for handling standard JDK-provided types.
 */
object NumberSerializers {

    fun addAll(allSerializers: MutableMap<String, ValueSerializer<*>>) {
        allSerializers[Integer::class.jvmName] = IntSerializer(Int::class)
        allSerializers[Integer.TYPE.name] = IntSerializer(Int::class)
        allSerializers[Int::class.qualifiedName!!] = IntSerializer(Int::class)
        allSerializers[java.lang.Long::class.jvmName] = LongSerializer(Long::class)
        allSerializers[java.lang.Long.TYPE.name] = LongSerializer(Long::class)
        allSerializers[Long::class.qualifiedName!!] = LongSerializer(Long::class)

        allSerializers[java.lang.Byte::class.jvmName] = IntLikeSerializer.INSTANCE
        allSerializers[java.lang.Byte.TYPE.name] = IntLikeSerializer.INSTANCE
        allSerializers[Byte::class.qualifiedName!!] = IntLikeSerializer.INSTANCE
        allSerializers[java.lang.Short::class.jvmName] = ShortSerializer.INSTANCE
        allSerializers[java.lang.Short.TYPE.name] = ShortSerializer.INSTANCE
        allSerializers[Short::class.qualifiedName!!] = ShortSerializer.INSTANCE

        allSerializers[java.lang.Float::class.jvmName] = FloatSerializer.INSTANCE
        allSerializers[java.lang.Float.TYPE.name] = FloatSerializer.INSTANCE
        allSerializers[Float::class.qualifiedName!!] = FloatSerializer.INSTANCE
        allSerializers[java.lang.Double::class.jvmName] = DoubleSerializer(Double::class)
        allSerializers[java.lang.Double.TYPE.name] = DoubleSerializer(Double::class)
        allSerializers[Double::class.qualifiedName!!] = DoubleSerializer(Double::class)
    }

    /*
     *******************************************************************************************************************
     * Shared base class
     *******************************************************************************************************************
     */

    /**
     * Base class for actual primitive/wrapper value serializers.
     * 
     * NOTE: while you can extend this class yourself, it is not designed as an extension point, and as such is not part
     * of public API. This means that the compatibility across minor versions is only guaranteed on minor-to-minor
     * basis, and class methods may be changed and/or removed via deprecation mechanism. Intent is, however, to allow
     * for gradual upgrading so that methods to remove are marked deprecated for at least one minor version.
     */
    @Suppress("UNCHECKED_CAST")
    abstract class Base<T : Any> protected constructor(type: KClass<*>,
            protected val myNumberType: CirJsonParser.NumberType, protected val mySchemaType: String) :
            StandardScalarSerializer<T>(type as KClass<T>) {

        protected val myIsInt = myNumberType == CirJsonParser.NumberType.INT ||
                myNumberType == CirJsonParser.NumberType.LONG || myNumberType == CirJsonParser.NumberType.BIG_INTEGER

        override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
            if (myIsInt) {
                visitIntFormat(visitor, typeHint, myNumberType)
            } else {
                visitFloatFormat(visitor, typeHint, myNumberType)
            }
        }

        override fun createContextual(provider: SerializerProvider, property: BeanProperty?): ValueSerializer<*> {
            val format = findFormatOverrides(provider, property, handledType()!!)

            if (format.shape != CirJsonFormat.Shape.STRING) {
                return this
            }

            if (handledType() == BigDecimal::class) {
                return NumberSerializer.bigDecimalAsStringSerializer()
            }

            return ToStringSerializer.INSTANCE
        }

    }

    /*
     *******************************************************************************************************************
     * Concrete serializers
     *******************************************************************************************************************
     */

    @CirJacksonStandardImplementation
    open class ShortSerializer : Base<Any>(Short::class, CirJsonParser.NumberType.INT, "integer") {

        @Throws(CirJacksonException::class)
        override fun serialize(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider) {
            generator.writeNumber(value as Short)
        }

        companion object {

            val INSTANCE = ShortSerializer()

        }

    }

    /**
     * This is the special serializer for regular [Ints][Int] (and primitive ints)
     * 
     * Since this is one of "natural" types, no type information is ever included on serialization (unlike for most
     * scalar types, except for `double`).
     */
    @CirJacksonStandardImplementation
    open class IntSerializer(type: KClass<*>) : Base<Any>(type, CirJsonParser.NumberType.INT, "integer") {

        @Throws(CirJacksonException::class)
        override fun serialize(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider) {
            generator.writeNumber(value as Int)
        }

        @Throws(CirJacksonException::class)
        override fun serializeWithType(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider,
                typeSerializer: TypeSerializer) {
            serialize(value, generator, serializers)
        }

    }

    /**
     * Similar to [IntSerializer], but will not cast to Int: instead, cast is to [Number], and conversion is by calling
     * [Number.toInt].
     */
    @CirJacksonStandardImplementation
    open class IntLikeSerializer : Base<Any>(Number::class, CirJsonParser.NumberType.INT, "integer") {

        @Throws(CirJacksonException::class)
        override fun serialize(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider) {
            generator.writeNumber((value as Number).toInt())
        }

        companion object {

            val INSTANCE = IntLikeSerializer()

        }

    }

    @CirJacksonStandardImplementation
    open class LongSerializer(type: KClass<*>) : Base<Any>(type, CirJsonParser.NumberType.LONG, "integer") {

        @Throws(CirJacksonException::class)
        override fun serialize(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider) {
            generator.writeNumber(value as Long)
        }

    }

    @CirJacksonStandardImplementation
    open class FloatSerializer : Base<Any>(Float::class, CirJsonParser.NumberType.FLOAT, "number") {

        @Throws(CirJacksonException::class)
        override fun serialize(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider) {
            generator.writeNumber(value as Float)
        }

        companion object {

            val INSTANCE = FloatSerializer()

        }

    }

    /**
     * This is the special serializer for regular [Doubles][Double] (and primitive doubles).
     * 
     * Since this is one of "native" types, no type information is ever included on serialization .
     */
    @CirJacksonStandardImplementation
    open class DoubleSerializer(type: KClass<*>) : Base<Any>(type, CirJsonParser.NumberType.DOUBLE, "number") {

        @Throws(CirJacksonException::class)
        override fun serialize(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider) {
            generator.writeNumber(value as Double)
        }

        @Throws(CirJacksonException::class)
        override fun serializeWithType(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider,
                typeSerializer: TypeSerializer) {
            if ((value as Double).isFinite()) {
                generator.writeNumber(value)
                return
            }

            val typeIdDefinition = typeSerializer.writeTypePrefix(generator, serializers,
                    typeSerializer.typeId(value, CirJsonToken.VALUE_NUMBER_FLOAT))
            generator.writeNumber(value)
            typeSerializer.writeTypeSuffix(generator, serializers, typeIdDefinition)
        }

    }

}