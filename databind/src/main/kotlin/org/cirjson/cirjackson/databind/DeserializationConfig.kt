package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.databind.cirjsontype.SubtypeResolver
import org.cirjson.cirjackson.databind.configuration.*
import org.cirjson.cirjackson.databind.introspection.ClassIntrospector
import org.cirjson.cirjackson.databind.introspection.MixInHandler
import org.cirjson.cirjackson.databind.type.TypeFactory
import org.cirjson.cirjackson.databind.util.RootNameLookup

class DeserializationConfig : MapperConfigBase<DeserializationFeature, DeserializationConfig> {

    /*
     *******************************************************************************************************************
     * Lifecycle, primary constructors for new instances
     *******************************************************************************************************************
     */

    constructor(builder: MapperBuilder<*, *>, mapperFeatures: Long, deserializationFeatures: Int,
            streamReadFeatures: Int, formatReadFeatures: Int, configOverrides: ConfigOverrides,
            coercionConfigs: CoercionConfigs, typeFactory: TypeFactory, classIntrospector: ClassIntrospector,
            mixins: MixInHandler, subtypeResolver: SubtypeResolver, defaultAttributes: ContextAttributes,
            rootNames: RootNameLookup, abstractTypeResolvers: Array<AbstractTypeResolver>) : super(builder,
            mapperFeatures, typeFactory, classIntrospector, mixins, subtypeResolver, configOverrides, defaultAttributes,
            rootNames) {
    }

    /*
     *******************************************************************************************************************
     * MapperConfig implementation/overrides: other
     *******************************************************************************************************************
     */

    override fun useRootWrapping(): Boolean {
        TODO("Not yet implemented")
    }

    fun isEnabled(feature: DeserializationFeature): Boolean {
        TODO("Not yet implemented")
    }

}