package org.cirjson.cirjackson.core

/**
 * Container for configuration values used when handling erroneous token inputs.
 * For example, unquoted text segments.
 *
 * Currently, default settings are:
 * * Maximum length of token to include in error messages (see {@link #_maxErrorTokenLength})
 * * Maximum length of raw content to include in error messages (see {@link #_maxRawContentLength})
 */
class ErrorReportConfiguration(val maxErrorTokenLength: Int, val maxRawContentLength: Int) {
}