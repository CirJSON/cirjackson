package org.cirjson.cirjackson.databind.deserialization.cirjackson

import org.cirjson.cirjackson.core.CirJsonLocation
import org.cirjson.cirjackson.core.io.ContentReference
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.deserialization.CreatorProperty
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.deserialization.ValueInstantiator

/**
 * For [CirJsonLocation], we should be able to just implement [ValueInstantiator] (not that explicit one would be very
 * hard but...)
 */
open class CirJsonLocationInstantiator : ValueInstantiator.Base(CirJsonLocation::class) {

    override fun canCreateFromObjectWith(): Boolean {
        return true
    }

    override fun getFromObjectArguments(config: DeserializationConfig): Array<SettableBeanProperty>? {
        val intType = config.constructType(Int::class)
        val longType = config.constructType(Long::class)
        return arrayOf(creatorProperty("byteOffset", longType, 0, true),
                creatorProperty("charOffset", longType, 1, true), creatorProperty("lineNr", intType, 2, true),
                creatorProperty("columnNr", intType, 3, true),
                creatorProperty("sourceRef", config.constructType(Any::class), 4, false))
    }

    override fun createFromObjectWith(context: DeserializationContext, args: Array<Any?>): Any? {
        val sourceReference = ContentReference.unknown()
        return CirJsonLocation(sourceReference, long(args[0]), long(args[1]), int(args[2]), int(args[3]))
    }

    companion object {

        private fun creatorProperty(name: String, type: KotlinType, index: Int, required: Boolean): CreatorProperty {
            val metadata = if (required) {
                PropertyMetadata.STANDARD_REQUIRED
            } else {
                PropertyMetadata.STANDARD_OPTIONAL
            }

            return CreatorProperty.construct(PropertyName.construct(name), type, null, null, null, null, index, null,
                    metadata)
        }

        private fun long(value: Any?): Long {
            return (value as Number?)?.toLong() ?: 0L
        }

        private fun int(value: Any?): Int {
            return (value as Number?)?.toInt() ?: 0
        }

    }

}