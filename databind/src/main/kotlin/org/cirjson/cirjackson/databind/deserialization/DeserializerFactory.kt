package org.cirjson.cirjackson.databind.deserialization

abstract class DeserializerFactory {

    /*
     *******************************************************************************************************************
     * Mutant factories for registering additional configuration
     *******************************************************************************************************************
     */

    abstract fun withAdditionalDeserializers(additional: Deserializers): DeserializerFactory

    abstract fun withAdditionalKeyDeserializers(additional: KeyDeserializers): DeserializerFactory

    abstract fun withDeserializerModifier(modifier: ValueDeserializerModifier): DeserializerFactory

    abstract fun withValueInstantiators(instantiators: ValueInstantiators): DeserializerFactory

}