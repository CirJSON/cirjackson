package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.exception.CirJacksonIOException
import org.cirjson.cirjackson.core.io.CharacterEscapes
import org.cirjson.cirjackson.core.io.SegmentedStringWriter
import org.cirjson.cirjackson.core.type.TypeReference
import org.cirjson.cirjackson.core.util.ByteArrayBuilder
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.configuration.*
import org.cirjson.cirjackson.databind.node.ArrayNode
import org.cirjson.cirjackson.databind.node.ObjectNode
import org.cirjson.cirjackson.databind.serialization.FilterProvider
import org.cirjson.cirjackson.databind.serialization.SerializationContextExtended
import org.cirjson.cirjackson.databind.serialization.implementation.TypeWrappedSerializer
import org.cirjson.cirjackson.databind.type.TypeFactory
import org.cirjson.cirjackson.databind.util.closeOnFailAndThrowAsCirJacksonException
import java.io.*
import java.nio.file.Path
import java.text.DateFormat
import java.util.*
import kotlin.reflect.KClass

/**
 * Builder object that can be used for per-serialization configuration of serialization parameters, such as CirJSON View
 * and root type to use. (and thus fully thread-safe with no external synchronization); new instances are constructed
 * for different configurations. Instances are initially constructed by [ObjectMapper] and can be reused in completely
 * thread-safe manner with no explicit synchronization
 */
open class ObjectWriter : Versioned {

    /*
     *******************************************************************************************************************
     * Immutable configuration from ObjectMapper
     *******************************************************************************************************************
     */

    /**
     * General serialization configuration settings
     */
    protected val myConfig: SerializationConfig

    /**
     * Factory used for constructing per-call [SerializerProviders][SerializerProvider].
     * 
     * Note: while serializers are only exposed [SerializerProvider], mappers and readers need to access additional API
     * defined by [SerializationContextExtended]
     */
    protected val mySerializationContexts: SerializationContexts

    /**
     * Factory used for constructing [CirJsonGenerators][CirJsonGenerator]
     */
    protected val myGeneratorFactory: TokenStreamFactory

    /*
     *******************************************************************************************************************
     * Configuration that can be changed via mutant factories
     *******************************************************************************************************************
     */

    /**
     * Container for settings that need to be passed to [CirJsonGenerator] constructed for serializing values.
     */
    protected val myGeneratorSettings: GeneratorSettings

    /**
     * We may pre-fetch serializer if root type is known (has been explicitly declared), and if so, reuse it afterward.
     * This allows avoiding further serializer lookups and increases performance a bit on cases where readers are
     * reused.
     */
    protected val myPrefetch: Prefetch

    /*
     *******************************************************************************************************************
     * Lifecycle, constructors
     *******************************************************************************************************************
     */

    protected constructor(mapper: ObjectMapper, config: SerializationConfig, rootType: KotlinType?,
            prettyPrinter: PrettyPrinter?) {
        myConfig = config

        mySerializationContexts = mapper.serializationContexts()
        myGeneratorFactory = mapper.streamFactory()

        myGeneratorSettings = prettyPrinter?.let { GeneratorSettings(it, null, null, null) } ?: GeneratorSettings.EMPTY

        myPrefetch = if (rootType == null) {
            Prefetch.EMPTY
        } else if (rootType.hasRawClass(Any::class)) {
            Prefetch.EMPTY.forRootType(this, rootType)
        } else {
            Prefetch.EMPTY.forRootType(this, rootType.withStaticTyping())
        }
    }

    /**
     * Alternative constructor for initial instantiation by [ObjectMapper]
     */
    protected constructor(mapper: ObjectMapper, config: SerializationConfig) {
        myConfig = config

        mySerializationContexts = mapper.serializationContexts()
        myGeneratorFactory = mapper.streamFactory()

        myGeneratorSettings = GeneratorSettings.EMPTY
        myPrefetch = Prefetch.EMPTY
    }

    /**
     * Alternative constructor for initial instantiation by [ObjectMapper]
     */
    protected constructor(mapper: ObjectMapper, config: SerializationConfig, schema: FormatSchema?) {
        myConfig = config

        mySerializationContexts = mapper.serializationContexts()
        myGeneratorFactory = mapper.streamFactory()

        myGeneratorSettings = schema?.let { GeneratorSettings(null, it, null, null) } ?: GeneratorSettings.EMPTY
        myPrefetch = Prefetch.EMPTY
    }

    /**
     * Copy constructor used for building variations.
     */
    protected constructor(base: ObjectWriter, config: SerializationConfig, generatorSettings: GeneratorSettings,
            prefetch: Prefetch) {
        myConfig = config

        mySerializationContexts = base.mySerializationContexts
        myGeneratorFactory = base.myGeneratorFactory

        myGeneratorSettings = generatorSettings
        myPrefetch = prefetch
    }

    /**
     * Copy constructor used for building variations.
     */
    protected constructor(base: ObjectWriter, config: SerializationConfig) {
        myConfig = config

        mySerializationContexts = base.mySerializationContexts
        myGeneratorFactory = base.myGeneratorFactory

        myGeneratorSettings = base.myGeneratorSettings
        myPrefetch = base.myPrefetch
    }

    /**
     * Method that will return version information stored in and read from jar that contains this class.
     */
    override fun version(): Version {
        return PackageVersion.VERSION
    }

