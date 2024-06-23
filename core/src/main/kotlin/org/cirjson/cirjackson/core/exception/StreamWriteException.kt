package org.cirjson.cirjackson.core.exception

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator

/**
 * Intermediate base class for all write-side streaming processing problems, mostly content generation issues.
 */
open class StreamWriteException : CirJacksonException {

    private val myProcessor: CirJsonGenerator?

    override val processor: CirJsonGenerator?
        get() = myProcessor

    constructor(generator: CirJsonGenerator, message: String) : super(message) {
        myProcessor = generator
    }

    constructor(generator: CirJsonGenerator, message: String, rootCause: Throwable) : super(message, rootCause) {
        myProcessor = generator
    }

}