package org.cirjson.cirjackson.core.io

import org.cirjson.cirjackson.core.CirJacksonException
import java.io.OutputStream
import java.io.Writer

/**
 * Handler class that can be used to decorate output destinations. Typical use is to use a filter abstraction (filtered
 * output stream, writer) around original output destination, and apply additional processing during write operations.
 */
abstract class OutputDecorator {

    /**
     * Method called by [org.cirjson.cirjackson.core.cirjson.CirJsonFactory] instance when creating generator for given
     * [OutputStream], when this decorator has been registered.
     *
     * @param context IO context in use (provides access to declared encoding)
     *
     * @param output Original output destination
     *
     * @return OutputStream to use; either passed in argument, or something that calls it
     *
     * @throws CirJacksonException if construction of decorated [OutputStream] fails
     */
    @Throws(CirJacksonException::class)
    abstract fun decorate(context: IOContext, output: OutputStream): OutputStream?

    /**
     * Method called by [org.cirjson.cirjackson.core.cirjson.CirJsonFactory] instance when creating generator for given
     * [Writer], when this decorator has been registered.
     *
     * @param context IO context in use (provides access to declared encoding)
     *
     * @param writer Original output writer
     *
     * @return Writer to use; either passed in argument, or something that calls it
     *
     * @throws CirJacksonException if construction of decorated [Writer] fails
     */
    @Throws(CirJacksonException::class)
    abstract fun decorate(context: IOContext, writer: Writer): Writer?

}