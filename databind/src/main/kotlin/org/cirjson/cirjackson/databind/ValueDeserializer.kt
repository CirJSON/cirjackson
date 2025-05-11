package org.cirjson.cirjackson.databind

// TODO
abstract class ValueDeserializer<T> {

    abstract class None private constructor() : ValueDeserializer<Any>()

}