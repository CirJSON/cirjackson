package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.databind.cirjsontype.SubtypeResolver
import org.cirjson.cirjackson.databind.configuration.ConfigOverrides
import org.cirjson.cirjackson.databind.configuration.ContextAttributes
import org.cirjson.cirjackson.databind.configuration.MapperBuilder
import org.cirjson.cirjackson.databind.configuration.MapperConfigBase
import org.cirjson.cirjackson.databind.introspection.ClassIntrospector
import org.cirjson.cirjackson.databind.introspection.MixInHandler
import org.cirjson.cirjackson.databind.serialization.FilterProvider
import org.cirjson.cirjackson.databind.type.TypeFactory
import org.cirjson.cirjackson.databind.util.RootNameLookup

class SerializationConfig : MapperConfigBase<SerializationFeature, SerializationConfig> {

    /*
     *******************************************************************************************************************
     * Lifecycle, primary constructors for new instances
     *******************************************************************************************************************
     */

    constructor(builder: MapperBuilder<*, *>, mapperFeatures: Long, serializationFeatures: Int,
            streamWriteFeatures: Int, formatWriteFeatures: Int, configOverrides: ConfigOverrides,
            typeFactory: TypeFactory, classIntrospector: ClassIntrospector, mixins: MixInHandler,
            subtypeResolver: SubtypeResolver, defaultAttributes: ContextAttributes, rootNames: RootNameLookup,
            filterProvider: FilterProvider) : super(builder, mapperFeatures, typeFactory, classIntrospector, mixins,
            subtypeResolver, configOverrides, defaultAttributes, rootNames) {
    }

    /*
     *******************************************************************************************************************
     * Configuration: other
     *******************************************************************************************************************
     */

    fun isEnabled(feature: SerializationFeature): Boolean {
        TODO("Not yet implemented")
    }

}