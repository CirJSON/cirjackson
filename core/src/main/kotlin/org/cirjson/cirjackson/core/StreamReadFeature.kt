package org.cirjson.cirjackson.core

enum class StreamReadFeature(override val isEnabledByDefault: Boolean) : CirJacksonFeature {

    /**
     * Feature that determines whether parser will automatically close underlying input source that is NOT owned by
     * the parser. If disabled, calling application has to separately close the underlying [java.io.InputStream] and
     * [java.io.Reader] instances used to create the parser. If enabled, parser will handle closing, as long as parser
     * itself gets closed: this happens when end-of-input is encountered, or parser is closed by a call to
     * [CirJsonParser.close].
     *
     * Feature is enabled by default.
     */
    AUTO_CLOSE_SOURCE(true),

    /**
     * Feature that determines whether [CirJsonParser] will explicitly check that no duplicate CirJSON Object field
     * names are encountered. If enabled, parser will check all names within context and report duplicates by
     * throwing a [org.cirjson.cirjackson.core.exception.StreamReadException]; if disabled, parser will not do such
     * checking. Assumption in latter case is that caller takes care of handling duplicates at a higher level:
     * data-binding, for example, has features to specify detection to be done there.
     *
     * Note that enabling this feature will incur performance overhead due to having to store and check additional
     * information: this typically adds 20-30% to execution time for basic parsing.
     */
    STRICT_DUPLICATE_DETECTION(false),

    /**
     * Feature that determines what to do if the underlying data format requires knowledge of all properties to
     * decode (usually via a Schema), and if no definition is found for a property that input content contains.
     * Typically most textual data formats do NOT require schema information (although some do, such as CSV),
     * whereas many binary data formats do require definitions (such as Avro, protobuf), although not all (Smile,
     * CBOR, BSON and MessagePack do not). Further note that some formats that do require schema information will
     * not be able to ignore undefined properties: for example, Avro is fully positional and there is no possibility
     * of undefined data. This leaves formats like Protobuf that have identifiers that may or may not map; and as
     * such Protobuf format does make use of this feature.
     *
     * Note that support for this feature is implemented by individual data format module, if (and only if) it makes
     * sense for the format in question. For CirJSON, for example, this feature has no effect as properties need not
     * be pre-defined.
     *
     * Feature is disabled by default, meaning that if the underlying data format requires knowledge of all
     * properties to output, attempts to read an unknown property will result in a [CirJsonProcessingException]
     */
    IGNORE_UNDEFINED(false),

    // // // Other

    /**
     * Feature that determines whether the [CirJsonLocation] instances should be constructed with a reference to the
     * source or not. If source reference is included, its type and contents are included when `toString()` method
     * is called (most notably when printing out parse exception with that location information). If feature is
     * disabled, no source reference is passed and source indicated as "UNKNOWN".
     *
     * Most common reason for disabling this feature is to avoid leaking information about internal information;
     * this may be done for security reasons.
     *
     * Note that even if the source reference is included, only parts of contents are usually printed, and not the
     * whole contents. Further, many source reference types can not necessarily access contents (like streams), so
     * only type is indicated, not contents.
     *
     * This feature is **disabled** by default, meaning that "source reference" information is NOT passed; this is
     * for security reasons (so by default no information gets leaked)
     */
    INCLUDE_SOURCE_IN_LOCATION(false),

    /**
     * Feature that determines whether we use the built-in [String.toDouble] code to parse doubles or if we use
     * `FastDoubleParser` implementation instead.
     */
    USE_FAST_DOUBLE_PARSER(true),

    /**
     * Feature that determines whether to use the built-in Java code for parsing `BigDecimal`s and `BigIntegers`s or
     * to use specifically optimized custom implementation instead.
     */
    USE_FAST_BIG_NUMBER_PARSER(true);

    override val mask: Int = 1 shl ordinal

    override fun isEnabledIn(flags: Int): Boolean {
        return (flags and mask) != 0
    }

    companion object {

        /**
         * Method that calculates bit set (flags) of all features that are enabled by default.
         *
         * @return Bit field of features enabled by default
         */
        fun collectDefaults(): Int {
            var flags = 0

            for (feature in entries) {
                if (feature.isEnabledByDefault) {
                    flags = flags or feature.mask
                }
            }

            return flags
        }

    }
}