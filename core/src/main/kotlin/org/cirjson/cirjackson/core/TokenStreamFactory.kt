package org.cirjson.cirjackson.core

import org.cirjson.cirjackson.core.async.ByteArrayFeeder
import org.cirjson.cirjackson.core.async.ByteBufferFeeder
import org.cirjson.cirjackson.core.exception.CirJacksonIOException
import org.cirjson.cirjackson.core.exception.StreamReadException
import org.cirjson.cirjackson.core.io.ContentReference
import org.cirjson.cirjackson.core.io.DataOutputAsStream
import org.cirjson.cirjackson.core.io.IOContext
import org.cirjson.cirjackson.core.symbols.PropertyNameMatcher
import org.cirjson.cirjackson.core.symbols.SimpleNameMatcher
import org.cirjson.cirjackson.core.util.*
import java.io.*
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

/**
 * Intermediate base class for actual format-specific factories for constructing parsers (reading) and generators
 * (writing).
 */
abstract class TokenStreamFactory : Versioned, Snapshottable<TokenStreamFactory>, Serializable {

    /*
     *******************************************************************************************************************
     * Configuration, simple features
     *******************************************************************************************************************
     */

    /**
     * Value for getting bit set of all [Feature]s enabled.
     */
    val factoryFeatures: Int

    /**
     * Value for getting bit set of all [StreamReadFeature]s enabled.
     */
    val streamReadFeatures: Int

    /**
     * Value for getting bit set of all [StreamWriteFeature]s enabled.
     */
    val streamWriteFeatures: Int

    /**
     * Value for getting bit set of all format-specific parser features enabled.
     */
    val formatReadFeatures: Int

    /**
     * Value for getting bit set of all format-specific generator features enabled.
     */
    val formatWriteFeatures: Int

    /*
     *******************************************************************************************************************
     * Configuration, providers
     *******************************************************************************************************************
     */

    val recyclerPool: RecyclerPool<BufferRecycler>

    /*
     *******************************************************************************************************************
     * Configuration, constraints
     *******************************************************************************************************************
     */

    /**
     * Active StreamReadConstraints to use.
     */
    val streamReadConstraints: StreamReadConstraints

    /**
     * Active StreamWriteConstraints to use.
     */
    val streamWriteConstraints: StreamWriteConstraints

    /**
     * Active ErrorReportConfiguration to use.
     */
    val errorReportConfiguration: ErrorReportConfiguration

    /*
     *******************************************************************************************************************
     * Construction
     *******************************************************************************************************************
     */

    /**
     * Default constructor used to create factory instances.
     *
     * Creation of a factory instance is a light-weight operation, but it is still a good idea to reuse limited number
     * of factory instances (and quite often just a single instance): factories are used as context for storing some
     * reused processing objects (such as symbol tables parsers use) and this reuse only works within context of a
     * single factory instance.
     *
     * @param streamReadConstraints StreamReadConstraints to use with parsers factory creates
     * @param streamWriteConstraints StreamWriteConstraints to use with generators factory creates
     * @param errorReportConfiguration ErrorReportConfiguration to use with parsers factory creates
     * @param formatReadFeatures Bitmask of format-specific read features enabled
     * @param formatWriteFeatures Bitmask of format-specific write features enabled
     */
    protected constructor(streamReadConstraints: StreamReadConstraints, streamWriteConstraints: StreamWriteConstraints,
            errorReportConfiguration: ErrorReportConfiguration, formatReadFeatures: Int, formatWriteFeatures: Int) {
        this.streamReadConstraints = streamReadConstraints
        this.streamWriteConstraints = streamWriteConstraints
        this.errorReportConfiguration = errorReportConfiguration
        recyclerPool = CirJsonRecyclerPools.defaultPool()
        factoryFeatures = DEFAULT_FACTORY_FEATURE_FLAGS
        streamReadFeatures = DEFAULT_STREAM_READ_FEATURE_FLAGS
        streamWriteFeatures = DEFAULT_STREAM_WRITE_FEATURE_FLAGS
        this.formatReadFeatures = formatReadFeatures
        this.formatWriteFeatures = formatWriteFeatures
    }

