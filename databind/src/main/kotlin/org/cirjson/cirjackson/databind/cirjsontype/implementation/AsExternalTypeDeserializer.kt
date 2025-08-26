package org.cirjson.cirjackson.databind.cirjsontype.implementation

import org.cirjson.cirjackson.annotations.CirJsonTypeInfo
import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.cirjsontype.TypeIdResolver

/**
 * Type deserializer used with [CirJsonTypeInfo.As.EXTERNAL_PROPERTY] inclusion mechanism. Actual implementation may
 * look a bit strange since it depends on comprehensive pre-processing done by
 * [org.cirjson.cirjackson.databind.deserialization.bean.BeanDeserializer] to basically transform external type id into
 * structure that looks more like "wrapper-array" style inclusion. This intermediate form is chosen to allow supporting
 * all possible CirJSON structures.
 */
open class AsExternalTypeDeserializer : AsArrayTypeDeserializer {

    constructor(baseType: KotlinType?, idResolver: TypeIdResolver, typePropertyName: String?, typeIdVisible: Boolean,
            defaultImplementation: KotlinType?) : super(baseType, idResolver, typePropertyName, typeIdVisible,
            defaultImplementation)

    constructor(source: AsExternalTypeDeserializer, property: BeanProperty?) : super(source, property)

    override fun forProperty(property: BeanProperty?): TypeDeserializer {
        if (property === myProperty) {
            return this
        }

        return AsExternalTypeDeserializer(this, property)
    }

    override val typeInclusion: CirJsonTypeInfo.As?
        get() = CirJsonTypeInfo.As.EXTERNAL_PROPERTY

    override fun usesExternalId(): Boolean {
        return true
    }

}