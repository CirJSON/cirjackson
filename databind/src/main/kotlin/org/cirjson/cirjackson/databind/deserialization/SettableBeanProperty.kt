package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonObjectFormatVisitor
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.deserialization.implementation.FailingDeserializer
import org.cirjson.cirjackson.databind.deserialization.implementation.NullsConstantProvider
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import org.cirjson.cirjackson.databind.introspection.BeanPropertyDefinition
import org.cirjson.cirjackson.databind.introspection.ConcreteBeanPropertyBase
import org.cirjson.cirjackson.databind.introspection.ObjectIdInfo
import org.cirjson.cirjackson.databind.util.*
import kotlin.reflect.KClass

/**
 * Base class for deserializable properties of a bean: contains both type and name definitions, and reflection-based set
 * functionality. Concrete subclasses implement details, so that field- and setter-backed properties, as well as a few
 * more esoteric variations, can be handled.
 */
abstract class SettableBeanProperty : ConcreteBeanPropertyBase {

    /**
     * Logical name of the property (often but not always derived from the setter method name)
     */
    protected val myPropertyName: PropertyName

    /**
     * Base type for property; may be a supertype of actual value.
     */
    protected val myType: KotlinType

    protected val myWrapperName: PropertyName?

    /**
     * Class that contains this property (either class that declares the property or one of its subclasses), class that
     * is deserialized using deserializer that contains this property.
     */
    protected val myContextAnnotations: Annotations?

    /**
     * Deserializer used for handling property value.
     */
    protected val myValueDeserializer: ValueDeserializer<Any>

    /**
     * If value will contain type information (to support polymorphic handling), this is the type deserializer used to
     * handle type resolution.
     */
    protected val myValueTypeDeserializer: TypeDeserializer?

    /**
     * Entity used for possible translation from `null` into non-null value of type of this property. Often same as
     * `myValueDeserializer`, but not always.
     */
    protected val myNullProvider: NullValueProvider

    /*
     *******************************************************************************************************************
     * Configuration that is not yet immutable; generally assigned during initialization process but cannot be passed to
     * constructor.
     *******************************************************************************************************************
     */

    /**
     * If property represents a managed (forward) reference, we will need the name of reference for later linking.
     */
    protected var myManagedReferenceName: String? = null

    /**
     * This is the information for object identity associated with the property.
     */
    protected var myObjectIdInfo: ObjectIdInfo? = null

    /**
     * Helper object used for checking whether this property is to be included in the active view, if property is
     * view-specific; `null` otherwise.
     */
    protected var myViewMatcher: ViewMatcher? = null

    /**
     * Index of property (within all property of a bean); assigned
     * when all properties have been collected. Order of entries
     * is arbitrary, but once indexes are assigned they are not
     * changed.
     */
    protected var myPropertyIndex = -1

    /*
     *******************************************************************************************************************
     * Lifecycle (construct & configure)
     *******************************************************************************************************************
     */

    protected constructor(propertyDefinition: BeanPropertyDefinition, type: KotlinType,
            typeDeserializer: TypeDeserializer?, contextAnnotations: Annotations?) : this(propertyDefinition.fullName,
            type, propertyDefinition.wrapperName, typeDeserializer, contextAnnotations, propertyDefinition.metadata)

    protected constructor(propertyName: PropertyName?, type: KotlinType, wrapper: PropertyName?,
            typeDeserializer: TypeDeserializer?, contextAnnotations: Annotations?, metadata: PropertyMetadata?) : super(
            metadata) {
        myPropertyName = propertyName ?: PropertyName.NO_NAME
        myType = type
        myWrapperName = wrapper
        myContextAnnotations = contextAnnotations
        myValueDeserializer = MISSING_VALUE_DESERIALIZER
        myValueTypeDeserializer = typeDeserializer?.forProperty(this)
        myNullProvider = MISSING_VALUE_DESERIALIZER
    }

    /**
     * Constructor only used by [org.cirjson.cirjackson.databind.deserialization.implementation.ObjectIdValueProperty].
     */
    protected constructor(propertyName: PropertyName?, type: KotlinType, metadata: PropertyMetadata?,
            valueDeserializer: ValueDeserializer<Any>) : super(metadata) {
        myPropertyName = propertyName ?: PropertyName.NO_NAME
        myType = type
        myWrapperName = null
        myContextAnnotations = null
        myValueDeserializer = valueDeserializer
        myValueTypeDeserializer = null
        myNullProvider = valueDeserializer
    }

