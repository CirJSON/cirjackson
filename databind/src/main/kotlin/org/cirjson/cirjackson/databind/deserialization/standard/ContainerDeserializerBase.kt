package org.cirjson.cirjackson.databind.deserialization.standard

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.deserialization.NullValueProvider
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.deserialization.ValueInstantiator
import org.cirjson.cirjackson.databind.deserialization.implementation.NullsConstantProvider
import org.cirjson.cirjackson.databind.util.AccessPattern
import org.cirjson.cirjackson.databind.util.throwIfError
import org.cirjson.cirjackson.databind.util.throwIfRuntimeException
import java.lang.reflect.InvocationTargetException

/**
 * Intermediate base deserializer class that adds more shared accessor so that other classes can access information
 * about contained (value) types
 */
abstract class ContainerDeserializerBase<T : Any> : StandardDeserializer<T>, ValueInstantiator.Gettable {

    protected val myContainerType: KotlinType

    /**
     * Handler needed for dealing with `nulls`.
     */
    protected val myNullProvider: NullValueProvider?

    /**
     * Marker flag set if the [myNullProvider] indicates that all `null` content values should be skipped (instead of
     * being possibly converted).
     */
    protected val mySkipNullValues: Boolean

    /**
     * Specific override for this instance (from proper, or global per-type overrides) to indicate whether single value
     * may be taken to mean an unwrapped one-element array or not. If `null`, left to global defaults.
     */
    protected val myUnwrapSingle: Boolean?

    protected constructor(selfType: KotlinType, nullValueProvider: NullValueProvider?, unwrapSingle: Boolean?) : super(
            selfType) {
        myContainerType = selfType
        myNullProvider = nullValueProvider
        myUnwrapSingle = unwrapSingle
        mySkipNullValues = NullsConstantProvider.isSkipper(nullValueProvider)
    }

    protected constructor(selfType: KotlinType) : this(selfType, null, null)

    protected constructor(base: ContainerDeserializerBase<*>) : this(base, base.myNullProvider, base.mySkipNullValues)

    protected constructor(base: ContainerDeserializerBase<*>, nullValueProvider: NullValueProvider?,
            unwrapSingle: Boolean?) : super(base.myContainerType) {
        myContainerType = base.myContainerType
        myNullProvider = nullValueProvider
        myUnwrapSingle = unwrapSingle
        mySkipNullValues = NullsConstantProvider.isSkipper(nullValueProvider)
    }

    /*
     *******************************************************************************************************************
     * Overrides
     *******************************************************************************************************************
     */

    override val valueType: KotlinType
        get() = myContainerType

    override fun supportsUpdate(config: DeserializationConfig): Boolean? {
        return true
    }

    override fun findBackReference(referenceName: String): SettableBeanProperty? {
        val valueDeserializer = contentDeserializer ?: throw IllegalArgumentException(
                "Cannot handle managed/back reference '$referenceName': type: container deserializer of type ${this::class.qualifiedName} returned null for 'contentDeserializer'")
        return valueDeserializer.findBackReference(referenceName)
    }

    /*
     *******************************************************************************************************************
     * Extended API
     *******************************************************************************************************************
     */

    /**
     * Accessor for declared type of contained value elements; either exact type, or one of its supertypes.
     */
    open val contentType: KotlinType?
        get() = myContainerType.contentType

    /**
     * Accessor for deserializer use for deserializing content values.
     */
    abstract val contentDeserializer: ValueDeserializer<Any>?

    override val emptyAccessPattern: AccessPattern
        get() = AccessPattern.DYNAMIC

    @Throws(CirJacksonException::class)
    override fun getEmptyValue(context: DeserializationContext): Any? {
        val valueInstantiator = valueInstantiator

        if (!(valueInstantiator?.canCreateUsingDefault() ?: false)) {
            val type = valueType
            return context.reportBadDefinition(type, "Cannot create empty instance of $type, no default Creator")
        }

        return valueInstantiator.createUsingDefault(context)
    }

    /*
     *******************************************************************************************************************
     * Shared methods for subclasses
     *******************************************************************************************************************
     */

    /**
     * Helper method called by various Map(-like) deserializers when encountering a processing problem (whether from
     * underlying parser, i/o, or something else).
     */
    @Suppress("ThrowableNotThrown")
    protected open fun wrapAndThrow(context: DeserializationContext, throwable: Throwable, reference: Any,
            key: String?): Nothing {
        var realThrowable = throwable

        while (realThrowable is InvocationTargetException && realThrowable.cause != null) {
            realThrowable = realThrowable.cause!!
        }

        realThrowable.throwIfError()

        if (!context.isEnabled(DeserializationFeature.WRAP_EXCEPTIONS)) {
            realThrowable.throwIfRuntimeException()
        }

        throw CirJacksonException.wrapWithPath(realThrowable, reference, key ?: "N/A")
    }

}