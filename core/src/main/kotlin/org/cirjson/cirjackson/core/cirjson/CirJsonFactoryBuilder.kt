package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.ErrorReportConfiguration
import org.cirjson.cirjackson.core.SerializableString
import org.cirjson.cirjackson.core.StreamReadConstraints
import org.cirjson.cirjackson.core.StreamWriteConstraints
import org.cirjson.cirjackson.core.base.DecorableTSFactory
import org.cirjson.cirjackson.core.io.CharacterEscapes
import org.cirjson.cirjackson.core.util.DefaultPrettyPrinter

/**
 * [org.cirjson.cirjackson.core.TSFBuilder] implementation for constructing [CirJsonFactory] instances for
 * reading/writing CirJSON encoded content.
 */
open class CirJsonFactoryBuilder : DecorableTSFactory.DecorableTSFBuilder<CirJsonFactory, CirJsonFactoryBuilder> {

    var caracterEscapes: CharacterEscapes?
        protected set

    var rootValueSeparator: SerializableString?
        protected set

    var highestNonEscapedCharCode: Int
        protected set

    /**
     * Character used for quoting Object Property names (if name quoting has not been disabled with
     * [CirJsonWriteFeature.QUOTE_PROPERTY_NAMES]) and CirJSON String values.
     */
    var quoteChar: Char
        protected set

    constructor() : super(StreamReadConstraints.defaults(), StreamWriteConstraints.defaults(),
            ErrorReportConfiguration.defaults(), CirJsonFactory.DEFAULT_CIRJSON_PARSER_FEATURE_FLAGS,
            CirJsonFactory.DEFAULT_CIRJSON_GENERATOR_FEATURE_FLAGS) {
        caracterEscapes = null
        rootValueSeparator = DefaultPrettyPrinter.DEFAULT_ROOT_VALUE_SEPARATOR
        highestNonEscapedCharCode = 0
        quoteChar = CirJsonFactory.DEFAULT_QUOTE_CHAR
    }

    constructor(base: CirJsonFactory) : super(base) {
        caracterEscapes = base.characterEscapes
        rootValueSeparator = base.rootValueSeparatorInternal
        highestNonEscapedCharCode = base.maximumNonEscapedCharCodeInternal
        quoteChar = base.quoteCharInternal
    }

    override fun build(): CirJsonFactory {
        return CirJsonFactory(this)
    }

}