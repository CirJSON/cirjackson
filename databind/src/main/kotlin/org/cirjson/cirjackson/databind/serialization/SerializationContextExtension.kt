package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.core.TokenStreamFactory
import org.cirjson.cirjackson.databind.SerializationConfig
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.configuration.GeneratorSettings

open class SerializationContextExtension protected constructor(streamFactory: TokenStreamFactory,
        config: SerializationConfig, generatorConfig: GeneratorSettings, factory: SerializerFactory,
        serializerCache: SerializerCache) :
        SerializerProvider(streamFactory, config, generatorConfig, factory, serializerCache) {

    /*
     *******************************************************************************************************************
     * Construction
     *******************************************************************************************************************
     */

    class Implementation(streamFactory: TokenStreamFactory, config: SerializationConfig,
            generatorConfig: GeneratorSettings, factory: SerializerFactory, serializerCache: SerializerCache) :
            SerializationContextExtension(streamFactory, config, generatorConfig, factory, serializerCache)

}