    /**
     * Constructors used by [TSFBuilder] for instantiation. Base builder is passed as-is to try to make interface
     * between base types and implementations less likely to change (given that sub-classing is a fragile way to do it):
     * if and when new general-purpose properties are added, implementation classes do not have to use different
     * constructors.
     *
     * @param baseBuilder Builder with configuration to use
     */
    protected constructor(baseBuilder: TSFBuilder<*, *>) {
        streamReadConstraints = baseBuilder.streamReadConstraints
        streamWriteConstraints = baseBuilder.streamWriteConstraints
        errorReportConfiguration = baseBuilder.errorReportConfiguration

        recyclerPool = baseBuilder.recyclerPool ?: CirJsonRecyclerPools.defaultPool()

        factoryFeatures = baseBuilder.factoryFeatures
        streamReadFeatures = baseBuilder.streamReadFeatures
        streamWriteFeatures = baseBuilder.streamWriteFeatures
        formatReadFeatures = baseBuilder.formatReadFeatures
        formatWriteFeatures = baseBuilder.formatWriteFeatures
    }

    /**
     * Constructor used if a snapshot is created, or possibly for subclassing or wrapping (delegating)
     *
     * @param src Source factory with configuration to copy
     */
    protected constructor(src: TokenStreamFactory) {
        streamReadConstraints = src.streamReadConstraints
        streamWriteConstraints = src.streamWriteConstraints
        errorReportConfiguration = src.errorReportConfiguration
        recyclerPool = src.recyclerPool
        factoryFeatures = src.factoryFeatures
        streamReadFeatures = src.streamReadFeatures
        streamWriteFeatures = src.streamWriteFeatures
        formatReadFeatures = src.formatReadFeatures
        formatWriteFeatures = src.formatWriteFeatures
    }

    /**
     * Method similar to [snapshot], but one that forces creation of actual new copy that does NOT share any state, even
     * non-visible to calling code, such as symbol table reuse.
     *
     * Implementation should be functionally equivalent to:
     * ```
     * factoryInstance.rebuild().build();
     * ```
     *
     * @return Newly constructed stream factory instance of same type as this one, with identical configuration settings
     */
    abstract fun copy(): TokenStreamFactory

    /**
     * Method that can be used to create differently configured stream factories: it will create and return a Builder
     * instance with exact settings of this stream factory.
     *
     * @return Builder instance initialized with configuration this stream factory has
     */
    abstract override fun snapshot(): TokenStreamFactory

    /**
     * Method that can be used to create differently configured stream factories: it will create and return a Builder
     * instance with exact settings of this stream factory.
     *
     * @return Builder instance initialized with configuration this stream factory has
     */
    abstract fun rebuild(): TSFBuilder<*, *>

    /*
     *******************************************************************************************************************
     * Capability introspection
     *******************************************************************************************************************
     */

    /**
     * Introspection that higher-level functionality may call to see whether underlying data format requires a stable
     * ordering of object properties or not. This is usually used for determining whether to force a stable ordering
     * (like alphabetic ordering by name) if no ordering is explicitly specified.
     *
     * Default implementation returns `false` as CirJSON does NOT require stable ordering. Formats that require ordering
     * include positional textual formats like `CSV`, and schema-based binary formats like `Avro`.
     */
    open val isRequiringPropertyOrdering: Boolean
        get() = false

    /**
     * Introspection that higher-level functionality may call to see whether underlying data format can read and write
     * binary data natively; that is, embedded it as-is without using encodings such as Base64.
     *
     * Default implementation returns `false` as CirJSON does not support native access: all binary content must use
     * Base64 encoding. Most binary formats (like Smile and Avro) support native binary content.
     */
    abstract val isHandlingBinaryNatively: Boolean

    /**
     * Introspection that can be used to check whether this factory can create non-blocking parsers: parsers that do not
     * use blocking I/O abstractions but instead use a [org.cirjson.cirjackson.core.async.NonBlockingInputFeeder].
     *
     * Helps know whether this factory supports non-blocking ("async") parsing or not (and consequently whether
     * `createNonBlockingXxx()` method(s) work)
     */
    abstract val isParsingAsyncPossible: Boolean

    /**
     * Value for accessing kind of [FormatFeature] that a parser [CirJsonParser] produced by this factory would accept,
     * if any; `null` returned if none.
     */
    open val formatReadFeatureType: Class<out FormatFeature>? = null

    /**
     * Value for accessing kind of [FormatFeature] that a parser [CirJsonGenerator] produced by this factory would
     * accept, if any; `null` returned if none.
     */
    open val formatWriteFeatureType: Class<out FormatFeature>? = null

    /*
     *******************************************************************************************************************
     * Format detection functionality
     *******************************************************************************************************************
     */

