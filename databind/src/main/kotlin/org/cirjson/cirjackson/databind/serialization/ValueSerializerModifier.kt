package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.databind.BeanDescription
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializationConfig
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.type.*

/**
 * Abstract class that defines API for objects that can be registered (via `ObjectMapper` configuration process, using
 * [org.cirjson.cirjackson.databind.configuration.MapperBuilder]) to participate in constructing [ValueSerializer]
 * instances (including but not limited to [BeanSerializers][BeanSerializer]). This is typically done by modules that
 * want alter some aspects of the typical serialization process.
 * 
 * Sequence in which callback methods are called is as follows:
 * 
 * 1. After factory has collected tentative set of properties (instances of `BeanPropertyWriter`) is sent for
 * modification via [changeProperties]. Changes can include removal, addition and replacement of suggested properties.
 * 
 * 2. Resulting set of properties are ordered (sorted) by factory, as per configuration, and then [orderProperties] is
 * called to allow modifiers to alter ordering.
 * 
 * 3. After all bean properties and related information is accumulated, [updateBuilder] is called with builder, to allow
 * builder state to be modified (including possibly replacing builder itself if necessary)
 * 
 * 4. Once all bean information has been determined, factory creates default [BeanSerializer] instance and passes it to
 * modifiers using [modifySerializer] (or type-specific alternative `modifyXxxSerializer()` method), for possible
 * modification or replacement (by any [ValueSerializer] instance)
 *
 * Default method implementations are "no-op", meaning that methods are implemented but have no effect.
 */
abstract class ValueSerializerModifier {

    /**
     * Method called by [BeanSerializerFactory] with tentative set of discovered properties. Implementations can add,
     * remove or replace any of passed properties.
     */
    open fun changeProperties(config: SerializationConfig, beanDescription: BeanDescription,
            beanProperties: MutableList<BeanPropertyWriter>): MutableList<BeanPropertyWriter> {
        return beanProperties
    }

    /**
     * Method called by [BeanSerializerFactory] with set of properties to serialize, in default ordering (based on
     * defaults as well as possible type annotations). Implementations can change ordering any way they like.
     */
    open fun orderProperties(config: SerializationConfig, beanDescription: BeanDescription,
            beanProperties: MutableList<BeanPropertyWriter>): MutableList<BeanPropertyWriter> {
        return beanProperties
    }

    /**
     * Method called by [BeanSerializerFactory] after collecting all information regarding POJO to serialize and
     * updating builder with it, but before constructing serializer. Implementations may choose to modify state of
     * builder (to affect serializer being built), or even completely replace it (if they want to build different kind
     * of serializer). Typically, however, passed-in builder is returned, possibly with some modifications.
     */
    open fun updateBuilder(config: SerializationConfig, beanDescription: BeanDescription,
            builder: BeanSerializerBuilder): BeanSerializerBuilder {
        return builder
    }

    /**
     * Method called by [BeanSerializerFactory] after constructing default bean serializer instance with properties
     * collected and ordered earlier. Implementations can modify or replace given serializer and return serializer to
     * use. Note that although initial serializer being passed is of type [BeanSerializer], modifiers may return
     * serializers of other types; and this is why implementations must check for type before casting.
     * 
     * NOTE: this method gets called for serializer of those non-POJO types that do not go through any of more specific
     * `modifyXxxSerializer` methods; mostly for standard types like [Iterator] and such.
     */
    open fun modifySerializer(config: SerializationConfig, beanDescription: BeanDescription,
            serializer: ValueSerializer<*>): ValueSerializer<*> {
        return serializer
    }

    /*
     *******************************************************************************************************************
     * Construction
     *******************************************************************************************************************
     */

    /**
     * Method called by [SerializerFactory] after it has constructed the standard serializer for given [ArrayType] to
     * make it possible to either replace or augment this serializer with additional functionality.
     *
     * @param config Configuration in use
     * 
     * @param valueType Type of the value serializer is used for.
     * 
     * @param beanDescription Details of the type in question, to allow checking class annotations
     * 
     * @param serializer Default serializer that would be used.
     *
     * @return Serializer to use; either [serializer] that was passed in, or an instance that this method constructed.
     */
    open fun modifyArraySerializer(config: SerializationConfig, valueType: ArrayType, beanDescription: BeanDescription,
            serializer: ValueSerializer<*>): ValueSerializer<*> {
        return serializer
    }

