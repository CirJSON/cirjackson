package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.type.*
import kotlin.reflect.KClass

/**
 * Interface that defines API for simple extensions that can provide additional deserializers for various types. Access
 * is by a single callback method; instance is to either return a configured [ValueDeserializer] for specified type, or
 * `null` to indicate that it does not support handling of the type. In the latter case, further calls can be made for
 * other providers; in the former case returned deserializer is used for handling the specified type's instances.
 *
 * It is **strongly recommended** that implementations always extend [Deserializers.Base] and NOT just implement
 * [Deserializers].
 */
interface Deserializers {

    /*
     *******************************************************************************************************************
     * Scalar types
     *******************************************************************************************************************
     */

    /**
     * Method called to locate deserializer for specified [Enum] type.
     *
     * @param type Type of [Enum] instances to deserialize
     *
     * @param config Configuration in effect
     *
     * @param beanDescription Definition of the enumeration type that contains class annotations and other information
     * typically needed for building deserializers
     *
     * @return Deserializer to use for the type; or `null` if this provider does not know how to construct it
     */
    fun findEnumDeserializer(type: KClass<*>, config: DeserializationConfig,
            beanDescription: BeanDescription): ValueDeserializer<*>?

    /**
     * Method called to locate deserializer for the specified CirJSON tree node type.
     *
     * @param nodeType Specific type of CirJSON tree nodes to deserialize (subtype of [CirJsonNode])
     *
     * @param config Configuration in effect
     *
     * @param beanDescription Definition of the CirJSON tree nodes type that contains class annotations and other
     * information typically needed for building deserializers
     *
     * @return Deserializer to use for the type; or `null` if this provider does not know how to construct it
     */
    fun findTreeNodeDeserializer(nodeType: KClass<out CirJsonNode>, config: DeserializationConfig,
            beanDescription: BeanDescription): ValueDeserializer<*>?

    /**
     * Method called to locate deserializer for the specified value type which does not belong to any other category
     * (not an Enum, Collection, Map, Array, reference value or tree node)
     *
     * @param type Bean type to deserialize
     *
     * @param config Configuration in effect
     *
     * @param beanDescription Definition of the enumeration type that contains class annotations and other information
     * typically needed for building deserializers
     *
     * @return Deserializer to use for the type; or `null` if this provider does not know how to construct it
     */
    fun findBeanDeserializer(type: KotlinType, config: DeserializationConfig,
            beanDescription: BeanDescription): ValueDeserializer<*>?

    /*
     *******************************************************************************************************************
     * Container types
     *******************************************************************************************************************
     */

    /**
     * Method called to locate deserializer for the value that is of the referential type,
     *
     * @param referenceType Specific referential type to deserialize
     *
     * @param config Configuration in effect
     *
     * @param beanDescription Definition of the reference type that contains class annotations and other information
     * typically needed for building deserializers
     *
     * @param contentTypeDeserializer Possible type deserializer for referenced value
     *
     * @param contentDeserializer Value deserializer to use for referenced value, if indicated by property annotation
     *
     * @return Deserializer to use for the type; or `null` if this provider does not know how to construct it
     */
    fun findReferenceDeserializer(referenceType: ReferenceType, config: DeserializationConfig,
            beanDescription: BeanDescription, contentTypeDeserializer: TypeDeserializer,
            contentDeserializer: ValueDeserializer<*>): ValueDeserializer<*>?

    /**
     * Method called to locate deserializer for specified [Collection] (List, Set, etc.) type.
     *
     * Deserializer for the element type may be passed, if configured explicitly at higher level (by annotations,
     * typically), but usually are not. Type deserializer for the element is passed if one is needed based on contextual
     * information (annotations on declared element class; or on field or method type is associated with).
     *
     * @param type Type of collection instances to deserialize
     *
     * @param config Configuration in effect
     *
     * @param beanDescription Definition of the enumeration type that contains class annotations and other information
     * typically needed for building deserializers
     *
     * @param elementTypeDeserializer If the element type needs polymorphic type handling, this is the type information
     * deserializer to use; should usually be used as is when constructing array deserializer.
     *
     * @param elementDeserializer Deserializer to use for elements, if explicitly defined (by using annotations, for
     * example). May be `null`, in which case it will need to be resolved by deserializer at a later point.
     *
     * @return Deserializer to use for the type; or `null` if this provider does not know how to construct it
     */
    fun findCollectionDeserializer(type: CollectionType, config: DeserializationConfig,
            beanDescription: BeanDescription, elementTypeDeserializer: TypeDeserializer,
            elementDeserializer: ValueDeserializer<*>?): ValueDeserializer<*>?