    /*
     *******************************************************************************************************************
     * Helper methods to simplify mutant-factory implementation
     *******************************************************************************************************************
     */

    /**
     * Overridable factory method called by various `withXxx()` methods.
     */
    protected open fun new(base: ObjectWriter, config: SerializationConfig): ObjectWriter {
        if (config === myConfig) {
            return this
        }

        return ObjectWriter(base, config)
    }

    /**
     * Overridable factory method called by various `withXxx()` methods. It assumes `this` as base for settings other
     * than those directly passed in.
     */
    protected open fun new(generatorSettings: GeneratorSettings, prefetch: Prefetch): ObjectWriter {
        if (myGeneratorSettings === generatorSettings && myPrefetch === prefetch) {
            return this
        }

        return ObjectWriter(this, myConfig, generatorSettings, prefetch)
    }

    /**
     * Overridable factory method called by [writeValues] method (and its various overrides), and initializes it as
     * necessary.
     */
    @Throws(CirJacksonException::class)
    protected fun newSequenceWriter(context: SerializationContextExtended, wrapInArray: Boolean,
            generator: CirJsonGenerator, managedInput: Boolean): SequenceWriter {
        return SequenceWriter(context, generator, managedInput, myPrefetch).init(wrapInArray)
    }

    /*
     *******************************************************************************************************************
     * Lifecycle, fluent factories for SerializationFeature
     *******************************************************************************************************************
     */

    /**
     * Method for constructing a new instance that is configured with specified feature enabled.
     */
    open fun with(feature: SerializationFeature): ObjectWriter {
        return new(this, myConfig.with(feature))
    }

    /**
     * Method for constructing a new instance that is configured with specified features enabled.
     */
    open fun with(feature: SerializationFeature, vararg others: SerializationFeature): ObjectWriter {
        return new(this, myConfig.with(feature, *others))
    }

    /**
     * Method for constructing a new instance that is configured with specified features enabled.
     */
    open fun withFeatures(vararg features: SerializationFeature): ObjectWriter {
        return new(this, myConfig.withFeatures(*features))
    }

    /**
     * Method for constructing a new instance that is configured with specified feature disabled.
     */
    open fun without(feature: SerializationFeature): ObjectWriter {
        return new(this, myConfig.without(feature))
    }

    /**
     * Method for constructing a new instance that is configured with specified features disabled.
     */
    open fun without(feature: SerializationFeature, vararg others: SerializationFeature): ObjectWriter {
        return new(this, myConfig.without(feature, *others))
    }

    /**
     * Method for constructing a new instance that is configured with specified features disabled.
     */
    open fun withoutFeatures(vararg features: SerializationFeature): ObjectWriter {
        return new(this, myConfig.withoutFeatures(*features))
    }

    /*
     *******************************************************************************************************************
     * Lifecycle, fluent factories for DatatypeFeature
     *******************************************************************************************************************
     */

    /**
     * Method for constructing a new instance that is configured with specified feature enabled.
     */
    open fun with(feature: DatatypeFeature): ObjectWriter {
        return new(this, myConfig.with(feature))
    }

    /**
     * Method for constructing a new instance that is configured with specified features enabled.
     */
    open fun withFeatures(vararg features: DatatypeFeature): ObjectWriter {
        return new(this, myConfig.withFeatures(*features))
    }

    /**
     * Method for constructing a new instance that is configured with specified feature disabled.
     */
    open fun without(feature: DatatypeFeature): ObjectWriter {
        return new(this, myConfig.without(feature))
    }

    /**
     * Method for constructing a new instance that is configured with specified features disabled.
     */
    open fun withoutFeatures(vararg features: DatatypeFeature): ObjectWriter {
        return new(this, myConfig.withoutFeatures(*features))
    }

    /*
     *******************************************************************************************************************
     * Lifecycle, fluent factories for StreamWriteFeature
     *******************************************************************************************************************
     */

    /**
     * Method for constructing a new instance that is configured with specified feature enabled.
     */
    open fun with(feature: StreamWriteFeature): ObjectWriter {
        return new(this, myConfig.with(feature))
    }

    /**
     * Method for constructing a new instance that is configured with specified features enabled.
     */
    open fun withFeatures(vararg features: StreamWriteFeature): ObjectWriter {
        return new(this, myConfig.withFeatures(*features))
    }

    /**
     * Method for constructing a new instance that is configured with specified feature disabled.
     */
    open fun without(feature: StreamWriteFeature): ObjectWriter {
        return new(this, myConfig.without(feature))
    }

    /**
     * Method for constructing a new instance that is configured with specified features disabled.
     */
    open fun withoutFeatures(vararg features: StreamWriteFeature): ObjectWriter {
        return new(this, myConfig.withoutFeatures(*features))
    }

    /*
     *******************************************************************************************************************
     * Lifecycle, fluent factories for FormatFeature
     *******************************************************************************************************************
     */

    /**
     * Method for constructing a new instance that is configured with specified feature enabled.
     */
    open fun with(feature: FormatFeature): ObjectWriter {
        return new(this, myConfig.with(feature))
    }

    /**
     * Method for constructing a new instance that is configured with specified features enabled.
     */
    open fun withFeatures(vararg features: FormatFeature): ObjectWriter {
        return new(this, myConfig.withFeatures(*features))
    }

    /**
     * Method for constructing a new instance that is configured with specified feature disabled.
     */
    open fun without(feature: FormatFeature): ObjectWriter {
        return new(this, myConfig.without(feature))
    }

