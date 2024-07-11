package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.async.ByteArrayFeeder
import org.cirjson.cirjackson.core.base.TextualTSFactory
import org.cirjson.cirjackson.core.io.CharacterEscapes
import org.cirjson.cirjackson.core.io.IOContext
import org.cirjson.cirjackson.core.symbols.ByteQuadsCanonicalizer
import org.cirjson.cirjackson.core.symbols.CharsToNameCanonicalizer
import org.cirjson.cirjackson.core.util.DefaultPrettyPrinter
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
     * Definition of custom character escapes to use for generators created by this factory, if any. If `null`, standard
     * data format specific escapes are used.
     */
    val characterEscapes: CharacterEscapes?

    /**
     * Separator used between root-level values, if any; `null` indicates "do not add separator". Default separator is a
     * single space character.
     */
    protected val myRootValueSeparator: SerializableString?

    /**
     * Internal accessor to [myRootValueSeparator].
     */
    internal val rootValueSeparatorInternal: SerializableString?
        get() = myRootValueSeparator

    /**
     * Optional threshold used for automatically escaping character above certain character
     * code value: either `0` to indicate that no threshold is specified, or value
     * at or above 127 to indicate last character code that is NOT automatically escaped
     * (but depends on other configuration rules for checking).
     */
    protected val myMaximumNonEscapedCharCode: Int

    /**
     * Internal accessor to [myMaximumNonEscapedCharCode].
     */
    internal val maximumNonEscapedCharCodeInternal: Int
        get() = myMaximumNonEscapedCharCode

    /**
     * Character used for quoting property names (if property name quoting has not been disabled with
     * [CirJsonWriteFeature.QUOTE_PROPERTY_NAMES]) and CirJSON String values.
     */
    protected val myQuoteChar: Char

    /**
     * Internal accessor to [myQuoteChar].
     */
    internal val quoteCharInternal: Char
        get() = myQuoteChar

    /**
     * Each factory comes equipped with a shared root symbol table. It should not be linked back to the original
     * blueprint, to avoid contents from leaking between factories.
     */
    protected val myRootCharSymbols: CharsToNameCanonicalizer

    /**
     * Alternative to the basic symbol table, some stream-based parsers use different name canonicalization method.
     */
    protected val myByteSymbolCanonicalizer = ByteQuadsCanonicalizer.createRoot()

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
            DEFAULT_CIRJSON_GENERATOR_FEATURE_FLAGS) {
        myRootValueSeparator = DefaultPrettyPrinter.DEFAULT_ROOT_VALUE_SEPARATOR
        characterEscapes = null
        myMaximumNonEscapedCharCode = 0
        myQuoteChar = DEFAULT_QUOTE_CHAR
        myRootCharSymbols = CharsToNameCanonicalizer.createRoot(this)
    }

    /**
     * Copy constructor.
     *
     * @param src Original factory to copy configuration from
     */
    constructor(src: CirJsonFactory) : super(src) {
        myRootValueSeparator = src.myRootValueSeparator
        characterEscapes = src.characterEscapes
        myMaximumNonEscapedCharCode = src.myMaximumNonEscapedCharCode
        myQuoteChar = src.myQuoteChar
        myRootCharSymbols = CharsToNameCanonicalizer.createRoot(this)
    }

    constructor(builder: CirJsonFactoryBuilder) : super(builder) {
        myRootValueSeparator = builder.rootValueSeparator
        characterEscapes = builder.characterEscapes
        myMaximumNonEscapedCharCode = builder.highestNonEscapedCharCode
        myQuoteChar = builder.quoteChar
        myRootCharSymbols = CharsToNameCanonicalizer.createRoot(this)
    }

    override fun rebuild(): CirJsonFactoryBuilder {
        return CirJsonFactoryBuilder(this)
    }

    override fun copy(): CirJsonFactory {
        return CirJsonFactory(this)
    }

    override fun snapshot(): TokenStreamFactory {
        return this
    }

    /*
     *******************************************************************************************************************
     * Capability introspection
     *******************************************************************************************************************
     */

    override fun version(): Version {
        return PackageVersion.VERSION
    }

    override val isParsingAsyncPossible: Boolean
        get() = true

    /**
     * Checked whether specified parser feature is enabled.
     *
     * @param feature Feature to check
     *
     * @return `true` if feature is enabled; `false` otherwise
     */
    fun isEnabled(feature: CirJsonReadFeature): Boolean {
        return formatReadFeatures and feature.mask != 0
    }

    /**
     * Check whether specified generator feature is enabled.
     *
     * @param feature Feature to check
     *
     * @return `true` if feature is enabled; `false` otherwise
     */
    fun isEnabled(feature: CirJsonWriteFeature): Boolean {
        return formatWriteFeatures and feature.mask != 0
    }

    /*
     *******************************************************************************************************************
     * Configuration access
     *******************************************************************************************************************
     */

    override fun canUseSchema(schema: FormatSchema): Boolean {
        return false
    }

    override val formatName: String
        get() = FORMAT_NAME_CIRJSON

    override val formatReadFeatureType: Class<out FormatFeature>
        get() = CirJsonReadFeature::class.java

    override val formatWriteFeatureType: Class<out FormatFeature>
        get() = CirJsonWriteFeature::class.java

    /*
     *******************************************************************************************************************
     * Configuration access
     *******************************************************************************************************************
     */

    val rootValueSeparator: String?
        get() = myRootValueSeparator?.value

    /*
     *******************************************************************************************************************
     * Parser factories, non-blocking (async) sources
     *******************************************************************************************************************
     */

    override fun <P> createNonBlockingByteArrayParser(
            readContext: ObjectReadContext): P where P : CirJsonParser, P : ByteArrayFeeder {
        TODO()
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

        const val DEFAULT_QUOTE_CHAR = '"'

        /**
         * Main factory method to use for constructing [CirJsonFactory] instances with different configuration.
         *
         * @return Builder instance to use
         */
        fun builder(): CirJsonFactoryBuilder {
            return CirJsonFactoryBuilder()
        }

    }

}