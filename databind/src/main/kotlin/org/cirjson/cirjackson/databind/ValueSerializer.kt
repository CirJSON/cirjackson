package org.cirjson.cirjackson.databind

// TODO
abstract class ValueSerializer<T> {

    abstract class None private constructor() : ValueSerializer<Any>()

}