package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.annotations.CirJacksonInject
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.exception.InvalidDefinitionException
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import org.cirjson.cirjackson.databind.introspection.AnnotatedParameter
import org.cirjson.cirjackson.databind.util.Annotations
import org.cirjson.cirjackson.databind.util.classDescription
import org.cirjson.cirjackson.databind.util.name
import kotlin.reflect.KClass

/**
 * This concrete subclass implements property that is passed via Creator (constructor or static factory method). It is
 * not a full-featured implementation in that its set method should usually not be called for primary mutation (instead,
 * value must separately be passed) but some aspects are still needed (specifically, injection).
 *
 * Note on injectable values: unlike with other mutators, where deserializer and injecting are separate, here we treat
 * the two as related things. This is necessary to add proper priority, as well as to simplify coordination.
 */
open class CreatorProperty : SettableBeanProperty {

    /**
     * Placeholder that represents constructor parameter, when it is created from actual constructor. May be `null` when
     * a synthetic instance is created.
     */
    protected val myAnnotated: AnnotatedParameter?

    /**
     * ID of value to inject, if value injection should be used for this parameter (in addition to, or instead of,
     * regular deserialization).
     */
    protected val myInjectableValue: CirJacksonInject.Value?

    /**
     * In special cases, when implementing "updateValue", we cannot use constructors or factory methods, but have to
     * fall back on using a setter (or mutable field property). If so, this refers to that fallback accessor.
     *
     * Mutable only to allow setting after construction, but must be strictly set before any use.
     */
    protected var myFallbackSetter: SettableBeanProperty?

    protected val myCreatorIndex: Int

    /**
     * Marker flag that may have to be set during construction, to indicate that although property may have been
     * constructed and added as a placeholder, it represents something that should be ignored during deserialization.
     * This mostly concerns Creator properties which may not be easily deleted during processing.
     */
    protected var myIgnorable: Boolean

    protected constructor(propertyName: PropertyName?, type: KotlinType, wrapper: PropertyName?,
            typeDeserializer: TypeDeserializer?, contextAnnotations: Annotations?, parameter: AnnotatedParameter?,
            index: Int, injectable: CirJacksonInject.Value?, metadata: PropertyMetadata?) : super(propertyName, type,
            wrapper, typeDeserializer, contextAnnotations, metadata) {
        myAnnotated = parameter
        myInjectableValue = injectable
        myFallbackSetter = null
        myCreatorIndex = index
        myIgnorable = false
    }

    protected constructor(source: CreatorProperty, propertyName: PropertyName) : super(source, propertyName) {
        myAnnotated = source.myAnnotated
        myInjectableValue = source.myInjectableValue
        myFallbackSetter = source.myFallbackSetter
        myCreatorIndex = source.myCreatorIndex
        myIgnorable = source.myIgnorable
    }

    protected constructor(source: CreatorProperty, deserializer: ValueDeserializer<*>?,
            nullProvider: NullValueProvider) : super(source, deserializer, nullProvider) {
        myAnnotated = source.myAnnotated
        myInjectableValue = source.myInjectableValue
        myFallbackSetter = source.myFallbackSetter
        myCreatorIndex = source.myCreatorIndex
        myIgnorable = source.myIgnorable
    }

    protected constructor(source: CreatorProperty, typeDeserializer: TypeDeserializer?) : super(source,
            typeDeserializer) {
        myAnnotated = source.myAnnotated
        myInjectableValue = source.myInjectableValue
        myFallbackSetter = source.myFallbackSetter
        myCreatorIndex = source.myCreatorIndex
        myIgnorable = source.myIgnorable
    }

    override fun withName(propertyName: PropertyName): SettableBeanProperty {
        return CreatorProperty(this, propertyName)
    }

    override fun withValueDeserializer(deserializer: ValueDeserializer<*>): SettableBeanProperty {
        if (myValueDeserializer === deserializer) {
            return this
        }

        val nullProvider = deserializer.takeIf { myValueDeserializer === myNullProvider } ?: myNullProvider
        return CreatorProperty(this, deserializer, nullProvider)
    }

    override fun withNullProvider(nullProvider: NullValueProvider): SettableBeanProperty {
        return CreatorProperty(this, myValueDeserializer, nullProvider)
    }

