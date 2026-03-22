package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.serialization.standard.StandardSerializer
import java.io.File
import java.net.URI
import java.net.URL
import java.nio.file.Path
import java.util.*
import java.util.regex.Pattern
import kotlin.reflect.KClass

/**
 * "Combo" serializer used for JDK types that work almost like
 * [ToStringSerializer][org.cirjson.cirjackson.databind.serialization.standard.ToStringSerializer].
 */
@CirJacksonStandardImplementation
open class JDKStringLikeSerializer(handledType: KClass<*>, protected val myType: Int) :
        StandardSerializer<Any>(handledType) {

    override fun isEmpty(provider: SerializerProvider, value: Any?): Boolean {
        return value!!.toString().isEmpty()
    }

    @Throws(CirJacksonException::class)
    override fun serialize(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider) {
        val string = when (myType) {
            TYPE_FILE -> (value as File).absolutePath
            TYPE_PATH -> (value as Path).toUri().toString()
            TYPE_CLASS -> ((value as? Class<*>)?.kotlin ?: value as KClass<*>).qualifiedName
            TYPE_LOCALE -> (value as Locale).takeUnless { it === Locale.ROOT }?.toLanguageTag() ?: ""
            else -> value.toString()
        }

        generator.writeString(string)
    }

    companion object {

        const val TYPE_URL = 1

        const val TYPE_URI = 2

        const val TYPE_FILE = 3

        const val TYPE_PATH = 4

        const val TYPE_CLASS = 5

        const val TYPE_CURRENCY = 6

        const val TYPE_LOCALE = 7

        const val TYPE_PATTERN = 8

        private val ourTypes = mapOf(URL::class to TYPE_URL, URI::class to TYPE_URI, File::class to TYPE_FILE,
                Path::class to TYPE_PATH, Class::class to TYPE_CLASS, KClass::class to TYPE_CLASS,
                Currency::class to TYPE_CURRENCY, Locale::class to TYPE_LOCALE, Pattern::class to TYPE_PATTERN)

        fun find(type: KClass<*>): ValueSerializer<*>? {
            return ourTypes[type]?.let { JDKStringLikeSerializer(type, it) }
        }

    }

}