package org.cirjson.cirjackson.databind.util

import org.cirjson.cirjackson.core.SerializableString
import org.cirjson.cirjackson.core.io.SerializedString
import org.cirjson.cirjackson.databind.EnumNamingStrategy
import org.cirjson.cirjackson.databind.SerializationConfig
import org.cirjson.cirjackson.databind.SerializationFeature
import org.cirjson.cirjackson.databind.configuration.EnumFeature
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.introspection.AnnotatedClass
import java.util.*
import kotlin.reflect.KClass

/**
 * Helper class used for storing String serializations of [Enums][Enum], to match to/from external representations.
 */
class EnumValues private constructor(val enumClass: KClass<Enum<*>>, private val myTextual: Array<SerializableString>) {

    private val myValues = enumClass.java.enumConstants as Array<Enum<*>>

    private var myAsMap: EnumMap<*, SerializableString>? = null

    /*
     *******************************************************************************************************************
     * Public API
     *******************************************************************************************************************
     */

    fun serializedValueFor(key: Enum<*>): SerializableString {
        return myTextual[key.ordinal]
    }

    fun values(): Collection<SerializableString> {
        return myTextual.asList()
    }

    /**
     * Convenience accessor for getting raw Enum instances.
     */
    fun enums(): List<Enum<*>> {
        return myValues.asList()
    }

    @Suppress("UNCHECKED_CAST")
    fun internalMap(): EnumMap<*, SerializableString> {
        var result = myAsMap

        if (result != null) {
            return result
        }

        val map = LinkedHashMap<Enum<*>, SerializableString>()

        for (enumValue in myValues) {
            map[enumValue] = myTextual[enumValue.ordinal]
        }

        result = EnumMap(map as MutableMap<AccessPattern, SerializableString>)
        myAsMap = result
        return result
    }

    companion object {

        /**
         * NOTE: do NOT call this if configuration may change, and choice between `toString()` and `name` might change
         * dynamically.
         */
        fun construct(config: SerializationConfig, annotatedClass: AnnotatedClass): EnumValues {
            return if (config.isEnabled(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)) {
                constructFromToString(config, annotatedClass)
            } else {
                constructFromName(config, annotatedClass)
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun constructFromName(config: MapperConfig<*>, annotatedClass: AnnotatedClass): EnumValues {
            val useLowercase = config.isEnabled(EnumFeature.WRITE_ENUMS_TO_LOWERCASE)
            val enumClass = annotatedClass.rawType as KClass<Enum<*>>
            val enumConstants = enumConstants(enumClass)

            val annotationIntrospector = config.annotationIntrospector!!
            val names = annotationIntrospector.findEnumValues(config, annotatedClass, enumConstants,
                    arrayOfNulls(enumConstants.size))

            val textual = Array<SerializableString>(enumConstants.size) { SerializedString("") }

            for ((i, enumValue) in enumConstants.withIndex()) {
                var name = names[i] ?: enumValue.name

                if (useLowercase) {
                    name = name.lowercase()
                }

                textual[enumValue.ordinal] = config.compileString(name)
            }

            return construct(enumClass, textual)
        }

        @Suppress("UNCHECKED_CAST")
        fun constructFromToString(config: MapperConfig<*>, annotatedClass: AnnotatedClass): EnumValues {
            val useLowercase = config.isEnabled(EnumFeature.WRITE_ENUMS_TO_LOWERCASE)
            val enumClass = annotatedClass.rawType as KClass<Enum<*>>
            val enumConstants = enumConstants(enumClass)

            val annotationIntrospector = config.annotationIntrospector
            val names = arrayOfNulls<String>(enumConstants.size)
            annotationIntrospector?.findEnumValues(config, annotatedClass, enumConstants, names)

            val textual = Array<SerializableString>(enumConstants.size) { SerializedString("") }

            for (i in enumConstants.indices) {
                var name = names[i] ?: enumConstants[i].toString()

                if (useLowercase) {
                    name = name.lowercase()
                }

                textual[i] = config.compileString(name)
            }

            return construct(enumClass, textual)
        }

        /**
         * Returns String serializations of Enum name using an instance of [EnumNamingStrategy].
         *
         * The output [EnumValues] should contain values that are symmetric to
         * [EnumResolver.constructUsingEnumNamingStrategy].
         */
        @Suppress("UNCHECKED_CAST")
        fun constructUsingEnumNamingStrategy(config: MapperConfig<*>, annotatedClass: AnnotatedClass,
                namingStrategy: EnumNamingStrategy): EnumValues {
            val useLowercase = config.isEnabled(EnumFeature.WRITE_ENUMS_TO_LOWERCASE)
            val enumClass = annotatedClass.rawType as KClass<Enum<*>>
            val enumConstants = enumConstants(enumClass)

            val annotationIntrospector = config.annotationIntrospector
            val names = arrayOfNulls<String>(enumConstants.size)
            annotationIntrospector?.findEnumValues(config, annotatedClass, enumConstants, names)

            val textual = Array<SerializableString>(enumConstants.size) { SerializedString("") }

            for ((i, enumValue) in enumConstants.withIndex()) {
                var name = names[i] ?: namingStrategy.convertEnumToExternalName(enumValue.name)

                if (useLowercase) {
                    name = name.lowercase()
                }

                textual[i] = config.compileString(name)
            }

            return construct(enumClass, textual)
        }

        fun construct(config: MapperConfig<*>, enumClass: KClass<Enum<*>>, externalValues: List<String>): EnumValues {
            val length = externalValues.size
            val textual = Array(length) { i -> config.compileString(externalValues[i]) }
            return construct(enumClass, textual)
        }

        fun construct(enumClass: KClass<Enum<*>>, externalValues: Array<SerializableString>): EnumValues {
            return EnumValues(enumClass, externalValues)
        }

        /*
         ***************************************************************************************************************
         * Internal Helpers
         ***************************************************************************************************************
         */

        @Suppress("UNCHECKED_CAST")
        private fun enumConstants(enumClass: KClass<*>): Array<Enum<*>> {
            return enumClass.findEnumType().java.enumConstants as Array<Enum<*>>? ?: throw IllegalArgumentException(
                    "No enum constants for class ${enumClass.qualifiedName}")
        }

    }

}