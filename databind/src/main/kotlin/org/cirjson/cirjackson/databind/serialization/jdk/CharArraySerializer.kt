package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializationFeature
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatTypes
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.standard.StandardSerializer

/**
 * Character arrays are different from other integral number arrays in that they are most likely to be textual data, and
 * should be written as Strings, not arrays of entries.
 * 
 * NOTE: since it is NOT serialized as an array, cannot use AsArraySerializer as base
 */
@CirJacksonStandardImplementation
open class CharArraySerializer : StandardSerializer<CharArray>(CharArray::class) {

    @Throws(CirJacksonException::class)
    override fun isEmpty(provider: SerializerProvider, value: CharArray?): Boolean {
        return value!!.isEmpty()
    }

    @Throws(CirJacksonException::class)
    override fun serialize(value: CharArray, generator: CirJsonGenerator, serializers: SerializerProvider) {
        if (serializers.isEnabled(SerializationFeature.WRITE_CHAR_ARRAYS_AS_CIRJSON_ARRAYS)) {
            generator.writeStartArray(value, value.size)
            writeArrayContents(generator, value)
            generator.writeEndArray()
        } else {
            generator.writeString(value, 0, value.size)
        }
    }

    @Throws(CirJacksonException::class)
    override fun serializeWithType(value: CharArray, generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        val asArray = serializers.isEnabled(SerializationFeature.WRITE_CHAR_ARRAYS_AS_CIRJSON_ARRAYS)

        val typeIdDefinition = if (asArray) {
            typeSerializer.writeTypePrefix(generator, serializers,
                    typeSerializer.typeId(value, CirJsonToken.START_ARRAY))
                    .also { writeArrayContents(generator, value) }
        } else {
            typeSerializer.writeTypePrefix(generator, serializers,
                    typeSerializer.typeId(value, CirJsonToken.VALUE_STRING))
                    .also { generator.writeString(value, 0, value.size) }
        }

        typeSerializer.writeTypeSuffix(generator, serializers, typeIdDefinition)
    }

    @Throws(CirJacksonException::class)
    private fun writeArrayContents(generator: CirJsonGenerator, value: CharArray) {
        for (i in value.indices) {
            generator.writeString(value, i, 1)
        }
    }

    override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
        visitArrayFormat(visitor, typeHint, CirJsonFormatTypes.STRING)
    }

}