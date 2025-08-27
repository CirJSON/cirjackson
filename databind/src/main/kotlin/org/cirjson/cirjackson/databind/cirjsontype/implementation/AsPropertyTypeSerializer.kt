package org.cirjson.cirjackson.databind.cirjsontype.implementation

import org.cirjson.cirjackson.annotations.CirJsonTypeInfo
import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsontype.TypeIdResolver

/**
 * Type serializer that preferably embeds type information as an additional CirJSON Object property, if possible (when
 * resulting serialization would use CirJSON Object). If this is not possible (for CirJSON Arrays, scalars), uses a
 * CirJSON Array wrapper (similar to how [CirJsonTypeInfo.As.WRAPPER_ARRAY] always works) as a fallback.
 */
open class AsPropertyTypeSerializer(idResolver: TypeIdResolver, property: BeanProperty?,
        protected val myTypePropertyName: String) : TypeSerializerBase(idResolver, property) {

    override fun forProperty(context: SerializerProvider, property: BeanProperty?): AsPropertyTypeSerializer {
        if (property === myProperty) {
            return this
        }

        return AsPropertyTypeSerializer(myIdResolver, property, myTypePropertyName)
    }

    override val propertyName: String?
        get() = myTypePropertyName

    override val typeInclusion: CirJsonTypeInfo.As
        get() = CirJsonTypeInfo.As.PROPERTY

}