package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.io.NumberInput
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.DeserializationFeature
import org.cirjson.cirjackson.databind.KeyDeserializer
import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.configuration.EnumFeature
import org.cirjson.cirjackson.databind.introspection.AnnotatedMethod
import org.cirjson.cirjackson.databind.util.EnumResolver
import org.cirjson.cirjackson.databind.util.exceptionMessage
import org.cirjson.cirjackson.databind.util.isEnumType
import org.cirjson.cirjackson.databind.util.unwrapAndThrowAsIllegalArgumentException
import java.io.IOException
import java.io.Serializable
import java.net.URI
import java.net.URL
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.javaMethod

/**
 * Default [KeyDeserializer] implementation used for most [Map] key types CirJackson supports. Implemented as
 * "chameleon" (or Swiss pocket knife) class; not particularly elegant, but helps reduce number of classes and jar size
 * (class metadata adds significant per-class overhead; much more than bytecode).
 */
@CirJacksonStandardImplementation
open class JDKKeyDeserializer : KeyDeserializer {

    protected val myKind: Int

    protected val myKeyClass: KClass<*>

    /**
     * Some types that are deserialized are using a helper deserializer.
     */
    protected val myDeserializer: JDKFromStringDeserializer?

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    protected constructor(kind: Int, keyClass: KClass<*>) : this(kind, keyClass, null)

    protected constructor(kind: Int, keyClass: KClass<*>, deserializer: JDKFromStringDeserializer?) {
        myKind = kind
        myKeyClass = keyClass
        myDeserializer = deserializer
    }

    /*
     *******************************************************************************************************************
     * Deserialization
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun deserializeKey(key: String?, context: DeserializationContext): Any? {
        key ?: return null

        try {
            val result = parse(key, context)

            if (result != null) {
                return result
            }
        } catch (e: CirJacksonException) {
            throw e
        } catch (e: Exception) {
            return context.handleWeirdKey(myKeyClass, key,
                    "not a valid representation, problem: (${e::class.qualifiedName}) ${e.exceptionMessage()}")
        }

        if (myKeyClass.isEnumType &&
                context.config.isEnabled(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)) {
            return null
        }

        return context.handleWeirdKey(myKeyClass, key, "not a valid representation")
    }

    open val keyClass: KClass<*>
        get() = myKeyClass

    @Throws(Exception::class)
    protected open fun parse(key: String, context: DeserializationContext): Any? {
        return when (myKind) {
            TYPE_BOOLEAN -> {
                if (key == "true") {
                    true
                } else if (key == "false") {
                    false
                } else {
                    context.handleWeirdKey(myKeyClass, key, "value not 'true' nor 'false'")
                }
            }

            TYPE_BYTE -> {
                val value = parseInt(key)

                if (value !in Byte.MIN_VALUE..255) {
                    context.handleWeirdKey(myKeyClass, key, "overflow, value cannot be represented as 8-bit value")
                } else {
                    value.toByte()
                }
            }

            TYPE_SHORT -> {
                val value = parseInt(key)

                if (value !in Short.MIN_VALUE..Short.MAX_VALUE) {
                    context.handleWeirdKey(myKeyClass, key, "overflow, value cannot be represented as 16-bit value")
                } else {
                    value.toShort()
                }
            }

            TYPE_CHAR -> {
                if (key.length != 1) {
                    context.handleWeirdKey(myKeyClass, key, "can only convert 1-character Strings")
                } else {
                    key[0]
                }
            }

            TYPE_INT -> {
                parseInt(key)
            }

            TYPE_LONG -> {
                parseLong(key)
            }

            TYPE_FLOAT -> {
                parseDouble(key).toFloat()
            }

            TYPE_DOUBLE -> {
                parseDouble(key)
            }

            TYPE_LOCALE, TYPE_CURRENCY -> {
                try {
                    myDeserializer!!.deserialize(key, context)
                } catch (e: IllegalArgumentException) {
                    weirdKey(context, key, e)
                } catch (e: IOException) {
                    weirdKey(context, key, e)
                }
            }

            TYPE_DATE -> {
                context.parseDate(key)
            }

            TYPE_CALENDAR -> {
                context.constructCalendar(context.parseDate(key))
            }

            TYPE_UUID -> {
                try {
                    UUID.fromString(key)
                } catch (e: Exception) {
                    weirdKey(context, key, e)
                }
            }

            TYPE_URI -> {
                try {
                    URI.create(key)
                } catch (e: Exception) {
                    weirdKey(context, key, e)
                }
            }

            TYPE_URL -> {
                try {
                    URL(key)
                } catch (e: Exception) {
                    weirdKey(context, key, e)
                }
            }

            TYPE_KCLASS -> {
                try {
                    context.findClass(key)
                } catch (e: Exception) {
                    context.handleWeirdKey(myKeyClass, key, "unable to parse key as KClass")
                }
            }

            TYPE_BYTE_ARRAY -> {
                try {
                    context.config.base64Variant.decode(key)
                } catch (e: IllegalArgumentException) {
                    weirdKey(context, key, e)
                }
            }

            else -> {
                throw IllegalStateException("Internal error: unknown key type $myKeyClass")
            }
        }
    }

    /*
     *******************************************************************************************************************
     * Helper methods for subclasses
     *******************************************************************************************************************
     */