    /**
     * Method for constructing a new instance that is configured with specified features disabled.
     */
    open fun withoutFeatures(vararg features: FormatFeature): ObjectWriter {
        return new(this, myConfig.withoutFeatures(*features))
    }

    /*
     *******************************************************************************************************************
     * Lifecycle, fluent factories, type-related
     *******************************************************************************************************************
     */

    /**
     * Method that will construct a new instance that uses specific type as the root type for serialization, instead of
     * runtime dynamic type of the root object itself.
     *
     * Note that method does NOT change state of this reader, but rather construct and returns a newly configured
     * instance.
     */
    open fun forType(rootType: KotlinType): ObjectWriter {
        return new(myGeneratorSettings, myPrefetch.forRootType(this, rootType))
    }

    /**
     * Method that will construct a new instance that uses specific type as the root type for serialization, instead of
     * runtime dynamic type of the root object itself.
     *
     * Note that method does NOT change state of this reader, but rather construct and returns a newly configured
     * instance.
     */
    open fun forType(rootType: KClass<*>): ObjectWriter {
        return forType(myConfig.constructType(rootType))
    }

    /**
     * Method that will construct a new instance that uses specific type as the root type for serialization, instead of
     * runtime dynamic type of the root object itself.
     *
     * Note that method does NOT change state of this reader, but rather construct and returns a newly configured
     * instance.
     */
    open fun forType(rootType: TypeReference<*>): ObjectWriter {
        return forType(myConfig.typeFactory.constructType(rootType.type))
    }

    /*
     *******************************************************************************************************************
     * Lifecycle, fluent factories, other
     *******************************************************************************************************************
     */

    /**
     * Fluent factory method that will construct a new writer instance that will use specified date format for
     * serializing dates.
     * 
     * Note that the method does NOT change state of this reader, but rather construct and returns a newly configured
     * instance.
     */
    open fun with(dateFormat: DateFormat): ObjectWriter {
        return new(this, myConfig.with(dateFormat))
    }

    /**
     * Method that will construct a new instance that will use the default pretty printer for serialization.
     */
    open fun withDefaultPrettyPrinter(): ObjectWriter {
        return with(myConfig.defaultPrettyPrinter)
    }

    /**
     * Method that will construct a new instance that uses specified provider for resolving filter instances by id.
     */
    open fun with(filterProvider: FilterProvider?): ObjectWriter {
        if (filterProvider === myConfig.filterProvider) {
            return this
        }

        return new(this, myConfig.withFilters(filterProvider))
    }

    /**
     * Method that will construct a new instance that will use specified pretty printer (or, if `null`, will not do any
     * pretty-printing)
     */
    open fun with(prettyPrinter: PrettyPrinter?): ObjectWriter {
        return new(myGeneratorSettings.with(prettyPrinter), myPrefetch)
    }

    /**
     * Method for constructing a new instance with configuration that specifies what root name to use for "root element
     * wrapping". See [SerializationConfig.withRootName] for details.
     * 
     * Note that method does NOT change state of this reader, but rather construct and returns a newly configured
     * instance.
     *
     * @param rootName Root name to use, if non-empty; `null` for "use defaults", and empty String (`""`) for "do NOT
     * add root wrapper"
     */
    open fun withRootName(rootName: String): ObjectWriter {
        return new(this, myConfig.withRootName(rootName))
    }

    /**
     * Method for constructing a new instance with configuration that specifies what root name to use for "root element
     * wrapping". See [SerializationConfig.withRootName] for details.
     * 
     * Note that method does NOT change state of this reader, but rather construct and returns a newly configured
     * instance.
     *
     * @param rootName Root name to use, if non-empty; `null` for "use defaults", and empty String (`""`) for "do NOT
     * add root wrapper"
     */
    open fun withRootName(rootName: PropertyName): ObjectWriter {
        return new(this, myConfig.withRootName(rootName))
    }

    /**
     * Convenience method that is same as calling:`withRootName(PropertyName.NO_NAME)` which will forcibly prevent use
     * of root name wrapping when writing values with this [ObjectWriter].
     */
    open fun withoutRootName(): ObjectWriter {
        return new(this, myConfig.withRootName(PropertyName.NO_NAME))
    }

    /**
     * Method that will construct a new instance that uses specific format schema for serialization.
     * 
     * Note that method does NOT change state of this reader, but rather construct and returns a newly configured
     * instance.
     */
    open fun with(schema: FormatSchema?): ObjectWriter {
        verifySchemaType(schema)
        return new(myGeneratorSettings.with(schema), myPrefetch)
    }

    /**
     * Method that will construct a new instance that uses specified serialization view for serialization (with `null`
     * basically disables view processing)
     * 
     * Note that the method does NOT change state of this reader, but rather construct and returns a newly configured
     * instance.
     */
    open fun withView(view: KClass<*>?): ObjectWriter {
        return new(this, myConfig.withView(view))
    }

    open fun with(locale: Locale): ObjectWriter {
        return new(this, myConfig.with(locale))
    }

    open fun with(timeZone: TimeZone): ObjectWriter {
        return new(this, myConfig.with(timeZone))
    }

    /**
     * Method that will construct a new instance that uses specified default [Base64Variant] for base64 encoding
     */
    open fun with(base64Variant: Base64Variant): ObjectWriter {
        return new(this, myConfig.with(base64Variant))
    }

