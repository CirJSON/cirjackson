package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.deserialization.standard.StandardConvertingDeserializer
import org.cirjson.cirjackson.databind.type.TypeFactory
import org.cirjson.cirjackson.databind.util.Converter
import java.util.*
import kotlin.reflect.KClass

/**
 * Helper class used to contain logic for deserializing "special" containers from [java.util.Collections] and
 * [java.util.Arrays]. This is needed because they do not have usable no-arguments constructor: however, are easy enough
 * to deserialize using delegating deserializer.
 */
object JavaUtilCollectionsDeserializers {

    private const val TYPE_SINGLETON_SET = 1

    private const val TYPE_SINGLETON_LIST = 2

    private const val TYPE_SINGLETON_MAP = 3

    private const val TYPE_UNMODIFIABLE_SET = 4

    private const val TYPE_UNMODIFIABLE_LIST = 5

    private const val TYPE_UNMODIFIABLE_MAP = 6

    private const val TYPE_SYNC_SET = 7

    private const val TYPE_SYNC_COLLECTION = 8

    private const val TYPE_SYNC_LIST = 9

    private const val TYPE_SYNC_MAP = 10

    private const val TYPE_AS_LIST = 11

    private const val PREFIX_JAVA_UTIL_COLLECTIONS = "java.util.Collections$"

    private const val PREFIX_JAVA_UTIL_ARRAYS = "java.util.Arrays$"

    private const val PREFIX_JAVA_UTIL_IMMUTABLE_COLLECTIONS = "java.util.ImmutableCollections$"