    /**
     * Basic copy-constructor for subclasses to use.
     */
    protected constructor(source: SettableBeanProperty) : super(source) {
        myPropertyName = source.myPropertyName
        myType = source.myType
        myWrapperName = source.myWrapperName
        myContextAnnotations = source.myContextAnnotations
        myValueDeserializer = source.myValueDeserializer
        myValueTypeDeserializer = source.myValueTypeDeserializer
        myNullProvider = source.myNullProvider
        myManagedReferenceName = source.myManagedReferenceName
        myObjectIdInfo = source.myObjectIdInfo
        myViewMatcher = source.myViewMatcher
        myPropertyIndex = source.myPropertyIndex
    }

    /**
     * Copy-with-deserializer-change constructor for subclasses to use.
     */
    @Suppress("UNCHECKED_CAST")
    protected constructor(source: SettableBeanProperty, deserializer: ValueDeserializer<*>?,
            nullProvider: NullValueProvider) : super(source) {
        myPropertyName = source.myPropertyName
        myType = source.myType
        myWrapperName = source.myWrapperName
        myContextAnnotations = source.myContextAnnotations
        myValueDeserializer = deserializer as ValueDeserializer<Any>? ?: MISSING_VALUE_DESERIALIZER
        myValueTypeDeserializer = source.myValueTypeDeserializer
        myNullProvider = nullProvider.takeUnless { it === MISSING_VALUE_DESERIALIZER } ?: myValueDeserializer
        myManagedReferenceName = source.myManagedReferenceName
        myObjectIdInfo = source.myObjectIdInfo
        myViewMatcher = source.myViewMatcher
        myPropertyIndex = source.myPropertyIndex
    }

    /**
     * Copy-with-property-name-change constructor for subclasses to use.
     */
    protected constructor(source: SettableBeanProperty, propertyName: PropertyName) : super(source) {
        myPropertyName = propertyName
        myType = source.myType
        myWrapperName = source.myWrapperName
        myContextAnnotations = source.myContextAnnotations
        myValueDeserializer = source.myValueDeserializer
        myValueTypeDeserializer = source.myValueTypeDeserializer
        myNullProvider = source.myNullProvider
        myManagedReferenceName = source.myManagedReferenceName
        myObjectIdInfo = source.myObjectIdInfo
        myViewMatcher = source.myViewMatcher
        myPropertyIndex = source.myPropertyIndex
    }

    /**
     * Copy-with-type-deserializer-change constructor for subclasses to use.
     */
    protected constructor(source: SettableBeanProperty, typeDeserializer: TypeDeserializer?) : super(source) {
        myPropertyName = source.myPropertyName
        myType = source.myType
        myWrapperName = source.myWrapperName
        myContextAnnotations = source.myContextAnnotations
        myValueDeserializer = source.myValueDeserializer
        myValueTypeDeserializer = typeDeserializer
        myNullProvider = source.myNullProvider
        myManagedReferenceName = source.myManagedReferenceName
        myObjectIdInfo = source.myObjectIdInfo
        myViewMatcher = source.myViewMatcher
        myPropertyIndex = source.myPropertyIndex
    }

    /**
     * Fluent factory method for constructing and returning a new instance with specified value deserializer. Note that
     * this method should NOT change configuration of this instance.
     *
     * @param deserializer Deserializer to assign to the new property instance
     *
     * @return Newly constructed instance, if value deserializer differs from the one used for this instance; or 'this'
     * if not.
     */
    abstract fun withValueDeserializer(deserializer: ValueDeserializer<*>): SettableBeanProperty

    /**
     * Fluent factory method for constructing and returning a new instance with specified property name. Note that this
     * method should NOT change configuration of this instance.
     *
     * @param propertyName Name to use for the new instance.
     *
     * @return Newly constructed instance, if property name differs from the one used for this instance; or 'this' if
     * not.
     */
    abstract fun withName(propertyName: PropertyName): SettableBeanProperty

    open fun withSimpleName(simpleName: String?): SettableBeanProperty {
        val name = myPropertyName.withSimpleName(simpleName).internSimpleName()

        if (name === myPropertyName) {
            return this
        }

        return withName(name)
    }

