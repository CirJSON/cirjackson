package org.cirjson.cirjackson.databind.deserialization.standard

import org.cirjson.cirjackson.annotations.ObjectIdGenerator
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.deserialization.implementation.ObjectIdReader
import org.cirjson.cirjackson.databind.deserialization.jdk.StringDeserializer

open class StandardObjectIdReader(idType: KotlinType, generator: ObjectIdGenerator<*>,
        idProperty: SettableBeanProperty?) :
        ObjectIdReader(idType, PROPERTY_NAME, generator, StandardObjectIdResolver.INSTANCE, StringDeserializer(),
                idProperty) {

    companion object {

        private val PROPERTY_NAME = PropertyName.construct("__cirJsonId__")

    }

}