    /**
     * Method called to locate deserializer for specified "Collection-like" type (one that acts like [Collection] but
     * does not implement it).
     *
     * Deserializer for the element type may be passed, if configured explicitly at higher level (by annotations,
     * typically), but usually are not. Type deserializer for the element is passed if one is needed based on contextual
     * information (annotations on declared element class; or on field or method type is associated with).
     *
     * @param type Type of instances to deserialize
     *
     * @param config Configuration in effect
     *
     * @param beanDescription Definition of the enumeration type that contains class annotations and other information
     * typically needed for building deserializers
     *
     * @param elementTypeDeserializer If the element type needs polymorphic type handling, this is the type information
     * deserializer to use; should usually be used as is when constructing array deserializer.
     *
     * @param elementDeserializer Deserializer to use for elements, if explicitly defined (by using annotations, for
     * example). May be `null`, in which case it will need to be resolved by deserializer at a later point.
     *
     * @return Deserializer to use for the type; or `null` if this provider does not know how to construct it
     */
    fun findCollectionLikeDeserializer(type: CollectionLikeType, config: DeserializationConfig,
            beanDescription: BeanDescription, elementTypeDeserializer: TypeDeserializer,
            elementDeserializer: ValueDeserializer<*>?): ValueDeserializer<*>?

    /**
     * Method called to locate deserializer for specified [Map] type.
     *
     * Deserializer for the element type may be passed, if configured explicitly at higher level (by annotations,
     * typically), but usually are not. Type deserializer for the element is passed if one is needed based on contextual
     * information (annotations on declared element class; or on field or method type is associated with).
     *
     * Similarly, a [KeyDeserializer] may be passed, but this is only done if there is a specific configuration override
     * (annotations) to indicate instance to use. Otherwise, `null` is passed, and key deserializer needs to be obtained
     * later during resolution of map serializer constructed here.
     *
     * @param type Type of [Map] instances to deserialize
     *
     * @param config Configuration in effect
     *
     * @param beanDescription Definition of the enumeration type that contains class annotations and other information
     * typically needed for building deserializers
     *
     * @param keyDeserializer Key deserializer use, if it is defined via annotations or other configuration; `null` if
     * default key deserializer for key type can be used.
     *
     * @param elementTypeDeserializer If the element type needs polymorphic type handling, this is the type information
     * deserializer to use; should usually be used as is when constructing array deserializer.
     *
     * @param elementDeserializer Deserializer to use for elements, if explicitly defined (by using annotations, for
     * example). May be `null`, in which case it will need to be resolved by deserializer at a later point.
     *
     * @return Deserializer to use for the type; or `null` if this provider does not know how to construct it
     */
    fun findMapDeserializer(type: MapType, config: DeserializationConfig, beanDescription: BeanDescription,
            keyDeserializer: KeyDeserializer?, elementTypeDeserializer: TypeDeserializer,
            elementDeserializer: ValueDeserializer<*>?): ValueDeserializer<*>?

