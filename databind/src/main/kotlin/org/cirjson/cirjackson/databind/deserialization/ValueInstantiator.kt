package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.databind.BeanDescription
import org.cirjson.cirjackson.databind.DeserializationConfig
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.deserialization.bean.PropertyValueBuffer
import org.cirjson.cirjackson.databind.introspection.AnnotatedWithParams
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.reflect.KClass

/**
 * Class that defines simple API implemented by objects that create value instances. Some or all of the properties of
 * value instances may be initialized by instantiator, rest being populated by deserializer, to which value instance is
 * passed. Since different kinds of CirJSON values (structured and scalar) may be bound to Kotlin values, in some cases
 * instantiator fully defines resulting value; this is the case when CirJSON value is a scalar value (String, number,
 * boolean).
 * 
 * Note that this type is not parameterized (even though it would seemingly make sense), because such type information
 * cannot be use effectively during runtime: access is always using either wildcard type, or just basic [Any]; and so,
 * adding type parameter seems like unnecessary extra work.
 * 
 * Actual implementations are strongly recommended to be based on
 * [org.cirjson.cirjackson.databind.deserialization.standard.StandardValueInstantiator] which implements all methods,
 * and as such will be compatible across versions even if new methods were added to this interface.
 */
abstract class ValueInstantiator {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    /**
     * "Contextualization" method that is called after construction but before first use, to allow instantiator access
     * to context needed to possible resolve its dependencies.
     *
     * @param context Currently active deserialization context: needed to (for example) resolving
     * [TypeDeserializers][org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer].
     *
     * @return This instance, if no change, or newly constructed instance
     */
    abstract fun createContextual(context: DeserializationContext, beanDescription: BeanDescription): ValueInstantiator

    /*
     *******************************************************************************************************************
     * Metadata accessors
     *******************************************************************************************************************
     */

    /**
     * Accessor for raw (type-erased) type of instances to create.
     */
    abstract val valueClass: KClass<*>

    /**
     * Accessor that returns description of the value type this instantiator handles. Used for error messages,
     * diagnostics.
     */
    abstract val valueTypeDescription: String

    /**
     * Method that will return `true` if any of `canCreateXxx` method returns `true`: that is, if there is any way that
     * an instance could be created.
     */
    open fun canInstantiate(): Boolean {
        return canCreateUsingDefault() || canCreateUsingDelegate() || canCreateUsingArrayDelegate() ||
                canCreateFromObjectWith() || canCreateFromString() || canCreateFromInt() || canCreateFromLong() ||
                canCreateFromBigInteger() || canCreateFromDouble() || canCreateFromBigDecimal() ||
                canCreateFromBoolean()
    }

    /**
     * Method that can be called to check whether a String-based creator is available for this instantiator.
     * 
     * NOTE: does NOT include possible case of fallbacks, or coercion; only considers explicit creator.
     */
    open fun canCreateFromString(): Boolean {
        return false
    }

    /**
     * Method that can be called to check whether an Int based creator is available to use (to call [createFromInt]).
     */
    open fun canCreateFromInt(): Boolean {
        return false
    }

    /**
     * Method that can be called to check whether a Long based creator is available to use (to call [createFromLong]).
     */
    open fun canCreateFromLong(): Boolean {
        return false
    }

    /**
     * Method that can be called to check whether a BigInteger based creator is available to use (to call
     * [createFromBigInteger]).
     */
    open fun canCreateFromBigInteger(): Boolean {
        return false
    }

    /**
     * Method that can be called to check whether a Double based creator is available to use (to call
     * [createFromDouble]).
     */
    open fun canCreateFromDouble(): Boolean {
        return false
    }

    /**
     * Method that can be called to check whether a BigDecimal based creator is available to use (to call
     * [createFromBigDecimal]).
     */
    open fun canCreateFromBigDecimal(): Boolean {
        return false
    }

    /**
     * Method that can be called to check whether a Boolean based creator is available to use (to call
     * [createFromBoolean]).
     */
    open fun canCreateFromBoolean(): Boolean {
        return false
    }

    /**
     * Method that can be called to check whether a default creator (constructor, or no-arg static factory method) is
     * available for this instantiator
     */
    open fun canCreateUsingDefault(): Boolean {
        return false
    }

    /**
     * Method that can be called to check whether a delegate-based creator (single-arg constructor or factory method) is
     * available for this instantiator
     */
    open fun canCreateUsingDelegate(): Boolean {
        return false
    }

    /**
     * Method that can be called to check whether a array-delegate-based creator (single-arg constructor or factory
     * method) is available for this instantiator
     */
    open fun canCreateUsingArrayDelegate(): Boolean {
        return false
    }

    /**
     * Method that can be called to check whether a property-based creator (argument-taking constructor or factory
     * method) is available to instantiate values from CirJSON Object
     */
    open fun canCreateFromObjectWith(): Boolean {
        return false
    }

