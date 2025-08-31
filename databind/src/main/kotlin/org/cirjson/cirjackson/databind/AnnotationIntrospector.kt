package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.annotations.*
import org.cirjson.cirjackson.core.Versioned
import org.cirjson.cirjackson.databind.cirjsontype.NamedType
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.introspection.*

/**
 * Abstract class that defines API used for introspecting annotation-based configuration for serialization and
 * deserialization. Separated so that different sets of annotations can be supported, and support plugged-in
 * dynamically.
 *
 * Although default implementations are based on using annotations as the only (or at least main) information source,
 * custom implementations are not limited in such a way, and in fact there is no expectation they should be. So the name
 * is a bit of a misnomer; this is a general configuration introspection facility.
 *
 * NOTE: due to rapid addition of new methods (and changes to existing methods), it is **strongly** recommended that
 * custom implementations should not directly extend this class, but rather extend [NopAnnotationIntrospector]. This
 * way, added methods will not break backwards compatibility of custom annotation introspectors.
 */
abstract class AnnotationIntrospector : Versioned {

    /*
     *******************************************************************************************************************
     * Access to possibly chained introspectors
     *******************************************************************************************************************
     */

    /**
     * Method that can be used to collect all "real" introspectors that this introspector contains, if any; or this
     * introspector if it is not a container. Used to get access to all container introspectors in their priority order.
     *
     * Default implementation returns a Singleton list with this introspector as contents. This usually works for
     * subclasses, except for proxy or delegating "container introspectors" which need to override implementation.
     *
     * @return Collection of all introspectors starting with this one, in case multiple introspectors are chained
     */
    open fun allIntrospectors(): Collection<AnnotationIntrospector> {
        return listOf(this)
    }

    /**
     * Method that can be used to collect all "real" introspectors that this introspector contains, if any; or this
     * introspector if it is not a container. Used to get access to all container introspectors in their priority order.
     *
     * Default implementation adds this introspector in result; this usually works for subclasses, except for proxy or
     * delegating "container introspectors" which need to override implementation.
     *
     * @param result Container to add introspectors to
     *
     * @return Passed in `Collection` filled with introspectors as explained above
     */
    open fun allIntrospectors(result: MutableCollection<AnnotationIntrospector>): Collection<AnnotationIntrospector> {
        return result.apply { add(this@AnnotationIntrospector) }
    }

    /*
     *******************************************************************************************************************
     * Construction
     *******************************************************************************************************************
     */

    /**
     * Method for checking whether given annotation is considered an annotation bundle: if so, all meta-annotations it
     * has will be used instead of annotation ("bundle") itself.
     *
     * @param annotation Annotated entity to introspect
     *
     * @return `true` if given annotation is considered an annotation bundle; `false` if not
     */
    open fun isAnnotationBundle(annotation: Annotation): Boolean {
        return false
    }

    /*
     *******************************************************************************************************************
     * Annotations for Object ID handling
     *******************************************************************************************************************
     */

    /**
     * Method for checking whether given annotated thing (type, or accessor) indicates that values referenced (values of
     * type of annotated class, or values referenced by annotated property; latter having precedence) should include
     * Object Identifier, and if so, specify details of Object Identity used.
     *
     * @param config Effective mapper configuration in use
     *
     * @param annotated Annotated entity to introspect
     *
     * @return Details of Object ID as explained above, if Object ID handling to be applied; `null` otherwise.
     */
    open fun findObjectIdInfo(config: MapperConfig<*>, annotated: Annotated): ObjectIdInfo? {
        return null
    }

    /**
     * Method for figuring out additional properties of an Object Identity reference
     *
     * @param config Effective mapper configuration in use
     *
     * @param annotated Annotated entity to introspect
     *
     * @param objectIdInfo (optional) Base Object ID information, if any; `null` if none
     *
     * @return [ObjectIdInfo] augmented with possible additional information
     */
    open fun findObjectReferenceInfo(config: MapperConfig<*>, annotated: Annotated,
            objectIdInfo: ObjectIdInfo?): ObjectIdInfo? {
        return objectIdInfo
    }

    /*
     *******************************************************************************************************************
     * General class annotations
     *******************************************************************************************************************
     */

    /**
     * Method for locating name used as "root name" (for use by some serializers when outputting root-level object --
     * mostly for XML compatibility purposes) for given class, if one is defined. Returns `null` if no declaration
     * found; can return explicit empty String, which is usually ignored as well as `null`.
     *
     * @param config Effective mapper configuration in use
     *
     * @param annotatedClass Annotated class to introspect
     */
    open fun findRootName(config: MapperConfig<*>, annotatedClass: AnnotatedClass): PropertyName? {
        return null
    }

    /**
     * Method for checking whether properties that have specified type (class, not generics aware) should be completely
     * ignored for serialization and deserialization purposes.
     *
     * @param config Effective mapper configuration in use
     *
     * @param annotatedClass Annotated class to introspect
     *
     * @return `true` if properties of type should be ignored; `false` if they are not to be ignored, `null` for default
     * handling (which is 'do not ignore')
     */
    open fun isIgnorableType(config: MapperConfig<*>, annotatedClass: AnnotatedClass): Boolean? {
        return null
    }