    @Suppress("UNCHECKED_CAST")
    fun findForCollection(type: KotlinType): ValueDeserializer<*>? {
        val className = type.rawClass.qualifiedName!!

        if (!className.startsWith("java.util.")) {
            return null
        }

        val localName = findUtilCollectionsTypeName(className)
        var name = ""

        return if (localName != null) {
            val converter = if (findUnmodifiableTypeName(localName)?.also { name = it } != null) {
                if (name.endsWith("Set")) {
                    converter(TYPE_UNMODIFIABLE_SET, type, Set::class)
                } else if (name.endsWith("List")) {
                    converter(TYPE_UNMODIFIABLE_LIST, type, List::class)
                } else {
                    null
                }
            } else if (findSingletonTypeName(localName)?.also { name = it } != null) {
                if (name.endsWith("Set")) {
                    converter(TYPE_SINGLETON_SET, type, Set::class)
                } else if (name.endsWith("List")) {
                    converter(TYPE_SINGLETON_LIST, type, List::class)
                } else {
                    null
                }
            } else if (findSyncTypeName(localName)?.also { name = it } != null) {
                if (name.endsWith("Set")) {
                    converter(TYPE_SYNC_SET, type, Set::class)
                } else if (name.endsWith("List")) {
                    converter(TYPE_SYNC_LIST, type, List::class)
                } else if (name.endsWith("Collection")) {
                    converter(TYPE_SYNC_COLLECTION, type, List::class)
                } else {
                    null
                }
            } else {
                null
            }

            converter?.let { StandardConvertingDeserializer(it as Converter<Any, Any>) }
        } else if (findUtilArrayTypeName(className)?.also { name = it } != null) {
            if ("List" in name) {
                StandardConvertingDeserializer(converter(TYPE_AS_LIST, type, List::class) as Converter<Any, Any>)
            } else {
                null
            }
        } else if (findUtilCollectionsImmutableTypeName(className)?.also { name = it } != null) {
            if ("List" in name) {
                StandardConvertingDeserializer(converter(TYPE_AS_LIST, type, List::class) as Converter<Any, Any>)
            } else if ("Set" in name) {
                StandardConvertingDeserializer(
                        converter(TYPE_UNMODIFIABLE_SET, type, Set::class) as Converter<Any, Any>)
            } else {
                null
            }
        } else {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun findForMap(type: KotlinType): ValueDeserializer<*>? {
        val className = type.rawClass.qualifiedName!!
        val localName = findUtilCollectionsTypeName(className)
        var name = ""

        val converter = if (localName != null) {
            if (findUnmodifiableTypeName(localName)?.also { name = it } != null) {
                if ("Map" in name) {
                    converter(TYPE_UNMODIFIABLE_MAP, type, Map::class)
                } else {
                    null
                }
            } else if (findSingletonTypeName(localName)?.also { name = it } != null) {
                if ("Map" in name) {
                    converter(TYPE_SINGLETON_MAP, type, Map::class)
                } else {
                    null
                }
            } else if (findSyncTypeName(localName)?.also { name = it } != null) {
                if ("Map" in name) {
                    converter(TYPE_SYNC_MAP, type, Map::class)
                } else {
                    null
                }
            } else {
                null
            }
        } else if (findUtilCollectionsImmutableTypeName(className)?.also { name = it } != null) {
            if ("Map" in name) {
                StandardConvertingDeserializer(
                        converter(TYPE_UNMODIFIABLE_MAP, type, Set::class) as Converter<Any, Any>)
            } else {
                null
            }
        } else {
            null
        }

        return converter?.let { StandardConvertingDeserializer(it as Converter<Any, Any>) }
    }

    private fun converter(kind: Int, concreteType: KotlinType, rawSuper: KClass<*>): JavaUtilCollectionsConverter {
        return JavaUtilCollectionsConverter(kind, concreteType.findSuperType(rawSuper)!!)
    }

    private fun findUtilArrayTypeName(className: String): String? {
        return if (className.startsWith(PREFIX_JAVA_UTIL_ARRAYS)) {
            className.substring(PREFIX_JAVA_UTIL_ARRAYS.length)
        } else {
            null
        }
    }

    private fun findUtilCollectionsTypeName(className: String): String? {
        return if (className.startsWith(PREFIX_JAVA_UTIL_COLLECTIONS)) {
            className.substring(PREFIX_JAVA_UTIL_COLLECTIONS.length)
        } else {
            null
        }
    }

    private fun findUtilCollectionsImmutableTypeName(className: String): String? {
        return if (className.startsWith(PREFIX_JAVA_UTIL_IMMUTABLE_COLLECTIONS)) {
            className.substring(PREFIX_JAVA_UTIL_IMMUTABLE_COLLECTIONS.length)
        } else {
            null
        }
    }

    private fun findSingletonTypeName(localName: String): String? {
        return localName.takeIf { it.startsWith("Singleton") }?.substring(9)
    }

    private fun findSyncTypeName(localName: String): String? {
        return localName.takeIf { it.startsWith("Synchronized") }?.substring(12)
    }

    private fun findUnmodifiableTypeName(localName: String): String? {
        return localName.takeIf { it.startsWith("Unmodifiable") }?.substring(12)
    }

    /**
     * Implementation used for converting from various generic container types ([java.util.Set], [java.util.List],
     * [java.util.Map]) into more specific implementations accessible via [java.util.Collections].
     */
    private class JavaUtilCollectionsConverter(private val myKind: Int, private val myInputType: KotlinType) :
            Converter<Any?, Any?> {

        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        override fun convert(input: Any?): Any? {
            input ?: return null

            return when (myKind) {
                TYPE_SINGLETON_SET -> {
                    val set = input as java.util.Set<*>
                    checkSingleton(set.size)
                    Collections.singleton(set.iterator().next())
                }

                TYPE_SINGLETON_LIST -> {
                    val list = input as java.util.List<*>
                    checkSingleton(list.size)
                    Collections.singletonList(list[0])
                }

                TYPE_SINGLETON_MAP -> {
                    val map = input as java.util.Map<*, *>
                    checkSingleton(map.size())
                    val entry = map.entrySet().iterator().next()
                    Collections.singletonMap(entry.key, entry.value)
                }

                TYPE_UNMODIFIABLE_SET -> {
                    Collections.unmodifiableSet(input as Set<*>)
                }

                TYPE_UNMODIFIABLE_LIST -> {
                    Collections.unmodifiableList(input as List<*>)
                }

                TYPE_UNMODIFIABLE_MAP -> {
                    Collections.unmodifiableMap(input as Map<*, *>)
                }

                TYPE_SYNC_SET -> {
                    Collections.synchronizedSet(input as Set<*>)
                }

                TYPE_SYNC_LIST -> {
                    Collections.synchronizedList(input as List<*>)
                }

                TYPE_SYNC_COLLECTION -> {
                    Collections.synchronizedMap(input as Map<*, *>)
                }

                TYPE_SYNC_MAP -> {
                    Collections.synchronizedCollection(input as Collection<*>)
                }

                else -> input
            }
        }

        override fun getInputType(typeFactory: TypeFactory): KotlinType {
            return myInputType
        }

        override fun getOutputType(typeFactory: TypeFactory): KotlinType {
            return myInputType
        }

        private fun checkSingleton(size: Int) {
            if (size != 1) {
                throw IllegalArgumentException("Can not deserialize Singleton container from $size entries")
            }
        }

    }

}