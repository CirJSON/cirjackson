package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.introspection.BeanPropertyDefinition
import org.cirjson.cirjackson.databind.type.*

/**
 * Abstract class that defines API for objects that can be registered (via `ObjectMapper` configuration process, using
 * [org.cirjson.cirjackson.databind.configuration.MapperBuilder]) to participate in constructing [ValueDeserializer]
 * instances (including but not limited to [BeanDeserializers][BeanDeserializer]). This is typically done by modules
 * that want to alter some aspects of the typical deserialization process.
 * 
 * Sequence in which callback methods are called for a [BeanDeserializer] is:
 * 
 * 1. [updateProperties] is called once all property definitions are collected, and initial filtering (by ignorable type
 * and explicit ignoral-by-bean) has been performed.
 * 
 * 2. [updateBuilder] is called once all initial pieces for building deserializer have been collected
 * 
 * 3. [modifyDeserializer] is called after deserializer has been built by [BeanDeserializerBuilder] but before it is
 * returned to be used
 * 
 * For other types of deserializers, methods called depend on type of values for which deserializer is being
 * constructed; and only a single method is called since the process does not involve builders (unlike that of
 * [BeanDeserializer].
 * 
 * Default method implementations are no-ops, meaning that methods are implemented but have no effect; this is mostly so
 * that new methods can be added in later versions.
 */
abstract class ValueDeserializerModifier {

    /**
     * Method called by [BeanDeserializerFactory] when it has collected initial list of
     * [BeanPropertyDefinitions][BeanPropertyDefinition], and done basic by-name and by-type filtering, but before
     * constructing builder or actual property handlers; or arranging order.
     *
     * The most common changes to make at this point are to completely remove specified properties, or rename them:
     * other modifications are easier to make at later points.
     */
    open fun updateProperties(config: DeserializationConfig, beanDescription: BeanDescription,
            propertyDefinitions: MutableList<BeanPropertyDefinition>): MutableList<BeanPropertyDefinition> {
        return propertyDefinitions
    }

    /**
     * Method called by [BeanDeserializerFactory] when it has collected basic information such as tentative list of
     * properties to deserialize.
     *
     * Implementations may choose to modify state of builder (to affect deserializer being built), or even completely
     * replace it (if they want to build different kind of deserializer). Typically, changes mostly concern set of
     * properties to deserialize.
     */
    open fun updateBuilder(config: DeserializationConfig, beanDescription: BeanDescription,
            builder: BeanDeserializerBuilder): BeanDeserializerBuilder {
        return builder
    }

    /**
     * Method called by [BeanDeserializerFactory] after constructing default bean deserializer instance with properties
     * collected and ordered earlier. Implementations can modify or replace given deserializer and return deserializer
     * to use. Note that although initial deserializer being passed is usually of type [BeanDeserializer], modifiers may
     * return deserializers of other types; and this is why implementations must check for type before casting.
     * 
     * This is also called for custom deserializers for types not deemed to be of any more specific (reference, enum,
     * array, collection(-like), map(-like), node type)
     */
    open fun modifyDeserializer(config: DeserializationConfig, beanDescription: BeanDescription,
            deserializer: ValueDeserializer<*>): ValueDeserializer<*> {
        return deserializer
    }

    /*
     *******************************************************************************************************************
     * Callback methods for other types
     *******************************************************************************************************************
     */

    /**
     * Method called by [DeserializerFactory] after it has constructed the standard deserializer for given enum type to
     * make it possible to either replace or augment this deserializer with additional functionality.
     *
     * @param config Configuration in use.
     *
     * @param type Type of the value deserializer is used for.
     *
     * @param beanDescription Description.
     *
     * @param deserializer Default deserializer that would be used.
     *
     * @return Deserializer to use; either `deserializer` that was passed in, or an instance method constructed.
     */
    open fun modifyEnumDeserializer(config: DeserializationConfig, type: KotlinType, beanDescription: BeanDescription,
            deserializer: ValueDeserializer<*>): ValueDeserializer<*> {
        return deserializer
    }

