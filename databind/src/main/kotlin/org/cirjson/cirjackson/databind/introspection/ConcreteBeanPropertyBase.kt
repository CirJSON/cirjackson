package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.annotations.CirJsonInclude
import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.PropertyMetadata
import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import kotlin.reflect.KClass

abstract class ConcreteBeanPropertyBase protected constructor(metadata: PropertyMetadata?) : BeanProperty {

    override val metadata = metadata ?: PropertyMetadata.STANDARD_REQUIRED_OR_OPTIONAL

    constructor(source: ConcreteBeanPropertyBase) : this(source.metadata)

    override val isRequired: Boolean
        get() = TODO("Not yet implemented")

    override val isVirtual: Boolean
        get() = TODO("Not yet implemented")

    override fun findFormatOverrides(config: MapperConfig<*>): CirJsonFormat.Value? {
        TODO("Not yet implemented")
    }

    override fun findPropertyFormat(config: MapperConfig<*>, baseType: KClass<*>): CirJsonFormat.Value {
        TODO("Not yet implemented")
    }

    override fun findPropertyInclusion(config: MapperConfig<*>, baseType: KClass<*>): CirJsonInclude.Value? {
        TODO("Not yet implemented")
    }

    override fun findAliases(config: MapperConfig<*>): List<PropertyName> {
        TODO("Not yet implemented")
    }

}