package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.annotations.CirJsonCreator
import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.annotations.CirJsonInclude
import org.cirjson.cirjackson.databind.annotation.CirJsonPOJOBuilder
import org.cirjson.cirjackson.databind.introspection.*
import org.cirjson.cirjackson.databind.util.Annotations
import org.cirjson.cirjackson.databind.util.Converter
import kotlin.reflect.KClass

/**
 * Basic container for information gathered by [ClassIntrospector] to help in constructing serializers and
 * deserializers. Note that the one implementation type is [BasicBeanDescription], meaning that it is safe to upcast to
 * that type.
 *
 * @property myType Bean type information, including raw class and possible generics information
 */
abstract class BeanDescription(protected val myType: KotlinType) {

    /*
     *******************************************************************************************************************
     * Simple accessors
     *******************************************************************************************************************
     */

    /**
     * Accessor for declared type of bean being introspected, including full generic type information (from declaration)
     */
    open val type: KotlinType
        get() = myType

    open val beanClass: KClass<*>
        get() = myType.rawClass

    open val isRecordType: Boolean
        get() = myType.isRecordType

    open val isNonStaticInnerClass: Boolean
        get() = classInfo.isNonStaticInnerClass

    /**
     * Accessor for getting low-level information about KClass this item describes.
     */
    abstract val classInfo: AnnotatedClass

    /**
     * Accessor for getting information about Object ID expected to be used for this POJO type, if any.
     */
    abstract val objectIdInfo: ObjectIdInfo?

    /**
     * Method for checking whether class being described has any annotations recognized by registered annotation
     * introspector.
     */
    abstract fun hasKnownClassAnnotations(): Boolean

    /**
     * Accessor for getting collection of annotations the bean class has.
     */
    abstract val classAnnotations: Annotations

    /*
     *******************************************************************************************************************
     * Basic API for finding properties
     *******************************************************************************************************************
     */

    /**
     * @return Ordered Map with logical property name as key, and matching getter method as value.
     */
    abstract fun findProperties(): List<BeanPropertyDefinition>

    abstract val ignoredPropertyNames: Set<String>

    /**
     * Method for locating all back-reference properties (setters, fields) bean has
     */
    abstract fun findBackReferences(): List<BeanPropertyDefinition>

    /*
     *******************************************************************************************************************
     * Basic API for finding Creators, related information
     *******************************************************************************************************************
     */

    /**
     * Helper accessor that will return all non-default constructors (that is, constructors that take one or more
     * arguments) this class has.
     */
    abstract val constructors: List<AnnotatedConstructor>

    /**
     * Accessor similar to [constructors] except will also introspect `CirJsonCreator.Mode` and filter out ones marked
     * as not applicable and include mode (or lack thereof) for remaining constructors.
     *
     * Note that no other filtering (regarding visibility or other annotations) is performed
     */
    abstract val constructorsWithMode: List<AnnotatedAndMetadata<AnnotatedConstructor, CirJsonCreator.Mode>>

    /**
     * Helper method that will check all static methods of the bean class that seem like factory methods eligible to be
     * used as Creators. This requires that the static method:
     *
     * 1. Returns type compatible with bean type (same or subtype)
     *
     * 2. Is recognized from either explicit annotation (usually `@CirJsonCreator` OR naming: names `valueOf()` and
     * `fromString()` are recognized but only for 1-argument factory methods, and in case of `fromString()` argument
     * type must further be either `String` or `CharSequence`.
     *
     * Note that caller typically applies further checks for things like visibility.
     *
     * @return List of static methods considered as possible Factory methods
     */
    abstract val factoryMethods: List<AnnotatedMethod>

    /**
     * Method similar to [factoryMethods] but will return `CirJsonCreator.Mode` metadata along with qualifying factory
     * method candidates.
     */
    abstract val factoryMethodsWithMode: List<AnnotatedAndMetadata<AnnotatedMethod, CirJsonCreator.Mode>>

    /**
     * Method that will locate the no-arg constructor for this class, if it has one, and that constructor has not been
     * marked as ignorable.
     */
    abstract fun findDefaultConstructor(): AnnotatedConstructor?