    /**
     * Method for finding information about properties to ignore either by name, or by more general specification
     * ("ignore all unknown"). This method combines multiple aspects of name-based (as opposed to value-based) ignorals.
     *
     * @param config Configuration settings in effect (for serialization or deserialization)
     *
     * @param annotated Annotated entity (Class, Accessor) to introspect
     */
    open fun findPropertyIgnoralByName(config: MapperConfig<*>, annotated: Annotated): CirJsonIgnoreProperties.Value? {
        return CirJsonIgnoreProperties.Value.EMPTY
    }

    /**
     * Method for finding information about names of properties to included. This is typically used to strictly limit
     * properties to include based on fully defined set of names ("allow-listing"), as opposed to excluding potential
     * properties by exclusion ("deny-listing").
     *
     * @param config Configuration settings in effect (for serialization or deserialization)
     *
     * @param annotated Annotated entity (Class, Accessor) to introspect
     */
    open fun findPropertyInclusionByName(config: MapperConfig<*>,
            annotated: Annotated): CirJsonIncludeProperties.Value? {
        return CirJsonIncludeProperties.Value.ALL
    }

    /**
     * Method for finding if annotated class has associated filter; and if so, to return id that is used to locate
     * filter.
     *
     * @param config Effective mapper configuration in use
     *
     * @param annotated Annotated entity to introspect
     *
     * @return ID of the filter to use for filtering properties of annotated class, if any; or `null` if none found.
     */
    open fun findFilterId(config: MapperConfig<*>, annotated: Annotated): Any? {
        return null
    }

    /**
     * Method for finding [PropertyNamingStrategy] for given class, if any specified by annotations; and if so, either
     * return a [PropertyNamingStrategy] instance, or Class to use for creating instance.
     *
     * @param config Effective mapper configuration in use
     *
     * @param annotatedClass Annotated class to introspect
     *
     * @return Subclass or instance of [PropertyNamingStrategy], if one is specified for given class; `null` if not.
     */
    open fun findNamingStrategy(config: MapperConfig<*>, annotatedClass: AnnotatedClass): Any? {
        return null
    }

    /**
     * Method for finding [EnumNamingStrategy] for given class, if any specified by annotations; and if so, either
     * return a [EnumNamingStrategy] instance, or Class to use for creating instance
     *
     * @param config Effective mapper configuration in use
     *
     * @param annotatedClass Annotated class to introspect
     *
     * @return Subclass or instance of [EnumNamingStrategy], if one is specified for given class; `null` if not.
     */
    open fun findEnumNamingStrategy(config: MapperConfig<*>, annotatedClass: AnnotatedClass): Any? {
        return null
    }

    /**
     * Method used to check whether specified class defines a human-readable description to use for documentation. There
     * are no further definitions for contents; for example, whether these may be marked up using HTML (or something
     * like wiki format like Markup) is not defined.
     *
     * @param config Effective mapper configuration in use
     *
     * @param annotatedClass Annotated class to introspect
     *
     * @return Human-readable description, if any.
     */
    open fun findClassDescription(config: MapperConfig<*>, annotatedClass: AnnotatedClass): String? {
        return null
    }

    /*
     *******************************************************************************************************************
     * Property auto-detection
     *******************************************************************************************************************
     */

    /**
     * Method for checking if annotations indicate changes to minimum visibility levels needed for auto-detecting
     * property elements (fields, methods, constructors). A baseline checker is given, and introspector is to either
     * return it as is (if no annotations are found), or build and return a derived instance (using checker's build
     * methods).
     *
     * @param config Effective mapper configuration in use
     *
     * @param annotatedClass Annotated class to introspect
     */
    open fun findAutoDetectVisibility(config: MapperConfig<*>, annotatedClass: AnnotatedClass,
            checker: VisibilityChecker): VisibilityChecker {
        return checker
    }

    /*
     *******************************************************************************************************************
     * Annotations for Polymorphic type handling
     *******************************************************************************************************************
     */

    open fun findPolymorphicTypeInfo(config: MapperConfig<*>, annotated: Annotated): CirJsonTypeInfo.Value? {
        TODO("Not yet implemented")
    }

    open fun findTypeResolverBuilder(config: MapperConfig<*>, annotated: Annotated): Any? {
        TODO("Not yet implemented")
    }

    open fun findTypeIdResolver(config: MapperConfig<*>, annotated: Annotated): Any? {
        TODO("Not yet implemented")
    }

    open fun findSubtypes(config: MapperConfig<*>, annotated: Annotated): List<NamedType>? {
        TODO("Not yet implemented")
    }

    open fun findTypeName(config: MapperConfig<*>, annotatedClass: AnnotatedClass): String? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * General member (field, method/constructor) annotations
     *******************************************************************************************************************
     */

