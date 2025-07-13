package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.core.FormatSchema
import org.cirjson.cirjackson.core.PrettyPrinter
import org.cirjson.cirjackson.core.SerializableString
import org.cirjson.cirjackson.core.io.CharacterEscapes

class GeneratorSettings(val prettyPrinter: PrettyPrinter?, val schema: FormatSchema?,
        val characterEscapes: CharacterEscapes?, val rootValueSeparator: SerializableString?) {
}