package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.annotations.ObjectIdGenerator
import org.cirjson.cirjackson.annotations.ObjectIdResolver
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.FormatSchema
import org.cirjson.cirjackson.core.TokenStreamFactory
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.introspection.Annotated

abstract class DeserializationContextExtended protected constructor(tokenStreamFactory: TokenStreamFactory,
        deserializerFactory: DeserializerFactory, cache: DeserializerCache, config: DeserializationConfig,
        schema: FormatSchema?, values: InjectableValues?) :
        DeserializationContext(tokenStreamFactory, deserializerFactory, cache, config, schema, values) {

    open fun assignParser(parser: CirJsonParser): DeserializationContextExtended {
        TODO("Not yet implemented")
    }

    open fun assignAndReturnParser(parser: CirJsonParser): CirJsonParser {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Abstract methods implementations, Object ID
     *******************************************************************************************************************
     */

    override fun findObjectId(id: Any, generator: ObjectIdGenerator<*>, resolver: ObjectIdResolver): ReadableObjectId {
        TODO("Not yet implemented")
    }

    override fun checkUnresolvedObjectId() {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Abstract methods implementations, other factory methods
     *******************************************************************************************************************
     */

    override fun deserializerInstance(annotated: Annotated, deserializerDefinition: Any?): ValueDeserializer<Any>? {
        TODO("Not yet implemented")
    }

    override fun keyDeserializerInstance(annotated: Annotated, deserializerDefinition: Any?): KeyDeserializer? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Extended API, read methods
     *******************************************************************************************************************
     */

    open fun readRootValue(parser: CirJsonParser, valueType: KotlinType, deserializer: ValueDeserializer<Any>,
            valueToUpdate: Any?): Any? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Concrete implementation class
     *******************************************************************************************************************
     */

    class Implementation(tokenStreamFactory: TokenStreamFactory, deserializerFactory: DeserializerFactory,
            cache: DeserializerCache, config: DeserializationConfig, schema: FormatSchema?, values: InjectableValues?) :
            DeserializationContextExtended(tokenStreamFactory, deserializerFactory, cache, config, schema, values)

}