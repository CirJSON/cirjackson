package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.databind.BeanDescription
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializationConfig
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.type.*

/**
 * Interface that defines API for simple extensions that can provide additional serializers for various types. Access is
 * by a single callback method; instance is to either return a configured [ValueSerializer] for the specified type, or
 * `null` to indicate that it does not support handling of the type. In the latter case, further calls can be made for
 * other providers; in the former case returned serializer is used for handling of the specified type's instances.
 */
interface Serializers {

    /**
     * Method called by serialization framework the first time a serializer is needed for the specified type, which is
     * not of a container or reference type (for which other methods are called).
     *
     * @param config Serialization configuration in use
     *
     * @param type Fully resolved type of instances to serialize
     *
     * @param beanDescription Additional information about type
     *
     * @param formatOverrides (nullable) Optional format overrides (usually from property definition), to change
     * definitions that `beanDescription` may have (and which are NOT included). Usually combined calling
     * [Serializers.Base.calculateEffectiveFormat].
     *
     * @return Configured serializer to use for the type; or 'null' if implementation does not recognize or support type
     */
    fun findSerializer(config: SerializationConfig, type: KotlinType, beanDescription: BeanDescription,
            formatOverrides: CirJsonFormat.Value?): ValueSerializer<*>?

    /**
     * Method called by serialization framework the first time a serializer is needed for given [ReferenceType]
     *
     * @param formatOverrides (nullable) Optional format overrides (usually from property definition), to change
     * definitions that `beanDescription` may have (and which are NOT included). Usually combined calling
     * [Serializers.Base.calculateEffectiveFormat]..
     */
    fun findReferenceSerializer(config: SerializationConfig, type: ReferenceType, beanDescription: BeanDescription,
            formatOverrides: CirJsonFormat.Value?, contentTypeSerializer: TypeSerializer,
            contentValueSerializer: ValueSerializer<Any>): ValueSerializer<*>?

    /**
     * Method called by serialization framework the first time a serializer is needed for the specified array type.
     * Implementation should return a serializer instance if it supports the specified type; or `null` if it does not.
     *
     * @param formatOverrides (nullable) Optional format overrides (usually from property definition), to change
     * definitions that `beanDescription` may have (and which are NOT included). Usually combined calling
     * [Serializers.Base.calculateEffectiveFormat].
     */
    fun findArraySerializer(config: SerializationConfig, type: ArrayType, beanDescription: BeanDescription,
            formatOverrides: CirJsonFormat.Value?, elementTypeSerializer: TypeSerializer,
            elementValueSerializer: ValueSerializer<Any>): ValueSerializer<*>?

    /**
     * Method called by serialization framework the first time a serializer is needed for the specified [Collection]
     * type. Implementation should return a serializer instance if it supports the specified type; or `null` if it does
     * not.
     *
     * @param formatOverrides (nullable) Optional format overrides (usually from property definition), to change
     * definitions that `beanDescription` may have (and which are NOT included). Usually combined calling
     * [Serializers.Base.calculateEffectiveFormat].
     */
    fun findCollectionSerializer(config: SerializationConfig, type: CollectionType, beanDescription: BeanDescription,
            formatOverrides: CirJsonFormat.Value?, elementTypeSerializer: TypeSerializer,
            elementValueSerializer: ValueSerializer<Any>): ValueSerializer<*>?

    /**
     * Method called by serialization framework the first time a serializer is needed for the specified
     * "Collection-like" type (type that acts like [Collection], but does not implement it). Implementation should
     * return a serializer instance if it supports the specified type; or `null` if it does not.
     *
     * @param formatOverrides (nullable) Optional format overrides (usually from property definition), to change
     * definitions that `beanDescription` may have (and which are NOT included). Usually combined calling
     * [Serializers.Base.calculateEffectiveFormat].
     */
    fun findCollectionLikeSerializer(config: SerializationConfig, type: CollectionLikeType,
            beanDescription: BeanDescription, formatOverrides: CirJsonFormat.Value?,
            elementTypeSerializer: TypeSerializer, elementValueSerializer: ValueSerializer<Any>): ValueSerializer<*>?

