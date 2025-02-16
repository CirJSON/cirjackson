package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonLocation
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.exception.CirJacksonIOException
import java.io.Closeable

/**
 * Exception used to signal fatal problems with mapping of content, distinct from low-level I/O problems (signaled using
 * simple [CirJacksonIOException]s) or data encoding/decoding problems (signaled with
 * [org.cirjson.cirjackson.core.exception.StreamReadException],
 * [org.cirjson.cirjackson.core.exception.StreamWriteException]).
 *
 * One additional feature is the ability to denote relevant path of references (during serialization/deserialization) to
 * help in troubleshooting.
 */
open class DatabindException : CirJacksonException {

    protected constructor(processor: Closeable?, message: String) : super(processor, message)

    protected constructor(processor: Closeable?, message: String, cause: Throwable) : super(processor, message, cause)

    protected constructor(processor: Closeable?, message: String, location: CirJsonLocation?) : super(processor,
            message, location)

    protected constructor(message: String, location: CirJsonLocation?, cause: Throwable) : super(message, location,
            cause)

    protected constructor(message: String) : super(message)

    companion object {

        fun from(parser: CirJsonParser, message: String): DatabindException {
            return DatabindException(parser, message)
        }

        fun from(parser: CirJsonParser, message: String, cause: Throwable): DatabindException {
            return DatabindException(parser, message, cause)
        }

        fun from(generator: CirJsonGenerator, message: String): DatabindException {
            return DatabindException(generator, message)
        }

        fun from(generator: CirJsonGenerator, message: String, cause: Throwable): DatabindException {
            return DatabindException(generator, message, cause)
        }

        fun from(context: DeserializationContext?, message: String): DatabindException {
            return DatabindException(parser(context), message)
        }

        private fun parser(context: DeserializationContext?): CirJsonParser? {
            return context?.parser
        }

        fun from(provider: SerializerProvider?, message: String): DatabindException {
            return DatabindException(generator(provider), message)
        }

        fun from(provider: SerializerProvider?, message: String, cause: Throwable): DatabindException {
            return DatabindException(generator(provider), message, cause)
        }

        private fun generator(provider: SerializerProvider?): CirJsonGenerator? {
            return provider?.generator
        }

    }

}