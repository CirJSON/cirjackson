package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.FormatFeature
import org.cirjson.cirjackson.core.PrettyPrinter
import org.cirjson.cirjackson.core.StreamWriteFeature
import org.cirjson.cirjackson.core.util.Instantiatable
import org.cirjson.cirjackson.databind.cirjsontype.SubtypeResolver
import org.cirjson.cirjackson.databind.configuration.*
import org.cirjson.cirjackson.databind.introspection.ClassIntrospector
import org.cirjson.cirjackson.databind.introspection.MixInHandler
import org.cirjson.cirjackson.databind.serialization.FilterProvider
import org.cirjson.cirjackson.databind.type.TypeFactory
import org.cirjson.cirjackson.databind.util.RootNameLookup
import java.text.DateFormat
import kotlin.reflect.KClass

class SerializationConfig : MapperConfigBase<SerializationFeature, SerializationConfig> {

    /*
     *******************************************************************************************************************
     * Configured helper objects
     *******************************************************************************************************************
     */

    /**
     * Object used for resolving filter ids to filter instances. Non-`null` if explicitly defined; `null` by default.
     */
    private val myFilterProvider: FilterProvider?

    /**
     * If "default pretty-printing" is enabled, it will create the instance from this blueprint object.
     */
    private val myDefaultPrettyPrinter: PrettyPrinter?

    /*
     *******************************************************************************************************************
     * Feature flags
     *******************************************************************************************************************
     */

    /**
     * Set of [SerializationFeatures][SerializationFeature] enabled.
     */
    private val mySerializationFeatures: Int

    /**
     * States of [StreamWriteFeatures][StreamWriteFeature] to enable/disable.
     */
    private val myStreamWriteFeatures: Int

    /**
     * States of [FormatWriteFeatures][FormatFeature] to enable/disable.
     */
    private val myFormatWriteFeatures: Int

    /*
     *******************************************************************************************************************
     * Lifecycle, primary constructors for new instances
     *******************************************************************************************************************
     */

    constructor(builder: MapperBuilder<*, *>, mapperFeatures: Long, serializationFeatures: Int,
            streamWriteFeatures: Int, formatWriteFeatures: Int, configOverrides: ConfigOverrides,
            typeFactory: TypeFactory, classIntrospector: ClassIntrospector, mixins: MixInHandler,
            subtypeResolver: SubtypeResolver, defaultAttributes: ContextAttributes, rootNames: RootNameLookup,
            filterProvider: FilterProvider?) : super(builder, mapperFeatures, typeFactory, classIntrospector, mixins,
            subtypeResolver, configOverrides, defaultAttributes, rootNames) {
        mySerializationFeatures = serializationFeatures
        myFilterProvider = filterProvider
        myStreamWriteFeatures = streamWriteFeatures
        myFormatWriteFeatures = formatWriteFeatures
        myDefaultPrettyPrinter = builder.defaultPrettyPrinter()
    }

    /*
     *******************************************************************************************************************
     * Life-cycle, secondary constructors to support "mutant factories", with single property changes
     *******************************************************************************************************************
     */

    private constructor(source: SerializationConfig, serializationFeatures: Int, streamWriteFeatures: Int,
            formatWriteFeatures: Int) : super(source) {
        mySerializationFeatures = serializationFeatures
        myFilterProvider = source.myFilterProvider
        myDefaultPrettyPrinter = source.myDefaultPrettyPrinter
        myStreamWriteFeatures = streamWriteFeatures
        myFormatWriteFeatures = formatWriteFeatures
    }

    private constructor(source: SerializationConfig, base: BaseSettings) : super(source, base) {
        mySerializationFeatures = source.mySerializationFeatures
        myFilterProvider = source.myFilterProvider
        myDefaultPrettyPrinter = source.myDefaultPrettyPrinter
        myStreamWriteFeatures = source.myStreamWriteFeatures
        myFormatWriteFeatures = source.myFormatWriteFeatures
    }

    private constructor(source: SerializationConfig, filterProvider: FilterProvider?) : super(source) {
        mySerializationFeatures = source.mySerializationFeatures
        myFilterProvider = filterProvider
        myDefaultPrettyPrinter = source.myDefaultPrettyPrinter
        myStreamWriteFeatures = source.myStreamWriteFeatures
        myFormatWriteFeatures = source.myFormatWriteFeatures
    }

