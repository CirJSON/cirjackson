package org.cirjson.cirjackson.databind.deserialization.standard

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.deserialization.ValueInstantiator
import org.cirjson.cirjackson.databind.introspection.AnnotatedWithParams
import java.lang.reflect.InvocationTargetException
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.reflect.KClass

/**
 * Default [ValueInstantiator] implementation, which supports Creator methods that can be indicated by standard
 * CirJackson annotations.
 */
@CirJacksonStandardImplementation
open class StandardValueInstantiator : ValueInstantiator {

    /**
     * Type of values that are instantiated; used for error reporting purposes.
     */
    protected val myValueTypeDescription: String

    protected val myValueClass: KClass<*>

    /*
     *******************************************************************************************************************
     * Default (no-args) construction
     *******************************************************************************************************************
     */

    /**
     * Default (no-argument) constructor to use for instantiation (with [createUsingDefault])
     */
    protected var myDefaultCreator: AnnotatedWithParams? = null

    /*
     *******************************************************************************************************************
     * With-args (property-based) construction
     *******************************************************************************************************************
     */

    protected var myWithArgsCreator: AnnotatedWithParams? = null

    protected var myConstructorArguments: Array<SettableBeanProperty>? = null

    /*
     *******************************************************************************************************************
     * Delegate construction
     *******************************************************************************************************************
     */

    protected var myDelegateType: KotlinType? = null

    protected var myDelegateCreator: AnnotatedWithParams? = null

    protected var myDelegateArguments: Array<SettableBeanProperty?>? = null

    /*
     *******************************************************************************************************************
     * Array delegate construction
     *******************************************************************************************************************
     */

    protected var myArrayDelegateType: KotlinType? = null

    protected var myArrayDelegateCreator: AnnotatedWithParams? = null

    protected var myArrayDelegateArguments: Array<SettableBeanProperty?>? = null

    /*
     *******************************************************************************************************************
     * Scalar construction
     *******************************************************************************************************************
     */

    protected var myFromStringCreator: AnnotatedWithParams? = null

    protected var myFromIntCreator: AnnotatedWithParams? = null

    protected var myFromLongCreator: AnnotatedWithParams? = null

    protected var myFromBigIntegerCreator: AnnotatedWithParams? = null

    protected var myFromDoubleCreator: AnnotatedWithParams? = null

    protected var myFromBigDecimalCreator: AnnotatedWithParams? = null

    protected var myFromBooleanCreator: AnnotatedWithParams? = null

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    constructor(config: DeserializationConfig, valueType: KotlinType?) : super() {
        if (valueType == null) {
            myValueTypeDescription = "UNKNOWN TYPE"
            myValueClass = Any::class
        } else {
            myValueTypeDescription = valueType.toString()
            myValueClass = valueType.rawClass
        }
    }

    /**
     * Copy-constructor that subclasses can use when creating new instances by fluent-style construction
     */
    protected constructor(source: StandardValueInstantiator) : super() {
        myValueTypeDescription = source.myValueTypeDescription
        myValueClass = source.myValueClass

        myDefaultCreator = source.myDefaultCreator

        myWithArgsCreator = source.myWithArgsCreator
        myConstructorArguments = source.myConstructorArguments

        myDelegateType = source.myDelegateType
        myDelegateCreator = source.myDelegateCreator
        myDelegateArguments = source.myDelegateArguments

        myArrayDelegateType = source.myArrayDelegateType
        myArrayDelegateCreator = source.myArrayDelegateCreator
        myArrayDelegateArguments = source.myArrayDelegateArguments

        myFromStringCreator = source.myFromStringCreator
        myFromIntCreator = source.myFromIntCreator
        myFromLongCreator = source.myFromLongCreator
        myFromBigIntegerCreator = source.myFromBigIntegerCreator
        myFromDoubleCreator = source.myFromDoubleCreator
        myFromBigDecimalCreator = source.myFromBigDecimalCreator
        myFromBooleanCreator = source.myFromBooleanCreator
    }

    override fun createContextual(context: DeserializationContext,
            beanDescription: BeanDescription): ValueInstantiator {
        return this
    }

