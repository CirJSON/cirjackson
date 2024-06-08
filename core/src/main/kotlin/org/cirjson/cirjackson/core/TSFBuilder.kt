package org.cirjson.cirjackson.core

import org.cirjson.cirjackson.core.util.BufferRecycler
import org.cirjson.cirjackson.core.util.RecyclerPool

/**
 * Since factory instances are immutable, a Builder class is needed for creating configurations for differently
 * configured factory instances.
 *
 * @param F The Factory created
 * @param B Self type
 */
abstract class TSFBuilder<F : TokenStreamFactory, B : TSFBuilder<F, B>> private constructor(
        recyclerPool: RecyclerPool<BufferRecycler>?, streamReadConstraints: StreamReadConstraints,
        streamWriteConstraints: StreamWriteConstraints, errorReportConfiguration: ErrorReportConfiguration,
        factoryFeatures: Int, streamReadFeatures: Int, streamWriteFeatures: Int, formatReadFeatures: Int,
        formatWriteFeatures: Int) {

    protected constructor(streamReadConstraints: StreamReadConstraints, streamWriteConstraints: StreamWriteConstraints,
            errorReportConfiguration: ErrorReportConfiguration, formatReadFeatures: Int,
            formatWriteFeatures: Int) : this(null, streamReadConstraints, streamWriteConstraints,
            errorReportConfiguration, TokenStreamFactory.DEFAULT_FACTORY_FEATURE_FLAGS,
            TokenStreamFactory.DEFAULT_STREAM_READ_FEATURE_FLAGS, TokenStreamFactory.DEFAULT_STREAM_WRITE_FEATURE_FLAGS,
            formatReadFeatures, formatWriteFeatures)

    protected constructor(base: TokenStreamFactory) : this(base.recyclerPool, base.streamReadConstraints,
            base.streamWriteConstraints, base.errorReportConfiguration, base.factoryFeatures, base.streamReadFeatures,
            base.streamWriteFeatures, base.formatReadFeatures, base.formatWriteFeatures)

    /**
     * Buffer recycler provider to use.
     */
    internal var recyclerPool = recyclerPool
        private set

    /**
     * StreamReadConstraints to use.
     */
    internal var streamReadConstraints = streamReadConstraints
        private set

    /**
     * StreamWriteConstraints to use.
     */
    internal var streamWriteConstraints = streamWriteConstraints
        private set

    /**
     * [ErrorReportConfiguration] to use.
     */
    internal var errorReportConfiguration = errorReportConfiguration
        private set

    /**
     * Value for getting bit set of all [TokenStreamFactory.Feature]s enabled.
     */
    var factoryFeatures = factoryFeatures
        private set

    /**
     * Value for getting bit set of all [StreamReadFeature]s enabled.
     */
    var streamReadFeatures = streamReadFeatures
        private set

    /**
     * Value for getting bit set of all [StreamWriteFeature]s enabled.
     */
    var streamWriteFeatures = streamWriteFeatures
        private set

    /**
     * Value for getting bit set of all format-specific parser features enabled.
     */
    var formatReadFeatures = formatReadFeatures
        private set

    /**
     * Value for getting bit set of all format-specific generator features enabled.
     */
    var formatWriteFeatures = formatWriteFeatures
        private set

    fun enable(feature: TokenStreamFactory.Feature): B {
        factoryFeatures = factoryFeatures or feature.mask
        return getThis()
    }

    fun disable(feature: TokenStreamFactory.Feature): B {
        factoryFeatures = factoryFeatures and feature.mask.inv()
        return getThis()
    }

    fun configure(feature: TokenStreamFactory.Feature, isEnabled: Boolean): B {
        return if (isEnabled) enable(feature) else disable(feature)
    }

    fun enable(feature: StreamReadFeature): B {
        streamReadFeatures = streamReadFeatures or feature.mask
        return getThis()
    }

    fun disable(feature: StreamReadFeature): B {
        streamReadFeatures = streamReadFeatures and feature.mask.inv()
        return getThis()
    }

    fun configure(feature: StreamReadFeature, isEnabled: Boolean): B {
        return if (isEnabled) enable(feature) else disable(feature)
    }

    fun enable(feature: StreamWriteFeature): B {
        streamWriteFeatures = streamWriteFeatures or feature.mask
        return getThis()
    }

    fun disable(feature: StreamWriteFeature): B {
        streamWriteFeatures = streamWriteFeatures and feature.mask.inv()
        return getThis()
    }

    fun configure(feature: StreamWriteFeature, isEnabled: Boolean): B {
        return if (isEnabled) enable(feature) else disable(feature)
    }

    /**
     * Sets the constraints for streaming reads.
     *
     * @param streamReadConstraints constraints for streaming reads
     *
     * @return this builder
     */
    fun streamReadConstraints(streamReadConstraints: StreamReadConstraints): B {
        this.streamReadConstraints = streamReadConstraints
        return getThis()
    }

    /**
     * Sets the constraints for streaming writes.
     *
     * @param streamWriteConstraints constraints for streaming writes
     *
     * @return this builder
     */
    fun streamWriteConstraints(streamWriteConstraints: StreamWriteConstraints): B {
        this.streamWriteConstraints = streamWriteConstraints
        return getThis()
    }

    /**
     * Sets the configuration for error tokens.
     *
     * @param errorReportConfiguration configuration values used for handling errorneous token inputs.
     *
     * @return this builder
     */
    fun errorReportConfiguration(errorReportConfiguration: ErrorReportConfiguration): B {
        this.errorReportConfiguration = errorReportConfiguration
        return getThis()
    }

    /**
     * Sets the recycler pool.
     *
     * @param recyclerPool RecyclerPool to use for buffer allocation
     *
     * @return this builder (for call chaining)
     */
    fun recyclerPool(recyclerPool: RecyclerPool<BufferRecycler>?): B {
        this.recyclerPool = recyclerPool
        return getThis()
    }

    /**
     * Method for constructing actual [TokenStreamFactory] instance, given configuration.
     *
     * @return [TokenStreamFactory] build using builder configuration settings
     */
    abstract fun build(): F

    @Suppress("UNCHECKED_CAST")
    protected fun getThis(): B {
        return this as B
    }

}