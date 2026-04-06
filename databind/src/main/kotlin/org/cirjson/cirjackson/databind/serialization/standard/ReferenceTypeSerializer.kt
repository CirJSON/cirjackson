package org.cirjson.cirjackson.databind.serialization.standard

import org.cirjson.cirjackson.annotations.CirJsonInclude
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.annotation.CirJsonSerialize
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.standard.ReferenceTypeSerializer.Companion.MARKER_FOR_EMPTY
import org.cirjson.cirjackson.databind.type.ReferenceType
import org.cirjson.cirjackson.databind.util.ArrayBuilders
import org.cirjson.cirjackson.databind.util.BeanUtil
import org.cirjson.cirjackson.databind.util.NameTransformer
import org.cirjson.cirjackson.databind.util.isArray
import kotlin.reflect.KClass

/**
 * Base implementation for values of [ReferenceType]. Implements most of the functionality, only leaving a couple of
 * abstract methods for subclasses to implement.
 */
abstract class ReferenceTypeSerializer<T : Any> : StandardDynamicSerializer<T> {

    /**
     * Value type.
     */
    protected val myReferredType: KotlinType

    /**
     * In case of unwrapping, need name transformer.
     */
    protected val myUnwrapper: NameTransformer?

    /*
     *******************************************************************************************************************
     * Config settings, filtering
     *******************************************************************************************************************
     */

    /**
     * Value that indicates suppression mechanism to use for **values contained**; either "filter" (of which `equals()`
     * is called), or marker value of [MARKER_FOR_EMPTY], or `null` to indicate no filtering for non-`null` values. Note
     * that inclusion value for Map instance itself is handled by caller (POJO property that refers to the Map value).
     */
    protected val mySuppressableValue: Any?

    /**
     * Flag that indicates what to do with `null` values, distinct from handling of [mySuppressableValue].
     */
    protected val mySuppressNulls: Boolean

    /*
     *******************************************************************************************************************
     * Constructors, factory methods
     *******************************************************************************************************************
     */

    constructor(fullType: ReferenceType, valueTypeSerializer: TypeSerializer?,
            valueSerializer: ValueSerializer<*>?) : super(fullType, null, valueTypeSerializer, valueSerializer) {
        myReferredType = fullType.referencedType
        myUnwrapper = null
        mySuppressableValue = null
        mySuppressNulls = false
    }

    protected constructor(source: ReferenceTypeSerializer<*>, property: BeanProperty?,
            valueTypeSerializer: TypeSerializer?, valueSerializer: ValueSerializer<*>?, unwrapper: NameTransformer?,
            suppressableValue: Any?, suppressNulls: Boolean) : super(source, property, valueTypeSerializer,
            valueSerializer) {
        myReferredType = source.myReferredType
        myUnwrapper = unwrapper
        mySuppressableValue = suppressableValue
        mySuppressNulls = suppressNulls
    }

    override fun unwrappingSerializer(unwrapper: NameTransformer): ValueSerializer<T> {
        var valueSerializer = myValueSerializer

        if (valueSerializer != null) {
            valueSerializer = valueSerializer.unwrappingSerializer(unwrapper)

            if (valueSerializer === myValueSerializer) {
                return this
            }
        }

        val transformer = myUnwrapper?.let { NameTransformer.chainedTransformer(unwrapper, it) } ?: unwrapper

        if (myValueSerializer === valueSerializer && myUnwrapper === transformer) {
            return this
        }

        return withResolved(myProperty, myValueTypeSerializer, valueSerializer, transformer)
    }

    /*
     *******************************************************************************************************************
     * Abstract methods to implement
     *******************************************************************************************************************
     */

    /**
     * Mutant factory method called when changes are needed; should construct newly configured instance with new values
     * as indicated.
     *
     * NOTE: caller has verified that there are changes, so implementations need NOT check if a new instance is needed.
     */
    protected abstract fun withResolved(property: BeanProperty?, valueTypeSerializer: TypeSerializer?,
            valueSerializer: ValueSerializer<*>?, unwrapper: NameTransformer?): ReferenceTypeSerializer<T>

