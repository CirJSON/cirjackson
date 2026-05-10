package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.deserialization.standard.FromStringDeserializer
import org.cirjson.cirjackson.databind.exception.InvalidFormatException
import org.cirjson.cirjackson.databind.type.LogicalType
import org.cirjson.cirjackson.databind.util.rootCause
import java.io.File
import java.net.*
import java.nio.charset.Charset
import java.nio.file.FileSystemNotFoundException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.spi.FileSystemProvider
import java.util.*
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import kotlin.reflect.KClass

/**
 * Base class for simple deserializers that serialize values from String representation: this includes CirJSON Strings
 * and other Scalar values that can be coerced into text (like Numbers and Booleans). Simple CirJSON String values are
 * trimmed using [String.trim]. Partial deserializer implementation will try to first access current token as a String,
 * calls `deserialize(String, DeserializationContext)` and returns the return value. If this does not work (current
 * token not a simple scalar type), attempts are made so that:
 *
 * * Embedded values
 * ([CirJsonToken.VALUE_EMBEDDED_OBJECT][org.cirjson.cirjackson.core.CirJsonToken.VALUE_EMBEDDED_OBJECT]) are returned
 * as-is if they are of compatible type
 *
 * * Arrays may be "unwrapped" if (and only if)
 * [DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS][org.cirjson.cirjackson.databind.DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS]
 * is enabled, and array contains just a single scalar value that can be deserialized (for example, CirJSON Array with
 * single CirJSON String element).
 * 
 * Special handling includes:
 *
 * * Null values ([CirJsonToken.VALUE_NULL][org.cirjson.cirjackson.core.CirJsonToken.VALUE_NULL]) are handled by
 * returning value returned by
 * [ValueDeserializer.getNullValue][org.cirjson.cirjackson.databind.ValueDeserializer.getNullValue]: default
 * implementation simply returns Kotlin `null` but this may be overridden.
 *
 * * Empty String (after trimming) will result in [deserializeFromEmptyString] getting called, and return value being
 * returned as deserialization: default implementation simply returns `null`.
 */