    /**
     * Method that can be used to quickly check whether given schema is something that parsers and/or generators
     * constructed by this factory could use. Note that this means possible use, at the level of data format (i.e.
     * schema is for same data format as parsers and generators this factory constructs); individual schema instances
     * may have further usage restrictions.
     *
     * @param schema Schema instance to check
     *
     * @return Whether parsers and generators constructed by this factory can use specified format schema instance
     */
    abstract fun canUseSchema(schema: FormatSchema): Boolean

    /**
     * Value that returns short textual id identifying format this factory supports.
     */
    abstract val formatName: String

    /*
     *******************************************************************************************************************
     * Configuration access
     *******************************************************************************************************************
     */

    /**
     * Method that verifies if the feature is enabled fo this factory.
     *
     * @param feature The feature to check.
     *
     * @return Whether it's enabled or not.
     */
    fun isEnabled(feature: Feature): Boolean {
        return (factoryFeatures and feature.mask) != 0
    }

    /**
     * Method that verifies if the feature is enabled fo this factory.
     *
     * @param feature The feature to check.
     *
     * @return Whether it's enabled or not.
     */
    fun isEnabled(feature: StreamReadFeature): Boolean {
        return (streamReadFeatures and feature.mask) != 0
    }

    /**
     * Method that verifies if the feature is enabled fo this factory.
     *
     * @param feature The feature to check.
     *
     * @return Whether it's enabled or not.
     */
    fun isEnabled(feature: StreamWriteFeature): Boolean {
        return (streamWriteFeatures and feature.mask) != 0
    }

    /*
     *******************************************************************************************************************
     * Factory methods for helper objects
     *******************************************************************************************************************
     */

    /**
     * Factory method for constructing case-sensitive [PropertyNameMatcher] for given names. It will call
     * [String.intern] on names unless specified that this has already been done by caller.
     *
     * @param matches Names to match, including both primary names and possible aliases
     * @param alreadyInterned Whether name Strings are already `String.intern()ed` or not
     *
     * @return Case-sensitive [PropertyNameMatcher] instance to use
     */
    open fun constructNameMatcher(matches: List<Named>, alreadyInterned: Boolean): PropertyNameMatcher {
        return SimpleNameMatcher.constructFrom(null, matches, alreadyInterned)
    }

    open fun constructCaseInsensitiveNameMatcher(matches: List<Named>, alreadyInterned: Boolean,
            locale: Locale): PropertyNameMatcher {
        return SimpleNameMatcher.constructCaseInsensitive(locale, matches, alreadyInterned)
    }

    /*
     *******************************************************************************************************************
     * Factory methods, parsers
     *******************************************************************************************************************
     */

    /**
     * Method for constructing parser for parsing the contents of given byte array.
     *
     * @param readContext Object read context to use
     * @param data Buffer that contains data to parse
     *
     * @throws CirJacksonException If there are problems constructing parser
     */
    @Throws(CirJacksonException::class)
    fun createParser(readContext: ObjectReadContext, data: ByteArray): CirJsonParser {
        return createParser(readContext, data, 0, data.size)
    }

    /**
     * Method for constructing parser for parsing the contents of given byte array.
     *
     * @param readContext Object read context to use
     * @param data Buffer that contains data to parse
     * @param offset Offset of the first data byte within buffer
     * @param length Length of contents to parse within buffer
     *
     * @return Constructed parser
     *
     * @throws CirJacksonException If there are problems constructing parser
     */
    @Throws(CirJacksonException::class)
    abstract fun createParser(readContext: ObjectReadContext, data: ByteArray, offset: Int, length: Int): CirJsonParser

    /**
     * Method for constructing parser for parsing contents of given char array.
     *
     * @param readContext Object read context to use
     * @param content Array that contains data to parse
     *
     * @return Constructed parser
     *
     * @throws CirJacksonException If there are problems constructing parser
     */
    @Throws(CirJacksonException::class)
    fun createParser(readContext: ObjectReadContext, content: CharArray): CirJsonParser {
        return createParser(readContext, content, 0, content.size)
    }

    /**
     * Method for constructing parser for parsing contents of given char array.
     *
     * @param readContext Object read context to use
     * @param content Array that contains data to parse
     * @param offset Offset of the first data byte within buffer
     * @param length Length of contents to parse within buffer
     *
     * @return Constructed parser
     *
     * @throws CirJacksonException If there are problems constructing parser
     */
    @Throws(CirJacksonException::class)
    abstract fun createParser(readContext: ObjectReadContext, content: CharArray, offset: Int,
            length: Int): CirJsonParser

