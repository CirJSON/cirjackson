package org.cirjson.cirjackson.core.base

import org.cirjson.cirjackson.core.ErrorReportConfiguration
import org.cirjson.cirjackson.core.StreamReadConstraints
import org.cirjson.cirjackson.core.StreamWriteConstraints
import org.cirjson.cirjackson.core.TokenStreamFactory
import org.cirjson.cirjackson.core.extentions.clone
import org.cirjson.cirjackson.core.io.InputDecorator
import org.cirjson.cirjackson.core.io.OutputDecorator
import org.cirjson.cirjackson.core.util.CirJsonGeneratorDecorator

/**
 * Intermediate base [TokenStreamFactory] implementation that offers support for streams that allow decoration of
 * low-level input sources and output targets.
 */
abstract class DecorableTSFactory : TokenStreamFactory {

    /**
     * Optional helper object that may decorate input sources, to do additional processing on input during parsing.
     */
    val inputDecorator: InputDecorator?

    /**
     * Optional helper object that may decorate output sources, to do additional processing on output during content
     * generation.
     */
    val outputDecorator: OutputDecorator?

    protected val myGeneratorDecorators: List<CirJsonGeneratorDecorator>?

    val generatorDecorators: List<CirJsonGeneratorDecorator>?
        get() = myGeneratorDecorators?.clone()

    constructor(streamReadConstraints: StreamReadConstraints, streamWriteConstraints: StreamWriteConstraints,
            errorReportConfiguration: ErrorReportConfiguration, formatReadFeatures: Int,
            formatWriteFeatures: Int) : super(streamReadConstraints, streamWriteConstraints, errorReportConfiguration,
            formatReadFeatures, formatWriteFeatures) {
        inputDecorator = null
        outputDecorator = null
        myGeneratorDecorators = null
    }

    constructor(src: DecorableTSFactory) : super(src) {
        inputDecorator = src.inputDecorator
        outputDecorator = src.outputDecorator
        myGeneratorDecorators = src.myGeneratorDecorators?.clone()
    }

}