    open fun with(escapes: CharacterEscapes?): ObjectWriter {
        return new(myGeneratorSettings.with(escapes), myPrefetch)
    }

    /**
     * Mutant factory for overriding set of (default) attributes for [ObjectWriter] to use.
     * 
     * Note that this will replace defaults passed by [ObjectMapper].
     *
     * @param attributes Default [ContextAttributes] to use with a writer
     *
     * @return [ObjectWriter] instance with specified default attributes (which is usually a newly constructed writer
     * instance with otherwise identical settings)
     */
    open fun with(attributes: ContextAttributes): ObjectWriter {
        return new(this, myConfig.with(attributes))
    }

    /**
     * Mutant factory method that allows construction of a new writer instance that uses specified set of default
     * attribute values.
     */
    open fun withAttributes(attributes: Map<*, *>): ObjectWriter {
        return new(this, myConfig.withAttributes(attributes))
    }

    open fun withAttribute(key: Any, value: Any): ObjectWriter {
        return new(this, myConfig.withAttribute(key, value))
    }

    open fun withoutAttribute(key: Any): ObjectWriter {
        return new(this, myConfig.withoutAttribute(key))
    }

    open fun withRootValueSeparator(separator: String): ObjectWriter {
        return new(myGeneratorSettings.withRootValueSeparator(separator), myPrefetch)
    }

    open fun withRootValueSeparator(separator: SerializableString): ObjectWriter {
        return new(myGeneratorSettings.withRootValueSeparator(separator), myPrefetch)
    }

    /*
     *******************************************************************************************************************
     * Public API: constructing Generator that are properly linked to `ObjectWriteContext`
     *******************************************************************************************************************
     */

    /**
     * Factory method for constructing [CirJsonGenerator] that is properly wired to allow callbacks for serialization:
     * basically constructs a [ObjectWriteContext] and then calls the related [TokenStreamFactory.createGenerator].
     */
    open fun createGenerator(target: OutputStream): CirJsonGenerator {
        return myGeneratorFactory.createGenerator(serializerProvider(), target)
    }

    /**
     * Factory method for constructing [CirJsonGenerator] that is properly wired to allow callbacks for serialization:
     * basically constructs a [ObjectWriteContext] and then calls the related [TokenStreamFactory.createGenerator].
     */
    open fun createGenerator(target: OutputStream, encoding: CirJsonEncoding): CirJsonGenerator {
        return myGeneratorFactory.createGenerator(serializerProvider(), target, encoding)
    }

    /**
     * Factory method for constructing [CirJsonGenerator] that is properly wired to allow callbacks for serialization:
     * basically constructs a [ObjectWriteContext] and then calls the related [TokenStreamFactory.createGenerator].
     */
    open fun createGenerator(target: Writer): CirJsonGenerator {
        return myGeneratorFactory.createGenerator(serializerProvider(), target)
    }

    /**
     * Factory method for constructing [CirJsonGenerator] that is properly wired to allow callbacks for serialization:
     * basically constructs a [ObjectWriteContext] and then calls the related [TokenStreamFactory.createGenerator].
     */
    open fun createGenerator(target: File, encoding: CirJsonEncoding): CirJsonGenerator {
        return myGeneratorFactory.createGenerator(serializerProvider(), target, encoding)
    }

    /**
     * Factory method for constructing [CirJsonGenerator] that is properly wired to allow callbacks for serialization:
     * basically constructs a [ObjectWriteContext] and then calls the related [TokenStreamFactory.createGenerator].
     */
    open fun createGenerator(target: Path, encoding: CirJsonEncoding): CirJsonGenerator {
        return myGeneratorFactory.createGenerator(serializerProvider(), target, encoding)
    }

    /**
     * Factory method for constructing [CirJsonGenerator] that is properly wired to allow callbacks for serialization:
     * basically constructs a [ObjectWriteContext] and then calls the related [TokenStreamFactory.createGenerator].
     */
    open fun createGenerator(target: DataOutput): CirJsonGenerator {
        return myGeneratorFactory.createGenerator(serializerProvider(), target)
    }

    /*
     *******************************************************************************************************************
     * Convenience methods for CirJsonNode creation
     *******************************************************************************************************************
     */

    open fun createObjectNode(): ObjectNode {
        return myConfig.nodeFactory.objectNode()
    }

    open fun createArrayNode(): ArrayNode {
        return myConfig.nodeFactory.arrayNode()
    }

    /*
     *******************************************************************************************************************
     * Factory methods for sequence writers
     *******************************************************************************************************************
     */

    /**
     * Method for creating a [SequenceWriter] to write a sequence of root values using configuration of this
     * [ObjectWriter]. Sequence is not surrounded by CirJSON array; some backend types may not support writing of such
     * sequences as root level. Resulting writer needs to be [closed][SequenceWriter.close] after all values have been
     * written to ensure closing of underlying generator and output stream.
     *
     * @param target Target file to write value sequence to.
     */
    @Throws(CirJacksonException::class)
    open fun writeValues(target: File): SequenceWriter {
        val context = serializerProvider()
        return newSequenceWriter(context, false, createGenerator(target, CirJsonEncoding.UTF8), true)
    }