    /**
     * Method called to locate deserializer for specified "Map-like" type (one that acts like [Map] but does not
     * implement it).
     *
     * Deserializer for the element type may be passed, if configured explicitly at higher level (by annotations,
     * typically), but usually are not. Type deserializer for the element is passed if one is needed based on contextual
     * information (annotations on declared element class; or on field or method type is associated with).
     *
     * Similarly, a [KeyDeserializer] may be passed, but this is only done if there is a specific configuration override
     * (annotations) to indicate instance to use. Otherwise, `null` is passed, and key deserializer needs to be obtained
     * later during resolution, by deserializer constructed here.
     *
     * @param type Type of [Map] instances to deserialize
     *
     * @param config Configuration in effect
     *
     * @param beanDescription Definition of the enumeration type that contains class annotations and other information
     * typically needed for building deserializers
     *
     * @param keyDeserializer Key deserializer use, if it is defined via annotations or other configuration; `null` if
     * default key deserializer for key type can be used.
     *
     * @param elementTypeDeserializer If the element type needs polymorphic type handling, this is the type information
     * deserializer to use; should usually be used as is when constructing array deserializer.
     *
     * @param elementDeserializer Deserializer to use for elements, if explicitly defined (by using annotations, for
     * example). May be `null`, in which case it will need to be resolved by deserializer at a later point.
     *
     * @return Deserializer to use for the type; or `null` if this provider does not know how to construct it
     */
    fun findMapLikeDeserializer(type: MapLikeType, config: DeserializationConfig, beanDescription: BeanDescription,
            keyDeserializer: KeyDeserializer?, elementTypeDeserializer: TypeDeserializer,
            elementDeserializer: ValueDeserializer<*>?): ValueDeserializer<*>?

    /**
     * Method that may be called to check whether this deserializer provider would provide deserializer for values of
     * the given type, without attempting to construct (and possibly fail in some cases) actual deserializer. Mostly
     * needed to support validation of polymorphic type ids.
     *
     * Note: implementations should take care NOT to claim supporting types that they do not recognize as this could
     * lead to an incorrect assumption of safe support by caller.
     */
    fun hasDeserializerFor(config: DeserializationConfig, valueType: KClass<*>): Boolean

    /*
     *******************************************************************************************************************
     * Helper class
     *******************************************************************************************************************
     */

    abstract class Base : Deserializers {

        override fun findEnumDeserializer(type: KClass<*>, config: DeserializationConfig,
                beanDescription: BeanDescription): ValueDeserializer<*>? {
            return null
        }

        override fun findTreeNodeDeserializer(nodeType: KClass<out CirJsonNode>, config: DeserializationConfig,
                beanDescription: BeanDescription): ValueDeserializer<*>? {
            return null
        }

        override fun findBeanDeserializer(type: KotlinType, config: DeserializationConfig,
                beanDescription: BeanDescription): ValueDeserializer<*>? {
            return null
        }

        override fun findReferenceDeserializer(referenceType: ReferenceType, config: DeserializationConfig,
                beanDescription: BeanDescription, contentTypeDeserializer: TypeDeserializer,
                contentDeserializer: ValueDeserializer<*>): ValueDeserializer<*>? {
            return null
        }

        override fun findCollectionDeserializer(type: CollectionType, config: DeserializationConfig,
                beanDescription: BeanDescription, elementTypeDeserializer: TypeDeserializer,
                elementDeserializer: ValueDeserializer<*>?): ValueDeserializer<*>? {
            return null
        }

        override fun findCollectionLikeDeserializer(type: CollectionLikeType, config: DeserializationConfig,
                beanDescription: BeanDescription, elementTypeDeserializer: TypeDeserializer,
                elementDeserializer: ValueDeserializer<*>?): ValueDeserializer<*>? {
            return null
        }

        override fun findMapDeserializer(type: MapType, config: DeserializationConfig, beanDescription: BeanDescription,
                keyDeserializer: KeyDeserializer?, elementTypeDeserializer: TypeDeserializer,
                elementDeserializer: ValueDeserializer<*>?): ValueDeserializer<*>? {
            return null
        }

        override fun findMapLikeDeserializer(type: MapLikeType, config: DeserializationConfig,
                beanDescription: BeanDescription, keyDeserializer: KeyDeserializer?,
                elementTypeDeserializer: TypeDeserializer,
                elementDeserializer: ValueDeserializer<*>?): ValueDeserializer<*>? {
            return null
        }

    }

}