    /**
     * Mutant factory method called to create a differently constructed instance, specifically with different exclusion
     * rules for contained value.
     *
     * NOTE: caller has verified that there are changes, so implementations need NOT check if a new instance is needed.
     */
    protected abstract fun withContentInclusion(suppressableValue: Any?,
            suppressNulls: Boolean): ReferenceTypeSerializer<T>

    internal fun withContentInclusionInternal(suppressableValue: Any?,
            suppressNulls: Boolean): ReferenceTypeSerializer<T> {
        return withContentInclusion(suppressableValue, suppressNulls)
    }

    /**
     * Method called to see if there is a value present or not. Note that value itself may still be `null`, even if
     * present, if referential type allows three states (absent, present-`null`, present-non-`null`); some only allow
     * two (absent, present-non-`null`).
     */
    protected abstract fun isValuePresent(value: T): Boolean

    protected abstract fun getReferenced(value: T): Any?

    protected abstract fun getReferencedIfPresent(value: T): Any?

    /*
     *******************************************************************************************************************
     * Contextualization (support for property annotations)
     *******************************************************************************************************************
     */

    override fun createContextual(provider: SerializerProvider, property: BeanProperty?): ValueSerializer<*> {
        val typeSerializer = myValueTypeSerializer?.forProperty(provider, property)

        var serializer = findAnnotatedContentSerializer(provider, property)

        if (serializer == null) {
            serializer = myValueSerializer

            if (serializer == null) {
                if (useStatic(provider, property, myReferredType)) {
                    serializer = findSerializer(provider, myReferredType, property)
                }
            } else {
                serializer = provider.handlePrimaryContextualization(serializer, property)
            }
        }

        serializer = findContextualConvertingSerializer(provider, property, serializer)

        var referenceSerializer = if (myProperty === property && myValueTypeSerializer === typeSerializer &&
                myValueSerializer === serializer) {
            this
        } else {
            withResolved(property, typeSerializer, serializer, myUnwrapper)
        }

        val includeValue =
                property?.findPropertyInclusion(provider.config, handledType()!!) ?: return referenceSerializer
        val inclusion = includeValue.contentInclusion

        if (inclusion == CirJsonInclude.Include.USE_DEFAULTS) {
            return referenceSerializer
        }

        val (valueToSuppress, suppressNulls) = when (inclusion) {
            CirJsonInclude.Include.NON_DEFAULT -> {
                var value = BeanUtil.getDefaultValue(myReferredType)

                if (value != null && value::class.isArray) {
                    value = ArrayBuilders.getArrayComparator(value)
                }

                value to true
            }

            CirJsonInclude.Include.NON_ABSENT -> {
                MARKER_FOR_EMPTY.takeIf { myReferredType.isReferenceType } to true
            }

            CirJsonInclude.Include.NON_EMPTY -> {
                MARKER_FOR_EMPTY to true
            }

            CirJsonInclude.Include.CUSTOM -> {
                val value = provider.includeFilterInstance(null, includeValue.contentFilter)

                value to (value?.let { provider.includeFilterSuppressNulls(it) } ?: true)
            }

            CirJsonInclude.Include.NON_NULL -> {
                null to true
            }

            else -> {
                null to false
            }
        }

        if (mySuppressableValue !== valueToSuppress || mySuppressNulls != suppressNulls) {
            referenceSerializer = referenceSerializer.withContentInclusion(valueToSuppress, suppressNulls)
        }

        return referenceSerializer
    }

