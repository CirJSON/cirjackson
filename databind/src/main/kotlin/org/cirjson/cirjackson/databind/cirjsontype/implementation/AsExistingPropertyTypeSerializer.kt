package org.cirjson.cirjackson.databind.cirjsontype.implementation

import org.cirjson.cirjackson.annotations.CirJsonTypeInfo
import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsontype.TypeIdResolver

/**
 * Type serializer used with [CirJsonTypeInfo.As.EXISTING_PROPERTY] inclusion mechanism. Expects type information to be
 * a well-defined property on all subclasses. Inclusion of type information otherwise follows behavior of
 * [CirJsonTypeInfo.As.PROPERTY].
 */
open class AsExistingPropertyTypeSerializer(idResolver: TypeIdResolver, property: BeanProperty?,
        protected val myTypePropertyName: String) : TypeSerializerBase(idResolver, property) {

    override fun forProperty(context: SerializerProvider, property: BeanProperty?): AsExistingPropertyTypeSerializer {
        if (property === myProperty) {
            return this
        }

        return AsExistingPropertyTypeSerializer(myIdResolver, property, myTypePropertyName)
    }

    override val typeInclusion: CirJsonTypeInfo.As
        get() = CirJsonTypeInfo.As.EXISTING_PROPERTY

}