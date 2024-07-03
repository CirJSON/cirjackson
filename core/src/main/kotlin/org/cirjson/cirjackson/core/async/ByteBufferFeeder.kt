package org.cirjson.cirjackson.core.async

import org.cirjson.cirjackson.core.CirJacksonException
import java.nio.ByteBuffer

/**
 * [NonBlockingInputFeeder] implementation used when feeding data
 * as [ByteBuffer] contents.
 */
interface ByteBufferFeeder : NonBlockingInputFeeder {

    /**
     * Method that can be called to feed more data, if (and only if) [isNeedingMoreInput] returns `true`.
     *
     * @param buffer Buffer that contains additional input to read
     *
     * @throws CirJacksonException if the state is such that this method should not be called (has not yet consumed
     * existing input data, or has been marked as closed)
     */
    @Throws(CirJacksonException::class)
    fun feedInput(buffer: ByteBuffer)

}