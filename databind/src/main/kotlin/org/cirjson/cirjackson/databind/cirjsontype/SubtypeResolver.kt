package org.cirjson.cirjackson.databind.cirjsontype

import org.cirjson.cirjackson.core.util.Snapshottable
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.introspection.AnnotatedClass
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import kotlin.reflect.KClass

/**
 * Helper object used for handling registration on resolving of super-types to subtypes.
 */
abstract class SubtypeResolver : Snapshottable<SubtypeResolver> {

    /*
     *******************************************************************************************************************
     * Snapshottable
     *******************************************************************************************************************
     */

    /**
     * Method that has to create a new instance that contains same registration information as this instance, but is not
     * linked to this instance.
     */
    abstract override fun snapshot(): SubtypeResolver

    /*
     *******************************************************************************************************************
     * Methods for registering external subtype definitions
     *******************************************************************************************************************
     */

    /**
     * Method for registering specified subtypes (possibly including type names); for type entries without name,
     * nonqualified class name as used as name (unless overridden by annotation).
     */
    abstract fun registerSubtypes(vararg subtypes: NamedType): SubtypeResolver

    /**
     * Method for registering specified subtypes (possibly including type names); for type entries without name,
     * nonqualified class name as used as name (unless overridden by annotation).
     */
    abstract fun registerSubtypes(vararg subtypes: KClass<*>): SubtypeResolver

    /**
     * Method for registering specified subtypes (possibly including type names); for type entries without name,
     * nonqualified class name as used as name (unless overridden by annotation).
     */
    abstract fun registerSubtypes(subtypes: Collection<KClass<*>>): SubtypeResolver

    /*
     *******************************************************************************************************************
     * Subtype resolution
     *******************************************************************************************************************
     */

    /**
     * Method for finding out all reachable subtypes for a property specified by given element (method or field), such
     * that access is by type, typically needed for serialization (converting from type to type name).
     *
     * @param baseType Effective property base type to use; may differ from actual type of property; for structured
     * types it is content (value) type and NOT structured type.
     */
    abstract fun collectAndResolveSubtypesByClass(config: MapperConfig<*>, property: AnnotatedMember?,
            baseType: KotlinType?): Collection<NamedType>

    /**
     * Method for finding out all reachable subtypes for given type, such that access is by type, typically needed for
     * serialization (converting from type to type name).
     *
     * @param baseType Effective property base type to use; may differ from actual type of property; for structured
     * types it is content (value) type and NOT structured type.
     */
    abstract fun collectAndResolveSubtypesByClass(config: MapperConfig<*>,
            baseType: AnnotatedClass): Collection<NamedType>

    /**
     * Method for finding out all reachable subtypes for a property specified by given element (method or field), such
     * that access is by type id, typically needed for deserialization (converting from type id to type).
     *
     * @param baseType Effective property base type to use; may differ from actual type of property; for structured
     * types it is content (value) type and NOT structured type.
     */
    abstract fun collectAndResolveSubtypesByTypeId(config: MapperConfig<*>, property: AnnotatedMember?,
            baseType: KotlinType): Collection<NamedType>

    /**
     * Method for finding out all reachable subtypes for given type, such that access is by type id, typically needed
     * for deserialization (converting from type id to type).
     *
     * @param baseType Effective property base type to use; may differ from actual type of property; for structured
     * types it is content (value) type and NOT structured type.
     */
    abstract fun collectAndResolveSubtypesByTypeId(config: MapperConfig<*>,
            baseType: AnnotatedClass): Collection<NamedType>

}