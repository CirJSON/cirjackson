package org.cirjson.cirjackson.databind.serialization.implementation

import org.cirjson.cirjackson.databind.serialization.standard.ToEmptyObjectSerializer

open class UnknownSerializer : ToEmptyObjectSerializer {

    constructor() : super(Any::class)

}