    @Throws(IllegalArgumentException::class)
    protected open fun parseInt(key: String): Int {
        return NumberInput.parseInt(key)
    }

    @Throws(IllegalArgumentException::class)
    protected open fun parseLong(key: String): Long {
        return NumberInput.parseLong(key)
    }

    @Throws(IllegalArgumentException::class)
    protected open fun parseDouble(key: String): Double {
        return NumberInput.parseDouble(key, false)
    }

    @Throws(CirJacksonException::class)
    protected open fun weirdKey(context: DeserializationContext, key: String, e: Exception): Any? {
        return context.handleWeirdKey(myKeyClass, key, "problem: ${e.exceptionMessage()}")
    }

    /*
     *******************************************************************************************************************
     * Key deserializer implementations; standard "String as String"
     *******************************************************************************************************************
     */

    @CirJacksonStandardImplementation
    internal class StringKeyDeserializer private constructor(keyClass: KClass<*>) : JDKKeyDeserializer(-1, keyClass) {

        @Throws(CirJacksonException::class)
        override fun deserializeKey(key: String?, context: DeserializationContext): Any? {
            return key
        }

        companion object {

            private val STRING = StringKeyDeserializer(String::class)

            private val OBJECT = StringKeyDeserializer(Any::class)

            fun forType(keyClass: KClass<*>): StringKeyDeserializer {
                return if (keyClass == String::class) {
                    STRING
                } else if (keyClass == Any::class) {
                    OBJECT
                } else {
                    StringKeyDeserializer(keyClass)
                }
            }

        }

    }

    /*
     *******************************************************************************************************************
     * Key deserializer implementations; other
     *******************************************************************************************************************
     */

    /**
     * Key deserializer that wraps a "regular" deserializer (but one that must recognize FIELD_NAMEs as text!) to reuse
     * existing handlers as key handlers.
     */
    internal class DelegatingKeyDeserializer(val keyClass: KClass<*>, private val myDelegate: ValueDeserializer<*>) :
            KeyDeserializer() {

        @Throws(CirJacksonException::class)
        override fun deserializeKey(key: String?, context: DeserializationContext): Any? {
            key ?: return null

            val tokenBuffer = context.bufferForInputBuffering()
            tokenBuffer.writeString(key)

            return try {
                val parser = tokenBuffer.asParser(context)
                parser.nextToken()
                myDelegate.deserialize(parser, context) ?: context.handleWeirdKey(keyClass, key,
                        "not a valid representation")
            } catch (e: Exception) {
                context.handleWeirdKey(keyClass, key, "not a valid representation: ${e.message}")
            }
        }

    }

