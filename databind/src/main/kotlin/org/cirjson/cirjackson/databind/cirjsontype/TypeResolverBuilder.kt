package org.cirjson.cirjackson.databind.cirjsontype

import org.cirjson.cirjackson.annotations.CirJsonTypeInfo
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import kotlin.reflect.KClass

/**
 * Interface that defines builders that are configured based on annotations (like [CirJsonTypeInfo] or JAXB
 * annotations), and produce type serializers and deserializers used for handling type information embedded in CirJSON
 * to allow for safe polymorphic type handling.
 *
 * Builder is first initialized by calling [init] method. Finally, after calling all configuration methods,
 * [buildTypeSerializer] or [buildTypeDeserializer] will be called to get actual type resolver constructed and used for
 * resolving types for configured base type and its subtypes.
 *
 * Note that instances are used for two related but distinct use cases:
 *
 * * To create builders to use with explicit type information inclusion (usually via `@CirJsonTypeInfo` annotation)
 *
 * * To create builders when "default typing" is used; if so, type information is automatically included for certain
 * kind of types, regardless of annotations
 *
 * Important distinction between the cases is that in first case, calls to create builders are only made when builders
 * are certainly needed; whereas in second case builder has to first verify whether type information is applicable for
 * given type, and if not, just return `null` to indicate this.
 */
interface TypeResolverBuilder<T : TypeResolverBuilder<T>> {

    /**
     * Accessor for currently configured default type; implementation class that may be used in case no valid type
     * information is available during type resolution
     */
    val defaultImplementation: KClass<*>?

    /**
     * Method for building type serializer based on current configuration of this builder.
     *
     * @param baseType Base type that constructed resolver will handle; super type of all types it will be used for.
     *
     * @param subtypes Known subtypes of the base type.
     */
    fun buildTypeSerializer(context: SerializerProvider, baseType: KotlinType,
            subtypes: Collection<NamedType>?): TypeSerializer?

    /**
     * Method for building type deserializer based on current configuration of this builder.
     *
     * @param baseType Base type that constructed resolver will handle; super type of all types it will be used for.
     *
     * @param subtypes Known subtypes of the base type.
     */
    fun buildTypeDeserializer(context: DeserializationContext, baseType: KotlinType,
            subtypes: Collection<NamedType>?): TypeDeserializer?

    /**
     * Initialization method that is called right after constructing the builder instance, in cases where information
     * could not be passed directly (for example when instantiated for an annotation)
     *
     * @param settings Configuration settings to apply.
     *
     * @return Resulting builder instance (usually this builder, but not necessarily)
     */
    fun init(settings: CirJsonTypeInfo.Value?, resolver: TypeIdResolver?): T

    /**
     * "Mutant factory" method for creating a new instance with different default implementation to use if type id is
     * either not available, or cannot be resolved.
     *
     * @return Either this instance (if nothing changed) or a new instance with different default implementation
     */
    fun withDefaultImplementation(defaultImplementation: KClass<*>?): T

    /**
     * Method for overriding type information.
     */
    fun withSettings(settings: CirJsonTypeInfo.Value): T

}