    /**
     * Method for creating a [SequenceWriter] to write a sequence of root values using configuration of this
     * [ObjectWriter]. Sequence is not surrounded by CirJSON array; some backend types may not support writing of such
     * sequences as root level. Resulting writer needs to be [closed][SequenceWriter.close] after all values have been
     * written to ensure closing of underlying generator and output stream.
     *
     * @param target Target path to write value sequence to.
     */
    @Throws(CirJacksonException::class)
    open fun writeValues(target: Path): SequenceWriter {
        val context = serializerProvider()
        return newSequenceWriter(context, false, createGenerator(target, CirJsonEncoding.UTF8), true)
    }

    /**
     * Method for creating a [SequenceWriter] to write a sequence of root values using configuration of this
     * [ObjectWriter]. Sequence is not surrounded by CirJSON array; some backend types may not support writing of such
     * sequences as root level. Resulting writer needs to be [closed][SequenceWriter.close] after all values have been
     * written to ensure that all content gets flushed by the generator. However, since a [CirJsonGenerator] is
     * explicitly passed, it will NOT be closed when [SequenceWriter.close] is called.
     *
     * @param generator Low-level generator caller has already constructed that will be used for actual writing of token
     * stream.
     */
    @Throws(CirJacksonException::class)
    open fun writeValues(generator: CirJsonGenerator): SequenceWriter {
        val context = serializerProvider()
        return newSequenceWriter(context, false, generator, false)
    }

    /**
     * Method for creating a [SequenceWriter] to write a sequence of root values using configuration of this
     * [ObjectWriter]. Sequence is not surrounded by CirJSON array; some backend types may not support writing of such
     * sequences as root level. Resulting writer needs to be [closed][SequenceWriter.close] after all values have been
     * written to ensure closing of underlying generator and output stream.
     *
     * @param target Target writer to use for writing the token stream
     */
    @Throws(CirJacksonException::class)
    open fun writeValues(target: Writer): SequenceWriter {
        val context = serializerProvider()
        return newSequenceWriter(context, false, createGenerator(target), true)
    }

    /**
     * Method for creating a [SequenceWriter] to write a sequence of root values using configuration of this
     * [ObjectWriter]. Sequence is not surrounded by CirJSON array; some backend types may not support writing of such
     * sequences as root level. Resulting writer needs to be [closed][SequenceWriter.close] after all values have been
     * written to ensure closing of underlying generator and output stream.
     *
     * @param target Physical output stream to use for writing the token stream
     */
    @Throws(CirJacksonException::class)
    open fun writeValues(target: OutputStream): SequenceWriter {
        val context = serializerProvider()
        return newSequenceWriter(context, false, createGenerator(target, CirJsonEncoding.UTF8), true)
    }

    /**
     * Method for creating a [SequenceWriter] to write a sequence of root values using configuration of this
     * [ObjectWriter]. Sequence is not surrounded by CirJSON array; some backend types may not support writing of such
     * sequences as root level. Resulting writer needs to be [closed][SequenceWriter.close] after all values have been
     * written to ensure closing of underlying generator and output stream.
     *
     * @param target Target data output to use for writing the token stream
     */
    @Throws(CirJacksonException::class)
    open fun writeValues(target: DataOutput): SequenceWriter {
        val context = serializerProvider()
        return newSequenceWriter(context, false, createGenerator(target), true)
    }

    /**
     * Method for creating a [SequenceWriter] to write an array of root-level values, using configuration of this
     * [ObjectWriter]. Resulting writer needs to be [closed][SequenceWriter.close] after all values have been written to
     * ensure closing of underlying generator and output stream.
     * 
     * Note that the type to use with [ObjectWriter.forType] needs to be type of individual values (elements) to write
     * and NOT matching array or [Collection] type.
     *
     * @param target File to write token stream to
     */
    @Throws(CirJacksonException::class)
    open fun writeValuesAsArray(target: File): SequenceWriter {
        val context = serializerProvider()
        return newSequenceWriter(context, true, createGenerator(target, CirJsonEncoding.UTF8), true)
    }

    /**
     * Method for creating a [SequenceWriter] to write an array of root-level values, using configuration of this
     * [ObjectWriter]. Resulting writer needs to be [closed][SequenceWriter.close] after all values have been written to
     * ensure closing of underlying generator and output stream.
     * 
     * Note that the type to use with [ObjectWriter.forType] needs to be type of individual values (elements) to write
     * and NOT matching array or [Collection] type.
     *
     * @param target Path to write token stream to
     */
    @Throws(CirJacksonException::class)
    open fun writeValuesAsArray(target: Path): SequenceWriter {
        val context = serializerProvider()
        return newSequenceWriter(context, true, createGenerator(target, CirJsonEncoding.UTF8), true)
    }

    /**
     * Method for creating a [SequenceWriter] to write an array of root-level values, using configuration of this
     * [ObjectWriter]. Resulting writer needs to be [closed][SequenceWriter.close] after all values have been written to
     * ensure that all content gets flushed by the generator. However, since a [CirJsonGenerator] is explicitly passed,
     * it will NOT be closed when [SequenceWriter.close] is called.
     * 
     * Note that the type to use with [ObjectWriter.forType] needs to be type of individual values (elements) to write
     * and NOT matching array or [Collection] type.
     *
     * @param generator Underlying generator to use for writing the token stream
     */
    @Throws(CirJacksonException::class)
    open fun writeValuesAsArray(generator: CirJsonGenerator): SequenceWriter {
        val context = serializerProvider()
        return newSequenceWriter(context, true, generator, false)
    }