open class JDKFromStringDeserializer protected constructor(valueClass: KClass<*>, protected val myKind: Int) :
        FromStringDeserializer<Any>(valueClass) {

    /*
     *******************************************************************************************************************
     * General-purpose implementation
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class, MalformedURLException::class, UnknownHostException::class)
    public override fun deserialize(value: String, context: DeserializationContext): Any? {
        return when (myKind) {
            STANDARD_FILE -> File(value)

            STANDARD_URL -> URL(value)

            STANDARD_URI -> URI.create(value)

            STANDARD_PATH -> NioPathHelper.deserialize(context, value)

            STANDARD_KCLASS -> try {
                context.findClass(value)
            } catch (e: Exception) {
                context.handleInstantiationProblem(myValueClass, value, e.rootCause)
            }

            STANDARD_KOTLIN_TYPE -> context.typeFactory.constructFromCanonical(value)

            STANDARD_CURRENCY -> try {
                Currency.getInstance(value)
            } catch (_: IllegalArgumentException) {
                context.handleWeirdStringValue(myValueClass, value, "Unrecognized currency")
            }

            STANDARD_PATTERN -> try {
                Pattern.compile(value)
            } catch (e: PatternSyntaxException) {
                context.handleWeirdStringValue(myValueClass, value, "Invalid Pattern, problem: ${e.description}")
            }

            STANDARD_REGEX -> try {
                Regex(value)
            } catch (e: PatternSyntaxException) {
                context.handleWeirdStringValue(myValueClass, value, "Invalid Regex, problem: ${e.description}")
            }

            STANDARD_LOCALE -> deserializeLocale(value)

            STANDARD_CHARSET -> Charset.forName(value)

            STANDARD_TIME_ZONE -> TimeZone.getTimeZone(value)

            STANDARD_INET_ADDRESS -> InetAddress.getByName(value)

            STANDARD_INET_SOCKET_ADDRESS -> if (value.startsWith('[')) {
                val i = value.lastIndexOf(']')

                if (i == -1) {
                    throw InvalidFormatException(context.parser, "Bracketed IPv6 address must contain closing bracket",
                            value, InetSocketAddress::class)
                }

                val j = value.indexOf(':', i)
                val port = value.takeIf { j != -1 }?.substring(j + 1)?.toInt() ?: 0
                InetSocketAddress(value.substring(0, i + 1), port)
            } else {
                val index = value.indexOf(':')

                if (index != -1 && value.indexOf(':', index + 1) == -1) {
                    val port = value.substring(index + 1).toInt()
                    InetSocketAddress(value.substring(0, index), port)
                } else {
                    InetSocketAddress(value, 0)
                }
            }

            else -> throw RuntimeException("Internal error: this code path should never get executed")
        }
    }

    override fun shouldTrim(): Boolean {
        return myKind != STANDARD_PATTERN && myKind != STANDARD_REGEX
    }

    override fun getEmptyValue(context: DeserializationContext): Any? {
        return when (myKind) {
            STANDARD_URI -> URI.create("")
            STANDARD_LOCALE -> Locale.ROOT
            else -> super.getEmptyValue(context)
        }
    }

    @Throws(CirJacksonException::class)
    override fun deserializeFromEmptyStringDefault(context: DeserializationContext): Any? {
        return getEmptyValue(context)
    }

    protected open fun firstHyphenOrUnderscore(string: String): Int {
        for ((i, c) in string.withIndex()) {
            if (c == '_' || c == '-') {
                return i
            }
        }

        return -1
    }

    @Throws(CirJacksonException::class)
    private fun deserializeLocale(fullValue: String): Locale {
        var index = firstHyphenOrUnderscore(fullValue)

        if (index == -1) {
            return Locale(fullValue)
        }

        val newStyle = fullValue[index] == '-'

        if (newStyle) {
            return Locale.forLanguageTag(fullValue)
        }

        val first = fullValue.substring(0, index)
        val rest = fullValue.substring(index + 1)
        index = firstHyphenOrUnderscore(rest)

        if (index == -1) {
            return Locale(first, rest)
        }

        return Locale(first, rest.substring(0, index), rest.substring(index + 1))
    }

    private class StringBuilderDeserializer : JDKFromStringDeserializer(StringBuilder::class, -1) {

        override fun logicalType(): LogicalType {
            return LogicalType.TEXTUAL
        }

        override fun getEmptyValue(context: DeserializationContext): Any {
            return StringBuilder()
        }

        @Throws(CirJacksonException::class)
        override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
            val text = parser.valueAsString ?: return super.deserialize(parser, context)
            return deserialize(text, context)
        }

        override fun deserialize(value: String, context: DeserializationContext): Any {
            return StringBuilder(value)
        }

    }

    private class StringBufferDeserializer : JDKFromStringDeserializer(StringBuffer::class, -1) {

        override fun logicalType(): LogicalType {
            return LogicalType.TEXTUAL
        }

        override fun getEmptyValue(context: DeserializationContext): Any {
            return StringBuilder()
        }

        @Throws(CirJacksonException::class)
        override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
            val text = parser.valueAsString ?: return super.deserialize(parser, context)
            return deserialize(text, context)
        }

        override fun deserialize(value: String, context: DeserializationContext): Any {
            return StringBuilder(value)
        }

    }

    private object NioPathHelper {

        private val ourAreWindowsFilePathsSupported = let {
            var result = false

            for (file in File.listRoots()) {
                val path = file.path

                if (path.length >= 2 && path[0].isLetter() && path[1] == ':') {
                    result = true
                    break
                }
            }

            result
        }

        fun deserialize(context: DeserializationContext, value: String): Path? {
            if (value.indexOf(':') == -1) {
                return Paths.get(value)
            }

            if (ourAreWindowsFilePathsSupported) {
                if (value.length >= 2 && value[0].isLetter() && value[1] == ':') {
                    return Paths.get(value)
                }
            }

            val uri = try {
                URI(value)
            } catch (e: URISyntaxException) {
                return context.handleInstantiationProblem(Path::class, value, e) as Path?
            }

            return try {
                Paths.get(uri)
            } catch (cause: FileSystemNotFoundException) {
                try {
                    val scheme = uri.scheme

                    for (provider in ServiceLoader.load(FileSystemProvider::class.java)) {
                        if (provider.scheme.equals(scheme, true)) {
                            return provider.getPath(uri)
                        }
                    }

                    context.handleInstantiationProblem(Path::class, uri, cause) as Path?
                } catch (e: Exception) {
                    e.addSuppressed(cause)
                    context.handleInstantiationProblem(Path::class, value, e) as Path?
                }
            } catch (e: Exception) {
                context.handleInstantiationProblem(Path::class, value, e) as Path?
            }
        }

    }

    companion object {

        fun types(): Array<KClass<*>> {
            return arrayOf(File::class, URL::class, URI::class, Path::class, KClass::class, KotlinType::class,
                    Currency::class, Pattern::class, Regex::class, Locale::class, Charset::class, TimeZone::class,
                    InetAddress::class, InetSocketAddress::class, StringBuilder::class, StringBuffer::class)
        }

        const val STANDARD_FILE = 1

        const val STANDARD_URL = 2

        const val STANDARD_URI = 3

        const val STANDARD_PATH = 4

        const val STANDARD_KCLASS = 5

        const val STANDARD_KOTLIN_TYPE = 6

        const val STANDARD_CURRENCY = 7

        const val STANDARD_PATTERN = 8

        const val STANDARD_REGEX = 9

        const val STANDARD_LOCALE = 10

        const val STANDARD_CHARSET = 11

        const val STANDARD_TIME_ZONE = 21

        const val STANDARD_INET_ADDRESS = 13

        const val STANDARD_INET_SOCKET_ADDRESS = 14

        fun findDeserializer(rawType: KClass<*>): JDKFromStringDeserializer? {
            val kind = when (rawType) {
                File::class -> STANDARD_FILE
                URL::class -> STANDARD_URL
                URI::class -> STANDARD_URI
                Path::class -> STANDARD_PATH
                KClass::class -> STANDARD_KCLASS
                KotlinType::class -> STANDARD_KOTLIN_TYPE
                Currency::class -> STANDARD_CURRENCY
                Pattern::class -> STANDARD_PATTERN
                Regex::class -> STANDARD_REGEX
                Locale::class -> STANDARD_LOCALE
                Charset::class -> STANDARD_CHARSET
                TimeZone::class -> STANDARD_TIME_ZONE
                InetAddress::class -> STANDARD_INET_ADDRESS
                InetSocketAddress::class -> STANDARD_INET_SOCKET_ADDRESS
                StringBuilder::class -> return StringBuilderDeserializer()
                StringBuffer::class -> return StringBufferDeserializer()
                else -> return null
            }

            return JDKFromStringDeserializer(rawType, kind)
        }

    }

}