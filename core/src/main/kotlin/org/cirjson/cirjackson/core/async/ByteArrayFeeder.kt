package org.cirjson.cirjackson.core.async

import org.cirjson.cirjackson.core.CirJacksonException

/**
 * [NonBlockingInputFeeder] implementation used when feeding data as Byte arrays.
 */
interface ByteArrayFeeder : NonBlockingInputFeeder {

    /**
     * Method that can be called to feed more data, if (and only if) [isNeedingMoreInput] returns `true`.
     *
     * @param data Byte array that contains data to feed: caller must ensure data remains stable until it is fully
     * processed (which is true when [isNeedingMoreInput] returns `true`)
     *
     * @param offset Offset within array where input data to process starts
     *
     * @param end Offset after last byte contained in the input array
     *
     * @throws CirJacksonException if the state is such that this method should not be called (has not yet consumed
     * existing input data, or has been marked as closed)
     */
    @Throws(CirJacksonException::class)
    fun feedInput(data: ByteArray, offset: Int, end: Int)

}