package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.type.*
import kotlin.reflect.KClass

/**
 * Abstract class that defines API used by [DeserializationContext] to construct actual [ValueDeserializer] instances
 * (which are then cached by context and/or dedicated cache).
 * 
 * Since there are multiple broad categories of deserializers, there are multiple factory methods:
 * 
 * * For CirJSON "Array" type, we need 2 methods: one to deal with expected Kotlin arrays ([createArrayDeserializer])
 * and the other for other Kotlin containers like [Lists][List] and [Sets][Set] ([createCollectionDeserializer]).
 * Actually there is also a third method for "Collection-like" types; things like Scala collections that act like JDK
 * collections but do not implement same interfaces.
 * 
 * * For CirJSON "Object" type, we need 2 methods: one to deal with expected Kotlin [Maps][Map]
 * ([createMapDeserializer]), and another for POJOs ([createBeanDeserializer]. As an additional twist there is also a
 * callback for "Map-like" types, mostly to make it possible to support Scala Maps (which are NOT JDK Map compatible).
 * 
 * * For Tree Model ([org.cirjson.cirjackson.databind.CirJsonNode]) properties there is [createTreeDeserializer]
 * 
 * * For enumerated types ([Enum]) there is [createEnumDeserializer]
 * 
 * * For all other types, [createBeanDeserializer] is used.
 */
abstract class DeserializerFactory {

    /*
     *******************************************************************************************************************
     * Basic DeserializerFactory API
     *******************************************************************************************************************
     */

    /**
     * Method that is to find all creators (constructors, factory methods) for the bean type to deserialize.
     */
    abstract fun findValueInstantiator(context: DeserializationContext,
            beanDescription: BeanDescription): ValueInstantiator?

    /**
     * Method called to create (or, for completely immutable deserializers, reuse) a deserializer that can convert
     * CirJSON content into values of specified Kotlin "bean" (POJO) type. At this point it is known that the type is
     * not otherwise recognized as one of structured types (array, Collection, Map) or a well-known JDK type (enum,
     * primitives/wrappers, String); this method only gets called if other options are exhausted. This also means that
     * this method can be overridden to add support for custom types.
     *
     * @param type Type to be deserialized
     */
    abstract fun createBeanDeserializer(context: DeserializationContext, type: KotlinType,
            beanDescription: BeanDescription): ValueDeserializer<Any>?

    /**
     * Method called to create a deserializer that will use specified Builder class for building value instances.
     */
    abstract fun createBuilderBasedDeserializer(context: DeserializationContext, type: KotlinType,
            beanDescription: BeanDescription, builderClass: KClass<*>): ValueDeserializer<Any>

    abstract fun createEnumDeserializer(context: DeserializationContext, type: KotlinType,
            beanDescription: BeanDescription): ValueDeserializer<*>

    abstract fun createReferenceDeserializer(context: DeserializationContext, type: ReferenceType,
            beanDescription: BeanDescription): ValueDeserializer<*>

    /**
     * Method called to create and return a deserializer that can construct CirJsonNode(s) from CirJSON content.
     */
    abstract fun createTreeDeserializer(config: DeserializationConfig, type: KotlinType,
            beanDescription: BeanDescription): ValueDeserializer<*>

    /**
     * Method called to create (or, for completely immutable deserializers, reuse) a deserializer that can convert
     * CirJSON content into values of specified Kotlin type.
     *
     * @param type Type to be deserialized
     */
    abstract fun createArrayDeserializer(context: DeserializationContext, type: ArrayType,
            beanDescription: BeanDescription): ValueDeserializer<*>

    abstract fun createCollectionDeserializer(context: DeserializationContext, type: CollectionType,
            beanDescription: BeanDescription): ValueDeserializer<*>

    abstract fun createCollectionLikeDeserializer(context: DeserializationContext, type: CollectionLikeType,
            beanDescription: BeanDescription): ValueDeserializer<*>

    abstract fun createMapDeserializer(context: DeserializationContext, type: MapType,
            beanDescription: BeanDescription): ValueDeserializer<*>

    abstract fun createMapLikeDeserializer(context: DeserializationContext, type: MapLikeType,
            beanDescription: BeanDescription): ValueDeserializer<*>

    /**
     * Method called to find if factory knows how to create a key deserializer for specified type; currently this means
     * checking if a module has registered possible deserializers.
     *
     * @return Key deserializer to use for specified type, if one found; null if not (and default key deserializer
     * should be used)
     */
    abstract fun createKeyDeserializer(context: DeserializationContext, type: KotlinType): KeyDeserializer?

    /**
     * Method that can be used to check if databind module has explicitly declared deserializer for given (likely JDK)
     * type, explicit meaning that there is specific deserializer for given type as opposed to auto-generated "Bean"
     * deserializer. Factory itself will check for known JDK-provided types, but registered
     * [CirJacksonModules][org.cirjson.cirjackson.databind.CirJacksonModule] are also called to see if they might
     * provide explicit deserializer.
     * 
     * Main use for this method is with Safe Default Typing (and generally Safe Polymorphic Deserialization). During
     * this, it is good to be able to check that given raw type is explicitly supported and as such "known type" (as
     * opposed to potentially dangerous "gadget type" which could be exploited).
     * 
     * This matches [Deserializers.hasDeserializerFor] method, which is the mechanism used to determine if a
     * [Module][org.cirjson.cirjackson.databind.CirJacksonModule] might provide an explicit deserializer instead of core
     * databind.
     */
    abstract fun hasExplicitDeserializerFor(context: DatabindContext, valueType: KClass<*>): Boolean

    /*
     *******************************************************************************************************************
     * Mutant factories for registering additional configuration
     *******************************************************************************************************************
     */

    /**
     * Convenience method for creating a new factory instance with additional deserializer provider.
     */
    abstract fun withAdditionalDeserializers(additional: Deserializers): DeserializerFactory

    /**
     * Convenience method for creating a new factory instance with additional [KeyDeserializers].
     */
    abstract fun withAdditionalKeyDeserializers(additional: KeyDeserializers): DeserializerFactory

    /**
     * Convenience method for creating a new factory instance with additional [ValueDeserializerModifier].
     */
    abstract fun withDeserializerModifier(modifier: ValueDeserializerModifier): DeserializerFactory

    /**
     * Convenience method for creating a new factory instance with additional [ValueInstantiators].
     */
    abstract fun withValueInstantiators(instantiators: ValueInstantiators): DeserializerFactory

    companion object {

        val NO_DESERIALIZERS = emptyArray<Deserializers>()

    }

}