    /**
     * Method for setting properties related to instantiating values from CirJSON Object. We will choose basically only
     * one approach (out of possible three), and clear other properties
     */
    open fun configureFromObjectSettings(defaultCreator: AnnotatedWithParams?, delegateCreator: AnnotatedWithParams?,
            delegateType: KotlinType?, delegateArgs: Array<SettableBeanProperty?>?,
            withArgsCreator: AnnotatedWithParams?, constructorArgs: Array<SettableBeanProperty>?) {
        myDefaultCreator = defaultCreator
        myDelegateCreator = delegateCreator
        myDelegateType = delegateType
        myDelegateArguments = delegateArgs
        myWithArgsCreator = withArgsCreator
        myConstructorArguments = constructorArgs
    }

    open fun configureFromArraySettings(arrayDelegateCreator: AnnotatedWithParams?, arrayDelegateType: KotlinType?,
            arrayDelegateArgs: Array<SettableBeanProperty?>?) {
        myArrayDelegateCreator = arrayDelegateCreator
        myArrayDelegateType = arrayDelegateType
        myArrayDelegateArguments = arrayDelegateArgs
    }

    open fun configureFromStringCreator(creator: AnnotatedWithParams?) {
        myFromStringCreator = creator
    }

    open fun configureFromIntCreator(creator: AnnotatedWithParams?) {
        myFromIntCreator = creator
    }

    open fun configureFromLongCreator(creator: AnnotatedWithParams?) {
        myFromLongCreator = creator
    }

    open fun configureFromBigIntegerCreator(creator: AnnotatedWithParams?) {
        myFromBigIntegerCreator = creator
    }

    open fun configureFromDoubleCreator(creator: AnnotatedWithParams?) {
        myFromDoubleCreator = creator
    }

    open fun configureFromBigDecimalCreator(creator: AnnotatedWithParams?) {
        myFromBigDecimalCreator = creator
    }

    open fun configureFromBooleanCreator(creator: AnnotatedWithParams?) {
        myFromBooleanCreator = creator
    }

    /*
     *******************************************************************************************************************
     * Public API implementation; metadata
     *******************************************************************************************************************
     */

    override val valueTypeDescription: String
        get() = myValueTypeDescription

    override val valueClass: KClass<*>
        get() = myValueClass

    override fun canCreateFromString(): Boolean {
        return myFromStringCreator != null
    }

    override fun canCreateFromInt(): Boolean {
        return myFromIntCreator != null
    }

    override fun canCreateFromLong(): Boolean {
        return myFromLongCreator != null
    }

    override fun canCreateFromBigInteger(): Boolean {
        return myFromBigIntegerCreator != null
    }

    override fun canCreateFromDouble(): Boolean {
        return myFromDoubleCreator != null
    }

    override fun canCreateFromBigDecimal(): Boolean {
        return myFromBigDecimalCreator != null
    }

    override fun canCreateFromBoolean(): Boolean {
        return myFromBooleanCreator != null
    }

    override fun canCreateUsingDefault(): Boolean {
        return myDefaultCreator != null
    }

    override fun canCreateUsingDelegate(): Boolean {
        return myDelegateCreator != null
    }

    override fun canCreateUsingArrayDelegate(): Boolean {
        return myArrayDelegateCreator != null
    }

    override fun canCreateFromObjectWith(): Boolean {
        return myWithArgsCreator != null
    }

    override fun canInstantiate(): Boolean {
        return canCreateUsingDefault() || canCreateUsingDelegate() || canCreateUsingArrayDelegate() ||
                canCreateFromObjectWith() || canCreateFromString() || canCreateFromInt() || canCreateFromLong() ||
                canCreateFromBigInteger() || canCreateFromDouble() || canCreateFromBigDecimal() ||
                canCreateFromBoolean()
    }

    override fun getDelegateType(config: DeserializationConfig): KotlinType? {
        return myDelegateType
    }

    override fun getArrayDelegateType(config: DeserializationConfig): KotlinType? {
        return myArrayDelegateType
    }

    override fun getFromObjectArguments(config: DeserializationConfig): Array<SettableBeanProperty>? {
        return myConstructorArguments
    }

