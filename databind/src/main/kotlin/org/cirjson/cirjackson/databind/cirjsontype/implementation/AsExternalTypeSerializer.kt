package org.cirjson.cirjackson.databind.cirjsontype.implementation

import org.cirjson.cirjackson.annotations.CirJsonTypeInfo
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsontype.TypeIdResolver

/**
 * Type serializer that preferably embeds type information as an "external" type property; embedded in enclosing CirJSON
 * object. Note that this serializer should only be used when value is being output at CirJSON Object context; otherwise
 * it cannot work reliably, and will have to revert operation similar to [AsPropertyTypeSerializer].
 *
 * Note that implementation of serialization is a bit cumbersome as we must serialize external type id AFTER object;
 * this is because callback only occurs after field name has been written.
 *
 * Also note that this type of type id inclusion will NOT try to make use of native Type Ids, even if those exist.
 */
open class AsExternalTypeSerializer(idResolver: TypeIdResolver, property: BeanProperty?,
        protected val myTypePropertyName: String) : TypeSerializerBase(idResolver, property) {

    override fun forProperty(context: SerializerProvider, property: BeanProperty?): AsExternalTypeSerializer {
        if (property === myProperty) {
            return this
        }

        return AsExternalTypeSerializer(myIdResolver, property, myTypePropertyName)
    }

    override val propertyName: String?
        get() = myTypePropertyName

    override val typeInclusion: CirJsonTypeInfo.As
        get() = CirJsonTypeInfo.As.EXTERNAL_PROPERTY

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun writeScalarPrefix(value: Any, generator: CirJsonGenerator) {
        // No-op
    }

    @Throws(CirJacksonException::class)
    protected fun writeObjectPrefix(value: Any, generator: CirJsonGenerator) {
        generator.writeStartObject()
        generator.writeObjectId(Any())
    }

    @Throws(CirJacksonException::class)
    protected fun writeArrayPrefix(value: Any, generator: CirJsonGenerator) {
        generator.writeStartArray()
        generator.writeArrayId(Any())
    }

    @Throws(CirJacksonException::class)
    protected fun writeScalarSuffix(value: Any, generator: CirJsonGenerator, typeId: String?) {
        if (typeId != null) {
            generator.writeStringProperty(myTypePropertyName, typeId)
        }
    }

    @Throws(CirJacksonException::class)
    protected fun writeObjectSuffix(value: Any, generator: CirJsonGenerator, typeId: String?) {
        generator.writeEndObject()

        if (typeId != null) {
            generator.writeStringProperty(myTypePropertyName, typeId)
        }
    }

    @Throws(CirJacksonException::class)
    protected fun writeArraySuffix(value: Any, generator: CirJsonGenerator, typeId: String?) {
        generator.writeEndArray()

        if (typeId != null) {
            generator.writeStringProperty(myTypePropertyName, typeId)
        }
    }

}