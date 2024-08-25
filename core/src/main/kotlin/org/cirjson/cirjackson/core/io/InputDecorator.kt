package org.cirjson.cirjackson.core.io

import org.cirjson.cirjackson.core.CirJacksonException
import java.io.*

/**
 * Handler class that can be used to decorate input sources. Typical use is to use a filter abstraction (filtered
 * stream, reader) around original input source, and apply additional processing during read operations.
 */
abstract class InputDecorator {

    /**
     * Method called by [org.cirjson.cirjackson.core.cirjson.CirJsonFactory] instance when creating parser on given
     * "raw" byte source. Method can either construct a [InputStream] for reading; or return null to indicate that no
     * wrapping should occur.
     *
     * @param context IO context in use (provides access to declared encoding). NOTE: at this point context may not have
     * all information initialized; specifically auto-detected encoding is only available once parsing starts, which may
     * occur only after this method is called.
     *
     * @param data Input buffer that contains contents to parse
     *
     * @param offset Offset of the first available byte in the input buffer
     *
     * @param length Number of bytes available in the input buffer
     *
     * @return Either [InputStream] to use as input source; or `null` to indicate that contents are to be processed
     * as-is by caller
     *
     * @throws CirJacksonException if construction of [InputStream] fails
     */
    @Throws(CirJacksonException::class)
    abstract fun decorate(context: IOContext, data: ByteArray?, offset: Int, length: Int): InputStream?

    /**
     * Method called by [org.cirjson.cirjackson.core.cirjson.CirJsonFactory] instance when creating parser given an
     * [DataInput], when this decorator has been registered.
     *
     * Default implementation simply throws [UnsupportedOperationException]
     *
     * @param context IO context in use (provides access to declared encoding). NOTE: at this point context may not have
     * all information initialized; specifically auto-detected encoding is only available once parsing starts, which may
     * occur only after this method is called.
     *
     * @param input Original input source
     *
     * @return DataInput to use; either 'input' as is, or decorator version that typically delegates to 'input'
     *
     * @throws CirJacksonException if construction of [DataInput] fails
     */
    @Throws(CirJacksonException::class)
    open fun decorate(context: IOContext, input: DataInput): DataInput? {
        throw UnsupportedOperationException()
    }

    /**
     * Method called by [org.cirjson.cirjackson.core.cirjson.CirJsonFactory] instance when
     * creating parser given an [InputStream], when this decorator
     * has been registered.
     *
     * @param context IO context in use (provides access to declared encoding). NOTE: at this point context may not have
     * all information initialized; specifically auto-detected encoding is only available once parsing starts, which may
     * occur only after this method is called.
     *
     * @param input Original input source
     *
     * @return InputStream to use; either 'input' as is, or decorator version that typically delegates to 'input'
     *
     * @throws CirJacksonException if construction of [InputStream] fails
     */
    @Throws(CirJacksonException::class)
    abstract fun decorate(context: IOContext, input: InputStream): InputStream?

    /**
     * Method called by [org.cirjson.cirjackson.core.cirjson.CirJsonFactory] instance when
     * creating parser given an [Reader], when this decorator
     * has been registered.
     *
     * @param context IO context in use (provides access to declared encoding). NOTE: at this point context may not have
     * all information initialized; specifically auto-detected encoding is only available once parsing starts, which may
     * occur only after this method is called.
     *
     * @param reader Original reader
     *
     * @return Reader to use; either passed in argument, or something that calls it (for example, a [FilterReader])
     *
     * @throws CirJacksonException if construction of [Reader] fails
     */
    @Throws(CirJacksonException::class)
    abstract fun decorate(context: IOContext, reader: Reader): Reader?

}