    /**
     * Accessor that is replacing earlier Creator introspection access methods and accessors.
     *
     * @return Container for introspected Creator candidates, if any
     */
    abstract val potentialCreators: PotentialCreators?

    /*
     *******************************************************************************************************************
     * Basic API for finding property accessors
     *******************************************************************************************************************
     */

    /**
     * Method for locating accessor (readable field, or "getter" method) that has
     * [org.cirjson.cirjackson.annotations.CirJsonKey] annotation, if any. If multiple ones are found, an error is
     * reported by throwing [IllegalArgumentException]
     */
    open fun findCirJsonKeyAccessor(): AnnotatedMember? {
        return null
    }

    /**
     * Method for locating accessor (readable field, or "getter" method) that has
     * [org.cirjson.cirjackson.annotations.CirJsonValue] annotation, if any. If multiple ones are found, an error is
     * reported by throwing [IllegalArgumentException]
     */
    abstract fun findCirJsonValueAccessor(): AnnotatedMember?

    abstract fun findAnyGetter(): AnnotatedMember?

    /**
     * Method used to locate a mutator (settable field, or 2-argument set method) of introspected class that implements
     * [org.cirjson.cirjackson.annotations.CirJsonAnySetter]. If no such mutator exists null is returned. If more than
     * one are found, an exception is thrown. Additional checks are also made to see that method signature is
     * acceptable: needs to take 2 arguments, first one String or Object; second any can be any type.
     */
    abstract fun findAnySetterAccessor(): AnnotatedMember?

    abstract fun findMethod(name: String, paramTypes: Array<KClass<*>>): AnnotatedMember?

    /*
     *******************************************************************************************************************
     * Basic API, class configuration
     *******************************************************************************************************************
     */

    /**
     * Method for finding annotation-indicated inclusion definition (if any); possibly overriding given default value.
     *
     * NOTE: does NOT use global inclusion default settings as the base, unless passed as `defaultValue`.
     */
    abstract fun findPropertyInclusion(defaultValue: CirJsonInclude.Value?): CirJsonInclude.Value?

    /**
     * Method for checking what is the expected format for POJO, as defined by possible annotations and possible
     * per-type config overrides, if any.
     */
    abstract fun findExpectedFormat(baseType: KClass<*>): CirJsonFormat.Value?

    /**
     * Method for finding [Converter] used for serializing instances of this class.
     */
    abstract fun findSerializationConverter(): Converter<Any, Any>?

    /**
     * Method for finding [Converter] used for deserializing instances of this class.
     */
    abstract fun findDeserializationConverter(): Converter<Any, Any>?

    /**
     * Method for getting possible description for the bean type, used for constructing documentation.
     */
    open fun findClassDescription(): String? {
        return null
    }

    /*
     *******************************************************************************************************************
     * Basic API, other
     *******************************************************************************************************************
     */

    abstract fun findInjectables(): Map<Any, AnnotatedMember>

    /**
     * Method for checking if the POJO type has annotations to indicate that a builder is to be used for instantiating
     * instances and handling data binding, instead of standard bean deserializer.
     */
    abstract fun findPOJOBuilder(): KClass<*>?

    /**
     * Method for finding configuration for POJO Builder class.
     */
    abstract fun findPOJOBuilderConfig(): CirJsonPOJOBuilder.Value?

    /**
     * Method called to create a "default instance" of the bean, currently only needed for obtaining default field
     * values which may be used for suppressing serialization of fields that have "not changed".
     *
     * @param fixAccess If `true`, method is allowed to fix access to the default constructor (to be able to call
     * non-public constructor); if `false`, has to use constructor as is.
     *
     * @return Instance of class represented by this descriptor, if suitable default constructor was found; `null`
     * otherwise.
     */
    abstract fun instantiateBean(fixAccess: Boolean): Any?

    /**
     * Method for finding out if the POJO specifies default view(s) to use for properties, considering both per-type
     * annotations and global default settings.
     */
    abstract fun findDefaultViews(): Array<KClass<*>>

}