    abstract fun withNullProvider(nullProvider: NullValueProvider): SettableBeanProperty

    open var views: Array<KClass<*>>?
        get() = throw UnsupportedOperationException("views can only be set")
        set(value) {
            myViewMatcher = value?.let { ViewMatcher.construct(it) }
        }

    /**
     * Method used to assign index for property.
     */
    open fun assignIndex(index: Int) {
        if (myPropertyIndex != -1 && myPropertyIndex != index) {
            throw IllegalStateException(
                    "Property '$name' already had index ($myPropertyIndex), trying to assign $index")
        }

        myPropertyIndex = index
    }

    /**
     * Method called to ensure that the mutator has proper access rights to be called, as per configuration. Overridden
     * by implementations that have mutators that require access, fields and setters. By default, this method does
     * nothing.
     */
    open fun fixAccess(config: DeserializationConfig) {
        // No op
    }

    /**
     * By default, this method does nothing.
     */
    open fun markAsIgnorable() {
        // No op
    }

    open val isIgnorable: Boolean
        get() = false

    /*
     *******************************************************************************************************************
     * BeanProperty implementation
     *******************************************************************************************************************
     */

    override val name: String
        get() = myPropertyName.simpleName

    override val fullName: PropertyName
        get() = myPropertyName

    override val type: KotlinType
        get() = myType

    override val wrapperName: PropertyName?
        get() = myWrapperName

    override fun <A : Annotation> getContextAnnotation(clazz: KClass<A>): A? {
        return myContextAnnotations!![clazz]
    }

    override fun depositSchemaProperty(objectVisitor: CirJsonObjectFormatVisitor, provider: SerializerProvider) {
        if (isRequired) {
            objectVisitor.property(this)
        } else {
            objectVisitor.optionalProperty(this)
        }
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    open val declaringClass: KClass<*>
        get() = member!!.declaringClass

    open var managedReferenceName: String?
        get() = myManagedReferenceName
        set(value) {
            myManagedReferenceName = value
        }

    open var objectIdInfo: ObjectIdInfo?
        get() = myObjectIdInfo
        set(value) {
            myObjectIdInfo = value
        }

    open fun hasValueDeserializer(): Boolean {
        return myValueDeserializer !== MISSING_VALUE_DESERIALIZER
    }

    open fun hasValueTypeDeserializer(): Boolean {
        return myValueTypeDeserializer != null
    }

    open val valueDeserializer: ValueDeserializer<Any>?
        get() = myValueDeserializer.takeUnless { it === MISSING_VALUE_DESERIALIZER }

    open val valueTypeDeserializer: TypeDeserializer?
        get() = myValueTypeDeserializer

    open val nullValueProvider: NullValueProvider
        get() = myNullProvider

    open fun visibleInView(activeView: KClass<*>): Boolean {
        return myViewMatcher?.isVisibleForView(activeView) ?: true
    }

    open fun hasViews(): Boolean {
        return myViewMatcher != null
    }

    /**
     * Accessor for unique index of this property; indexes are assigned once all properties of a
     * [BeanDeserializer][org.cirjson.cirjackson.databind.deserialization.bean.BeanDeserializer] have been collected.
     */
    open val propertyIndex: Int
        get() = myPropertyIndex

    /**
     * Accessor for index of the creator property: for other types of properties will simply return `-1`.
     */
    open val creatorIndex: Int
        get() = throw IllegalStateException(
                "Internal error: no creator index for property '$name' (of type ${this::class.qualifiedName})")

    /**
     * Accessor for id of injectable value, if this bean property supports value injection.
     */
    open val injectableValueId: Any?
        get() = null

    /**
     * Accessor for checking whether this property is injectable, and if so, ONLY injectable (will not bind from input).
     * Can only return `true` for Creator-backed properties.
     *
     * @return `true` if (and only if) property has injector that is also defined NOT to bind from input.
     */
    open val isInjectionOnly: Boolean
        get() = false

    /*
     *******************************************************************************************************************
     * Public API
     *******************************************************************************************************************
     */

    /**
     * Method called to deserialize appropriate value, given parser (and context), and set it using appropriate
     * mechanism. Pre-condition is that passed parser must point to the first token that should be consumed to produce
     * the value (the only value for scalars, multiple for Objects and Arrays).
     */
    @Throws(CirJacksonException::class)
    abstract fun deserializeAndSet(parser: CirJsonParser, context: DeserializationContext, instance: Any)

    /**
     * Alternative to [deserializeAndSet] that returns either return value of setter method called (if one is), or
     * `null` to indicate that no return value is available. Mostly used to support Builder style deserialization.
     */
    @Throws(CirJacksonException::class)
    abstract fun deserializeSetAndReturn(parser: CirJsonParser, context: DeserializationContext, instance: Any): Any?

    /**
     * Method called to assign given value to this property, on specified Object.
     *
     * Note: this is an optional operation, not supported by all implementations, creator-backed properties for example
     * do not support this method.
     */
    abstract fun set(instance: Any, value: Any?)

    /**
     * Method called to assign given value to this property, on specified Object, and return whatever delegating
     * accessor returned (if anything)
     *
     * Note: this is an optional operation, not supported by all implementations, creator-backed properties for example
     * do not support this method.
     */
    abstract fun setAndReturn(instance: Any, value: Any?): Any

    /**
     * This method is needed by some specialized bean deserializers, and also called by some [deserializeAndSet]
     * implementations.
     *
     * Pre-condition is that passed parser must point to the first token that should be consumed to produce the value
     * (the only value for scalars, multiple for Objects and Arrays).
     *
     * Note that this method is not open for performance reasons: to override functionality, you must override other
     * methods that call this method; this method should also not be called directly unless you really know what you are
     * doing (and probably not even then).
     */
    @Throws(CirJacksonException::class)
    fun deserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
        if (parser.hasToken(CirJsonToken.VALUE_NULL)) {
            return myNullProvider.getNullValue(context)
        }

        if (myValueTypeDeserializer != null) {
            return myValueDeserializer.deserializeWithType(parser, context, myValueTypeDeserializer)
        }

        return myValueDeserializer.deserialize(parser, context) ?: myNullProvider.getNullValue(context)
    }

