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

}