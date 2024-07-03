package org.cirjson.cirjackson.core.base

import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.ObjectReadContext
import org.cirjson.cirjackson.core.StreamReadConstraints
import org.cirjson.cirjackson.core.StreamReadFeature
import org.cirjson.cirjackson.core.io.IOContext

/**
 * Intermediate base class used by all CirJackson [CirJsonParser] implementations, but does not add any additional
 * fields that depend on particular method of obtaining input.
 *
 * Note that 'minimal' here mostly refers to minimal number of fields (size) and functionality that is specific to
 * certain types of parser implementations; but not necessarily to number of methods.
 *
 * @property myIOContext I/O context for this reader. It handles buffer allocation for the reader, including possible
 * reuse/recycling.
 */
abstract class ParserMinimalBase private constructor(override val objectReadContext: ObjectReadContext,
        private val myIOContext: IOContext?, override val streamReadFeatures: Int,
        override val streamReadConstraints: StreamReadConstraints) : CirJsonParser() {

    /**
     * Main constructor for subclasses to use
     *
     * @param objectReadContext Context for databinding
     *
     * @param ioContext Context for I/O handling, buffering
     *
     * @param streamReadFeatures Bit set of [StreamReadFeature]s.
     */
    constructor(objectReadContext: ObjectReadContext, ioContext: IOContext, streamReadFeatures: Int) : this(
            objectReadContext, ioContext, streamReadFeatures, ioContext.streamReadConstraints)

    companion object {

        // Control chars:

        const val CODE_TAB = '\t'.code

        const val CODE_LF = '\n'.code

        const val CODE_CR = '\r'.code

        const val CODE_SPACE = 0x0020

        // Markup

        const val CODE_L_BRACKET = '['.code

        const val CODE_R_BRACKET = ']'.code

        const val CODE_L_CURLY = '{'.code

        const val CODE_R_CURLY = '}'.code

        const val CODE_QUOTE = '"'.code

        const val CODE_APOSTROPHE = '\''.code

        const val CODE_BACKSLASH = '\\'.code

        const val CODE_SLASH = '/'.code

        const val CODE_ASTERISK = '*'.code

        const val CODE_COLON = ':'.code

        const val CODE_COMMA = ','.code

        const val CODE_HASH = '#'.code

        // Numbers

        const val CODE_0 = '0'.code

        const val CODE_9 = '9'.code

        const val CODE_MINUS = '-'.code

        const val CODE_PLUS = '+'.code

        const val CODE_PERIOD = '.'.code

        const val CODE_e = 'e'.code

        const val CODE_E = 'E'.code

        // Other

        const val CODE_NULL_CHAR = '\u0000'.code

        val NO_BYTES = byteArrayOf()

        val NO_INTEGERS = intArrayOf()

        val STREAM_READ_FEATURE_DEFAULTS = StreamReadFeature.collectDefaults()

    }

}