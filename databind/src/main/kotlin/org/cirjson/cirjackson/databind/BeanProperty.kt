package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.annotations.CirJsonInclude
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonObjectFormatVisitor
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import org.cirjson.cirjackson.databind.type.TypeFactory
import org.cirjson.cirjackson.databind.util.FullyNamed
import kotlin.reflect.KClass

/**
 * Bean properties are logical entities that represent data that Java objects (POJOs (Plain Old Java Objects), sometimes
 * also called "beans") contain; and that are accessed using accessors (methods like getters and setters, fields,
 * constructor parameters). Instances allow access to annotations directly associated with property (via field or
 * method), as well as contextual annotations (annotations for class that contains properties).
 *
 * Instances are not typically passed when constructing serializers and deserializers, but rather only passed when
 * context is known and [ValueSerializer.createContextual] and [ValueDeserializer.createContextual] are called.
 * References may (need to) be retained by serializers and deserializers, especially when further resolving dependent
 * handlers like value serializers/deserializers or structured types.
 */
interface BeanProperty : FullyNamed {

    /**
     * Accessor to get the declared type of the property.
     */
    val type: KotlinType

    /**
     * If the property is indicated to be wrapped, the name of the wrapper element to use.
     */
    val wrapperName: PropertyName?

    /**
     * Accessor for additional optional information about property.
     *
     * @return Metadata about property; never `null`.
     */
    val metadata: PropertyMetadata

    /**
     * Whether the value for property is marked as required using annotations or associated schema. Equivalent to:
     * ```
     * metadata.isRequired
     * ```
     */
    val isRequired: Boolean

    /**
     * Accessor for checking whether there is an actual physical property behind this property abstraction or not.
     */
    val isVirtual: Boolean

    /*
     *******************************************************************************************************************
     * Access to annotation information
     *******************************************************************************************************************
     */

    /**
     * Method for finding annotation associated with this property, meaning annotation associated with one of the
     * entities used to access property.
     *
     * Note that this method should only be called for custom annotations; access to standard CirJackson annotations (or
     * ones supported by alternate [AnnotationIntrospectors][AnnotationIntrospector]) should be accessed through
     * [AnnotationIntrospector].
     */
    fun <A : Annotation> getAnnotation(clazz: KClass<A>): A?

    /**
     * Method for finding annotation associated with the context of this property; usually class in which member is
     * declared (or its subtype if processing subtype).
     *
     * Note that this method should only be called for custom annotations; access to standard CirJackson annotations (or
     * ones supported by alternate [AnnotationIntrospectors][AnnotationIntrospector]) should be accessed through
     * [AnnotationIntrospector].
     */
    fun <A : Annotation> getContextAnnotation(clazz: KClass<A>): A?

    /**
     * Accessor for primary physical entity that represents the property; annotated field, method or constructor
     * property.
     */
    val member: AnnotatedMember?

    /**
     * Helper method used to look up format settings applicable to this property, considering both possible per-type
     * configuration settings
     */
    fun findPropertyFormat(config: MapperConfig<*>, baseType: KClass<*>): CirJsonFormat.Value

    /**
     * Helper method used to only access property-specified format overrides, if any, not considering type or global
     * default format settings.
     *
     * @return Format override settings if any; `null` if no overrides
     */
    fun findFormatOverrides(config: MapperConfig<*>): CirJsonFormat.Value?

    /**
     * Convenience method that is roughly equivalent to
     * ```
     * return config.annotationIntrospector.findPropertyInclusion(member);
     * ```
     * but that also considers global default settings for inclusion
     */
    fun findPropertyInclusion(config: MapperConfig<*>, baseType: KClass<*>): CirJsonInclude.Value?

    /**
     * Method for accessing the set of possible alternate names that are accepted during deserialization.
     *
     * @return List (possibly empty) of alternate names; never `null`
     */
    fun findAliases(config: MapperConfig<*>): List<PropertyName>

    /*
     *******************************************************************************************************************
     * Schema/introspection support
     *******************************************************************************************************************
     */

