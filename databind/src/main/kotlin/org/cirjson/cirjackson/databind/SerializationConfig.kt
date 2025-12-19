package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.FormatFeature
import org.cirjson.cirjackson.databind.cirjsontype.SubtypeResolver
import org.cirjson.cirjackson.databind.configuration.*
import org.cirjson.cirjackson.databind.introspection.ClassIntrospector
import org.cirjson.cirjackson.databind.introspection.MixInHandler
import org.cirjson.cirjackson.databind.serialization.FilterProvider
import org.cirjson.cirjackson.databind.type.TypeFactory
import org.cirjson.cirjackson.databind.util.RootNameLookup
import kotlin.reflect.KClass

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
            filterProvider: FilterProvider?) : super(builder, mapperFeatures, typeFactory, classIntrospector, mixins,
            subtypeResolver, configOverrides, defaultAttributes, rootNames) {
    }

    /*
     *******************************************************************************************************************
     * Lifecycle, factory methods from MapperConfigBase
     *******************************************************************************************************************
     */

    override fun withBase(newBase: BaseSettings): SerializationConfig {
        TODO("Not yet implemented")
    }

    override fun with(datatypeFeatures: DatatypeFeatures): SerializationConfig {
        TODO("Not yet implemented")
    }

    override fun withRootName(rootName: PropertyName?): SerializationConfig {
        TODO("Not yet implemented")
    }

    override fun withView(view: KClass<*>?): SerializationConfig {
        TODO("Not yet implemented")
    }

    override fun with(attributes: ContextAttributes): SerializationConfig {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Configuration: other
     *******************************************************************************************************************
     */

    override fun useRootWrapping(): Boolean {
        TODO("Not yet implemented")
    }

    fun isEnabled(feature: SerializationFeature): Boolean {
        TODO("Not yet implemented")
    }

    fun hasFormatFeature(feature: FormatFeature): Boolean {
        TODO("Not yet implemented")
    }

}