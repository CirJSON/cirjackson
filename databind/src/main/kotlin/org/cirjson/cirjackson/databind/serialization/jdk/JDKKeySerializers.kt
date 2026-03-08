package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.introspection.AnnotatedClass
import org.cirjson.cirjackson.databind.serialization.implementation.PropertySerializerMap
import org.cirjson.cirjackson.databind.serialization.standard.StandardSerializer
import org.cirjson.cirjackson.databind.util.*
import java.util.*
import kotlin.reflect.KClass

object JDKKeySerializers {

    val DEFAULT_STRING_SERIALIZER = StringKeySerializer()

    /**
     * @param rawKeyType Type of key values to serialize
     *
     * @param useDefault If no match is found, should we return fallback deserializer (`true`), or `null` (`false`)?
     */
    fun getStdKeySerializer(rawKeyType: KClass<*>?, useDefault: Boolean): ValueSerializer<Any>? {
        var realRawKeyType = rawKeyType ?: return Dynamic()

        if (realRawKeyType == Any::class) {
            return Dynamic()
        }

        if (realRawKeyType == String::class) {
            return DEFAULT_STRING_SERIALIZER
        }

        if (realRawKeyType.isPrimitive) {
            realRawKeyType = realRawKeyType.wrapperType()
        }

        return when {
            realRawKeyType == Int::class -> Default(Default.TYPE_INTEGER, realRawKeyType)
            realRawKeyType == Long::class -> Default(Default.TYPE_LONG, realRawKeyType)
            realRawKeyType.isPrimitive || Number::class.isAssignableFrom(realRawKeyType) -> Default(
                    Default.TYPE_TO_STRING, realRawKeyType)

            realRawKeyType == KClass::class -> Default(Default.TYPE_CLASS, realRawKeyType)
            Date::class.isAssignableFrom(realRawKeyType) -> Default(Default.TYPE_DATE, realRawKeyType)
            Calendar::class.isAssignableFrom(realRawKeyType) -> Default(Default.TYPE_CALENDAR, realRawKeyType)
            realRawKeyType == UUID::class -> Default(Default.TYPE_TO_STRING, realRawKeyType)
            realRawKeyType == ByteArray::class -> Default(Default.TYPE_BYTE_ARRAY, realRawKeyType)
            useDefault -> Default(Default.TYPE_TO_STRING, realRawKeyType)
            else -> null
        }
    }

    /**
     * Method called if no specified key serializer was located; will return a "default" key serializer initialized by
     * [EnumKeySerializer.construct]
     */
    fun getFallbackKeySerializer(config: SerializationConfig, rawKeyType: KClass<*>,
            annotatedClass: AnnotatedClass): ValueSerializer<Any> {
        return if (rawKeyType == Enum::class) {
            Dynamic()
        } else if (rawKeyType.isEnumType) {
            EnumKeySerializer.construct(rawKeyType, EnumValues.constructFromName(config, annotatedClass),
                    EnumSerializer.constructEnumNamingStrategyValues(config, annotatedClass))
        } else {
            Default(Default.TYPE_TO_STRING, rawKeyType)
        }
    }

    /*
     *******************************************************************************************************************
     * Standard implementations used
     *******************************************************************************************************************
     */

    /**
     * This is a "chameleon" style multi-type key serializer for simple standard JDK types.
     */
    open class Default(protected val myTypeId: Int, type: KClass<*>) : StandardSerializer<Any>(type) {