    /*
     *******************************************************************************************************************
     * Public API implementation; instantiation from CirJSON Object
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun createUsingDefault(context: DeserializationContext): Any? {
        val creator = myDefaultCreator ?: return super.createUsingDefault(context)

        return try {
            creator.call()
        } catch (e: Exception) {
            context.handleInstantiationProblem(myValueClass, null, rewrapConstructorProblem(context, e))
        }
    }

    @Throws(CirJacksonException::class)
    override fun createFromObjectWith(context: DeserializationContext, args: Array<Any?>): Any? {
        val creator = myWithArgsCreator ?: return super.createFromObjectWith(context, args)

        return try {
            creator.call(args)
        } catch (e: Exception) {
            context.handleInstantiationProblem(myValueClass, args, rewrapConstructorProblem(context, e))
        }
    }

    @Throws(CirJacksonException::class)
    override fun createUsingDefaultOrWithoutArguments(context: DeserializationContext): Any? {
        return if (myDefaultCreator != null) {
            createUsingDefault(context)
        } else if (myWithArgsCreator != null) {
            createFromObjectWith(context, arrayOfNulls(myConstructorArguments!!.size))
        } else {
            super.createUsingDefaultOrWithoutArguments(context)
        }
    }

    @Throws(CirJacksonException::class)
    override fun createUsingDelegate(context: DeserializationContext, delegate: Any?): Any? {
        return if (myDelegateCreator == null && myArrayDelegateCreator != null) {
            createUsingDelegate(myArrayDelegateCreator, myArrayDelegateArguments, context, delegate)
        } else {
            createUsingDelegate(myDelegateCreator, myDelegateArguments, context, delegate)
        }
    }

    @Throws(CirJacksonException::class)
    override fun createUsingArrayDelegate(context: DeserializationContext, delegate: Any?): Any? {
        return if (myArrayDelegateCreator == null && myDelegateCreator != null) {
            createUsingDelegate(myDelegateCreator, myDelegateArguments, context, delegate)
        } else {
            createUsingDelegate(myArrayDelegateCreator, myArrayDelegateArguments, context, delegate)
        }
    }

    /*
     *******************************************************************************************************************
     * Public API implementation; instantiation from CirJSON scalars
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun createFromString(context: DeserializationContext, value: String?): Any? {
        val creator = myFromStringCreator ?: return super.createFromString(context, value)

        return try {
            creator.call(value)
        } catch (e: Exception) {
            context.handleInstantiationProblem(creator.declaringClass, value, rewrapConstructorProblem(context, e))
        }
    }

    @Throws(CirJacksonException::class)
    override fun createFromInt(context: DeserializationContext, value: Int): Any? {
        myFromIntCreator?.let {
            return try {
                it.call(value)
            } catch (e: Exception) {
                context.handleInstantiationProblem(it.declaringClass, value, rewrapConstructorProblem(context, e))
            }
        }

        myFromLongCreator?.let {
            val arg = value.toLong()

            return try {
                it.call(arg)
            } catch (e: Exception) {
                context.handleInstantiationProblem(it.declaringClass, arg, rewrapConstructorProblem(context, e))
            }
        }

        myFromBigIntegerCreator?.let {
            val arg = BigInteger.valueOf(value.toLong())

            return try {
                it.call(arg)
            } catch (e: Exception) {
                context.handleInstantiationProblem(it.declaringClass, arg, rewrapConstructorProblem(context, e))
            }
        }

        myFromDoubleCreator?.let {
            val arg = value.toDouble()

            return try {
                it.call(arg)
            } catch (e: Exception) {
                context.handleInstantiationProblem(it.declaringClass, arg, rewrapConstructorProblem(context, e))
            }
        }

        return super.createFromInt(context, value)
    }

    @Throws(CirJacksonException::class)
    override fun createFromLong(context: DeserializationContext, value: Long): Any? {
        myFromLongCreator?.let {
            return try {
                it.call(value)
            } catch (e: Exception) {
                context.handleInstantiationProblem(it.declaringClass, value, rewrapConstructorProblem(context, e))
            }
        }

        myFromBigIntegerCreator?.let {
            val arg = BigInteger.valueOf(value)

            return try {
                it.call(arg)
            } catch (e: Exception) {
                context.handleInstantiationProblem(it.declaringClass, arg, rewrapConstructorProblem(context, e))
            }
        }

        myFromDoubleCreator?.let {
            val arg = value.toDouble()

            return try {
                it.call(arg)
            } catch (e: Exception) {
                context.handleInstantiationProblem(it.declaringClass, arg, rewrapConstructorProblem(context, e))
            }
        }

        return super.createFromLong(context, value)
    }

    @Throws(CirJacksonException::class)
    override fun createFromBigInteger(context: DeserializationContext, value: BigInteger): Any? {
        myFromBigIntegerCreator?.let {
            return try {
                it.call(value)
            } catch (e: Exception) {
                context.handleInstantiationProblem(it.declaringClass, value, rewrapConstructorProblem(context, e))
            }
        }

        return super.createFromBigInteger(context, value)
    }

    @Throws(CirJacksonException::class)
    override fun createFromDouble(context: DeserializationContext, value: Double): Any? {
        myFromDoubleCreator?.let {
            return try {
                it.call(value)
            } catch (e: Exception) {
                context.handleInstantiationProblem(it.declaringClass, value, rewrapConstructorProblem(context, e))
            }
        }

        myFromBigDecimalCreator?.let {
            val arg = BigDecimal.valueOf(value)

            return try {
                it.call(arg)
            } catch (e: Exception) {
                context.handleInstantiationProblem(it.declaringClass, arg, rewrapConstructorProblem(context, e))
            }
        }

        return super.createFromDouble(context, value)
    }

    @Throws(CirJacksonException::class)
    override fun createFromBigDecimal(context: DeserializationContext, value: BigDecimal): Any? {
        myFromBigDecimalCreator?.let {
            return try {
                it.call(value)
            } catch (e: Exception) {
                context.handleInstantiationProblem(it.declaringClass, value, rewrapConstructorProblem(context, e))
            }
        }

        myFromDoubleCreator?.let {
            val arg = tryConvertToDouble(value)

            return try {
                it.call(arg)
            } catch (e: Exception) {
                context.handleInstantiationProblem(it.declaringClass, arg, rewrapConstructorProblem(context, e))
            }
        }

        return super.createFromBigDecimal(context, value)
    }

    @Throws(CirJacksonException::class)
    override fun createFromBoolean(context: DeserializationContext, value: Boolean): Any? {
        val creator = myFromBooleanCreator ?: return super.createFromBoolean(context, value)

        return try {
            creator.call(value)
        } catch (e: Exception) {
            context.handleInstantiationProblem(creator.declaringClass, value, rewrapConstructorProblem(context, e))
        }
    }

    /*
     *******************************************************************************************************************
     * Extended API: configuration mutators, accessors
     *******************************************************************************************************************
     */

