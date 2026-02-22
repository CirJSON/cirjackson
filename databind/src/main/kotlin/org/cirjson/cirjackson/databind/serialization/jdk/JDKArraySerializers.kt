package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatTypes
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.standard.ArraySerializerBase
import org.cirjson.cirjackson.databind.serialization.standard.StandardContainerSerializer
import org.cirjson.cirjackson.databind.type.TypeFactory
import kotlin.reflect.KClass

/**
 * Object to group standard homogeneous array serializer implementations (primitive arrays).
 */
object JDKArraySerializers {

    private val ourArraySerializer = hashMapOf<String, ValueSerializer<*>>(
            ByteArray::class.qualifiedName!! to ByteArraySerializer(),
            CharArray::class.qualifiedName!! to CharArraySerializer(),
            BooleanArray::class.qualifiedName!! to BooleanArraySerializer(),
            ShortArray::class.qualifiedName!! to ShortArraySerializer(),
            IntArray::class.qualifiedName!! to IntArraySerializer(),
            LongArray::class.qualifiedName!! to LongArraySerializer(),
            FloatArray::class.qualifiedName!! to FloatArraySerializer(),
            DoubleArray::class.qualifiedName!! to DoubleArraySerializer())

    /**
     * Accessor for checking to see if there is a standard serializer for given primitive value type.
     */
    fun findStandardImplementation(type: KClass<*>): ValueSerializer<*>? {
        return ourArraySerializer[type.qualifiedName]
    }

    /*
     *******************************************************************************************************************
     * Intermediate base classes
     *******************************************************************************************************************
     */

    /**
     * Intermediate base class used for cases where we may add type information (excludes Boolean/Int/Double arrays).
     */
    abstract class TypedPrimitiveArraySerializer<T : Any> : ArraySerializerBase<T> {

        constructor(type: KClass<T>) : super(type)

        constructor(source: TypedPrimitiveArraySerializer<T>, property: BeanProperty?, unwrapSingle: Boolean?) : super(
                source, property, unwrapSingle)

        final override fun withValueTypeSerializerImplementation(
                valueTypeSerializer: TypeSerializer): StandardContainerSerializer<*> {
            return this
        }

    }

    /*
     *******************************************************************************************************************
     * Concrete serializers, arrays
     *******************************************************************************************************************
     */

    @CirJacksonStandardImplementation
    open class BooleanArraySerializer : ArraySerializerBase<BooleanArray> {

        constructor() : super(BooleanArray::class)

        protected constructor(source: BooleanArraySerializer, property: BeanProperty?, unwrapSingle: Boolean?) : super(
                source, property, unwrapSingle)

        override fun withResolved(property: BeanProperty?, unwrapSingle: Boolean?): ValueSerializer<*> {
            return BooleanArraySerializer(this, property, unwrapSingle)
        }

        /**
         * Booleans never add type info; hence, even if type serializer is suggested, we'll ignore it...
         */
        override fun withValueTypeSerializerImplementation(
                valueTypeSerializer: TypeSerializer): StandardContainerSerializer<*> {
            return this
        }

        override val contentType: KotlinType
            get() = VALUE_TYPE

        override val contentSerializer: ValueSerializer<*>?
            get() = null

        override fun isEmpty(provider: SerializerProvider, value: BooleanArray?): Boolean {
            return value!!.isEmpty()
        }

        override fun hasSingleElement(value: BooleanArray): Boolean {
            return value.size == 1
        }

        @Throws(CirJacksonException::class)
        override fun serialize(value: BooleanArray, generator: CirJsonGenerator, serializers: SerializerProvider) {
            val size = value.size

            if (size == 1 && shouldUnwrapSingle(serializers)) {
                serializeContents(value, generator, serializers)
                return
            }

            generator.writeStartArray(value, size)
            serializeContents(value, generator, serializers)
            generator.writeEndArray()
        }

        @Throws(CirJacksonException::class)
        override fun serializeContents(value: BooleanArray, generator: CirJsonGenerator, context: SerializerProvider) {
            for (boolean in value) {
                generator.writeBoolean(boolean)
            }
        }

        override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
            visitArrayFormat(visitor, typeHint, CirJsonFormatTypes.BOOLEAN)
        }

