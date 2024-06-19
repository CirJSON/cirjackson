package org.cirjson.cirjackson.core

import org.cirjson.cirjackson.core.exception.StreamWriteException
import org.cirjson.cirjackson.core.util.CirJacksonFeature
import java.io.OutputStream
import java.io.Writer
import java.math.BigDecimal

/**
 * Token writer (generator) features not-specific to any particular format backend.
 */
enum class StreamWriteFeature(override val isEnabledByDefault: Boolean) : CirJacksonFeature {

    /**
     * Feature that determines whether generator will automatically close underlying output target that is NOT owned by
     * the generator.
     *
     * If disabled, calling application has to separately close the underlying [OutputStream] and [Writer] instances
     * used to create the generator. If enabled, generator will handle closing, as long as generator itself gets closed:
     * this happens when end-of-input is encountered, or generator is closed by a call to [CirJsonGenerator.close].
     *
     * Feature is enabled by default.
     */
    AUTO_CLOSE_TARGET(true),

    /**
     * Feature that determines what happens when the generator is closed while there are still unmatched
     * [CirJsonToken.START_ARRAY] or [CirJsonToken.START_OBJECT] entries in output content. If enabled, such Array(s)
     * and/or Object(s) are automatically closed; if disabled, nothing specific is done.
     *
     * Feature is enabled by default.
     */
    AUTO_CLOSE_CONTENT(true),

    /**
     * Feature that specifies that calls to [CirJsonGenerator.flush] will cause matching `flush()` to underlying
     * [OutputStream] or [Writer]; if disabled this will not be done.
     *
     * Main reason to disable this feature is to prevent flushing at generator level, if it is not possible to prevent
     * method being called by other code (like `ObjectMapper` or third party libraries).
     *
     * Feature is enabled by default.
     */
    FLUSH_PASSED_TO_STREAM(true),

    // // Datatype coercion features

    /**
     * Feature that determines whether [java.math.BigDecimal] entries are serialized using
     * [java.math.BigDecimal.toPlainString] to prevent values to be written using scientific notation.
     *
     * NOTE: only affects generators that serialize [java.math.BigDecimal]s using textual representation (textual
     * formats but potentially some binary formats).
     *
     * Feature is disabled by default, so default output mode is used; this generally depends on how [BigDecimal] has
     * been created.
     */
    WRITE_BIG_DECIMAL_AS_PLAIN(false),

    // // Schema/Validity support features

    /**
     * Feature that determines whether [CirJsonGenerator] will explicitly check that no duplicate CirJSON Object
     * Property names are written.
     *
     * If enabled, generator will check all names within context and report duplicates by throwing a
     * [StreamWriteException]; if disabled, no such checking will be done. Assumption in latter case is that caller
     * takes care of not trying to write duplicate names.
     *
     * Note that enabling this feature will incur performance overhead due to having to store and check additional
     * information.
     *
     * Feature is disabled by default.
     */
    STRICT_DUPLICATE_DETECTION(false),

    /**
     * Feature that determines what to do if the underlying data format requires knowledge of all properties to output,
     * and if no definition is found for a property that caller tries to write. If enabled, such properties will be
     * quietly ignored; if disabled, a [StreamWriteException] will be thrown to indicate the problem.
     *
     * Typically most textual data formats do NOT require schema information (although some do, such as CSV), whereas
     * many binary data formats do require definitions (such as Avro, protobuf), although not all (Smile, CBOR, BSON and
     * MessagePack do not).
     *
     * Note that support for this feature is implemented by individual data format module, if (and only if) it makes
     * sense for the format in question. For JSON, for example, this feature has no effect as properties need not be
     * pre-defined.
     *
     * Feature is disabled by default, meaning that if the underlying data format requires knowledge of all properties
     * to output, attempts to write an unknown property will result in a [StreamWriteException]
     */
    IGNORE_UNKNOWN(false),

    // // Misc other features

    /**
     * Feature that determines whether to use standard JDK methods to write floats/doubles or use faster Schubfach
     * algorithm.
     *
     * The latter approach may lead to small differences in the precision of the float/double that is written to the
     * CirJSON output.
     *
     * This setting is enabled by default so that faster Schubfach implementation is used.
     */
    USE_FAST_DOUBLE_WRITER(false);

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