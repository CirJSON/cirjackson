package org.cirjson.cirjackson.databind.cirjsontype.implementation

import org.cirjson.cirjackson.annotations.CirJsonTypeInfo
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.type.WritableTypeID
import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.DatabindContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsontype.TypeIdResolver
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import kotlin.reflect.KClass

open class AsDeductionTypeSerializer protected constructor() : TypeSerializerBase(NoOpTypeIdResolver(), null) {

    override fun forProperty(context: SerializerProvider, property: BeanProperty?): TypeSerializer {
        return this
    }

    override val typeInclusion: CirJsonTypeInfo.As
        get() = CirJsonTypeInfo.As.EXISTING_PROPERTY

    @Throws(CirJacksonException::class)
    override fun writeTypePrefix(generator: CirJsonGenerator, context: SerializerProvider,
            typeID: WritableTypeID): WritableTypeID? {
        if (!typeID.valueShape.isStructStart) {
            return null
        }

        if (!generator.isAbleWriteTypeId) {
            return generator.writeTypePrefix(typeID)
        }

        typeID.isWrapperWritten = false

        if (typeID.valueShape == CirJsonToken.START_OBJECT) {
            generator.writeStartObject(typeID.forValue)
            generator.writeObjectId(Any())
        } else if (typeID.valueShape == CirJsonToken.START_ARRAY) {
            generator.writeStartArray(typeID.forValue)
            generator.writeArrayId(Any())
        }

        return typeID
    }

    @Throws(CirJacksonException::class)
    override fun writeTypeSuffix(generator: CirJsonGenerator, context: SerializerProvider,
            typeID: WritableTypeID?): WritableTypeID? {
        typeID ?: return null
        return generator.writeTypeSuffix(typeID)
    }

    protected class NoOpTypeIdResolver : TypeIdResolver {

        override fun init(type: KotlinType) {
            throw UnsupportedOperationException()
        }

        override fun idFromValue(context: DatabindContext, value: Any?): String {
            throw UnsupportedOperationException()
        }

        override fun idFromValueAndType(context: DatabindContext, value: Any?, suggestedType: KClass<*>?): String {
            throw UnsupportedOperationException()
        }

        override fun idFromBaseType(context: DatabindContext): String {
            throw UnsupportedOperationException()
        }

        override fun typeFromId(context: DatabindContext, id: String): KotlinType {
            throw UnsupportedOperationException()
        }

        override val descriptionForKnownTypeIds: String
            get() = throw UnsupportedOperationException()

        override val mechanism: CirJsonTypeInfo.Id
            get() = throw UnsupportedOperationException()

    }

    companion object {

        val INSTANCE = AsDeductionTypeSerializer()

    }

}