package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.core.FormatSchema
import org.cirjson.cirjackson.core.TokenStreamFactory
import org.cirjson.cirjackson.databind.DeserializationConfig
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.InjectableValues

abstract class DeserializationContextExtended protected constructor(tokenStreamFactory: TokenStreamFactory,
        deserializerFactory: DeserializerFactory, cache: DeserializerCache, config: DeserializationConfig,
        schema: FormatSchema?, values: InjectableValues?) :
        DeserializationContext(tokenStreamFactory, deserializerFactory, cache, config, schema, values) {

    /*
     *******************************************************************************************************************
     * Concrete implementation class
     *******************************************************************************************************************
     */

    class Implementation(tokenStreamFactory: TokenStreamFactory, deserializerFactory: DeserializerFactory,
            cache: DeserializerCache, config: DeserializationConfig, schema: FormatSchema?, values: InjectableValues?) :
            DeserializationContextExtended(tokenStreamFactory, deserializerFactory, cache, config, schema, values)

}