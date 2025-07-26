package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.core.TokenStreamFactory
import org.cirjson.cirjackson.databind.serialization.SerializerCache
import org.cirjson.cirjackson.databind.serialization.SerializerFactory

abstract class SerializationContexts protected constructor(protected val myStreamFactory: TokenStreamFactory?,
        protected val mySerializerFactory: SerializerFactory?, protected val myCache: SerializerCache?) {

    /*
     *******************************************************************************************************************
     * Vanilla implementation
     *******************************************************************************************************************
     */

    open class DefaultImplementation : SerializationContexts {

        constructor() : super(null, null, null)

    }

}