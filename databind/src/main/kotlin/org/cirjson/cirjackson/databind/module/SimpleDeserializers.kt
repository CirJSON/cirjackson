package org.cirjson.cirjackson.databind.module

import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.deserialization.Deserializers
import org.cirjson.cirjackson.databind.type.*
import org.cirjson.cirjackson.databind.util.isEnumType
import kotlin.reflect.KClass

/**
 * Simple implementation [Deserializers] which allows registration of deserializers based on raw (type erased class). It
 * can work well for basic bean and scalar type deserializers, but is not a good fit for handling generic types (like
 * [Maps][Map] and [Collections][Collection] or array types).
 * 
 * Unlike [SimpleSerializers], this class does not currently support generic mappings; all mappings must be to exact
 * declared deserialization type.
 */
open class SimpleDeserializers : Deserializers.Base {

    private var myClassMappings: HashMap<ClassKey, ValueDeserializer<*>>? = null

    /**
     * Flag to help find "generic" enum deserializer, if one has been registered.
     */
    protected var myHasEnumDeserializer = false

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    constructor() : super()

    constructor(deserializers: Map<KClass<*>, ValueDeserializer<*>>) : super() {
        addDeserializers(deserializers)
    }

    open fun <T : Any> addDeserializer(forType: KClass<T>,
            deserializer: ValueDeserializer<out T>): SimpleDeserializers {
        val key = ClassKey(forType)
        val classMappings = myClassMappings ?: HashMap<ClassKey, ValueDeserializer<*>>().also { myClassMappings = it }
        classMappings[key] = deserializer

        if (forType == Enum::class) {
            myHasEnumDeserializer = true
        }

        return this
    }

    @Suppress("UNCHECKED_CAST")
    open fun addDeserializers(deserializers: Map<KClass<*>, ValueDeserializer<*>>): SimpleDeserializers {
        for ((key, value) in deserializers) {
            val forType = key as KClass<Any>
            val deserializer = value as ValueDeserializer<Any>
            addDeserializer(forType, deserializer)
        }

        return this
    }

    /*
     *******************************************************************************************************************
     * Deserializers implementation
     *******************************************************************************************************************
     */

    override fun findArrayDeserializer(type: ArrayType, config: DeserializationConfig, beanDescription: BeanDescription,
            elementTypeDeserializer: TypeDeserializer?,
            elementDeserializer: ValueDeserializer<*>?): ValueDeserializer<*>? {
        return find(type)
    }

    override fun findBeanDeserializer(type: KotlinType, config: DeserializationConfig,
            beanDescription: BeanDescription): ValueDeserializer<*>? {
        return find(type)
    }

    override fun findCollectionDeserializer(type: CollectionType, config: DeserializationConfig,
            beanDescription: BeanDescription, elementTypeDeserializer: TypeDeserializer?,
            elementDeserializer: ValueDeserializer<*>?): ValueDeserializer<*>? {
        return find(type)
    }

    override fun findCollectionLikeDeserializer(type: CollectionLikeType, config: DeserializationConfig,
            beanDescription: BeanDescription, elementTypeDeserializer: TypeDeserializer?,
            elementDeserializer: ValueDeserializer<*>?): ValueDeserializer<*>? {
        return find(type)
    }

    override fun findEnumDeserializer(type: KClass<*>, config: DeserializationConfig,
            beanDescription: BeanDescription): ValueDeserializer<*>? {
        val classMappings = myClassMappings ?: return null
        return classMappings[ClassKey(type)] ?: classMappings.takeIf { myHasEnumDeserializer && type.isEnumType }
                ?.get(ClassKey(Enum::class))
    }

    override fun findTreeNodeDeserializer(nodeType: KClass<out CirJsonNode>, config: DeserializationConfig,
            beanDescription: BeanDescription): ValueDeserializer<*>? {
        return myClassMappings?.get(ClassKey(nodeType))
    }

    override fun findReferenceDeserializer(referenceType: ReferenceType, config: DeserializationConfig,
            beanDescription: BeanDescription, contentTypeDeserializer: TypeDeserializer?,
            contentDeserializer: ValueDeserializer<*>?): ValueDeserializer<*>? {
        return find(referenceType)
    }

    override fun findMapDeserializer(type: MapType, config: DeserializationConfig, beanDescription: BeanDescription,
            keyDeserializer: KeyDeserializer?, elementTypeDeserializer: TypeDeserializer?,
            elementDeserializer: ValueDeserializer<*>?): ValueDeserializer<*>? {
        return find(type)
    }

    override fun findMapLikeDeserializer(type: MapLikeType, config: DeserializationConfig,
            beanDescription: BeanDescription, keyDeserializer: KeyDeserializer?,
            elementTypeDeserializer: TypeDeserializer?,
            elementDeserializer: ValueDeserializer<*>?): ValueDeserializer<*>? {
        return find(type)
    }

    override fun hasDeserializerFor(config: DeserializationConfig, valueType: KClass<*>): Boolean {
        return myClassMappings?.containsKey(ClassKey(valueType)) ?: false
    }

    /*
     *******************************************************************************************************************
     * Internal methods
     *******************************************************************************************************************
     */

    private fun find(type: KotlinType): ValueDeserializer<*>? {
        return myClassMappings?.get(ClassKey(type.rawClass))
    }

}