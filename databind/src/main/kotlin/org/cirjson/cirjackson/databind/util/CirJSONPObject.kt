package org.cirjson.cirjackson.databind.util

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.util.CirJsonpCharacterEscapes
import org.cirjson.cirjackson.databind.CirJacksonSerializable
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer

/**
 * Container class that can be used to wrap any Object instances (including `nulls`), and will serialize embedded in
 * CirJSONP wrapping.
 *
 * @see CirJSONWrappedObject
 *
 * @property function CirJSONP function name to use for serialization
 *
 * @property value Value to be serialized as CirJSONP padded; can be `null`.
 *
 * @property serializationType Optional static type to use for serialization; if `null`, runtime type is used. Can be
 * used to specify declared type which defines serializer to use, as well as aspects of extra type information to
 * include (if any).
 */
open class CirJSONPObject(val function: String, val value: Any?, val serializationType: KotlinType? = null) :
        CirJacksonSerializable {

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
        generator.writeRaw(function)
        generator.writeRaw('(')

        if (value == null) {
            context.defaultSerializeNullValue(generator)
            generator.writeRaw(')')
            return
        }

        val override = generator.characterEscapes == null

        if (override) {
            generator.characterEscapes = CirJsonpCharacterEscapes
        }

        try {
            if (serializationType != null) {
                context.findTypedValueSerializer(serializationType, true).serialize(value, generator, context)
            } else {
                context.findTypedValueSerializer(value::class, true).serialize(value, generator, context)
            }
        } finally {
            if (override) {
                generator.characterEscapes = null
            }
        }

        generator.writeRaw(')')
    }

}