    /**
     * Method called to determine types of instantiation arguments to use when creating instances with creator arguments
     * (when [canCreateFromObjectWith] returns `true`). These arguments are bound from CirJSON, using specified property
     * types to locate deserializers.
     * 
     * NOTE: all properties will be of type [org.cirjson.cirjackson.databind.deserialization.CreatorProperty].
     */
    open fun getFromObjectArguments(config: DeserializationConfig): Array<SettableBeanProperty>? {
        return null
    }

    /**
     * Method that can be used to determine what is the type of delegate type to use, if any. If no delegates are used,
     * will return `null`. If non-`null` type is returned, deserializer will bind CirJSON into specified type (using
     * standard deserializer for that type), and pass that to instantiator.
     */
    open fun getDelegateType(config: DeserializationConfig): KotlinType? {
        return null
    }

    /**
     * Method that can be used to determine what is the type of array delegate type to use, if any. If no delegates are
     * used, will return `null`. If non-`null` type is returned, deserializer will bind CirJSON into specified type
     * (using standard deserializer for that type), and pass that to instantiator.
     */
    open fun getArrayDelegateType(config: DeserializationConfig): KotlinType? {
        return null
    }

    /*
     *******************************************************************************************************************
     * Instantiation methods for CirJSON Object
     *******************************************************************************************************************
     */

    /**
     * Method called to create value instance from a CirJSON value when no data needs to passed to creator (constructor,
     * factory method); typically this will call the default constructor of the value object. It will only be used if
     * more specific creator methods are not applicable; hence "default".
     * 
     * This method is called if [getFromObjectArguments] returns `null` or empty Array.
     */
    @Throws(CirJacksonException::class)
    open fun createUsingDefault(context: DeserializationContext): Any? {
        return context.handleMissingInstantiator(valueClass, this, null, "no default no-arguments constructor found")
    }

    /**
     * Method called to create value instance from CirJSON Object when instantiation arguments are passed; this is done,
     * for example when passing information specified with "Creator" annotations.
     * 
     * This method is called if [getFromObjectArguments] returns a non-empty Array of arguments.
     */
    @Throws(CirJacksonException::class)
    open fun createFromObjectWith(context: DeserializationContext, args: Array<Any?>): Any? {
        return context.handleMissingInstantiator(valueClass, this, null, "no creator with arguments specified")
    }

    /**
     * Combination of [createUsingDefault] and [createFromObjectWith] which will call former first, if possible; or
     * latter if possible (with `null` arguments); and if neither works throw an exception.
     */
    @Throws(CirJacksonException::class)
    open fun createUsingDefaultOrWithoutArguments(context: DeserializationContext): Any? {
        return context.handleMissingInstantiator(valueClass, this, null,
                "neither default (no-arguments) nor with-arguments Creator found")
    }

    /**
     * Method that delegates to [createFromObjectWith] by default, but can be overridden if the application should have
     * customized behavior with respect to missing properties.
     * 
     * The default implementation of this method uses [PropertyValueBuffer.getParameters] to read and validate all
     * properties in bulk, possibly substituting defaults for missing properties or throwing exceptions for missing
     * properties. An overridden implementation of this method could, for example, use
     * [PropertyValueBuffer.hasParameter] and [PropertyValueBuffer.getParameter] to safely read the present properties
     * only, and to have some other behavior for the missing properties.
     */
    @Throws(CirJacksonException::class)
    open fun createFromObjectWith(context: DeserializationContext, properties: Array<SettableBeanProperty>,
            buffer: PropertyValueBuffer): Any? {
        return createFromObjectWith(context, buffer.getParameters(properties))
    }

    /**
     * Method to called to create value instance from CirJSON Object using an intermediate "delegate" value to pass to
     * creator method
     */
    @Throws(CirJacksonException::class)
    open fun createUsingDelegate(context: DeserializationContext, delegate: Any?): Any? {
        return context.handleMissingInstantiator(valueClass, this, null, "no delegate creator specified")
    }

    /**
     * Method to called to create value instance from CirJSON Array using an intermediate "delegate" value to pass to
     * creator method
     */
    @Throws(CirJacksonException::class)
    open fun createUsingArrayDelegate(context: DeserializationContext, delegate: Any?): Any? {
        return context.handleMissingInstantiator(valueClass, this, null, "no array delegate creator specified")
    }

