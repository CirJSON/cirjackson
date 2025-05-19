package org.cirjson.cirjackson.databind.introspection

import kotlin.reflect.KClass

abstract class Annotated protected constructor() {

    abstract fun <A : Annotation> getAnnotation(kClass: KClass<A>): A?

}