    /**
     * Method for creating a [SequenceWriter] to write an array of root-level values, using configuration of this
     * [ObjectWriter]. Resulting writer needs to be [closed][SequenceWriter.close] after all values have been written to
     * ensure closing of underlying generator and output stream.
     * 
     * Note that the type to use with [ObjectWriter.forType] needs to be type of individual values (elements) to write
     * and NOT matching array or [Collection] type.
     *
     * @param target Writer to use for writing the token stream
     */
    @Throws(CirJacksonException::class)
    open fun writeValuesAsArray(target: Writer): SequenceWriter {
        val context = serializerProvider()
        return newSequenceWriter(context, true, createGenerator(target), true)
    }

    /**
     * Method for creating a [SequenceWriter] to write an array of root-level values, using configuration of this
     * [ObjectWriter]. Resulting writer needs to be [closed][SequenceWriter.close] after all values have been written to
     * ensure closing of underlying generator and output stream.
     * 
     * Note that the type to use with [ObjectWriter.forType] needs to be type of individual values (elements) to write
     * and NOT matching array or [Collection] type.
     *
     * @param target Physical output stream to use for writing the token stream
     */
    @Throws(CirJacksonException::class)
    open fun writeValuesAsArray(target: OutputStream): SequenceWriter {
        val context = serializerProvider()
        return newSequenceWriter(context, true, createGenerator(target, CirJsonEncoding.UTF8), true)
    }

    /**
     * Method for creating a [SequenceWriter] to write an array of root-level values, using configuration of this
     * [ObjectWriter]. Resulting writer needs to be [closed][SequenceWriter.close] after all values have been written to
     * ensure closing of underlying generator and output stream.
     * 
     * Note that the type to use with [ObjectWriter.forType] needs to be type of individual values (elements) to write
     * and NOT matching array or [Collection] type.
     *
     * @param target Data output to use for writing the token stream
     */
    @Throws(CirJacksonException::class)
    open fun writeValuesAsArray(target: DataOutput): SequenceWriter {
        val context = serializerProvider()
        return newSequenceWriter(context, true, createGenerator(target), true)
    }

    /*
     *******************************************************************************************************************
     * Simple accessors
     *******************************************************************************************************************
     */

    open fun isEnabled(feature: SerializationFeature): Boolean {
        return myConfig.isEnabled(feature)
    }

    open fun isEnabled(feature: DatatypeFeature): Boolean {
        return myConfig.isEnabled(feature)
    }

    open fun isEnabled(feature: MapperFeature): Boolean {
        return myConfig.isEnabled(feature)
    }

    open fun isEnabled(feature: StreamWriteFeature): Boolean {
        return myConfig.isEnabled(feature)
    }

    open val config: SerializationConfig
        get() = myConfig

    open fun generatorFactory(): TokenStreamFactory {
        return myGeneratorFactory
    }

    open fun typeFactory(): TypeFactory {
        return myConfig.typeFactory
    }

    /**
     * Diagnostics method that can be called to check whether this writer has pre-fetched serializer to use:
     * pre-fetching improves performance when writer instances are reused as it avoids a per-call serializer lookup.
     */
    open fun hasPrefetchedSerializer(): Boolean {
        return myPrefetch.hasSerializer()
    }

    open val attributes: ContextAttributes
        get() = myConfig.attributes

    /*
     *******************************************************************************************************************
     * Serialization methods, ones from ObjectCodec first
     *******************************************************************************************************************
     */

    /**
     * Method that can be used to serialize any value as CirJSON output, using provided [CirJsonGenerator].
     */
    @Throws(CirJacksonException::class)
    open fun writeValue(generator: CirJsonGenerator, value: Any?) {
        if (myConfig.isEnabled(SerializationFeature.CLOSE_CLOSEABLE) && value is Closeable) {
            try {
                myPrefetch.serialize(generator, value, serializerProvider())

                if (myConfig.isEnabled(SerializationFeature.FLUSH_AFTER_WRITE_VALUE)) {
                    generator.flush()
                }
            } catch (e: Exception) {
                closeOnFailAndThrowAsCirJacksonException(null, value, e)
                return
            }

            try {
                value.close()
            } catch (e: IOException) {
                throw CirJacksonIOException.construct(e, generator)
            }
        } else {
            myPrefetch.serialize(generator, value, serializerProvider())

            if (myConfig.isEnabled(SerializationFeature.FLUSH_AFTER_WRITE_VALUE)) {
                generator.flush()
            }
        }
    }

    /*
     *******************************************************************************************************************
     * Serialization methods, others
     *******************************************************************************************************************
     */

    /**
     * Method that can be used to serialize any value as CirJSON output, written to File provided.
     */
    @Throws(CirJacksonException::class)
    open fun writeValue(target: File, value: Any?) {
        val context = serializerProvider()
        configAndWriteValue(context, createGenerator(target, CirJsonEncoding.UTF8), value)
    }

    /**
     * Method that can be used to serialize any value as CirJSON output, written to Path provided.
     */
    @Throws(CirJacksonException::class)
    open fun writeValue(target: Path, value: Any?) {
        val context = serializerProvider()
        configAndWriteValue(context, createGenerator(target, CirJsonEncoding.UTF8), value)
    }