    open fun withValueTypeDeserializer(typeDeserializer: TypeDeserializer?): SettableBeanProperty {
        if (myValueTypeDeserializer === typeDeserializer) {
            return this
        }

        return CreatorProperty(this, typeDeserializer)
    }

    override fun fixAccess(config: DeserializationConfig) {
        myFallbackSetter?.fixAccess(config)
    }

    /**
     * NOTE: one exception to immutability, due to problems with CreatorProperty instances being shared between Bean,
     * separate PropertyBasedCreator
     */
    open var fallbackSetter: SettableBeanProperty
        get() = throw NotImplementedError("This is only a setter")
        set(value) {
            myFallbackSetter = value
        }

    override fun markAsIgnorable() {
        myIgnorable = true
    }

    override val isIgnorable: Boolean
        get() = myIgnorable

    /*
     *******************************************************************************************************************
     * BeanProperty implementation
     *******************************************************************************************************************
     */

    override fun <A : Annotation> getAnnotation(clazz: KClass<A>): A? {
        return myAnnotated?.getAnnotation(clazz)
    }

    override val member: AnnotatedMember?
        get() = myAnnotated

    override val creatorIndex: Int
        get() = myCreatorIndex

    /*
     *******************************************************************************************************************
     * SettableBeanProperty implementation
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun deserializeAndSet(parser: CirJsonParser, context: DeserializationContext, instance: Any) {
        verifySetter().set(instance, deserialize(parser, context))
    }

    @Throws(CirJacksonException::class)
    override fun deserializeSetAndReturn(parser: CirJsonParser, context: DeserializationContext, instance: Any): Any? {
        return verifySetter().setAndReturn(instance, deserialize(parser, context))
    }

    override fun set(instance: Any, value: Any?) {
        verifySetter().set(instance, value)
    }

    override fun setAndReturn(instance: Any, value: Any?): Any {
        return verifySetter().setAndReturn(instance, value)
    }

    override val metadata: PropertyMetadata
        get() {
            val metadata = super.metadata
            return myFallbackSetter?.let { metadata.withMergeInfo(it.metadata.mergeInfo) } ?: metadata
        }

    override val injectableValueId: Any?
        get() = myInjectableValue?.id

    override val isInjectionOnly: Boolean
        get() = !(myInjectableValue?.willUseInput(true) ?: true)

    /*
     *******************************************************************************************************************
     * Overridden methods, other
     *******************************************************************************************************************
     */

    override fun toString(): String {
        return "[creator property, name ${name.name()}; inject id '$injectableValueId']"
    }

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    private fun verifySetter(): SettableBeanProperty {
        return myFallbackSetter ?: reportMissingSetter()
    }

    @Throws(CirJacksonException::class)
    private fun reportMissingSetter(): Nothing {
        val classDescription = myAnnotated?.owner?.declaringClass?.classDescription ?: "UNKNOWN TYPE"
        val message = "No fallback setter/field defined for creator property ${name.name()} (of $classDescription)"
        throw InvalidDefinitionException.from(null as CirJsonParser?, message, type)
    }

    companion object {

        /**
         * Factory method for creating {@link CreatorProperty} instances
         *
         * @param propertyName Name of the logical property
         *
         * @param type Type of the property, used to find deserializer
         *
         * @param wrapper Possible wrapper to use for logical property, if any
         *
         * @param typeDeserializer Type deserializer to use for handling polymorphic type information, if one is needed
         *
         * @param contextAnnotations Contextual annotations (usually by class that declares creator [constructor,
         * factory method] that includes this property)
         *
         * @param parameter Representation of property, constructor or factory method parameter; used for accessing
         * annotations of the property
         *
         * @param injectable Information about injectable value, if any
         *
         * @param index Index of this property within creator invocation
         *
         * @param metadata Additional information about property
         */
        fun construct(propertyName: PropertyName?, type: KotlinType, wrapper: PropertyName?,
                typeDeserializer: TypeDeserializer?, contextAnnotations: Annotations?, parameter: AnnotatedParameter?,
                index: Int, injectable: CirJacksonInject.Value?, metadata: PropertyMetadata?): CreatorProperty {
            return CreatorProperty(propertyName, type, wrapper, typeDeserializer, contextAnnotations, parameter, index,
                    injectable, metadata)
        }

    }

}