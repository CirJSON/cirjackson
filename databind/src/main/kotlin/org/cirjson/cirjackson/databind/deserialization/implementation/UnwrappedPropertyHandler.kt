package org.cirjson.cirjackson.databind.deserialization.implementation

import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.util.InternCache
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.util.NameTransformer
import org.cirjson.cirjackson.databind.util.TokenBuffer

/**
 * Object that is responsible for handling acrobatics related to deserializing "unwrapped" values; sets of properties
 * that are embedded (inlined) as properties of parent CirJSON object.
 */
open class UnwrappedPropertyHandler {

    protected val myProperties: MutableList<SettableBeanProperty>

    constructor() {
        myProperties = ArrayList()
    }

    protected constructor(properties: MutableList<SettableBeanProperty>) {
        myProperties = properties
    }

    open fun addProperty(property: SettableBeanProperty) {
        myProperties.add(property)
    }

    open fun renameAll(context: DeserializationContext, transformer: NameTransformer): UnwrappedPropertyHandler {
        val properties = ArrayList<SettableBeanProperty>(myProperties.size)

        for (prop in myProperties) {
            var property = prop
            val newName = transformer.transform(property.name).let { InternCache.INSTANCE.intern(it) }
            property = property.withSimpleName(newName)
            val deserializer = property.valueDeserializer

            if (deserializer == null) {
                properties.add(property)
                continue
            }

            val newDeserializer = deserializer.unwrappingDeserializer(context, transformer)

            if (newDeserializer !== deserializer) {
                property = property.withValueDeserializer(newDeserializer)
            }

            properties.add(property)
        }

        return UnwrappedPropertyHandler(properties)
    }

    open fun processUnwrapped(originalParser: CirJsonParser, context: DeserializationContext, bean: Any,
            buffered: TokenBuffer): Any {
        for (property in myProperties) {
            val parser = buffered.asParserOnFirstToken(context)
            property.deserializeAndSet(parser, context, bean)
        }

        return bean
    }

}