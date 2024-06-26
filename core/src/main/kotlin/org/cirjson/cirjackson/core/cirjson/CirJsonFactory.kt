package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.base.TextualTSFactory
import org.cirjson.cirjackson.core.io.IOContext
import java.io.*

/**
 * The main factory class of CirJackson package, used to configure and construct reader (aka parser, [CirJsonParser])
 * and writer (aka generator, [CirJsonGenerator]) instances.
 *
 * Factory instances are thread-safe and reusable after configuration (if any). Typically, applications and services use
 * only a single globally shared factory instance, unless they need differently configured factories. Factory reuse is
 * important if efficiency matters; most recycling of expensive construct is done on per-factory basis.
 *
 * Creation of a factory instance is a light-weight operation, and since there is no need for pluggable alternative
 * implementations (as there is no "standard" CirJSON processor API to implement), the default constructor is used for
 * constructing factory instances.
 */
open class CirJsonFactory : TextualTSFactory {

    /**
     * Default constructor used to create factory instances.
     *
     * Creation of a factory instance is a light-weight operation, but it is still a good idea to reuse limited number
     * of factory instances (and quite often just a single instance): factories are used as context for storing some
     * reused processing objects (such as symbol tables parsers use) and this reuse only works within context of a
     * single factory instance.
     */
    constructor() : super(StreamReadConstraints.defaults(), StreamWriteConstraints.defaults(),
            ErrorReportConfiguration.defaults(), DEFAULT_CIRJSON_PARSER_FEATURE_FLAGS,
            DEFAULT_CIRJSON_GENERATOR_FEATURE_FLAGS)

    constructor(src: CirJsonFactory) : super(src)

    override fun copy(): TokenStreamFactory {
        return CirJsonFactory(this)
    }

    override fun snapshot(): TokenStreamFactory {
        return this
    }

    override fun version(): Version {
        TODO("Not yet implemented")
    }

    override fun rebuild(): TSFBuilder<*, *> {
        TODO("Not yet implemented")
    }

    override fun createParser(readContext: ObjectReadContext, context: IOContext, data: ByteArray, offset: Int,
            len: Int): CirJsonParser {
        TODO("Not yet implemented")
    }

    override fun createParser(readContext: ObjectReadContext, context: IOContext, content: CharArray, offset: Int,
            len: Int, recyclable: Boolean): CirJsonParser {
        TODO("Not yet implemented")
    }

    override fun createParser(readContext: ObjectReadContext, context: IOContext, input: DataInput): CirJsonParser {
        TODO("Not yet implemented")
    }

    override fun createParser(readContext: ObjectReadContext, context: IOContext, input: InputStream): CirJsonParser {
        TODO("Not yet implemented")
    }

    override fun createParser(readContext: ObjectReadContext, context: IOContext, reader: Reader): CirJsonParser {
        TODO("Not yet implemented")
    }

    override fun createGenerator(writeContext: ObjectWriteContext, context: IOContext,
            writer: Writer): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    override fun createUTF8Generator(writeContext: ObjectWriteContext, context: IOContext,
            output: OutputStream): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    override val isParsingAsyncPossible: Boolean
        get() = TODO("Not yet implemented")

    override fun canUseSchema(schema: FormatSchema): Boolean {
        TODO("Not yet implemented")
    }

    override val formatName: String
        get() = TODO("Not yet implemented")

    companion object {

        /**
         * Name used to identify CirJSON format (and returned by [formatName]
         */
        const val FORMAT_NAME_CIRJSON = "CirJSON"

        /**
         * Bitfield (set of flags) of all parser features that are enabled by default.
         */
        internal val DEFAULT_CIRJSON_PARSER_FEATURE_FLAGS = CirJsonReadFeature.collectDefaults()

        /**
         * Bitfield (set of flags) of all generator features that are enabled by default.
         */
        internal val DEFAULT_CIRJSON_GENERATOR_FEATURE_FLAGS = CirJsonReadFeature.collectDefaults()


    }

}