package org.cirjson.cirjackson.databind.util

import org.cirjson.cirjackson.databind.DeserializationConfig
import org.cirjson.cirjackson.databind.EnumNamingStrategy
import org.cirjson.cirjackson.databind.MapperFeature
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.introspection.AnnotatedClass
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import kotlin.reflect.KClass

/**
 * Helper class used to resolve String values (either CirJSON Object field names or regular String values) into Enum
 * instances.
 *
 * @property myIsIgnoreCase Marker for case-insensitive handling
 *
 * @property isFromIntValue Accessor for checking if it is a case in which value to map is from `@CirJsonValue`
 * annotated accessor with integral type: this matters for cases where incoming content value is of the integral type
 * and should be mapped to specific value and NOT to `Enum.index()`.
 */
open class EnumResolver protected constructor(val enumClass: KClass<Enum<*>>, val rawEnums: Array<Enum<*>>,
        protected val myEnumsById: HashMap<String, Enum<*>>, val defaultValue: Enum<*>?,
        protected val myIsIgnoreCase: Boolean, val isFromIntValue: Boolean) {

    /*
     *******************************************************************************************************************
     * Factory methods
     *******************************************************************************************************************
     */

    @Suppress("UNCHECKED_CAST")
    fun constructLookup(): CompactStringObjectMap {
        return CompactStringObjectMap.construct(myEnumsById as Map<String?, *>)
    }

    /*
     *******************************************************************************************************************
     * Public API
     *******************************************************************************************************************
     */

    fun findEnum(key: String): Enum<*>? {
        val enum = myEnumsById[key]

        if (enum != null) {
            return enum
        }

        if (!myIsIgnoreCase) {
            return null
        }

        return findEnumIgnoreCase(key)
    }


    private fun findEnumIgnoreCase(key: String): Enum<*>? {
        return myEnumsById.entries.filter { it.key.equals(key, ignoreCase = true) }.map { it.value }.firstOrNull()
    }

    fun getEnum(index: Int): Enum<*>? {
        if (index !in 0..<rawEnums.size) {
            return null
        }

        return rawEnums[index]
    }

    val enums: List<Enum<*>>
        get() = rawEnums.toList()

    val enumIds: Collection<String>
        get() = myEnumsById.keys

    fun lastValidIndex() = rawEnums.lastIndex

    companion object {

        /*
         ***************************************************************************************************************
         * Factory methods
         ***************************************************************************************************************
         */

        /**
         * Factory method for constructing an [EnumResolver] based on the given [DeserializationConfig] and
         * [AnnotatedClass] of the enum to be resolved.
         *
         * @param config the deserialization configuration to use
         *
         * @param annotatedClass the annotated class of the enum to be resolved
         *
         * @return the constructed [EnumResolver]
         */
        @Suppress("UNCHECKED_CAST")
        fun constructFor(config: DeserializationConfig, annotatedClass: AnnotatedClass): EnumResolver {
            val annotationIntrospector = config.annotationIntrospector!!
            val enumClass = annotatedClass.rawType as KClass<Enum<*>>
            val enumConstants = enumConstants(enumClass)

            val names = annotationIntrospector.findEnumValues(config, annotatedClass, enumConstants,
                    arrayOfNulls(enumConstants.size))
            val allAliases = arrayOfNulls<Array<String>>(names.size)
            annotationIntrospector.findEnumAliases(config, annotatedClass, enumConstants, allAliases)

            val map = HashMap<String, Enum<*>>()

            for (i in 0..<enumConstants.size) {
                val enumValue = enumConstants[i]
                val name = names[i] ?: enumValue.name
                map[name] = enumValue
                val aliases = allAliases[i] ?: continue

                for (alias in aliases) {
                    map.putIfAbsent(alias, enumValue)
                }
            }

            val isIgnoreCase = config.isEnabled(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            val defaultEnum = enumDefault(config, annotatedClass, enumConstants)
            return EnumResolver(enumClass, enumConstants, map, defaultEnum, isIgnoreCase, false)
        }

        /**
         * Factory method for constructing resolver that maps from `Enum.toString()` into Enum value
         */
        @Suppress("UNCHECKED_CAST")
        fun constructUsingToString(config: DeserializationConfig, annotatedClass: AnnotatedClass): EnumResolver {
            val annotationIntrospector = config.annotationIntrospector
            val enumClass = annotatedClass.rawType as KClass<Enum<*>>
            val enumConstants = enumConstants(enumClass)

            val names = arrayOfNulls<String>(enumConstants.size)
            val allAliases = arrayOfNulls<Array<String>>(enumConstants.size)
            annotationIntrospector?.findEnumValues(config, annotatedClass, enumConstants, names)
            annotationIntrospector?.findEnumAliases(config, annotatedClass, enumConstants, allAliases)

            val map = HashMap<String, Enum<*>>()

            for (i in IntProgression.fromClosedRange(enumConstants.size - 1, 0, -1)) {
                val enumValue = enumConstants[i]
                val name = names[i] ?: enumValue.toString()
                map[name] = enumValue
                val aliases = allAliases[i] ?: continue

                for (alias in aliases) {
                    map.putIfAbsent(alias, enumValue)
                }
            }

            val isIgnoreCase = config.isEnabled(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            val defaultEnum = enumDefault(config, annotatedClass, enumConstants)
            return EnumResolver(enumClass, enumConstants, map, defaultEnum, isIgnoreCase, false)
        }

        /**
         * Factory method for constructing resolver that maps from index of `Enum.values()` into Enum value.
         */
        @Suppress("UNCHECKED_CAST")
        fun constructUsingIndex(config: DeserializationConfig, annotatedClass: AnnotatedClass): EnumResolver {
            val enumClass = annotatedClass.rawType as KClass<Enum<*>>
            val enumConstants = enumConstants(enumClass)

            val map = HashMap<String, Enum<*>>()

            for (i in IntProgression.fromClosedRange(enumConstants.size - 1, 0, -1)) {
                val enumValue = enumConstants[i]
                map[i.toString()] = enumValue
            }

            val isIgnoreCase = config.isEnabled(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            val defaultEnum = enumDefault(config, annotatedClass, enumConstants)
            return EnumResolver(enumClass, enumConstants, map, defaultEnum, isIgnoreCase, false)
        }

        /**
         * Factory method for constructing an [EnumResolver] with [EnumNamingStrategy] applied.
         */
        @Suppress("UNCHECKED_CAST")
        fun constructUsingEnumNamingStrategy(config: DeserializationConfig, annotatedClass: AnnotatedClass,
                enumNamingStrategy: EnumNamingStrategy): EnumResolver {
            val enumClass = annotatedClass.rawType as KClass<Enum<*>>
            val enumConstants = enumConstants(enumClass)

            val names = arrayOfNulls<String>(enumConstants.size)
            val allAliases = arrayOfNulls<Array<String>>(enumConstants.size)
            val annotationIntrospector = config.annotationIntrospector
            annotationIntrospector?.findEnumValues(config, annotatedClass, enumConstants, names)
            annotationIntrospector?.findEnumAliases(config, annotatedClass, enumConstants, allAliases)

            val map = HashMap<String, Enum<*>>()

            for (i in IntProgression.fromClosedRange(enumConstants.size - 1, 0, -1)) {
                val enumValue = enumConstants[i]
                val name = names[i] ?: enumNamingStrategy.convertEnumToExternalName(enumValue.name)
                map[name] = enumValue
                val aliases = allAliases[i] ?: continue

                for (alias in aliases) {
                    map.putIfAbsent(alias, enumValue)
                }
            }

            val isIgnoreCase = config.isEnabled(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            val defaultEnum = enumDefault(config, annotatedClass, enumConstants)
            return EnumResolver(enumClass, enumConstants, map, defaultEnum, isIgnoreCase, false)
        }

        /**
         * Method used when actual String serialization is indicated using `@CirJsonValue` on a method in Enum class.
         */
        @Suppress("UNCHECKED_CAST")
        fun constructUsingMethod(config: DeserializationConfig, annotatedClass: AnnotatedClass,
                accessor: AnnotatedMember): EnumResolver {
            val enumClass = annotatedClass.rawType as KClass<Enum<*>>
            val enumConstants = enumConstants(enumClass)

            val map = HashMap<String, Enum<*>>()

            for (i in IntProgression.fromClosedRange(enumConstants.size - 1, 0, -1)) {
                val enumValue = enumConstants[i]

                try {
                    val obj = accessor.getValue(enumValue) ?: continue
                    map[obj.toString()] = enumValue
                } catch (e: Exception) {
                    throw IllegalArgumentException(
                            "Failed to access @CirJsonValue of Enum value $enumValue: ${e.message}")
                }
            }

            val isIgnoreCase = config.isEnabled(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            val defaultEnum = enumDefault(config, annotatedClass, enumConstants)
            return EnumResolver(enumClass, enumConstants, map, defaultEnum, isIgnoreCase, isIntType(accessor.rawType))
        }

        fun enumConstants(enumClass: KClass<Enum<*>>): Array<Enum<*>> {
            val enumConstants = enumClass.java.enumConstants as Array<Enum<*>>? ?: throw IllegalArgumentException(
                    "No enum constants for class ${enumClass.qualifiedName}")
            return enumConstants
        }

        fun enumDefault(config: MapperConfig<*>, annotatedClass: AnnotatedClass, enums: Array<Enum<*>>): Enum<*>? {
            val annotationIntrospector = config.annotationIntrospector
            return annotationIntrospector?.findDefaultEnumValue(config, annotatedClass, enums)
        }

        fun isIntType(erasedType: KClass<*>): Boolean {
            return erasedType == Long::class || erasedType == Int::class || erasedType == Short::class || erasedType == Byte::class
        }

    }

}