    /**
     * Optional method for constructing parser for reading contents from specified [DataInput] instance.
     *
     * If this factory does not support [DataInput] as source, will throw [UnsupportedOperationException]
     *
     * @param readContext Object read context to use
     * @param input The data to parse
     *
     * @return Constructed parser
     *
     * @throws CirJacksonException If there are problems constructing parser
     */
    @Throws(CirJacksonException::class)
    abstract fun createParser(readContext: ObjectReadContext, input: DataInput): CirJsonParser

    /**
     * Method for constructing CirJSON parser instance to parse contents of specified file.
     *
     * Encoding is auto-detected from contents according to JSON specification recommended mechanism. CirJSON
     * specification supports only UTF-8, UTF-16 and UTF-32 as valid encodings, so auto-detection implemented only for
     * this charsets. For other charsets use the `createParser` that uses a [Reader].
     *
     * Underlying input stream (needed for reading contents) will be **owned** (and managed, i.e. closed as need be) by
     * the parser, since caller has no access to it.
     *
     * @param readContext Object read context to use
     * @param file File that contains CirJSON content to parse
     *
     * @return Constructed parser
     *
     * @throws CirJacksonException If there are problems constructing parser
     */
    @Throws(CirJacksonException::class)
    abstract fun createParser(readContext: ObjectReadContext, file: File): CirJsonParser

    /**
     * Method for constructing parser instance to decode contents of specified path.
     *
     * Encoding is auto-detected from contents according to CirJSON specification recommended mechanism. CirJson
     * specification supports only UTF-8, UTF-16 and UTF-32 as valid encodings, so auto-detection implemented only for
     * this charsets. For other charsets use the `createParser` that uses a [Reader].
     *
     * Underlying input stream (needed for reading contents) will be **owned** (and managed, i.e. closed as need be) by
     * the parser, since caller has no access to it.
     *
     * @param readContext Object read context to use
     *
     * @param path Path that contains content to parse
     *
     * @return Parser constructed
     *
     * @throws CirJacksonException If parser construction or initialization fails
     *
     * @since 3.0
     */
    @Throws(CirJacksonException::class)
    abstract fun createParser(readContext: ObjectReadContext, path: Path): CirJsonParser

    /**
     * Method for constructing CirJSON parser instance to parse the contents accessed via specified input stream.
     *
     * The input stream will **not be owned** by the parser, it will still be managed (i.e. closed if end-of-stream is
     * reached, or parser close method called) if (and only if) [StreamReadFeature.AUTO_CLOSE_SOURCE] is enabled.
     *
     * Note: no encoding argument is taken since it can always be auto-detected as suggested by CirJSON RFC. CirJSON
     * specification supports only UTF-8, UTF-16 and UTF-32 as valid encodings, so auto-detection implemented only for
     * this charsets. For other charsets use the `createParser` that uses a [Reader].
     *
     * @param readContext Object read context to use
     * @param input InputStream to use for reading CirJSON content to parse
     *
     * @return Constructed parser
     *
     * @throws CirJacksonException If there are problems constructing parser
     */
    @Throws(CirJacksonException::class)
    abstract fun createParser(readContext: ObjectReadContext, input: InputStream): CirJsonParser

    /**
     * Method for constructing parser for parsing the contents accessed via specified Reader.
     *
     * The read stream will **not be owned** by the parser, it will still be managed (i.e. closed if end-of-stream is
     * reached, or parser close method called) if (and only if) [StreamReadFeature.AUTO_CLOSE_SOURCE] is enabled.
     *
     * @param readContext Object read context to use
     * @param reader Reader to use for reading CirJSON content to parse
     *
     * @return Constructed parser
     *
     * @throws CirJacksonException If there are problems constructing parser
     */
    @Throws(CirJacksonException::class)
    abstract fun createParser(readContext: ObjectReadContext, reader: Reader): CirJsonParser

    /**
     * Method for constructing parser for parsing contents of given String.
     *
     * @param readContext Object read context to use
     * @param content The content to parse
     *
     * @return Constructed parser
     *
     * @throws CirJacksonException If there are problems constructing parser
     */
    @Throws(CirJacksonException::class)
    abstract fun createParser(readContext: ObjectReadContext, content: String): CirJsonParser