    /**
     * Method that can be used to serialize any value as CirJSON output, using output stream provided (using encoding
     * [CirJsonEncoding.UTF8]).
     * 
     * Note: method does not close the underlying stream explicitly here; however, [TokenStreamFactory] this mapper uses
     * may choose to close the stream depending on its settings (by default, it will try to close it when
     * [CirJsonGenerator] we construct is closed).
     */
    @Throws(CirJacksonException::class)
    open fun writeValue(target: OutputStream, value: Any?) {
        val context = serializerProvider()
        configAndWriteValue(context, createGenerator(target, CirJsonEncoding.UTF8), value)
    }

    /**
     * Method that can be used to serialize any value as CirJSON output, using Writer provided.
     * 
     * Note: method does not close the underlying stream explicitly here; however, [TokenStreamFactory] this mapper uses
     * may choose to close the stream depending on its settings (by default, it will try to close it when
     * [CirJsonGenerator] we construct is closed).
     */
    @Throws(CirJacksonException::class)
    open fun writeValue(target: Writer, value: Any?) {
        val context = serializerProvider()
        configAndWriteValue(context, createGenerator(target), value)
    }

    /**
     * Method that can be used to serialize any value as CirJSON output, using DataOutput provided.
     * 
     * Note: method does not close the underlying stream explicitly here; however, [TokenStreamFactory] this mapper uses
     * may choose to close the stream depending on its settings (by default, it will try to close it when
     * [CirJsonGenerator] we construct is closed).
     */
    @Throws(CirJacksonException::class)
    open fun writeValue(target: DataOutput, value: Any?) {
        val context = serializerProvider()
        configAndWriteValue(context, createGenerator(target), value)
    }

    /**
     * Method that can be used to serialize any value as a String. Functionally equivalent to calling [writeValue] with
     * [StringWriter] and constructing String, but more efficient.
     */
    @Throws(CirJacksonException::class)
    open fun writeValueAsString(value: Any?): String {
        val bufferRecycler = myGeneratorFactory.bufferRecycler

        try {
            SegmentedStringWriter(bufferRecycler).use {
                val context = serializerProvider()
                configAndWriteValue(context, createGenerator(it), value)
                return it.contentAndClear
            }
        } finally {
            bufferRecycler.releaseToPool()
        }
    }

    /**
     * Method that can be used to serialize any value as a ByteArray. Functionally equivalent to calling [writeValue]
     * with [ByteArrayOutputStream] and getting bytes, but more efficient. Encoding used will be UTF-8.
     */
    @Throws(CirJacksonException::class)
    open fun writeValueAsBytes(value: Any?): ByteArray {
        val bufferRecycler = myGeneratorFactory.bufferRecycler

        try {
            ByteArrayBuilder(bufferRecycler).use {
                val context = serializerProvider()
                configAndWriteValue(context, createGenerator(it, CirJsonEncoding.UTF8), value)
                return it.getClearAndRelease()
            }
        } finally {
            bufferRecycler.releaseToPool()
        }
    }

    /**
     * Method called to configure the generator as necessary and then call write functionality
     */
    @Throws(CirJacksonException::class)
    protected fun configAndWriteValue(context: SerializationContextExtended, generator: CirJsonGenerator, value: Any?) {
        if (myConfig.isEnabled(SerializationFeature.CLOSE_CLOSEABLE) && value is Closeable) {
            writeCloseable(generator, value)
            return
        }

        try {
            myPrefetch.serialize(generator, value, context)
        } catch (e: Exception) {
            closeOnFailAndThrowAsCirJacksonException(generator, e)
            return
        }

        generator.close()
    }

    /**
     * Helper method used when value to serialize is [Closeable] and its `close()` method is to be called right after
     * serialization has been called
     */
    @Throws(CirJacksonException::class)
    private fun writeCloseable(generator: CirJsonGenerator, value: Any) {
        var toClose = value as Closeable?

        try {
            myPrefetch.serialize(generator, value, serializerProvider())
            val tempTpClose = toClose!!
            toClose = null
            tempTpClose.close()
        } catch (e: Exception) {
            closeOnFailAndThrowAsCirJacksonException(generator, toClose, e)
            return
        }

        generator.close()
    }

    /*
     *******************************************************************************************************************
     * Serialization-like methods
     *******************************************************************************************************************
     */

    /**
     * Method that will convert given Java value (usually bean) into its equivalent Tree model [CirJsonNode]
     * representation. Functionally similar to serializing value into token stream and parsing that stream back as tree
     * model node, but more efficient as [org.cirjson.cirjackson.databind.util.TokenBuffer] is used to contain the
     * intermediate representation instead of fully serialized contents.
     * 
     * NOTE: while results are usually identical to that of serialization followed by deserialization, this is not
     * always the case. In some cases serialization into intermediate representation will retain encapsulation of things
     * like raw value ([org.cirjson.cirjackson.databind.util.RawValue]) or basic node identity ([CirJsonNode]). If so,
     * result is a valid tree, but values are not re-constructed through actual format representation. So if
     * transformation requires actual materialization of encoded content, it will be necessary to do actual
     * serialization.
     *
     * @param T Actual node type; usually either basic [CirJsonNode] or [ObjectNode]
     *  
     * @param fromValue value to convert
     *
     * @return (non-`null`) Root node of the resulting content tree: in case of `null` value node for which
     * [CirJsonNode.isNull] returns `true`.
     */
    @Throws(CirJacksonException::class)
    open fun <T : CirJsonNode> valueToTree(fromValue: Any?): T {
        return serializerProvider().valueToTree(fromValue)
    }

