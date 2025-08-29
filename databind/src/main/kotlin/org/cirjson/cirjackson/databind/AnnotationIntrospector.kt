package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.annotations.*
import org.cirjson.cirjackson.core.Versioned
import org.cirjson.cirjackson.databind.cirjsontype.NamedType
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.introspection.Annotated
import org.cirjson.cirjackson.databind.introspection.AnnotatedClass
import org.cirjson.cirjackson.databind.introspection.VisibilityChecker

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

    open class ReferenceProperty(val type: Type, val name: String) {

        enum class Type {
        }

    }

}