    /**
     * Method called by [DeserializerFactory] after it has constructed the standard deserializer for given
     * [ReferenceType] to make it possible to either replace or augment this deserializer with additional functionality.
     *
     * @param config Configuration in use.
     *
     * @param type Type of the value deserializer is used for.
     *
     * @param beanDescription Description.
     *
     * @param deserializer Default deserializer that would be used.
     *
     * @return Deserializer to use; either `deserializer` that was passed in, or an instance method constructed.
     */
    open fun modifyReferenceDeserializer(config: DeserializationConfig, type: ReferenceType,
            beanDescription: BeanDescription, deserializer: ValueDeserializer<*>): ValueDeserializer<*> {
        return deserializer
    }

    /**
     * Method called by [DeserializerFactory] after it has constructed the standard deserializer for given [ArrayType]
     * to make it possible to either replace or augment this deserializer with additional functionality.
     *
     * @param config Configuration in use.
     *
     * @param type Type of the value deserializer is used for.
     *
     * @param beanDescription Description.
     *
     * @param deserializer Default deserializer that would be used.
     *
     * @return Deserializer to use; either `deserializer` that was passed in, or an instance method constructed.
     */
    open fun modifyArrayDeserializer(config: DeserializationConfig, type: ArrayType, beanDescription: BeanDescription,
            deserializer: ValueDeserializer<*>): ValueDeserializer<*> {
        return deserializer
    }

    /**
     * Method called by [DeserializerFactory] after it has constructed the standard deserializer for given
     * [CollectionType] to make it possible to either replace or augment this deserializer with additional
     * functionality.
     *
     * @param config Configuration in use.
     *
     * @param type Type of the value deserializer is used for.
     *
     * @param beanDescription Description.
     *
     * @param deserializer Default deserializer that would be used.
     *
     * @return Deserializer to use; either `deserializer` that was passed in, or an instance method constructed.
     */
    open fun modifyCollectionDeserializer(config: DeserializationConfig, type: CollectionType,
            beanDescription: BeanDescription, deserializer: ValueDeserializer<*>): ValueDeserializer<*> {
        return deserializer
    }

    /**
     * Method called by [DeserializerFactory] after it has constructed the standard deserializer for given
     * [CollectionLikeType] to make it possible to either replace or augment this deserializer with additional
     * functionality.
     *
     * @param config Configuration in use.
     *
     * @param type Type of the value deserializer is used for.
     *
     * @param beanDescription Description.
     *
     * @param deserializer Default deserializer that would be used.
     *
     * @return Deserializer to use; either `deserializer` that was passed in, or an instance method constructed.
     */
    open fun modifyCollectionLikeDeserializer(config: DeserializationConfig, type: CollectionLikeType,
            beanDescription: BeanDescription, deserializer: ValueDeserializer<*>): ValueDeserializer<*> {
        return deserializer
    }

    /**
     * Method called by [DeserializerFactory] after it has constructed the standard deserializer for given [MapType] to
     * make it possible to either replace or augment this deserializer with additional functionality.
     *
     * @param config Configuration in use.
     *
     * @param type Type of the value deserializer is used for.
     *
     * @param beanDescription Description.
     *
     * @param deserializer Default deserializer that would be used.
     *
     * @return Deserializer to use; either `deserializer` that was passed in, or an instance method constructed.
     */
    open fun modifyMapDeserializer(config: DeserializationConfig, type: MapType, beanDescription: BeanDescription,
            deserializer: ValueDeserializer<*>): ValueDeserializer<*> {
        return deserializer
    }

    /**
     * Method called by [DeserializerFactory] after it has constructed the standard deserializer for given [MapLikeType]
     * to make it possible to either replace or augment this deserializer with additional functionality.
     *
     * @param config Configuration in use.
     *
     * @param type Type of the value deserializer is used for.
     *
     * @param beanDescription Description.
     *
     * @param deserializer Default deserializer that would be used.
     *
     * @return Deserializer to use; either `deserializer` that was passed in, or an instance method constructed.
     */
    open fun modifyMapLikeDeserializer(config: DeserializationConfig, type: MapLikeType,
            beanDescription: BeanDescription, deserializer: ValueDeserializer<*>): ValueDeserializer<*> {
        return deserializer
    }

    /**
     * Method called by [DeserializerFactory] after it has constructed the standard key deserializer for given key type.
     * This make it possible to replace the default key deserializer, or augment it somehow (including optional use of
     * default deserializer with occasional override).
     */
    open fun modifyKeyDeserializer(config: DeserializationConfig, type: KotlinType,
            deserializer: KeyDeserializer): KeyDeserializer {
        return deserializer
    }

}