    /**
     * Method for constructing CirJSON parser instance to parse contents of resource reference by given URL.
     *
     * Encoding is auto-detected from contents according to CirJSON specification recommended mechanism. CirJSON
     * specification supports only UTF-8, UTF-16 and UTF-32 as valid encodings, so auto-detection implemented only for
     * this charsets. For other charsets use the `createParser` that uses a [Reader].
     *
     * Underlying input stream (needed for reading contents) will be **owned** (and managed, i.e. closed as need be) by
     * the parser, since caller has no access to it.
     *
     * @param readContext Object read context to use
     * @param url URL pointing to resource that contains CirJSON content to parse
     *
     * @return Constructed parser
     *
     * @throws CirJacksonException If there are problems constructing parser
     */
    @Throws(CirJacksonException::class)
    abstract fun createParser(readContext: ObjectReadContext, url: URL): CirJsonParser

    /**
     * Optional method for constructing parser for non-blocking parsing via [ByteArrayFeeder] interface (accessed using
     * [CirJsonParser.nonBlockingInputFeeder] from constructed instance).
     *
     * If this factory does not support non-blocking parsing (either at all, or from byte array), will throw
     * [UnsupportedOperationException].
     *
     * Note that CirJSON-backed factory only supports parsing of UTF-8 encoded CirJSON content (and US-ASCII since it is
     * proper subset); other encodings are not supported at this point.
     *
     * @param P Nominal type of parser constructed and returned
     * @param readContext Object read context to use
     *
     * @return Constructed parser
     *
     * @throws CirJacksonException If there are problems constructing parser
     */
    @Throws(CirJacksonException::class)
    open fun <P> createNonBlockingByteArrayParser(
            readContext: ObjectReadContext): P where P : CirJsonParser, P : ByteArrayFeeder {
        return unsupported("Non-blocking source not (yet?) supported for this format ($formatName)")
    }

    /**
     * Optional method for constructing parser for non-blocking parsing via [ByteBufferFeeder] interface (accessed using
     * [CirJsonParser.nonBlockingInputFeeder] from constructed instance).
     *
     * If this factory does not support non-blocking parsing (either at all, or from byte array), will throw
     * [UnsupportedOperationException].
     *
     * Note that CirJSON-backed factory only supports parsing of UTF-8 encoded CirJSON content (and US-ASCII since it is
     * proper subset); other encodings are not supported at this point.
     *
     * @param P Nominal type of parser constructed and returned
     * @param readContext Object read context to use
     *
     * @return Constructed parser
     *
     * @throws CirJacksonException If there are problems constructing parser
     */
    @Throws(CirJacksonException::class)
    open fun <P> createNonBlockingByteBufferParser(
            readContext: ObjectReadContext): P where P : CirJsonParser, P : ByteBufferFeeder {
        return unsupported("Non-blocking source not (yet?) supported for this format ($formatName)")
    }

    /*
     *******************************************************************************************************************
     * Factory methods, generators
     *******************************************************************************************************************
     */

    /**
     * Method for constructing generator for writing content using specified [DataOutput] instance.
     *
     * @param writeContext Object-binding context where applicable; used for providing contextual configuration
     * @param output DataOutput to use for writing CirJSON content
     *
     * @return Constructed generator
     *
     * @throws CirJacksonException If there are problems constructing parser
     */
    @Throws(CirJacksonException::class)
    fun createGenerator(writeContext: ObjectWriteContext, output: DataOutput): CirJsonGenerator {
        return createGenerator(writeContext, createDataOutputWrapper(output))
    }

    /**
     * Method for constructing CirJSON generator for writing CirJSON content to specified file, overwriting contents it
     * might have (or creating it if such file does not yet exist). Encoding to use must be specified, and needs to be
     * one of available types (as per CirJSON specification).
     *
     * Underlying stream **is owned** by the generator constructed, i.e. generator will handle closing of file when
     * [CirJsonGenerator.close] is called.
     *
     * @param writeContext Object-binding context where applicable; used for providing contextual configuration
     * @param file File to write contents to
     * @param encoding Character encoding to use
     *
     * @return Constructed generator
     *
     * @throws CirJacksonException If there are problems constructing parser
     */
    @Throws(CirJacksonException::class)
    abstract fun createGenerator(writeContext: ObjectWriteContext, file: File,
            encoding: CirJsonEncoding): CirJsonGenerator