    /**
     * Method called by serialization framework the first time a serializer is needed for the specified [Map] type.
     * Implementation should return a serializer instance if it supports the specified type; or `null` if it does not.
     *
     * @param formatOverrides (nullable) Optional format overrides (usually from property definition), to change
     * definitions that `beanDescription` may have (and which are NOT included). Usually combined calling
     * [Serializers.Base.calculateEffectiveFormat].
     */
    fun findMapSerializer(config: SerializationConfig, type: MapType, beanDescription: BeanDescription,
            formatOverrides: CirJsonFormat.Value?, keySerializer: ValueSerializer<Any>,
            elementTypeSerializer: TypeSerializer, elementValueSerializer: ValueSerializer<Any>): ValueSerializer<*>?

    /**
     * Method called by serialization framework the first time a serializer is needed for the specified "Map-like" type
     * (type that acts like [Map], but does not implement it). Implementation should return a serializer instance if it
     * supports the specified type; or `null` if it does not.
     *
     * @param formatOverrides (nullable) Optional format overrides (usually from property definition), to change
     * definitions that `beanDescription` may have (and which are NOT included). Usually combined calling
     * [Serializers.Base.calculateEffectiveFormat].
     */
    fun findMapLikeSerializer(config: SerializationConfig, type: MapLikeType, beanDescription: BeanDescription,
            formatOverrides: CirJsonFormat.Value?, keySerializer: ValueSerializer<Any>,
            elementTypeSerializer: TypeSerializer, elementValueSerializer: ValueSerializer<Any>): ValueSerializer<*>?

    /**
     * Method called in case that a given type or property is declared to use shape [CirJsonFormat.Shape.POJO] and is
     * expected to be serialized "as POJO", that is, as an (CirJSON) Object. This is usually NOT handled by extension
     * modules as core databind knows how to do this, but sometimes it may be necessary to override this behavior.
     */
    fun findExplicitPOJOSerializer(config: SerializationConfig, type: KotlinType, beanDescription: BeanDescription,
            formatOverrides: CirJsonFormat.Value?): ValueSerializer<*>?

    /**
     * Basic [Serializers] implementation that implements all methods but provides no serializers. Its main purpose is
     * to serve as a base class so that subclasses only need to override methods they need.
     */
    open class Base : Serializers {

        override fun findSerializer(config: SerializationConfig, type: KotlinType, beanDescription: BeanDescription,
                formatOverrides: CirJsonFormat.Value?): ValueSerializer<*>? {
            return null
        }

        override fun findReferenceSerializer(config: SerializationConfig, type: ReferenceType,
                beanDescription: BeanDescription, formatOverrides: CirJsonFormat.Value?,
                contentTypeSerializer: TypeSerializer,
                contentValueSerializer: ValueSerializer<Any>): ValueSerializer<*>? {
            return null
        }

        override fun findArraySerializer(config: SerializationConfig, type: ArrayType, beanDescription: BeanDescription,
                formatOverrides: CirJsonFormat.Value?, elementTypeSerializer: TypeSerializer,
                elementValueSerializer: ValueSerializer<Any>): ValueSerializer<*>? {
            return null
        }

        override fun findCollectionSerializer(config: SerializationConfig, type: CollectionType,
                beanDescription: BeanDescription, formatOverrides: CirJsonFormat.Value?,
                elementTypeSerializer: TypeSerializer,
                elementValueSerializer: ValueSerializer<Any>): ValueSerializer<*>? {
            return null
        }

        override fun findCollectionLikeSerializer(config: SerializationConfig, type: CollectionLikeType,
                beanDescription: BeanDescription, formatOverrides: CirJsonFormat.Value?,
                elementTypeSerializer: TypeSerializer,
                elementValueSerializer: ValueSerializer<Any>): ValueSerializer<*>? {
            return null
        }

        override fun findMapSerializer(config: SerializationConfig, type: MapType, beanDescription: BeanDescription,
                formatOverrides: CirJsonFormat.Value?, keySerializer: ValueSerializer<Any>,
                elementTypeSerializer: TypeSerializer,
                elementValueSerializer: ValueSerializer<Any>): ValueSerializer<*>? {
            return null
        }

        override fun findMapLikeSerializer(config: SerializationConfig, type: MapLikeType,
                beanDescription: BeanDescription, formatOverrides: CirJsonFormat.Value?,
                keySerializer: ValueSerializer<Any>, elementTypeSerializer: TypeSerializer,
                elementValueSerializer: ValueSerializer<Any>): ValueSerializer<*>? {
            return null
        }

        override fun findExplicitPOJOSerializer(config: SerializationConfig, type: KotlinType,
                beanDescription: BeanDescription, formatOverrides: CirJsonFormat.Value?): ValueSerializer<*>? {
            return null
        }

    }

}