    private constructor(source: SerializationConfig, view: KClass<*>?) : super(source, view) {
        mySerializationFeatures = source.mySerializationFeatures
        myFilterProvider = source.myFilterProvider
        myDefaultPrettyPrinter = source.myDefaultPrettyPrinter
        myStreamWriteFeatures = source.myStreamWriteFeatures
        myFormatWriteFeatures = source.myFormatWriteFeatures
    }

    private constructor(source: SerializationConfig, rootName: PropertyName?) : super(source, rootName) {
        mySerializationFeatures = source.mySerializationFeatures
        myFilterProvider = source.myFilterProvider
        myDefaultPrettyPrinter = source.myDefaultPrettyPrinter
        myStreamWriteFeatures = source.myStreamWriteFeatures
        myFormatWriteFeatures = source.myFormatWriteFeatures
    }

    private constructor(source: SerializationConfig, attributes: ContextAttributes) : super(source, attributes) {
        mySerializationFeatures = source.mySerializationFeatures
        myFilterProvider = source.myFilterProvider
        myDefaultPrettyPrinter = source.myDefaultPrettyPrinter
        myStreamWriteFeatures = source.myStreamWriteFeatures
        myFormatWriteFeatures = source.myFormatWriteFeatures
    }

    private constructor(source: SerializationConfig, defaultPrettyPrinter: PrettyPrinter?) : super(source) {
        mySerializationFeatures = source.mySerializationFeatures
        myFilterProvider = source.myFilterProvider
        myDefaultPrettyPrinter = defaultPrettyPrinter
        myStreamWriteFeatures = source.myStreamWriteFeatures
        myFormatWriteFeatures = source.myFormatWriteFeatures
    }

    private constructor(source: SerializationConfig, datatypeFeatures: DatatypeFeatures) : super(source,
            datatypeFeatures) {
        mySerializationFeatures = source.mySerializationFeatures
        myFilterProvider = source.myFilterProvider
        myDefaultPrettyPrinter = source.myDefaultPrettyPrinter
        myStreamWriteFeatures = source.myStreamWriteFeatures
        myFormatWriteFeatures = source.myFormatWriteFeatures
    }

    /*
     *******************************************************************************************************************
     * Lifecycle, factory methods from MapperConfigBase
     *******************************************************************************************************************
     */

    override fun withBase(newBase: BaseSettings): SerializationConfig {
        return if (newBase === myBase) this else SerializationConfig(this, newBase)
    }

    override fun with(datatypeFeatures: DatatypeFeatures): SerializationConfig {
        return SerializationConfig(this, datatypeFeatures)
    }

    override fun withRootName(rootName: PropertyName?): SerializationConfig {
        return if (myRootName == rootName) this else SerializationConfig(this, rootName)
    }

    override fun withView(view: KClass<*>?): SerializationConfig {
        return if (view === myView) this else SerializationConfig(this, view)
    }

    override fun with(attributes: ContextAttributes): SerializationConfig {
        return if (attributes === myAttributes) this else SerializationConfig(this, attributes)
    }

    /*
     *******************************************************************************************************************
     * Factory method overrides
     *******************************************************************************************************************
     */