    /*
     *******************************************************************************************************************
     * Other public methods
     *******************************************************************************************************************
     */

    /**
     * Method for visiting type hierarchy for given type, using specified visitor. Visitation uses `Serializer`
     * hierarchy and related properties
     * 
     * This method can be used for things like generating CirJSON Schema instance for specified type.
     *
     * @param type Type to generate schema for (possibly with generic signature)
     */
    open fun acceptCirJsonFormatVisitor(type: KotlinType, visitor: CirJsonFormatVisitorWrapper) {
        serializerProvider().acceptCirJsonFormatVisitor(type, visitor)
    }

    /**
     * Method for visiting type hierarchy for given type, using specified visitor. Visitation uses `Serializer`
     * hierarchy and related properties
     * 
     * This method can be used for things like generating CirJSON Schema instance for specified type.
     *
     * @param type Type to generate schema for (possibly with generic signature)
     */
    open fun acceptCirJsonFormatVisitor(type: KClass<*>, visitor: CirJsonFormatVisitorWrapper) {
        acceptCirJsonFormatVisitor(myConfig.constructType(type), visitor)
    }

    /*
     *******************************************************************************************************************
     * Helper method
     *******************************************************************************************************************
     */

    /**
     * Overridable helper method used for constructing [SerializerProvider] to use for serialization.
     */
    protected fun serializerProvider(): SerializationContextExtended {
        return mySerializationContexts.createContext(myConfig, myGeneratorSettings)
    }

    /*
     *******************************************************************************************************************
     * Internal methods
     *******************************************************************************************************************
     */

    protected open fun verifySchemaType(schema: FormatSchema?) {
        schema ?: return

        if (!myGeneratorFactory.canUseSchema(schema)) {
            throw IllegalArgumentException(
                    "Cannot use FormatSchema of type ${schema::class.qualifiedName} for format ${myGeneratorFactory.formatName}")
        }
    }

    /*
     *******************************************************************************************************************
     * Helper classes for configuration
     *******************************************************************************************************************
     */

    /**
     * As a minor optimization, we will make an effort to pre-fetch a serializer, or at least relevant `TypeSerializer`,
     * if given enough information.
     * 
     * @property myRootType Specified root serialization type to use; can be same as runtime type, but usually one of
     * its super types (parent class or interface it implements).
     * 
     * @property myValueSerializer We may pre-fetch serializer if [myRootType] is known, and if so, reuse it afterward.
     * This allows avoiding further serializer lookups and increases performance a bit on cases where readers are
     * reused.
     * 
     * @property myTypeSerializer When dealing with polymorphic types, we cannot pre-fetch serializer, but can pre-fetch
     * [TypeSerializer].
     */
    class Prefetch private constructor(private val myRootType: KotlinType?,
            private val myValueSerializer: ValueSerializer<Any>?, private val myTypeSerializer: TypeSerializer?) {

        fun forRootType(parent: ObjectWriter, newType: KotlinType?): Prefetch {
            if (newType == null) {
                if (myRootType == null || myValueSerializer == parent) {
                    return this
                }

                return Prefetch(null, null, null)
            }

            if (newType == myRootType) {
                return this
            }

            if (newType.isJavaLangObject) {
                val context = parent.serializerProvider()
                val typeSerializer = context.findTypeSerializer(newType)
                return Prefetch(null, null, typeSerializer)
            }

            if (parent.isEnabled(SerializationFeature.EAGER_SERIALIZER_FETCH)) {
                val context = parent.serializerProvider()

                try {
                    val serializer = context.findTypedValueSerializer(newType, true)

                    return if (serializer is TypeWrappedSerializer) {
                        Prefetch(newType, null, serializer.typeSerializer())
                    } else {
                        Prefetch(newType, serializer, null)
                    }
                } catch (_: CirJacksonException) {
                }
            }

            return Prefetch(newType, null, myTypeSerializer)
        }

        val valueSerializer: ValueSerializer<Any>?
            get() = myValueSerializer

        val typeSerializer: TypeSerializer?
            get() = myTypeSerializer

        fun hasSerializer(): Boolean {
            return myValueSerializer != null || myTypeSerializer != null
        }

        @Throws(CirJacksonException::class)
        fun serialize(generator: CirJsonGenerator, value: Any?, context: SerializationContextExtended) {
            if (myTypeSerializer != null) {
                context.serializePolymorphic(generator, value, myRootType, myValueSerializer, myTypeSerializer)
            } else if (myValueSerializer != null) {
                context.serializeValue(generator, value, myRootType, myValueSerializer)
            } else if (myRootType != null) {
                context.serializeValue(generator, value, myRootType)
            } else {
                context.serializeValue(generator, value)
            }
        }

        companion object {

            val EMPTY = Prefetch(null, null, null)

        }

    }

    companion object {

        internal fun construct(mapper: ObjectMapper, config: SerializationConfig, rootType: KotlinType?,
                prettyPrinter: PrettyPrinter?): ObjectWriter {
            return ObjectWriter(mapper, config, rootType, prettyPrinter)
        }

        internal fun construct(mapper: ObjectMapper, config: SerializationConfig): ObjectWriter {
            return ObjectWriter(mapper, config)
        }

        internal fun construct(mapper: ObjectMapper, config: SerializationConfig, schema: FormatSchema?): ObjectWriter {
            return ObjectWriter(mapper, config, schema)
        }

    }

}