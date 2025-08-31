package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.annotations.*
import org.cirjson.cirjackson.core.Versioned
import org.cirjson.cirjackson.databind.cirjsontype.NamedType
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.introspection.Annotated
import org.cirjson.cirjackson.databind.introspection.AnnotatedClass
import org.cirjson.cirjackson.databind.introspection.VisibilityChecker

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
     * General class annotations
     *******************************************************************************************************************
     */

    open fun findRootName(config: MapperConfig<*>, annotatedClass: AnnotatedClass): PropertyName? {
        TODO("Not yet implemented")
    }

    open fun findPropertyIgnoralByName(config: MapperConfig<*>, annotated: Annotated): CirJsonIgnoreProperties.Value? {
        TODO("Not yet implemented")
    }

    open fun findPropertyInclusionByName(config: MapperConfig<*>,
            annotated: Annotated): CirJsonIncludeProperties.Value? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Property auto-detection
     *******************************************************************************************************************
     */

    open fun findAutoDetectVisibility(config: MapperConfig<*>, annotatedClass: AnnotatedClass,
            checker: VisibilityChecker): VisibilityChecker {
        TODO("Not yet implemented")
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

}