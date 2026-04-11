package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.annotations.ObjectIdGenerator

open class ReadableObjectId(protected val myKey: ObjectIdGenerator.IDKey) {

    open val key: ObjectIdGenerator.IDKey
        get() = myKey

}