    @Throws(CirJacksonException::class)
    fun deserializeWith(parser: CirJsonParser, context: DeserializationContext, toUpdate: Any): Any? {
        return if (parser.hasToken(CirJsonToken.VALUE_NULL)) {
            if (NullsConstantProvider.isSkipper(myNullProvider)) {
                toUpdate
            } else {
                myNullProvider.getNullValue(context)
            }
        } else if (myValueTypeDeserializer != null) {
            val subtype = context.typeFactory.constructType(toUpdate::class.java)
            val subtypeValueDeserializer = context.findContextualValueDeserializer(subtype, this)
            subtypeValueDeserializer.deserialize(parser, context, toUpdate)
        } else {
            myValueDeserializer.deserialize(parser, context, toUpdate) ?: if (NullsConstantProvider.isSkipper(
                            myNullProvider)) {
                toUpdate
            } else {
                myNullProvider.getNullValue(context)
            }
        }
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected open fun throwAsCirJacksonException(parser: CirJsonParser?, exception: Exception, value: Any?): Nothing {
        if (exception is IllegalArgumentException) {
            val actualType = value.className
            val builder = StringBuilder("Problem deserializing property '").append(name).append("' (expected type: ")
                    .append(type).append("; actual type: ").append(actualType).append(')')
            val originalMessage = exception.exceptionMessage()

            if (originalMessage != null) {
                builder.append(", problem: ").append(originalMessage)
            } else {
                builder.append(" (no error message provided)")
            }

            throw DatabindException.from(parser, builder.toString(), exception)
        }

        throwAsCirJacksonException(parser, exception)
    }

    @Throws(CirJacksonException::class)
    protected open fun throwAsCirJacksonException(parser: CirJsonParser?, exception: Exception): Nothing {
        val throwable = exception.throwIfRuntimeException().throwIfCirJacksonException().rootCause
        throw DatabindException.from(parser, throwable.exceptionMessage(), throwable)
    }

    @Throws(CirJacksonException::class)
    protected open fun throwAsCirJacksonException(exception: Exception, value: Any?): Nothing {
        throwAsCirJacksonException(null, exception, value)
    }

    override fun toString(): String {
        return "[property '$name']"
    }

    /*
     *******************************************************************************************************************
     * Helper classes
     *******************************************************************************************************************
     */

    /**
     * Helper class that is designed to both make it easier to subclass delegating subtypes and to reduce likelihood of
     * breakage when new methods are added.
     *
     * Class was specifically added to help with `Afterburner` module, but its use is not limited to only support it.
     */
    abstract class Delegating protected constructor(protected val myDelegate: SettableBeanProperty) :
            SettableBeanProperty(myDelegate) {

        /**
         * Method subclasses must implement, to construct a new instance with given delegate.
         */
        protected abstract fun withDelegate(delegate: SettableBeanProperty): SettableBeanProperty

        protected open fun with(delegate: SettableBeanProperty): SettableBeanProperty {
            if (myDelegate === delegate) {
                return this
            }

            return withDelegate(myDelegate)
        }

        override fun withValueDeserializer(deserializer: ValueDeserializer<*>): SettableBeanProperty {
            return with(myDelegate.withValueDeserializer(deserializer))
        }

        override fun withName(propertyName: PropertyName): SettableBeanProperty {
            return with(myDelegate.withName(propertyName))
        }

        override fun withNullProvider(nullProvider: NullValueProvider): SettableBeanProperty {
            return with(myDelegate.withNullProvider(nullProvider))
        }

        override fun assignIndex(index: Int) {
            return myDelegate.assignIndex(index)
        }

        override fun fixAccess(config: DeserializationConfig) {
            myDelegate.fixAccess(config)
        }

        /*
         ***************************************************************************************************************
         * Accessors
         ***************************************************************************************************************
         */

        override val declaringClass: KClass<*>
            get() = myDelegate.declaringClass

        override var managedReferenceName: String?
            get() = myDelegate.managedReferenceName
            set(value) {
                super.managedReferenceName = value
            }

        override var objectIdInfo: ObjectIdInfo?
            get() = myDelegate.objectIdInfo
            set(value) {
                super.objectIdInfo = value
            }

        override fun hasValueDeserializer(): Boolean {
            return myDelegate.hasValueDeserializer()
        }

        override fun hasValueTypeDeserializer(): Boolean {
            return myDelegate.hasValueTypeDeserializer()
        }

        override val valueDeserializer: ValueDeserializer<Any>?
            get() = myDelegate.valueDeserializer

        override val valueTypeDeserializer: TypeDeserializer?
            get() = myDelegate.valueTypeDeserializer

        override fun visibleInView(activeView: KClass<*>): Boolean {
            return myDelegate.visibleInView(activeView)
        }

        override fun hasViews(): Boolean {
            return myDelegate.hasViews()
        }

        override val propertyIndex: Int
            get() = myDelegate.propertyIndex

        override val creatorIndex: Int
            get() = myDelegate.creatorIndex

        override val injectableValueId: Any?
            get() = myDelegate.injectableValueId

        override val isInjectionOnly: Boolean
            get() = myDelegate.isInjectionOnly

        override val member: AnnotatedMember?
            get() = myDelegate.member

        override fun <A : Annotation> getAnnotation(clazz: KClass<A>): A? {
            return myDelegate.getAnnotation(clazz)
        }

        /*
         ***************************************************************************************************************
         * Extended API
         ***************************************************************************************************************
         */

        open val delegate: SettableBeanProperty
            get() = myDelegate

        /*
         ***************************************************************************************************************
         * Deserialization
         ***************************************************************************************************************
         */

        @Throws(CirJacksonException::class)
        override fun deserializeAndSet(parser: CirJsonParser, context: DeserializationContext, instance: Any) {
            myDelegate.deserializeAndSet(parser, context, instance)
        }

        @Throws(CirJacksonException::class)
        override fun deserializeSetAndReturn(parser: CirJsonParser, context: DeserializationContext,
                instance: Any): Any? {
            return myDelegate.deserializeSetAndReturn(parser, context, instance)
        }

        override fun set(instance: Any, value: Any?) {
            myDelegate.set(instance, value)
        }

        override fun setAndReturn(instance: Any, value: Any?): Any {
            return myDelegate.setAndReturn(instance, value)
        }

    }

    companion object {

        /**
         * To avoid nasty NPEs, let's use a placeholder for _valueDeserializer, if real deserializer is not (yet)
         * available.
         */
        val MISSING_VALUE_DESERIALIZER = FailingDeserializer("No myValueDeserializer assigned")

    }

}