        @Throws(CirJacksonException::class)
        override fun serialize(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider) {
            when (myTypeId) {
                TYPE_DATE -> serializers.defaultSerializeDateKey(value as Date, generator)

                TYPE_CALENDAR -> serializers.defaultSerializeDateKey((value as Calendar).timeInMillis, generator)

                TYPE_CLASS -> generator.writeName((value as KClass<*>).qualifiedName!!)

                TYPE_ENUM -> {
                    val key = if (serializers.isEnabled(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)) {
                        value.toString()
                    } else {
                        val enum = value as Enum<*>

                        if (serializers.isEnabled(SerializationFeature.WRITE_ENUM_KEYS_USING_INDEX)) {
                            enum.ordinal.toString()
                        } else {
                            enum.name
                        }
                    }

                    generator.writeName(key)
                }

                TYPE_INTEGER, TYPE_LONG -> generator.writePropertyId((value as Number).toLong())

                TYPE_BYTE_ARRAY -> {
                    val encoded = serializers.config.base64Variant.encode(value as ByteArray)
                    generator.writeName(encoded)
                }

                TYPE_TO_STRING -> generator.writeName(value.toString())

                else -> generator.writeName(value.toString())
            }
        }

        companion object {

            const val TYPE_DATE = 1

            const val TYPE_CALENDAR = 2

            const val TYPE_CLASS = 3

            const val TYPE_ENUM = 4

            const val TYPE_INTEGER = 5

            const val TYPE_LONG = 6

            const val TYPE_BYTE_ARRAY = 7

            const val TYPE_TO_STRING = 8

        }

    }

    /**
     * Key serializer used when key type is not known statically, and actual key serializer needs to be dynamically
     * located.
     */
    open class Dynamic : StandardSerializer<Any>(String::class) {

        @Transient
        protected var myDynamicSerializers = PropertySerializerMap.emptyForProperties()

        @Throws(CirJacksonException::class)
        override fun serialize(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider) {
            val type = value::class
            val map = myDynamicSerializers
            val serializer = map.serializerFor(type) ?: findAndAddDynamic(map, type, serializers)
            serializer.serialize(value, generator, serializers)
        }

        override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
            visitStringFormat(visitor, typeHint)
        }

        protected open fun findAndAddDynamic(map: PropertySerializerMap, type: KClass<*>,
                context: SerializerProvider): ValueSerializer<Any> {
            if (type == Any::class) {
                return Default(Default.TYPE_TO_STRING, type).also { myDynamicSerializers = map.newWith(type, it) }
            }

            val result = map.findAndAddKeySerializer(type, context, null)

            if (map !== result.map) {
                myDynamicSerializers = result.map
            }

            return result.serializer
        }

    }

    /**
     * Simple and fast key serializer when keys are Strings.
     */
    open class StringKeySerializer : StandardSerializer<Any>(String::class) {

        @Throws(CirJacksonException::class)
        override fun serialize(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider) {
            generator.writeName(value as String)
        }

    }

    /**
     * Specialized instance to use for Enum keys.
     * 
     * @property myValuesByEnumNaming
     * Map with key as converted property class defined implementation of [EnumNamingStrategy] and with value as Enum
     * names collected using `Enum.name()`.
     */
    open class EnumKeySerializer protected constructor(enumType: KClass<*>, protected val myValues: EnumValues,
            protected val myValuesByEnumNaming: EnumValues?) : StandardSerializer<Any>(enumType) {

        @Throws(CirJacksonException::class)
        override fun serialize(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider) {
            if (serializers.isEnabled(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)) {
                generator.writeName(value.toString())
                return
            }

            val enum = value as Enum<*>

            if (myValuesByEnumNaming != null) {
                generator.writeName(myValuesByEnumNaming.serializedValueFor(enum))
            } else if (serializers.isEnabled(SerializationFeature.WRITE_ENUM_KEYS_USING_INDEX)) {
                generator.writeName(enum.ordinal.toString())
            } else {
                generator.writeName(myValues.serializedValueFor(enum))
            }
        }

        companion object {

            fun construct(enumType: KClass<*>, enumValues: EnumValues): EnumKeySerializer {
                return EnumKeySerializer(enumType, enumValues, null)
            }

            fun construct(enumType: KClass<*>, enumValues: EnumValues,
                    enumValuesByEnumNaming: EnumValues?): EnumKeySerializer {
                return EnumKeySerializer(enumType, enumValues, enumValuesByEnumNaming)
            }

        }

    }

}