    /*
     *******************************************************************************************************************
     * Instantiation methods for CirJSON scalar types (String, Number, Boolean)
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    open fun createFromString(context: DeserializationContext, value: String?): Any? {
        return context.handleMissingInstantiator(valueClass, this, null,
                "no String-argument constructor/factory method to deserialize from String value ('$value')")
    }

    @Throws(CirJacksonException::class)
    open fun createFromInt(context: DeserializationContext, value: Int): Any? {
        return context.handleMissingInstantiator(valueClass, this, null,
                "no Int-argument constructor/factory method to deserialize from Number value ($value)")
    }

    @Throws(CirJacksonException::class)
    open fun createFromLong(context: DeserializationContext, value: Long): Any? {
        return context.handleMissingInstantiator(valueClass, this, null,
                "no Long-argument constructor/factory method to deserialize from Number value ($value)")
    }

    @Throws(CirJacksonException::class)
    open fun createFromBigInteger(context: DeserializationContext, value: BigInteger): Any? {
        return context.handleMissingInstantiator(valueClass, this, null,
                "no BigInteger-argument constructor/factory method to deserialize from Number value ($value)")
    }

    @Throws(CirJacksonException::class)
    open fun createFromDouble(context: DeserializationContext, value: Double): Any? {
        return context.handleMissingInstantiator(valueClass, this, null,
                "no Double-argument constructor/factory method to deserialize from Number value ($value)")
    }

    @Throws(CirJacksonException::class)
    open fun createFromBigDecimal(context: DeserializationContext, value: BigDecimal): Any? {
        return context.handleMissingInstantiator(valueClass, this, null,
                "no BigDecimal-argument constructor/factory method to deserialize from Number value ($value)")
    }

    @Throws(CirJacksonException::class)
    open fun createFromBoolean(context: DeserializationContext, value: Boolean): Any? {
        return context.handleMissingInstantiator(valueClass, this, null,
                "no Boolean-argument constructor/factory method to deserialize from boolean value ($value)")
    }

    /*
     *******************************************************************************************************************
     * Accessors for underlying creator objects (optional)
     *******************************************************************************************************************
     */

    /**
     * Accessor that can be called to try to access member (constructor, static factory method) that is used as the
     * "default creator" (creator that is called without arguments; typically default (zero-argument) constructor of the
     * type). Note that implementations not required to return actual object they use (or, they may use some other
     * instantiation method). That is, even if [canCreateUsingDefault] returns `true`, this accessor may return `null`.
     */
    open val defaultCreator: AnnotatedWithParams?
        get() = null

    /**
     * Accessor that can be called to try to access member (constructor, static factory method) that is used as the
     * "delegate creator". Note that implementations not required to return actual object they use (or, they may use
     * some other instantiation method). That is, even if [canCreateUsingDelegate] returns `true`, this accessor may
     * return `null`.
     */
    open val delegateCreator: AnnotatedWithParams?
        get() = null

    /**
     * Accessor that can be called to try to access member (constructor, static factory method) that is used as the
     * "array delegate creator". Note that implementations not required to return actual object they use (or, they may
     * use some other instantiation method). That is, even if [canCreateUsingArrayDelegate] returns `true`, this
     * accessor may return `null`.
     */
    open val arrayDelegateCreator: AnnotatedWithParams?
        get() = null

    /**
     * Accessor that can be called to try to access member (constructor, static factory method) that is used as the
     * "non-default creator" (constructor or factory method that takes one or more arguments). Note that implementations
     * not required to return actual object they use (or, they may use some other instantiation method). That is, even
     * if [canCreateFromObjectWith] returns `true`, this accessor may return `null`.
     */
    open val withArgsCreator: AnnotatedWithParams?
        get() = null

    /*
     *******************************************************************************************************************
     * Introspection
     *******************************************************************************************************************
     */

    /**
     * Tag-on interface to let deserializers indicate that they make use of [ValueInstantiators][ValueInstantiator] and
     * there is access for instantiator assigned.
     */
    interface Gettable {

        val valueInstantiator: ValueInstantiator?

    }

    /*
     *******************************************************************************************************************
     * Standard Base implementation
     *******************************************************************************************************************
     */

    /**
     * Partial [ValueInstantiator] implementation that is strongly recommended to be used instead of directly extending
     * [ValueInstantiator] itself.
     */
    open class Base : ValueInstantiator {

        protected val myValueType: KClass<*>

        constructor(type: KClass<*>) {
            myValueType = type
        }

        constructor(type: KotlinType) {
            myValueType = type.rawClass
        }

        override fun createContextual(context: DeserializationContext,
                beanDescription: BeanDescription): ValueInstantiator {
            return this
        }

        override val valueClass: KClass<*>
            get() = myValueType

        override val valueTypeDescription: String
            get() = myValueType.qualifiedName!!

    }

    /*
     *******************************************************************************************************************
     * Standard delegate implementation
     *******************************************************************************************************************
     */

    /**
     * Delegating [ValueInstantiator] implementation meant as a base type that by default delegates methods to specified
     * fallback instantiator.
     */
    open class Delegate protected constructor(protected val myDelegate: ValueInstantiator) : ValueInstantiator() {

