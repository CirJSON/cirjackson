package org.cirjson.cirjackson.core

/**
 * Container for commonly used Base64 variants:
 * * [MIME]
 * * [MIME_NO_LINEFEEDS]
 * * [PEM]
 * * [MODIFIED_FOR_URL]
 *
 * See entries for full description of differences.
 *
 * Note that for default [Base64Variant] instances listed above, configuration is such that if padding is written
 * on output, it will also be required on reading. This behavior may be changed by using methods:
 * * [Base64Variant.withPaddingAllowed]
 * * [Base64Variant.withPaddingForbidden]
 * * [Base64Variant.withPaddingRequired]
 * * [Base64Variant.withWritePadding]
 */
object Base64Variants {

    private const val STD_BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    /**
     * This variant is what most people would think of "the standard" Base64 encoding.
     *
     * See [wikipedia Base64 entry](https://en.wikipedia.org/wiki/Base64) for details.
     *
     * Note that although this can be thought of as the standard variant, it is **not** the default for CirJackson:
     * no-linefeeds alternative is instead used because of CirJSON requirement of escaping all linefeeds.
     *
     * Writes padding on output; requires padding when reading (may change later with a call to
     * [Base64Variant.withWritePadding])
     */
    val MIME = Base64Variant("MIME", STD_BASE64_ALPHABET, true, '=', 76)

    /**
     * Slightly non-standard modification of [MIME] which does not use linefeeds (max line length set to infinite).
     * Useful when linefeeds wouldn't work well (possibly in attributes), or for minor space savings (save 1 linefeed
     * per 76 data chars, i.e. ~1.4% savings).
     *
     * Writes padding on output; requires padding when reading (may change later with a call to
     * [Base64Variant.withWritePadding])
     */
    val MIME_NO_LINEFEEDS = Base64Variant(MIME, "MIME-NO-LINEFEEDS", Int.MAX_VALUE)

    /**
     * This variant is the one that predates [MIME]: it is otherwise identical, except that it mandates shorter line
     * length.
     *
     * Writes padding on output; requires padding when reading (may change later with a call to
     * [Base64Variant.withWritePadding])
     */
    val PEM = Base64Variant(MIME, "PEM", true, '=', 64)

    /**
     * This non-standard variant is usually used when encoded data needs to be passed via URLs (such as part of GET
     * request). It differs from the base [MIME] variant in multiple ways. First, no padding is used: this also means
     * that it generally can not be written in multiple separate but adjacent chunks (which would not be the usual use
     * case in any case). Also, no linefeeds are used (max line length set to infinite). And finally, two characters
     * (plus and slash) that would need quoting in URLs are replaced with more optimal alternatives (hyphen and
     * underscore, respectively).
     *
     * Does not write padding on output; does not accept padding when reading (may change later with a call to
     * [Base64Variant.withWritePadding])
     */
    val MODIFIED_FOR_URL = Base64Variant("MODIFIED-FOR-URL", StringBuilder(STD_BASE64_ALPHABET).apply {
        this[indexOf("+")] = '-'
        this[indexOf("/")] = '_'
    }.toString(), false, Base64Variant.PADDING_CHAR_NONE, Int.MAX_VALUE)

    /**
     * Method used to get the default variant -- [MIME_NO_LINEFEEDS] -- for cases where caller does not explicitly
     * specify the variant. We will prefer no-linefeed version because linefeeds in JSON values must be escaped, making
     * linefeed-containing variants suboptimal.
     *
     * @return Default variant (`MIME_NO_LINEFEEDS`)
     */
    val defaultVariant = MIME_NO_LINEFEEDS

    /**
     * Lookup method for finding one of standard variants by name. If name does not match any of standard variant names,
     * a [IllegalArgumentException] is thrown.
     *
     * @param name Name of base64 variant to return
     *
     * @return Standard base64 variant that matches given `name`
     *
     * @throws IllegalArgumentException if no standard variant with given name exists
     */
    @Throws(IllegalArgumentException::class)
    fun valueOf(name: String?): Base64Variant {
        return when (name) {
            MIME.name -> MIME
            MIME_NO_LINEFEEDS.name -> MIME_NO_LINEFEEDS
            PEM.name -> PEM
            MODIFIED_FOR_URL.name -> MODIFIED_FOR_URL
            else -> throw IllegalArgumentException("No Base64Variant with name ${name?.let { "'$it'" } ?: "<null>"}")
        }
    }

}