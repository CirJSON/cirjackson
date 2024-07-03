package org.cirjson.cirjackson.core.async

/**
 * Interface used by non-blocking [org.cirjson.cirjackson.core.CirJsonParser] implementations to feed input to parse.
 * Feeder is used by entity that feeds content to parse; at any given point only one chunk of content can be processed
 * so caller has to take care to only feed more content when existing content has been parsed (which occurs when
 * parser's `nextToken` is called). Once application using non-blocking parser has no more data to feed it should call
 * [endOfInput] to indicate end of logical input (stream) to parse.
 */
interface NonBlockingInputFeeder {

    /**
     * Accessor called to check whether it is ok to feed more data: parser returns `true` if it has no more content to
     * parse (and it is ok to feed more); otherwise `false` (and no data should yet be fed).
     */
    val isNeedingMoreInput: Boolean

    /**
     * Method that should be called after last chunk of data to parse has been fed (with `feedInput` in subclass); can
     * be called regardless of what [isNeedingMoreInput] returns. After calling this method, no more data can be fed;
     * and parser assumes no more data will be available.
     */
    fun endOfInput()

}