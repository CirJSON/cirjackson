package org.cirjson.cirjackson.databind.serialization.cirjackson

import org.cirjson.cirjackson.annotations.CirJsonTypeInfo
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.type.WritableTypeID
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.cirjsontype.TypeIdResolver
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import org.cirjson.cirjackson.databind.serialization.standard.StandardDynamicSerializer
import org.cirjson.cirjackson.databind.util.enumConstants
import org.cirjson.cirjackson.databind.util.isEnumType
import org.cirjson.cirjackson.databind.util.throwIfError
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KClass

/**
 * Serializer class that can serialize Object that have a [org.cirjson.cirjackson.annotations.CirJsonValue] annotation
 * to indicate that serialization should be done by calling the method annotated, and serializing result it returns.
 *
 * Implementation note: we will post-process resulting serializer (much like what is done with
 * [BeanSerializer][org.cirjson.cirjackson.databind.serialization.BeanSerializer]) to figure out actual serializers for
 * final types. This must be done from [createContextual] method, and NOT from constructor; otherwise we could end up
 * with an infinite loop.
 */
@CirJacksonStandardImplementation
open class CirJsonValueSerializer : StandardDynamicSerializer<Any> {

    /**
     * Accessor (field, getter) used to access value to serialize.
     */
    protected val myAccessor: AnnotatedMember

    /**
     * Value for annotated accessor.
     */
    protected val myValueType: KotlinType

    protected val myStaticTyping: Boolean

    /**
     * This is a flag that is set in rare (?) cases where this serializer is used for "natural" types (boolean, int, 
     * String, double); and where we actually must force type information wrapping, even though one would not normally
     * be added.
     */
    protected val myForceTypeInformation: Boolean

    protected val myIgnoredProperties: Set<String>

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    /**
     * @param serializer Explicit serializer to use, if caller knows it (which occurs if and only if the "value method"
     * was annotated with [org.cirjson.cirjackson.databind.annotation.CirJsonSerialize.using]), otherwise `null`
     */
    protected constructor(nominalType: KotlinType, valueType: KotlinType, staticTyping: Boolean,
            valueTypeSerializer: TypeSerializer?, serializer: ValueSerializer<*>?, accessor: AnnotatedMember,
            ignoredProperties: Set<String>) : super(nominalType, null, valueTypeSerializer, serializer) {
        myValueType = valueType
        myStaticTyping = staticTyping
        myAccessor = accessor
        myForceTypeInformation = true
        myIgnoredProperties = ignoredProperties
    }

    protected constructor(source: CirJsonValueSerializer, property: BeanProperty?, valueTypeSerializer: TypeSerializer?,
            serializer: ValueSerializer<*>?, forceTypeInformation: Boolean) : super(source, property,
            valueTypeSerializer, serializer) {
        myValueType = source.myValueType
        myStaticTyping = source.myStaticTyping
        myAccessor = source.myAccessor
        myForceTypeInformation = forceTypeInformation
        myIgnoredProperties = source.myIgnoredProperties
    }

