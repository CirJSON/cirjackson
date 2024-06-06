package org.cirjson.cirjackson.core

/**
 * Container for configuration values used when handling erroneous token inputs.
 * For example, unquoted text segments.
 *
 * Currently, default settings are:
 * * Maximum length of token to include in error messages (see [maxErrorTokenLength])
 * * Maximum length of raw content to include in error messages (see [maxRawContentLength])
 *
 */
class ErrorReportConfiguration(maxErrorTokenLength: Int, maxRawContentLength: Int) {

    /**
     * Maximum length of token to include in error messages
     *
     * @see Builder.maxErrorTokenLength
     */
    @Suppress("CanBePrimaryConstructorProperty")
    val maxErrorTokenLength = maxErrorTokenLength

    /**
     * Maximum length of raw content to include in error messages
     *
     * @see Builder.maxRawContentLength
     */
    @Suppress("CanBePrimaryConstructorProperty")
    val maxRawContentLength = maxRawContentLength

    class Builder {
    }

    companion object {

        /**
         * Default value for [maxErrorTokenLength].
         */
        private const val DEFAULT_MAX_ERROR_TOKEN_LENGTH = 256

        /**
         * Default value for [maxRawContentLength].
         */
        private const val DEFAULT_MAX_RAW_CONTENT_LENGTH = 256

        private val DEFAULT = ErrorReportConfiguration(DEFAULT_MAX_ERROR_TOKEN_LENGTH, DEFAULT_MAX_RAW_CONTENT_LENGTH)

        private var CURRENT_DEFAULT = DEFAULT

        /**
         * Override the default ErrorReportConfiguration. These defaults are only used when `CirJsonFactory` instances
         * are not configured with their own ErrorReportConfiguration.
         *
         * Library maintainers should not set this as it will affect other code that uses CirJackson. Library
         * maintainers who want to configure ErrorReportConfiguration for the CirJackson usage within their lib should
         * create `ObjectMapper` instances that have a `CirJsonFactory` instance with the required
         * ErrorReportConfiguration.
         *
         * This method is meant for users delivering applications. If they use this, they set it when they start their
         * application to avoid having other code initialize their mappers before the defaults are overridden.
         *
         * @param errorReportConfiguration new default for ErrorReportConfiguration (a null value will reset to built-in
         * default)
         *
         * @see defaults
         * @see builder
         */
        fun overrideDefaultErrorReportConfiguration(errorReportConfiguration: ErrorReportConfiguration?) {
            CURRENT_DEFAULT = errorReportConfiguration ?: DEFAULT
        }

        /**
         * Gives the current default [ErrorReportConfiguration]
         *
         * @return the default [ErrorReportConfiguration] (when none is set on the `CirJsonFactory` explicitly)
         *
         * @see overrideDefaultErrorReportConfiguration
         */
        fun defaults(): ErrorReportConfiguration {
            return CURRENT_DEFAULT
        }

    }

}