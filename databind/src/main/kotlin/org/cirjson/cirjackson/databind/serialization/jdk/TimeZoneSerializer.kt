package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.standard.StandardScalarSerializer
import java.util.*

open class TimeZoneSerializer : StandardScalarSerializer<TimeZone>(TimeZone::class) {

    @Throws(CirJacksonException::class)
    override fun serialize(value: TimeZone, generator: CirJsonGenerator, serializers: SerializerProvider) {
        generator.writeString(value.id)
    }

    override fun serializeWithType(value: TimeZone, generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        val typeIdDefinition = typeSerializer.writeTypePrefix(generator, serializers,
                typeSerializer.typeId(value, TimeZone::class, CirJsonToken.VALUE_STRING))
        serialize(value, generator, serializers)
        typeSerializer.writeTypeSuffix(generator, serializers, typeIdDefinition)
    }

}