    protected open fun useStatic(context: SerializerProvider, property: BeanProperty?,
            referencedType: KotlinType): Boolean {
        if (referencedType.isJavaLangObject) {
            return false
        }

        if (referencedType.isFinal) {
            return true
        }

        if (referencedType.isUsedAsStaticType) {
            return true
        }

        val introspector = context.annotationIntrospector ?: return context.isEnabled(MapperFeature.USE_STATIC_TYPING)

        val annotated = property?.member ?: return context.isEnabled(MapperFeature.USE_STATIC_TYPING)

        val typing = introspector.findSerializationTyping(context.config, annotated)

        return when (typing) {
            CirJsonSerialize.Typing.STATIC -> true
            CirJsonSerialize.Typing.DYNAMIC -> false
            else -> context.isEnabled(MapperFeature.USE_STATIC_TYPING)
        }
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun isEmpty(provider: SerializerProvider, value: T?): Boolean {
        if (!isValuePresent(value!!)) {
            return true
        }

        val contents = getReferenced(value) ?: return mySuppressNulls

        mySuppressableValue ?: return false

        val serializer = myValueSerializer ?: findCachedSerializer(provider, contents::class)

        if (mySuppressableValue === MARKER_FOR_EMPTY) {
            return serializer.isEmpty(provider, value)
        }

        return mySuppressableValue == contents
    }

    override val isUnwrappingSerializer: Boolean
        get() = myUnwrapper != null

    open val referredType: KotlinType
        get() = myReferredType

    /*
     *******************************************************************************************************************
     * Serialization methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun serialize(value: T, generator: CirJsonGenerator, serializers: SerializerProvider) {
        val referencedValue = getReferencedIfPresent(value)

        if (referencedValue == null) {
            if (myUnwrapper == null) {
                serializers.defaultSerializeNullValue(generator)
            }

            return
        }

        val serializer = myValueSerializer ?: findCachedSerializer(serializers, referencedValue::class)

        if (myValueTypeSerializer != null) {
            serializer.serializeWithType(referencedValue, generator, serializers, myValueTypeSerializer)
        } else {
            serializer.serialize(referencedValue, generator, serializers)
        }
    }

    @Throws(CirJacksonException::class)
    override fun serializeWithType(value: T, generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        val referencedValue = getReferencedIfPresent(value)

        if (referencedValue == null) {
            if (myUnwrapper == null) {
                serializers.defaultSerializeNullValue(generator)
            }

            return
        }

        val serializer = myValueSerializer ?: findCachedSerializer(serializers, referencedValue::class)

        serializer.serializeWithType(referencedValue, generator, serializers, typeSerializer)
    }

    /*
     *******************************************************************************************************************
     * Introspection support
     *******************************************************************************************************************
     */

    override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
        var serializer = myValueSerializer

        if (serializer == null) {
            serializer = findSerializer(visitor.provider!!, myReferredType, myProperty)

            if (myUnwrapper != null) {
                serializer = serializer.unwrappingSerializer(myUnwrapper)
            }
        }

        serializer.acceptCirJsonFormatVisitor(visitor, myReferredType)
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    /**
     * Helper method that encapsulates logic of retrieving and caching required serializer.
     */
    private fun findCachedSerializer(context: SerializerProvider, rawType: KClass<*>): ValueSerializer<Any> {
        var serializer = myDynamicValueSerializers.serializerFor(rawType)

        if (serializer != null) {
            return serializer
        }

        serializer = if (myReferredType.hasGenericTypes()) {
            val fullType = context.constructSpecializedType(myReferredType, rawType)
            context.findPrimaryPropertySerializer(fullType, myProperty)
        } else {
            context.findPrimaryPropertySerializer(rawType, myProperty)
        }

        if (myUnwrapper != null) {
            serializer = serializer.unwrappingSerializer(myUnwrapper)
        }

        myDynamicValueSerializers = myDynamicValueSerializers.newWith(rawType, serializer)
        return serializer
    }

    private fun findSerializer(context: SerializerProvider, type: KotlinType,
            property: BeanProperty?): ValueSerializer<Any> {
        return context.findPrimaryPropertySerializer(type, property)
    }

    companion object {

        val MARKER_FOR_EMPTY = CirJsonInclude.Include.NON_EMPTY

    }

}