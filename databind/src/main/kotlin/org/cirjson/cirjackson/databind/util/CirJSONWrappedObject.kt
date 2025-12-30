package org.cirjson.cirjackson.databind.util

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.CirJacksonSerializable
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer

/**
 * General-purpose wrapper class that can be used to decorate serialized value with arbitrary literal prefix and suffix.
 * This can be used, for example, to construct arbitrary JavaScript values (similar to how basic function name and
 * parenthesis are used with CirJSONP).
 *
 * @see CirJSONPObject
 *
 * @constructor Constructor that should be used when the specific serialization type to use is important, and needs to
 * be passed instead of just using the runtime (type-erased) type of the value.
 *
 * @property prefix Literal String to output before serialized value. Will not be quoted when serializing value.
 *
 * @property suffix Literal String to output after serialized value. Will not be quoted when serializing value.
 *
 * @property value Value to be serialized as CirJSONP padded; can be `null`.
 *
 * @property serializationType Optional static type to use for serialization; if `null`, runtime type is used. Can be
 * used to specify declared type which defines serializer to use, as well as aspects of extra type information to
 * include (if any).
 */
open class CirJSONWrappedObject(val prefix: String?, val suffix: String?, val value: Any?,
        val serializationType: KotlinType? = null) : CirJacksonSerializable {

    /*
     *******************************************************************************************************************
     * CirJacksonSerializable implementation
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun serialize(generator: CirJsonGenerator, context: SerializerProvider,
            typeSerializer: TypeSerializer) {
        serialize(generator, context)
    }

    @Throws(CirJacksonException::class)
    override fun serialize(generator: CirJsonGenerator, context: SerializerProvider) {
        if (prefix != null) {
            generator.writeRaw(prefix)
        }

        if (value == null) {
            context.defaultSerializeNullValue(generator)
        } else if (serializationType != null) {
            context.findTypedValueSerializer(serializationType, true).serialize(value, generator, context)
        } else {
            context.findTypedValueSerializer(value::class, true).serialize(value, generator, context)
        }

        if (suffix != null) {
            generator.writeRaw(suffix)
        }
    }

}