package org.cirjson.cirjackson.databind.testutil

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.databind.ObjectMapper
import org.cirjson.cirjackson.databind.ObjectReader
import org.cirjson.cirjackson.databind.ObjectWriter
import org.cirjson.cirjackson.databind.cirjson.CirJsonMapper
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import org.cirjson.cirjackson.databind.introspection.CirJacksonAnnotationIntrospector
import org.cirjson.cirjackson.databind.type.TypeFactory
import org.cirjson.cirjackson.databind.util.isAssignableFrom
import java.io.*
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.*

/**
 * Class containing test utility methods.
 */
open class DatabindTestUtil {

    /*
     *******************************************************************************************************************
     * Helper methods, serialization
     *******************************************************************************************************************
     */

    protected fun serializeAsString(mapper: ObjectMapper, value: Any): String {
        return mapper.writeValueAsString(value)
    }

    protected fun serializeAsString(value: Any): String {
        return serializeAsString(sharedMapper(), value)
    }

    /*
     *******************************************************************************************************************
     * Additional assertion methods
     *******************************************************************************************************************
     */

    protected fun assertType(value: Any?, expectedType: KClass<*>) {
        assertNotNull(value, "Expected an object of type $expectedType, got null")
        val actualType = value::class
        assertTrue(expectedType.isAssignableFrom(actualType), "Expected type $expectedType, got $actualType")
    }

    /**
     * Helper method for verifying 3 basic cookie cutter cases; identity comparison (`true`), and against `null`
     * (`false`), or object of different type (`false`)
     */
    protected fun assertStandardEquals(value: Any?) {
        assertEquals(value, value)
        assertNotEquals(null, value)
        assertNotEquals(SINGLETON_OBJECT, value)
        value?.hashCode()
    }

    /*
     *******************************************************************************************************************
     * Helper methods, other
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun createParserUsingReader(input: String): CirJsonParser {
        return createParserUsingReader(CirJsonFactory(), input)
    }

    @Throws(CirJacksonException::class)
    protected fun createParserUsingReader(factory: CirJsonFactory, input: String): CirJsonParser {
        return factory.createParser(ObjectReadContext.empty(), StringReader(input))
    }

    /*
     *******************************************************************************************************************
     * Helper classes
     *******************************************************************************************************************
     */

    @Target(AnnotationTarget.VALUE_PARAMETER)
    @Retention(AnnotationRetention.RUNTIME)
    protected annotation class ImplicitName(val value: String)

    open class ImplicitNameIntrospector : CirJacksonAnnotationIntrospector() {

        override fun findImplicitPropertyName(config: MapperConfig<*>, member: AnnotatedMember): String? {
            val annotation = member.getAnnotation(ImplicitName::class)
            return annotation?.value
        }

    }

    /**
     * Enumeration type with subclasses per value.
     */
    enum class EnumWithSubclass {

        A {
            override fun foobar() {
                TODO("Not yet implemented")
            }
        },

        B {
            override fun foobar() {
                TODO("Not yet implemented")
            }
        };

        abstract fun foobar()

    }

    /*
     *******************************************************************************************************************
     * Shared helper classes
     *******************************************************************************************************************
     */

    open class IntWrapper(val i: Int) {

        constructor() : this(0)

    }

    open class LongWrapper(val l: Long) {

        constructor() : this(0L)

    }

    open class FloatWrapper(val f: Float) {

        constructor() : this(0.0f)

    }

    open class DoubleWrapper(val d: Double) {

        constructor() : this(0.0)

    }

    /**
     * Simple wrapper around String type, usually to test value conversions or wrapping
     */
    open class StringWrapper(val string: String?) {

        constructor() : this(null)

    }

    enum class ABC { A, B, C }

    open class Point(val x: Int, val y: Int) {

        protected constructor() : this(0, 0)

        override fun equals(other: Any?): Boolean {
            return other is Point && x == other.x && y == other.y
        }

        override fun toString(): String {
            return "[x=$x, y=$y]"
        }

    }