    open fun findFormat(config: MapperConfig<*>, annotated: Annotated): CirJsonFormat.Value? {
        TODO("Not yet implemented")
    }

    open fun findWrapperName(config: MapperConfig<*>, annotated: Annotated): PropertyName? {
        TODO("Not yet implemented")
    }

    open fun findPropertyAliases(config: MapperConfig<*>, annotated: Annotated): List<PropertyName>? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Serialization: general annotations
     *******************************************************************************************************************
     */

    open fun findPropertyInclusion(config: MapperConfig<*>, annotated: Annotated): CirJsonInclude.Value? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Serialization: property annotations
     *******************************************************************************************************************
     */

    open fun findEnumValues(config: MapperConfig<*>, annotatedClass: AnnotatedClass, enumValues: Array<Enum<*>>,
            names: Array<String?>): Array<String?> {
        TODO("Not yet implemented")
    }

    open fun findEnumAliases(config: MapperConfig<*>, annotatedClass: AnnotatedClass, enumValues: Array<Enum<*>>,
            aliases: Array<Array<String>?>) {
        TODO("Not yet implemented")
    }

    open fun findDefaultEnumValue(config: MapperConfig<*>, annotatedClass: AnnotatedClass,
            enumValues: Array<Enum<*>>): Enum<*>? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Helper types
     *******************************************************************************************************************
     */

    /**
     * Value type used with managed and back references; contains type and logic name, used to link related references
     */
    open class ReferenceProperty(protected val myType: Type, protected val myName: String) {

        open val type: Type
            get() = myType

        open val name: String
            get() = myName

        open val isManagedReference: Boolean
            get() = myType == Type.MANAGED_REFERENCE

        open val isBackReference: Boolean
            get() = myType == Type.BACK_REFERENCE

        enum class Type {

            /**
             * Reference property that Jackson manages and that is serialized normally (by serializing reference
             * object), but is used for resolving back references during deserialization. Usually this can be defined by
             * using [CirJsonManagedReference]
             */
            MANAGED_REFERENCE,

            /**
             * Reference property that Jackson manages by suppressing it during serialization, and reconstructing during
             * deserialization. Usually this can be defined by using [CirJsonBackReference]
             */
            BACK_REFERENCE

        }

        companion object {

            fun managed(name: String): ReferenceProperty {
                return ReferenceProperty(Type.MANAGED_REFERENCE, name)
            }

            fun back(name: String): ReferenceProperty {
                return ReferenceProperty(Type.BACK_REFERENCE, name)
            }

        }

    }

    /**
     * Add-on extension used for XML-specific configuration, needed to decouple format module functionality from
     * pluggable introspection functionality (especially JAXB-annotation related one).
     */
    interface XmlExtensions {

        /**
         * Method that can be called to figure out generic namespace property for an annotated object.
         *
         * @param config Configuration settings in effect
         *
         * @param annotated Annotated entity to introspect
         *
         * @return `null` if annotated thing does not define any namespace information; non-`null` namespace (which may
         * be empty String) otherwise.
         */
        fun findNamespace(config: MapperConfig<*>, annotated: Annotated): String?

        /**
         * Method used to check whether given annotated element (field, method, constructor parameter) has indicator
         * that suggests it be output as an XML attribute or not (if not, then as element)
         *
         * @param config Configuration settings in effect
         *
         * @param annotated Annotated entity to introspect
         *
         * @return `null` if no indicator found; `true` or `false` otherwise
         */
        fun isOutputAsAttribute(config: MapperConfig<*>, annotated: Annotated): Boolean?

        /**
         * Method used to check whether given annotated element (field, method, constructor parameter) has indicator
         * that suggests it should be serialized as text, without element wrapper.
         *
         * @param config Configuration settings in effect
         *
         * @param annotated Annotated entity to introspect
         *
         * @return `null` if no indicator found; `true` or `false` otherwise
         */
        fun isOutputAsText(config: MapperConfig<*>, annotated: Annotated): Boolean?

        /**
         * Method used to check whether given annotated element (field, method, constructor parameter) has indicator
         * that suggests it should be wrapped in a CDATA tag.
         *
         * @param config Configuration settings in effect
         *
         * @param annotated Annotated entity to introspect
         *
         * @return `null` if no indicator found; `true` or `false` otherwise
         */
        fun isOutputAsCData(config: MapperConfig<*>, annotated: Annotated): Boolean?

    }

    companion object {

        /**
         * Factory method for accessing "no operation" implementation of introspector: instance that will never find any
         * annotation-based configuration.
         *
         * @return "no operation" instance
         */
        fun nopInstance(): AnnotationIntrospector {
            return NopAnnotationIntrospector.INSTANCE
        }

        fun pair(annotationIntrospector1: AnnotationIntrospector,
                annotationIntrospector2: AnnotationIntrospector): AnnotationIntrospector {
            return AnnotationIntrospectorPair(annotationIntrospector1, annotationIntrospector2)
        }

    }

}