package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.PropertyFilter
import org.cirjson.cirjackson.databind.serialization.standard.StandardContainerSerializer

@CirJacksonStandardImplementation
open class MapSerializer : StandardContainerSerializer<Map<*, *>> {

    /*
     *******************************************************************************************************************
     * Construction
     *******************************************************************************************************************
     */

    protected constructor(source: MapSerializer, filterId: Any?, sortKeys: Boolean) : super(source) {
    }

    override fun withValueTypeSerializerImplementation(valueTypeSerializer: TypeSerializer): MapSerializer {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    override val contentType: KotlinType
        get() = TODO("Not yet implemented")

    override val contentSerializer: ValueSerializer<*>?
        get() = TODO("Not yet implemented")

    override fun isEmpty(provider: SerializerProvider, value: Map<*, *>?): Boolean {
        TODO("Not yet implemented")
    }

    override fun hasSingleElement(value: Map<*, *>): Boolean {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * ValueSerializer implementation
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun serialize(value: Map<*, *>, generator: CirJsonGenerator, serializers: SerializerProvider) {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Secondary serialization methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    open fun serializeWithoutTypeInfo(value: Map<*, *>, generator: CirJsonGenerator, context: SerializerProvider) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected open fun serializeFilteredAnyProperties(context: SerializerProvider, generator: CirJsonGenerator,
            bean: Any, value: Map<*, *>, filter: PropertyFilter, suppressableValue: Any?) {
        TODO("Not yet implemented")
    }

    /**
     * Internal access to [serializeFilteredAnyProperties] for `AnyGetterWriter`.
     */
    @Throws(CirJacksonException::class)
    internal fun serializeFilteredAnyPropertiesInternal(context: SerializerProvider, generator: CirJsonGenerator,
            bean: Any, value: Map<*, *>, filter: PropertyFilter, suppressableValue: Any?) {
        serializeFilteredAnyProperties(context, generator, bean, value, filter, suppressableValue)
    }

}