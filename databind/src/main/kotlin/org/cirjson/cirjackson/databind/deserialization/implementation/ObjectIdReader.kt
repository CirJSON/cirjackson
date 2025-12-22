package org.cirjson.cirjackson.databind.deserialization.implementation

import org.cirjson.cirjackson.annotations.ObjectIdGenerator
import org.cirjson.cirjackson.annotations.ObjectIdResolver
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty

open class ObjectIdReader protected constructor(protected val myIdType: KotlinType, val propertyName: PropertyName,
        val generator: ObjectIdGenerator<*>, val resolver: ObjectIdResolver,
        protected val myDeserializer: ValueDeserializer<Any>, val idProperty: SettableBeanProperty?) {
}