    /**
     * Method that can be called to visit the type structure that this property is part of. Note that not all
     * implementations support traversal with this method; those that do not should throw
     * [UnsupportedOperationException].
     *
     * @param objectVisitor Visitor to use as the callback handler
     */
    fun depositSchemaProperty(objectVisitor: CirJsonObjectFormatVisitor, provider: SerializerProvider)

    /*
     *******************************************************************************************************************
     * Helper classes
     *******************************************************************************************************************
     */

    /**
     * Simple stand-alone implementation, useful as a placeholder or base class for more complex implementations.
     */
    open class Standard(override val fullName: PropertyName, override val type: KotlinType,
            override val wrapperName: PropertyName?, override val member: AnnotatedMember?,
            override val metadata: PropertyMetadata) : BeanProperty {

        constructor(base: Standard, newType: KotlinType) : this(base.fullName, newType, base.wrapperName, base.member,
                base.metadata)

        override val name: String
            get() = fullName.simpleName

        override val isRequired: Boolean
            get() = metadata.isRequired

        override val isVirtual = false

        override fun <A : Annotation> getAnnotation(clazz: KClass<A>): A? {
            return member?.getAnnotation(clazz)
        }

        override fun <A : Annotation> getContextAnnotation(clazz: KClass<A>): A? {
            return null
        }

        override fun findFormatOverrides(config: MapperConfig<*>): CirJsonFormat.Value? {
            return null
        }

        override fun findPropertyFormat(config: MapperConfig<*>, baseType: KClass<*>): CirJsonFormat.Value {
            val baseValue = config.getDefaultPropertyFormat(baseType)
            val introspector = config.annotationIntrospector ?: return baseValue

            if (member == null) {
                return baseValue
            }

            val value = introspector.findFormat(config, member!!) ?: return baseValue
            return baseValue.withOverrides(value)
        }

        override fun findPropertyInclusion(config: MapperConfig<*>, baseType: KClass<*>): CirJsonInclude.Value? {
            val baseValue = config.getDefaultInclusion(baseType, type.rawClass)
            val introspector = config.annotationIntrospector ?: return baseValue

            if (member == null) {
                return baseValue
            }

            val value = introspector.findPropertyInclusion(config, member!!) ?: return baseValue
            return baseValue!!.withOverrides(value)
        }

        override fun findAliases(config: MapperConfig<*>): List<PropertyName> {
            return emptyList()
        }

        /**
         * Implementation of this method throws [UnsupportedOperationException], since instances of this implementation
         * should not be used as part of actual structure visited. Rather, other implementations should handle it.
         */
        override fun depositSchemaProperty(objectVisitor: CirJsonObjectFormatVisitor, provider: SerializerProvider) {
            throw UnsupportedOperationException("Instances of ${this::class.qualifiedName} should not get visited")
        }

    }

    /**
     * Alternative "Null" implementation that can be used in cases where a non-`null` [BeanProperty] is needed
     */
    class Bogus : BeanProperty {

        override val name = ""

        override val fullName = PropertyName.NO_NAME

        override val type = TypeFactory.unknownType()

        override val wrapperName = null

        override val metadata = PropertyMetadata.STANDARD_REQUIRED_OR_OPTIONAL

        override val isRequired = false

        override val isVirtual = false

        override fun <A : Annotation> getAnnotation(clazz: KClass<A>): A? {
            return null
        }

        override fun <A : Annotation> getContextAnnotation(clazz: KClass<A>): A? {
            return null
        }

        override val member = null

        override fun findFormatOverrides(config: MapperConfig<*>): CirJsonFormat.Value? {
            return null
        }

        override fun findPropertyFormat(config: MapperConfig<*>, baseType: KClass<*>): CirJsonFormat.Value {
            return CirJsonFormat.Value.EMPTY
        }

        override fun findPropertyInclusion(config: MapperConfig<*>, baseType: KClass<*>): CirJsonInclude.Value? {
            return null
        }

        override fun findAliases(config: MapperConfig<*>): List<PropertyName> {
            return emptyList()
        }

        override fun depositSchemaProperty(objectVisitor: CirJsonObjectFormatVisitor, provider: SerializerProvider) {}

    }

    companion object {

        val EMPTY_FORMAT = CirJsonFormat.Value()

    }

}