package org.cirjson.cirjackson.databind.cirjsontype.implementation

import org.cirjson.cirjackson.annotations.CirJsonTypeInfo
import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsontype.TypeIdResolver

/**
 * Type serializer that will embed type information in an array, as the second element, preceded by a dummy object id,
 * and actual value as the third element.
 */
open class AsArrayTypeSerializer(idResolver: TypeIdResolver, property: BeanProperty?) :
        TypeSerializerBase(idResolver, property) {

    override fun forProperty(context: SerializerProvider, property: BeanProperty?): AsArrayTypeSerializer {
        if (property === myProperty) {
            return this
        }

        return AsArrayTypeSerializer(myIdResolver, property)
    }

    override val typeInclusion: CirJsonTypeInfo.As
        get() = CirJsonTypeInfo.As.WRAPPER_ARRAY

}