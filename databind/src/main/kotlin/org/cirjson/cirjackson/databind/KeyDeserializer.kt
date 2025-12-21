package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.CirJacksonException

/**
 * Abstract class that defines API used for deserializing CirJSON content field names into Map keys. These deserializers
 * are only used if the Map key class is not `String` or `Object`.
 */
abstract class KeyDeserializer {

    /*
     *******************************************************************************************************************
     * Initialization, with former `ResolvableDeserializer`
     *******************************************************************************************************************
     */

    /**
     * Method called after deserializer instance has been constructed (and registered as necessary by provider objects),
     * but before it has returned it to the caller. Called object can then resolve its dependencies to other types,
     * including self-references (direct or indirect).
     *
     * @param context Context to use for accessing configuration, resolving secondary deserializers
     */
    @Throws(CirJacksonException::class)
    open fun resolve(context: DeserializationContext) {
        // No-op
    }

    /*
     *******************************************************************************************************************
     * Main API
     *******************************************************************************************************************
     */

    /**
     * Method called to deserialize a [Map] key from CirJSON property name.
     */
    @Throws(CirJacksonException::class)
    abstract fun deserializeKey(key: String, context: DeserializationContext): Any?

    /**
     * This marker class is only to be used with annotations, to indicate that **no deserializer is configured**.
     *
     * Specifically, this class is to be used as the marker for annotation
     * [org.cirjson.cirjackson.databind.annotation.CirJsonDeserialize].
     */
    abstract class None private constructor() : KeyDeserializer()

}