    /**
     * Method for constructing generator that writes contents to specified path, overwriting contents it might have (or
     * creating it if such path does not yet exist).
     *
     * Underlying stream **is owned** by the generator constructed, i.e. generator will handle closing of file when
     * [CirJsonGenerator.close] is called.
     *
     * @param writeContext Object-binding context where applicable; used for providing contextual configuration
     *
     * @param path Path to write contents to
     *
     * @param encoding Character set encoding to use (usually {@link CirJsonEncoding.UTF8})
     *
     * @return Generator constructed
     *
     * @throws CirJacksonException If generator construction or initialization fails
     */
    @Throws(CirJacksonException::class)
    abstract fun createGenerator(writeContext: ObjectWriteContext, path: Path,
            encoding: CirJsonEncoding): CirJsonGenerator

    /**
     * Convenience method for constructing generator that uses default encoding of the format (UTF-8 for CirJSON and
     * most other data formats).
     *
     * Note: there are formats that use fixed encoding (like most binary data formats).
     *
     * @param writeContext Object-binding context where applicable; used for providing contextual configuration
     * @param output OutputStream to use for writing CirJSON content
     *
     * @return Constructed generator
     *
     * @throws CirJacksonException If there are problems constructing parser
     */
    @Throws(CirJacksonException::class)
    fun createGenerator(writeContext: ObjectWriteContext, output: OutputStream): CirJsonGenerator {
        return createGenerator(writeContext, output, CirJsonEncoding.UTF8)
    }

    /**
     * Method for constructing CirJSON generator for writing CirJSON content using specified output stream. Encoding to
     * use must be specified, and needs to be one of available types (as per CirJSON specification).
     *
     * Underlying stream **is NOT owned** by the generator constructed, so that generator will NOT close the output
     * stream when [CirJsonGenerator.close] is called (unless auto-closing feature,
     * [StreamWriteFeature.AUTO_CLOSE_TARGET] is enabled). Using application needs to close it explicitly if this is the
     * case.
     *
     * Note: there are formats that use fixed encoding (like most binary data formats) and that ignore passed in
     * encoding.
     *
     * @param writeContext Object-binding context where applicable; used for providing contextual configuration
     * @param output OutputStream to use for writing CirJSON content
     * @param encoding Character encoding to use
     *
     * @return Constructed generator
     *
     * @throws CirJacksonException If there are problems constructing parser
     */
    @Throws(CirJacksonException::class)
    abstract fun createGenerator(writeContext: ObjectWriteContext, output: OutputStream,
            encoding: CirJsonEncoding): CirJsonGenerator

    /**
     * Method for constructing CirJSON generator for writing CirJSON content using specified Writer.
     *
     * Underlying stream **is NOT owned** by the generator constructed, so that generator will NOT close the Reader when
     * [CirJsonGenerator.close] is called (unless auto-closing feature, [StreamWriteFeature.AUTO_CLOSE_TARGET] is
     * enabled). Using application needs to close it explicitly.
     *
     * @param writeContext Object-binding context where applicable; used for providing contextual configuration
     * @param writer Writer to use for writing CirJSON content
     *
     * @return Constructed generator
     *
     * @throws CirJacksonException If there are problems constructing parser
     */
    @Throws(CirJacksonException::class)
    abstract fun createGenerator(writeContext: ObjectWriteContext, writer: Writer): CirJsonGenerator

    /*
     *******************************************************************************************************************
     * Internal factory methods, other
     *******************************************************************************************************************
     */

    /**
     * Value used by factory to create buffer recycler instances to use for parsers and generators.
     *
     * Note: only public to give access for `ObjectMapper`
     */
    val bufferRecycler
        get() = recyclerPool.acquireAndLinkPooled()

    /**
     * Overridable factory method that actually instantiates desired
     * context object.
     *
     * @param contentReference Source reference to use (relevant to `CirJsonLocation` construction)
     * @param isResourceManaged Whether input/output buffers used are managed by this factory
     *
     * @return Context constructed
     */
    protected fun createContext(contentReference: ContentReference, isResourceManaged: Boolean): IOContext {
        return createContext(contentReference, isResourceManaged, null)
    }

