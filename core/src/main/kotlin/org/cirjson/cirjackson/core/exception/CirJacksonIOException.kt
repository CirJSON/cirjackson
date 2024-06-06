package org.cirjson.cirjackson.core.exception

import org.cirjson.cirjackson.core.CirJacksonException
import java.io.IOException

class CirJacksonIOException private constructor(@Transient override val processor: Any?, source: IOException) :
        CirJacksonException(source.message, source) {

    companion object {

        fun construct(e: IOException): CirJacksonIOException {
            return construct(e, null)
        }

        fun construct(e: IOException, processor: Any?): CirJacksonIOException {
            return CirJacksonIOException(processor, e)
        }

    }

}