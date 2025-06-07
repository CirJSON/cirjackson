package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.annotations.ObjectIdGenerator
import org.cirjson.cirjackson.annotations.ObjectIdResolver
import org.cirjson.cirjackson.databind.PropertyName
import kotlin.reflect.KClass

open class ObjectIdInfo(val propertyName: PropertyName, val scope: KClass<*>,
        val generatorType: KClass<out ObjectIdGenerator<*>>, val resolverType: KClass<out ObjectIdResolver>,
        val alwaysAsId: Boolean) {
}