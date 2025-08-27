package org.cirjson.cirjackson.databind.cirjsontype.implementation

import org.cirjson.cirjackson.annotations.CirJsonTypeInfo
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsontype.TypeIdResolver

/**
 * Type wrapper that tries to use an extra CirJSON Object, with a single entry that has type name as key, to serialize
 * type information. If this is not possible (value is serialize as array or primitive), will use
 * [CirJsonTypeInfo.As.WRAPPER_ARRAY] mechanism as fallback: that is, just use a wrapping array with a dummy object id
 * as the first element, type information as the second element and value as third.
 */
open class AsWrapperTypeSerializer(idResolver: TypeIdResolver, property: BeanProperty?) :
        TypeSerializerBase(idResolver, property) {

    override fun forProperty(context: SerializerProvider, property: BeanProperty?): AsWrapperTypeSerializer {
        if (property === myProperty) {
            return this
        }

        return AsWrapperTypeSerializer(myIdResolver, property)
    }

    override val typeInclusion: CirJsonTypeInfo.As
        get() = CirJsonTypeInfo.As.WRAPPER_OBJECT

    /*
     *******************************************************************************************************************
     * Internal helper methods
     *******************************************************************************************************************
     */

    /**
     * Helper method used to ensure that intended type id is output as something that is valid:
     * currently only used to ensure that `null` output is converted to an empty String.
     */
    protected open fun validTypeId(typeId: String?): String {
        return typeId ?: ""
    }

    @Throws(CirJacksonException::class)
    protected fun writeTypeId(generator: CirJsonGenerator, typeId: String?) {
        if (typeId != null) {
            generator.writeTypeId(typeId)
        }
    }

}