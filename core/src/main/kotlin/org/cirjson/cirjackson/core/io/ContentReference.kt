package org.cirjson.cirjackson.core.io

import org.cirjson.cirjackson.core.ErrorReportConfiguration

open class ContentReference(val hasTextualContent: Boolean, val rawContent: Any, val contentOffset: Int,
        val contentLength: Int, errorReportConfiguration: ErrorReportConfiguration) {

    protected val maxRawContentLength = errorReportConfiguration.maxRawContentLength

}