    /**
     * Overridable factory method that actually instantiates desired context object.
     *
     * @param contentReference Source reference to use (relevant to `CirJsonLocation` construction)
     * @param isResourceManaged Whether input/output buffers used are managed by this factory
     * @param encoding Character encoding defined to be used/expected
     *
     * @return Context constructed
     */
    protected fun createContext(contentReference: ContentReference?, isResourceManaged: Boolean,
            encoding: CirJsonEncoding?): IOContext {
        val content = contentReference?.rawContent

        var recyclerExternal = false
        val bufferRecycler = (content as? BufferRecycler.Gettable)?.bufferRecycler()?.also { recyclerExternal = true }
                ?: bufferRecycler
        val context = IOContext(streamReadConstraints, streamWriteConstraints, errorReportConfiguration, bufferRecycler,
                contentReference, isResourceManaged, encoding)

        if (recyclerExternal) {
            context.markBufferRecyclerReleased()
        }

        return context
    }

    /**
     * Overridable factory method for constructing [ContentReference] to pass to parser or generator being created; used
     * in cases where no offset or length is applicable (either irrelevant, or full contents assumed).
     *
     * @param contentReference Underlying input source (parser) or target (generator)
     *
     * @return InputSourceReference instance to use
     */
    protected abstract fun createContentReference(contentReference: Any): ContentReference

    /**
     * Overridable factory method for constructing [ContentReference] to pass to parser or generator being created; used
     * in cases where input comes in a static buffer with relevant offset and length.
     *
     * @param contentReference Underlying input source (parser) or target (generator)
     * @param offset Offset of content in buffer (`rawSource`)
     * @param length Length of content in buffer (`rawSource`)
     *
     * @return InputSourceReference instance to use
     */
    protected abstract fun createContentReference(contentReference: Any, offset: Int, length: Int): ContentReference

    /**
     * Method that creates an OutputStream from a DataOutput, so that all writing operations use the DataOutput
     *
     * @param output DataOutput to write to
     */
    protected fun createDataOutputWrapper(output: DataOutput): OutputStream {
        return DataOutputAsStream(output)
    }

    /**
     * Helper method used for constructing an optimal stream for parsers to use, when input is to be read from a URL.
     * This helps when reading file content via URL.
     *
     * @param url Source to read content to parse from
     *
     * @return InputStream constructed for given [URL]
     *
     * @throws IOException If there is a problem accessing content from specified [URL]
     */
    @Throws(IOException::class)
    protected open fun optimizedStreamFromURL(url: URL): InputStream {
        if (url.protocol == "file") {
            val host = url.host

            if (host.isNullOrEmpty()) {
                val path = url.path

                if (path.indexOf('%') < 0) {
                    try {
                        return Files.newInputStream(Paths.get(url.path))
                    } catch (e: IOException) {
                        throw wrapIOFailure(e)
                    }
                }
            }
        }

        try {
            return url.openStream()
        } catch (e: IOException) {
            throw wrapIOFailure(e)
        }
    }

    /**
     * Helper methods used for constructing an [InputStream] for parsers to use, when input is to be read from given
     * [File].
     *
     * @param file File to open stream for
     *
     * @return [InputStream] constructed
     *
     * @throws CirJacksonException If there is a problem opening the stream
     */
    @Throws(CirJacksonException::class)
    protected open fun fileInputStream(file: File): InputStream {
        try {
            return Files.newInputStream(file.toPath())
        } catch (e: IOException) {
            throw wrapIOFailure(e)
        }
    }

    @Throws(CirJacksonException::class)
    protected open fun pathInputStream(path: Path): InputStream {
        try {
            return Files.newInputStream(path)
        } catch (e: IOException) {
            throw wrapIOFailure(e)
        }
    }

    /**
     * Helper methods used for constructing an [OutputStream] for generator to use, when target is to be written into
     * given [File].
     *
     * @param file File to open stream for
     *
     * @return [OutputStream] constructed
     *
     * @throws CirJacksonException If there is a problem opening the stream
     */
    @Throws(CirJacksonException::class)
    protected open fun fileOutputStream(file: File): OutputStream {
        try {
            return Files.newOutputStream(file.toPath())
        } catch (e: IOException) {
            throw wrapIOFailure(e)
        }
    }

    @Throws(CirJacksonException::class)
    protected open fun pathOutputStream(path: Path): OutputStream {
        try {
            return Files.newOutputStream(path)
        } catch (e: IOException) {
            throw wrapIOFailure(e)
        }
    }

