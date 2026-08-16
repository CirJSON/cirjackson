package org.cirjson.cirjackson.databind.module

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.databind.BeanDescription
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializationConfig
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.Serializers
import org.cirjson.cirjackson.databind.type.*
import org.cirjson.cirjackson.databind.util.interfaces
import org.cirjson.cirjackson.databind.util.isInterface
import org.cirjson.cirjackson.databind.util.superclass
import kotlin.reflect.KClass

/**
 * Simple implementation [Serializers] which allows registration of serializers based on raw (type erased class). It can
 * work well for basic bean and scalar type serializers, but is not a good fit for handling generic types (like
 * [Maps][Map] and [Collections][Collection]).
 * 
 * Type registrations are assumed to be general; meaning that registration of serializer for a supertype will also be
 * used for handling subtypes, unless an exact match is found first. As an example, handler for [CharSequence] would
 * also be used serializing [StringBuilder] instances, unless a direct mapping was found.
 */
open class SimpleSerializers : Serializers.Base {

    /**
     * Class-based mappings that are used both for exact and subclass matches.
     */
    protected var myClassMappings: HashMap<ClassKey, ValueSerializer<*>>? = null

    /**
     * Interface-based matches.
     */
    protected var myInterfaceMappings: HashMap<ClassKey, ValueSerializer<*>>? = null

    /**
     * Flag to help find "generic" enum serializer, if one has been registered.
     */
    protected var myHasEnumSerializer = false

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    constructor() : super()

    constructor(serializers: List<ValueSerializer<*>>) : super() {
        addSerializers(serializers)
    }

    /**
     * Method for adding given serializer for type that [ValueSerializer.handledType] specifies (which MUST return a
     * non-`null` class; and can NOT be [Any], as a sanity check). For serializers that do not declare handled type, use
     * the variant that takes two arguments.
     */
    open fun addSerializer(serializer: ValueSerializer<*>): SimpleSerializers {
        val type = serializer.handledType()

        if (type == null || type == Any::class) {
            throw IllegalArgumentException(
                    "`ValueSerializer` of type `${serializer::class.qualifiedName}` does not define valid handledType() -- must either register with method that takes type argument or make serializer extend 'org.cirjson.cirjackson.databind.serialization.standard.StandardSerializer'")
        }

        addSerializerImplementation(type, serializer)
        return this
    }

    open fun <T : Any> addSerializer(type: KClass<out T>, serializer: ValueSerializer<T>): SimpleSerializers {
        addSerializerImplementation(type, serializer)
        return this
    }

    open fun addSerializers(serializers: List<ValueSerializer<*>>): SimpleSerializers {
        for (serializer in serializers) {
            addSerializer(serializer)
        }

        return this
    }

    /*
     *******************************************************************************************************************
     * Serializers implementation
     *******************************************************************************************************************
     */

    override fun findSerializer(config: SerializationConfig, type: KotlinType, beanDescription: BeanDescription,
            formatOverrides: CirJsonFormat.Value?): ValueSerializer<*>? {
        val rawClass = type.rawClass
        val key = ClassKey(rawClass)

        if (rawClass.isInterface) {
            myInterfaceMappings?.get(key)?.let { return it }
        } else {
            val classMappings = myClassMappings

            if (classMappings != null) {
                classMappings[key]?.let { return it }

                if (myHasEnumSerializer && type.isEnumType) {
                    key.reset(Enum::class)
                    classMappings[key]?.let { return it }
                }

                var current: KClass<*>? = rawClass

                while (current != null) {
                    key.reset(current)
                    classMappings[key]?.let { return it }
                    current = current.superclass
                }
            }
        }

        if (myInterfaceMappings != null) {
            findInterfaceMapping(rawClass, key)?.let { return it }

            if (!rawClass.isInterface) {
                var current = rawClass

                while (current.superclass?.also { current = it } != null) {
                    findInterfaceMapping(rawClass, key)?.let { return it }
                }
            }
        }

        return null
    }

    override fun findArraySerializer(config: SerializationConfig, type: ArrayType, beanDescription: BeanDescription,
            formatOverrides: CirJsonFormat.Value?, elementTypeSerializer: TypeSerializer?,
            elementValueSerializer: ValueSerializer<Any>?): ValueSerializer<*>? {
        return findSerializer(config, type, beanDescription, formatOverrides)
    }

    override fun findCollectionSerializer(config: SerializationConfig, type: CollectionType,
            beanDescription: BeanDescription, formatOverrides: CirJsonFormat.Value?,
            elementTypeSerializer: TypeSerializer?,
            elementValueSerializer: ValueSerializer<Any>?): ValueSerializer<*>? {
        return findSerializer(config, type, beanDescription, formatOverrides)
    }

    override fun findCollectionLikeSerializer(config: SerializationConfig, type: CollectionLikeType,
            beanDescription: BeanDescription, formatOverrides: CirJsonFormat.Value?,
            elementTypeSerializer: TypeSerializer?,
            elementValueSerializer: ValueSerializer<Any>?): ValueSerializer<*>? {
        return findSerializer(config, type, beanDescription, formatOverrides)
    }

    override fun findMapSerializer(config: SerializationConfig, type: MapType, beanDescription: BeanDescription,
            formatOverrides: CirJsonFormat.Value?, keySerializer: ValueSerializer<Any>?,
            elementTypeSerializer: TypeSerializer?,
            elementValueSerializer: ValueSerializer<Any>?): ValueSerializer<*>? {
        return findSerializer(config, type, beanDescription, formatOverrides)
    }

    override fun findMapLikeSerializer(config: SerializationConfig, type: MapLikeType, beanDescription: BeanDescription,
            formatOverrides: CirJsonFormat.Value?, keySerializer: ValueSerializer<Any>?,
            elementTypeSerializer: TypeSerializer?,
            elementValueSerializer: ValueSerializer<Any>?): ValueSerializer<*>? {
        return findSerializer(config, type, beanDescription, formatOverrides)
    }

    /*
     *******************************************************************************************************************
     * Internal methods
     *******************************************************************************************************************
     */

    protected open fun findInterfaceMapping(type: KClass<*>, key: ClassKey): ValueSerializer<*>? {
        val interfaceMappings = myInterfaceMappings!!

        for (inter in type.interfaces) {
            key.reset(inter)
            interfaceMappings[key]?.let { return it }
            findInterfaceMapping(inter, key)?.let { return it }
        }

        return null
    }

    protected open fun addSerializerImplementation(type: KClass<*>, serializer: ValueSerializer<*>) {
        val key = ClassKey(type)

        if (type.isInterface) {
            val interfaceMappings =
                    myInterfaceMappings ?: HashMap<ClassKey, ValueSerializer<*>>().also { myInterfaceMappings = it }
            interfaceMappings[key] = serializer
            return
        }

        val classMappings = myClassMappings ?: HashMap<ClassKey, ValueSerializer<*>>().also { myClassMappings = it }
        classMappings[key] = serializer

        if (type == Enum::class) {
            myHasEnumSerializer = true
        }
    }

}