package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.databind.configuration.DeserializerFactoryConfig

abstract class BasicDeserializerFactory protected constructor(
        protected val myFactoryConfig: DeserializerFactoryConfig) : DeserializerFactory() {
}