    /*
     *******************************************************************************************************************
     * Range check helper methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun checkRangeBoundsForByteArray(data: ByteArray?, offset: Int, len: Int) {
        data ?: return reportRangeError("Invalid `data` argument: `null`")
        checkRange(data.size, offset, len)
    }

    @Throws(CirJacksonException::class)
    private fun checkRange(dataLen: Int, offset: Int, len: Int) {
        val end = offset + len

        // Note: we are checking that:
        //
        // !(offset < 0)
        // !(len < 0)
        // !((offset + len) < 0) // int overflow!
        // !((offset + len) > dataLen) == !((dataLen - (offset+len)) < 0)
        //
        // All can be optimized by doing bitwise OR and checking for negative:
        val anyNegs = offset or len or end or (dataLen - end)

        if (anyNegs < 0) {
            reportRangeError<Nothing>(
                    "Invalid 'offset' ($offset) and/or 'len' ($len) arguments for `data` of length $dataLen")
        }
    }

    @Throws(CirJacksonException::class)
    protected fun checkRangeBoundsForCharArray(data: CharArray?, offset: Int, len: Int) {
        data ?: return reportRangeError("Invalid `data` argument: `null`")
        checkRange(data.size, offset, len)
    }

    /**
     * Method that throws a [StreamReadException] with the given [message]
     *
     * @param message The message of the exception
     *
     * @throws StreamReadException The exception thrown
     */
    @Throws(CirJacksonException::class)
    protected fun <T> reportRangeError(message: String): T {
        throw StreamReadException(null, message)
    }

    /*
     *******************************************************************************************************************
     * Error reporting methods
     *******************************************************************************************************************
     */

    protected fun wrapIOFailure(e: IOException): CirJacksonException {
        return CirJacksonIOException.construct(e, this)
    }

    @Throws(UnsupportedOperationException::class)
    protected fun <T> unsupported(): T {
        return unsupported("Operation not supported for this format ($formatName)")
    }

    @Throws(UnsupportedOperationException::class)
    protected fun <T> unsupported(message: String): T {
        throw UnsupportedOperationException(message)
    }

    enum class Feature(override val isEnabledByDefault: Boolean) : CirJacksonFeature {

        /**
         * Feature that determines whether CirJSON object property names are to be canonicalized using [String.intern]
         * or not.
         *
         * If enabled, all property names will be intern()ed (and caller can count on this being true for all such
         * names). If disabled, no intern()ing is done. There may still be basic canonicalization (that is, same String
         * will be used to represent all identical object property names for a single document).
         *
         * Note: this setting only has effect if [CANONICALIZE_PROPERTY_NAMES] is true -- otherwise no canonicalization
         * of any sort is done.
         *
         * This setting is disabled by default since 3.0 (was enabled in 1.x and 2.x)
         */
        INTERN_PROPERTY_NAMES(false),

        /**
         * Feature that determines whether JSON object property names are to be canonicalized (details of how
         * canonicalization is done then further specified by [INTERN_PROPERTY_NAMES]).
         *
         * This setting is enabled by default.
         */
        CANONICALIZE_PROPERTY_NAMES(true),

        /**
         * Feature that determines what happens if we encounter a case in symbol handling where number of hash
         * collisions exceeds a safety threshold -- which almost certainly means a denial-of-service attack via
         * generated duplicate hash codes.
         *
         * If feature is enabled, an [IllegalStateException] is thrown to indicate the suspected denial-of-service
         * attack; if disabled, processing continues but canonicalization (and thereby `intern()`ing) is disabled as
         * protective measure.
         *
         * This setting is enabled by default.
         */
        FAIL_ON_SYMBOL_HASH_OVERFLOW(true),

        /**
         * Feature to control charset detection for byte-based inputs (`byte[]`, [InputStream]...). When this feature is
         * enabled (the default), the factory will allow UTF-16 and UTF-32 inputs and try to detect them, as specified
         * by RFC 4627. When this feature is disabled the factory will assume UTF-8, as specified by RFC 8259.
         *
         * NOTE: only applies to some implementations: most notable for `JsonFactory`.
         */
        CHARSET_DETECTION(true);

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

    companion object {

        /**
         * Bitfield (set of flags) of all factory features that are enabled by default.
         */
        val DEFAULT_FACTORY_FEATURE_FLAGS = Feature.collectDefaults()

        /**
         * Bitfield (set of flags) of all factory features that are enabled by default.
         */
        val DEFAULT_STREAM_READ_FEATURE_FLAGS = StreamReadFeature.collectDefaults()

        /**
         * Bitfield (set of flags) of all factory features that are enabled by default.
         */
        val DEFAULT_STREAM_WRITE_FEATURE_FLAGS = StreamWriteFeature.collectDefaults()

    }

}