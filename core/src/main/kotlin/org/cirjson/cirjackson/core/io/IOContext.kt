package org.cirjson.cirjackson.core.io

import org.cirjson.cirjackson.core.CirJsonEncoding
import org.cirjson.cirjackson.core.ErrorReportConfiguration
import org.cirjson.cirjackson.core.StreamReadConstraints
import org.cirjson.cirjackson.core.StreamWriteConstraints
import org.cirjson.cirjackson.core.util.BufferRecycler

/**
 * To limit number of configuration and state objects to pass, all contextual objects that need to be passed by the
 * factory to readers and writers are combined under this object. One instance is created for each reader and writer.
 *
 * @constructor Main constructor to use.
 *
 * @param streamReadConstraints Constraints for streaming reads
 * @param streamWriteConstraints Constraints for streaming writes
 * @param errorReportConfiguration Configuration for error reporting
 * @param bufferRecycler BufferRecycler to use, if any (`null` if none)
 * @param contentReference Input source reference for location reporting
 * @param isResourceManaged Whether input source is managed (owned) by Jackson library
 * @param encoding Encoding in use
 */
class IOContext(val streamReadConstraints: StreamReadConstraints, val streamWriteConstraints: StreamWriteConstraints,
        val errorReportConfiguration: ErrorReportConfiguration, val bufferRecycler: BufferRecycler?,
        val contentReference: ContentReference?, val isResourceManaged: Boolean, val encoding: CirJsonEncoding?) :
        AutoCloseable {

    /**
     * Flag that indicates whether this context instance should release configured `bufferRecycler` or not: if it does,
     * it needs to call (via [BufferRecycler.releaseToPool] when closed; if not, should do nothing (recycler life-cycle
     * is externally managed)
     */
    private var releaseRecycler = true

    override fun close() {
        TODO("Not yet implemented")
    }

    /**
     * Method to call to prevent [bufferRecycler] release upon [close]: called when [bufferRecycler] life-cycle is
     * externally managed.
     */
    fun markBufferRecyclerReleased(): IOContext {
        releaseRecycler = false
        return this
    }

}