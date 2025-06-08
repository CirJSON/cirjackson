package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.KotlinType
import kotlin.reflect.KClass

abstract class Annotated protected constructor() {

    abstract fun <A : Annotation> getAnnotation(kClass: KClass<A>): A?

    abstract val name: String

    abstract val type: KotlinType

    abstract val rawType: KClass<*>

}