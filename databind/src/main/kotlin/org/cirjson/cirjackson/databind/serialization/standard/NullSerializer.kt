package org.cirjson.cirjackson.databind.serialization.standard

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer

/**
 * This is a simple dummy serializer that will just output literal CirJSON `null` value whenever serialization is
 * requested. Used as the default "null serializer" (which is used for serializing `null` object references unless
 * overridden), as well as for some more exotic types (java.lang.Void).
 */
@CirJacksonStandardImplementation
object NullSerializer : StandardSerializer<Any>(Any::class) {

    @Throws(CirJacksonException::class)
    override fun serialize(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider) {
        generator.writeNull()
    }

    @Throws(CirJacksonException::class)
    override fun serializeNullable(value: Any?, generator: CirJsonGenerator, serializers: SerializerProvider) {
        generator.writeNull()
    }

    /**
     * Although this method should rarely get called, for convenience we should override it, and handle it same way as
     * "natural" types: by serializing exactly as is, without type decorations. The most common possible use case is
     * that of delegation by CirJSON filter; caller cannot know what kind of serializer it gets handed.
     */
    @Throws(CirJacksonException::class)
    override fun serializeWithType(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        generator.writeNull()
    }

    override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
        visitor.expectNullFormat(typeHint)
    }

}