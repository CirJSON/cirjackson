package org.cirjson.cirjackson.core.base

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.extensions.clone
import org.cirjson.cirjackson.core.io.IOContext
import org.cirjson.cirjackson.core.io.InputDecorator
import org.cirjson.cirjackson.core.io.OutputDecorator
import org.cirjson.cirjackson.core.util.CirJsonGeneratorDecorator
import java.io.*

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

    constructor(baseBuilder: DecorableTSFBuilder<*, *>) : super(baseBuilder) {
        inputDecorator = baseBuilder.inputDecorator
        outputDecorator = baseBuilder.outputDecorator
        myGeneratorDecorators = baseBuilder.generatorDecorators
    }

    /*
     *******************************************************************************************************************
     * Decorators, input
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun decorate(context: IOContext, input: InputStream): InputStream {
        if (inputDecorator != null) {
            val input2 = inputDecorator.decorate(context, input)

            if (input2 != null) {
                return input2
            }
        }

        return input
    }

    @Throws(CirJacksonException::class)
    protected fun decorate(context: IOContext, reader: Reader): Reader {
        if (inputDecorator != null) {
            val reader2 = inputDecorator.decorate(context, reader)

            if (reader2 != null) {
                return reader2
            }
        }

        return reader
    }

    @Throws(CirJacksonException::class)
    protected fun decorate(context: IOContext, input: DataInput): DataInput {
        if (inputDecorator != null) {
            val input2 = inputDecorator.decorate(context, input)

            if (input2 != null) {
                return input2
            }
        }

        return input
    }

    /*
     *******************************************************************************************************************
     * Decorators, output
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun decorate(context: IOContext, output: OutputStream): OutputStream {
        if (outputDecorator != null) {
            val output2 = outputDecorator.decorate(context, output)

            if (output2 != null) {
                return output2
            }
        }

        return output
    }

    @Throws(CirJacksonException::class)
    protected fun decorate(context: IOContext, output: Writer): Writer {
        if (outputDecorator != null) {
            val output2 = outputDecorator.decorate(context, output)

            if (output2 != null) {
                return output2
            }
        }

        return output
    }

    @Throws(CirJacksonException::class)
    protected fun decorate(generator: CirJsonGenerator): CirJsonGenerator {
        var result = generator

        if (myGeneratorDecorators != null) {
            for (decorator in myGeneratorDecorators) {
                result = decorator.generate(this, result)
            }
        }

        return result
    }

    /**
     * Since factory instances are immutable, a Builder class is needed for creating configurations for differently
     * configured factory instances.
     */
    abstract class DecorableTSFBuilder<F : TokenStreamFactory, T : TSFBuilder<F, T>> : TSFBuilder<F, T> {

        /**
         * Optional helper object that may decorate input sources, to do additional processing on input during parsing.
         */
        var inputDecorator: InputDecorator?
            private set

        /**
         * Optional helper object that may decorate output object, to do additional processing on output during content
         * generation.
         */
        var outputDecorator: OutputDecorator?
            private set

        var generatorDecorators: List<CirJsonGeneratorDecorator>?
            private set

        constructor(streamReadConstraints: StreamReadConstraints, streamWriteConstraints: StreamWriteConstraints,
                errorReportConfiguration: ErrorReportConfiguration, formatReadFeatures: Int,
                formatWriteFeatures: Int) : super(streamReadConstraints, streamWriteConstraints,
                errorReportConfiguration, formatReadFeatures, formatWriteFeatures) {
            inputDecorator = null
            outputDecorator = null
            generatorDecorators = null
        }

        constructor(base: DecorableTSFactory) : super(base) {
            inputDecorator = base.inputDecorator
            outputDecorator = base.outputDecorator
            generatorDecorators = base.generatorDecorators?.clone()
        }

        fun inputDecorator(inputDecorator: InputDecorator?): T {
            this.inputDecorator = inputDecorator
            return getThis()
        }

        fun outputDecorator(outputDecorator: OutputDecorator?): T {
            this.outputDecorator = outputDecorator
            return getThis()
        }

        fun generatorDecorators(generatorDecorators: List<CirJsonGeneratorDecorator>?): T {
            this.generatorDecorators = generatorDecorators ?: arrayListOf()
            return getThis()
        }

    }

}