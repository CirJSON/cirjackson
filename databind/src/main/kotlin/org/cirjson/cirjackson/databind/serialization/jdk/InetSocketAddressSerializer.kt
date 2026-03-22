package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.standard.StandardScalarSerializer
import java.net.Inet6Address
import java.net.InetSocketAddress

/**
 * Simple serializer for [InetSocketAddress].
 */
open class InetSocketAddressSerializer : StandardScalarSerializer<InetSocketAddress>(InetSocketAddress::class) {

    @Throws(CirJacksonException::class)
    override fun serialize(value: InetSocketAddress, generator: CirJsonGenerator, serializers: SerializerProvider) {
        val address = value.address
        var string = address?.toString()?.trim() ?: value.hostName
        val index = string.indexOf('/')

        if (index != -1) {
            string = if (index == 0) {
                if (address is Inet6Address) {
                    "[${string.substring(1)}]"
                } else {
                    string.substring(1)
                }
            } else {
                string.substring(0, index)
            }
        }

        generator.writeString("$string:${value.port}")
    }

    @Throws(CirJacksonException::class)
    override fun serializeWithType(value: InetSocketAddress, generator: CirJsonGenerator,
            serializers: SerializerProvider, typeSerializer: TypeSerializer) {
        val typeIdDefinition = typeSerializer.writeTypePrefix(generator, serializers,
                typeSerializer.typeId(value, InetSocketAddress::class, CirJsonToken.VALUE_STRING))
        serialize(value, generator, serializers)
        typeSerializer.writeTypeSuffix(generator, serializers, typeIdDefinition)
    }

}