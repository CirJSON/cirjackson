package org.cirjson.cirjackson.databind.serialization.implementation

import org.cirjson.cirjackson.databind.serialization.standard.ToEmptyObjectSerializer
import kotlin.reflect.KClass

open class UnknownSerializer : ToEmptyObjectSerializer {

    constructor() : super(Any::class)

    constructor(clazz: KClass<*>) : super(clazz)

}