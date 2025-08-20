package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.databind.configuration.DeserializerFactoryConfig

abstract class BasicDeserializerFactory protected constructor(
        protected val myFactoryConfig: DeserializerFactoryConfig) : DeserializerFactory() {

    /*
     *******************************************************************************************************************
     * Configuration handling: fluent factories
     *******************************************************************************************************************
     */

    override fun withAdditionalDeserializers(additional: Deserializers): DeserializerFactory {
        TODO("Not yet implemented")
    }

    override fun withAdditionalKeyDeserializers(additional: KeyDeserializers): DeserializerFactory {
        TODO("Not yet implemented")
    }

    override fun withDeserializerModifier(modifier: ValueDeserializerModifier): DeserializerFactory {
        TODO("Not yet implemented")
    }

    override fun withValueInstantiators(instantiators: ValueInstantiators): DeserializerFactory {
        TODO("Not yet implemented")
    }

}