    /**
     * @property myByEnumNamingResolver Look up map with **key** as `Enum.name` converted by
     * [EnumNamingStrategy.convertEnumToExternalName][org.cirjson.cirjackson.databind.EnumNamingStrategy.convertEnumToExternalName]
     * and **value** as Enums.
     * 
     * @property myByToStringResolver Alternative resolver to parse enums with `toString()` method as the source. Works
     * when [DeserializationFeature.READ_ENUMS_USING_TO_STRING] is enabled.
     * 
     * @property myByIndexResolver Alternative resolver to parse enums with [Enum.ordinal] as the source. Works when
     * [EnumFeature.READ_ENUM_KEYS_USING_INDEX] is enabled.
     */
    internal class EnumKeyDeserializer(private val myByNameResolver: EnumResolver,
            private val myFactory: AnnotatedMethod?, private val myByEnumNamingResolver: EnumResolver?,
            private val myByToStringResolver: EnumResolver, private val myByIndexResolver: EnumResolver?) :
            JDKKeyDeserializer(-1, myByNameResolver.enumClass) {

        private val myEnumDefaultValue = myByNameResolver.defaultValue

        @Throws(CirJacksonException::class)
        override fun parse(key: String, context: DeserializationContext): Any? {
            if (myFactory != null) {
                try {
                    return myFactory.call(key)
                } catch (e: Exception) {
                    e.unwrapAndThrowAsIllegalArgumentException()
                }
            }

            val resolver = resolveCurrentResolver(context)

            return resolver.findEnum(key) ?: myByIndexResolver?.takeIf {
                context.isEnabled(EnumFeature.READ_ENUM_KEYS_USING_INDEX)
            }?.findEnum(key) ?: if (myEnumDefaultValue != null && context.isEnabled(
                            DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)) {
                myEnumDefaultValue
            } else if (!context.isEnabled(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)) {
                context.handleWeirdKey(keyClass, key,
                        "not one of the values accepted for Enum class: ${resolver.enumIds}")
            } else {
                null
            }
        }

        private fun resolveCurrentResolver(context: DeserializationContext): EnumResolver {
            return myByEnumNamingResolver ?: if (context.isEnabled(DeserializationFeature.READ_ENUMS_USING_TO_STRING)) {
                myByToStringResolver
            } else {
                myByNameResolver
            }
        }

    }

    /**
     * Key deserializer that calls a single-string-arg constructor to instantiate desired key type.
     */
    internal class StringConstructorKeyDeserializer(private val myConstructor: KFunction<*>) :
            JDKKeyDeserializer(-1, (myConstructor.javaConstructor!!.declaringClass as Class<*>).kotlin) {

        @Throws(Exception::class)
        override fun parse(key: String, context: DeserializationContext): Any? {
            return myConstructor.call(key)
        }

    }

    /**
     * Key deserializer that calls a static no-args factory method to instantiate desired key type.
     */
    internal class StringFactoryKeyDeserializer(private val myFactory: KFunction<*>) :
            JDKKeyDeserializer(-1, myFactory.javaMethod!!.declaringClass.kotlin) {

        @Throws(Exception::class)
        override fun parse(key: String, context: DeserializationContext): Any? {
            return myFactory.javaMethod!!.invoke(null, key)
        }

    }

    companion object {

        const val TYPE_BOOLEAN = 1

        const val TYPE_BYTE = 2

        const val TYPE_SHORT = 3

        const val TYPE_CHAR = 4

        const val TYPE_INT = 5

        const val TYPE_LONG = 6

        const val TYPE_FLOAT = 7

        const val TYPE_DOUBLE = 8

        const val TYPE_LOCALE = 9

        const val TYPE_DATE = 10

        const val TYPE_CALENDAR = 11

        const val TYPE_UUID = 12

        const val TYPE_URI = 13

        const val TYPE_URL = 14

        const val TYPE_KCLASS = 15

        const val TYPE_CURRENCY = 16

        const val TYPE_BYTE_ARRAY = 17

        fun forType(raw: KClass<*>): JDKKeyDeserializer? {
            val kind = when (raw) {
                String::class, Any::class, CharSequence::class, Serializable::class -> {
                    return StringKeyDeserializer.forType(raw)
                }

                UUID::class -> {
                    TYPE_UUID
                }

                Int::class -> {
                    TYPE_INT
                }

                Long::class -> {
                    TYPE_LONG
                }

                Date::class -> {
                    TYPE_DATE
                }

                Calendar::class -> {
                    TYPE_CALENDAR
                }

                Boolean::class -> {
                    TYPE_BOOLEAN
                }

                Byte::class -> {
                    TYPE_BYTE
                }

                Char::class -> {
                    TYPE_CHAR
                }

                Short::class -> {
                    TYPE_SHORT
                }

                Float::class -> {
                    TYPE_FLOAT
                }

                Double::class -> {
                    TYPE_DOUBLE
                }

                URI::class -> {
                    TYPE_URI
                }

                URL::class -> {
                    TYPE_URL
                }

                KClass::class -> {
                    TYPE_KCLASS
                }

                Locale::class -> {
                    val deserializer = JDKFromStringDeserializer.findDeserializer(Locale::class)
                    return JDKKeyDeserializer(TYPE_LOCALE, raw, deserializer)
                }

                Currency::class -> {
                    val deserializer = JDKFromStringDeserializer.findDeserializer(Currency::class)
                    return JDKKeyDeserializer(TYPE_CURRENCY, raw, deserializer)
                }

                ByteArray::class -> {
                    TYPE_BYTE_ARRAY
                }

                else -> {
                    return null
                }
            }

            return JDKKeyDeserializer(kind, raw)
        }

    }

}