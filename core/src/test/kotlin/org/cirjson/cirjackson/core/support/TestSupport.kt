package org.cirjson.cirjackson.core.support

import org.cirjson.cirjackson.core.CirJsonEncoding
import org.cirjson.cirjackson.core.ErrorReportConfiguration
import org.cirjson.cirjackson.core.StreamReadConstraints
import org.cirjson.cirjackson.core.StreamWriteConstraints
import org.cirjson.cirjackson.core.io.ContentReference
import org.cirjson.cirjackson.core.io.IOContext
import org.cirjson.cirjackson.core.util.BufferRecycler

/**
 * Container for various factories needed by (unit) tests.
 */
object TestSupport {

    /**
     * Factory method for creating [IOContext]s for tests
     */
    fun testIOContext(): IOContext {
        return testIOContext(StreamReadConstraints.defaults(), StreamWriteConstraints.defaults(),
                ErrorReportConfiguration.defaults())
    }

    private fun testIOContext(streamReadConstraints: StreamReadConstraints,
            streamWriteConstraints: StreamWriteConstraints,
            errorReportConfiguration: ErrorReportConfiguration): IOContext {
        return IOContext(streamReadConstraints, streamWriteConstraints, errorReportConfiguration, BufferRecycler(),
                ContentReference.unknown(), false, CirJsonEncoding.UTF8)
    }

}