    override val delegateCreator: AnnotatedWithParams?
        get() = myDelegateCreator

    override val arrayDelegateCreator: AnnotatedWithParams?
        get() = myArrayDelegateCreator

    override val defaultCreator: AnnotatedWithParams?
        get() = myDefaultCreator

    override val withArgsCreator: AnnotatedWithParams?
        get() = myWithArgsCreator

    /*
     *******************************************************************************************************************
     * Internal methods
     *******************************************************************************************************************
     */

    /**
     * Helper method that will return given [Throwable] case as a [DatabindException] (if it is of that type), or call
     * [DeserializationContext.instantiationException] to produce and return suitable [DatabindException].
     */
    protected open fun wrapAsDatabindException(context: DeserializationContext,
            throwable: Throwable): DatabindException {
        return throwable as? DatabindException ?: context.instantiationException(valueClass, throwable)
    }

    /**
     * Method that subclasses may call for standard handling of an exception thrown when calling constructor or factory
     * method. Will unwrap [ExceptionInInitializerError] and [InvocationTargetException]s, then call
     * [wrapAsDatabindException].
     */
    protected open fun rewrapConstructorProblem(context: DeserializationContext,
            throwable: Throwable): DatabindException {
        var realThrowable = throwable

        if (realThrowable is ExceptionInInitializerError || throwable is InvocationTargetException) {
            realThrowable.cause?.let { realThrowable = it }
        }

        return wrapAsDatabindException(context, realThrowable)
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    private fun createUsingDelegate(delegateCreator: AnnotatedWithParams?,
            delegateArguments: Array<SettableBeanProperty?>?, context: DeserializationContext, delegate: Any?): Any? {
        delegateCreator ?: throw IllegalStateException("No delegate constructor for $valueTypeDescription")

        return try {
            delegateArguments ?: return delegateCreator.call(delegate)
            val length = delegateArguments.size

            val args = Array<Any?>(length) {
                val property = delegateArguments[it]

                if (property == null) {
                    delegate
                } else {
                    context.findInjectableValue(property.injectableValueId!!, property, null)
                }
            }

            delegateCreator.call(args)
        } catch (e: Exception) {
            throw rewrapConstructorProblem(context, e)
        }
    }

    companion object {

        fun tryConvertToDouble(value: BigDecimal): Double? {
            val doubleValue = value.toDouble()
            return doubleValue.takeUnless { it.isInfinite() }
        }

    }

}