    /**
     * Method called by [SerializerFactory] after it has constructed the standard serializer for given [CollectionType]
     * to make it possible to either replace or augment this serializer with additional functionality.
     *
     * @param config Configuration in use
     * 
     * @param valueType Type of the value serializer is used for.
     * 
     * @param beanDescription Details of the type in question, to allow checking class annotations
     * 
     * @param serializer Default serializer that would be used.
     *
     * @return Serializer to use; either [serializer] that was passed in, or an instance that this method constructed.
     */
    open fun modifyCollectionSerializer(config: SerializationConfig, valueType: ArrayType,
            beanDescription: BeanDescription, serializer: ValueSerializer<*>): ValueSerializer<*> {
        return serializer
    }

    /**
     * Method called by [SerializerFactory] after it has constructed the standard serializer for given
     * [CollectionLikeType] to make it possible to either replace or augment this serializer with additional
     * functionality.
     *
     * @param config Configuration in use
     *
     * @param valueType Type of the value serializer is used for.
     *
     * @param beanDescription Details of the type in question, to allow checking class annotations
     *
     * @param serializer Default serializer that would be used.
     *
     * @return Serializer to use; either [serializer] that was passed in, or an instance that this method constructed.
     */
    open fun modifyCollectionLikeSerializer(config: SerializationConfig, valueType: ArrayType,
            beanDescription: BeanDescription, serializer: ValueSerializer<*>): ValueSerializer<*> {
        return serializer
    }

    /**
     * Method called by [SerializerFactory] after it has constructed the standard serializer for given [MapType] to make
     * it possible to either replace or augment this serializer with additional functionality.
     *
     * @param config Configuration in use
     * 
     * @param valueType Type of the value serializer is used for.
     * 
     * @param beanDescription Details of the type in question, to allow checking class annotations
     * 
     * @param serializer Default serializer that would be used.
     *
     * @return Serializer to use; either [serializer] that was passed in, or an instance that this method constructed.
     */
    open fun modifyMapSerializer(config: SerializationConfig, valueType: ArrayType, beanDescription: BeanDescription,
            serializer: ValueSerializer<*>): ValueSerializer<*> {
        return serializer
    }

    /**
     * Method called by [SerializerFactory] after it has constructed the standard serializer for given [MapLikeType] to
     * make it possible to either replace or augment this serializer with additional functionality.
     *
     * @param config Configuration in use
     *
     * @param valueType Type of the value serializer is used for.
     *
     * @param beanDescription Details of the type in question, to allow checking class annotations
     *
     * @param serializer Default serializer that would be used.
     *
     * @return Serializer to use; either [serializer] that was passed in, or an instance that this method constructed.
     */
    open fun modifyMapLikeSerializer(config: SerializationConfig, valueType: ArrayType,
            beanDescription: BeanDescription, serializer: ValueSerializer<*>): ValueSerializer<*> {
        return serializer
    }

    /**
     * Method called by [SerializerFactory] after it has constructed the standard serializer for given [KotlinType] to
     * make it possible to either replace or augment this serializer with additional functionality.
     *
     * @param config Configuration in use
     * 
     * @param valueType Type of the value serializer is used for.
     * 
     * @param beanDescription Details of the type in question, to allow checking class annotations
     * 
     * @param serializer Default serializer that would be used.
     *
     * @return Serializer to use; either [serializer] that was passed in, or an instance that this method constructed.
     */
    open fun modifyEnumSerializer(config: SerializationConfig, valueType: KotlinType, beanDescription: BeanDescription,
            serializer: ValueSerializer<*>): ValueSerializer<*> {
        return serializer
    }

    /**
     * Method called by [SerializerFactory] after it has constructed the default key serializer to use for serializing
     * [Map] keys of given type. This makes it possible to either replace or augment default serializer with additional
     * functionality.
     *
     * @param config Configuration in use
     *
     * @param valueType Type of keys the serializer is used for.
     *
     * @param beanDescription Details of the type in question, to allow checking class annotations
     *
     * @param serializer Default serializer that would be used.
     *
     * @return Serializer to use; either `serializer` that was passed in, or an instance method constructed.
     */
    open fun modifyKeySerializer(config: SerializationConfig, valueType: KotlinType, beanDescription: BeanDescription,
            serializer: ValueSerializer<*>): ValueSerializer<*> {
        return serializer
    }

}