        override fun createContextual(context: DeserializationContext,
                beanDescription: BeanDescription): ValueInstantiator {
            val delegate = myDelegate.createContextual(context, beanDescription)

            if (delegate === myDelegate) {
                return this
            }

            return Delegate(delegate)
        }

        protected open fun delegate(): ValueInstantiator {
            return myDelegate
        }

        override val valueClass: KClass<*>
            get() = delegate().valueClass

        override val valueTypeDescription: String
            get() = delegate().valueTypeDescription

        override fun canInstantiate(): Boolean {
            return delegate().canInstantiate()
        }

        override fun canCreateFromString(): Boolean {
            return delegate().canCreateFromString()
        }

        override fun canCreateFromInt(): Boolean {
            return delegate().canCreateFromInt()
        }

        override fun canCreateFromLong(): Boolean {
            return delegate().canCreateFromLong()
        }

        override fun canCreateFromBigInteger(): Boolean {
            return delegate().canCreateFromBigInteger()
        }

        override fun canCreateFromDouble(): Boolean {
            return delegate().canCreateFromDouble()
        }

        override fun canCreateFromBigDecimal(): Boolean {
            return delegate().canCreateFromBigDecimal()
        }

        override fun canCreateFromBoolean(): Boolean {
            return delegate().canCreateFromBoolean()
        }

        override fun canCreateUsingDefault(): Boolean {
            return delegate().canCreateUsingDefault()
        }

        override fun canCreateUsingDelegate(): Boolean {
            return delegate().canCreateUsingDelegate()
        }

        override fun canCreateUsingArrayDelegate(): Boolean {
            return delegate().canCreateUsingArrayDelegate()
        }

        override fun canCreateFromObjectWith(): Boolean {
            return delegate().canCreateFromObjectWith()
        }

        override fun getFromObjectArguments(config: DeserializationConfig): Array<SettableBeanProperty>? {
            return delegate().getFromObjectArguments(config)
        }

        override fun getDelegateType(config: DeserializationConfig): KotlinType? {
            return delegate().getDelegateType(config)
        }

        override fun getArrayDelegateType(config: DeserializationConfig): KotlinType? {
            return delegate().getArrayDelegateType(config)
        }

        /*
         ***************************************************************************************************************
         * Creation methods
         ***************************************************************************************************************
         */

        override fun createUsingDefault(context: DeserializationContext): Any? {
            return delegate().createUsingDefault(context)
        }

        override fun createFromObjectWith(context: DeserializationContext, args: Array<Any?>): Any? {
            return delegate().createFromObjectWith(context, args)
        }

        override fun createFromObjectWith(context: DeserializationContext, properties: Array<SettableBeanProperty>,
                buffer: PropertyValueBuffer): Any? {
            return delegate().createFromObjectWith(context, properties, buffer)
        }

        override fun createUsingDefaultOrWithoutArguments(context: DeserializationContext): Any? {
            return delegate().createUsingDefaultOrWithoutArguments(context)
        }

        override fun createUsingDelegate(context: DeserializationContext, delegate: Any?): Any? {
            return delegate().createUsingDelegate(context, delegate)
        }

        override fun createUsingArrayDelegate(context: DeserializationContext, delegate: Any?): Any? {
            return delegate().createUsingArrayDelegate(context, delegate)
        }

        override fun createFromString(context: DeserializationContext, value: String?): Any? {
            return delegate().createFromString(context, value)
        }

        override fun createFromInt(context: DeserializationContext, value: Int): Any? {
            return delegate().createFromInt(context, value)
        }

        override fun createFromLong(context: DeserializationContext, value: Long): Any? {
            return delegate().createFromLong(context, value)
        }

        override fun createFromBigInteger(context: DeserializationContext, value: BigInteger): Any? {
            return delegate().createFromBigInteger(context, value)
        }

        override fun createFromDouble(context: DeserializationContext, value: Double): Any? {
            return delegate().createFromDouble(context, value)
        }

        override fun createFromBigDecimal(context: DeserializationContext, value: BigDecimal): Any? {
            return delegate().createFromBigDecimal(context, value)
        }

        override fun createFromBoolean(context: DeserializationContext, value: Boolean): Any? {
            return delegate().createFromBoolean(context, value)
        }

        /*
         ***************************************************************************************************************
         * Accessors for underlying creator objects (optional)
         ***************************************************************************************************************
         */

        override val defaultCreator: AnnotatedWithParams?
            get() = delegate().defaultCreator

        override val delegateCreator: AnnotatedWithParams?
            get() = delegate().delegateCreator

        override val arrayDelegateCreator: AnnotatedWithParams?
            get() = delegate().arrayDelegateCreator

        override val withArgsCreator: AnnotatedWithParams?
            get() = delegate().withArgsCreator

    }

}