        companion object {

            private val VALUE_TYPE = TypeFactory.DEFAULT_INSTANCE.constructType(BooleanArray::class.java)

        }

    }

    @CirJacksonStandardImplementation
    open class ShortArraySerializer : TypedPrimitiveArraySerializer<ShortArray> {

        constructor() : super(ShortArray::class)

        protected constructor(source: ShortArraySerializer, property: BeanProperty?, unwrapSingle: Boolean?) : super(
                source, property, unwrapSingle)

        override fun withResolved(property: BeanProperty?, unwrapSingle: Boolean?): ValueSerializer<*> {
            return ShortArraySerializer(this, property, unwrapSingle)
        }

        override val contentType: KotlinType
            get() = VALUE_TYPE

        override val contentSerializer: ValueSerializer<*>?
            get() = null

        override fun isEmpty(provider: SerializerProvider, value: ShortArray?): Boolean {
            return value!!.isEmpty()
        }

        override fun hasSingleElement(value: ShortArray): Boolean {
            return value.size == 1
        }

        @Throws(CirJacksonException::class)
        override fun serialize(value: ShortArray, generator: CirJsonGenerator, serializers: SerializerProvider) {
            val size = value.size

            if (size == 1 && shouldUnwrapSingle(serializers)) {
                serializeContents(value, generator, serializers)
                return
            }

            generator.writeStartArray(value, size)
            serializeContents(value, generator, serializers)
            generator.writeEndArray()
        }

        @Throws(CirJacksonException::class)
        override fun serializeContents(value: ShortArray, generator: CirJsonGenerator, context: SerializerProvider) {
            for (short in value) {
                generator.writeNumber(short)
            }
        }

        override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
            visitArrayFormat(visitor, typeHint, CirJsonFormatTypes.INTEGER)
        }

        companion object {

            private val VALUE_TYPE = TypeFactory.DEFAULT_INSTANCE.constructType(ShortArray::class.java)

        }

    }

    @CirJacksonStandardImplementation
    open class IntArraySerializer : ArraySerializerBase<IntArray> {

        constructor() : super(IntArray::class)

        protected constructor(source: IntArraySerializer, property: BeanProperty?, unwrapSingle: Boolean?) : super(
                source, property, unwrapSingle)

        override fun withResolved(property: BeanProperty?, unwrapSingle: Boolean?): ValueSerializer<*> {
            return IntArraySerializer(this, property, unwrapSingle)
        }

        /**
         * Ints never add type info; hence, even if type serializer is suggested, we'll ignore it...
         */
        override fun withValueTypeSerializerImplementation(
                valueTypeSerializer: TypeSerializer): StandardContainerSerializer<*> {
            return this
        }

        override val contentType: KotlinType
            get() = VALUE_TYPE

        override val contentSerializer: ValueSerializer<*>?
            get() = null

        override fun isEmpty(provider: SerializerProvider, value: IntArray?): Boolean {
            return value!!.isEmpty()
        }

        override fun hasSingleElement(value: IntArray): Boolean {
            return value.size == 1
        }

        @Throws(CirJacksonException::class)
        override fun serialize(value: IntArray, generator: CirJsonGenerator, serializers: SerializerProvider) {
            val size = value.size

            if (size == 1 && shouldUnwrapSingle(serializers)) {
                serializeContents(value, generator, serializers)
                return
            }

            generator.writeStartArray(value, size)
            serializeContents(value, generator, serializers)
            generator.writeEndArray()
        }

        @Throws(CirJacksonException::class)
        override fun serializeContents(value: IntArray, generator: CirJsonGenerator, context: SerializerProvider) {
            for (int in value) {
                generator.writeNumber(int)
            }
        }

        override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
            visitArrayFormat(visitor, typeHint, CirJsonFormatTypes.INTEGER)
        }

        companion object {

            private val VALUE_TYPE = TypeFactory.DEFAULT_INSTANCE.constructType(IntArray::class.java)

        }

    }

    @CirJacksonStandardImplementation
    open class LongArraySerializer : TypedPrimitiveArraySerializer<LongArray> {

        constructor() : super(LongArray::class)

        protected constructor(source: LongArraySerializer, property: BeanProperty?, unwrapSingle: Boolean?) : super(
                source, property, unwrapSingle)

        override fun withResolved(property: BeanProperty?, unwrapSingle: Boolean?): ValueSerializer<*> {
            return LongArraySerializer(this, property, unwrapSingle)
        }

        override val contentType: KotlinType
            get() = VALUE_TYPE

        override val contentSerializer: ValueSerializer<*>?
            get() = null

        override fun isEmpty(provider: SerializerProvider, value: LongArray?): Boolean {
            return value!!.isEmpty()
        }

        override fun hasSingleElement(value: LongArray): Boolean {
            return value.size == 1
        }

        @Throws(CirJacksonException::class)
        override fun serialize(value: LongArray, generator: CirJsonGenerator, serializers: SerializerProvider) {
            val size = value.size

            if (size == 1 && shouldUnwrapSingle(serializers)) {
                serializeContents(value, generator, serializers)
                return
            }

            generator.writeStartArray(value, size)
            serializeContents(value, generator, serializers)
            generator.writeEndArray()
        }

        @Throws(CirJacksonException::class)
        override fun serializeContents(value: LongArray, generator: CirJsonGenerator, context: SerializerProvider) {
            for (long in value) {
                generator.writeNumber(long)
            }
        }

        override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
            visitArrayFormat(visitor, typeHint, CirJsonFormatTypes.INTEGER)
        }

        companion object {

            private val VALUE_TYPE = TypeFactory.DEFAULT_INSTANCE.constructType(LongArray::class.java)

        }

    }

    @CirJacksonStandardImplementation
    open class FloatArraySerializer : TypedPrimitiveArraySerializer<FloatArray> {

        constructor() : super(FloatArray::class)

        protected constructor(source: FloatArraySerializer, property: BeanProperty?, unwrapSingle: Boolean?) : super(
                source, property, unwrapSingle)

        override fun withResolved(property: BeanProperty?, unwrapSingle: Boolean?): ValueSerializer<*> {
            return FloatArraySerializer(this, property, unwrapSingle)
        }

        override val contentType: KotlinType
            get() = VALUE_TYPE

        override val contentSerializer: ValueSerializer<*>?
            get() = null

        override fun isEmpty(provider: SerializerProvider, value: FloatArray?): Boolean {
            return value!!.isEmpty()
        }

        override fun hasSingleElement(value: FloatArray): Boolean {
            return value.size == 1
        }

        @Throws(CirJacksonException::class)
        override fun serialize(value: FloatArray, generator: CirJsonGenerator, serializers: SerializerProvider) {
            val size = value.size

            if (size == 1 && shouldUnwrapSingle(serializers)) {
                serializeContents(value, generator, serializers)
                return
            }

            generator.writeStartArray(value, size)
            serializeContents(value, generator, serializers)
            generator.writeEndArray()
        }

        @Throws(CirJacksonException::class)
        override fun serializeContents(value: FloatArray, generator: CirJsonGenerator, context: SerializerProvider) {
            for (float in value) {
                generator.writeNumber(float)
            }
        }

        override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
            visitArrayFormat(visitor, typeHint, CirJsonFormatTypes.NUMBER)
        }

        companion object {

            private val VALUE_TYPE = TypeFactory.DEFAULT_INSTANCE.constructType(FloatArray::class.java)

        }

    }

    @CirJacksonStandardImplementation
    open class DoubleArraySerializer : ArraySerializerBase<DoubleArray> {

        constructor() : super(DoubleArray::class)

        protected constructor(source: DoubleArraySerializer, property: BeanProperty?, unwrapSingle: Boolean?) : super(
                source, property, unwrapSingle)

        override fun withResolved(property: BeanProperty?, unwrapSingle: Boolean?): ValueSerializer<*> {
            return DoubleArraySerializer(this, property, unwrapSingle)
        }

        /**
         * Doubles never add type info; hence, even if type serializer is suggested, we'll ignore it...
         */
        override fun withValueTypeSerializerImplementation(
                valueTypeSerializer: TypeSerializer): StandardContainerSerializer<*> {
            return this
        }

        override val contentType: KotlinType
            get() = VALUE_TYPE

        override val contentSerializer: ValueSerializer<*>?
            get() = null

        override fun isEmpty(provider: SerializerProvider, value: DoubleArray?): Boolean {
            return value!!.isEmpty()
        }

        override fun hasSingleElement(value: DoubleArray): Boolean {
            return value.size == 1
        }

        @Throws(CirJacksonException::class)
        override fun serialize(value: DoubleArray, generator: CirJsonGenerator, serializers: SerializerProvider) {
            val size = value.size

            if (size == 1 && shouldUnwrapSingle(serializers)) {
                serializeContents(value, generator, serializers)
                return
            }

            generator.writeStartArray(value, size)
            serializeContents(value, generator, serializers)
            generator.writeEndArray()
        }

        @Throws(CirJacksonException::class)
        override fun serializeContents(value: DoubleArray, generator: CirJsonGenerator, context: SerializerProvider) {
            for (double in value) {
                generator.writeNumber(double)
            }
        }

        override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
            visitArrayFormat(visitor, typeHint, CirJsonFormatTypes.NUMBER)
        }

        companion object {

            private val VALUE_TYPE = TypeFactory.DEFAULT_INSTANCE.constructType(DoubleArray::class.java)

        }

    }

}