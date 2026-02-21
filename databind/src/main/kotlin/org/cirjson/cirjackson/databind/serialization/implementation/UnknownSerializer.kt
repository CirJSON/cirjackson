package org.cirjson.cirjackson.databind.serialization.implementation

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.SerializationFeature
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.standard.ToEmptyObjectSerializer
import org.cirjson.cirjackson.databind.util.NativeImageUtil
import kotlin.reflect.KClass

open class UnknownSerializer : ToEmptyObjectSerializer {

    constructor() : super(Any::class)

    constructor(clazz: KClass<*>) : super(clazz)

    @Throws(CirJacksonException::class)
    override fun serialize(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider) {
        if (serializers.isEnabled(SerializationFeature.FAIL_ON_EMPTY_BEANS)) {
            failForEmpty(serializers, value)
        }

        super.serialize(value, generator, serializers)
    }

    @Throws(CirJacksonException::class)
    override fun serializeWithType(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        if (serializers.isEnabled(SerializationFeature.FAIL_ON_EMPTY_BEANS)) {
            failForEmpty(serializers, value)
        }

        super.serializeWithType(value, generator, serializers, typeSerializer)
    }

    protected fun failForEmpty(provider: SerializerProvider, value: Any) {
        val clazz = value::class
        val handledType = handledType()!!

        return if (NativeImageUtil.needsReflectionConfiguration(clazz)) {
            provider.reportBadDefinition(handledType,
                    "No serializer found for class ${clazz.qualifiedName} and no properties discovered to create BeanSerializer (to avoid exception, disable SerializationFeature.FAIL_ON_EMPTY_BEANS). This appears to be a native image, in which case you may need to configure reflection for the class that is to be serialized")
        } else {
            provider.reportBadDefinition(handledType,
                    "No serializer found for class ${clazz.qualifiedName} and no properties discovered to create BeanSerializer (to avoid exception, disable SerializationFeature.FAIL_ON_EMPTY_BEANS)")
        }
    }

}