    open fun withResolved(property: BeanProperty?, valueTypeSerializer: TypeSerializer?,
            serializer: ValueSerializer<*>?, forceTypeInformation: Boolean): CirJsonValueSerializer {
        if (myProperty === property && myValueTypeSerializer === valueTypeSerializer && myValueSerializer == serializer
                && myForceTypeInformation == forceTypeInformation) {
            return this
        }

        return CirJsonValueSerializer(this, property, valueTypeSerializer, serializer, forceTypeInformation)
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    override fun isEmpty(provider: SerializerProvider, value: Any?): Boolean {
        val referenced = myAccessor.getValue(value!!) ?: return true
        val serializer = myValueSerializer ?: findSerializer(provider, value)
        return serializer.isEmpty(provider, referenced)
    }

    /*
     *******************************************************************************************************************
     * Postprocessing
     *******************************************************************************************************************
     */

    /**
     * We can try to find the actual serializer for value, if we can statically figure out what the result type must be.
     */
    override fun createContextual(provider: SerializerProvider, property: BeanProperty?): ValueSerializer<*> {
        val valueTypeSerializer = myValueTypeSerializer?.forProperty(provider, property)

        var serializer = myValueSerializer

        if (serializer != null) {
            serializer = provider.handlePrimaryContextualization(serializer, property)
            return withResolved(property, valueTypeSerializer, serializer, myForceTypeInformation)
        }

        if (myStaticTyping || provider.isEnabled(MapperFeature.USE_STATIC_TYPING) || myValueType.isFinal) {
            serializer = provider.findPrimaryPropertySerializer(myValueType, property)
            serializer = withIgnoreProperties(serializer, myIgnoredProperties)
            val forceTypeInformation = isNaturalTypeWithStandardHandling(myValueType.rawClass, serializer)
            return withResolved(property, valueTypeSerializer, serializer, forceTypeInformation)
        }

        if (myProperty === property) {
            return this
        }

        return withResolved(property, valueTypeSerializer, null, myForceTypeInformation)
    }

    /*
     *******************************************************************************************************************
     * Actual serialization
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun serialize(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider) {
        val realValue = try {
            myAccessor.getValue(value)
        } catch (e: Exception) {
            wrapAndThrow(serializers, e, value, "${myAccessor.name}()")
        }

        if (realValue == null) {
            serializers.defaultSerializeNullValue(generator)
            return
        }

        val serializer = myValueSerializer ?: findSerializer(serializers, realValue)

        if (myValueTypeSerializer != null) {
            serializer.serializeWithType(realValue, generator, serializers, myValueTypeSerializer)
        } else {
            serializer.serialize(realValue, generator, serializers)
        }
    }

    protected open fun findSerializer(context: SerializerProvider, value: Any): ValueSerializer<Any> {
        val serializerTransformer = { valueSerializer: ValueSerializer<Any> ->
            withIgnoreProperties(valueSerializer, myIgnoredProperties)!!
        }
        val type = value::class

        return if (myValueType.hasGenericTypes()) {
            findAndAddDynamic(context, context.constructSpecializedType(myValueType, type), serializerTransformer)
        } else {
            findAndAddDynamic(context, type, serializerTransformer)
        }
    }

    @Throws(CirJacksonException::class)
    override fun serializeWithType(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        val realValue = try {
            myAccessor.getValue(value)
        } catch (e: Exception) {
            wrapAndThrow(serializers, e, value, "${myAccessor.name}()")
        }

        if (realValue == null) {
            serializers.defaultSerializeNullValue(generator)
            return
        }

        val serializer = myValueSerializer ?: realValue::class.let {
            if (myValueType.hasGenericTypes()) {
                findAndAddDynamic(serializers, serializers.constructSpecializedType(myValueType, it))
            } else {
                findAndAddDynamic(serializers, it)
            }
        }

        if (myForceTypeInformation) {
            val typeIdDefinition = typeSerializer.writeTypePrefix(generator, serializers,
                    typeSerializer.typeId(value, CirJsonToken.VALUE_STRING))
            serializer.serialize(realValue, generator, serializers)
            typeSerializer.writeTypeSuffix(generator, serializers, typeIdDefinition)
            return
        }

        val rerouter = TypeSerializerRerouter(typeSerializer, value)
        serializer.serializeWithType(realValue, generator, serializers, rerouter)
    }

    @Suppress("UNCHECKED_CAST")
    override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
        val type = myAccessor.type
        val declaringClass = myAccessor.declaringClass

        if (declaringClass.isEnumType) {
            if (acceptJsonFormatVisitorForEnum(visitor, typeHint, declaringClass as KClass<out Enum<*>>)) {
                return
            }
        }

        val serializer = myValueSerializer ?: visitor.provider!!.findPrimaryPropertySerializer(type, myProperty)
        serializer.acceptCirJsonFormatVisitor(visitor, type)
    }

    /**
     * Overridable helper method used for special case handling of schema information for Enums.
     *
     * @return `true` if method handled callbacks; `false` if not. In latter case, caller will send default callbacks
     */
    protected open fun acceptJsonFormatVisitorForEnum(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType,
            enumType: KClass<out Enum<*>>): Boolean {
        val stringVisitor = visitor.expectStringFormat(typeHint) ?: return true
        val enums = LinkedHashSet<String>()

        for (enum in enumType.enumConstants) {
            try {
                enums.add(myAccessor.getValue(enum).toString())
            } catch (e: Exception) {
                var t: Throwable = e

                while (t is InvocationTargetException && t.cause != null) {
                    t = t.cause!!
                }

                throw CirJacksonException.wrapWithPath(t.throwIfError(), enum, "${myAccessor.name}()")
            }
        }

        stringVisitor.enumTypes(enums)
        return true
    }

    protected open fun isNaturalTypeWithStandardHandling(rawType: KClass<*>, serializer: ValueSerializer<*>?): Boolean {
        if (rawType != String::class && rawType != Int::class && rawType != Boolean::class &&
                rawType != Double::class) {
            return false
        }

        return isDefaultSerializer(serializer)
    }

    /*
     *******************************************************************************************************************
     * Helper class
     *******************************************************************************************************************
     */

    /**
     * Silly little wrapper class we need to re-route type serialization so that we can override Object to use for type
     * id (logical type) even when asking serialization of something else (delegate type)
     */
    protected open class TypeSerializerRerouter(protected val myTypeSerializer: TypeSerializer,
            protected val myForObject: Any?) : TypeSerializer() {

        override fun forProperty(context: SerializerProvider, property: BeanProperty?): TypeSerializer {
            throw UnsupportedOperationException()
        }

        override val typeInclusion: CirJsonTypeInfo.As
            get() = myTypeSerializer.typeInclusion

        override val propertyName: String?
            get() = myTypeSerializer.propertyName

        override val typeIdResolver: TypeIdResolver
            get() = myTypeSerializer.typeIdResolver

        @Throws(CirJacksonException::class)
        override fun writeTypePrefix(generator: CirJsonGenerator, context: SerializerProvider,
                typeID: WritableTypeID): WritableTypeID? {
            typeID.forValue = myForObject
            return myTypeSerializer.writeTypePrefix(generator, context, typeID)
        }

        @Throws(CirJacksonException::class)
        override fun writeTypeSuffix(generator: CirJsonGenerator, context: SerializerProvider,
                typeID: WritableTypeID?): WritableTypeID? {
            return myTypeSerializer.writeTypeSuffix(generator, context, typeID)
        }

    }

    companion object {

        fun construct(config: SerializationConfig, nominalType: KotlinType, valueType: KotlinType,
                staticTyping: Boolean, valueTypeSerializer: TypeSerializer?, serializer: ValueSerializer<*>?,
                accessor: AnnotatedMember): CirJsonValueSerializer {
            val ignorals = config.annotationIntrospector!!.findPropertyIgnoralByName(config, accessor)
            val ignoredProperties = ignorals!!.findIgnoredForSerialization()
            return CirJsonValueSerializer(nominalType, valueType, staticTyping, valueTypeSerializer,
                    withIgnoreProperties(serializer, ignoredProperties), accessor, ignoredProperties)
        }

        /**
         * Helper that configures the provided `serializer` to ignore properties specified by
         * [CirJsonIgnoreProperties][org.cirjson.cirjackson.annotations.CirJsonIgnoreProperties].
         *
         * @param serializer  Serializer to be configured
         *
         * @param ignoredProperties Properties to ignore, if any
         *
         * @return Configured serializer with specified properties ignored
         */
        @Suppress("UNCHECKED_CAST")
        fun withIgnoreProperties(serializer: ValueSerializer<*>?,
                ignoredProperties: Set<String>): ValueSerializer<Any>? {
            serializer ?: return null

            return if (ignoredProperties.isNotEmpty()) {
                serializer.withIgnoredProperties(ignoredProperties) as ValueSerializer<Any>?
            } else {
                serializer as ValueSerializer<Any>
            }
        }

    }

}