package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.databind.deserialization.NullValueProvider

abstract class ValueDeserializer<T> : NullValueProvider {

    abstract class None private constructor() : ValueDeserializer<Any>()

}