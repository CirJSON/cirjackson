package org.cirjson.cirjackson.databind.external.sql

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatTypes
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.standard.StandardScalarSerializer
import java.sql.Blob
import java.sql.SQLException

/**
 * Serializer implementation for [Blob] to write as binary (for CirJSON and other formats Base64-encoded String, for
 * binary formats as true binary).
 *
 * @see org.cirjson.cirjackson.databind.serialization.jdk.ByteArraySerializer
 */
@CirJacksonStandardImplementation
open class JavaSqlBlobSerializer : StandardScalarSerializer<Blob>(Blob::class) {

    @Throws(CirJacksonException::class)
    override fun serialize(value: Blob, generator: CirJsonGenerator, serializers: SerializerProvider) {
        writeValue(value, generator, serializers)
    }

    @Throws(CirJacksonException::class)
    override fun serializeWithType(value: Blob, generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        val typeIdDefinition = typeSerializer.writeTypePrefix(generator, serializers,
                typeSerializer.typeId(value, CirJsonToken.VALUE_STRING))
        writeValue(value, generator, serializers)
        typeSerializer.writeTypeSuffix(generator, serializers, typeIdDefinition)
    }

    @Throws(CirJacksonException::class)
    protected open fun writeValue(value: Blob, generator: CirJsonGenerator, context: SerializerProvider) {
        val inputStream = try {
            value.binaryStream
        } catch (e: SQLException) {
            return context.reportMappingProblem(e, "Failed to access `java.sql.Blob` value to write as binary value")
        }

        generator.writeBinary(context.config.base64Variant, inputStream, -1)
    }

    override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
        visitor.expectArrayFormat(typeHint)?.itemsFormat(CirJsonFormatTypes.INTEGER)
    }

}