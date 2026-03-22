package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.standard.StandardScalarSerializer
import java.net.InetAddress

/**
 * Simple serializer for [InetAddress]. Main complexity is with registration, since same serializer is to be used for
 * subclasses.
 * 
 * Allows use of [CirJsonFormat] configuration (annotation, per-type defaulting) so that if [CirJsonFormat.Shape.NUMBER]
 * (or [CirJsonFormat.Shape.ARRAY]) is used, will serialize as "host address" (dotted numbers) instead of simple
 * conversion.
 */
open class InetAddressSerializer(protected val myAsNumeric: Boolean) :
        StandardScalarSerializer<InetAddress>(InetAddress::class) {

    constructor() : this(false)

    override fun createContextual(provider: SerializerProvider, property: BeanProperty?): ValueSerializer<*> {
        val format = findFormatOverrides(provider, property, handledType()!!)
        var asNumeric = false
        val shape = format.shape

        if (shape.isNumeric || shape == CirJsonFormat.Shape.ARRAY) {
            asNumeric = true
        }

        if (asNumeric == myAsNumeric) {
            return this
        }

        return InetAddressSerializer(asNumeric)
    }

    @Throws(CirJacksonException::class)
    override fun serialize(value: InetAddress, generator: CirJsonGenerator, serializers: SerializerProvider) {
        if (myAsNumeric) {
            generator.writeString(value.hostAddress)
            return
        }

        var string = value.toString().trim()
        val index = string.indexOf('/')

        if (index != -1) {
            string = if (index == 0) {
                string.substring(1)
            } else {
                string.substring(0, index)
            }
        }

        generator.writeString(string)
    }

    @Throws(CirJacksonException::class)
    override fun serializeWithType(value: InetAddress, generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        val typeIdDefinition = typeSerializer.writeTypePrefix(generator, serializers,
                typeSerializer.typeId(value, InetAddress::class, CirJsonToken.VALUE_STRING))
        serialize(value, generator, serializers)
        typeSerializer.writeTypeSuffix(generator, serializers, typeIdDefinition)
    }

}