    /**
     * In addition to constructing instance with specified date format, will enable or disable
     * `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS` (enable if format set as `null`; disable if non-`null`)
     */
    override fun with(dateFormat: DateFormat): SerializationConfig {
        return super.with(dateFormat).without(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    /*
     *******************************************************************************************************************
     * Factory methods for SerializationFeature
     *******************************************************************************************************************
     */

    /**
     * Fluent factory method that will construct and return a new configuration object instance with specified feature
     * enabled.
     */
    fun with(feature: SerializationFeature): SerializationConfig {
        val newFeatures = mySerializationFeatures or feature.mask

        return if (mySerializationFeatures == newFeatures) {
            this
        } else {
            SerializationConfig(this, newFeatures, myStreamWriteFeatures, myFormatWriteFeatures)
        }
    }

    /**
     * Fluent factory method that will construct and return a new configuration object instance with specified features
     * enabled.
     */
    fun with(first: SerializationFeature, vararg features: SerializationFeature): SerializationConfig {
        var newFeatures = mySerializationFeatures or first.mask

        for (feature in features) {
            newFeatures = newFeatures or feature.mask
        }

        return if (mySerializationFeatures == newFeatures) {
            this
        } else {
            SerializationConfig(this, newFeatures, myStreamWriteFeatures, myFormatWriteFeatures)
        }
    }

    /**
     * Fluent factory method that will construct and return a new configuration object instance with specified features
     * enabled.
     */
    fun withFeatures(vararg features: SerializationFeature): SerializationConfig {
        var newFeatures = mySerializationFeatures

        for (feature in features) {
            newFeatures = newFeatures or feature.mask
        }

        return if (mySerializationFeatures == newFeatures) {
            this
        } else {
            SerializationConfig(this, newFeatures, myStreamWriteFeatures, myFormatWriteFeatures)
        }
    }

    /**
     * Fluent factory method that will construct and return a new configuration object instance with specified feature
     * disabled.
     */
    fun without(feature: SerializationFeature): SerializationConfig {
        val newFeatures = mySerializationFeatures and feature.mask.inv()

        return if (mySerializationFeatures == newFeatures) {
            this
        } else {
            SerializationConfig(this, newFeatures, myStreamWriteFeatures, myFormatWriteFeatures)
        }
    }

    /**
     * Fluent factory method that will construct and return a new configuration object instance with specified features
     * disabled.
     */
    fun without(first: SerializationFeature, vararg features: SerializationFeature): SerializationConfig {
        val newFeatures = mySerializationFeatures and first.mask.inv()

        return if (mySerializationFeatures == newFeatures) {
            this
        } else {
            SerializationConfig(this, newFeatures, myStreamWriteFeatures, myFormatWriteFeatures)
        }
    }

    /**
     * Fluent factory method that will construct and return a new configuration object instance with specified feature
     * disabled.
     */
    fun withoutFeatures(vararg features: SerializationFeature): SerializationConfig {
        var newFeatures = mySerializationFeatures

        for (feature in features) {
            newFeatures = newFeatures and feature.mask.inv()
        }

        return if (mySerializationFeatures == newFeatures) {
            this
        } else {
            SerializationConfig(this, newFeatures, myStreamWriteFeatures, myFormatWriteFeatures)
        }
    }

    /*
     *******************************************************************************************************************
     * Factory methods for StreamWriteFeature
     *******************************************************************************************************************
     */

    /**
     * Fluent factory method that will construct and return a new configuration object instance with specified feature
     * enabled.
     */
    fun with(feature: StreamWriteFeature): SerializationConfig {
        val newFeatures = myStreamWriteFeatures or feature.mask

        return if (myStreamWriteFeatures == newFeatures) {
            this
        } else {
            SerializationConfig(this, mySerializationFeatures, newFeatures, myFormatWriteFeatures)
        }
    }

    /**
     * Fluent factory method that will construct and return a new configuration object instance with specified features
     * enabled.
     */
    fun withFeatures(vararg features: StreamWriteFeature): SerializationConfig {
        var newFeatures = myStreamWriteFeatures

        for (feature in features) {
            newFeatures = newFeatures or feature.mask
        }

        return if (myStreamWriteFeatures == newFeatures) {
            this
        } else {
            SerializationConfig(this, myStreamWriteFeatures, newFeatures, myFormatWriteFeatures)
        }
    }

    /**
     * Fluent factory method that will construct and return a new configuration object instance with specified feature
     * disabled.
     */
    fun without(feature: StreamWriteFeature): SerializationConfig {
        val newFeatures = myStreamWriteFeatures and feature.mask.inv()

        return if (myStreamWriteFeatures == newFeatures) {
            this
        } else {
            SerializationConfig(this, mySerializationFeatures, newFeatures, myFormatWriteFeatures)
        }
    }

    /**
     * Fluent factory method that will construct and return a new configuration object instance with specified feature
     * disabled.
     */
    fun withoutFeatures(vararg features: StreamWriteFeature): SerializationConfig {
        var newFeatures = myStreamWriteFeatures

        for (feature in features) {
            newFeatures = newFeatures and feature.mask.inv()
        }

        return if (myStreamWriteFeatures == newFeatures) {
            this
        } else {
            SerializationConfig(this, mySerializationFeatures, newFeatures, myFormatWriteFeatures)
        }
    }

    /*
     *******************************************************************************************************************
     * Factory methods for FormatFeature
     *******************************************************************************************************************
     */

    /**
     * Fluent factory method that will construct and return a new configuration object instance with specified feature
     * enabled.
     */
    fun with(feature: FormatFeature): SerializationConfig {
        val newFeatures = myFormatWriteFeatures or feature.mask

        return if (myFormatWriteFeatures == newFeatures) {
            this
        } else {
            SerializationConfig(this, mySerializationFeatures, myStreamWriteFeatures, newFeatures)
        }
    }

    /**
     * Fluent factory method that will construct and return a new configuration object instance with specified features
     * enabled.
     */
    fun withFeatures(vararg features: FormatFeature): SerializationConfig {
        var newFeatures = myFormatWriteFeatures

        for (feature in features) {
            newFeatures = newFeatures or feature.mask
        }

        return if (myFormatWriteFeatures == newFeatures) {
            this
        } else {
            SerializationConfig(this, mySerializationFeatures, myStreamWriteFeatures, newFeatures)
        }
    }

    /**
     * Fluent factory method that will construct and return a new configuration object instance with specified feature
     * disabled.
     */
    fun without(feature: FormatFeature): SerializationConfig {
        val newFeatures = myFormatWriteFeatures and feature.mask.inv()

        return if (myFormatWriteFeatures == newFeatures) {
            this
        } else {
            SerializationConfig(this, mySerializationFeatures, myStreamWriteFeatures, newFeatures)
        }
    }

    /**
     * Fluent factory method that will construct and return a new configuration object instance with specified feature
     * disabled.
     */
    fun withoutFeatures(vararg features: FormatFeature): SerializationConfig {
        var newFeatures = myFormatWriteFeatures

        for (feature in features) {
            newFeatures = newFeatures and feature.mask.inv()
        }

        return if (myFormatWriteFeatures == newFeatures) {
            this
        } else {
            SerializationConfig(this, mySerializationFeatures, myStreamWriteFeatures, newFeatures)
        }
    }

    /*
     *******************************************************************************************************************
     * Factory methods, other
     *******************************************************************************************************************
     */

    fun withFilters(filterProvider: FilterProvider?): SerializationConfig {
        return if (myFilterProvider === filterProvider) this else SerializationConfig(this, filterProvider)
    }

    fun withDefaultPrettyPrinter(prettyPrinter: PrettyPrinter?): SerializationConfig {
        return if (myDefaultPrettyPrinter === prettyPrinter) this else SerializationConfig(this, prettyPrinter)
    }

    /*
     *******************************************************************************************************************
     * Factories for objects configured here
     *******************************************************************************************************************
     */

    fun constructDefaultPrettyPrinter(): PrettyPrinter? {
        val prettyPrinter = myDefaultPrettyPrinter

        if (prettyPrinter is Instantiatable<*>) {
            return prettyPrinter.createInstance() as PrettyPrinter
        }

        return prettyPrinter
    }

    /*
     *******************************************************************************************************************
     * Support for ObjectWriteContext
     *******************************************************************************************************************
     */

    val streamWriteFeatures: Int
        get() = myStreamWriteFeatures

    val formatWriteFeatures: Int
        get() = myFormatWriteFeatures

    /*
     *******************************************************************************************************************
     * Configuration: other
     *******************************************************************************************************************
     */

    override fun useRootWrapping(): Boolean {
        return if (myRootName != null) {
            !myRootName.isEmpty()
        } else {
            isEnabled(SerializationFeature.WRAP_ROOT_VALUE)
        }
    }

    /**
     * Accessor for checking whether given [SerializationFeature] is enabled or not.
     *
     * @param feature Feature to check
     *
     * @return `true` if feature is enabled; `false` otherwise
     */
    fun isEnabled(feature: SerializationFeature): Boolean {
        return mySerializationFeatures and feature.mask != 0
    }

    /**
     * Accessor for checking whether given [StreamWriteFeature] is enabled or not.
     *
     * @param feature Feature to check
     *
     * @return `true` if feature is enabled; `false` otherwise
     */
    fun isEnabled(feature: StreamWriteFeature): Boolean {
        return myStreamWriteFeatures and feature.mask != 0
    }

    fun hasFormatFeature(feature: FormatFeature): Boolean {
        return myFormatWriteFeatures and feature.mask != 0
    }

    /**
     * "Bulk" access method for checking that all features specified by mask are enabled.
     */
    fun hasSerializationFeatures(featureMask: Int): Boolean {
        return myFormatWriteFeatures and featureMask == featureMask
    }

    internal val serializationFeatures: Int
        get() = mySerializationFeatures

    /**
     * Method for getting provider used for locating filters given id (which is usually provided with filter
     * annotations). Will be null if no provided was set for [ObjectWriter] (or if serialization directly called from
     * [ObjectMapper])
     */
    val filterProviders: FilterProvider?
        get() = myFilterProvider

    /**
     * Accessor for configured blueprint "default" [PrettyPrinter] to use, if default pretty-printing is enabled.
     * 
     * NOTE: returns the "blueprint" instance, and does NOT construct an instance ready to use; call
     * [constructDefaultPrettyPrinter] if actually usable instance is desired.
     */
    val defaultPrettyPrinter: PrettyPrinter?
        get() = myDefaultPrettyPrinter

}