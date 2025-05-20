package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.databind.util.isArray
import org.cirjson.cirjackson.databind.util.isAssignableFrom
import org.cirjson.cirjackson.databind.util.isEnumType
import kotlin.reflect.KClass

/**
 * Set of logical types (or type categories, classes of classes), used for defining applicability of configuration like
 * coercion configuration. Used instead to allow easier targeting of types than having to enumerate physical types
 * ([KClass] or [org.cirjson.cirjackson.databind.KotlinType]).
 */
enum class LogicalType {

    /**
     * Array types of other values.
     *
     * Note: excludes binary type [ByteArray].
     */
    ARRAY,

    /**
     * [Collection] values (and "Collection-like" for JVM languages and datatype libraries with semantically similar
     * types)
     */
    COLLECTION,

    /**
     * [Map] values (and "Map-like" for JVM languages and datatype libraries with semantically similar types)
     */
    MAP,

    // // // Other structured java types

    /**
     * Types that are handled by default "set of key/value pairs" serialization, also known as "Beans".
     *
     * In addition to user-defined types, it also includes JDK types like:
     *
     * * [Throwable]
     */
    POJO,

    /**
     * "Non-type", Type used to the contained untyped, free-form content: maybe a "Tree" (sometimes called "AST"), or
     * buffer of some kind, or even just nominal type of [Any]
     */
    UNTYPED,

    // // // Basic scalar types

    /**
     * Basic integral numbers types like [Short], [Int], [Long] and matching wrapper types, [java.math.BigInteger].
     */
    INTEGER,

    /**
     * Basic floating-point numbers types like [Float], [Double], and matching wrapper types, [java.math.BigDecimal].
     */
    FLOAT,

    /**
     * [Boolean], [java.util.concurrent.atomic.AtomicBoolean].
     */
    BOOLEAN,

    /**
     * Various [Enum] types.
     */
    ENUM,

    /**
     * Purely textual types, [String] and similar (but not types that are generally expressed as Strings in input).
     */
    TEXTUAL,

    /**
     * Binary data such as [ByteArray] and [java.nio.ByteBuffer].
     */
    BINARY,

    /**
     * Date/time datatypes such as [java.util.Date], [java.util.Calendar].
     */
    DATE_TIME,

    /**
     * Scalar types other than ones listed above: includes types like [java.net.URL] and [java.util.UUID].
     */
    OTHER_SCALAR;

    companion object {

        /**
         * Helper method to use for figuring out logical type from physical type, in cases where caller wants a guess.
         * Note that introspection is not exhaustive and mostly covers basic [Collection], [Map] and [Enum] cases; but
         * not more specific types (for example, datatype-provided extension types).
         *
         * @param raw Type-erased class to classify
         *
         * @param default if no type recognized, value to return (for example, `null`)
         */
        fun fromClass(raw: KClass<*>, default: LogicalType?): LogicalType? {
            if (raw.isEnumType) {
                return ENUM
            }

            if (raw.isArray) {
                if (raw == ByteArray::class) {
                    return BINARY
                }

                return ARRAY
            }

            if (Map::class.isAssignableFrom(raw)) {
                return MAP
            }

            if (Collection::class.isAssignableFrom(raw)) {
                return COLLECTION
            }

            if (raw == String::class) {
                return TEXTUAL
            }

            return default
        }

    }

}