    open class MapWrapper<K, V>(val map: MutableMap<K, V>?) {

        constructor() : this(null)

        constructor(key: K, value: V) : this(linkedMapOf(key to value))

    }

    protected open class ListWrapper<T>(vararg values: T) {

        val list: MutableList<T> = arrayListOf(*values)

    }

    protected open class ArrayWrapper<T>(val array: Array<T>)

    open class BogusSchema : FormatSchema {

        override val schemaType: String
            get() = "TestFormat"

    }

    companion object {

        private val SINGLETON_OBJECT = Any()

        /*
         ***************************************************************************************************************
         * A sample documents
         ***************************************************************************************************************
         */

        const val SAMPLE_SPEC_VALUE_WIDTH = 800

        const val SAMPLE_SPEC_VALUE_HEIGHT = 600

        const val SAMPLE_SPEC_VALUE_TITLE = "View from 15th Floor"

        const val SAMPLE_SPEC_VALUE_TN_URL = "http://www.example.com/image/481989943"

        const val SAMPLE_SPEC_VALUE_TN_HEIGHT = 125

        const val SAMPLE_SPEC_VALUE_TN_WIDTH = "100"

        const val SAMPLE_SPEC_VALUE_TN_ID1 = 116

        const val SAMPLE_SPEC_VALUE_TN_ID2 = 943

        const val SAMPLE_SPEC_VALUE_TN_ID3 = 234

        const val SAMPLE_SPEC_VALUE_TN_ID4 = 38793

        const val SAMPLE_DOC_CIRJSON_SPEC = """{
  "__cirJsonId__": "root",
  "Image" : {
    "__cirJsonId__": "Image",
    "Width" : $SAMPLE_SPEC_VALUE_WIDTH,
    "Height" : $SAMPLE_SPEC_VALUE_HEIGHT,"Title" : "$SAMPLE_SPEC_VALUE_TITLE",
    "Thumbnail" : {
    "__cirJsonId__": "Image/Thumbnail",
      "Url" : "$SAMPLE_SPEC_VALUE_TN_URL",
"Height" : $SAMPLE_SPEC_VALUE_TN_HEIGHT,
      "Width" : "$SAMPLE_SPEC_VALUE_TN_WIDTH"
    },
    "IDs" : ["ids", $SAMPLE_SPEC_VALUE_TN_ID1,$SAMPLE_SPEC_VALUE_TN_ID2,$SAMPLE_SPEC_VALUE_TN_ID3,$SAMPLE_SPEC_VALUE_TN_ID4]
  }
}"""

        fun verifyCirJsonSpecSampleDoc(parser: CirJsonParser, verifyContents: Boolean) {
            verifyCirJsonSpecSampleDoc(parser, verifyContents, true)
        }

        fun verifyCirJsonSpecSampleDoc(parser: CirJsonParser, verifyContents: Boolean, requireNumbers: Boolean) {

        }

        private fun verifyIntToken(token: CirJsonToken, requireNumbers: Boolean) {

        }

        private fun verifyFieldName(parser: CirJsonParser, expectedName: String) {

        }

        private fun verifyIntValue(parser: CirJsonParser, expectedValue: Long) {

        }

        /*
         ***************************************************************************************************************
         * Factory methods
         ***************************************************************************************************************
         */

        private val SHARED_MAPPER by lazy { newCirJsonMapper() }

        fun sharedMapper(): ObjectMapper {
            return SHARED_MAPPER
        }

        fun objectMapper(): ObjectMapper {
            return sharedMapper()
        }

        fun objectWriter(): ObjectWriter {
            return sharedMapper().writer()
        }

        fun objectReader(): ObjectReader {
            return sharedMapper().reader()
        }

        fun newTypeFactory(): TypeFactory {
            return TypeFactory.DEFAULT_INSTANCE.withModifier(null)
        }

        /*
         ***************************************************************************************************************
         * Mapper construction helpers
         ***************************************************************************************************************
         */

        fun newCirJsonMapper(): CirJsonMapper {
            return cirJsonMapperBuilder().build()
        }

        fun cirJsonMapperBuilder(): CirJsonMapper.Builder {
            return CirJsonMapper.builder().enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
        }

        /*
         ***************************************************************************************************************
         * Helper methods, serialization
         ***************************************************************************************************************
         */

        @Suppress("UNCHECKED_CAST")
        fun writeAndMap(mapper: ObjectMapper, value: Any): MutableMap<String, Any?> {
            val string = mapper.writeValueAsString(value)
            return mapper.readValue(string, LinkedHashMap::class) as MutableMap<String, Any?>
        }

        /*
         ***************************************************************************************************************
         * Encoding or String representations
         ***************************************************************************************************************
         */

        fun apostropheToQuote(string: String): String {
            return string.replace('\'', '"')
        }

        fun quote(string: String): String {
            return "\"$string\""
        }

        fun utf8Bytes(string: String): ByteArray {
            return string.toByteArray()
        }

        /*
         ***************************************************************************************************************
         * Additional assertion methods
         ***************************************************************************************************************
         */

        fun assertToken(expectedToken: CirJsonToken, actualToken: CirJsonToken) {
            assertEquals(expectedToken, actualToken, "Expected token $expectedToken, current token $actualToken")
        }

        fun assertValidLocation(location: CirJsonLocation) {
            assertTrue(location.lineNumber > 0, "Should have positive line number")
        }

        fun verifyException(e: Exception, expectedType: KClass<*>, expectedMessage: String?) {
            assertEquals(e::class, expectedType,
                    "Expected exception of type ${expectedType.qualifiedName}, got ${e::class.qualifiedName}")
            expectedMessage?.let { verifyException(e, expectedMessage) }
        }

        /**
         * @param e Exception to check
         *
         * @param anyMatches Array of Strings of which AT LEAST ONE ("any") has to be included in `e.message` -- using
         * case-INSENSITIVE comparison
         */
        fun verifyException(e: Throwable, vararg anyMatches: String) {
            val message = e.message?.lowercase() ?: ""

            for (match in anyMatches) {
                if (match.lowercase() in message) {
                    return
                }
            }

            fail("Expected an exception with one of substrings (${anyMatches.contentToString()}): got one (of type ${e::class.qualifiedName}) with message \"${e.message}\"")
        }

        /**
         * Method that gets textual contents of the current token using available methods, and ensures results are
         * consistent, before returning them
         */
        fun getAndVerifyText(parser: CirJsonParser): String {
            val actualLength = parser.textLength
            val chars = parser.textCharacters!!
            val string2 = String(chars, parser.textOffset, actualLength)
            val string = parser.text!!

            assertEquals(string.length, actualLength,
                    "Internal problem (parser.token == ${parser.currentToken()}): parser.text.length ['$string'] == ${string.length}; parser.textLength == $actualLength")
            assertEquals(string, string2, "String access via text, textXxx must be the same")

            return string
        }

        /*
         ***************************************************************************************************************
         * JDK ser/deser
         ***************************************************************************************************************
         */

        fun jdkSerialize(value: Any): ByteArray {
            val bytes = ByteArrayOutputStream(2000)

            try {
                ObjectOutputStream(bytes).use {
                    it.writeObject(value)
                    it.close()
                    return bytes.toByteArray()
                }
            } catch (e: IOException) {
                throw UncheckedIOException(e)
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun <T : Any> jdkDeserialize(raw: ByteArray): T? {
            try {
                ObjectInputStream(ByteArrayInputStream(raw)).use {
                    return it.readObject() as T?
                }
            } catch (e: ClassNotFoundException) {
                fail("Missing class: ${e.message}")
            } catch (e: IOException) {
                throw UncheckedIOException(e)
            }
        }

        /*
         ***************************************************************************************************************
         * Helper methods, other
         ***************************************************************************************************************
         */

        val utcTimeZone: TimeZone
            get() = TimeZone.getTimeZone("GMT")

    }

}